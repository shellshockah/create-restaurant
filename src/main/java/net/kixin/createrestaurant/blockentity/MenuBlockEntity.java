package net.kixin.createrestaurant.blockentity;

import net.kixin.createrestaurant.client.RestaurantMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class MenuBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_ROWS = 30;

    private final ItemStackHandler foods =
            new ItemStackHandler(MAX_ROWS);

    private final int[] prices = new int[MAX_ROWS];
    public static final int DATA_COUNT = 10;
    public static final int MAX_PRICE = 64;
    public static final int SUNSET = 12_000;
    private static final int CUSTOMER_INTERVAL = 200;
    private final int[] customerHistory = new int[5];
    private String restaurantName = "My Restaurant";
    private boolean running;
    private int rating;
    private int activeRows = 2;
    private int currentCustomers;
    private int pendingEmeralds;
    private int customerTimer;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> running ? 1 : 0;
                case 1 -> rating;
                case 2 -> activeRows;
                case 3 -> currentCustomers;
                case 4 -> pendingEmeralds;
                case 5, 6, 7, 8, 9 -> customerHistory[index - 5];
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> running = value != 0;
                case 1 -> rating = value;
                case 2 -> activeRows = value;
                case 3 -> currentCustomers = value;
                case 4 -> pendingEmeralds = value;
                case 5, 6, 7, 8, 9 -> customerHistory[index - 5] = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MenuBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MENU_ENTITIES.get(), pos, state);
        Arrays.fill(prices, 1);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MenuBlockEntity blockEntity) {
        if (level.isClientSide || !blockEntity.running) {
            return;
        }

        long timeOfDay = level.getDayTime() % 24_000L;
        if (timeOfDay >= SUNSET) {
            blockEntity.finishGame();
            return;
        }

        blockEntity.customerTimer++;
        if (blockEntity.customerTimer >= CUSTOMER_INTERVAL) {
            blockEntity.customerTimer = 0;
            blockEntity.tryCustomer();
        }
    }

    public void toggleGame(ServerPlayer player) {
        if (running) {
            finishGame();
            return;
        }

        if (level == null || level.getDayTime() % 24_000L >= SUNSET) {
            player.displayClientMessage(Component.translatable("message.create_restaurant.closed_at_night"), true);
            return;
        }

        running = true;
        currentCustomers = 0;
        customerTimer = 0;
        setChangedAndSync();
    }

    private void finishGame() {
        running = false;
        customerTimer = 0;

        System.arraycopy(customerHistory, 1, customerHistory, 0, customerHistory.length - 1);
        customerHistory[customerHistory.length - 1] = currentCustomers;

        int variety = availableDishCount();
        int earnedStars = 1 + Math.min(3, currentCustomers / 5);
        if (variety >= 3) {
            earnedStars++;
        }
        if (averageListedPrice() > 20) {
            earnedStars--;
        }
        earnedStars = Mth.clamp(earnedStars, 1, 5);
        rating = rating == 0 ? earnedStars : Mth.clamp(Math.round((rating * 2 + earnedStars) / 3.0F), 1, 5);

        setChangedAndSync();
    }

    private void tryCustomer() {
        if (level == null) {
            return;
        }

        int[] availableRows = new int[activeRows];
        int available = 0;
        for (int row = 0; row < activeRows; row++) {
            if (!foods.getStackInSlot(row).isEmpty() && prices[row] > 0) {
                availableRows[available++] = row;
            }
        }
        if (available == 0) {
            return;
        }

        float chance = 0.25F + rating * 0.08F + Math.min(0.15F, available * 0.04F);
        chance -= Math.min(0.20F, averageListedPrice() * 0.006F);
        if (level.getRandom().nextFloat() > Mth.clamp(chance, 0.10F, 0.85F)) {
            return;
        }

        int chosenRow = availableRows[level.getRandom().nextInt(available)];
        ItemStack sold = foods.extractItem(chosenRow, 1, false);
        if (!sold.isEmpty()) {
            currentCustomers++;
            pendingEmeralds += prices[chosenRow];
            setChangedAndSync();
        }
    }

    private int availableDishCount() {
        int count = 0;
        for (int row = 0; row < activeRows; row++) {
            if (!foods.getStackInSlot(row).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int averageListedPrice() {
        int total = 0;
        int count = 0;
        for (int row = 0; row < activeRows; row++) {
            if (!foods.getStackInSlot(row).isEmpty()) {
                total += prices[row];
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    public void setRestaurantName(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}]", "").trim();
        restaurantName = clean.isEmpty() ? "My Restaurant" : clean.substring(0, Math.min(32, clean.length()));
        setChangedAndSync();
    }

    public void setPrice(int row, int price) {
        if (row < 0 || row >= activeRows) {
            return;
        }
        prices[row] = Mth.clamp(price, 1, MAX_PRICE);
        setChangedAndSync();
    }

    public void addRow() {
        if (activeRows < MAX_ROWS) {
            activeRows++;
            setChangedAndSync();
        }
    }

    public void subRow() {
        if (activeRows >= 2) {
            activeRows--;
            setChangedAndSync();
        }
    }

    public void collectEarnings(ServerPlayer player) {
        int remaining = pendingEmeralds;
        pendingEmeralds = 0;
        while (remaining > 0) {
            int amount = Math.min(remaining, 64);
            ItemStack stack = new ItemStack(Items.EMERALD, amount);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= amount;
        }
        setChangedAndSync();
    }

    public void dropContents(Level level) {
        for (int slot = 0; slot < foods.getSlots(); slot++) {
            ItemStack stack = foods.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
        while (pendingEmeralds > 0) {
            int amount = Math.min(pendingEmeralds, 64);
            Containers.dropItemStack(
                    level,
                    worldPosition.getX(),
                    worldPosition.getY(),
                    worldPosition.getZ(),
                    new ItemStack(Items.EMERALD, amount)
            );
            pendingEmeralds -= amount;
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("creativetab.createrestaurant.restaurant_items");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RestaurantMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("RestaurantName", restaurantName);
        tag.put("Foods", foods.serializeNBT(registries));
        tag.putIntArray("Prices", prices);
        tag.putIntArray("CustomerHistory", customerHistory);
        tag.putBoolean("Running", running);
        tag.putInt("Rating", rating);
        tag.putInt("ActiveRows", activeRows);
        tag.putInt("CurrentCustomers", currentCustomers);
        tag.putInt("PendingEmeralds", pendingEmeralds);
        tag.putInt("CustomerTimer", customerTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Foods", Tag.TAG_COMPOUND)) {
            CompoundTag foodData = tag.getCompound("Foods").copy();

            // Prevent old saved block entities from shrinking the handler.
            foodData.putInt("Size", MAX_ROWS);

            foods.deserializeNBT(registries, foodData);
        }
        restaurantName = tag.getString("RestaurantName");
        if (restaurantName.isBlank()) {
            restaurantName = "My Restaurant";
        }
        foods.deserializeNBT(registries, tag.getCompound("Foods"));
        copyArray(tag.getIntArray("Prices"), prices, 1);
        copyArray(tag.getIntArray("CustomerHistory"), customerHistory, 0);
        running = tag.getBoolean("Running");
        rating = Mth.clamp(tag.getInt("Rating"), 0, 5);
        activeRows = Mth.clamp(tag.getInt("ActiveRows"), 2, MAX_ROWS);
        currentCustomers = Math.max(0, tag.getInt("CurrentCustomers"));
        pendingEmeralds = Math.max(0, tag.getInt("PendingEmeralds"));
        customerTimer = Mth.clamp(tag.getInt("CustomerTimer"), 0, CUSTOMER_INTERVAL);
    }

    private static void copyArray(int[] source, int[] destination, int defaultValue) {
        Arrays.fill(destination, defaultValue);
        System.arraycopy(source, 0, destination, 0, Math.min(source.length, destination.length));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public ItemStackHandler getFoods() {
        return foods;
    }

    public ContainerData getData() {
        return data;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public int getPrice(int row) {
        return row >= 0 && row < prices.length ? prices[row] : 1;
    }
}

package net.kixin.createrestaurant.client;

import net.kixin.createrestaurant.blockentity.MenuBlockEntity;
import net.kixin.createrestaurant.block.ModBlocks;
import net.kixin.createrestaurant.client.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public final class RestaurantMenu extends AbstractContainerMenu {
    private static final int PLAYER_SLOT_COUNT = 36;

    private final @Nullable MenuBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final ContainerData data;
    private boolean foodTabVisible = true;

    public RestaurantMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    private RestaurantMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, findBlockEntity(playerInventory, pos), pos);
    }

    public RestaurantMenu(int containerId, Inventory playerInventory, MenuBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity, blockEntity.getBlockPos());
    }

    private RestaurantMenu(
            int containerId,
            Inventory playerInventory,
            @Nullable MenuBlockEntity blockEntity,
            BlockPos pos
    ) {
        super(ModMenus.RESTAURANT_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.blockPos = pos;

        IItemHandler foods = blockEntity == null
                ? new ItemStackHandler(MenuBlockEntity.MAX_ROWS)
                : blockEntity.getFoods();
        this.data = blockEntity == null
                ? new SimpleContainerData(MenuBlockEntity.DATA_COUNT)
                : blockEntity.getData();

        checkContainerDataCount(this.data, MenuBlockEntity.DATA_COUNT);
        addDataSlots(this.data);

        for (int row = 0; row < MenuBlockEntity.MAX_ROWS; row++) {
            addSlot(new FoodSlot(foods, row, 36, 64 + row * 21, this));
        }

        addPlayerInventory(playerInventory);
    }

    private static @Nullable MenuBlockEntity findBlockEntity(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        return blockEntity instanceof MenuBlockEntity restaurant ? restaurant : null;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 43 + column * 18, 178 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 43 + column * 18, 236));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int foodSlots = MenuBlockEntity.MAX_ROWS;
        int allSlots = foodSlots + PLAYER_SLOT_COUNT;

        if (index < foodSlots) {
            if (!moveItemStackTo(stack, foodSlots, allSlots, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, getActiveRows(), false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.getLevel() == null) {
            return true;
        }
        return AbstractContainerMenu.stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockPos),
                player,
                ModBlocks.MENU_BLOCKS.get()
        );
    }

    public void setFoodTabVisible(boolean visible) {
        foodTabVisible = visible;
    }

    public boolean isFoodTabVisible() {
        return foodTabVisible;
    }

    public boolean isRunning() {
        return data.get(0) != 0;
    }

    public int getRating() {
        return data.get(1);
    }

    public int getActiveRows() {
        return data.get(2);
    }

    public int getCurrentCustomers() {
        return data.get(3);
    }

    public int getPendingEmeralds() {
        return data.get(4);
    }

    public int getCustomerHistory(int index) {
        return index >= 0 && index < 5 ? data.get(5 + index) : 0;
    }

    public String getRestaurantName() {
        return blockEntity == null ? "My Restaurant" : blockEntity.getRestaurantName();
    }

    public int getPrice(int row) {
        return blockEntity == null ? 1 : blockEntity.getPrice(row);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public @Nullable MenuBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private static final class FoodSlot extends SlotItemHandler {
        private final int row;
        private final RestaurantMenu menu;

        private FoodSlot(IItemHandler handler, int row, int x, int y, RestaurantMenu menu) {
            super(handler, row, x, y);
            this.row = row;
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return menu.isFoodTabVisible() && row < menu.getActiveRows();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return row < menu.getActiveRows() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return row < menu.getActiveRows() && super.mayPickup(player);
        }
    }
}

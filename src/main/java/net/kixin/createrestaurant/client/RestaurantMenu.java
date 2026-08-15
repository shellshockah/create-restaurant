package net.kixin.createrestaurant.client;

import net.kixin.createrestaurant.blockentity.MenuBlockEntity;
import net.kixin.createrestaurant.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;

import static net.kixin.createrestaurant.blockentity.MenuBlockEntity.MAX_ROWS;

public final class RestaurantMenu extends AbstractContainerMenu {
    private static final int PLAYER_SLOT_COUNT = 36;
    public static final int VISIBLE_ROWS = 4;
    private int foodPage;
    public static int FIRST_VIS_ROW = 0;

    private final boolean clientSideMenu;


    private final @Nullable MenuBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final ContainerData data;
    private boolean foodTabVisible = true;

    public RestaurantMenu( int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer ) {
        this( containerId, playerInventory, findBlockEntity(playerInventory, buffer.readBlockPos()), true);
    }

    public RestaurantMenu( int containerId, Inventory playerInventory, MenuBlockEntity blockEntity ) {
        this(containerId, playerInventory, blockEntity, false);
    }

    private RestaurantMenu(
            int containerId,
            Inventory playerInventory,
            @Nullable MenuBlockEntity blockEntity,
            boolean clientSideMenu
    ) {
        super(ModMenus.RESTAURANT_MENU.get(), containerId);

        this.blockEntity = blockEntity;
        this.blockPos = blockEntity == null
                ? BlockPos.ZERO
                : blockEntity.getBlockPos();
        this.clientSideMenu = clientSideMenu;

        IItemHandler foods = blockEntity == null
                ? new ItemStackHandler(MAX_ROWS)
                : blockEntity.getFoods();

        this.data = blockEntity == null
                ? new SimpleContainerData(MenuBlockEntity.DATA_COUNT)
                : blockEntity.getData();

        checkContainerDataCount(data, MenuBlockEntity.DATA_COUNT);
        addDataSlots(data);

        if (foods.getSlots() != MAX_ROWS) {
            throw new IllegalStateException(
                    "Expected " + MAX_ROWS
                            + " food slots, but found " + foods.getSlots()
            );
        }

        for (int row = 0; row < MAX_ROWS; row++) {
            int visiblePosition = row % VISIBLE_ROWS;

            addSlot(new FoodSlot(
                    foods,
                    row,
                    36,
                    64 + visiblePosition * 21,
                    this
            ));
        }

        addPlayerInventory(playerInventory);
    }

    private static @Nullable MenuBlockEntity findBlockEntity(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        return blockEntity instanceof MenuBlockEntity restaurant ? restaurant : null;
    }

    public int getFoodPage() {
        return foodPage;
    }

    public int getPageCount() {
        return Math.max(
                1,
                (getActiveRows() + VISIBLE_ROWS - 1) / VISIBLE_ROWS
        );
    }

    public int getFirstVisibleRow() {
        return FIRST_VIS_ROW;
    }

    public int getVisibleRowCount() {
        return Math.min(
                VISIBLE_ROWS,
                Math.max(0, getActiveRows() - getFirstVisibleRow())
        );
    }

    public void setFoodPage(int page) {
        foodPage = Mth.clamp(page, 0, getPageCount() - 1);
    }


    public void setFirstVisibleRow(int row) {
        int maxFirstRow = Math.max(0, MAX_ROWS - VISIBLE_ROWS);
        FIRST_VIS_ROW = Mth.clamp(row, 0, maxFirstRow);
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
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        int foodSlotCount = MAX_ROWS;
        int playerInventoryStart = foodSlotCount;
        int hotbarStart = playerInventoryStart + 27;
        int playerInventoryEnd = hotbarStart + 9;

        // Ghost food entries cannot be shift-clicked out.
        if (index < foodSlotCount) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        // Continue allowing shift-click movement between the player's main
        // inventory and hotbar, but never into the ghost food slots.
        if (index < hotbarStart) {
            if (!moveItemStackTo(
                    stack,
                    hotbarStart,
                    playerInventoryEnd,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(
                stack,
                playerInventoryStart,
                hotbarStart,
                false
        )) {
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

    @Override
    public void clicked(
            int slotId,
            int button,
            ClickType clickType,
            Player player
    ) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot clickedSlot = getSlot(slotId);

            if (clickedSlot instanceof FoodSlot foodSlot) {
                handleGhostSlotClick(foodSlot, clickType);
                return;
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    private void handleGhostSlotClick(
            FoodSlot foodSlot,
            ClickType clickType
    ) {
        // Ignore shift-clicking, dragging, number keys, and other operations.
        if (clickType != ClickType.PICKUP) {
            return;
        }

        ItemStack carriedStack = getCarried();

        // Clicking with an empty cursor clears the ghost slot without giving
        // the displayed item to the player.
        if (carriedStack.isEmpty()) {
            foodSlot.set(ItemStack.EMPTY);
            foodSlot.setChanged();
            return;
        }

        if (!foodSlot.mayPlace(carriedStack)) {
            return;
        }

        ItemStack displayedCopy = carriedStack.copy();
        displayedCopy.setCount(1);

        foodSlot.set(displayedCopy);
        foodSlot.setChanged();

        // Do not shrink carriedStack. The player keeps the real item.
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof FoodSlot);
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
            if (row >= menu.getActiveRows()) {
                return false;
            }

            // The server keeps every unlocked slot active.
            if (!menu.clientSideMenu) {
                return true;
            }

            int firstRow = menu.getFirstVisibleRow();

            return menu.isFoodTabVisible()
                    && row >= firstRow
                    && row < firstRow + RestaurantMenu.VISIBLE_ROWS;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return row < menu.getActiveRows() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}

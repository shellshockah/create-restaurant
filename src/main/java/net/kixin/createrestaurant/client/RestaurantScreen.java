package net.kixin.createrestaurant.client;

import net.kixin.createrestaurant.config.MarketPriceConfig;
import net.kixin.createrestaurant.network.ActionPayload;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.kixin.createrestaurant.blockentity.MenuBlockEntity.MAX_ROWS;
import static net.kixin.createrestaurant.client.RestaurantMenu.VISIBLE_ROWS;

public final class RestaurantScreen extends AbstractContainerScreen<RestaurantMenu> {
    private enum Tab { SUMMARY, FOOD }

    private static final int PANEL = 0xFF2B2520;
    private static final int PANEL_LIGHT = 0xFF4A4036;
    private static final int BORDER = 0xFFB8905B;
    private static final int TEXT = 0xFFF2E5C8;
    private static final int MUTED = 0xFFB9AA91;
    private static final int GOLD = 0xFFFFC642;
    private static final int SCROLL_X = 20;
    private static final int SCROLL_Y = 53;
    private static final int SCROLL_WIDTH = 8;
    private static final int SCROLL_HEIGHT = 82;
    private static final int MIN_THUMB_HEIGHT = 12;

    private boolean draggingScrollbar;

    private Tab tab = Tab.SUMMARY;
    private Button summaryTab;
    private Button foodTab;
    private Button runningSwitch;
    private Button addRowButton;
    private Button subRowButton;
    private Button collectButton;
    private EditBox restaurantName;
    private final EditBox[] priceBoxes = new EditBox[VISIBLE_ROWS];
    private final EditBox[] marketPriceBoxes = new EditBox[VISIBLE_ROWS];
    private boolean changingWidgetValues;

    public RestaurantScreen(RestaurantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 248;
        imageHeight = 250;
        titleLabelX = 10;
        titleLabelY = 7;
        inventoryLabelX = 43;
        inventoryLabelY = 155;
    }

    @Override
    protected void init() {
        super.init();
        summaryTab = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.summary"),
                button -> selectTab(Tab.SUMMARY)
        ).bounds(leftPos + 11, topPos + 19, 108, 20).build());

        foodTab = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.food"),
                button -> selectTab(Tab.FOOD)
        ).bounds(leftPos + 129, topPos + 19, 108, 20).build());

        restaurantName = addRenderableWidget(new EditBox(
                font,
                leftPos + 72,
                topPos + 47,
                157,
                18,
                Component.translatable("gui.create_restaurant.restaurant_name")
        ));
        restaurantName.setMaxLength(32);
        restaurantName.setValue(menu.getRestaurantName());
        restaurantName.setResponder(value -> {
            if (!changingWidgetValues) {
                PacketDistributor.sendToServer(new ActionPayload(
                        menu.getBlockPos(),
                        ActionPayload.SET_NAME,
                        0,
                        0,
                        value
                ));
            }
        });

        runningSwitch = addRenderableWidget(Button.builder(
                switchLabel(),
                button -> sendSimple(ActionPayload.TOGGLE_GAME)
        ).bounds(leftPos + 25, topPos + 133, 90, 17).build());
        collectButton = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> sendSimple(ActionPayload.COLLECT_EARNINGS)
        ).bounds(leftPos + 121, topPos + 133, 108, 17).build());

        for (int row = 0; row < priceBoxes.length; row++) {
            final int capturedRow = row;
            EditBox priceBox = addRenderableWidget(new EditBox(
                    font,
                    leftPos + 94,
                    topPos + 54 + row * 21,
                    45,
                    18,
                    Component.translatable("gui.create_restaurant.price")
            ));
            priceBox.setMaxLength(2);
            priceBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            priceBox.setValue(Integer.toString(menu.getPrice(row)));
            priceBox.setResponder(value -> {
                if (changingWidgetValues || value.isEmpty()) {
                    return;
                }
                try {
                    int price = Integer.parseInt(value);
                    int absoluteRow = menu.getFirstVisibleRow() + capturedRow;

                    PacketDistributor.sendToServer(new ActionPayload(
                            menu.getBlockPos(),
                            ActionPayload.SET_PRICE,
                            absoluteRow,
                            price,
                            ""
                    ));
                } catch (NumberFormatException ignored) {
                    // The filter and max length normally make this unreachable.
                }
            });
            priceBoxes[row] = priceBox;

            EditBox marketPriceBox = addRenderableWidget(new EditBox(
                    font,
                    leftPos + 174,
                    topPos + 54 + row * 21,
                    45,
                    18,
                    Component.translatable("gui.create_restaurant.market_price")
            ));
            marketPriceBox.setEditable(false);
            marketPriceBox.setValue(getMarketPriceText(row));
            marketPriceBoxes[row] = marketPriceBox;
        }

        addRowButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.add_row"),
                button -> sendSimple(ActionPayload.ADD_ROW)
        ).bounds(leftPos + 35, topPos + 136, 94, 16).build());
        subRowButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.sub_row"),
                button -> sendSimple(ActionPayload.SUB_ROW)
        ).bounds(leftPos + 136, topPos + 136, 93, 16).build());

        applyTabVisibility();
    }

    private void selectTab(Tab selected) {
        tab = selected;
        applyTabVisibility();
    }

    private void applyTabVisibility() {
        boolean summary = tab == Tab.SUMMARY;
        menu.setFoodTabVisible(!summary);
        summaryTab.active = !summary;
        foodTab.active = summary;
        restaurantName.visible = summary;
        runningSwitch.visible = summary;
        collectButton.visible = summary;
        addRowButton.visible = !summary;
        subRowButton.visible = !summary;

        for (int row = 0; row < priceBoxes.length; row++) {
            boolean rowVisible = !summary && row < menu.getVisibleRowCount();
            priceBoxes[row].visible = rowVisible;
            marketPriceBoxes[row].visible = rowVisible;
        }
    }

    private Component switchLabel() {
        return Component.translatable(menu.isRunning()
                ? "gui.create_restaurant.on"
                : "gui.create_restaurant.off");
    }

    private void sendSimple(int action) {
        PacketDistributor.sendToServer(ActionPayload.simple(menu.getBlockPos(), action));
    }

    private String getMarketPriceText(int absoluteRow) {
        if (absoluteRow < 0 || absoluteRow >= MAX_ROWS) {
            return "";
        }

        // RestaurantMenu adds all food slots before the player inventory slots,
        // so the absolute food row is also its menu slot index.
        ItemStack food = menu.getSlot(absoluteRow).getItem();
        if (food.isEmpty()) {
            return "";
        }

        int marketPrice = MarketPriceConfig.getMarketPrice(food);
        return marketPrice >= 0 ? Integer.toString(marketPrice) : "—";
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        runningSwitch.setMessage(switchLabel());
        collectButton.setMessage(Component.translatable(
                "gui.create_restaurant.collect",
                menu.getPendingEmeralds()
        ));
        collectButton.active = menu.getPendingEmeralds() > 0;
        addRowButton.active = menu.getActiveRows() < MAX_ROWS;
        subRowButton.active = menu.getActiveRows() > 1;

        // If rows were removed while scrolled down, move back to the new end.
        if (menu.getFirstVisibleRow() > getMaxFirstVisibleRow()) {
            menu.setFirstVisibleRow(getMaxFirstVisibleRow());
        }

        changingWidgetValues = true;
        if (!restaurantName.isFocused() && !restaurantName.getValue().equals(menu.getRestaurantName())) {
            restaurantName.setValue(menu.getRestaurantName());
        }
        for (int visibleRow = 0; visibleRow < priceBoxes.length; visibleRow++) {
            int absoluteRow = menu.getFirstVisibleRow() + visibleRow;
            EditBox priceBox = priceBoxes[visibleRow];
            EditBox marketPriceBox = marketPriceBoxes[visibleRow];

            priceBox.visible = tab == Tab.FOOD
                    && visibleRow < menu.getVisibleRowCount();
            marketPriceBox.visible = priceBox.visible;

            if (priceBox.visible && !priceBox.isFocused()) {
                String serverPrice = Integer.toString(
                        menu.getPrice(absoluteRow)
                );

                if (!priceBox.getValue().equals(serverPrice)) {
                    priceBox.setValue(serverPrice);
                }
            }

            if (marketPriceBox.visible) {
                String marketPrice = getMarketPriceText(absoluteRow);
                if (!marketPriceBox.getValue().equals(marketPrice)) {
                    marketPriceBox.setValue(marketPrice);
                }
            }
        }
        changingWidgetValues = false;
        applyTabVisibility();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF181512);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, PANEL);
        outline(graphics, leftPos + 6, topPos + 6, imageWidth - 12, imageHeight - 12, BORDER);

        if (tab == Tab.SUMMARY) {
            renderSummaryBackground(graphics);
        } else {
            renderFoodBackground(graphics);
        }
        renderPlayerInventoryBackground(graphics);
    }

    private void renderSummaryBackground(GuiGraphics graphics) {
        graphics.fill(leftPos + 17, topPos + 42, leftPos + 231, topPos + 153, PANEL_LIGHT);
        outline(graphics, leftPos + 17, topPos + 42, 214, 111, BORDER);

        int graphLeft = leftPos + 25;
        int graphTop = topPos + 95;
        int graphWidth = 198;
        int graphHeight = 24;
        graphics.fill(graphLeft, graphTop, graphLeft + graphWidth, graphTop + graphHeight, 0xFF211D19);
        graphics.hLine(graphLeft, graphLeft + graphWidth, graphTop + graphHeight, BORDER);
        graphics.vLine(graphLeft, graphTop, graphTop + graphHeight, BORDER);

        int max = 1;
        for (int i = 0; i < 5; i++) {
            max = Math.max(max, menu.getCustomerHistory(i));
        }
        for (int i = 0; i < 5; i++) {
            int value = menu.getCustomerHistory(i);
            int barHeight = Math.round((graphHeight - 4) * (value / (float) max));
            int x = graphLeft + 12 + i * 37;
            graphics.fill(x, graphTop + graphHeight - barHeight, x + 17, graphTop + graphHeight, 0xFF4BA3A8);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int x = leftPos + SCROLL_X;
        int y = topPos + SCROLL_Y;

        // Track
        graphics.fill(
                x,
                y,
                x + SCROLL_WIDTH,
                y + SCROLL_HEIGHT,
                0xFF181512
        );

        int maxFirstVisibleRow = getMaxFirstVisibleRow();

        if (maxFirstVisibleRow == 0) {
            graphics.fill(
                    x + 1,
                    y + 1,
                    x + SCROLL_WIDTH - 1,
                    y + SCROLL_HEIGHT - 1,
                    0xFF655B4E
            );
            return;
        }

        int thumbHeight = getScrollbarThumbHeight();
        int availableTravel = SCROLL_HEIGHT - thumbHeight;

        int thumbY = y + Math.round(
                availableTravel
                        * (menu.getFirstVisibleRow() / (float) maxFirstVisibleRow)
        );

        graphics.fill(
                x + 1,
                thumbY,
                x + SCROLL_WIDTH - 1,
                thumbY + thumbHeight,
                BORDER
        );
    }

    private int getScrollbarThumbHeight() {
        int activeRows = Math.max(1, menu.getActiveRows());
        int visibleRows = Math.min(VISIBLE_ROWS, activeRows);

        return Mth.clamp(
                Math.round(SCROLL_HEIGHT * (visibleRows / (float) activeRows)),
                MIN_THUMB_HEIGHT,
                SCROLL_HEIGHT
        );
    }

    private int getMaxFirstVisibleRow() {
        return Math.max(0, menu.getActiveRows() - VISIBLE_ROWS);
    }

    private void renderFoodBackground(GuiGraphics graphics) {
        for (int row = 0; row < menu.getVisibleRowCount(); row++) {
            int y = topPos + 53 + row * 21;

            graphics.fill(
                    leftPos + 35,
                    y,
                    leftPos + 54,
                    y + 19,
                    0xFF181512
            );

            outline(graphics, leftPos + 35, y, 19, 19, BORDER);
        }
        renderScrollbar(graphics);
    }

    private void updateScrollbarFromMouse(double mouseY) {
        int maxFirstVisibleRow = getMaxFirstVisibleRow();
        if (maxFirstVisibleRow == 0) {
            setFirstVisibleFoodRow(0);
            return;
        }

        int thumbHeight = getScrollbarThumbHeight();
        int travel = SCROLL_HEIGHT - thumbHeight;

        double relativeY =
                mouseY
                        - (topPos + SCROLL_Y)
                        - thumbHeight / 2.0;

        double percentage = Mth.clamp(relativeY / travel, 0.0, 1.0);

        int firstVisibleRow = (int) Math.round(
                percentage * maxFirstVisibleRow
        );

        setFirstVisibleFoodRow(firstVisibleRow);
    }

    private void setFirstVisibleFoodRow(int row) {
        for (EditBox priceBox : priceBoxes) {
            priceBox.setFocused(false);
        }

        menu.setFirstVisibleRow(Mth.clamp(row, 0, getMaxFirstVisibleRow()));

        changingWidgetValues = true;

        for (int visibleRow = 0;
             visibleRow < priceBoxes.length;
             visibleRow++) {

            int absoluteRow =
                    menu.getFirstVisibleRow() + visibleRow;

            priceBoxes[visibleRow].setValue(
                    Integer.toString(menu.getPrice(absoluteRow))
            );
            marketPriceBoxes[visibleRow].setValue(
                    getMarketPriceText(absoluteRow)
            );
        }

        changingWidgetValues = false;
        applyTabVisibility();
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        int x = leftPos + SCROLL_X;
        int y = topPos + SCROLL_Y;

        return mouseX >= x
                && mouseX < x + SCROLL_WIDTH
                && mouseY >= y
                && mouseY < y + SCROLL_HEIGHT;
    }

    private boolean isMouseOverFoodArea(double mouseX, double mouseY) {
        return mouseX >= leftPos + 16
                && mouseX < leftPos + 231
                && mouseY >= topPos + 40
                && mouseY < topPos + 153;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (tab == Tab.FOOD
                && scrollY != 0.0
                && isMouseOverFoodArea(mouseX, mouseY)) {
            int rowChange = scrollY > 0.0 ? -1 : 1;

            setFirstVisibleFoodRow(
                    menu.getFirstVisibleRow() + rowChange
            );

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && tab == Tab.FOOD
                && isMouseOverScrollbar(mouseX, mouseY)
                && getMaxFirstVisibleRow() > 0) {

            draggingScrollbar = true;
            updateScrollbarFromMouse(mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (button == 0 && draggingScrollbar) {
            updateScrollbarFromMouse(mouseY);
            return true;
        }

        return super.mouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderPlayerInventoryBackground(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, leftPos + 42 + column * 18, topPos + 166 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, leftPos + 42 + column * 18, topPos + 224);
        }
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF151210);
        outline(graphics, x, y, 18, 18, 0xFF73624E);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.hLine(x, x + width - 1, y, color);
        graphics.hLine(x, x + width - 1, y + height - 1, color);
        graphics.vLine(x, y, y + height - 1, color);
        graphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);

        if (tab == Tab.SUMMARY) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.restaurant_name"),
                    25,
                    52,
                    TEXT,
                    false
            );
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.rating"),
                    25,
                    72,
                    TEXT,
                    false
            );
            for (int star = 0; star < 5; star++) {
                graphics.drawString(font, "★", 76 + star * 14, 70, star < menu.getRating() ? GOLD : 0xFF655B4E, false);
            }
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.last_five_games"),
                    25,
                    84,
                    MUTED,
                    false
            );
            for (int i = 0; i < 5; i++) {
                graphics.drawCenteredString(font, Integer.toString(menu.getCustomerHistory(i)), 45 + i * 37, 109, TEXT);
            }
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.current_customers", menu.getCurrentCustomers()),
                    25,
                    121,
                    TEXT,
                    false
            );
        } else {
            graphics.drawString(font, Component.translatable("gui.create_restaurant.food_item"), 25, 42, TEXT, false);
            graphics.drawString(font, Component.translatable("gui.create_restaurant.emerald_price"), 79, 42, TEXT, false);
            graphics.drawString(font, Component.translatable("gui.create_restaurant.market_price"), 164, 42, TEXT, false);
            for (int row = 0; row < menu.getVisibleRowCount(); row++) {
                graphics.drawString(font, "×", 79, 59 + row * 21, MUTED, false);
                graphics.drawString(font, "♦", 86, 59 + row * 21, 0xFF55E27A, false);
                graphics.drawString(font, "♦", 165, 59 + row * 21, 0xFF55E27A, false);
            }
        }
    }
}

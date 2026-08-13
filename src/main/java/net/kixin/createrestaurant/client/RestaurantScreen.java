package net.kixin.createrestaurant.client;

import net.kixin.createrestaurant.blockentity.MenuBlockEntity;
import net.kixin.createrestaurant.client.RestaurantMenu;
import net.kixin.createrestaurant.network.ActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RestaurantScreen extends AbstractContainerScreen<RestaurantMenu> {
    private enum Tab { SUMMARY, FOOD }

    private static final int PANEL = 0xFF2B2520;
    private static final int PANEL_LIGHT = 0xFF4A4036;
    private static final int BORDER = 0xFFB8905B;
    private static final int TEXT = 0xFFF2E5C8;
    private static final int MUTED = 0xFFB9AA91;
    private static final int GOLD = 0xFFFFC642;

    private Tab tab = Tab.SUMMARY;
    private Button summaryTab;
    private Button foodTab;
    private Button runningSwitch;
    private Button addRowButton;
    private Button collectButton;
    private EditBox restaurantName;
    private final EditBox[] priceBoxes = new EditBox[MenuBlockEntity.MAX_ROWS];
    private boolean changingWidgetValues;

    public RestaurantScreen(RestaurantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 248;
        imageHeight = 266;
        titleLabelX = 10;
        titleLabelY = 10;
        inventoryLabelX = 43;
        inventoryLabelY = 166;
    }

    @Override
    protected void init() {
        super.init();

        runningSwitch = addRenderableWidget(Button.builder(
                switchLabel(),
                button -> sendSimple(ActionPayload.TOGGLE_GAME)
        ).bounds(leftPos + 169, topPos + 7, 68, 20).build());

        summaryTab = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.summary"),
                button -> selectTab(Tab.SUMMARY)
        ).bounds(leftPos + 11, topPos + 34, 108, 20).build());

        foodTab = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.food"),
                button -> selectTab(Tab.FOOD)
        ).bounds(leftPos + 129, topPos + 34, 108, 20).build());

        restaurantName = addRenderableWidget(new EditBox(
                font,
                leftPos + 72,
                topPos + 62,
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

        collectButton = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> sendSimple(ActionPayload.COLLECT_EARNINGS)
        ).bounds(leftPos + 68, topPos + 142, 112, 20).build());

        for (int row = 0; row < priceBoxes.length; row++) {
            final int capturedRow = row;
            EditBox priceBox = addRenderableWidget(new EditBox(
                    font,
                    leftPos + 117,
                    topPos + 64 + row * 21,
                    48,
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
                    PacketDistributor.sendToServer(new ActionPayload(
                            menu.getBlockPos(),
                            ActionPayload.SET_PRICE,
                            capturedRow,
                            price,
                            ""
                    ));
                } catch (NumberFormatException ignored) {
                    // The filter and max length normally make this unreachable.
                }
            });
            priceBoxes[row] = priceBox;
        }

        addRowButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.create_restaurant.add_row"),
                button -> sendSimple(ActionPayload.ADD_ROW)
        ).bounds(leftPos + 174, topPos + 64, 55, 18).build());

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
        collectButton.visible = summary;
        addRowButton.visible = !summary;

        for (int row = 0; row < priceBoxes.length; row++) {
            priceBoxes[row].visible = !summary && row < menu.getActiveRows();
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

    @Override
    protected void containerTick() {
        super.containerTick();
        runningSwitch.setMessage(switchLabel());
        collectButton.setMessage(Component.translatable(
                "gui.create_restaurant.collect",
                menu.getPendingEmeralds()
        ));
        collectButton.active = menu.getPendingEmeralds() > 0;
        addRowButton.active = menu.getActiveRows() < MenuBlockEntity.MAX_ROWS;

        changingWidgetValues = true;
        if (!restaurantName.isFocused() && !restaurantName.getValue().equals(menu.getRestaurantName())) {
            restaurantName.setValue(menu.getRestaurantName());
        }
        for (int row = 0; row < priceBoxes.length; row++) {
            EditBox priceBox = priceBoxes[row];
            String serverPrice = Integer.toString(menu.getPrice(row));
            if (!priceBox.isFocused() && !priceBox.getValue().equals(serverPrice)) {
                priceBox.setValue(serverPrice);
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
        graphics.fill(leftPos + 17, topPos + 58, leftPos + 231, topPos + 164, PANEL_LIGHT);
        outline(graphics, leftPos + 17, topPos + 58, 214, 106, BORDER);

        int graphLeft = leftPos + 25;
        int graphTop = topPos + 103;
        int graphWidth = 198;
        int graphHeight = 35;
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

    private void renderFoodBackground(GuiGraphics graphics) {
        for (int row = 0; row < menu.getActiveRows(); row++) {
            int y = topPos + 63 + row * 21;
            graphics.fill(leftPos + 35, y, leftPos + 54, y + 19, 0xFF181512);
            outline(graphics, leftPos + 35, y, 19, 19, BORDER);
        }
    }

    private void renderPlayerInventoryBackground(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, leftPos + 42 + column * 18, topPos + 177 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, leftPos + 42 + column * 18, topPos + 235);
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
                    67,
                    TEXT,
                    false
            );
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.rating"),
                    25,
                    86,
                    TEXT,
                    false
            );
            for (int star = 0; star < 5; star++) {
                graphics.drawString(font, "★", 76 + star * 14, 84, star < menu.getRating() ? GOLD : 0xFF655B4E, false);
            }
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.last_five_games"),
                    25,
                    93,
                    MUTED,
                    false
            );
            for (int i = 0; i < 5; i++) {
                graphics.drawCenteredString(font, Integer.toString(menu.getCustomerHistory(i)), 45 + i * 37, 128, TEXT);
            }
            graphics.drawString(
                    font,
                    Component.translatable("gui.create_restaurant.current_customers", menu.getCurrentCustomers()),
                    25,
                    148,
                    TEXT,
                    false
            );
        } else {
            graphics.drawString(font, Component.translatable("gui.create_restaurant.food_item"), 25, 54, TEXT, false);
            graphics.drawString(font, Component.translatable("gui.create_restaurant.emerald_price"), 93, 54, TEXT, false);
            for (int row = 0; row < menu.getActiveRows(); row++) {
                graphics.drawString(font, "×", 101, 69 + row * 21, MUTED, false);
                graphics.drawString(font, "♦", 108, 69 + row * 21, 0xFF55E27A, false);
            }
        }
    }
}

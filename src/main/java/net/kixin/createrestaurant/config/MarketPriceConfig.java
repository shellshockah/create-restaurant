package net.kixin.createrestaurant.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-synced market prices for vanilla Minecraft foods.
 *
 * Register {@link #SPEC} as a SERVER config in the mod constructor. NeoForge
 * will then create create_restaurant-market-prices.toml in the world's
 * serverconfig directory and synchronize it to connected clients.
 */
public final class MarketPriceConfig {
    public static final MarketPriceConfig VALUES;
    public static final ModConfigSpec SPEC;

    static {
        Pair<MarketPriceConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(MarketPriceConfig::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private final Map<String, ModConfigSpec.IntValue> marketPrices =
            new LinkedHashMap<>();

    private MarketPriceConfig(ModConfigSpec.Builder builder) {
        builder.comment(
                "Item used as currency. Default is emeralds"
        ).push("currency");

        addCurrency(builder, "minecraft:emerald");
        builder.comment(
                "Base market prices, measured in emeralds, for vanilla Minecraft foods.",
                "A value of 0 makes the food's market price free. Valid range: 0-64."
        ).push("market_prices");

        add(builder, "apple", 2);
        add(builder, "baked_potato", 2);
        add(builder, "beef", 2);
        add(builder, "beetroot", 1);
        add(builder, "beetroot_soup", 3);
        add(builder, "bread", 2);
        add(builder, "cake", 6);
        add(builder, "carrot", 1);
        add(builder, "chicken", 1);
        add(builder, "chorus_fruit", 2);
        add(builder, "cod", 1);
        add(builder, "cooked_beef", 3);
        add(builder, "cooked_chicken", 2);
        add(builder, "cooked_cod", 2);
        add(builder, "cooked_mutton", 3);
        add(builder, "cooked_porkchop", 3);
        add(builder, "cooked_rabbit", 2);
        add(builder, "cooked_salmon", 2);
        add(builder, "cookie", 1);
        add(builder, "dried_kelp", 1);
        add(builder, "enchanted_golden_apple", 32);
        add(builder, "glow_berries", 1);
        add(builder, "golden_apple", 12);
        add(builder, "golden_carrot", 6);
        add(builder, "honey_bottle", 2);
        add(builder, "melon_slice", 1);
        add(builder, "mushroom_stew", 3);
        add(builder, "mutton", 2);
        add(builder, "poisonous_potato", 0);
        add(builder, "porkchop", 2);
        add(builder, "potato", 1);
        add(builder, "pufferfish", 0);
        add(builder, "pumpkin_pie", 3);
        add(builder, "rabbit", 1);
        add(builder, "rabbit_stew", 4);
        add(builder, "rotten_flesh", 0);
        add(builder, "salmon", 1);
        add(builder, "spider_eye", 0);
        add(builder, "suspicious_stew", 3);
        add(builder, "sweet_berries", 1);
        add(builder, "tropical_fish", 1);

        builder.pop();
    }

    private void add(
            ModConfigSpec.Builder builder,
            String itemPath,
            int defaultPrice
    ) {
        String itemId = "minecraft:" + itemPath;
        marketPrices.put(
                itemId,
                builder.comment("Market price for " + itemId + ".")
                        .defineInRange(itemPath, defaultPrice, 0, 64)
        );
    }

    private void addCurrency(
            ModConfigSpec.Builder builder,
            String currencyId
    ) {

    }

    /**
     * @return configured emerald price, or -1 when the item has no entry
     */
    public static int getMarketPrice(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ModConfigSpec.IntValue value = VALUES.marketPrices.get(itemId.toString());
        return value == null ? -1 : value.get();
    }

    private MarketPriceConfig() {
    }
}
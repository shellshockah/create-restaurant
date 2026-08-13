package net.kixin.createrestaurant.item;

import net.kixin.createrestaurant.block.ModBlocks;
import net.kixin.createrestaurant.CreateRestaurant;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateRestaurant.MODID);

    public static final DeferredItem<BlockItem> RESTAURANT_MENU =
            ITEMS.registerSimpleBlockItem(ModBlocks.MENU_BLOCKS);

    private ModItems() {}
}

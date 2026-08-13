package net.kixin.createrestaurant.block;

import net.kixin.createrestaurant.CreateRestaurant;
import net.kixin.createrestaurant.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateRestaurant.MODID);

    public static final DeferredBlock<MenuBlock> MENU_BLOCKS = BLOCKS.register(
            "restaurant_menu",
            () -> new MenuBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).noOcclusion())
    );

    private ModBlocks() {}
}

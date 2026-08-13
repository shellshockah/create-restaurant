package net.kixin.createrestaurant.blockentity;

import net.kixin.createrestaurant.block.ModBlocks;
import net.kixin.createrestaurant.CreateRestaurant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateRestaurant.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MenuBlockEntity>> MENU_ENTITIES =
            BLOCK_ENTITY_TYPES.register(
                    "crest_menu",
                    () -> BlockEntityType.Builder.of(
                            MenuBlockEntity::new,
                            ModBlocks.MENU_BLOCKS.get()
                    ).build(null)
            );

    private ModBlockEntities() {}
}
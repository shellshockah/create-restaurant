package net.kixin.createrestaurant.client;

import net.kixin.createrestaurant.CreateRestaurant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreateRestaurant.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RestaurantMenu>> RESTAURANT_MENU =
            MENUS.register("crest_menu", () -> IMenuTypeExtension.create(RestaurantMenu::new));

    private ModMenus() {}
}

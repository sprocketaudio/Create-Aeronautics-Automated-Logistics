package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.LogisticsTerminalMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreateAeronauticsAutomatedLogistics.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<AirshipStationMenu>> AIRSHIP_STATION =
            MENUS.register("airship_station", () -> IMenuTypeExtension.create(AirshipStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AirshipScheduleMenu>> AIRSHIP_SCHEDULE =
            MENUS.register("airship_schedule", () -> IMenuTypeExtension.create(AirshipScheduleMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ShipTransponderMenu>> SHIP_TRANSPONDER =
            MENUS.register("ship_transponder", () -> IMenuTypeExtension.create(ShipTransponderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LogisticsTerminalMenu>> LOGISTICS_TERMINAL =
            MENUS.register("logistics_terminal", () -> IMenuTypeExtension.create(LogisticsTerminalMenu::new));

    private ModMenus() {
    }
}

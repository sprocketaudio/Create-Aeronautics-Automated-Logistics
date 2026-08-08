package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.item.LogisticsBlockItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateAeronauticsAutomatedLogistics.MOD_ID);

    public static final DeferredItem<BlockItem> AIRSHIP_STATION = ITEMS.register(
            "airship_station",
            () -> new LogisticsBlockItem(
                    ModBlocks.AIRSHIP_STATION.get(),
                    new Item.Properties(),
                    "block.create_aeronautics_automated_logistics.airship_station.tooltip"
            )
    );

    public static final DeferredItem<BlockItem> TRAIN_STATION = ITEMS.register(
            "train_station",
            () -> new LogisticsBlockItem(
                    ModBlocks.TRAIN_STATION.get(),
                    new Item.Properties(),
                    "block.create_aeronautics_automated_logistics.train_station.tooltip"
            )
    );

    public static final DeferredItem<BlockItem> SHIP_TRANSPONDER = ITEMS.register(
            "ship_transponder",
            () -> new LogisticsBlockItem(
                    ModBlocks.SHIP_TRANSPONDER.get(),
                    new Item.Properties(),
                    "block.create_aeronautics_automated_logistics.ship_transponder.tooltip"
            )
    );

    public static final DeferredItem<BlockItem> LOGISTICS_TERMINAL = ITEMS.register(
            "logistics_terminal",
            () -> new LogisticsBlockItem(
                    ModBlocks.LOGISTICS_TERMINAL.get(),
                    new Item.Properties(),
                    "block.create_aeronautics_automated_logistics.logistics_terminal.tooltip"
            )
    );

    public static final DeferredItem<BlockItem> DRIVE_MODULE = ITEMS.register(
            "drive_module",
            () -> new LogisticsBlockItem(
                    ModBlocks.DRIVE_MODULE.get(),
                    new Item.Properties(),
                    "block.create_aeronautics_automated_logistics.drive_module.tooltip"
            )
    );

    private ModItems() {
    }
}

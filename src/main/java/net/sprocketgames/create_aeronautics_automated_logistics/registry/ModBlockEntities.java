package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.LogisticsTerminalBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateAeronauticsAutomatedLogistics.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirshipStationBlockEntity>> AIRSHIP_STATION =
            BLOCK_ENTITY_TYPES.register(
                    "airship_station",
                    () -> BlockEntityType.Builder.of(
                            AirshipStationBlockEntity::new,
                            ModBlocks.AIRSHIP_STATION.get(),
                            ModBlocks.TRAIN_STATION.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipTransponderBlockEntity>> SHIP_TRANSPONDER =
            BLOCK_ENTITY_TYPES.register(
                    "ship_transponder",
                    () -> BlockEntityType.Builder.of(ShipTransponderBlockEntity::new, ModBlocks.SHIP_TRANSPONDER.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedTransponderBlockEntity>> ADVANCED_TRANSPONDER =
            BLOCK_ENTITY_TYPES.register(
                    "advanced_transponder",
                    () -> BlockEntityType.Builder.of(AdvancedTransponderBlockEntity::new, ModBlocks.ADVANCED_TRANSPONDER.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveModuleBlockEntity>> DRIVE_MODULE =
            BLOCK_ENTITY_TYPES.register(
                    "drive_module",
                    () -> BlockEntityType.Builder.of(DriveModuleBlockEntity::new, ModBlocks.DRIVE_MODULE.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticsTerminalBlockEntity>> LOGISTICS_TERMINAL =
            BLOCK_ENTITY_TYPES.register(
                    "logistics_terminal",
                    () -> BlockEntityType.Builder.of(LogisticsTerminalBlockEntity::new, ModBlocks.LOGISTICS_TERMINAL.get()).build(null)
            );

    private ModBlockEntities() {
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AdvancedTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AirshipStationBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.LogisticsTerminalBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.TrainStationBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateAeronauticsAutomatedLogistics.MOD_ID);

    public static final DeferredBlock<AirshipStationBlock> AIRSHIP_STATION = BLOCKS.register(
            "airship_station",
            () -> new AirshipStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );

    public static final DeferredBlock<TrainStationBlock> TRAIN_STATION = BLOCKS.register(
            "train_station",
            () -> new TrainStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );

    public static final DeferredBlock<ShipTransponderBlock> SHIP_TRANSPONDER = BLOCKS.register(
            "ship_transponder",
            () -> new ShipTransponderBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );

    public static final DeferredBlock<LogisticsTerminalBlock> LOGISTICS_TERMINAL = BLOCKS.register(
            "logistics_terminal",
            () -> new LogisticsTerminalBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );

    public static final DeferredBlock<AdvancedTransponderBlock> ADVANCED_TRANSPONDER = BLOCKS.register(
            "advanced_transponder",
            () -> new AdvancedTransponderBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );

    private ModBlocks() {
    }
}

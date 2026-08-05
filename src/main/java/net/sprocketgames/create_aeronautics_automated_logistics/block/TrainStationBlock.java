package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

public class TrainStationBlock extends AirshipStationBlock {
    public static final MapCodec<TrainStationBlock> CODEC = simpleCodec(TrainStationBlock::new);

    public TrainStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public TransportMode transportMode() {
        return TransportMode.TRAIN;
    }
}

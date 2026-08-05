package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;

public class AdvancedTransponderBlock extends ShipTransponderBlock {
    public static final MapCodec<AdvancedTransponderBlock> CODEC = simpleCodec(AdvancedTransponderBlock::new);

    public enum OutputPort {
        DOCK,
        LIFT,
        NORTH_SOUTH,
        EAST_WEST,
        NONE
    }

    public AdvancedTransponderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedTransponderBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.ADVANCED_TRANSPONDER.get(),
                AdvancedTransponderBlockEntity::serverTick
        );
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof AdvancedTransponderBlockEntity transponder)) {
            return 0;
        }
        return transponder.outputSignal(portForFace(state, direction));
    }

    public static OutputPort portForFace(BlockState state, Direction face) {
        if (face.getAxis().isVertical()) {
            return OutputPort.NONE;
        }
        Direction front = state.getValue(FACING);
        if (face == front) {
            return OutputPort.DOCK;
        }
        if (face == front.getOpposite()) {
            return OutputPort.LIFT;
        }
        if (face == front.getCounterClockWise()) {
            return OutputPort.NORTH_SOUTH;
        }
        if (face == front.getClockWise()) {
            return OutputPort.EAST_WEST;
        }
        return OutputPort.NONE;
    }
}

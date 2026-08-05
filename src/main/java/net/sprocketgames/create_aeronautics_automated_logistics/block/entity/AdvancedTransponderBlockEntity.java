package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AdvancedTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;

public class AdvancedTransponderBlockEntity extends ShipTransponderBlockEntity {
    private static final double LIFT_SIGNAL_PER_BLOCK = 2.0D;
    private static final double HORIZONTAL_FULL_DEMAND_DISTANCE = 2.0D;
    private static final double HORIZONTAL_DEADBAND = 0.05D;

    private int liftOutput;
    private int northSouthOutput;
    private int eastWestOutput;
    private boolean forcedOutputsActive;
    private boolean forcedDockOutput;
    private int forcedLiftOutput;
    private int forcedNorthSouthOutput;
    private int forcedEastWestOutput;
    private int forcedOutputTicksRemaining;

    public AdvancedTransponderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ADVANCED_TRANSPONDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AdvancedTransponderBlockEntity transponder) {
        ShipTransponderBlockEntity.serverTick(level, pos, state, transponder);
        transponder.tickForcedOutputs();
    }

    @Override
    protected Block controllerBlock() {
        return ModBlocks.ADVANCED_TRANSPONDER.get();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.advanced_transponder.title");
    }

    @Override
    public void setRuntimeStatus(RouteStatus runtimeStatus) {
        super.setRuntimeStatus(runtimeStatus);
        if (runtimeStatus != RouteStatus.RUNNING) {
            clearDriveOutputs();
        }
    }

    public int liftOutput() {
        return liftOutput;
    }

    public int northSouthOutput() {
        return northSouthOutput;
    }

    public int eastWestOutput() {
        return eastWestOutput;
    }

    public int outputSignal(AdvancedTransponderBlock.OutputPort port) {
        if (forcedOutputsActive) {
            return switch (port) {
                case DOCK -> forcedDockOutput ? 15 : 0;
                case LIFT -> forcedLiftOutput;
                case NORTH_SOUTH -> forcedNorthSouthOutput;
                case EAST_WEST -> forcedEastWestOutput;
                case NONE -> 0;
            };
        }
        return switch (port) {
            case DOCK -> dockOutputActive() ? 15 : 0;
            case LIFT -> liftOutput;
            case NORTH_SOUTH -> northSouthOutput;
            case EAST_WEST -> eastWestOutput;
            case NONE -> 0;
        };
    }

    public boolean forcedOutputsActive() {
        return forcedOutputsActive;
    }

    public int forcedOutputTicksRemaining() {
        return forcedOutputTicksRemaining;
    }

    public void forceSingleOutput(AdvancedTransponderBlock.OutputPort port, int signal, int ticks) {
        signal = clamp(signal, 0, 15);
        ticks = Math.max(1, ticks);
        forcedOutputsActive = true;
        forcedDockOutput = port == AdvancedTransponderBlock.OutputPort.DOCK && signal > 0;
        forcedLiftOutput = port == AdvancedTransponderBlock.OutputPort.LIFT ? signal : 0;
        forcedNorthSouthOutput = port == AdvancedTransponderBlock.OutputPort.NORTH_SOUTH ? signal : 0;
        forcedEastWestOutput = port == AdvancedTransponderBlock.OutputPort.EAST_WEST ? signal : 0;
        forcedOutputTicksRemaining = ticks;
        setChanged();
        notifyRedstoneNeighbors();
        syncClientState();
    }

    public void clearForcedOutputs() {
        if (!forcedOutputsActive) {
            return;
        }
        forcedOutputsActive = false;
        forcedDockOutput = false;
        forcedLiftOutput = 0;
        forcedNorthSouthOutput = 0;
        forcedEastWestOutput = 0;
        forcedOutputTicksRemaining = 0;
        setChanged();
        notifyRedstoneNeighbors();
        syncClientState();
    }

    public void updateDriveOutputs(Vec3 currentPosition, Vec3 guidancePosition) {
        if (forcedOutputsActive) {
            return;
        }
        int nextLift = liftDemand(guidancePosition.y - currentPosition.y);
        int nextNorthSouth = splitAxisDemand(guidancePosition.z - currentPosition.z);
        int nextEastWest = splitAxisDemand(guidancePosition.x - currentPosition.x);
        setDriveOutputs(nextLift, nextNorthSouth, nextEastWest);
    }

    public void clearDriveOutputs() {
        if (forcedOutputsActive) {
            return;
        }
        setDriveOutputs(0, 0, 0);
    }

    private void tickForcedOutputs() {
        if (!forcedOutputsActive || forcedOutputTicksRemaining <= 0) {
            return;
        }
        forcedOutputTicksRemaining--;
        if (forcedOutputTicksRemaining > 0) {
            return;
        }
        clearForcedOutputs();
    }

    private void setDriveOutputs(int lift, int northSouth, int eastWest) {
        lift = clamp(lift, 0, 15);
        northSouth = clamp(northSouth, 0, 15);
        eastWest = clamp(eastWest, 0, 15);
        if (liftOutput == lift && northSouthOutput == northSouth && eastWestOutput == eastWest) {
            return;
        }
        liftOutput = lift;
        northSouthOutput = northSouth;
        eastWestOutput = eastWest;
        setChanged();
        notifyRedstoneNeighbors();
        syncClientState();
    }

    private static int liftDemand(double verticalError) {
        return clamp((int) Math.round(8.0D + verticalError * LIFT_SIGNAL_PER_BLOCK), 0, 15);
    }

    private static int splitAxisDemand(double axisError) {
        double magnitude = Math.abs(axisError);
        if (magnitude < HORIZONTAL_DEADBAND) {
            return 0;
        }
        if (axisError < 0.0D) {
            return clamp((int) Math.ceil(magnitude / HORIZONTAL_FULL_DEMAND_DISTANCE * 7.0D), 1, 7);
        }
        return 7 + clamp((int) Math.ceil(magnitude / HORIZONTAL_FULL_DEMAND_DISTANCE * 8.0D), 1, 8);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

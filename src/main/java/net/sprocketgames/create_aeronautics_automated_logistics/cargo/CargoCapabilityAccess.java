package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

final class CargoCapabilityAccess {
    private static final @Nullable Direction[] SIDES = new Direction[] {
            null,
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private CargoCapabilityAccess() {
    }

    static @Nullable IItemHandler findItemHandler(Level level, BlockPos pos) {
        for (Direction side : SIDES) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    static @Nullable IFluidHandler findFluidHandler(Level level, BlockPos pos) {
        for (Direction side : SIDES) {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    static boolean hasAnyItemHandler(Level level, BlockPos pos) {
        return findItemHandler(level, pos) != null;
    }

    static boolean hasAnyFluidHandler(Level level, BlockPos pos) {
        return findFluidHandler(level, pos) != null;
    }
}

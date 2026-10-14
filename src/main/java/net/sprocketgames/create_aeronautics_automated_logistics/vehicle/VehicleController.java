package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;

public interface VehicleController {
    ResourceLocation controllerType();

    VehicleControllerRef ref();

    ResourceKey<Level> dimension();

    Vec3 position();

    default Optional<Vec3> linearVelocity() {
        return Optional.empty();
    }

    default Optional<Vec3> angularVelocity() {
        return Optional.empty();
    }

    default Optional<LiftRotorState> liftRotorState() {
        return Optional.empty();
    }

    default Vec3 recordingPosition(ServerPlayer player) {
        return position();
    }

    default Optional<Float> yaw() {
        return Optional.empty();
    }

    default Optional<RouteRotation> routeRotation() {
        return Optional.empty();
    }

    default Optional<Entity> collisionEntity() {
        return Optional.empty();
    }

    boolean isAssembled();

    boolean isLoaded(ServerLevel level);

    boolean isAutomationCapable();

    boolean isControlledByPlayer(UUID playerId);

    VehicleMotionResult moveToward(ServerLevel level, Vec3 targetPosition, double maxSpeedBlocksPerSecond);

    default VehicleMotionResult moveToward(
            ServerLevel level,
            Vec3 targetPosition,
            double maxSpeedBlocksPerSecond,
            double desiredSpeedBlocksPerTick
    ) {
        return moveToward(level, targetPosition, maxSpeedBlocksPerSecond);
    }

    default VehicleMotionResult moveToward(
            ServerLevel level,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation,
            double maxSpeedBlocksPerSecond,
            double desiredSpeedBlocksPerTick
    ) {
        return moveToward(level, targetPosition, maxSpeedBlocksPerSecond, desiredSpeedBlocksPerTick);
    }

    default void hold(ServerLevel level, Vec3 holdPosition, Optional<RouteRotation> holdRotation) {
        stop(level);
    }

    default void relocate(ServerLevel level, Vec3 targetPosition, Optional<RouteRotation> targetRotation) {
        stop(level);
    }

    void stop(ServerLevel level);

    record LiftRotorState(int rotorCount, int activeRotorCount, double averageAirflow) {
        public LiftRotorState(int rotorCount, int activeRotorCount) {
            this(rotorCount, activeRotorCount, 0.0D);
        }

        public LiftRotorState {
            rotorCount = Math.max(0, rotorCount);
            activeRotorCount = Math.clamp(activeRotorCount, 0, rotorCount);
            averageAirflow = Math.max(0.0D, Math.abs(averageAirflow));
        }

        public boolean hasRotors() {
            return rotorCount > 0;
        }

        public boolean isAnyActive() {
            return activeRotorCount > 0;
        }
    }
}

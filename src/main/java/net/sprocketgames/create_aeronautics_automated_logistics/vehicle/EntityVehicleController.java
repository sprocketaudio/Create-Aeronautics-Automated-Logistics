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
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import org.joml.Quaterniond;

public class EntityVehicleController implements VehicleController {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsAutomatedLogistics.MOD_ID,
            "ridden_entity"
    );

    private final Entity entity;

    public EntityVehicleController(Entity entity) {
        this.entity = entity;
    }

    @Override
    public ResourceLocation controllerType() {
        return TYPE;
    }

    @Override
    public VehicleControllerRef ref() {
        return new VehicleControllerRef(TYPE, dimension(), Optional.of(entity.getUUID()), Optional.empty());
    }

    @Override
    public ResourceKey<Level> dimension() {
        return entity.level().dimension();
    }

    @Override
    public Vec3 position() {
        return entity.position();
    }

    @Override
    public Vec3 recordingPosition(ServerPlayer player) {
        return entity.getPassengers().contains(player) ? player.position() : position();
    }

    @Override
    public Optional<Float> yaw() {
        return Optional.of(entity.getYRot());
    }

    @Override
    public Optional<RouteRotation> routeRotation() {
        return Optional.of(RouteRotation.of(new Quaterniond().rotationY(Math.toRadians(-entity.getYRot()))));
    }

    @Override
    public Optional<Entity> collisionEntity() {
        return Optional.of(entity);
    }

    @Override
    public boolean isAssembled() {
        return entity.isAlive();
    }

    @Override
    public boolean isLoaded(ServerLevel level) {
        return level.getEntity(entity.getUUID()) != null;
    }

    @Override
    public boolean isAutomationCapable() {
        return entity.isAlive();
    }

    @Override
    public boolean isControlledByPlayer(UUID playerId) {
        return entity.getPassengers().stream().anyMatch(passenger -> passenger.getUUID().equals(playerId));
    }

    @Override
    public VehicleMotionResult moveToward(ServerLevel level, Vec3 targetPosition, double maxSpeedMultiplier) {
        return moveToward(level, targetPosition, maxSpeedMultiplier, 0.35D);
    }

    @Override
    public VehicleMotionResult moveToward(
            ServerLevel level,
            Vec3 targetPosition,
            double maxSpeedMultiplier,
            double desiredSpeedBlocksPerTick
    ) {
        if (!isLoaded(level) || !entity.isAlive()) {
            return VehicleMotionResult.failed(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
        }
        Vec3 delta = targetPosition.subtract(entity.position());
        double distance = delta.length();
        if (distance < 0.05D) {
            stop(level);
            return VehicleMotionResult.moved();
        }

        double maxSpeed = Math.max(0.0D, desiredSpeedBlocksPerTick * maxSpeedMultiplier);
        Vec3 velocity = delta.normalize().scale(Math.min(distance, maxSpeed));
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        return VehicleMotionResult.moved();
    }

    @Override
    public void stop(ServerLevel level) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
    }

    @Override
    public void hold(ServerLevel level, Vec3 holdPosition, Optional<RouteRotation> holdRotation) {
        if (!isLoaded(level) || !entity.isAlive()) {
            return;
        }
        stop(level);
    }

    @Override
    public void relocate(ServerLevel level, Vec3 targetPosition, Optional<RouteRotation> targetRotation) {
        if (!isLoaded(level) || !entity.isAlive()) {
            return;
        }
        stop(level);
    }
}

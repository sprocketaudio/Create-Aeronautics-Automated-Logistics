package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Converts recorded route error into player-wired Drive Module outputs. This
 * service never alters a vehicle's pose or physics state.
 */
public final class DriveModuleOutputService {
    private static final double TRANSLATION_DEAD_ZONE = 0.15D;
    private static final double TURN_DEAD_ZONE_RADIANS = Math.toRadians(2.0D);
    private static final double STRENGTH_PER_BLOCK = 3.0D;
    private static final double STRENGTH_PER_RADIAN = 8.0D;
    private static final int INITIAL_LIFT_STRENGTH = 8;
    private static final int MINIMUM_LIFT_STRENGTH = 0;
    private static final int LIFT_TRIM_SLOW_INTERVAL_TICKS = 20;
    private static final double LIFT_VELOCITY_DAMPING = 10.0D;
    private static final Map<UUID, LiftTrimState> LIFT_TRIMS = new HashMap<>();

    private DriveModuleOutputService() {
    }

    public static boolean apply(
            ServerLevel level,
            Route route,
            VehicleController vehicle,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation
    ) {
        Optional<AdvancedTransponderBlockEntity> controller = advancedControllerFor(level, route);
        if (controller.isEmpty()) {
            return false;
        }
        UUID controllerId = controller.get().transponderId();
        List<DriveModuleBlockEntity> modules = DriveModuleLinkService.linkedModules(level, controllerId);
        if (modules.isEmpty()) {
            return false;
        }

        DriveDemand demand = demandFor(vehicle, targetPosition, targetRotation, controller.get());
        for (DriveModuleBlockEntity module : modules) {
            DriveModuleConfig config = module.config().orElse(null);
            int signal = config == null
                    ? 0
                    : outputFor(level, module, vehicle, targetPosition, config.intent(), demand);
            if (config != null && config.inverted() && config.supportsInversion()) {
                signal = 15 - signal;
            }
            int previousSignal = module.outputSignal();
            if (previousSignal != signal) {
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Drive module command {} intent={} encoding={} inverted={} output {} -> {} vehicle={} target={} error=[lift={}, forwardBack={}, leftRight={}, turn={}]",
                        module.moduleId(),
                        config == null ? "none" : config.intent(),
                        config == null ? "none" : config.encoding(),
                        config != null && config.inverted(),
                        previousSignal,
                        signal,
                        vehicle.position(),
                        targetPosition,
                        demand.lift(),
                        demand.forwardBack(),
                        demand.leftRight(),
                        demand.turn()
                );
            }
            module.setOutputSignal(signal);
        }
        return true;
    }

    public static void clear(ServerLevel level, Route route) {
        advancedControllerFor(level, route).ifPresent(controller ->
                DriveModuleLinkService.linkedModules(level, controller.transponderId())
                        .forEach(module -> {
                            LIFT_TRIMS.remove(module.moduleId());
                            if (module.outputSignal() != 0) {
                                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                        "Drive module {} clearing output {} -> 0 because route {} stopped",
                                        module.moduleId(),
                                        module.outputSignal(),
                                        route.id().value()
                                );
                            }
                            module.setOutputSignal(0);
                        })
        );
    }

    public static boolean isAdvancedController(ServerLevel level, Route route) {
        return advancedControllerFor(level, route).isPresent();
    }

    private static Optional<AdvancedTransponderBlockEntity> advancedControllerFor(ServerLevel level, Route route) {
        return ShipTransponderRegistry.knownShips(level.dimension()).stream()
                .filter(snapshot -> snapshot.controllerRef().filter(route.linkedController()::matches).isPresent())
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(AdvancedTransponderBlockEntity.class::isInstance)
                .map(AdvancedTransponderBlockEntity.class::cast)
                .findFirst();
    }

    private static DriveDemand demandFor(
            VehicleController vehicle,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation,
            AdvancedTransponderBlockEntity transponder
    ) {
        Quaterniond currentRotation = vehicle.routeRotation()
                .map(RouteRotation::toQuaterniond)
                .orElseGet(Quaterniond::new);
        Vec3 worldError = targetPosition.subtract(vehicle.position());
        Vector3d localError = new Vector3d(worldError.x, worldError.y, worldError.z);
        new Quaterniond(currentRotation).conjugate().transform(localError);

        Direction forward = transponder.getBlockState().getValue(ShipTransponderBlock.FACING);
        double forwardError = localError.x * forward.getStepX() + localError.z * forward.getStepZ();
        Direction right = forward.getClockWise();
        double rightError = localError.x * right.getStepX() + localError.z * right.getStepZ();

        double turnError = targetRotation.map(target -> signedYawError(currentRotation, target.toQuaterniond())).orElse(0.0D);
        // Aeronautics balloon lift is always vertical in the world. Pitching or
        // rolling the craft must not turn a world-height error into a descent command.
        return new DriveDemand(worldError.y, forwardError, rightError, turnError);
    }

    private static double signedYawError(Quaterniond current, Quaterniond target) {
        Vector3d currentForward = current.transform(new Vector3d(0.0D, 0.0D, 1.0D));
        Vector3d targetForward = target.transform(new Vector3d(0.0D, 0.0D, 1.0D));
        currentForward.y = 0.0D;
        targetForward.y = 0.0D;
        if (currentForward.lengthSquared() < 1.0E-8D || targetForward.lengthSquared() < 1.0E-8D) {
            return 0.0D;
        }
        currentForward.normalize();
        targetForward.normalize();
        return Math.atan2(
                currentForward.x * targetForward.z - currentForward.z * targetForward.x,
                currentForward.dot(targetForward)
        );
    }

    private static int outputFor(
            ServerLevel level,
            DriveModuleBlockEntity module,
            VehicleController vehicle,
            Vec3 targetPosition,
            DriveIntentType intent,
            DriveDemand demand
    ) {
        return switch (intent) {
            case LIFT_TARGET_STRENGTH -> liftTrim(level, module, vehicle, targetPosition, demand.lift());
            case LIFT_INCREASE -> binary(demand.lift() > TRANSLATION_DEAD_ZONE);
            case LIFT_DECREASE -> binary(demand.lift() < -TRANSLATION_DEAD_ZONE);
            case FORWARD_BACK_TARGET_STRENGTH -> magnitudeStrength(demand.forwardBack(), STRENGTH_PER_BLOCK);
            case FORWARD_BACK_INCREASE -> binary(demand.forwardBack() > TRANSLATION_DEAD_ZONE);
            case FORWARD_BACK_DECREASE -> binary(demand.forwardBack() < -TRANSLATION_DEAD_ZONE);
            case FORWARD_BACK_DIRECTION -> binary(demand.forwardBack() >= 0.0D);
            case LEFT_RIGHT_TARGET_STRENGTH -> magnitudeStrength(demand.leftRight(), STRENGTH_PER_BLOCK);
            case LEFT_RIGHT_INCREASE -> binary(demand.leftRight() > TRANSLATION_DEAD_ZONE);
            case LEFT_RIGHT_DECREASE -> binary(demand.leftRight() < -TRANSLATION_DEAD_ZONE);
            case LEFT_RIGHT_DIRECTION -> binary(demand.leftRight() >= 0.0D);
            case TURN_TARGET_STRENGTH -> magnitudeStrength(demand.turn(), STRENGTH_PER_RADIAN);
            case TURN_INCREASE -> binary(demand.turn() > TURN_DEAD_ZONE_RADIANS);
            case TURN_DECREASE -> binary(demand.turn() < -TURN_DEAD_ZONE_RADIANS);
            case TURN_DIRECTION -> binary(demand.turn() >= 0.0D);
            case ROTATIONAL_FORCE_ENABLED -> binary(demand.requiresRotationalForce());
            case DOCKED -> 0;
        };
    }

    private static int liftTrim(
            ServerLevel level,
            DriveModuleBlockEntity module,
            VehicleController vehicle,
            Vec3 targetPosition,
            double liftError
    ) {
        long gameTime = level.getGameTime();
        double currentY = vehicle.position().y;
        LiftTrimState state = LIFT_TRIMS.get(module.moduleId());
        if (state == null) {
            LIFT_TRIMS.put(module.moduleId(), new LiftTrimState(INITIAL_LIFT_STRENGTH, currentY, gameTime));
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Drive lift trim {} initialized: output={} vehicleY={} targetY={} error={}",
                    module.moduleId(),
                    INITIAL_LIFT_STRENGTH,
                    currentY,
                    targetPosition.y,
                    liftError
            );
            return INITIAL_LIFT_STRENGTH;
        }

        // Reduce lift before a rising craft overshoots, and increase it before a
        // falling craft drops past the recorded height.
        double verticalVelocity = currentY - state.previousY();
        double predictedError = liftError - verticalVelocity * LIFT_VELOCITY_DAMPING;
        int adjustedStrength = state.strength();
        long lastAdjustmentTick = state.lastAdjustmentTick();
        if (Math.abs(predictedError) > TRANSLATION_DEAD_ZONE
                && gameTime - lastAdjustmentTick >= liftTrimInterval(predictedError, verticalVelocity)) {
            int previousStrength = adjustedStrength;
            adjustedStrength = Math.clamp(
                    adjustedStrength + (predictedError > 0.0D ? 1 : -1),
                    MINIMUM_LIFT_STRENGTH,
                    15
            );
            lastAdjustmentTick = gameTime;
            if (adjustedStrength != previousStrength) {
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Drive lift trim {}: output {} -> {} vehicleY={} targetY={} error={} velocity={} predictedError={}",
                        module.moduleId(),
                        previousStrength,
                        adjustedStrength,
                        currentY,
                        targetPosition.y,
                        liftError,
                        verticalVelocity,
                        predictedError
                );
            }
        }
        LIFT_TRIMS.put(module.moduleId(), new LiftTrimState(adjustedStrength, currentY, lastAdjustmentTick));
        return adjustedStrength;
    }

    private static int liftTrimInterval(double predictedError, double verticalVelocity) {
        double absoluteError = Math.abs(predictedError);
        double absoluteVelocity = Math.abs(verticalVelocity);
        if (absoluteError >= 4.0D || absoluteVelocity >= 0.15D) {
            return 1;
        }
        if (absoluteError >= 1.0D || absoluteVelocity >= 0.05D) {
            return 2;
        }
        if (absoluteError >= 0.35D || absoluteVelocity >= 0.01D) {
            return 5;
        }
        return LIFT_TRIM_SLOW_INTERVAL_TICKS;
    }

    private static int signedStrength(double value, double scale) {
        return Math.clamp((int) Math.round(7.5D + value * scale), 0, 15);
    }

    private static int magnitudeStrength(double value, double scale) {
        return Math.clamp((int) Math.round(Math.abs(value) * scale), 0, 15);
    }

    private static int binary(boolean active) {
        return active ? 15 : 0;
    }

    private record DriveDemand(double lift, double forwardBack, double leftRight, double turn) {
        private boolean requiresRotationalForce() {
            return Math.abs(lift) > TRANSLATION_DEAD_ZONE
                    || Math.abs(forwardBack) > TRANSLATION_DEAD_ZONE
                    || Math.abs(leftRight) > TRANSLATION_DEAD_ZONE
                    || Math.abs(turn) > TURN_DEAD_ZONE_RADIANS;
        }
    }

    private record LiftTrimState(int strength, double previousY, long lastAdjustmentTick) {
    }
}

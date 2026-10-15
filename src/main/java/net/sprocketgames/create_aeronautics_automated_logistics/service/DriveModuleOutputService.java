package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveFlightMode;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveSignalCategory;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationProfile;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
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
    private static final int MINIMUM_ACTIVE_LIFT_STRENGTH = 1;
    private static final int LIFT_MINIMUM_ADJUSTMENT_TICKS = 20 * 2;
    private static final int LIFT_NEAR_TARGET_DAMPING_TICKS = 10;
    private static final int LIFT_RESPONSE_OBSERVATION_TICKS = 20 * 8;
    private static final int LIFT_UNOBSERVED_RESPONSE_MARGIN_TICKS = 20;
    private static final int LIFT_PREDICTION_TICKS = 20 * 4;
    private static final int LIFT_HANDOFF_RETENTION_TICKS = 10;
    private static final double LIFT_VELOCITY_SMOOTHING = 0.2D;
    private static final double LIFT_TARGET_VELOCITY_SMOOTHING = 0.2D;
    private static final double LIFT_ACCELERATION_SMOOTHING = 0.1D;
    private static final double LIFT_MAX_TARGET_SPEED = 0.15D;
    private static final double LIFT_HEIGHT_CORRECTION_HORIZON_TICKS = 20.0D * 10.0D;
    private static final double LIFT_MAX_ASCENT_CORRECTION_SPEED = 0.05D;
    private static final double LIFT_MAX_DESCENT_CORRECTION_SPEED = 0.06D;
    private static final double LIFT_VELOCITY_TOLERANCE = 0.02D;
    private static final double LIFT_ACCELERATION_TOLERANCE = 0.0001D;
    private static final double LIFT_ACCELERATION_FLOOR = 0.0002D;
    private static final double LIFT_ALTITUDE_TOLERANCE = 0.5D;
    private static final double LIFT_NEAR_TARGET_DAMPING_DISTANCE = 1.0D;
    private static final double LIFT_NEAR_TARGET_DAMPING_SPEED = 0.005D;
    private static final double LIFT_NEAR_TARGET_DAMPING_STEP = 0.125D;
    private static final double LIFT_STOPPING_DISTANCE_MARGIN = 0.75D;
    private static final double LIFT_NEUTRAL_COMMAND_TOLERANCE = 0.05D;
    private static final double LIFT_NEUTRAL_DRIFT_DEAD_ZONE = 0.001D;
    private static final double LIFT_MAX_NEUTRAL_LEARNING_STEP = 0.02D;
    private static final double RESTORE_ALTITUDE_TOLERANCE = 1.0D;
    private static final int RESTORE_ALTITUDE_SETTLE_TICKS = 20;
    private static final double RESTORE_VERTICAL_MOVEMENT_TOLERANCE = 0.02D;
    private static final int RELATIVE_RESTORE_READY_TICKS = 10;
    private static final double RELATIVE_RESTORE_AIRFLOW_FRACTION = 0.95D;
    private static final int RELATIVE_LIFT_DEFAULT_PULSE_TICKS = 10;
    private static final int RELATIVE_LIFT_DEFAULT_RESPONSE_TICKS = 20 * 6;
    private static final double RELATIVE_LIFT_VELOCITY_TOLERANCE = 0.012D;
    private static final double RELATIVE_LIFT_EMERGENCY_SPEED = 0.08D;
    private static final double RELATIVE_LIFT_ACCELERATION_TOLERANCE = 0.0002D;
    private static final int RELATIVE_LIFT_PREDICTION_TICKS = 60;
    private static final double RELATIVE_LIFT_FAR_REVERSAL_DISTANCE = 2.0D;
    private static final double RELATIVE_APPROACH_CAPTURE_DISTANCE = 3.0D;
    private static final double RELATIVE_APPROACH_RELEASE_DISTANCE = 0.75D;
    private static final double RELATIVE_TAKEOFF_RELEASE_HEIGHT = 0.5D;
    private static final double ROTORCRAFT_ENDPOINT_SETTLED_SPEED = 0.20D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double BINARY_TRANSLATION_FULL_POWER_DEMAND = 2.5D;
    private static final double BINARY_TRANSLATION_MEDIUM_POWER_DEMAND = 1.0D;
    private static final double BINARY_TURN_FULL_POWER_RADIANS = Math.toRadians(15.0D);
    private static final double BINARY_TURN_MEDIUM_POWER_RADIANS = Math.toRadians(6.0D);
    private static final int BINARY_MEDIUM_PULSE_PERIOD_TICKS = 4;
    private static final int BINARY_MEDIUM_PULSE_ON_TICKS = 2;
    private static final int BINARY_FINE_PULSE_PERIOD_TICKS = 5;
    private static final Map<UUID, LiftTrimState> LIFT_TRIMS = new HashMap<>();
    private static final Map<UUID, RelativeLiftState> RELATIVE_LIFT_STATES = new HashMap<>();
    private static final Map<RouteId, Integer> RESTORE_ALTITUDE_SETTLE = new HashMap<>();
    private static final Map<RouteId, RestoreWarmupState> RESTORE_WARMUPS = new HashMap<>();

    private DriveModuleOutputService() {
    }

    public static boolean apply(
            ServerLevel level,
            Route route,
            VehicleController vehicle,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation
    ) {
        return apply(level, route, vehicle, targetPosition, targetRotation, false);
    }

    public static boolean apply(
            ServerLevel level,
            Route route,
            VehicleController vehicle,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation,
            boolean preciseArrival
    ) {
        Optional<AdvancedTransponderBlockEntity> controller = advancedControllerFor(level, route);
        if (controller.isEmpty()) {
            return false;
        }
        UUID controllerId = controller.get().transponderId();
        LiftCalibrationService.release(controllerId);
        List<DriveModuleBlockEntity> modules = DriveModuleLinkService.linkedModules(level, controllerId);
        if (modules.isEmpty()) {
            return false;
        }
        Optional<DriveFlightMode> flightMode = DriveFlightMode.resolve(controlIntents(modules));
        if (flightMode.isEmpty()) {
            modules.forEach(module -> module.setOutputSignal(0));
            return false;
        }

        DriveDemand demand = demandFor(
                vehicle,
                targetPosition,
                targetRotation,
                controller.get(),
                flightMode.get()
        );
        if (flightMode.get() == DriveFlightMode.AIRSHIP && holdHorizontalDuringRestore(route, demand.lift())) {
            demand = new DriveDemand(
                    demand.lift(),
                    0.0D,
                    0.0D,
                    0.0D,
                    demand.forwardVelocity(),
                    demand.rightVelocity(),
                    demand.yawVelocity(),
                    demand.uprightDot()
            );
        }
        applyOutputs(
                level,
                controller.get(),
                modules,
                vehicle,
                targetPosition,
                demand,
                flightMode.get(),
                preciseArrival
        );
        return true;
    }

    private static void applyOutputs(
            ServerLevel level,
            AdvancedTransponderBlockEntity controller,
            List<DriveModuleBlockEntity> modules,
            VehicleController vehicle,
            Vec3 targetPosition,
            DriveDemand demand,
            DriveFlightMode flightMode,
            boolean preciseArrival
    ) {
        if (flightMode == DriveFlightMode.AIRSHIP) {
            applyAirshipOutputs(level, controller, modules, vehicle, targetPosition, demand);
            return;
        }
        applyRotorcraftOutputs(level, controller, modules, vehicle, targetPosition, demand, preciseArrival);
    }

    private static void applyAirshipOutputs(
            ServerLevel level,
            AdvancedTransponderBlockEntity controller,
            List<DriveModuleBlockEntity> modules,
            VehicleController vehicle,
            Vec3 targetPosition,
            DriveDemand demand
    ) {
        RELATIVE_LIFT_STATES.remove(controller.transponderId());
        RotorcraftDriveController.clear(controller.transponderId());
        writeOutputs(
                level,
                controller,
                modules,
                vehicle,
                targetPosition,
                demand,
                RelativeLiftCommand.NONE,
                DriveFlightMode.AIRSHIP
        );
    }

    private static void applyRotorcraftOutputs(
            ServerLevel level,
            AdvancedTransponderBlockEntity controller,
            List<DriveModuleBlockEntity> modules,
            VehicleController vehicle,
            Vec3 targetPosition,
            DriveDemand demand,
            boolean preciseArrival
    ) {
        vehicle.liftRotorState()
                .filter(VehicleController.LiftRotorState::hasRotors)
                .ifPresent(rotors -> controller.updateRelativeLiftRotorSnapshot(
                        rotors.rotorCount(),
                        rotors.averageAirflow()
                ));
        RelativeLiftCommand relativeLiftCommand = relativeLiftCommand(
                level,
                controller,
                vehicle,
                targetPosition,
                demand.lift(),
                preciseArrival
        );
        EnumSet<DriveIntentType> controls = controlIntents(modules);
        RelativeLiftState liftState = RELATIVE_LIFT_STATES.get(controller.transponderId());
        demand = guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controller.transponderId(),
                controls,
                liftState != null && liftState.takeoffRampActive,
                preciseArrival,
                targetPosition,
                demand
        );
        double adaptedTurn = RotorcraftDriveController.adaptDirectTurnOutput(
                controller.transponderId(),
                level.getGameTime(),
                demand.turn(),
                demand.yawVelocity(),
                hasDirectTurnPair(controls) && !hasVariableStrengthControl(controls, DriveSignalCategory.TURN)
        );
        if (adaptedTurn != demand.turn()) {
            demand = new DriveDemand(
                    demand.lift(),
                    demand.forwardBack(),
                    demand.leftRight(),
                    adaptedTurn,
                    demand.forwardVelocity(),
                    demand.rightVelocity(),
                    demand.yawVelocity(),
                    demand.uprightDot()
            );
        }
        writeOutputs(
                level,
                controller,
                modules,
                vehicle,
                targetPosition,
                demand,
                relativeLiftCommand,
                DriveFlightMode.ROTORCRAFT
        );
    }

    private static void writeOutputs(
            ServerLevel level,
            AdvancedTransponderBlockEntity controller,
            List<DriveModuleBlockEntity> modules,
            VehicleController vehicle,
            Vec3 targetPosition,
            DriveDemand demand,
            RelativeLiftCommand relativeLiftCommand,
            DriveFlightMode flightMode
    ) {
        EnumSet<DriveIntentType> controls = controlIntents(modules);
        for (DriveModuleBlockEntity module : modules) {
            DriveModuleConfig config = module.config().orElse(null);
            int signal = config == null
                    ? 0
                    : outputFor(
                            level,
                            module,
                            controller,
                            vehicle,
                            targetPosition,
                            config.intent(),
                            demand,
                            relativeLiftCommand,
                            flightMode,
                            controls
                    );
            if (config != null && config.inverted() && config.supportsInversion()) {
                signal = 15 - signal;
            }
            int previousSignal = module.outputSignal();
            if (previousSignal != signal) {
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Drive module command {} intent={} encoding={} inverted={} output {} -> {} vehicle={} target={} error=[lift={}, forwardBack={}, leftRight={}, turn={}] motion=[forward={}, right={}, yaw={}]",
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
                        demand.turn(),
                        demand.forwardVelocity(),
                        demand.rightVelocity(),
                        demand.yawVelocity()
                );
            }
            module.setOutputSignal(signal);
        }
    }

    public static void clear(ServerLevel level, Route route) {
        RESTORE_ALTITUDE_SETTLE.remove(route.id());
        RESTORE_WARMUPS.remove(route.id());
        advancedControllerFor(level, route).ifPresent(controller ->
                DriveModuleLinkService.linkedModules(level, controller.transponderId())
                        .forEach(module -> {
                            // Clear the real output immediately, but leave the estimator alive
                            // briefly so an adjacent schedule leg keeps the ship's momentum.
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

    public static boolean hasTurnControl(ServerLevel level, Route route) {
        return hasDirectionalControl(linkedControlIntents(level, route), DriveSignalCategory.TURN);
    }

    public static boolean isRotorcraftControl(ServerLevel level, Route route) {
        return DriveFlightMode.resolve(linkedControlIntents(level, route))
                .filter(mode -> mode == DriveFlightMode.ROTORCRAFT)
                .isPresent();
    }

    public static boolean shouldHoldRotorcraftGuidance(ServerLevel level, Route route) {
        return advancedControllerFor(level, route)
                .map(AdvancedTransponderBlockEntity::transponderId)
                .map(controllerId -> {
                    RelativeLiftState liftState = RELATIVE_LIFT_STATES.get(controllerId);
                    return (liftState != null && liftState.takeoffRampActive)
                            || RotorcraftDriveController.isHoldingGuidance(controllerId);
                })
                .orElse(false);
    }

    static boolean isRotorcraftEndpointMotionSettled(Vec3 velocity) {
        return velocity.length() <= ROTORCRAFT_ENDPOINT_SETTLED_SPEED;
    }

    /**
     * Strength outputs improve smoothness, but reversible on/off controls are
     * still valid Drive Mode controls and must not be rejected at takeoff.
     */
    public static Optional<PlaybackFailure> controlRequirementFailure(ServerLevel level, Route route) {
        advancedControllerFor(level, route)
                .ifPresent(controller -> DriveModuleLinkService.reconcileKnownModules(level, controller.transponderId()));
        EnumSet<DriveIntentType> controls = linkedControlIntents(level, route);
        if (DriveFlightMode.resolve(controls).isEmpty()) {
            return Optional.of(PlaybackFailure.LIFT_CONTROL_REQUIRED);
        }
        return hasRequiredHorizontalControl(controls)
                ? Optional.empty()
                : Optional.of(PlaybackFailure.HORIZONTAL_CONTROL_REQUIRED);
    }

    static boolean hasRequiredHorizontalControl(EnumSet<DriveIntentType> controls) {
        boolean forwardBack = hasDirectionalControl(controls, DriveSignalCategory.FORWARD_BACK);
        boolean leftRight = hasDirectionalControl(controls, DriveSignalCategory.LEFT_RIGHT);
        return (forwardBack && leftRight)
                || (hasDirectionalControl(controls, DriveSignalCategory.TURN) && (forwardBack || leftRight));
    }

    private static boolean hasDirectionalControl(
            EnumSet<DriveIntentType> controls,
            DriveSignalCategory category
    ) {
        return switch (category) {
            case FORWARD_BACK -> controls.contains(DriveIntentType.FORWARD_BACK_DIRECTION)
                    || controls.contains(DriveIntentType.FORWARD_BACK_FORWARD)
                    && controls.contains(DriveIntentType.FORWARD_BACK_BACKWARD);
            case LEFT_RIGHT -> controls.contains(DriveIntentType.LEFT_RIGHT_DIRECTION)
                    || controls.contains(DriveIntentType.LEFT_RIGHT_LEFT)
                    && controls.contains(DriveIntentType.LEFT_RIGHT_RIGHT);
            case TURN -> controls.contains(DriveIntentType.TURN_DIRECTION)
                    || controls.contains(DriveIntentType.TURN_CLOCKWISE)
                    && controls.contains(DriveIntentType.TURN_ANTICLOCKWISE);
            default -> false;
        };
    }

    private static EnumSet<DriveIntentType> linkedControlIntents(ServerLevel level, Route route) {
        return advancedControllerFor(level, route)
                .map(controller -> controlIntents(
                        DriveModuleLinkService.linkedModules(level, controller.transponderId())
                ))
                .orElseGet(() -> EnumSet.noneOf(DriveIntentType.class));
    }

    private static EnumSet<DriveIntentType> controlIntents(List<DriveModuleBlockEntity> modules) {
        EnumSet<DriveIntentType> intents = EnumSet.noneOf(DriveIntentType.class);
        modules.stream()
                .map(DriveModuleBlockEntity::config)
                .flatMap(Optional::stream)
                .map(DriveModuleConfig::intent)
                .forEach(intents::add);
        return intents;
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
            AdvancedTransponderBlockEntity transponder,
            DriveFlightMode flightMode
    ) {
        Quaterniond currentRotation = vehicle.routeRotation()
                .map(RouteRotation::toQuaterniond)
                .orElseGet(Quaterniond::new);
        Vec3 worldError = targetPosition.subtract(vehicle.position());
        Direction forward = transponder.getBlockState().getValue(ShipTransponderBlock.FACING);
        Vec3 worldVelocity = vehicle.linearVelocity().orElse(Vec3.ZERO);
        HorizontalDemand horizontal = flightMode == DriveFlightMode.ROTORCRAFT
                ? rotorcraftHorizontalDemand(currentRotation, forward, worldError, worldVelocity)
                : localHorizontalDemand(currentRotation, forward, worldError, worldVelocity);
        double yawVelocity = vehicle.angularVelocity().map(velocity -> velocity.y).orElse(0.0D);
        double uprightDot = currentRotation.transform(new Vector3d(0.0D, 1.0D, 0.0D)).y;

        double turnError = targetRotation.map(target -> signedYawError(currentRotation, target.toQuaterniond())).orElse(0.0D);
        // Aeronautics balloon lift is always vertical in the world. Pitching or
        // rolling the craft must not turn a world-height error into a descent command.
        return new DriveDemand(
                worldError.y,
                horizontal.forwardError(),
                horizontal.rightError(),
                turnError,
                horizontal.forwardVelocity(),
                horizontal.rightVelocity(),
                yawVelocity,
                uprightDot
        );
    }

    private static HorizontalDemand localHorizontalDemand(
            Quaterniond currentRotation,
            Direction forward,
            Vec3 worldError,
            Vec3 worldVelocity
    ) {
        Vector3d localError = new Vector3d(worldError.x, worldError.y, worldError.z);
        new Quaterniond(currentRotation).conjugate().transform(localError);
        Vector3d localVelocity = new Vector3d(worldVelocity.x, worldVelocity.y, worldVelocity.z);
        new Quaterniond(currentRotation).conjugate().transform(localVelocity);
        Direction right = forward.getClockWise();
        return new HorizontalDemand(
                localError.x * forward.getStepX() + localError.z * forward.getStepZ(),
                localError.x * right.getStepX() + localError.z * right.getStepZ(),
                localVelocity.x * forward.getStepX() + localVelocity.z * forward.getStepZ(),
                localVelocity.x * right.getStepX() + localVelocity.z * right.getStepZ()
        );
    }

    static HorizontalDemand rotorcraftHorizontalDemand(
            Quaterniond currentRotation,
            Direction forward,
            Vec3 worldError,
            Vec3 worldVelocity
    ) {
        Vector3d worldForward = currentRotation.transform(new Vector3d(
                forward.getStepX(),
                0.0D,
                forward.getStepZ()
        ));
        worldForward.y = 0.0D;
        if (worldForward.lengthSquared() < 1.0E-8D) {
            Direction right = forward.getClockWise();
            Vector3d worldRight = currentRotation.transform(new Vector3d(
                    right.getStepX(),
                    0.0D,
                    right.getStepZ()
            ));
            worldRight.y = 0.0D;
            if (worldRight.lengthSquared() < 1.0E-8D) {
                worldForward.set(forward.getStepX(), 0.0D, forward.getStepZ());
            } else {
                worldRight.normalize();
                worldForward.set(worldRight.z, 0.0D, -worldRight.x);
            }
        } else {
            worldForward.normalize();
        }
        Vector3d worldRight = new Vector3d(-worldForward.z, 0.0D, worldForward.x);
        return new HorizontalDemand(
                worldError.x * worldForward.x + worldError.z * worldForward.z,
                worldError.x * worldRight.x + worldError.z * worldRight.z,
                worldVelocity.x * worldForward.x + worldVelocity.z * worldForward.z,
                worldVelocity.x * worldRight.x + worldVelocity.z * worldRight.z
        );
    }

    record HorizontalDemand(
            double forwardError,
            double rightError,
            double forwardVelocity,
            double rightVelocity
    ) {
    }

    static DriveDemand guidanceDemandForMode(
            DriveFlightMode flightMode,
            UUID controllerId,
            EnumSet<DriveIntentType> controls,
            boolean takeoffRampActive,
            DriveDemand demand
    ) {
        return guidanceDemandForMode(
                flightMode,
                controllerId,
                controls,
                takeoffRampActive,
                false,
                Vec3.ZERO,
                demand
        );
    }

    static DriveDemand guidanceDemandForMode(
            DriveFlightMode flightMode,
            UUID controllerId,
            EnumSet<DriveIntentType> controls,
            boolean takeoffRampActive,
            boolean preciseArrival,
            Vec3 targetPosition,
            DriveDemand demand
    ) {
        if (flightMode == DriveFlightMode.AIRSHIP) {
            RotorcraftDriveController.clear(controllerId);
            return demand;
        }
        return RotorcraftDriveController.apply(
                controllerId,
                hasDirectionalControl(controls, DriveSignalCategory.TURN),
                hasDirectionalControl(controls, DriveSignalCategory.FORWARD_BACK),
                hasDirectionalControl(controls, DriveSignalCategory.LEFT_RIGHT),
                takeoffRampActive,
                preciseArrival,
                targetPosition,
                demand
        );
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
            AdvancedTransponderBlockEntity controller,
            VehicleController vehicle,
            Vec3 targetPosition,
            DriveIntentType intent,
            DriveDemand demand,
            RelativeLiftCommand relativeLiftCommand,
            DriveFlightMode flightMode,
            EnumSet<DriveIntentType> controls
    ) {
        return switch (intent) {
            case LIFT_TARGET_STRENGTH -> liftTrim(
                    level,
                    module,
                    controller,
                    vehicle,
                    targetPosition,
                    demand.lift()
            );
            case LIFT_INCREASE -> binary(relativeLiftCommand == RelativeLiftCommand.INCREASE);
            case LIFT_DECREASE -> binary(relativeLiftCommand == RelativeLiftCommand.DECREASE);
            case FORWARD_BACK_TARGET_STRENGTH -> magnitudeStrength(demand.forwardBack(), STRENGTH_PER_BLOCK);
            case FORWARD_BACK_INCREASE -> binary(demand.forwardBack() > TRANSLATION_DEAD_ZONE);
            case FORWARD_BACK_DECREASE -> binary(demand.forwardBack() < -TRANSLATION_DEAD_ZONE);
            case FORWARD_BACK_FORWARD -> binary(
                    demand.forwardBack() > TRANSLATION_DEAD_ZONE
                            && binaryMotionPowered(
                                    Math.abs(demand.forwardBack()),
                                    BINARY_TRANSLATION_FULL_POWER_DEMAND,
                                    BINARY_TRANSLATION_MEDIUM_POWER_DEMAND,
                                    level.getGameTime(),
                                    shouldFeatherDirectControl(
                                            flightMode,
                                            controls,
                                            DriveSignalCategory.FORWARD_BACK
                                    )
                            )
            );
            case FORWARD_BACK_BACKWARD -> binary(
                    demand.forwardBack() < -TRANSLATION_DEAD_ZONE
                            && binaryMotionPowered(
                                    Math.abs(demand.forwardBack()),
                                    BINARY_TRANSLATION_FULL_POWER_DEMAND,
                                    BINARY_TRANSLATION_MEDIUM_POWER_DEMAND,
                                    level.getGameTime(),
                                    shouldFeatherDirectControl(
                                            flightMode,
                                            controls,
                                            DriveSignalCategory.FORWARD_BACK
                                    )
                            )
            );
            case FORWARD_BACK_DIRECTION -> binary(demand.forwardBack() >= 0.0D);
            case LEFT_RIGHT_TARGET_STRENGTH -> magnitudeStrength(demand.leftRight(), STRENGTH_PER_BLOCK);
            case LEFT_RIGHT_INCREASE -> binary(demand.leftRight() > TRANSLATION_DEAD_ZONE);
            case LEFT_RIGHT_DECREASE -> binary(demand.leftRight() < -TRANSLATION_DEAD_ZONE);
            case LEFT_RIGHT_LEFT -> binary(
                    demand.leftRight() < -TRANSLATION_DEAD_ZONE
                            && binaryMotionPowered(
                                    Math.abs(demand.leftRight()),
                                    BINARY_TRANSLATION_FULL_POWER_DEMAND,
                                    BINARY_TRANSLATION_MEDIUM_POWER_DEMAND,
                                    level.getGameTime(),
                                    shouldFeatherDirectControl(
                                            flightMode,
                                            controls,
                                            DriveSignalCategory.LEFT_RIGHT
                                    )
                            )
            );
            case LEFT_RIGHT_RIGHT -> binary(
                    demand.leftRight() > TRANSLATION_DEAD_ZONE
                            && binaryMotionPowered(
                                    Math.abs(demand.leftRight()),
                                    BINARY_TRANSLATION_FULL_POWER_DEMAND,
                                    BINARY_TRANSLATION_MEDIUM_POWER_DEMAND,
                                    level.getGameTime(),
                                    shouldFeatherDirectControl(
                                            flightMode,
                                            controls,
                                            DriveSignalCategory.LEFT_RIGHT
                                    )
                            )
            );
            case LEFT_RIGHT_DIRECTION -> binary(demand.leftRight() >= 0.0D);
            case TURN_TARGET_STRENGTH -> magnitudeStrength(demand.turn(), STRENGTH_PER_RADIAN);
            case TURN_INCREASE -> binary(demand.turn() > TURN_DEAD_ZONE_RADIANS);
            case TURN_DECREASE -> binary(demand.turn() < -TURN_DEAD_ZONE_RADIANS);
            case TURN_CLOCKWISE -> binary(
                    demand.turn() > TURN_DEAD_ZONE_RADIANS
                            && binaryMotionPowered(
                                    Math.abs(demand.turn()),
                                    BINARY_TURN_FULL_POWER_RADIANS,
                                    BINARY_TURN_MEDIUM_POWER_RADIANS,
                                    level.getGameTime(),
                                    shouldFeatherDirectControl(
                                            flightMode,
                                            controls,
                                            DriveSignalCategory.TURN
                                    )
                            )
            );
            case TURN_ANTICLOCKWISE -> binary(
                    demand.turn() < -TURN_DEAD_ZONE_RADIANS
                            && binaryMotionPowered(
                                    Math.abs(demand.turn()),
                                    BINARY_TURN_FULL_POWER_RADIANS,
                                    BINARY_TURN_MEDIUM_POWER_RADIANS,
                                    level.getGameTime(),
                                    shouldFeatherDirectControl(
                                            flightMode,
                                            controls,
                                            DriveSignalCategory.TURN
                                    )
                            )
            );
            case TURN_DIRECTION -> binary(demand.turn() >= 0.0D);
            // A clutch or other force-enable circuit has to remain engaged
            // throughout active Drive Mode. Lift trim then changes the burner
            // strength through a separate LIFT_TARGET_STRENGTH module; dropping
            // this output in the route dead zone makes a hovering ship fall.
            case ROTATIONAL_FORCE_ENABLED -> 15;
            case DOCKED -> 0;
        };
    }

    private static RelativeLiftCommand relativeLiftCommand(
            ServerLevel level,
            AdvancedTransponderBlockEntity controller,
            VehicleController vehicle,
            Vec3 targetPosition,
            double liftError,
            boolean preciseArrival
    ) {
        long gameTime = level.getGameTime();
        LiftCalibrationProfile profile = controller.liftCalibration()
                .filter(LiftCalibrationProfile::relativeControl)
                .orElse(null);
        int increasePulseTicks = profile == null
                ? RELATIVE_LIFT_DEFAULT_PULSE_TICKS
                : profile.increasePulseTicks();
        int decreasePulseTicks = profile == null
                ? RELATIVE_LIFT_DEFAULT_PULSE_TICKS
                : profile.decreasePulseTicks();
        int responseTicks = profile == null
                ? RELATIVE_LIFT_DEFAULT_RESPONSE_TICKS
                : profile.responseTicks();

        double currentY = vehicle.position().y;
        RelativeLiftState state = RELATIVE_LIFT_STATES.get(controller.transponderId());
        if (state == null || !isLiftHandoffSampleReusable(gameTime, state.lastSampleTick)) {
            double initialVelocity = vehicle.linearVelocity()
                    .map(velocity -> verticalVelocityBlocksPerTick(velocity.y))
                    .orElse(0.0D);
            boolean rotorsAlreadyRunning = vehicle.liftRotorState()
                    .filter(VehicleController.LiftRotorState::hasRotors)
                    .filter(VehicleController.LiftRotorState::isAnyActive)
                    .map(rotors -> Math.abs(rotors.averageAirflow()) > 0.0D)
                    .orElse(false);
            state = new RelativeLiftState(
                    currentY,
                    targetPosition.y,
                    initialVelocity,
                    initialVelocity,
                    0.0D,
                    0.0D,
                    gameTime,
                    profile != null && !rotorsAlreadyRunning
            );
            RELATIVE_LIFT_STATES.put(controller.transponderId(), state);
        } else {
            state.sample(currentY, targetPosition.y, gameTime);
        }

        if (state.takeoffRampActive && state.command == RelativeLiftCommand.NONE) {
            if (!relativeTakeoffReady(state.takeoffStartY, currentY)) {
                return RelativeLiftCommand.NONE;
            }
            state.takeoffRampActive = false;
            state.lastCommand = RelativeLiftCommand.NONE;
            state.observationTicks = 0;
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Relative lift takeoff {} observed physical rise at y={} startY={}; enabling horizontal guidance",
                    controller.transponderId(), currentY, state.takeoffStartY
            );
        }

        double correctionVelocity = Math.clamp(
                liftError / LIFT_HEIGHT_CORRECTION_HORIZON_TICKS,
                -LIFT_MAX_DESCENT_CORRECTION_SPEED,
                LIFT_MAX_ASCENT_CORRECTION_SPEED
        );
        double desiredVelocity = Math.clamp(
                state.smoothedTargetVelocity + correctionVelocity,
                -LIFT_MAX_TARGET_SPEED,
                LIFT_MAX_TARGET_SPEED
        );
        boolean pairedApproachDescent = shouldUseRelativeApproachDescent(
                preciseArrival,
                state.approachDescentActive,
                liftError
        );
        if (pairedApproachDescent && !state.approachDescentActive) {
            state.approachDescentActive = true;
            state.approachDescentNotchEstablished = state.smoothedVelocity
                    < -RELATIVE_LIFT_VELOCITY_TOLERANCE;
            state.approachBrakeActive = false;
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Relative lift approach {} entered paired descent control: vehicleY={} targetY={} error={} velocity={} acceleration={} descentNotchEstablished={}",
                    controller.transponderId(), currentY, targetPosition.y, liftError,
                    state.smoothedVelocity, state.smoothedAcceleration,
                    state.approachDescentNotchEstablished
            );
        } else if (!pairedApproachDescent && state.approachDescentActive) {
            state.approachDescentActive = false;
            state.approachDescentNotchEstablished = false;
            state.approachBrakeActive = false;
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Relative lift approach {} entered fine capture control: vehicleY={} targetY={} error={} velocity={} acceleration={}",
                    controller.transponderId(), currentY, targetPosition.y, liftError,
                    state.smoothedVelocity, state.smoothedAcceleration
            );
        }

        RelativeLiftCommand desiredCommand;
        if (pairedApproachDescent) {
            desiredCommand = relativeApproachDescentDecision(
                    state.approachDescentNotchEstablished,
                    state.approachBrakeActive,
                    state.smoothedVelocity,
                    state.smoothedAcceleration,
                    responseTicks
            );
        } else {
            desiredCommand = relativeLiftDecision(
                    desiredVelocity,
                    state.smoothedVelocity,
                    state.smoothedAcceleration,
                    responseTicks
            );
            desiredCommand = suppressFarRelativeLiftReversal(
                    liftError,
                    state.smoothedVelocity - state.smoothedTargetVelocity,
                    desiredCommand,
                    responseTicks
            );
        }

        if (state.command != RelativeLiftCommand.NONE) {
            boolean unsafeContinuation = state.command == RelativeLiftCommand.INCREASE
                    ? state.smoothedVelocity > RELATIVE_LIFT_EMERGENCY_SPEED
                    : state.smoothedVelocity < -RELATIVE_LIFT_EMERGENCY_SPEED;
            if (!unsafeContinuation && state.commandTicks < state.commandTargetTicks) {
                state.commandTicks++;
                return state.command;
            }
            finishRelativeLiftPulse(state);
            return RelativeLiftCommand.NONE;
        }

        if (state.lastCommand != RelativeLiftCommand.NONE
                && state.observationTicks < responseTicks) {
            state.observationTicks++;
            if (!urgentRelativeLiftCommand(
                    state.lastCommand,
                    desiredCommand,
                    state.observationTicks,
                    state.smoothedVelocity,
                    state.smoothedAcceleration
            )) {
                return RelativeLiftCommand.NONE;
            }
        }

        if (desiredCommand == RelativeLiftCommand.NONE) {
            return RelativeLiftCommand.NONE;
        }
        if (state.takeoffRampPending && desiredCommand == RelativeLiftCommand.DECREASE) {
            return RelativeLiftCommand.NONE;
        }
        boolean takeoffRamp = state.takeoffRampPending
                && desiredCommand == RelativeLiftCommand.INCREASE;
        int pulseTicks = relativeLiftPulseTicks(
                profile,
                desiredCommand,
                takeoffRamp,
                increasePulseTicks,
                decreasePulseTicks
        );
        state.command = desiredCommand;
        state.commandTicks = 1;
        state.commandTargetTicks = pulseTicks;
        if (takeoffRamp) {
            state.takeoffRampPending = false;
            state.takeoffRampActive = true;
            state.takeoffStartY = currentY;
        }
        if (pairedApproachDescent) {
            if (desiredCommand == RelativeLiftCommand.INCREASE) {
                state.approachBrakeActive = true;
            } else {
                state.approachDescentNotchEstablished = true;
                state.approachBrakeActive = false;
            }
        }
        state.lastCommand = RelativeLiftCommand.NONE;
        state.observationTicks = 0;
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Relative lift command {}: command={} pulseTicks={} takeoffRamp={} vehicleY={} targetY={} error={} velocity={} targetVelocity={} desiredVelocity={} acceleration={}",
                controller.transponderId(),
                desiredCommand,
                pulseTicks,
                takeoffRamp,
                currentY,
                targetPosition.y,
                liftError,
                state.smoothedVelocity,
                state.smoothedTargetVelocity,
                desiredVelocity,
                state.smoothedAcceleration
        );
        return desiredCommand;
    }

    static int relativeLiftPulseTicks(
            LiftCalibrationProfile profile,
            RelativeLiftCommand command,
            boolean takeoffRamp,
            int increasePulseTicks,
            int decreasePulseTicks
    ) {
        if (takeoffRamp && profile != null && command == RelativeLiftCommand.INCREASE) {
            return profile.takeoffIncreaseTicks();
        }
        return command == RelativeLiftCommand.INCREASE
                ? increasePulseTicks
                : decreasePulseTicks;
    }

    static RelativeLiftCommand relativeLiftDecision(
            double desiredVelocity,
            double measuredVelocity,
            double measuredAcceleration,
            int responseTicks
    ) {
        int predictionTicks = Math.min(RELATIVE_LIFT_PREDICTION_TICKS, Math.max(1, responseTicks));
        double usefulAcceleration = Math.abs(measuredAcceleration) >= RELATIVE_LIFT_ACCELERATION_TOLERANCE
                ? measuredAcceleration
                : 0.0D;
        double predictedVelocity = measuredVelocity + usefulAcceleration * predictionTicks;
        double velocityError = desiredVelocity - predictedVelocity;
        if (velocityError > RELATIVE_LIFT_VELOCITY_TOLERANCE) {
            return RelativeLiftCommand.INCREASE;
        }
        if (velocityError < -RELATIVE_LIFT_VELOCITY_TOLERANCE) {
            return RelativeLiftCommand.DECREASE;
        }
        return RelativeLiftCommand.NONE;
    }

    static RelativeLiftCommand relativeApproachDescentDecision(
            boolean descentNotchEstablished,
            boolean brakeActive,
            double measuredVelocity,
            double measuredAcceleration,
            int responseTicks
    ) {
        if (!descentNotchEstablished) {
            return RelativeLiftCommand.DECREASE;
        }
        int adjustment = LiftCalibrationService.relativeLandingBrakeAdjustment(
                brakeActive,
                measuredVelocity,
                measuredAcceleration,
                responseTicks
        );
        if (adjustment > 0) {
            return RelativeLiftCommand.INCREASE;
        }
        if (adjustment < 0) {
            return RelativeLiftCommand.DECREASE;
        }
        return RelativeLiftCommand.NONE;
    }

    static boolean urgentRelativeLiftCommand(
            RelativeLiftCommand lastCommand,
            RelativeLiftCommand desiredCommand,
            int observationTicks,
            double measuredVelocity,
            double measuredAcceleration
    ) {
        if (lastCommand == RelativeLiftCommand.NONE
                || desiredCommand == RelativeLiftCommand.NONE
                || observationTicks < RELATIVE_LIFT_DEFAULT_PULSE_TICKS) {
            return false;
        }
        if (desiredCommand != lastCommand) {
            return Math.abs(measuredVelocity) >= RELATIVE_LIFT_VELOCITY_TOLERANCE * 2.0D
                    || Math.abs(measuredAcceleration) >= RELATIVE_LIFT_ACCELERATION_TOLERANCE;
        }

        // A pulse can be cut short by the emergency-speed guard. If the craft
        // subsequently starts moving in the opposite direction, waiting for
        // the actuator's entire calibrated response window can let it reach the
        // ground before the same corrective command is allowed again.
        int commandDirection = desiredCommand == RelativeLiftCommand.INCREASE ? 1 : -1;
        return measuredVelocity * commandDirection
                <= -RELATIVE_LIFT_VELOCITY_TOLERANCE * 1.5D;
    }

    static boolean shouldUseRelativeApproachDescent(
            boolean preciseArrival,
            boolean approachDescentActive,
            double liftError
    ) {
        return preciseArrival
                && (liftError < -RELATIVE_APPROACH_CAPTURE_DISTANCE
                || approachDescentActive && liftError < -RELATIVE_APPROACH_RELEASE_DISTANCE);
    }

    static boolean relativeTakeoffReady(double takeoffStartY, double currentY) {
        return Double.isFinite(takeoffStartY)
                && Double.isFinite(currentY)
                && currentY - takeoffStartY >= RELATIVE_TAKEOFF_RELEASE_HEIGHT;
    }

    static double verticalVelocityBlocksPerTick(double blocksPerSecond) {
        return blocksPerSecond / TICKS_PER_SECOND;
    }

    static RelativeLiftCommand suppressFarRelativeLiftReversal(
            double liftError,
            double measuredRelativeVelocity,
            RelativeLiftCommand command,
            int responseTicks
    ) {
        double errorDistance = Math.abs(liftError);
        if (errorDistance <= RELATIVE_LIFT_FAR_REVERSAL_DISTANCE
                || command == RelativeLiftCommand.NONE) {
            return command;
        }
        int commandDirection = command == RelativeLiftCommand.INCREASE ? 1 : -1;
        int targetDirection = liftError > 0.0D ? 1 : -1;
        if (commandDirection == targetDirection) {
            return command;
        }

        // A relative rotor-speed change persists after the pulse. Do not undo
        // the descent/ascent notch merely because its current speed crossed the
        // cruise target; wait until the calibrated response distance says that
        // braking must begin. This is equally valid for airborne endpoints and
        // landing endpoints because it depends only on the requested Y.
        double velocityTowardTarget = measuredRelativeVelocity * targetDirection;
        double brakingDistance = Math.max(
                RELATIVE_LIFT_FAR_REVERSAL_DISTANCE,
                Math.max(0.0D, velocityTowardTarget) * Math.max(1, responseTicks)
                        + LIFT_STOPPING_DISTANCE_MARGIN
        );
        if (errorDistance > brakingDistance) {
            return RelativeLiftCommand.NONE;
        }
        return command;
    }

    static boolean binaryMotionPowered(
            double errorMagnitude,
            double fullPowerThreshold,
            double mediumPowerThreshold,
            long gameTime
    ) {
        return binaryMotionPowered(
                errorMagnitude,
                fullPowerThreshold,
                mediumPowerThreshold,
                gameTime,
                false
        );
    }

    static boolean binaryMotionPowered(
            double errorMagnitude,
            double fullPowerThreshold,
            double mediumPowerThreshold,
            long gameTime,
            boolean fineFeather
    ) {
        if (fineFeather) {
            return Math.floorMod(gameTime, BINARY_FINE_PULSE_PERIOD_TICKS) == 0;
        }
        if (errorMagnitude >= fullPowerThreshold) {
            return true;
        }
        if (errorMagnitude >= mediumPowerThreshold) {
            return Math.floorMod(gameTime, BINARY_MEDIUM_PULSE_PERIOD_TICKS)
                    < BINARY_MEDIUM_PULSE_ON_TICKS;
        }
        return Math.floorMod(gameTime, BINARY_FINE_PULSE_PERIOD_TICKS) == 0;
    }

    static boolean shouldFeatherDirectControl(
            DriveFlightMode flightMode,
            EnumSet<DriveIntentType> controls,
            DriveSignalCategory category
    ) {
        return flightMode == DriveFlightMode.ROTORCRAFT
                && category == DriveSignalCategory.TURN
                && !hasVariableStrengthControl(controls, category);
    }

    private static boolean hasVariableStrengthControl(
            EnumSet<DriveIntentType> controls,
            DriveSignalCategory category
    ) {
        return switch (category) {
            case FORWARD_BACK -> controls.contains(DriveIntentType.FORWARD_BACK_TARGET_STRENGTH)
                    || controls.contains(DriveIntentType.FORWARD_BACK_INCREASE)
                    && controls.contains(DriveIntentType.FORWARD_BACK_DECREASE);
            case LEFT_RIGHT -> controls.contains(DriveIntentType.LEFT_RIGHT_TARGET_STRENGTH)
                    || controls.contains(DriveIntentType.LEFT_RIGHT_INCREASE)
                    && controls.contains(DriveIntentType.LEFT_RIGHT_DECREASE);
            case TURN -> controls.contains(DriveIntentType.TURN_TARGET_STRENGTH)
                    || controls.contains(DriveIntentType.TURN_INCREASE)
                    && controls.contains(DriveIntentType.TURN_DECREASE);
            default -> false;
        };
    }

    private static boolean hasDirectTurnPair(EnumSet<DriveIntentType> controls) {
        return controls.contains(DriveIntentType.TURN_CLOCKWISE)
                && controls.contains(DriveIntentType.TURN_ANTICLOCKWISE);
    }

    private static void finishRelativeLiftPulse(RelativeLiftState state) {
        state.lastCommand = state.command;
        state.command = RelativeLiftCommand.NONE;
        state.commandTicks = 0;
        state.observationTicks = 0;
    }

    private static int liftTrim(
            ServerLevel level,
            DriveModuleBlockEntity module,
            AdvancedTransponderBlockEntity controller,
            VehicleController vehicle,
            Vec3 targetPosition,
            double liftError
    ) {
        long gameTime = level.getGameTime();
        double currentY = vehicle.position().y;
        LiftCalibrationProfile profile = controller.liftCalibration()
                .filter(saved -> !saved.relativeControl())
                .orElse(null);
        LiftTrimState state = LIFT_TRIMS.get(module.moduleId());
        if (state != null && !isLiftHandoffSampleReusable(gameTime, state.lastSampleTick())) {
            LIFT_TRIMS.remove(module.moduleId());
            state = null;
        }
        if (state == null) {
            double initialVerticalVelocity = vehicle.linearVelocity()
                    .map(velocity -> velocity.y)
                    .orElse(0.0D);
            double initialDesiredVelocity = Math.clamp(
                    liftError / LIFT_HEIGHT_CORRECTION_HORIZON_TICKS,
                    -LIFT_MAX_DESCENT_CORRECTION_SPEED,
                    LIFT_MAX_ASCENT_CORRECTION_SPEED
            );
            double initialStrength = profile == null
                    ? INITIAL_LIFT_STRENGTH
                    : profile.strengthForVerticalVelocity(initialDesiredVelocity);
            LIFT_TRIMS.put(module.moduleId(), new LiftTrimState(
                    initialStrength,
                     currentY,
                     targetPosition.y,
                     initialVerticalVelocity,
                     initialVerticalVelocity,
                     0.0D,
                     0.0D,
                     gameTime,
                     0,
                     gameTime,
                     gameTime
            ));
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Drive lift trim {} initialized: output={} vehicleY={} targetY={} error={} velocity={}",
                    module.moduleId(),
                    initialStrength,
                    currentY,
                    targetPosition.y,
                    liftError,
                    initialVerticalVelocity
            );
            return LiftCalibrationService.modulatedSignal(initialStrength, gameTime, module.moduleId());
        }

        long elapsedTicks = Math.max(1L, gameTime - state.lastSampleTick());
        double measuredVelocity = (currentY - state.previousY()) / elapsedTicks;
        double smoothedVelocity = state.smoothedVelocity()
                + (measuredVelocity - state.smoothedVelocity()) * LIFT_VELOCITY_SMOOTHING;
        double measuredTargetVelocity = Math.clamp(
                 (targetPosition.y - state.previousTargetY()) / elapsedTicks,
                 -LIFT_MAX_TARGET_SPEED,
                 LIFT_MAX_TARGET_SPEED
         );
        double targetVelocity = state.smoothedTargetVelocity()
                + (measuredTargetVelocity - state.smoothedTargetVelocity())
                * LIFT_TARGET_VELOCITY_SMOOTHING;
        double relativeVelocity = smoothedVelocity - targetVelocity;
        double measuredVerticalAcceleration = measuredVelocity - state.previousMeasuredVelocity();
        double smoothedVerticalAcceleration = state.smoothedVerticalAcceleration()
                + (measuredVerticalAcceleration - state.smoothedVerticalAcceleration())
                 * LIFT_ACCELERATION_SMOOTHING;
        double correctionVelocity = Math.clamp(
                liftError / LIFT_HEIGHT_CORRECTION_HORIZON_TICKS,
                -LIFT_MAX_DESCENT_CORRECTION_SPEED,
                LIFT_MAX_ASCENT_CORRECTION_SPEED
        );
        double desiredVelocity = Math.clamp(
                targetVelocity + correctionVelocity,
                -LIFT_MAX_TARGET_SPEED,
                LIFT_MAX_TARGET_SPEED
        );
        double errorDirection = Math.signum(liftError);
        double errorDistance = Math.abs(liftError);
        double velocityTowardTarget = relativeVelocity * errorDirection;
        double accelerationTowardTarget = smoothedVerticalAcceleration * errorDirection;
        int predictionTicks = profile == null
                ? LIFT_PREDICTION_TICKS
                : Math.clamp(profile.responseTicks(), LIFT_PREDICTION_TICKS, LIFT_RESPONSE_OBSERVATION_TICKS);
        double calibratedBrakingAcceleration = profile == null
                ? LIFT_ACCELERATION_FLOOR
                : (relativeVelocity >= 0.0D ? profile.descentRate() : profile.ascentRate())
                        / profile.responseTicks();
        double predictedRelativeTravel = relativeVelocity * predictionTicks
                + 0.5D * smoothedVerticalAcceleration
                * predictionTicks * predictionTicks;
        double predictedRelativeVelocity = relativeVelocity
                + smoothedVerticalAcceleration * predictionTicks;
        double predictedError = liftError - predictedRelativeTravel;
        double predictedTravelTowardTarget = predictedRelativeTravel * errorDirection;
        double predictedVelocityTowardTarget = predictedRelativeVelocity * errorDirection;
        double desiredVelocityTowardTarget = correctionVelocity * errorDirection;
        double stoppingDistance = 0.0D;
        int strengthAdjustment = 0;
        String decision = "hold";

        if (errorDistance <= LIFT_ALTITUDE_TOLERANCE) {
            if (Math.abs(relativeVelocity) > LIFT_VELOCITY_TOLERANCE) {
                boolean alreadySlowing = relativeVelocity * smoothedVerticalAcceleration
                         < -LIFT_ACCELERATION_TOLERANCE;
                stoppingDistance = relativeVelocity * relativeVelocity
                        / (2.0D * Math.max(
                                Math.abs(smoothedVerticalAcceleration),
                                calibratedBrakingAcceleration
                        ));
                if (!alreadySlowing || stoppingDistance > LIFT_ALTITUDE_TOLERANCE) {
                    strengthAdjustment = relativeVelocity > 0.0D ? -1 : 1;
                    decision = "brake_near_target";
                }
            }
        } else if (shouldBrakeForPredictedRelativeSpeed(
                accelerationTowardTarget,
                predictedVelocityTowardTarget,
                desiredVelocityTowardTarget
        )) {
            // A delayed burner response can keep adding vertical speed after
            // the current closing speed looks reasonable. Trim one level now,
            // before that stored acceleration turns into an altitude overshoot.
            strengthAdjustment = errorDirection > 0.0D ? -1 : 1;
            decision = "brake_predicted_speed";
        } else if ((velocityTowardTarget > LIFT_VELOCITY_TOLERANCE
                || accelerationTowardTarget > LIFT_ACCELERATION_TOLERANCE)
                && predictedTravelTowardTarget >= errorDistance) {
            // The ship is still gaining motion toward this altitude and its
            // current trajectory reaches or passes it within the burner response
            // horizon. Back off one level now instead of waiting to overshoot.
            strengthAdjustment = errorDirection > 0.0D ? -1 : 1;
            decision = "brake_predicted_overshoot";
        } else if (velocityTowardTarget < -LIFT_VELOCITY_TOLERANCE) {
            if (accelerationTowardTarget > LIFT_ACCELERATION_TOLERANCE) {
                stoppingDistance = velocityTowardTarget * velocityTowardTarget
                        / (2.0D * accelerationTowardTarget);
                // The last trim change is already bending the motion back toward
                // the route. Do not stack another level while that response is live.
                decision = "await_away_motion_reversal";
            } else {
                strengthAdjustment = errorDirection > 0.0D ? 1 : -1;
                decision = "correct_away_motion";
            }
        } else if (velocityTowardTarget > LIFT_VELOCITY_TOLERANCE) {
            if (accelerationTowardTarget < -LIFT_ACCELERATION_TOLERANCE) {
                stoppingDistance = velocityTowardTarget * velocityTowardTarget
                        / (-2.0D * accelerationTowardTarget);
                if (stoppingDistance < errorDistance - LIFT_STOPPING_DISTANCE_MARGIN) {
                    strengthAdjustment = errorDirection > 0.0D ? 1 : -1;
                    decision = "avoid_stopping_short";
                } else if (stoppingDistance > errorDistance + LIFT_STOPPING_DISTANCE_MARGIN) {
                    strengthAdjustment = errorDirection > 0.0D ? -1 : 1;
                    decision = "increase_braking";
                } else {
                    decision = "braking_for_target";
                }
            } else {
                double brakingAcceleration = Math.max(
                        Math.abs(smoothedVerticalAcceleration),
                        calibratedBrakingAcceleration
                );
                stoppingDistance = velocityTowardTarget * velocityTowardTarget
                        / (2.0D * brakingAcceleration);
                if (stoppingDistance >= errorDistance - LIFT_STOPPING_DISTANCE_MARGIN) {
                    strengthAdjustment = errorDirection > 0.0D ? -1 : 1;
                    decision = "begin_braking";
                } else {
                    decision = "continue_toward_target";
                }
            }
        } else if (accelerationTowardTarget <= LIFT_ACCELERATION_TOLERANCE) {
            strengthAdjustment = errorDirection > 0.0D ? 1 : -1;
            decision = "start_toward_target";
        } else {
            decision = "await_toward_acceleration";
        }

        double adjustedStrength = state.strength();
        long lastAdjustmentTick = state.lastAdjustmentTick();
        int lastAdjustmentDirection = state.lastAdjustmentDirection();
        boolean reversesLastAdjustment = lastAdjustmentDirection != 0
                && strengthAdjustment == -lastAdjustmentDirection;
        boolean nearTargetAwayMotionDamping = shouldDampNearTargetAwayMotion(
                errorDistance,
                targetVelocity,
                velocityTowardTarget,
                accelerationTowardTarget,
                lastAdjustmentDirection,
                strengthAdjustment
        );
        if (nearTargetAwayMotionDamping) {
            decision = "damp_near_target_reversal";
        }
        boolean firstObservedBrake = lastAdjustmentDirection == 0
                && (((smoothedVelocity > LIFT_VELOCITY_TOLERANCE
                || smoothedVerticalAcceleration > LIFT_ACCELERATION_TOLERANCE)
                && strengthAdjustment < 0)
                || ((smoothedVelocity < -LIFT_VELOCITY_TOLERANCE
                || smoothedVerticalAcceleration < -LIFT_ACCELERATION_TOLERANCE)
                && strengthAdjustment > 0));
        int responseObservationTicks = profile == null
                ? LIFT_RESPONSE_OBSERVATION_TICKS
                : profile.responseTicks();
        int requiredObservationTicks = requiredLiftObservationTicks(
                lastAdjustmentDirection,
                strengthAdjustment,
                accelerationTowardTarget,
                responseObservationTicks,
                reversesLastAdjustment || firstObservedBrake
        );
        if (nearTargetAwayMotionDamping) {
            requiredObservationTicks = Math.min(requiredObservationTicks, LIFT_NEAR_TARGET_DAMPING_TICKS);
        }
        if (strengthAdjustment != 0
                && gameTime - lastAdjustmentTick >= requiredObservationTicks) {
            double previousStrength = adjustedStrength;
            double adjustmentSize = profile != null
                    ? nearTargetAwayMotionDamping ? LIFT_NEAR_TARGET_DAMPING_STEP : 0.25D
                    : 1.0D;
            adjustedStrength = Math.clamp(
                    adjustedStrength + strengthAdjustment * adjustmentSize,
                    (double) MINIMUM_ACTIVE_LIFT_STRENGTH,
                    15.0D
            );
            lastAdjustmentTick = gameTime;
            if (adjustedStrength != previousStrength) {
                lastAdjustmentDirection = strengthAdjustment;
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Drive lift trim {}: output {} -> {} decision={} vehicleY={} targetY={} error={} velocity={} targetVelocity={} desiredVelocity={} acceleration={} stoppingDistance={} predictedVelocity={} predictedError={}",
                        module.moduleId(),
                        previousStrength,
                        adjustedStrength,
                        decision,
                        currentY,
                        targetPosition.y,
                         liftError,
                         smoothedVelocity,
                         targetVelocity,
                         desiredVelocity,
                         smoothedVerticalAcceleration,
                        stoppingDistance,
                        predictedRelativeVelocity,
                        predictedError
                );
            }
        }
        long lastLearningTick = state.lastLearningTick();
        if (profile != null
                && gameTime - lastLearningTick >= 20 * 10
                && Math.abs(liftError) <= 1.0D
                && Math.abs(targetVelocity) <= 0.005D
                && Math.abs(relativeVelocity) <= 0.015D
                && Math.abs(smoothedVerticalAcceleration) <= 0.0003D) {
            double learnedNeutral = profile.neutralStrength();
            double correction = learnedNeutralCorrection(
                    profile,
                    adjustedStrength,
                    relativeVelocity
            );
            if (Math.abs(correction) >= 0.005D) {
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Drive lift neutral learning {}: neutral {} -> {} output={} drift={} targetVelocity={}",
                        module.moduleId(),
                        learnedNeutral,
                        learnedNeutral + correction,
                        adjustedStrength,
                        relativeVelocity,
                        targetVelocity
                );
            }
            controller.updateLearnedLiftNeutral(learnedNeutral + correction, gameTime);
            lastLearningTick = gameTime;
        }
        LIFT_TRIMS.put(module.moduleId(), new LiftTrimState(
                adjustedStrength,
                 currentY,
                 targetPosition.y,
                 measuredVelocity,
                 smoothedVelocity,
                 targetVelocity,
                 smoothedVerticalAcceleration,
                lastAdjustmentTick,
                lastAdjustmentDirection,
                lastLearningTick,
                gameTime
        ));
        return LiftCalibrationService.modulatedSignal(adjustedStrength, gameTime, module.moduleId());
    }

    static boolean isLiftHandoffSampleReusable(long gameTime, long lastSampleTick) {
        long ticksSinceSample = gameTime - lastSampleTick;
        return ticksSinceSample >= 0 && ticksSinceSample <= LIFT_HANDOFF_RETENTION_TICKS;
    }

    static boolean shouldBrakeForPredictedRelativeSpeed(
            double accelerationTowardTarget,
            double predictedVelocityTowardTarget,
            double desiredVelocityTowardTarget
    ) {
        return accelerationTowardTarget > LIFT_ACCELERATION_TOLERANCE
                && predictedVelocityTowardTarget
                > desiredVelocityTowardTarget + LIFT_VELOCITY_TOLERANCE;
    }

    static boolean shouldDampNearTargetAwayMotion(
            double errorDistance,
            double targetVelocity,
            double velocityTowardTarget,
            double accelerationTowardTarget,
            int lastAdjustmentDirection,
            int strengthAdjustment
    ) {
        return errorDistance <= LIFT_NEAR_TARGET_DAMPING_DISTANCE
                && Math.abs(targetVelocity) <= 0.005D
                && velocityTowardTarget < -LIFT_NEAR_TARGET_DAMPING_SPEED
                && accelerationTowardTarget < -LIFT_ACCELERATION_TOLERANCE
                && lastAdjustmentDirection != 0
                && strengthAdjustment == -lastAdjustmentDirection;
    }

    static int requiredLiftObservationTicks(
            int lastAdjustmentDirection,
            int strengthAdjustment,
            double accelerationTowardTarget,
            int responseObservationTicks,
            boolean expeditedBrake
    ) {
        if (expeditedBrake) {
            return LIFT_MINIMUM_ADJUSTMENT_TICKS;
        }
        boolean repeatsUnobservedCorrection = strengthAdjustment != 0
                && strengthAdjustment == lastAdjustmentDirection
                && accelerationTowardTarget <= LIFT_ACCELERATION_TOLERANCE;
        return repeatsUnobservedCorrection
                ? responseObservationTicks + LIFT_UNOBSERVED_RESPONSE_MARGIN_TICKS
                : responseObservationTicks;
    }

    static double learnedNeutralCorrection(
            LiftCalibrationProfile profile,
            double commandedStrength,
            double relativeVerticalVelocity
    ) {
        double correction = commandedStrength - profile.neutralStrength();
        if (Math.abs(correction) <= LIFT_NEUTRAL_COMMAND_TOLERANCE
                && Math.abs(relativeVerticalVelocity) > LIFT_NEUTRAL_DRIFT_DEAD_ZONE) {
            if (relativeVerticalVelocity > 0.0D) {
                double fraction = Math.clamp(
                        relativeVerticalVelocity / profile.descentRate(),
                        0.0D,
                        1.0D
                );
                correction += (profile.descendingStrength() - profile.neutralStrength()) * fraction;
            } else {
                double fraction = Math.clamp(
                        -relativeVerticalVelocity / profile.ascentRate(),
                        0.0D,
                        1.0D
                );
                correction += (profile.ascendingStrength() - profile.neutralStrength()) * fraction;
            }
        }
        return Math.clamp(
                correction,
                -LIFT_MAX_NEUTRAL_LEARNING_STEP,
                LIFT_MAX_NEUTRAL_LEARNING_STEP
        );
    }

    public static void beginRestoreAltitudeRecovery(Route route) {
        RESTORE_ALTITUDE_SETTLE.put(route.id(), 0);
        RESTORE_WARMUPS.put(route.id(), new RestoreWarmupState());
    }

    public static void resetRuntime() {
        RESTORE_ALTITUDE_SETTLE.clear();
        RESTORE_WARMUPS.clear();
        LIFT_TRIMS.clear();
        RELATIVE_LIFT_STATES.clear();
        RotorcraftDriveController.resetRuntime();
    }

    public static boolean restoreWarmupPending(Route route) {
        return RESTORE_WARMUPS.containsKey(route.id());
    }

    public static void cancelRestoreWarmup(Route route) {
        RESTORE_WARMUPS.remove(route.id());
    }

    /**
     * Warms a restored ship's lift circuit while external playback keeps the
     * physical body fixed. Relative rotor lift waits for the saved rotor speed
     * without replaying a takeoff pulse; analog lift holds calibrated neutral.
     */
    public static boolean tickRestoreWarmup(
            ServerLevel level,
            Route route,
            VehicleController vehicle,
            Vec3 holdPosition
    ) {
        RestoreWarmupState warmup = RESTORE_WARMUPS.get(route.id());
        if (warmup == null) {
            return false;
        }
        Optional<AdvancedTransponderBlockEntity> controller = advancedControllerFor(level, route);
        if (controller.isEmpty()) {
            return true;
        }
        UUID controllerId = controller.get().transponderId();
        DriveModuleLinkService.reconcileKnownModules(level, controllerId);
        List<DriveModuleBlockEntity> modules = DriveModuleLinkService.linkedModules(level, controllerId);
        EnumSet<DriveIntentType> controls = controlIntents(modules);
        Optional<DriveFlightMode> flightMode = DriveFlightMode.resolve(controls);
        if (flightMode.isEmpty() || !hasRequiredHorizontalControl(controls)) {
            modules.forEach(module -> module.setOutputSignal(0));
            return true;
        }

        LiftCalibrationService.release(controllerId);
        LiftCalibrationProfile profile = controller.get().liftCalibration().orElse(null);
        if (profile != null
                && profile.relativeControl()
                && flightMode.get() == DriveFlightMode.ROTORCRAFT) {
            return tickRelativeRestoreWarmup(
                    level,
                    route,
                    controller.get(),
                    modules,
                    vehicle,
                    holdPosition,
                    profile,
                    warmup
            );
        }
        applyOutputs(
                level,
                controller.get(),
                modules,
                vehicle,
                holdPosition,
                new DriveDemand(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D),
                flightMode.get(),
                false
        );
        int nextWarmedTicks = warmup.elapsedTicks + 1;
        int requiredWarmupTicks = controller.get().liftCalibration()
                .map(LiftCalibrationProfile::responseTicks)
                .orElse(LIFT_PREDICTION_TICKS);
        if (nextWarmedTicks < requiredWarmupTicks) {
            warmup.elapsedTicks = nextWarmedTicks;
            return true;
        }

        RESTORE_WARMUPS.remove(route.id());
        RESTORE_ALTITUDE_SETTLE.put(route.id(), 0);
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Drive playback {} completed reload control warmup after {} ticks; releasing physical hold",
                route.id().value(),
                nextWarmedTicks
        );
        return false;
    }

    private static boolean tickRelativeRestoreWarmup(
            ServerLevel level,
            Route route,
            AdvancedTransponderBlockEntity controller,
            List<DriveModuleBlockEntity> modules,
            VehicleController vehicle,
            Vec3 holdPosition,
            LiftCalibrationProfile profile,
            RestoreWarmupState warmup
    ) {
        applyRelativeRestoreOutputs(modules);
        warmup.elapsedTicks++;

        Optional<VehicleController.LiftRotorState> rotorState = vehicle.liftRotorState();
        double savedAirflow = controller.relativeLiftRotorAirflow().orElse(0.0D);
        boolean rotorReady;
        if (rotorState.isPresent() && rotorState.get().hasRotors()) {
            VehicleController.LiftRotorState rotors = rotorState.get();
            if (rotors.isAnyActive()) {
                warmup.activeRotorTicks++;
            } else {
                warmup.activeRotorTicks = 0;
            }
            rotorReady = savedAirflow > 0.0D
                    ? isRelativeRestoreRotorReady(rotors.averageAirflow(), savedAirflow)
                    : warmup.activeRotorTicks >= profile.responseTicks();
        } else {
            rotorReady = warmup.elapsedTicks >= profile.responseTicks();
        }

        double currentY = vehicle.position().y;
        double altitudeError = holdPosition.y - currentY;
        double verticalMovement = Double.isFinite(warmup.previousBodyY)
                ? currentY - warmup.previousBodyY
                : Double.POSITIVE_INFINITY;
        warmup.previousBodyY = currentY;
        double verticalVelocity = vehicle.linearVelocity().map(velocity -> velocity.y).orElse(0.0D);
        boolean bodyReady = isRestoreBodyReady(altitudeError, verticalMovement);
        warmup.readyTicks = rotorReady && bodyReady ? warmup.readyTicks + 1 : 0;

        if (!warmup.startedLogged) {
            warmup.startedLogged = true;
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Drive playback {} holding relative-lift vehicle for rotor recovery: savedAirflow={} savedRotors={} currentRotors={} holdY={} currentY={}",
                    route.id().value(),
                    savedAirflow,
                    controller.relativeLiftRotorCount(),
                    rotorState.map(VehicleController.LiftRotorState::rotorCount).orElse(0),
                    holdPosition.y,
                    vehicle.position().y
            );
        }
        if (warmup.readyTicks < RELATIVE_RESTORE_READY_TICKS) {
            if (warmup.elapsedTicks % 100 == 0) {
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Drive playback {} still holding for rotor recovery after {} ticks: airflow={} savedAirflow={} activeRotors={} altitudeError={} verticalMovement={} reportedVerticalVelocity={}",
                        route.id().value(),
                        warmup.elapsedTicks,
                        rotorState.map(VehicleController.LiftRotorState::averageAirflow).orElse(0.0D),
                        savedAirflow,
                        rotorState.map(VehicleController.LiftRotorState::activeRotorCount).orElse(0),
                        altitudeError,
                        verticalMovement,
                        verticalVelocity
                );
            }
            return true;
        }

        seedRelativeLiftStateAfterRestore(level, controller, vehicle, holdPosition);
        RESTORE_WARMUPS.remove(route.id());
        RESTORE_ALTITUDE_SETTLE.remove(route.id());
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Drive playback {} completed relative-lift reload recovery after {} ticks at airflow={}; releasing physical hold and resuming guidance",
                route.id().value(),
                warmup.elapsedTicks,
                rotorState.map(VehicleController.LiftRotorState::averageAirflow).orElse(0.0D)
        );
        return false;
    }

    static boolean isRelativeRestoreRotorReady(double currentAirflow, double savedAirflow) {
        if (!Double.isFinite(currentAirflow) || !Double.isFinite(savedAirflow) || savedAirflow <= 0.0D) {
            return false;
        }
        return Math.abs(currentAirflow) >= Math.abs(savedAirflow) * RELATIVE_RESTORE_AIRFLOW_FRACTION;
    }

    static boolean isRestoreBodyReady(double altitudeError, double verticalMovement) {
        return Double.isFinite(altitudeError)
                && Double.isFinite(verticalMovement)
                && Math.abs(altitudeError) <= RESTORE_ALTITUDE_TOLERANCE
                && Math.abs(verticalMovement) <= RESTORE_VERTICAL_MOVEMENT_TOLERANCE;
    }

    private static void applyRelativeRestoreOutputs(List<DriveModuleBlockEntity> modules) {
        for (DriveModuleBlockEntity module : modules) {
            DriveModuleConfig config = module.config().orElse(null);
            int signal = config != null && config.intent() == DriveIntentType.ROTATIONAL_FORCE_ENABLED ? 15 : 0;
            if (config != null && config.inverted() && config.supportsInversion()) {
                signal = 15 - signal;
            }
            module.setOutputSignal(signal);
        }
    }

    private static void seedRelativeLiftStateAfterRestore(
            ServerLevel level,
            AdvancedTransponderBlockEntity controller,
            VehicleController vehicle,
            Vec3 holdPosition
    ) {
        double currentY = vehicle.position().y;
        double verticalVelocity = vehicle.linearVelocity()
                .map(velocity -> verticalVelocityBlocksPerTick(velocity.y))
                .orElse(0.0D);
        RELATIVE_LIFT_STATES.put(controller.transponderId(), new RelativeLiftState(
                currentY,
                holdPosition.y,
                verticalVelocity,
                verticalVelocity,
                0.0D,
                0.0D,
                level.getGameTime(),
                false
        ));
    }

    private static boolean holdHorizontalDuringRestore(Route route, double liftError) {
        Integer settledTicks = RESTORE_ALTITUDE_SETTLE.get(route.id());
        if (settledTicks == null) {
            return false;
        }
        int nextSettledTicks = Math.abs(liftError) <= RESTORE_ALTITUDE_TOLERANCE
                ? settledTicks + 1
                : 0;
        if (nextSettledTicks >= RESTORE_ALTITUDE_SETTLE_TICKS) {
            RESTORE_ALTITUDE_SETTLE.remove(route.id());
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Drive playback {} completed reload altitude recovery; horizontal guidance resumed",
                    route.id().value()
            );
            return false;
        }
        RESTORE_ALTITUDE_SETTLE.put(route.id(), nextSettledTicks);
        return true;
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

    private record LiftTrimState(
             double strength,
             double previousY,
             double previousTargetY,
             double previousMeasuredVelocity,
             double smoothedVelocity,
             double smoothedTargetVelocity,
            double smoothedVerticalAcceleration,
            long lastAdjustmentTick,
            int lastAdjustmentDirection,
            long lastLearningTick,
            long lastSampleTick
    ) {
    }

    enum RelativeLiftCommand {
        NONE,
        INCREASE,
        DECREASE
    }

    private static final class RelativeLiftState {
        private double previousY;
        private double previousTargetY;
        private double previousMeasuredVelocity;
        private double smoothedVelocity;
        private double smoothedTargetVelocity;
        private double smoothedAcceleration;
        private long lastSampleTick;
        private RelativeLiftCommand command = RelativeLiftCommand.NONE;
        private RelativeLiftCommand lastCommand = RelativeLiftCommand.NONE;
        private int commandTicks;
        private int commandTargetTicks;
        private int observationTicks;
        private boolean takeoffRampPending;
        private boolean takeoffRampActive;
        private double takeoffStartY = Double.NaN;
        private boolean approachDescentActive;
        private boolean approachDescentNotchEstablished;
        private boolean approachBrakeActive;

        private RelativeLiftState(
                double previousY,
                double previousTargetY,
                double previousMeasuredVelocity,
                double smoothedVelocity,
                double smoothedTargetVelocity,
                double smoothedAcceleration,
                long lastSampleTick,
                boolean takeoffRampPending
        ) {
            this.previousY = previousY;
            this.previousTargetY = previousTargetY;
            this.previousMeasuredVelocity = previousMeasuredVelocity;
            this.smoothedVelocity = smoothedVelocity;
            this.smoothedTargetVelocity = smoothedTargetVelocity;
            this.smoothedAcceleration = smoothedAcceleration;
            this.lastSampleTick = lastSampleTick;
            this.takeoffRampPending = takeoffRampPending;
        }

        private void sample(double currentY, double targetY, long gameTime) {
            long elapsedTicks = Math.max(1L, gameTime - lastSampleTick);
            double measuredVelocity = (currentY - previousY) / elapsedTicks;
            double measuredTargetVelocity = Math.clamp(
                    (targetY - previousTargetY) / elapsedTicks,
                    -LIFT_MAX_TARGET_SPEED,
                    LIFT_MAX_TARGET_SPEED
            );
            double measuredAcceleration = (measuredVelocity - previousMeasuredVelocity) / elapsedTicks;
            smoothedVelocity += (measuredVelocity - smoothedVelocity) * LIFT_VELOCITY_SMOOTHING;
            smoothedTargetVelocity += (measuredTargetVelocity - smoothedTargetVelocity)
                    * LIFT_TARGET_VELOCITY_SMOOTHING;
            smoothedAcceleration += (measuredAcceleration - smoothedAcceleration)
                    * LIFT_ACCELERATION_SMOOTHING;
            previousY = currentY;
            previousTargetY = targetY;
            previousMeasuredVelocity = measuredVelocity;
            lastSampleTick = gameTime;
        }
    }

    private static final class RestoreWarmupState {
        private int elapsedTicks;
        private int activeRotorTicks;
        private int readyTicks;
        private boolean startedLogged;
        private double previousBodyY = Double.NaN;
    }
}

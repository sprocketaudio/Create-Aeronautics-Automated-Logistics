package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveFlightMode;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationProfile;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationState;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

/** Learns a physical ship's lift controls without moving it directly. */
public final class LiftCalibrationService {
    private static final int DEFAULT_RESPONSE_TICKS = 20 * 6;
    private static final int MEASUREMENT_TICKS = 20 * 3;
    private static final int MAX_CALIBRATION_TICKS = 20 * 240;
    private static final int EMERGENCY_ADJUSTMENT_TICKS = 20 * 2;
    private static final double MAX_CALIBRATION_DISPLACEMENT = 24.0D;
    private static final double MAX_RELATIVE_CALIBRATION_DISPLACEMENT = 72.0D;
    private static final double MAX_SAFE_VERTICAL_SPEED = 0.15D;
    private static final double SETTLED_VERTICAL_SPEED = 0.004D;
    private static final double SETTLED_VERTICAL_ACCELERATION = 0.0001D;
    private static final double VELOCITY_SMOOTHING = 0.2D;
    private static final double ACCELERATION_SMOOTHING = 0.1D;
    private static final double RESPONSE_ACCELERATION_THRESHOLD = 0.00015D;
    private static final double RELATIVE_TAKEOFF_HEIGHT = 1.0D;
    private static final double RELATIVE_RESPONSE_HEIGHT = 0.5D;
    private static final double RETURN_ALTITUDE_TOLERANCE = 0.75D;
    private static final double RETURN_SETTLED_SPEED = 0.01D;
    private static final int RETURN_SETTLED_TICKS = 20;
    private static final double MINIMUM_RETURN_DROP_PER_WINDOW = 0.25D;
    private static final int PWM_PERIOD_TICKS = 20;
    private static final int RELATIVE_CONTROL_PULSE_TICKS = 10;
    private static final int RELATIVE_CONTROL_MAX_PULSE_TICKS = 240;
    private static final int RELATIVE_PRE_TAKEOFF_IDLE_OBSERVATION_TICKS = 60;
    private static final double RELATIVE_PRE_TAKEOFF_MOTION_HEIGHT = 0.1D;
    private static final int RELATIVE_AIRFLOW_SAMPLE_TICKS = 10;
    private static final int RELATIVE_AIRFLOW_SETTLED_WINDOWS = 2;
    private static final double RELATIVE_AIRFLOW_MIN_SETTLED_RANGE = 0.005D;
    private static final int RELATIVE_HEIGHT_SAMPLE_TICKS = 20;
    private static final int RELATIVE_HEIGHT_SETTLED_WINDOWS = 3;
    private static final double RELATIVE_HEIGHT_SETTLED_RANGE = 0.25D;
    private static final double RELATIVE_TOUCHDOWN_HEIGHT_TOLERANCE = 0.5D;
    private static final double RELATIVE_HOVER_ALTITUDE_TOLERANCE = 0.75D;
    private static final double RELATIVE_SECOND_HOVER_PROBE_MAX_RISE = 16.0D;
    private static final double RELATIVE_SECOND_HOVER_SAFETY_MARGIN = 12.0D;
    private static final double RELATIVE_LANDING_BRAKE_TRIGGER_SPEED = 0.10D;
    private static final double RELATIVE_LANDING_BRAKE_RELEASE_SPEED = 0.055D;
    private static final int RELATIVE_LANDING_MIN_PREDICTION_TICKS = 10;
    private static final int RELATIVE_LANDING_MAX_PREDICTION_TICKS = 40;
    private static final int RELATIVE_TOUCHDOWN_SETTLED_TICKS = 10;
    private static final int RELATIVE_GROUND_UNWIND_MARGIN_TICKS = 10;
    private static final Map<UUID, RuntimeCalibration> ACTIVE = new HashMap<>();

    private LiftCalibrationService() {
    }

    public static StartResult start(ServerLevel level, AdvancedTransponderBlockEntity transponder) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            return StartResult.failure(Component.translatable(
                    "message.create_aeronautics_automated_logistics.lift_calibration.route_running"
            ));
        }
        Optional<VehicleController> resolvedVehicle = resolveVehicle(level, transponder);
        if (resolvedVehicle.isEmpty()
                || !resolvedVehicle.get().isLoaded(level)
                || !resolvedVehicle.get().isAssembled()) {
            return StartResult.failure(Component.translatable(
                    "message.create_aeronautics_automated_logistics.lift_calibration.no_vehicle"
            ));
        }
        List<DriveModuleBlockEntity> modules = DriveModuleLinkService.linkedModules(level, transponder.transponderId());
        List<DriveIntentType> intents = configuredIntents(modules);
        if (DriveFlightMode.hasMixedLiftModes(intents)) {
            return StartResult.failure(Component.translatable(
                    "message.create_aeronautics_automated_logistics.lift_calibration.mixed_lift_controls"
            ));
        }
        Optional<DriveFlightMode> flightMode = DriveFlightMode.resolve(intents);
        if (flightMode.isEmpty()) {
            return StartResult.failure(Component.translatable(
                    "message.create_aeronautics_automated_logistics.lift_calibration.no_lift_module"
            ));
        }

        VehicleController vehicle = resolvedVehicle.get();
        boolean relativeLiftControl = flightMode.get() == DriveFlightMode.ROTORCRAFT;
        if (relativeLiftControl) {
            Optional<VehicleController.LiftRotorState> rotorState = vehicle.liftRotorState();
            if (rotorState.isEmpty() || !rotorState.get().hasRotors()) {
                return StartResult.failure(Component.translatable(
                        "message.create_aeronautics_automated_logistics.lift_calibration.no_lift_rotor"
                ));
            }
            if (rotorState.get().isAnyActive()) {
                return StartResult.failure(Component.translatable(
                        "message.create_aeronautics_automated_logistics.lift_calibration.rotors_running"
                ));
            }
        }

        int initialStrength = Math.clamp((int) Math.round(transponder.liftCalibration()
                .map(LiftCalibrationProfile::neutralStrength)
                .orElse(8.0D)), 1, 15);
        int responseTicks = transponder.liftCalibration()
                .map(LiftCalibrationProfile::responseTicks)
                .orElse(DEFAULT_RESPONSE_TICKS);
        if (relativeLiftControl) {
            // A relative redstone control changes retained machinery state
            // before that machinery changes lift. Give that chained physical
            // response a full extra observation window.
            responseTicks = Math.max(responseTicks, DEFAULT_RESPONSE_TICKS * 2);
        }
        Vec3 position = vehicle.position();
        RuntimeCalibration runtime = new RuntimeCalibration(
                position.y,
                position.y,
                0.0D,
                0.0D,
                initialStrength,
                responseTicks,
                relativeLiftControl
        );
        ACTIVE.put(transponder.transponderId(), runtime);
        commandCalibrationModules(level, transponder.transponderId(), modules, initialStrength);
        transponder.updateLiftCalibrationRuntime(
                LiftCalibrationState.CALIBRATING,
                0,
                runtime.relativeLiftControl ? "relative_settling" : "testing_" + initialStrength
        );
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Lift calibration {} started at output={} y={} responseTicks={}",
                transponder.transponderId(), initialStrength, position.y, responseTicks
        );
        return StartResult.success(relativeLiftControl
                ? Component.translatable("message.create_aeronautics_automated_logistics.lift_calibration.started_rotorcraft")
                : Component.translatable(
                        "message.create_aeronautics_automated_logistics.lift_calibration.started",
                        initialStrength
                ));
    }

    public static boolean cancel(ServerLevel level, AdvancedTransponderBlockEntity transponder) {
        RuntimeCalibration removed = ACTIVE.remove(transponder.transponderId());
        clearCalibrationModules(level, transponder.transponderId());
        transponder.updateLiftCalibrationRuntime(
                transponder.liftCalibration().isPresent() ? LiftCalibrationState.CALIBRATED : LiftCalibrationState.UNCALIBRATED,
                transponder.liftCalibration().isPresent() ? 100 : 0,
                ""
        );
        return removed != null;
    }

    public static void release(UUID transponderId) {
        ACTIVE.remove(transponderId);
    }

    public static boolean isActive(UUID transponderId) {
        return ACTIVE.containsKey(transponderId);
    }

    public static int modulatedSignal(double strength, long gameTime, UUID phaseId) {
        double clamped = Math.clamp(strength, 1.0D, 15.0D);
        int lower = (int) Math.floor(clamped);
        int upper = Math.min(15, lower + 1);
        if (lower == upper) {
            return lower;
        }
        int upperTicks = (int) Math.round((clamped - lower) * PWM_PERIOD_TICKS);
        int phaseOffset = Math.floorMod(phaseId.hashCode(), PWM_PERIOD_TICKS);
        int phase = Math.floorMod((int) (gameTime % PWM_PERIOD_TICKS) + phaseOffset, PWM_PERIOD_TICKS);
        return phase < upperTicks ? upper : lower;
    }

    public static void tick(ServerLevel level, AdvancedTransponderBlockEntity transponder) {
        RuntimeCalibration runtime = ACTIVE.get(transponder.transponderId());
        if (runtime == null) {
            return;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            ACTIVE.remove(transponder.transponderId());
            return;
        }

        Optional<VehicleController> resolvedVehicle = resolveVehicle(level, transponder);
        List<DriveModuleBlockEntity> modules = DriveModuleLinkService.linkedModules(level, transponder.transponderId());
        List<DriveIntentType> intents = configuredIntents(modules);
        if (DriveFlightMode.hasMixedLiftModes(intents)) {
            fail(level, transponder, "mixed_lift_controls");
            return;
        }
        Optional<DriveFlightMode> flightMode = DriveFlightMode.resolve(intents);
        if (resolvedVehicle.isEmpty()
                || flightMode.isEmpty()
                || (flightMode.get() == DriveFlightMode.ROTORCRAFT) != runtime.relativeLiftControl) {
            fail(level, transponder, "hardware_unavailable");
            return;
        }
        VehicleController vehicle = resolvedVehicle.get();
        if (!vehicle.isLoaded(level) || !vehicle.isAssembled()) {
            fail(level, transponder, "vehicle_unavailable");
            return;
        }

        runtime.totalTicks++;
        runtime.ticksAtStrength++;
        double currentY = vehicle.position().y;

        if (runtime.relativeLiftControl
                && relativeSecondHoverProbeExceeded(runtime.initialY, runtime.relativeFirstHoverY, currentY,
                runtime.relativeCalibrationStage == RelativeCalibrationStage.FIND_SECOND_HOVER)) {
            beginRelativeReturnToFirstHover(transponder, runtime, currentY, false);
        }

        double maximumDisplacement = runtime.relativeLiftControl
                ? MAX_RELATIVE_CALIBRATION_DISPLACEMENT
                : MAX_CALIBRATION_DISPLACEMENT;
        if (runtime.totalTicks >= MAX_CALIBRATION_TICKS
                || (!runtime.returning && Math.abs(currentY - runtime.initialY) > maximumDisplacement)) {
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Lift calibration {} safety limit y={} startY={} displacement={} output={}",
                    transponder.transponderId(), currentY, runtime.initialY,
                    currentY - runtime.initialY, runtime.strength
            );
            fail(level, transponder, "safety_limit");
            return;
        }

        if (runtime.relativeLiftControl) {
            Optional<VehicleController.LiftRotorState> rotorState = vehicle.liftRotorState();
            if (rotorState.isEmpty() || !rotorState.get().hasRotors()) {
                fail(level, transponder, "no_lift_rotor");
                return;
            }
            double measuredVelocity = currentY - runtime.previousY;
            double measuredAcceleration = measuredVelocity - runtime.relativePreviousMeasuredVelocity;
            runtime.relativeSmoothedVelocity += (measuredVelocity - runtime.relativeSmoothedVelocity)
                    * VELOCITY_SMOOTHING;
            runtime.relativeSmoothedAcceleration += (
                    measuredAcceleration - runtime.relativeSmoothedAcceleration
            ) * ACCELERATION_SMOOTHING;
            runtime.previousY = currentY;
            runtime.relativePreviousMeasuredVelocity = measuredVelocity;
            tickRelativeLiftControl(level, transponder, runtime, modules, rotorState.get(), currentY);
            return;
        }

        double measuredVelocity = currentY - runtime.previousY;
        double measuredAcceleration = measuredVelocity - runtime.previousMeasuredVelocity;
        runtime.smoothedVelocity += (measuredVelocity - runtime.smoothedVelocity) * VELOCITY_SMOOTHING;
        runtime.smoothedAcceleration += (measuredAcceleration - runtime.smoothedAcceleration)
                * ACCELERATION_SMOOTHING;
        runtime.previousY = currentY;
        runtime.previousMeasuredVelocity = measuredVelocity;

        if (runtime.observedResponseTicks == 0
                && runtime.ticksAtStrength >= 2
                && Math.abs(runtime.smoothedAcceleration) >= RESPONSE_ACCELERATION_THRESHOLD) {
            runtime.observedResponseTicks = runtime.ticksAtStrength;
            runtime.maximumObservedResponseTicks = Math.max(
                    runtime.maximumObservedResponseTicks,
                    runtime.observedResponseTicks
            );
        }

        if (runtime.returning) {
            tickReturn(level, transponder, runtime, modules, currentY);
            return;
        }

        if (runtime.ticksAtStrength >= EMERGENCY_ADJUSTMENT_TICKS
                && Math.abs(runtime.smoothedVelocity) >= MAX_SAFE_VERTICAL_SPEED) {
            int saferStrength = runtime.smoothedVelocity > 0.0D
                    ? runtime.strength - 1
                    : runtime.strength + 1;
            changeStrength(runtime, saferStrength);
            commandCalibrationModules(level, transponder.transponderId(), modules, runtime.strength);
            updateProgress(transponder, runtime);
            return;
        }

        if (runtime.ticksAtStrength >= runtime.responseTicks) {
            runtime.velocitySum += runtime.smoothedVelocity;
            runtime.velocitySamples++;
        }
        if (runtime.ticksAtStrength < runtime.responseTicks + MEASUREMENT_TICKS) {
            commandCalibrationModules(level, transponder.transponderId(), modules, runtime.strength);
            updateProgress(transponder, runtime);
            return;
        }

        double sampledVelocity = runtime.velocitySamples == 0
                ? runtime.smoothedVelocity
                : runtime.velocitySum / runtime.velocitySamples;
        double classifiedVelocity = classifiedCalibrationVelocity(
                sampledVelocity,
                runtime.smoothedAcceleration,
                runtime.responseTicks
        );
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Lift calibration {} sampled output={} velocity={} acceleration={} projectedVelocity={} y={}",
                transponder.transponderId(), runtime.strength, sampledVelocity,
                runtime.smoothedAcceleration, classifiedVelocity, currentY
        );
        if (Math.abs(sampledVelocity) <= SETTLED_VERTICAL_SPEED
                && Math.abs(runtime.smoothedAcceleration) <= SETTLED_VERTICAL_ACCELERATION) {
            runtime.neutralCandidateStrength = runtime.strength;
            int nextStrength;
            if (runtime.risingStrength < 1 && runtime.strength < 15) {
                nextStrength = runtime.strength + 1;
            } else if (runtime.fallingStrength < 1 && runtime.strength > 1) {
                nextStrength = runtime.strength - 1;
            } else {
                fail(level, transponder, "no_hover_strength");
                return;
            }
            changeStrength(runtime, nextStrength);
            commandCalibrationModules(level, transponder.transponderId(), modules, runtime.strength);
            updateProgress(transponder, runtime);
            return;
        }
        if (classifiedVelocity > 0.0D) {
            runtime.risingStrength = runtime.strength;
            runtime.risingVelocity = classifiedVelocity;
        } else {
            runtime.fallingStrength = runtime.strength;
            runtime.fallingVelocity = classifiedVelocity;
        }

        if (runtime.hasBracket()) {
            double neutral = interpolateNeutral(runtime);
            beginReturn(level, transponder, runtime, neutral);
            return;
        }

        int nextStrength;
        if (classifiedVelocity > 0.0D && runtime.neutralCandidateStrength > 1) {
            nextStrength = runtime.neutralCandidateStrength - 1;
        } else if (classifiedVelocity < 0.0D && runtime.neutralCandidateStrength >= 1
                && runtime.neutralCandidateStrength < 15) {
            nextStrength = runtime.neutralCandidateStrength + 1;
        } else {
            nextStrength = classifiedVelocity > 0.0D ? runtime.strength - 1 : runtime.strength + 1;
        }
        if (nextStrength < 1 || nextStrength > 15) {
            fail(level, transponder, "no_hover_strength");
            return;
        }
        changeStrength(runtime, nextStrength);
        commandCalibrationModules(level, transponder.transponderId(), modules, runtime.strength);
        updateProgress(transponder, runtime);
    }

    private static void beginReturn(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime,
            double neutralStrength
    ) {
        int responseTicks = runtime.maximumObservedResponseTicks == 0
                ? runtime.responseTicks
                : Math.clamp(runtime.maximumObservedResponseTicks, 40, 240);
        runtime.pendingProfile = new LiftCalibrationProfile(
                neutralStrength,
                runtime.risingStrength,
                runtime.risingVelocity,
                runtime.fallingStrength,
                -runtime.fallingVelocity,
                responseTicks,
                level.getGameTime()
        );
        runtime.returning = true;
        runtime.returnSettledTicks = 0;
        runtime.returnStrength = Math.max(0.0D, Math.min(
                runtime.pendingProfile.descendingStrength(),
                runtime.pendingProfile.neutralStrength() - 0.5D
        ));
        runtime.returnWindowStartY = runtime.previousY;
        runtime.returnWindowTicks = 0;
        transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 95, "returning");
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Lift calibration {} returning to y={} neutral={} responseTicks={} falling=[{},{}] rising=[{},{}]",
                transponder.transponderId(), runtime.initialY, neutralStrength, responseTicks,
                runtime.fallingStrength, runtime.fallingVelocity,
                runtime.risingStrength, runtime.risingVelocity
        );
    }

    /** Calibrates generic paired relative controls while reading the ship's actual lift rotors. */
    private static void tickRelativeLiftControl(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime,
            List<DriveModuleBlockEntity> modules,
            VehicleController.LiftRotorState rotorState,
            double currentY
    ) {
        runtime.relativePhaseTicks++;
        tickRelativeAirflowTracking(runtime, rotorState.averageAirflow());
        if (runtime.relativeCalibrationStage == RelativeCalibrationStage.FIND_SECOND_HOVER) {
            runtime.relativeSecondProbeTicks++;
        }
        switch (runtime.relativePhase) {
            case SETTLING -> {
                commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.NONE);
                if (runtime.relativePhaseTicks >= runtime.responseTicks) {
                    if (rotorState.isAnyActive()) {
                        fail(level, transponder, "rotors_running");
                        return;
                    }
                    beginRelativePulse(runtime, RelativeLiftPhase.PULSE_INCREASE);
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} settled baseline y={}; starting increase pulses",
                            transponder.transponderId(), currentY
                    );
                    transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 20, "relative_increase");
                }
            }
            case PULSE_INCREASE -> {
                if (!runtime.relativeRotorPulseLearned && rotorState.isAnyActive()) {
                    commandRelativeCalibrationModules(
                            level, transponder.transponderId(), modules, RelativeLiftDirection.NONE
                    );
                    int learnedPulseTicks = Math.max(1, runtime.relativeCurrentPulseTicks);
                    runtime.relativeRotorPulseLearned = true;
                    runtime.relativeLearnedIncreasePulseTicks = learnedPulseTicks;
                    runtime.relativeIncreasePulseTargetTicks = learnedPulseTicks;
                    runtime.relativeDecreasePulseTargetTicks = learnedPulseTicks;
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} detected {} of {} vertical rotors moving after {} powered ticks",
                            transponder.transponderId(), rotorState.activeRotorCount(), rotorState.rotorCount(),
                            learnedPulseTicks
                    );
                    completeRelativePulse(runtime, RelativeLiftDirection.INCREASE);
                    return;
                }
                commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.INCREASE);
                runtime.relativeIncreaseTicksApplied++;
                if (runtime.relativeCalibrationStage == RelativeCalibrationStage.FIND_SECOND_HOVER) {
                    runtime.relativeSecondIncreaseTicksApplied++;
                }
                runtime.relativeCurrentPulseTicks++;
                if (!runtime.relativeRotorPulseLearned) {
                    if (runtime.relativeCurrentPulseTicks >= RELATIVE_CONTROL_MAX_PULSE_TICKS) {
                        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                "Relative lift calibration {} did not start {} vertical rotors after {} powered ticks",
                                transponder.transponderId(), rotorState.rotorCount(),
                                runtime.relativeCurrentPulseTicks
                        );
                        fail(level, transponder, "no_rotor_response");
                    }
                    return;
                }
                if (runtime.relativePhaseTicks == 1) {
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} increase pulse {} targetTicks={} fromY={} y={}",
                            transponder.transponderId(), runtime.relativePulseCount,
                            runtime.relativeIncreasePulseTargetTicks,
                            runtime.relativeHeightBeforePulse, currentY
                    );
                }
                boolean reachedTakeoffHeight = !runtime.relativeBaselineEstablished
                        && currentY - runtime.initialY >= RELATIVE_TAKEOFF_HEIGHT;
                if (runtime.relativePhaseTicks >= runtime.relativeIncreasePulseTargetTicks
                        || reachedTakeoffHeight) {
                    completeRelativePulse(runtime, RelativeLiftDirection.INCREASE);
                }
            }
            case OBSERVE_INCREASE -> {
                commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.NONE);
                observeRelativeResponse(runtime, RelativeLiftDirection.INCREASE, currentY);
                if (runtime.relativeReverseResponseObserved) {
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} confirmed reversed Increase response after {} observation ticks: startY={} currentY={}",
                            transponder.transponderId(), runtime.relativePhaseTicks,
                            runtime.relativeHeightBeforePulse, currentY
                    );
                    fail(level, transponder, "increase_reversed");
                    return;
                }
                if (!runtime.relativeBaselineEstablished) {
                    if (currentY - runtime.initialY >= RELATIVE_TAKEOFF_HEIGHT) {
                        runtime.relativeBaselineEstablished = true;
                        runtime.relativeResponseObserved = true;
                        runtime.relativeTakeoffIncreaseTicks = runtime.relativeIncreaseTicksApplied;
                        runtime.relativePhaseTicks = 0;
                        resetRelativeHeightTracking(runtime, currentY);
                        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                "Relative lift calibration {} took off after {} powered ticks y={}",
                                transponder.transponderId(), runtime.relativeTakeoffIncreaseTicks,
                                currentY
                        );
                    } else if (shouldAdvancePreTakeoffObservation(
                            runtime.relativePhaseTicks,
                            runtime.responseTicks,
                            currentY - runtime.relativeHeightBeforePulse,
                            runtime.relativeAirflowSettledWindows >= RELATIVE_AIRFLOW_SETTLED_WINDOWS
                    )) {
                        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                "Relative lift calibration {} found no lift at airflow={} after {} ticks; testing next notch",
                                transponder.transponderId(), runtime.relativeCurrentAirflow,
                                runtime.relativePhaseTicks
                        );
                        beginRelativePulse(runtime, RelativeLiftPhase.PULSE_INCREASE);
                        return;
                    } else {
                        return;
                    }
                }
                tickRelativeHoverObservation(
                        level,
                        transponder,
                        runtime,
                        RelativeLiftDirection.INCREASE,
                        currentY
                );
            }
            case PULSE_DECREASE -> {
                commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.DECREASE);
                runtime.relativeDecreaseTicksApplied++;
                runtime.relativeCurrentPulseTicks++;
                if (runtime.relativePhaseTicks == 1) {
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} decrease pulse {} targetTicks={} fromY={} y={}",
                            transponder.transponderId(), runtime.relativePulseCount,
                            runtime.relativeDecreasePulseTargetTicks,
                            runtime.relativeHeightBeforePulse, currentY
                    );
                }
                if (runtime.relativePhaseTicks >= runtime.relativeDecreasePulseTargetTicks) {
                    completeRelativePulse(runtime, RelativeLiftDirection.DECREASE);
                }
            }
            case OBSERVE_DECREASE -> {
                commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.NONE);
                observeRelativeResponse(runtime, RelativeLiftDirection.DECREASE, currentY);
                if (runtime.relativeReverseResponseObserved) {
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} confirmed reversed Decrease response after {} observation ticks: startY={} currentY={}",
                            transponder.transponderId(), runtime.relativePhaseTicks,
                            runtime.relativeHeightBeforePulse, currentY
                    );
                    fail(level, transponder, "decrease_reversed");
                    return;
                }
                tickRelativeHoverObservation(
                        level,
                        transponder,
                        runtime,
                        RelativeLiftDirection.DECREASE,
                        currentY
                );
            }
            case LANDING -> {
                double heightAboveStart = currentY - runtime.initialY;
                if (!runtime.relativeTouchdownConfirmed) {
                    boolean heightSettled = relativeHeightHasSettled(runtime, currentY);
                    boolean touchdownCandidate = heightAboveStart <= RELATIVE_TOUCHDOWN_HEIGHT_TOLERANCE
                            && heightSettled;
                    runtime.relativeTouchdownSettledTicks = touchdownCandidate
                            ? runtime.relativeTouchdownSettledTicks + 1
                            : 0;
                    if (runtime.relativeTouchdownSettledTicks >= RELATIVE_TOUCHDOWN_SETTLED_TICKS) {
                        runtime.relativeTouchdownConfirmed = true;
                        runtime.relativeLandingCommand = RelativeLiftDirection.NONE;
                        runtime.relativeLandingPulseTicks = 0;
                        runtime.relativeLandingWaitTicks = 0;
                        runtime.relativeGroundUnwindTicks = 0;
                        runtime.relativeGroundUnwindTargetTicks = Math.max(
                                0,
                                runtime.relativeIncreaseTicksApplied - runtime.relativeDecreaseTicksApplied
                        ) + RELATIVE_GROUND_UNWIND_MARGIN_TICKS;
                        commandRelativeCalibrationModules(
                                level, transponder.transponderId(), modules, RelativeLiftDirection.NONE
                        );
                        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                "Relative lift calibration {} touchdown confirmed y={}; stopping rotors",
                                transponder.transponderId(), currentY
                        );
                        transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 98, "stopping_rotors");
                        return;
                    }

                    if (runtime.relativeLandingCommand != RelativeLiftDirection.NONE
                            && runtime.relativeLandingPulseTicks < relativePulseTicks(
                            runtime, runtime.relativeLandingCommand
                    )) {
                        commandRelativeCalibrationModules(
                                level, transponder.transponderId(), modules, runtime.relativeLandingCommand
                        );
                        if (runtime.relativeLandingCommand == RelativeLiftDirection.INCREASE) {
                            runtime.relativeIncreaseTicksApplied++;
                        } else {
                            runtime.relativeDecreaseTicksApplied++;
                        }
                        runtime.relativeLandingPulseTicks++;
                        return;
                    }

                    commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.NONE);
                    runtime.relativeLandingWaitTicks++;
                    int brakeAdjustment = relativeLandingBrakeAdjustment(
                            runtime.relativeLandingAtHigherSpeed,
                            runtime.relativeSmoothedVelocity,
                            runtime.relativeSmoothedAcceleration,
                            runtime.relativeLearnedResponseTicks
                    );
                    if (brakeAdjustment != 0) {
                        runtime.relativeLandingCommand = brakeAdjustment > 0
                                ? RelativeLiftDirection.INCREASE
                                : RelativeLiftDirection.DECREASE;
                        runtime.relativeLandingAtHigherSpeed = brakeAdjustment > 0;
                        runtime.relativeLandingPulseTicks = 0;
                        runtime.relativeLandingWaitTicks = 0;
                        resetRelativeHeightTracking(runtime, currentY);
                        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                "Relative lift calibration {} landing brake command={} y={} height={} velocity={} acceleration={}",
                                transponder.transponderId(), runtime.relativeLandingCommand,
                                currentY, heightAboveStart, runtime.relativeSmoothedVelocity,
                                runtime.relativeSmoothedAcceleration
                        );
                        return;
                    }
                    if (!runtime.relativeLandingAtHigherSpeed
                            && runtime.relativeLandingWaitTicks >= runtime.relativeLearnedResponseTicks
                            && heightSettled) {
                        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                                "Relative lift calibration {} settled during landing y={}; applying next lower notch",
                                transponder.transponderId(), currentY
                        );
                        runtime.relativeLandingCommand = RelativeLiftDirection.DECREASE;
                        runtime.relativeLandingPulseTicks = 0;
                        runtime.relativeLandingWaitTicks = 0;
                        resetRelativeHeightTracking(runtime, currentY);
                    }
                    transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 95, "returning");
                    return;
                }

                // Relative controls are intentionally generic. Once touchdown
                // is confirmed, hold Decrease continuously for at least the
                // complete Increase time used by this calibration. Bounded
                // controls can settle at their minimum without any knowledge
                // of the redstone component behind the Drive Modules.
                if (runtime.relativeGroundUnwindTicks < runtime.relativeGroundUnwindTargetTicks) {
                    commandRelativeCalibrationModules(
                            level, transponder.transponderId(), modules, RelativeLiftDirection.DECREASE
                    );
                    runtime.relativeGroundUnwindTicks++;
                    transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 98, "stopping_rotors");
                    return;
                }
                commandRelativeCalibrationModules(level, transponder.transponderId(), modules, RelativeLiftDirection.NONE);
                if (runtime.pendingProfile != null && runtime.pendingProfile.relativeControl()) {
                    runtime.pendingProfile = runtime.pendingProfile.withStopDecreaseTicks(
                            runtime.relativeGroundUnwindTicks
                    );
                }
                finish(level, transponder, runtime);
                return;
            }
        }
    }

    private static void tickRelativeHoverObservation(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime,
            RelativeLiftDirection observedDirection,
            double currentY
    ) {
        boolean settled = relativeHeightHasSettled(runtime, currentY);
        int requiredObservationTicks = runtime.relativeResponseObserved
                ? runtime.relativeLearnedResponseTicks
                : runtime.responseTicks;
        if (runtime.relativePhaseTicks < requiredObservationTicks) {
            return;
        }

        switch (runtime.relativeCalibrationStage) {
            case FIND_FIRST_HOVER -> {
                if (!settled) {
                    return;
                }
                runtime.relativeFirstHoverY = currentY;
                runtime.relativeCalibrationStage = RelativeCalibrationStage.FIND_SECOND_HOVER;
                runtime.relativeStageResponseObserved = false;
                runtime.relativeSecondIncreaseTicksApplied = 0;
                runtime.relativeSecondProbeStartY = currentY;
                runtime.relativeSecondProbeTicks = 0;
                runtime.relativeLearnedDecreasePulseTicks = 0;
                runtime.relativeIncreasePulseTargetTicks = relativePulseTicks(
                        runtime, RelativeLiftDirection.INCREASE
                );
                runtime.relativeDecreasePulseTargetTicks = runtime.relativeIncreasePulseTargetTicks;
                runtime.relativeAscentRate = 0.0D;
                runtime.relativeDescentRate = 0.0D;
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Relative lift calibration {} settled at first hover y={}; testing a faster notch",
                        transponder.transponderId(), currentY
                );
                beginRelativePulse(runtime, RelativeLiftPhase.PULSE_INCREASE);
                transponder.updateLiftCalibrationRuntime(
                        LiftCalibrationState.CALIBRATING,
                        45,
                        "relative_increase"
                );
            }
            case FIND_SECOND_HOVER -> {
                if (!runtime.relativeStageResponseObserved) {
                    if (!settled) {
                        return;
                    }
                    beginRelativePulse(runtime, RelativeLiftPhase.PULSE_INCREASE);
                    transponder.updateLiftCalibrationRuntime(
                            LiftCalibrationState.CALIBRATING,
                            65,
                            "relative_increase"
                    );
                    return;
                }
                if (!settled) {
                    return;
                }
                beginRelativeReturnToFirstHover(transponder, runtime, currentY, true);
            }
            case RETURN_TO_FIRST_HOVER -> {
                double firstHoverError = currentY - runtime.relativeFirstHoverY;
                if (settled && Math.abs(firstHoverError) <= RELATIVE_HOVER_ALTITUDE_TOLERANCE) {
                    CreateAeronauticsAutomatedLogistics.debugDriveModules(
                            "Relative lift calibration {} returned to first hover y={} targetY={}",
                            transponder.transponderId(), currentY, runtime.relativeFirstHoverY
                    );
                    finishRelativeCalibration(level, transponder, runtime);
                    return;
                }
                if (!runtime.relativeStageResponseObserved) {
                    if (!settled) {
                        return;
                    }
                    if (observedDirection == RelativeLiftDirection.INCREASE) {
                        beginRelativePulse(runtime, RelativeLiftPhase.PULSE_INCREASE);
                    } else {
                        beginRelativePulse(runtime, RelativeLiftPhase.PULSE_DECREASE);
                    }
                    return;
                }
                if (!settled) {
                    return;
                }
                runtime.relativeStageResponseObserved = false;
                if (firstHoverError > 0.0D) {
                    runtime.relativeDecreasePulseTargetTicks = relativePulseTicks(
                            runtime, RelativeLiftDirection.DECREASE
                    );
                    beginRelativePulse(runtime, RelativeLiftPhase.PULSE_DECREASE);
                    transponder.updateLiftCalibrationRuntime(
                            LiftCalibrationState.CALIBRATING, 75, "relative_decrease"
                    );
                } else {
                    runtime.relativeIncreasePulseTargetTicks = relativePulseTicks(
                            runtime, RelativeLiftDirection.INCREASE
                    );
                    beginRelativePulse(runtime, RelativeLiftPhase.PULSE_INCREASE);
                    transponder.updateLiftCalibrationRuntime(
                            LiftCalibrationState.CALIBRATING, 65, "relative_increase"
                    );
                }
            }
        }
    }

    private static void beginRelativeReturnToFirstHover(
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime,
            double currentY,
            boolean settledAtSecondHover
    ) {
        double measuredRise = Math.max(0.0D, currentY - runtime.relativeSecondProbeStartY);
        double measuredRate = measuredRise / Math.max(1, runtime.relativeSecondProbeTicks);
        runtime.relativeAscentRate = Math.max(runtime.relativeAscentRate, measuredRate);
        runtime.relativeSecondHoverY = currentY;
        runtime.relativeCalibrationStage = RelativeCalibrationStage.RETURN_TO_FIRST_HOVER;
        runtime.relativeStageResponseObserved = false;
        runtime.relativeDecreasePulseTargetTicks = Math.max(
                1,
                runtime.relativeSecondIncreaseTicksApplied
        );
        if (settledAtSecondHover) {
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Relative lift calibration {} settled at second hover y={} (first={}); returning one notch",
                    transponder.transponderId(), currentY, runtime.relativeFirstHoverY
            );
        } else {
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Relative lift calibration {} faster notch exceeded the {} block probe band at y={} (first={}); returning one notch",
                    transponder.transponderId(), relativeSecondHoverProbeRiseLimit(
                            runtime.initialY,
                            runtime.relativeFirstHoverY
                    ),
                    currentY, runtime.relativeFirstHoverY
            );
        }
        beginRelativePulse(runtime, RelativeLiftPhase.PULSE_DECREASE);
        transponder.updateLiftCalibrationRuntime(
                LiftCalibrationState.CALIBRATING,
                75,
                "relative_decrease"
        );
    }

    static boolean relativeSecondHoverProbeExceeded(
            double initialY,
            double firstHoverY,
            double currentY,
            boolean findingSecondHover
    ) {
        return findingSecondHover
                && currentY - firstHoverY >= relativeSecondHoverProbeRiseLimit(initialY, firstHoverY);
    }

    static double relativeSecondHoverProbeRiseLimit(double initialY, double firstHoverY) {
        double remainingSafeRise = MAX_RELATIVE_CALIBRATION_DISPLACEMENT
                - Math.max(0.0D, firstHoverY - initialY)
                - RELATIVE_SECOND_HOVER_SAFETY_MARGIN;
        return Math.max(
                RELATIVE_RESPONSE_HEIGHT,
                Math.min(RELATIVE_SECOND_HOVER_PROBE_MAX_RISE, remainingSafeRise)
        );
    }

    static boolean shouldAdvancePreTakeoffObservation(
            int observationTicks,
            int responseTicks,
            double heightDelta,
            boolean airflowSettled
    ) {
        if (observationTicks >= responseTicks) {
            return true;
        }
        return observationTicks >= Math.min(responseTicks, RELATIVE_PRE_TAKEOFF_IDLE_OBSERVATION_TICKS)
                && Math.abs(heightDelta) < RELATIVE_PRE_TAKEOFF_MOTION_HEIGHT
                && airflowSettled;
    }

    static int relativeLandingBrakeAdjustment(
            boolean atHigherSpeed,
            double verticalVelocity,
            double verticalAcceleration,
            int responseTicks
    ) {
        int predictionTicks = Math.clamp(
                Math.max(1, responseTicks) / 4,
                RELATIVE_LANDING_MIN_PREDICTION_TICKS,
                RELATIVE_LANDING_MAX_PREDICTION_TICKS
        );
        double usefulAcceleration = Math.abs(verticalAcceleration) >= SETTLED_VERTICAL_ACCELERATION
                ? verticalAcceleration
                : 0.0D;
        double predictedVelocity = verticalVelocity + usefulAcceleration * predictionTicks;
        if (atHigherSpeed) {
            return predictedVelocity >= -RELATIVE_LANDING_BRAKE_RELEASE_SPEED
                    ? -1
                    : 0;
        }
        return predictedVelocity <= -RELATIVE_LANDING_BRAKE_TRIGGER_SPEED
                ? 1
                : 0;
    }

    private static void observeRelativeResponse(
            RuntimeCalibration runtime,
            RelativeLiftDirection direction,
            double currentY
    ) {
        double heightDelta = currentY - runtime.relativeHeightBeforePulse;
        runtime.relativeReverseResponseObserved = !runtime.relativeResponseObserved
                && confirmedReversedRelativeResponse(
                runtime.relativePhaseTicks,
                runtime.responseTicks,
                direction == RelativeLiftDirection.INCREASE,
                heightDelta
        );
        boolean observed = direction == RelativeLiftDirection.INCREASE
                ? heightDelta >= RELATIVE_RESPONSE_HEIGHT
                : heightDelta <= -RELATIVE_RESPONSE_HEIGHT;
        if (!observed || runtime.relativeResponseObserved) {
            return;
        }

        runtime.relativeResponseObserved = true;
        double measuredRate = Math.abs(heightDelta) / Math.max(1, runtime.relativePhaseTicks);
        if (direction == RelativeLiftDirection.INCREASE
                && runtime.relativeCalibrationStage == RelativeCalibrationStage.FIND_SECOND_HOVER) {
            runtime.relativeAscentRate = Math.max(runtime.relativeAscentRate, measuredRate);
        } else if (direction == RelativeLiftDirection.DECREASE
                && runtime.relativeCalibrationStage == RelativeCalibrationStage.RETURN_TO_FIRST_HOVER) {
            runtime.relativeDescentRate = Math.max(runtime.relativeDescentRate, measuredRate);
        }
        runtime.relativeLearnedResponseTicks = Math.clamp(
                Math.max(runtime.relativeLearnedResponseTicks, runtime.relativePhaseTicks + 20),
                40,
                runtime.responseTicks
        );
        if (direction == RelativeLiftDirection.INCREASE) {
            runtime.relativeLearnedIncreasePulseTicks = runtime.relativeLearnedIncreasePulseTicks == 0
                    ? runtime.relativeLastPulseTicks
                    : Math.min(runtime.relativeLearnedIncreasePulseTicks, runtime.relativeLastPulseTicks);
        } else {
            runtime.relativeLearnedDecreasePulseTicks = runtime.relativeLearnedDecreasePulseTicks == 0
                    ? runtime.relativeLastPulseTicks
                    : Math.min(runtime.relativeLearnedDecreasePulseTicks, runtime.relativeLastPulseTicks);
        }
        if (runtime.relativeCalibrationStage != RelativeCalibrationStage.FIND_FIRST_HOVER) {
            runtime.relativeStageResponseObserved = true;
        }
    }

    static boolean confirmedReversedRelativeResponse(
            int observationTicks,
            int learnedResponseTicks,
            boolean increaseCommand,
            double heightDelta
    ) {
        if (observationTicks < learnedResponseTicks) {
            return false;
        }
        return increaseCommand
                ? heightDelta <= -RELATIVE_RESPONSE_HEIGHT
                : heightDelta >= RELATIVE_RESPONSE_HEIGHT;
    }

    private static void beginRelativePulse(RuntimeCalibration runtime, RelativeLiftPhase phase) {
        runtime.relativePhase = phase;
        runtime.relativePhaseTicks = 0;
        runtime.relativeResponseObserved = false;
        runtime.relativeReverseResponseObserved = false;
        runtime.relativeHeightBeforePulse = runtime.previousY;
        runtime.relativeCurrentPulseTicks = 0;
        runtime.relativePulseCount++;
        resetRelativeHeightTracking(runtime, runtime.previousY);
    }

    private static void completeRelativePulse(
            RuntimeCalibration runtime,
            RelativeLiftDirection direction
    ) {
        runtime.relativeLastPulseTicks = Math.max(1, runtime.relativeCurrentPulseTicks);
        runtime.relativePhase = direction == RelativeLiftDirection.INCREASE
                ? RelativeLiftPhase.OBSERVE_INCREASE
                : RelativeLiftPhase.OBSERVE_DECREASE;
        runtime.relativePhaseTicks = 0;
        runtime.relativeResponseObserved = false;
        runtime.relativeReverseResponseObserved = false;
        resetRelativeHeightTracking(runtime, runtime.previousY);
        resetRelativeAirflowTracking(runtime);
    }

    private static void tickRelativeAirflowTracking(RuntimeCalibration runtime, double currentAirflow) {
        runtime.relativeCurrentAirflow = Double.isFinite(currentAirflow)
                ? Math.max(0.0D, currentAirflow)
                : 0.0D;
        if (!runtime.relativeAirflowTrackingInitialized) {
            resetRelativeAirflowTracking(runtime);
            return;
        }

        runtime.relativeAirflowWindowTicks++;
        runtime.relativeAirflowWindowMin = Math.min(
                runtime.relativeAirflowWindowMin,
                runtime.relativeCurrentAirflow
        );
        runtime.relativeAirflowWindowMax = Math.max(
                runtime.relativeAirflowWindowMax,
                runtime.relativeCurrentAirflow
        );
        if (runtime.relativeAirflowWindowTicks < RELATIVE_AIRFLOW_SAMPLE_TICKS) {
            return;
        }

        double settledRange = Math.max(
                RELATIVE_AIRFLOW_MIN_SETTLED_RANGE,
                runtime.relativeCurrentAirflow * 0.01D
        );
        if (runtime.relativeAirflowWindowMax - runtime.relativeAirflowWindowMin <= settledRange) {
            runtime.relativeAirflowSettledWindows++;
        } else {
            runtime.relativeAirflowSettledWindows = 0;
        }
        runtime.relativeAirflowWindowTicks = 0;
        runtime.relativeAirflowWindowMin = runtime.relativeCurrentAirflow;
        runtime.relativeAirflowWindowMax = runtime.relativeCurrentAirflow;
    }

    private static void resetRelativeAirflowTracking(RuntimeCalibration runtime) {
        runtime.relativeAirflowTrackingInitialized = true;
        runtime.relativeAirflowWindowTicks = 0;
        runtime.relativeAirflowWindowMin = runtime.relativeCurrentAirflow;
        runtime.relativeAirflowWindowMax = runtime.relativeCurrentAirflow;
        runtime.relativeAirflowSettledWindows = 0;
    }

    private static int relativePulseTicks(
            RuntimeCalibration runtime,
            RelativeLiftDirection direction
    ) {
        if (direction == RelativeLiftDirection.INCREASE) {
            return runtime.relativeLearnedIncreasePulseTicks > 0
                    ? runtime.relativeLearnedIncreasePulseTicks
                    : RELATIVE_CONTROL_PULSE_TICKS;
        }
        return runtime.relativeLearnedDecreasePulseTicks > 0
                ? runtime.relativeLearnedDecreasePulseTicks
                : RELATIVE_CONTROL_PULSE_TICKS;
    }

    private static void finishRelativeCalibration(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime
    ) {
        int responseTicks = Math.clamp(runtime.relativeLearnedResponseTicks, 40, runtime.responseTicks);
        int increasePulseTicks = Math.max(
                1,
                runtime.relativeLearnedIncreasePulseTicks > 0
                        ? runtime.relativeLearnedIncreasePulseTicks
                        : runtime.relativeIncreasePulseTargetTicks
        );
        int decreasePulseTicks = Math.max(
                1,
                runtime.relativeLearnedDecreasePulseTicks > 0
                        ? runtime.relativeLearnedDecreasePulseTicks
                        : runtime.relativeDecreasePulseTargetTicks
        );
        int predictedStopTicks = Math.max(
                runtime.relativeTakeoffIncreaseTicks,
                runtime.relativeIncreaseTicksApplied
        ) + RELATIVE_GROUND_UNWIND_MARGIN_TICKS;
        runtime.pendingProfile = LiftCalibrationProfile.relative(
                Math.max(0.0001D, runtime.relativeAscentRate),
                Math.max(0.0001D, runtime.relativeDescentRate),
                responseTicks,
                level.getGameTime(),
                Math.max(1, runtime.relativeTakeoffIncreaseTicks),
                increasePulseTicks,
                decreasePulseTicks,
                predictedStopTicks
        );
        runtime.relativeLearnedResponseTicks = responseTicks;
        runtime.relativePhase = RelativeLiftPhase.LANDING;
        runtime.relativeLandingCommand = RelativeLiftDirection.DECREASE;
        runtime.relativeLandingPulseTicks = 0;
        runtime.relativeLandingWaitTicks = 0;
        runtime.relativeLandingAtHigherSpeed = false;
        runtime.relativeTouchdownSettledTicks = 0;
        runtime.relativeTouchdownConfirmed = false;
        runtime.relativeGroundUnwindTicks = 0;
        resetRelativeHeightTracking(runtime, runtime.previousY);
        transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 95, "returning");
    }

    private static boolean relativeHeightHasSettled(RuntimeCalibration runtime, double currentY) {
        if (!runtime.relativeHeightTrackingInitialized) {
            resetRelativeHeightTracking(runtime, currentY);
            return false;
        }

        runtime.relativeHeightWindowTicks++;
        runtime.relativeHeightWindowMinY = Math.min(runtime.relativeHeightWindowMinY, currentY);
        runtime.relativeHeightWindowMaxY = Math.max(runtime.relativeHeightWindowMaxY, currentY);
        if (runtime.relativeHeightWindowTicks < RELATIVE_HEIGHT_SAMPLE_TICKS) {
            return runtime.relativeHeightSettledWindows >= RELATIVE_HEIGHT_SETTLED_WINDOWS;
        }

        double heightChange = currentY - runtime.relativeHeightWindowStartY;
        double measuredRate = Math.abs(heightChange) / runtime.relativeHeightWindowTicks;
        if (runtime.relativeCalibrationStage == RelativeCalibrationStage.FIND_SECOND_HOVER
                && heightChange > 0.0D) {
            runtime.relativeAscentRate = Math.max(runtime.relativeAscentRate, measuredRate);
        } else if (runtime.relativeCalibrationStage == RelativeCalibrationStage.RETURN_TO_FIRST_HOVER
                && heightChange < 0.0D) {
            runtime.relativeDescentRate = Math.max(runtime.relativeDescentRate, measuredRate);
        }

        if (relativeHeightWindowIsStable(
                runtime.relativeHeightWindowStartY,
                runtime.relativeHeightWindowMinY,
                runtime.relativeHeightWindowMaxY,
                currentY
        )) {
            runtime.relativeHeightSettledWindows++;
        } else {
            runtime.relativeHeightSettledWindows = 0;
        }
        runtime.relativeHeightWindowTicks = 0;
        runtime.relativeHeightWindowStartY = currentY;
        runtime.relativeHeightWindowMinY = currentY;
        runtime.relativeHeightWindowMaxY = currentY;
        return runtime.relativeHeightSettledWindows >= RELATIVE_HEIGHT_SETTLED_WINDOWS;
    }

    static boolean relativeHeightWindowIsStable(
            double startY,
            double minimumY,
            double maximumY,
            double endY
    ) {
        return maximumY - minimumY <= RELATIVE_HEIGHT_SETTLED_RANGE
                && Math.abs(endY - startY) <= RELATIVE_HEIGHT_SETTLED_RANGE;
    }

    private static void resetRelativeHeightTracking(RuntimeCalibration runtime, double currentY) {
        runtime.relativeHeightTrackingInitialized = true;
        runtime.relativeHeightWindowTicks = 0;
        runtime.relativeHeightWindowStartY = currentY;
        runtime.relativeHeightWindowMinY = currentY;
        runtime.relativeHeightWindowMaxY = currentY;
        runtime.relativeHeightSettledWindows = 0;
    }

    private static void tickReturn(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime,
            List<DriveModuleBlockEntity> modules,
            double currentY
    ) {
        LiftCalibrationProfile profile = runtime.pendingProfile;
        if (profile == null) {
            fail(level, transponder, "hardware_unavailable");
            return;
        }
        double altitudeError = runtime.initialY - currentY;
        boolean atStartHeight = Math.abs(altitudeError) <= RETURN_ALTITUDE_TOLERANCE;
        if (atStartHeight) {
            clearCalibrationModules(level, transponder.transponderId());
            if (Math.abs(runtime.smoothedVelocity) <= RETURN_SETTLED_SPEED) {
                runtime.returnSettledTicks++;
            } else {
                runtime.returnSettledTicks = 0;
            }
            if (runtime.returnSettledTicks >= Math.max(RETURN_SETTLED_TICKS, profile.responseTicks())) {
                finish(level, transponder, runtime);
            }
            return;
        }

        if (altitudeError > RETURN_ALTITUDE_TOLERANCE) {
            commandCalibrationModules(
                    level,
                    transponder.transponderId(),
                    modules,
                    profile.ascendingStrength()
            );
            return;
        }

        runtime.returnWindowTicks++;
        if (runtime.returnWindowTicks >= profile.responseTicks()) {
            double verticalDrop = runtime.returnWindowStartY - currentY;
            if (verticalDrop < MINIMUM_RETURN_DROP_PER_WINDOW) {
                runtime.returnStrength = Math.max(0.0D, runtime.returnStrength - 1.0D);
            }
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Lift calibration {} landing y={} targetY={} drop={} velocity={} acceleration={} output={}",
                    transponder.transponderId(), currentY, runtime.initialY, verticalDrop,
                    runtime.smoothedVelocity, runtime.smoothedAcceleration, runtime.returnStrength
            );
            runtime.returnWindowStartY = currentY;
            runtime.returnWindowTicks = 0;
        }
        commandCalibrationModules(level, transponder.transponderId(), modules, runtime.returnStrength);
        transponder.updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATING, 95, "returning");
    }

    private static void finish(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder,
            RuntimeCalibration runtime
    ) {
        LiftCalibrationProfile profile = runtime.pendingProfile;
        ACTIVE.remove(transponder.transponderId());
        clearCalibrationModules(level, transponder.transponderId());
        transponder.setLiftCalibration(profile);
        if (runtime.relativePhase == RelativeLiftPhase.LANDING) {
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Relative lift calibration {} completed: takeoffIncreaseTicks={} increasePulseTicks={} decreasePulseTicks={} responseTicks={} totalIncreaseTicks={} totalDecreaseTicks={} groundUnwindTicks={}",
                    transponder.transponderId(), runtime.relativeTakeoffIncreaseTicks,
                    profile.increasePulseTicks(), profile.decreasePulseTicks(), profile.responseTicks(),
                    runtime.relativeIncreaseTicksApplied,
                    runtime.relativeDecreaseTicksApplied + runtime.relativeGroundUnwindTicks,
                    runtime.relativeGroundUnwindTicks
            );
        }
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Lift calibration {} completed neutral={} responseTicks={} ascent=[{},{}] descent=[{},{}] y={}",
                transponder.transponderId(), profile.neutralStrength(), profile.responseTicks(),
                profile.ascendingStrength(), profile.ascentRate(),
                profile.descendingStrength(), profile.descentRate(), runtime.previousY
        );
    }

    private static double interpolateNeutral(RuntimeCalibration runtime) {
        double span = runtime.risingVelocity - runtime.fallingVelocity;
        if (Math.abs(span) < 1.0E-6D) {
            return (runtime.fallingStrength + runtime.risingStrength) * 0.5D;
        }
        double fraction = -runtime.fallingVelocity / span;
        return Math.clamp(
                runtime.fallingStrength + (runtime.risingStrength - runtime.fallingStrength) * fraction,
                Math.min(runtime.fallingStrength, runtime.risingStrength),
                Math.max(runtime.fallingStrength, runtime.risingStrength)
        );
    }

    static double classifiedCalibrationVelocity(
            double sampledVelocity,
            double acceleration,
            int responseTicks
    ) {
        double projectedVelocity = sampledVelocity + acceleration * responseTicks * 0.5D;
        if (Math.abs(sampledVelocity) <= SETTLED_VERTICAL_SPEED) {
            return projectedVelocity;
        }
        if (Math.signum(projectedVelocity) != Math.signum(sampledVelocity)) {
            return projectedVelocity;
        }
        return sampledVelocity;
    }

    private static void changeStrength(RuntimeCalibration runtime, int strength) {
        runtime.strength = Math.clamp(strength, 1, 15);
        runtime.ticksAtStrength = 0;
        runtime.velocitySum = 0.0D;
        runtime.velocitySamples = 0;
        runtime.observedResponseTicks = 0;
    }

    private static void updateProgress(AdvancedTransponderBlockEntity transponder, RuntimeCalibration runtime) {
        int progress = Math.min(95, (int) Math.round(runtime.totalTicks * 95.0D / MAX_CALIBRATION_TICKS));
        transponder.updateLiftCalibrationRuntime(
                LiftCalibrationState.CALIBRATING,
                progress,
                "testing_" + runtime.strength
        );
    }

    private static void fail(ServerLevel level, AdvancedTransponderBlockEntity transponder, String detail) {
        ACTIVE.remove(transponder.transponderId());
        clearCalibrationModules(level, transponder.transponderId());
        transponder.updateLiftCalibrationRuntime(LiftCalibrationState.FAILED, 0, detail);
        CreateAeronauticsAutomatedLogistics.debugDriveModules(
                "Lift calibration {} failed: {}", transponder.transponderId(), detail
        );
    }

    private static Optional<VehicleController> resolveVehicle(
            ServerLevel level,
            AdvancedTransponderBlockEntity transponder
    ) {
        transponder.refreshRuntimeShip(level);
        return ShipTransponderRegistry.snapshot(transponder.transponderId())
                .flatMap(snapshot -> snapshot.controllerRef())
                .flatMap(ref -> VehicleControllerResolver.resolve(level, ref));
    }

    private static List<DriveIntentType> configuredIntents(List<DriveModuleBlockEntity> modules) {
        return modules.stream()
                .map(DriveModuleBlockEntity::config)
                .flatMap(Optional::stream)
                .map(config -> config.intent())
                .toList();
    }

    private static void commandCalibrationModules(
            ServerLevel level,
            UUID transponderId,
            List<DriveModuleBlockEntity> modules,
            double strength
    ) {
        boolean rotorcraft = DriveFlightMode.resolve(configuredIntents(modules))
                .filter(mode -> mode == DriveFlightMode.ROTORCRAFT)
                .isPresent();
        for (DriveModuleBlockEntity module : modules) {
            DriveIntentType intent = module.config().map(config -> config.intent()).orElse(null);
            int desired = switch (intent) {
                case LIFT_TARGET_STRENGTH -> strength <= 0.0D
                        ? 0
                        : modulatedSignal(strength, level.getGameTime(), module.moduleId());
                // Relative inputs are deliberately centred at the calibration's
                // initial setting (8); they do not accept an absolute strength.
                case LIFT_INCREASE -> rotorcraft && strength > 8.0D ? 15 : 0;
                case LIFT_DECREASE -> rotorcraft && strength < 8.0D ? 15 : 0;
                case ROTATIONAL_FORCE_ENABLED -> 15;
                case null, default -> 0;
            };
            int output = module.config()
                    .filter(config -> config.inverted() && config.supportsInversion())
                    .map(ignored -> 15 - desired)
                    .orElse(desired);
            module.setOutputSignal(output);
        }
    }

    private static void commandRelativeCalibrationModules(
            ServerLevel level,
            UUID transponderId,
            List<DriveModuleBlockEntity> modules,
            RelativeLiftDirection direction
    ) {
        for (DriveModuleBlockEntity module : modules) {
            DriveIntentType intent = module.config().map(config -> config.intent()).orElse(null);
            int desired = switch (intent) {
                case LIFT_INCREASE -> direction == RelativeLiftDirection.INCREASE ? 15 : 0;
                case LIFT_DECREASE -> direction == RelativeLiftDirection.DECREASE ? 15 : 0;
                case ROTATIONAL_FORCE_ENABLED -> 15;
                case null, default -> 0;
            };
            int output = module.config()
                    .filter(config -> config.inverted() && config.supportsInversion())
                    .map(ignored -> 15 - desired)
                    .orElse(desired);
            module.setOutputSignal(output);
        }
    }

    private static void clearCalibrationModules(ServerLevel level, UUID transponderId) {
        DriveModuleLinkService.linkedModules(level, transponderId)
                .forEach(module -> module.setOutputSignal(0));
    }

    public record StartResult(boolean started, Component message) {
        private static StartResult success(Component message) {
            return new StartResult(true, message);
        }

        private static StartResult failure(Component message) {
            return new StartResult(false, message);
        }
    }

    private static final class RuntimeCalibration {
        private final double initialY;
        private double previousY;
        private double previousMeasuredVelocity;
        private double smoothedVelocity;
        private double smoothedAcceleration;
        private int strength;
        private final int responseTicks;
        private final boolean relativeLiftControl;
        private RelativeLiftPhase relativePhase;
        private int relativePhaseTicks;
        private boolean relativeResponseObserved;
        private boolean relativeReverseResponseObserved;
        private boolean relativeBaselineEstablished;
        private RelativeCalibrationStage relativeCalibrationStage = RelativeCalibrationStage.FIND_FIRST_HOVER;
        private boolean relativeStageResponseObserved;
        private double relativeFirstHoverY;
        private double relativeSecondHoverY;
        private int relativeSecondIncreaseTicksApplied;
        private double relativeSecondProbeStartY;
        private int relativeSecondProbeTicks;
        private double relativeHeightBeforePulse;
        private boolean relativeHeightTrackingInitialized;
        private int relativeHeightWindowTicks;
        private double relativeHeightWindowStartY;
        private double relativeHeightWindowMinY;
        private double relativeHeightWindowMaxY;
        private int relativeHeightSettledWindows;
        private int relativePulseCount;
        private int relativeCurrentPulseTicks;
        private int relativeLastPulseTicks;
        private boolean relativeRotorPulseLearned;
        private int relativeIncreasePulseTargetTicks = RELATIVE_CONTROL_PULSE_TICKS;
        private int relativeDecreasePulseTargetTicks = RELATIVE_CONTROL_PULSE_TICKS;
        private int relativeLearnedIncreasePulseTicks;
        private int relativeLearnedDecreasePulseTicks;
        private int relativeLearnedResponseTicks = DEFAULT_RESPONSE_TICKS;
        private int relativeTakeoffIncreaseTicks;
        private double relativeAscentRate;
        private double relativeDescentRate;
        private RelativeLiftDirection relativeLandingCommand = RelativeLiftDirection.NONE;
        private int relativeLandingPulseTicks;
        private int relativeLandingWaitTicks;
        private boolean relativeLandingAtHigherSpeed;
        private double relativePreviousMeasuredVelocity;
        private double relativeSmoothedVelocity;
        private double relativeSmoothedAcceleration;
        private double relativeCurrentAirflow;
        private boolean relativeAirflowTrackingInitialized;
        private int relativeAirflowWindowTicks;
        private double relativeAirflowWindowMin;
        private double relativeAirflowWindowMax;
        private int relativeAirflowSettledWindows;
        private int relativeIncreaseTicksApplied;
        private int relativeDecreaseTicksApplied;
        private int relativeTouchdownSettledTicks;
        private boolean relativeTouchdownConfirmed;
        private int relativeGroundUnwindTicks;
        private int relativeGroundUnwindTargetTicks;
        private int ticksAtStrength;
        private int totalTicks;
        private int observedResponseTicks;
        private int maximumObservedResponseTicks;
        private double velocitySum;
        private int velocitySamples;
        private int fallingStrength = -1;
        private double fallingVelocity;
        private int risingStrength = -1;
        private double risingVelocity;
        private int neutralCandidateStrength = -1;
        private boolean returning;
        private int returnSettledTicks;
        private double returnStrength;
        private double returnWindowStartY;
        private int returnWindowTicks;
        private LiftCalibrationProfile pendingProfile;

        private RuntimeCalibration(
                double initialY,
                double previousY,
                double previousMeasuredVelocity,
                double smoothedVelocity,
                int strength,
                int responseTicks,
                boolean relativeLiftControl
        ) {
            this.initialY = initialY;
            this.previousY = previousY;
            this.previousMeasuredVelocity = previousMeasuredVelocity;
            this.smoothedVelocity = smoothedVelocity;
            this.strength = strength;
            this.responseTicks = responseTicks;
            this.relativeLiftControl = relativeLiftControl;
            this.relativePhase = relativeLiftControl ? RelativeLiftPhase.SETTLING : null;
        }

        private boolean hasBracket() {
            return fallingStrength >= 1
                    && risingStrength >= 1
                    && fallingStrength < risingStrength
                    && fallingVelocity < 0.0D
                    && risingVelocity > 0.0D;
        }
    }

    private enum RelativeLiftPhase {
        SETTLING,
        PULSE_INCREASE,
        OBSERVE_INCREASE,
        PULSE_DECREASE,
        OBSERVE_DECREASE,
        LANDING
    }

    private enum RelativeLiftDirection {
        NONE,
        INCREASE,
        DECREASE
    }

    private enum RelativeCalibrationStage {
        FIND_FIRST_HOVER,
        FIND_SECOND_HOVER,
        RETURN_TO_FIRST_HOVER
    }
}

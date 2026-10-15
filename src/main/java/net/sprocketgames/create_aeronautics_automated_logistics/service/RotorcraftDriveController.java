package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

/**
 * Rotorcraft-only horizontal guidance. Airships never pass through this
 * controller, so rotor momentum and upright recovery cannot alter their
 * proven positional control path.
 */
final class RotorcraftDriveController {
    private static final double TRANSLATION_DEAD_ZONE = 0.15D;
    private static final double TURN_DEAD_ZONE_RADIANS = Math.toRadians(2.0D);
    private static final double TRANSLATION_POSITION_GAIN = 0.35D;
    private static final double TRANSLATION_MAX_SPEED = 3.0D;
    private static final double TRANSLATION_VELOCITY_DEAD_ZONE = 0.20D;
    private static final double TRANSLATION_MAX_PULSED_DEMAND = 2.45D;
    private static final double DRIVE_ENTER_RADIANS = Math.toRadians(10.0D);
    private static final double DRIVE_EXIT_RADIANS = Math.toRadians(20.0D);
    private static final double DRIVE_MAX_YAW_RATE = Math.toRadians(3.0D);
    private static final double TILT_SUSPEND_RADIANS = Math.toRadians(10.0D);
    private static final double TILT_RESUME_RADIANS = Math.toRadians(5.0D);
    private static final double TILT_RESUME_MAX_RATE = Math.toRadians(2.0D);
    private static final double TILT_PREDICTION_SECONDS = 0.5D;
    private static final double TILT_RATE_SMOOTHING = 0.25D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double OPPOSITE_BOUNDARY_RADIANS = Math.toRadians(170.0D);
    private static final double NAVIGATION_POSITION_TOLERANCE = 0.5D;
    private static final double ENDPOINT_CAPTURE_DISTANCE = 0.65D;
    private static final double ENDPOINT_CAPTURE_MAX_SPEED = 0.20D;
    private static final double ENDPOINT_REACQUIRE_DISTANCE = 1.0D;
    private static final double ENDPOINT_BRAKE_DISTANCE = 4.0D;
    private static final double ENDPOINT_BRAKE_REACQUIRE_DISTANCE = 6.0D;
    private static final double ENDPOINT_POSITION_GAIN = 0.20D;
    private static final double ENDPOINT_MAX_SPEED = 1.0D;
    private static final double ENDPOINT_VELOCITY_GAIN = 4.0D;
    private static final double ENDPOINT_TARGET_CHANGE_DISTANCE = 2.0D;
    private static final double TURN_PREDICTION_SECONDS = 1.0D;
    private static final int TURN_RESPONSE_OBSERVATION_TICKS = 8;
    private static final double TURN_RESPONSE_MINIMUM_YAW_CHANGE = Math.toRadians(1.0D);
    private static final Map<UUID, HeadingSequenceState> STATES = new HashMap<>();
    private static final Map<UUID, DirectTurnResponseState> TURN_RESPONSE_STATES = new HashMap<>();

    private RotorcraftDriveController() {
    }

    static DriveDemand apply(
            UUID controllerId,
            boolean hasTurn,
            boolean hasForwardBack,
            boolean hasLeftRight,
            boolean takeoffRampActive,
            boolean preciseArrival,
            Vec3 targetPosition,
            DriveDemand demand
    ) {
        if (!hasTurn || hasForwardBack == hasLeftRight) {
            STATES.remove(controllerId);
            return demand;
        }

        HeadingSequenceState state = STATES.computeIfAbsent(controllerId, ignored -> new HeadingSequenceState());
        double horizontalDistance = Math.hypot(demand.forwardBack(), demand.leftRight());
        double horizontalSpeed = Math.hypot(demand.forwardVelocity(), demand.rightVelocity());
        state.updateTiltSafety(demand.uprightDot());
        state.updateEndpointTarget(preciseArrival, targetPosition);
        if (state.endpointPoseActive && horizontalDistance > ENDPOINT_REACQUIRE_DISTANCE) {
            state.endpointPoseActive = false;
        }
        if (state.endpointBrakeActive && horizontalDistance > ENDPOINT_BRAKE_REACQUIRE_DISTANCE) {
            state.clearEndpointBrake();
        }
        if (preciseArrival
                && !state.endpointPoseActive
                && !state.endpointBrakeActive
                && horizontalDistance <= ENDPOINT_BRAKE_DISTANCE
                && horizontalSpeed > ENDPOINT_CAPTURE_MAX_SPEED) {
            state.endpointBrakeActive = true;
            state.endpointBrakeRecordedTurn = demand.turn();
        }
        if (state.endpointBrakeActive && horizontalSpeed <= ENDPOINT_CAPTURE_MAX_SPEED) {
            state.clearEndpointBrake();
            if (horizontalDistance <= ENDPOINT_CAPTURE_DISTANCE) {
                state.endpointPoseActive = true;
            }
        }
        if (preciseArrival
                && !state.endpointPoseActive
                && !state.endpointBrakeActive
                && horizontalDistance <= ENDPOINT_CAPTURE_DISTANCE
                && horizontalSpeed <= ENDPOINT_CAPTURE_MAX_SPEED) {
            state.endpointPoseActive = true;
        }
        boolean navigatingToPosition = !state.endpointPoseActive
                && !state.endpointBrakeActive
                && (horizontalDistance > NAVIGATION_POSITION_TOLERANCE
                || preciseArrival && horizontalSpeed > ENDPOINT_CAPTURE_MAX_SPEED);
        double navigationTurn = navigatingToPosition
                ? navigationTurnError(
                        demand.forwardBack(),
                        demand.leftRight(),
                        hasForwardBack,
                        demand.turn()
                )
                : demand.turn();
        double steeringTurn = state.endpointBrakeActive
                ? wrapRadians(demand.turn() - state.endpointBrakeRecordedTurn)
                : navigatingToPosition ? navigationTurn : demand.turn();
        if (state.preferredTurnDirection == 0 && Math.abs(steeringTurn) > TURN_DEAD_ZONE_RADIANS) {
            state.preferredTurnDirection = steeringTurn > 0.0D ? 1 : -1;
        }
        double turnCommand = predictedTurnError(
                steeringTurn,
                demand.yawVelocity(),
                state.preferredTurnDirection
        );
        if (Math.abs(turnCommand) > TURN_DEAD_ZONE_RADIANS
                && Math.abs(steeringTurn) < OPPOSITE_BOUNDARY_RADIANS) {
            state.preferredTurnDirection = turnCommand > 0.0D ? 1 : -1;
        }

        boolean tiltSafe = !state.tiltRecoveryActive;
        state.guidanceHoldActive = takeoffRampActive || !tiltSafe;
        if (state.translationEnabled) {
            if (Math.abs(steeringTurn) > DRIVE_EXIT_RADIANS
                    || Math.abs(demand.yawVelocity()) > DRIVE_MAX_YAW_RATE * 2.0D
                    || !tiltSafe
                    || takeoffRampActive) {
                state.translationEnabled = false;
            }
        } else if (Math.abs(steeringTurn) <= DRIVE_ENTER_RADIANS
                && Math.abs(demand.yawVelocity()) <= DRIVE_MAX_YAW_RATE
                && tiltSafe
                && !takeoffRampActive) {
            state.translationEnabled = true;
        }

        String phase = takeoffRampActive
                ? "TAKEOFF"
                : !tiltSafe
                ? "RECOVER_UPRIGHT"
                : state.endpointBrakeActive
                ? "BRAKE_ENDPOINT"
                : state.endpointPoseActive
                ? state.translationEnabled ? "ENDPOINT_HOLD" : "ALIGN_ENDPOINT"
                : state.translationEnabled ? "TRANSLATE" : "ALIGN_HEADING";
        String steeringMode = state.endpointBrakeActive
                ? "APPROACH_HEADING"
                : navigatingToPosition ? "GUIDANCE_POINT" : "RECORDED_ROTATION";
        String loggedState = phase + ':' + steeringMode;
        if (!loggedState.equals(state.lastLoggedPhase)) {
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Rotorcraft control {} phase={} steering={} headingErrorDegrees={} courseErrorDegrees={} recordedHeadingErrorDegrees={} predictedErrorDegrees={} yawDegreesPerSecond={} tiltDegrees={} tiltRateDegreesPerSecond={} uprightDot={} horizontalDistance={} horizontalSpeed={}",
                    controllerId,
                    phase,
                    steeringMode,
                    Math.toDegrees(steeringTurn),
                    Math.toDegrees(navigationTurn),
                    Math.toDegrees(demand.turn()),
                    Math.toDegrees(turnCommand),
                    Math.toDegrees(demand.yawVelocity()),
                    Math.toDegrees(state.tiltAngle),
                    Math.toDegrees(state.smoothedTiltRate),
                    demand.uprightDot(),
                    horizontalDistance,
                    horizontalSpeed
            );
            state.lastLoggedPhase = loggedState;
        }

        if (takeoffRampActive || !tiltSafe) {
            turnCommand = 0.0D;
        }
        double forwardBack = hasForwardBack && state.translationEnabled
                ? state.endpointBrakeActive
                ? endpointTranslationDemand(demand.forwardBack(), demand.forwardVelocity())
                : binaryTranslationDemand(demand.forwardBack(), demand.forwardVelocity())
                : 0.0D;
        double leftRight = hasLeftRight && state.translationEnabled
                ? state.endpointBrakeActive
                ? endpointTranslationDemand(demand.leftRight(), demand.rightVelocity())
                : binaryTranslationDemand(demand.leftRight(), demand.rightVelocity())
                : 0.0D;
        return new DriveDemand(
                demand.lift(),
                forwardBack,
                leftRight,
                turnCommand,
                demand.forwardVelocity(),
                demand.rightVelocity(),
                demand.yawVelocity(),
                demand.uprightDot()
        );
    }

    static void clear(UUID controllerId) {
        STATES.remove(controllerId);
        TURN_RESPONSE_STATES.remove(controllerId);
    }

    static boolean isHoldingGuidance(UUID controllerId) {
        HeadingSequenceState state = STATES.get(controllerId);
        return state != null && state.guidanceHoldActive;
    }

    static double adaptDirectTurnOutput(
            UUID controllerId,
            long gameTime,
            double semanticTurn,
            double yawVelocity,
            boolean directTurnPair
    ) {
        if (!directTurnPair || Math.abs(semanticTurn) <= TURN_DEAD_ZONE_RADIANS) {
            return semanticTurn;
        }

        DirectTurnResponseState state = TURN_RESPONSE_STATES.computeIfAbsent(
                controllerId,
                ignored -> new DirectTurnResponseState()
        );
        if (state.turnPolarityLearned) {
            return semanticTurn * state.turnOutputPolarity;
        }

        if (state.turnProbeTick != Long.MIN_VALUE) {
            long elapsed = gameTime - state.turnProbeTick;
            double yawChange = yawVelocity - state.turnProbeStartYaw;
            if (elapsed > 0L && Math.abs(yawChange) >= TURN_RESPONSE_MINIMUM_YAW_CHANGE) {
                // The heading error changes in the same direction as Sable's
                // yaw velocity. A positive semantic turn therefore needs an
                // output whose physical response is negative yaw, and vice
                // versa. Learn that relationship from one short pulse so a
                // swapped clockwise/anticlockwise pair cannot create a spin.
                state.turnOutputPolarity = yawChange * state.turnProbeOutputDirection < 0.0D ? 1 : -1;
                state.turnPolarityLearned = true;
                CreateAeronauticsAutomatedLogistics.debugDriveModules(
                        "Rotorcraft control {} learned direct-turn polarity={} from probeDirection={} yawChangeDegreesPerSecond={}",
                        controllerId,
                        state.turnOutputPolarity,
                        state.turnProbeOutputDirection,
                        Math.toDegrees(yawChange)
                );
                return semanticTurn * state.turnOutputPolarity;
            }
            if (elapsed <= TURN_RESPONSE_OBSERVATION_TICKS) {
                return 0.0D;
            }
            state.clearTurnProbe();
        }

        if (Math.floorMod(gameTime, 5L) == 0L) {
            state.turnProbeTick = gameTime;
            state.turnProbeStartYaw = yawVelocity;
            state.turnProbeOutputDirection = semanticTurn > 0.0D ? 1 : -1;
        }
        return semanticTurn;
    }

    static void resetRuntime() {
        STATES.clear();
        TURN_RESPONSE_STATES.clear();
    }

    static double navigationTurnError(double forwardError, double rightError, boolean forwardBackControl) {
        return navigationTurnError(forwardError, rightError, forwardBackControl, 0.0D);
    }

    static double navigationTurnError(
            double forwardError,
            double rightError,
            boolean forwardBackControl,
            double recordedTurnError
    ) {
        double targetAngle = Math.atan2(rightError, forwardError);
        if (forwardBackControl) {
            // A forward/back rotorcraft must point its nose toward the active
            // guidance point. Choosing the reverse heading from the recorded
            // rotation makes the craft visibly turn away from the yellow box
            // and then attempt to fly the route backwards.
            return targetAngle;
        }

        double moveRightAngle = wrapRadians(targetAngle - Math.PI / 2.0D);
        double moveLeftAngle = wrapRadians(targetAngle + Math.PI / 2.0D);
        return angularDistance(moveRightAngle, recordedTurnError)
                <= angularDistance(moveLeftAngle, recordedTurnError)
                ? moveRightAngle
                : moveLeftAngle;
    }

    private static double angularDistance(double first, double second) {
        return Math.abs(wrapRadians(first - second));
    }

    static double predictedTurnError(double turnError, double yawVelocity, int preferredTurnDirection) {
        double predicted = wrapRadians(turnError + yawVelocity * TURN_PREDICTION_SECONDS);
        if (Math.abs(turnError) >= OPPOSITE_BOUNDARY_RADIANS
                && preferredTurnDirection != 0
                && Math.signum(predicted) != preferredTurnDirection) {
            return Math.copySign(Math.abs(predicted), preferredTurnDirection);
        }
        return predicted;
    }

    static double binaryTranslationDemand(double positionError, double velocity) {
        // Sable reports velocity in blocks per second. Drive the velocity toward
        // a bounded closing speed instead of holding a binary force until the
        // craft is close enough to the point.
        double desiredVelocity = Math.clamp(
                positionError * TRANSLATION_POSITION_GAIN,
                -TRANSLATION_MAX_SPEED,
                TRANSLATION_MAX_SPEED
        );
        double velocityError = desiredVelocity - velocity;
        if (Math.abs(positionError) <= TRANSLATION_DEAD_ZONE
                && Math.abs(velocity) <= TRANSLATION_VELOCITY_DEAD_ZONE) {
            return 0.0D;
        }
        if (Math.abs(velocityError) <= TRANSLATION_VELOCITY_DEAD_ZONE) {
            return 0.0D;
        }
        // Keep direct on/off aircraft below the continuous-power threshold so
        // every burst is followed by a fresh velocity observation.
        return Math.clamp(
                velocityError,
                -TRANSLATION_MAX_PULSED_DEMAND,
                TRANSLATION_MAX_PULSED_DEMAND
        );
    }

    static double endpointTranslationDemand(double positionError, double velocity) {
        double desiredVelocity = Math.clamp(
                positionError * ENDPOINT_POSITION_GAIN,
                -ENDPOINT_MAX_SPEED,
                ENDPOINT_MAX_SPEED
        );
        double velocityError = desiredVelocity - velocity;
        if (Math.abs(positionError) <= TRANSLATION_DEAD_ZONE
                && Math.abs(velocity) <= TRANSLATION_VELOCITY_DEAD_ZONE) {
            return 0.0D;
        }
        if (Math.abs(velocityError) <= TRANSLATION_VELOCITY_DEAD_ZONE) {
            return 0.0D;
        }
        return Math.clamp(
                velocityError * ENDPOINT_VELOCITY_GAIN,
                -TRANSLATION_MAX_PULSED_DEMAND,
                TRANSLATION_MAX_PULSED_DEMAND
        );
    }

    private static double wrapRadians(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static final class HeadingSequenceState {
        private int preferredTurnDirection;
        private boolean translationEnabled;
        private boolean guidanceHoldActive;
        private boolean endpointBrakeActive;
        private boolean endpointPoseActive;
        private double endpointBrakeRecordedTurn;
        private double endpointTargetX = Double.NaN;
        private double endpointTargetZ = Double.NaN;
        private double tiltAngle;
        private double previousTiltAngle = Double.NaN;
        private double smoothedTiltRate;
        private boolean tiltRecoveryActive;
        private String lastLoggedPhase = "";

        private void updateTiltSafety(double uprightDot) {
            tiltAngle = Math.acos(Math.clamp(uprightDot, -1.0D, 1.0D));
            if (Double.isFinite(previousTiltAngle)) {
                double measuredRate = (tiltAngle - previousTiltAngle) * TICKS_PER_SECOND;
                smoothedTiltRate += (measuredRate - smoothedTiltRate) * TILT_RATE_SMOOTHING;
            }
            previousTiltAngle = tiltAngle;

            double predictedTilt = tiltAngle
                    + Math.max(0.0D, smoothedTiltRate) * TILT_PREDICTION_SECONDS;
            if (!tiltRecoveryActive
                    && (tiltAngle >= TILT_SUSPEND_RADIANS
                    || predictedTilt >= TILT_SUSPEND_RADIANS)) {
                tiltRecoveryActive = true;
            } else if (tiltRecoveryActive
                    && tiltAngle <= TILT_RESUME_RADIANS
                    && smoothedTiltRate <= TILT_RESUME_MAX_RATE) {
                tiltRecoveryActive = false;
            }
        }

        private void updateEndpointTarget(boolean preciseArrival, Vec3 targetPosition) {
            if (!preciseArrival) {
                clearEndpointBrake();
                endpointPoseActive = false;
                endpointTargetX = Double.NaN;
                endpointTargetZ = Double.NaN;
                return;
            }
            if (Double.isNaN(endpointTargetX)
                    || Math.hypot(targetPosition.x - endpointTargetX, targetPosition.z - endpointTargetZ)
                    > ENDPOINT_TARGET_CHANGE_DISTANCE) {
                clearEndpointBrake();
                endpointPoseActive = false;
            }
            endpointTargetX = targetPosition.x;
            endpointTargetZ = targetPosition.z;
        }

        private void clearEndpointBrake() {
            endpointBrakeActive = false;
            endpointBrakeRecordedTurn = 0.0D;
        }

    }

    private static final class DirectTurnResponseState {
        private boolean turnPolarityLearned;
        private int turnOutputPolarity = 1;
        private long turnProbeTick = Long.MIN_VALUE;
        private double turnProbeStartYaw;
        private int turnProbeOutputDirection;

        private void clearTurnProbe() {
            turnProbeTick = Long.MIN_VALUE;
            turnProbeStartYaw = 0.0D;
            turnProbeOutputDirection = 0;
        }
    }
}

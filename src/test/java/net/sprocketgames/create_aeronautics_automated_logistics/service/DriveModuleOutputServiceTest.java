package net.sprocketgames.create_aeronautics_automated_logistics.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationProfile;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveFlightMode;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveSignalCategory;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

class DriveModuleOutputServiceTest {
    @Test
    void leavesAirshipGuidanceDemandUnchanged() {
        DriveDemand demand = new DriveDemand(4.0D, 12.0D, -7.0D, 0.4D, 3.0D, -2.0D, 0.2D, 0.7D);

        assertEquals(demand, DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.AIRSHIP,
                UUID.randomUUID(),
                EnumSet.of(
                        DriveIntentType.FORWARD_BACK_FORWARD,
                        DriveIntentType.FORWARD_BACK_BACKWARD,
                        DriveIntentType.TURN_CLOCKWISE,
                        DriveIntentType.TURN_ANTICLOCKWISE
                ),
                true,
                demand
        ));
    }

    @Test
    void pointsRotorcraftAtGuidancePointInsteadOfFollowingRecordedHeadingWhileTravelling() {
        DriveDemand demand = new DriveDemand(
                0.0D,
                10.0D,
                10.0D,
                -Math.PI / 2.0D,
                0.0D,
                0.0D,
                0.0D,
                1.0D
        );

        DriveDemand guided = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                UUID.randomUUID(),
                EnumSet.of(
                        DriveIntentType.FORWARD_BACK_FORWARD,
                        DriveIntentType.FORWARD_BACK_BACKWARD,
                        DriveIntentType.TURN_CLOCKWISE,
                        DriveIntentType.TURN_ANTICLOCKWISE
                ),
                false,
                demand
        );

        assertEquals(Math.PI / 4.0D, guided.turn(), 1.0E-9D);
        assertEquals(0.0D, guided.forwardBack(), 1.0E-9D);
    }

    @Test
    void ignoresVerticalSeparationWhenComputingRotorcraftCourse() {
        DriveModuleOutputService.HorizontalDemand horizontal =
                DriveModuleOutputService.rotorcraftHorizontalDemand(
                        new Quaterniond().rotationX(Math.toRadians(30.0D)),
                        Direction.NORTH,
                        new Vec3(0.0D, 100.0D, 0.0D),
                        Vec3.ZERO
                );

        assertEquals(0.0D, horizontal.forwardError(), 1.0E-9D);
        assertEquals(0.0D, horizontal.rightError(), 1.0E-9D);
    }

    @Test
    void requiresACompleteControlRatherThanAStandaloneStrengthOutput() {
        assertTrue(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_DIRECTION,
                DriveIntentType.LEFT_RIGHT_DIRECTION
        )));
        assertTrue(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_DIRECTION,
                DriveIntentType.TURN_DIRECTION
        )));
        assertTrue(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_DIRECTION,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        )));
        assertTrue(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.LEFT_RIGHT_LEFT,
                DriveIntentType.LEFT_RIGHT_RIGHT
        )));
        assertFalse(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_DIRECTION,
                DriveIntentType.LEFT_RIGHT_TARGET_STRENGTH
        )));
        assertFalse(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_INCREASE,
                DriveIntentType.FORWARD_BACK_DECREASE,
                DriveIntentType.LEFT_RIGHT_DIRECTION
        )));
        assertFalse(DriveModuleOutputService.hasRequiredHorizontalControl(EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.LEFT_RIGHT_LEFT,
                DriveIntentType.LEFT_RIGHT_RIGHT
        )));
    }

    @Test
    void keepsEstimatorAcrossImmediateScheduleLegHandoff() {
        assertTrue(DriveModuleOutputService.isLiftHandoffSampleReusable(1_000L, 1_000L));
        assertTrue(DriveModuleOutputService.isLiftHandoffSampleReusable(1_010L, 1_000L));
    }

    @Test
    void expiresEstimatorAfterARealStopOrClockReset() {
        assertFalse(DriveModuleOutputService.isLiftHandoffSampleReusable(1_011L, 1_000L));
        assertFalse(DriveModuleOutputService.isLiftHandoffSampleReusable(999L, 1_000L));
    }

    @Test
    void brakesBeforeDelayedAccelerationExceedsTheNeededClosingSpeed() {
        assertTrue(DriveModuleOutputService.shouldBrakeForPredictedRelativeSpeed(
                0.001D,
                0.09D,
                0.04D
        ));
    }

    @Test
    void doesNotBrakeEarlyWhenProjectedSpeedIsSafeOrAlreadyDecelerating() {
        assertFalse(DriveModuleOutputService.shouldBrakeForPredictedRelativeSpeed(
                0.001D,
                0.05D,
                0.04D
        ));
        assertFalse(DriveModuleOutputService.shouldBrakeForPredictedRelativeSpeed(
                -0.001D,
                -0.05D,
                0.04D
        ));
        assertFalse(DriveModuleOutputService.shouldBrakeForPredictedRelativeSpeed(
                0.0002D,
                0.05D,
                0.04D
        ));
    }

    @Test
    void dampsOnlyAConfirmedNearTargetReversalAwayFromTheTarget() {
        assertTrue(DriveModuleOutputService.shouldDampNearTargetAwayMotion(
                0.5D,
                0.0D,
                -0.017D,
                -0.0004D,
                1,
                -1
        ));
        assertFalse(DriveModuleOutputService.shouldDampNearTargetAwayMotion(
                0.5D,
                0.0D,
                0.017D,
                -0.0004D,
                1,
                -1
        ));
        assertFalse(DriveModuleOutputService.shouldDampNearTargetAwayMotion(
                1.5D,
                0.0D,
                -0.017D,
                -0.0004D,
                1,
                -1
        ));
    }

    @Test
    void waitsForAnUnobservedRepeatedLiftCorrection() {
        assertTrue(DriveModuleOutputService.requiredLiftObservationTicks(
                1,
                1,
                0.0D,
                62,
                false
        ) > 62);
        assertTrue(DriveModuleOutputService.requiredLiftObservationTicks(
                1,
                -1,
                0.0D,
                62,
                true
        ) < 62);
    }

    @Test
    void correctsSavedNeutralFromPersistentDriftAtNeutralPower() {
        LiftCalibrationProfile profile = liftProfile();

        assertEquals(-0.02D, DriveModuleOutputService.learnedNeutralCorrection(
                profile,
                6.4D,
                0.01D
        ), 1.0E-9D);
        assertEquals(0.02D, DriveModuleOutputService.learnedNeutralCorrection(
                profile,
                6.4D,
                -0.01D
        ), 1.0E-9D);
        assertEquals(0.0D, DriveModuleOutputService.learnedNeutralCorrection(
                profile,
                6.4D,
                0.0005D
        ), 1.0E-9D);
    }

    @Test
    void doesNotTreatExpectedMotionAwayFromNeutralPowerAsCalibrationError() {
        assertEquals(-0.02D, DriveModuleOutputService.learnedNeutralCorrection(
                liftProfile(),
                6.0D,
                -0.01D
        ), 1.0E-9D);
    }

    @Test
    void pulsesRelativeLiftOnlyWhenPredictedVelocityNeedsChanging() {
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.relativeLiftDecision(0.04D, 0.0D, 0.0D, 120)
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                DriveModuleOutputService.relativeLiftDecision(0.0D, 0.04D, 0.0D, 120)
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.NONE,
                DriveModuleOutputService.relativeLiftDecision(0.04D, 0.035D, 0.0D, 120)
        );
    }

    @Test
    void relativeLiftBrakesForMeasuredAccelerationBeforeOvershooting() {
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                DriveModuleOutputService.relativeLiftDecision(0.04D, 0.03D, 0.001D, 120)
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.relativeLiftDecision(-0.04D, -0.03D, -0.001D, 120)
        );
    }

    @Test
    void coastsInsteadOfReversingRelativeLiftWhileFarFromTheTarget() {
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.NONE,
                DriveModuleOutputService.suppressFarRelativeLiftReversal(
                        -10.0D,
                        0.01D,
                        DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                        120
                )
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.suppressFarRelativeLiftReversal(
                        -10.0D,
                        -0.08D,
                        DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                        120
                )
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.NONE,
                DriveModuleOutputService.suppressFarRelativeLiftReversal(
                        -16.0D,
                        -0.07D,
                        DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                        54
                )
        );
    }

    @Test
    void fluttersSmallBinaryMotionButHoldsLargeCorrections() {
        assertTrue(DriveModuleOutputService.binaryMotionPowered(4.0D, 3.0D, 1.0D, 1L));
        assertTrue(DriveModuleOutputService.binaryMotionPowered(2.0D, 3.0D, 1.0D, 0L));
        assertFalse(DriveModuleOutputService.binaryMotionPowered(2.0D, 3.0D, 1.0D, 2L));
        assertTrue(DriveModuleOutputService.binaryMotionPowered(0.5D, 3.0D, 1.0D, 5L));
        assertFalse(DriveModuleOutputService.binaryMotionPowered(0.5D, 3.0D, 1.0D, 6L));
    }

    @Test
    void finelyFeathersDirectRotorcraftControlsEvenForLargeCorrections() {
        assertTrue(DriveModuleOutputService.binaryMotionPowered(100.0D, 3.0D, 1.0D, 5L, true));
        assertFalse(DriveModuleOutputService.binaryMotionPowered(100.0D, 3.0D, 1.0D, 6L, true));
        assertFalse(DriveModuleOutputService.binaryMotionPowered(100.0D, 3.0D, 1.0D, 9L, true));
        assertTrue(DriveModuleOutputService.binaryMotionPowered(100.0D, 3.0D, 1.0D, 10L, true));
    }

    @Test
    void feathersOnlyRotorcraftAxesWithoutVariableStrengthControls() {
        EnumSet<DriveIntentType> directTurn = EnumSet.of(
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );
        assertTrue(DriveModuleOutputService.shouldFeatherDirectControl(
                DriveFlightMode.ROTORCRAFT,
                directTurn,
                DriveSignalCategory.TURN
        ));
        assertFalse(DriveModuleOutputService.shouldFeatherDirectControl(
                DriveFlightMode.AIRSHIP,
                directTurn,
                DriveSignalCategory.TURN
        ));

        EnumSet<DriveIntentType> analogTurn = EnumSet.copyOf(directTurn);
        analogTurn.add(DriveIntentType.TURN_TARGET_STRENGTH);
        assertFalse(DriveModuleOutputService.shouldFeatherDirectControl(
                DriveFlightMode.ROTORCRAFT,
                analogTurn,
                DriveSignalCategory.TURN
        ));

        EnumSet<DriveIntentType> relativeForwardSpeed = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.FORWARD_BACK_INCREASE,
                DriveIntentType.FORWARD_BACK_DECREASE
        );
        assertFalse(DriveModuleOutputService.shouldFeatherDirectControl(
                DriveFlightMode.ROTORCRAFT,
                relativeForwardSpeed,
                DriveSignalCategory.FORWARD_BACK
        ));

        EnumSet<DriveIntentType> directForward = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD
        );
        assertFalse(DriveModuleOutputService.shouldFeatherDirectControl(
                DriveFlightMode.ROTORCRAFT,
                directForward,
                DriveSignalCategory.FORWARD_BACK
        ));
    }

    @Test
    void usesCalibratedTakeoffRampBeforeNormalRelativeLiftTrimPulses() {
        LiftCalibrationProfile profile = LiftCalibrationProfile.relative(
                0.12D,
                0.28D,
                120,
                0L,
                66,
                11,
                13,
                54
        );

        assertEquals(66, DriveModuleOutputService.relativeLiftPulseTicks(
                profile,
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                true,
                profile.increasePulseTicks(),
                profile.decreasePulseTicks()
        ));
        assertEquals(11, DriveModuleOutputService.relativeLiftPulseTicks(
                profile,
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                false,
                profile.increasePulseTicks(),
                profile.decreasePulseTicks()
        ));
        assertEquals(13, DriveModuleOutputService.relativeLiftPulseTicks(
                profile,
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                true,
                profile.increasePulseTicks(),
                profile.decreasePulseTicks()
        ));
    }

    @Test
    void waitsForMeasuredRiseBeforeReleasingRotorcraftTakeoff() {
        assertFalse(DriveModuleOutputService.relativeTakeoffReady(64.0D, 64.49D));
        assertTrue(DriveModuleOutputService.relativeTakeoffReady(64.0D, 64.5D));
        assertFalse(DriveModuleOutputService.relativeTakeoffReady(Double.NaN, 64.5D));
    }

    @Test
    void convertsSableVelocityToThePerTickUnitsUsedByLiftPrediction() {
        assertEquals(0.1D, DriveModuleOutputService.verticalVelocityBlocksPerTick(2.0D), 1.0E-9D);
        assertEquals(-0.06D, DriveModuleOutputService.verticalVelocityBlocksPerTick(-1.2D), 1.0E-9D);
    }

    @Test
    void usesTheCalibratedPairedBrakeDuringRotorcraftApproach() {
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                DriveModuleOutputService.relativeApproachDescentDecision(false, false, 0.0D, 0.0D, 124)
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.relativeApproachDescentDecision(true, false, -0.08D, -0.001D, 124)
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                DriveModuleOutputService.relativeApproachDescentDecision(true, true, -0.08D, 0.001D, 124)
        );
        assertEquals(
                DriveModuleOutputService.RelativeLiftCommand.NONE,
                DriveModuleOutputService.relativeApproachDescentDecision(true, false, -0.06D, 0.0D, 124)
        );
    }

    @Test
    void acceptsRotorcraftEndpointsOnlyAtLowPhysicalSpeed() {
        assertTrue(DriveModuleOutputService.isRotorcraftEndpointMotionSettled(
                new Vec3(0.1D, 0.1D, 0.1D)
        ));
        assertFalse(DriveModuleOutputService.isRotorcraftEndpointMotionSettled(
                new Vec3(0.0D, 0.21D, 0.0D)
        ));
    }

    @Test
    void predictsTurnMomentumAndKeepsItsDirectionAcrossTheOppositeHeadingBoundary() {
        assertEquals(0.09D, RotorcraftDriveController.predictedTurnError(
                0.10D,
                -0.01D,
                1
        ), 1.0E-9D);

        double acrossBoundary = RotorcraftDriveController.predictedTurnError(
                Math.toRadians(179.0D),
                Math.toRadians(1.0D),
                1
        );
        assertTrue(acrossBoundary > 0.0D);
    }

    @Test
    void learnsAndCorrectsAReversedDirectTurnPairFromOnePulse() {
        UUID controllerId = UUID.randomUUID();

        assertTrue(RotorcraftDriveController.adaptDirectTurnOutput(
                controllerId,
                100L,
                1.0D,
                0.0D,
                true
        ) > 0.0D);
        assertEquals(0.0D, RotorcraftDriveController.adaptDirectTurnOutput(
                controllerId,
                101L,
                1.0D,
                Math.toRadians(0.2D),
                true
        ), 1.0E-9D);
        assertTrue(RotorcraftDriveController.adaptDirectTurnOutput(
                controllerId,
                103L,
                1.0D,
                Math.toRadians(1.5D),
                true
        ) < 0.0D);

        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void retainsCorrectDirectTurnPolarityAfterItsProbe() {
        UUID controllerId = UUID.randomUUID();

        RotorcraftDriveController.adaptDirectTurnOutput(controllerId, 200L, 1.0D, 0.0D, true);
        assertTrue(RotorcraftDriveController.adaptDirectTurnOutput(
                controllerId,
                203L,
                1.0D,
                Math.toRadians(-1.5D),
                true
        ) > 0.0D);

        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void holdsRouteGuidanceUntilRotorcraftHeadingControlIsReadyToTranslate() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );

        DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                true,
                new DriveDemand(4.0D, 5.0D, 0.0D, Math.PI / 2.0D, 0.0D, 0.0D, 0.0D, 1.0D)
        );
        assertTrue(RotorcraftDriveController.isHoldingGuidance(controllerId));

        DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(4.0D, 5.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D)
        );
        assertFalse(RotorcraftDriveController.isHoldingGuidance(controllerId));

        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void letsRouteGuidanceAdvanceWhileRotorcraftTurnsNormally() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );

        DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(4.0D, 5.0D, 0.0D, Math.PI / 2.0D, 0.0D, 0.0D, 0.0D, 1.0D)
        );

        assertFalse(RotorcraftDriveController.isHoldingGuidance(controllerId));
        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void steersTheMovableAxisTowardThePositionUsingTheSmallerTurn() {
        assertEquals(0.0D, RotorcraftDriveController.navigationTurnError(10.0D, 0.0D, true), 1.0E-9D);
        assertEquals(Math.PI, Math.abs(RotorcraftDriveController.navigationTurnError(
                -10.0D,
                0.0D,
                true
        )), 1.0E-9D);
        assertEquals(Math.PI / 4.0D, RotorcraftDriveController.navigationTurnError(
                10.0D,
                10.0D,
                true
        ), 1.0E-9D);
        assertEquals(0.0D, RotorcraftDriveController.navigationTurnError(0.0D, 10.0D, false), 1.0E-9D);
        assertEquals(0.0D, RotorcraftDriveController.navigationTurnError(0.0D, -10.0D, false), 1.0E-9D);
    }

    @Test
    void pointsForwardAxisTowardTheGuidancePointEvenWhenRecordedHeadingFacesAway() {
        assertEquals(0.0D, RotorcraftDriveController.navigationTurnError(
                10.0D,
                0.0D,
                true,
                Math.PI
        ), 1.0E-9D);
        assertEquals(Math.PI, Math.abs(RotorcraftDriveController.navigationTurnError(
                -10.0D,
                0.0D,
                true,
                0.0D
        )), 1.0E-9D);
    }

    @Test
    void brakesAtARecordedEndpointBeforeAligningToItsRecordedRotation() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );
        Vec3 airborneEndpoint = new Vec3(40.0D, 200.0D, -12.0D);

        DriveDemand braking = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                true,
                airborneEndpoint,
                new DriveDemand(0.0D, 0.4D, 0.0D, -Math.PI / 2.0D, 0.5D, 0.0D, 0.0D, 1.0D)
        );
        assertEquals(0.0D, braking.turn(), 1.0E-9D);
        assertTrue(braking.forwardBack() < 0.0D);

        DriveDemand aligned = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                true,
                airborneEndpoint,
                new DriveDemand(0.0D, 0.4D, 0.0D, -Math.PI / 2.0D, 0.0D, 0.0D, 0.0D, 1.0D)
        );
        assertEquals(-Math.PI / 2.0D, aligned.turn(), 1.0E-9D);
        assertEquals(0.0D, aligned.forwardBack(), 1.0E-9D);

        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void keepsTheApproachHeadingWhileBrakingPastAnEndpoint() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );
        Vec3 endpoint = new Vec3(40.0D, 80.0D, -12.0D);

        DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                true,
                endpoint,
                new DriveDemand(0.0D, 3.0D, 0.0D, 2.0D, 1.0D, 0.0D, 0.0D, 1.0D)
        );
        DriveDemand overshot = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                true,
                endpoint,
                new DriveDemand(0.0D, -0.5D, 0.0D, 2.0D, 0.5D, 0.0D, 0.0D, 1.0D)
        );

        assertEquals(0.0D, overshot.turn(), 1.0E-9D);
        assertTrue(overshot.forwardBack() < 0.0D);
        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void keepsTheRecordedPoseWhileFollowingAVerticalRouteAtAnyAltitude() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );
        DriveDemand recordedPose = new DriveDemand(
                -20.0D,
                0.1D,
                0.0D,
                Math.PI / 3.0D,
                0.0D,
                0.0D,
                0.0D,
                1.0D
        );

        DriveDemand high = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                true,
                new Vec3(12.0D, 180.0D, 30.0D),
                recordedPose
        );
        DriveDemand lower = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                true,
                new Vec3(12.0D, 90.0D, 30.0D),
                recordedPose
        );

        assertEquals(Math.PI / 3.0D, high.turn(), 1.0E-9D);
        assertEquals(high.turn(), lower.turn(), 1.0E-9D);
        assertEquals(0.0D, lower.forwardBack(), 1.0E-9D);

        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void controlsBinaryTranslationTowardABoundedClosingSpeed() {
        assertEquals(0.30D, RotorcraftDriveController.binaryTranslationDemand(1.0D, 0.05D), 1.0E-9D);
        assertEquals(0.0D, RotorcraftDriveController.binaryTranslationDemand(1.0D, 0.20D));
        assertEquals(0.55D, RotorcraftDriveController.binaryTranslationDemand(1.0D, -0.20D), 1.0E-9D);
        assertEquals(-2.45D, RotorcraftDriveController.binaryTranslationDemand(20.0D, 8.0D), 1.0E-9D);
        assertEquals(2.45D, RotorcraftDriveController.binaryTranslationDemand(20.0D, -8.0D), 1.0E-9D);
        assertEquals(-1.0D, RotorcraftDriveController.binaryTranslationDemand(0.0D, 1.0D), 1.0E-9D);
        assertEquals(0.0D, RotorcraftDriveController.binaryTranslationDemand(0.10D, 0.0D));
    }

    @Test
    void brakesEndpointMomentumMoreFirmlyThanCruiseGuidance() {
        assertEquals(-1.6D, RotorcraftDriveController.endpointTranslationDemand(3.0D, 1.0D), 1.0E-9D);
        assertEquals(-2.4D, RotorcraftDriveController.endpointTranslationDemand(-0.5D, 0.5D), 1.0E-9D);
    }

    @Test
    void suspendsHorizontalControlBeforeRotorcraftTiltBecomesUnsafe() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );

        DriveDemand level = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(0.0D, 10.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D)
        );
        DriveDemand tipping = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(
                        0.0D,
                        10.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        Math.cos(Math.toRadians(11.0D))
                )
        );

        assertTrue(level.forwardBack() > 0.0D);
        assertEquals(0.0D, tipping.forwardBack(), 1.0E-9D);
        assertEquals(0.0D, tipping.turn(), 1.0E-9D);
        assertTrue(RotorcraftDriveController.isHoldingGuidance(controllerId));
        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void waitsUntilRotorcraftIsLevelBeforeRestoringHorizontalControl() {
        UUID controllerId = UUID.randomUUID();
        EnumSet<DriveIntentType> controls = EnumSet.of(
                DriveIntentType.FORWARD_BACK_FORWARD,
                DriveIntentType.FORWARD_BACK_BACKWARD,
                DriveIntentType.TURN_CLOCKWISE,
                DriveIntentType.TURN_ANTICLOCKWISE
        );

        DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(
                        0.0D,
                        10.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        Math.cos(Math.toRadians(12.0D))
                )
        );
        DriveDemand stillTilted = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(
                        0.0D,
                        10.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        Math.cos(Math.toRadians(7.0D))
                )
        );
        DriveDemand recovered = DriveModuleOutputService.guidanceDemandForMode(
                DriveFlightMode.ROTORCRAFT,
                controllerId,
                controls,
                false,
                new DriveDemand(
                        0.0D,
                        10.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        Math.cos(Math.toRadians(4.0D))
                )
        );

        assertEquals(0.0D, stillTilted.forwardBack(), 1.0E-9D);
        assertTrue(recovered.forwardBack() > 0.0D);
        assertFalse(RotorcraftDriveController.isHoldingGuidance(controllerId));
        RotorcraftDriveController.clear(controllerId);
    }

    @Test
    void keepsPairedLandingControlActiveUntilNearTheEndpointHeight() {
        assertTrue(DriveModuleOutputService.shouldUseRelativeApproachDescent(true, false, -3.1D));
        assertTrue(DriveModuleOutputService.shouldUseRelativeApproachDescent(true, true, -1.0D));
        assertFalse(DriveModuleOutputService.shouldUseRelativeApproachDescent(true, true, -0.5D));
        assertFalse(DriveModuleOutputService.shouldUseRelativeApproachDescent(false, true, -10.0D));
    }

    @Test
    void retriesTheSameRelativeLiftCommandWhenTheCraftMovesTheWrongWay() {
        assertFalse(DriveModuleOutputService.urgentRelativeLiftCommand(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                9,
                -0.10D,
                0.0D
        ));
        assertTrue(DriveModuleOutputService.urgentRelativeLiftCommand(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                10,
                -0.10D,
                0.0D
        ));
        assertTrue(DriveModuleOutputService.urgentRelativeLiftCommand(
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                DriveModuleOutputService.RelativeLiftCommand.DECREASE,
                10,
                0.10D,
                0.0D
        ));
        assertFalse(DriveModuleOutputService.urgentRelativeLiftCommand(
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                DriveModuleOutputService.RelativeLiftCommand.INCREASE,
                10,
                0.02D,
                0.0D
        ));
    }

    @Test
    void waitsForRelativeLiftRotorsToRecoverTheirSavedAirflow() {
        assertFalse(DriveModuleOutputService.isRelativeRestoreRotorReady(94.9D, 100.0D));
        assertTrue(DriveModuleOutputService.isRelativeRestoreRotorReady(95.0D, 100.0D));
        assertTrue(DriveModuleOutputService.isRelativeRestoreRotorReady(-100.0D, 100.0D));
        assertFalse(DriveModuleOutputService.isRelativeRestoreRotorReady(100.0D, 0.0D));
    }

    @Test
    void releasesRestoreHoldFromObservedPositionStability() {
        assertTrue(DriveModuleOutputService.isRestoreBodyReady(0.11D, 0.001D));
        assertFalse(DriveModuleOutputService.isRestoreBodyReady(1.01D, 0.001D));
        assertFalse(DriveModuleOutputService.isRestoreBodyReady(0.11D, 0.03D));
    }

    private static LiftCalibrationProfile liftProfile() {
        return new LiftCalibrationProfile(
                6.4D,
                8,
                0.025D,
                5,
                0.06D,
                62,
                0L
        );
    }
}

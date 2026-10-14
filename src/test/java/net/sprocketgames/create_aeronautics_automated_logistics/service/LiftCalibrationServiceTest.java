package net.sprocketgames.create_aeronautics_automated_logistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationProfile;
import org.junit.jupiter.api.Test;

class LiftCalibrationServiceTest {
    private static final UUID MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Test
    void blendsOnlyAdjacentSignalsForFractionalNeutralLift() {
        int lowerTicks = 0;
        int upperTicks = 0;
        for (int tick = 0; tick < 20; tick++) {
            int signal = LiftCalibrationService.modulatedSignal(5.45D, tick, MODULE_ID);
            if (signal == 5) {
                lowerTicks++;
            } else if (signal == 6) {
                upperTicks++;
            }
        }

        assertEquals(11, lowerTicks);
        assertEquals(9, upperTicks);
    }

    @Test
    void clampsPersistedCalibrationToSafeRanges() {
        LiftCalibrationProfile profile = new LiftCalibrationProfile(18.0D, 20, -0.04D, -2, -0.03D, 5, 42L);

        assertEquals(15.0D, profile.neutralStrength());
        assertEquals(15, profile.ascendingStrength());
        assertEquals(0.04D, profile.ascentRate());
        assertEquals(1, profile.descendingStrength());
        assertEquals(0.03D, profile.descentRate());
        assertEquals(40, profile.responseTicks());
        assertEquals(profile, LiftCalibrationProfile.read(profile.write()));
        assertEquals(true, LiftCalibrationProfile.isComplete(profile.write()));
        assertEquals(false, LiftCalibrationProfile.isComplete(new CompoundTag()));
    }

    @Test
    void mapsLearnedVerticalRatesBackToTheirTestedStrengths() {
        LiftCalibrationProfile profile = new LiftCalibrationProfile(5.4D, 6, 0.08D, 5, 0.06D, 100, 42L);

        assertEquals(5.4D, profile.strengthForVerticalVelocity(0.0D), 0.0001D);
        assertEquals(6.0D, profile.strengthForVerticalVelocity(0.08D), 0.0001D);
        assertEquals(5.0D, profile.strengthForVerticalVelocity(-0.06D), 0.0001D);
        assertEquals(5.7D, profile.strengthForVerticalVelocity(0.04D), 0.0001D);
    }

    @Test
    void persistsGenericRelativeControlMeasurements() {
        LiftCalibrationProfile profile = LiftCalibrationProfile.relative(
                0.04D,
                0.03D,
                140,
                42L,
                73,
                8,
                11,
                96
        );

        assertEquals(profile, LiftCalibrationProfile.read(profile.write()));
        assertEquals(true, profile.relativeControl());
        assertEquals(73, profile.takeoffIncreaseTicks());
        assertEquals(8, profile.increasePulseTicks());
        assertEquals(11, profile.decreasePulseTicks());
        assertEquals(96, profile.stopDecreaseTicks());
    }

    @Test
    void classifiesSlowButAcceleratingMotionBeforeIncreasingPower() {
        double classified = LiftCalibrationService.classifiedCalibrationVelocity(
                0.001171D,
                0.000463D,
                120
        );

        assertEquals(0.028951D, classified, 0.000001D);
    }

    @Test
    void predictsAReversalWhenAChangedOutputIsAlreadyBraking() {
        double classified = LiftCalibrationService.classifiedCalibrationVelocity(0.04D, -0.001D, 120);

        assertEquals(-0.02D, classified, 0.000001D);
    }

    @Test
    void doesNotBlameACommandForHeightMovementBeforeItsResponseWindow() {
        assertFalse(LiftCalibrationService.confirmedReversedRelativeResponse(
                1,
                120,
                false,
                0.75D
        ));
        assertTrue(LiftCalibrationService.confirmedReversedRelativeResponse(
                120,
                120,
                false,
                0.75D
        ));
    }

    @Test
    void treatsSmallGroundMovementAsAStableHeightWindow() {
        assertTrue(LiftCalibrationService.relativeHeightWindowIsStable(
                80.50D,
                80.49D,
                80.52D,
                80.51D
        ));
        assertFalse(LiftCalibrationService.relativeHeightWindowIsStable(
                80.50D,
                80.49D,
                81.10D,
                81.10D
        ));
    }

    @Test
    void endsAnUnboundedSecondHoverProbeBeforeTheGlobalSafetyLimit() {
        assertEquals(16.0D, LiftCalibrationService.relativeSecondHoverProbeRiseLimit(
                64.5D,
                99.87D
        ), 1.0E-9D);
        assertFalse(LiftCalibrationService.relativeSecondHoverProbeExceeded(
                64.5D,
                99.87D,
                115.86D,
                true
        ));
        assertTrue(LiftCalibrationService.relativeSecondHoverProbeExceeded(
                64.5D,
                99.87D,
                115.87D,
                true
        ));
        assertFalse(LiftCalibrationService.relativeSecondHoverProbeExceeded(
                64.5D,
                99.87D,
                120.0D,
                false
        ));
    }

    @Test
    void shortensTheSecondHoverProbeWhenTheFirstHoverUsedMostHeadroom() {
        assertEquals(2.0D, LiftCalibrationService.relativeSecondHoverProbeRiseLimit(
                64.0D,
                122.0D
        ), 1.0E-9D);
    }

    @Test
    void advancesPastInactiveRotorSpeedsWithoutSkippingAChangingRotor() {
        assertFalse(LiftCalibrationService.shouldAdvancePreTakeoffObservation(59, 240, 0.0D, true));
        assertTrue(LiftCalibrationService.shouldAdvancePreTakeoffObservation(60, 240, 0.0D, true));
        assertFalse(LiftCalibrationService.shouldAdvancePreTakeoffObservation(60, 240, 0.0D, false));
        assertFalse(LiftCalibrationService.shouldAdvancePreTakeoffObservation(60, 240, 0.1D, true));
        assertTrue(LiftCalibrationService.shouldAdvancePreTakeoffObservation(240, 240, 0.1D, false));
    }

    @Test
    void pulsesTheHigherNotchOnlyToBrakeAnUnsafeDescent() {
        assertEquals(1, LiftCalibrationService.relativeLandingBrakeAdjustment(
                false,
                -0.08D,
                -0.001D,
                124
        ));
        assertEquals(0, LiftCalibrationService.relativeLandingBrakeAdjustment(
                false,
                -0.06D,
                0.0D,
                124
        ));
        assertEquals(-1, LiftCalibrationService.relativeLandingBrakeAdjustment(
                true,
                -0.08D,
                0.001D,
                124
        ));
        assertEquals(0, LiftCalibrationService.relativeLandingBrakeAdjustment(
                true,
                -0.12D,
                0.0D,
                124
        ));
    }
}

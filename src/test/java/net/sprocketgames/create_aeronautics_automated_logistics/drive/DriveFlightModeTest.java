package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class DriveFlightModeTest {
    @Test
    void burnerStrengthSelectsAirshipControl() {
        assertEquals(DriveFlightMode.AIRSHIP, DriveFlightMode.resolve(EnumSet.of(
                DriveIntentType.LIFT_TARGET_STRENGTH
        )).orElseThrow());
    }

    @Test
    void pairedRotorRolesSelectRotorcraftControl() {
        assertEquals(DriveFlightMode.ROTORCRAFT, DriveFlightMode.resolve(EnumSet.of(
                DriveIntentType.LIFT_INCREASE,
                DriveIntentType.LIFT_DECREASE
        )).orElseThrow());
    }

    @Test
    void rejectsIncompleteOrMixedLiftModes() {
        assertTrue(DriveFlightMode.resolve(EnumSet.of(DriveIntentType.LIFT_INCREASE)).isEmpty());
        assertTrue(DriveFlightMode.resolve(EnumSet.of(
                DriveIntentType.LIFT_TARGET_STRENGTH,
                DriveIntentType.LIFT_INCREASE,
                DriveIntentType.LIFT_DECREASE
        )).isEmpty());
        assertTrue(DriveFlightMode.hasMixedLiftModes(EnumSet.of(
                DriveIntentType.LIFT_TARGET_STRENGTH,
                DriveIntentType.LIFT_INCREASE
        )));
    }
}

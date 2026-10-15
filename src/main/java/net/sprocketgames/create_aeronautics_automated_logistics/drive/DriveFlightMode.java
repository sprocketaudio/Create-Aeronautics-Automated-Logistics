package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import java.util.Optional;

/**
 * Selects the physical flight controller from the configured lift wiring.
 */
public enum DriveFlightMode {
    AIRSHIP,
    ROTORCRAFT;

    public static Optional<DriveFlightMode> resolve(Iterable<DriveIntentType> intents) {
        boolean burnerStrength = false;
        boolean rotorFaster = false;
        boolean rotorSlower = false;
        for (DriveIntentType intent : intents) {
            burnerStrength |= intent == DriveIntentType.LIFT_TARGET_STRENGTH;
            rotorFaster |= intent == DriveIntentType.LIFT_INCREASE;
            rotorSlower |= intent == DriveIntentType.LIFT_DECREASE;
        }
        if (burnerStrength && !rotorFaster && !rotorSlower) {
            return Optional.of(AIRSHIP);
        }
        if (!burnerStrength && rotorFaster && rotorSlower) {
            return Optional.of(ROTORCRAFT);
        }
        return Optional.empty();
    }

    public static boolean hasMixedLiftModes(Iterable<DriveIntentType> intents) {
        boolean burnerStrength = false;
        boolean rotorControl = false;
        for (DriveIntentType intent : intents) {
            burnerStrength |= intent == DriveIntentType.LIFT_TARGET_STRENGTH;
            rotorControl |= intent == DriveIntentType.LIFT_INCREASE
                    || intent == DriveIntentType.LIFT_DECREASE;
        }
        return burnerStrength && rotorControl;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.drive;

/**
 * The redstone contract a Drive Module exposes to nearby player-built wiring.
 * Mapping an intent to a signal remains future runtime work.
 */
public enum DriveEncodingType {
    STRENGTH_0_TO_15,
    ON_OFF,
    SPLIT_ANALOG,
    PULSE_NUDGE
}

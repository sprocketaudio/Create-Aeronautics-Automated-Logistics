package net.sprocketgames.create_aeronautics_automated_logistics.drive;

/**
 * Future runtime health exposed by a Drive Module. No health is calculated in
 * the scaffolding phase.
 */
public enum DriveModuleHealth {
    UNLINKED,
    LINKED,
    CONTROLLER_UNAVAILABLE,
    WRONG_VEHICLE,
    CONFLICTING_CONFIGURATION
}

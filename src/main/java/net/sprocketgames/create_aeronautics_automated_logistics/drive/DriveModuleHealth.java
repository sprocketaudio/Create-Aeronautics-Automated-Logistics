package net.sprocketgames.create_aeronautics_automated_logistics.drive;

/**
 * Future runtime health exposed by a Drive Module. No health is calculated in
 * the scaffolding phase.
 */
public enum DriveModuleHealth {
    UNLINKED,
    NO_ASSEMBLED_VEHICLE,
    NO_ADVANCED_CONTROLLER,
    MULTIPLE_CONTROLLERS,
    LINKED,
    CONTROLLER_UNAVAILABLE,
    WRONG_VEHICLE,
    CONFLICTING_CONFIGURATION
}

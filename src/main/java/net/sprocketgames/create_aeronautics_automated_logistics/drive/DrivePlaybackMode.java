package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;

/**
 * Chooses how route playback moves a vehicle. Module output remains disabled
 * until linked Drive Modules can provide a complete, validated control set.
 */
public enum DrivePlaybackMode {
    PHYSICAL,
    MODULE_OUTPUT;

    public static DrivePlaybackMode resolve(ServerLevel level, Route route) {
        return PHYSICAL;
    }
}

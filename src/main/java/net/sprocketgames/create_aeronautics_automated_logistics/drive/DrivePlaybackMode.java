package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DriveModuleOutputService;

/** Chooses whether route playback moves directly or emits Drive Module signals. */
public enum DrivePlaybackMode {
    PHYSICAL,
    MODULE_OUTPUT;

    public static DrivePlaybackMode resolve(ServerLevel level, Route route) {
        return DriveModuleOutputService.isAdvancedController(level, route)
                ? MODULE_OUTPUT
                : PHYSICAL;
    }
}

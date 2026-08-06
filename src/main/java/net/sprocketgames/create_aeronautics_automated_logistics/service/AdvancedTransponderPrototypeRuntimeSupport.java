package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;

/**
 * Isolates the obsolete fixed-port Advanced Transponder prototype hookup from the
 * shared playback service so later 0.7 work can replace it without reopening
 * route playback internals.
 *
 * This class intentionally preserves the current prototype behavior.
 */
public final class AdvancedTransponderPrototypeRuntimeSupport {
    private AdvancedTransponderPrototypeRuntimeSupport() {
    }

    public static void updateDriveOutputs(
            ServerLevel level,
            Route route,
            Vec3 currentPosition,
            Vec3 guidancePosition
    ) {
        RouteControllerLookup.liveTransponder(level, route)
                .filter(AdvancedTransponderBlockEntity.class::isInstance)
                .map(AdvancedTransponderBlockEntity.class::cast)
                .ifPresent(transponder -> transponder.updateDriveOutputs(currentPosition, guidancePosition));
    }

    public static void clearDriveOutputs(ServerLevel level, Route route) {
        RouteControllerLookup.liveTransponder(level, route)
                .filter(AdvancedTransponderBlockEntity.class::isInstance)
                .map(AdvancedTransponderBlockEntity.class::cast)
                .ifPresent(AdvancedTransponderBlockEntity::clearDriveOutputs);
    }

    public static boolean isAdvancedTransponderRoute(ServerLevel level, Route route) {
        return RouteControllerLookup.liveTransponder(level, route)
                .filter(AdvancedTransponderBlockEntity.class::isInstance)
                .isPresent();
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;

/**
 * Resolves the live controller for a recorded route. The persisted controller
 * position remains the primary lookup; the registry is only the existing
 * fallback for routes whose local controller position is unavailable.
 */
public final class RouteControllerLookup {
    private RouteControllerLookup() {
    }

    public static Optional<ShipTransponderBlockEntity> liveTransponder(ServerLevel level, Route route) {
        Optional<BlockPos> routeControllerPos = route.linkedController().controllerPos();
        if (routeControllerPos.isPresent()
                && level.getBlockEntity(routeControllerPos.get()) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }

        return ShipTransponderRegistry.knownShips(level.dimension()).stream()
                .filter(snapshot -> snapshot.controllerRef().filter(route.linkedController()::matches).isPresent())
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast)
                .findFirst();
    }
}

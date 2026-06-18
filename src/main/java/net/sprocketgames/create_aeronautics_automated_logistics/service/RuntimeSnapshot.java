package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public record RuntimeSnapshot(
        UUID runtimeId,
        UUID transponderId,
        String shipName,
        String routeName,
        ResourceKey<Level> dimension,
        Optional<UUID> ownerId,
        Optional<BlockPos> stationPos,
        Optional<BlockPos> transponderPos,
        Optional<Vec3> position,
        Optional<RouteId> activeRouteId,
        RuntimeState state,
        boolean pendingPlayback,
        int restoreCooldownTicks,
        Optional<PlaybackFailure> lastFailure
) {
    public boolean canPause() {
        return activeRouteId.isPresent()
                && !pendingPlayback
                && state != RuntimeState.PAUSED_MANUAL
                && state != RuntimeState.PAUSED_FAULT
                && state != RuntimeState.MISSING_PLAYBACK
                && state != RuntimeState.MISSING_CONTROLLER
                && state != RuntimeState.ROUTE_MISSING
                && state != RuntimeState.RESTORE_FAILED
                && state != RuntimeState.INVALID_RUNTIME
                && state != RuntimeState.KILLED
                && state != RuntimeState.COMPLETED;
    }
}

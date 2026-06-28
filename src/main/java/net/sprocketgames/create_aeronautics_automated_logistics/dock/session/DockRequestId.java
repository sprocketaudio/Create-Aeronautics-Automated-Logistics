package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public record DockRequestId(
        UUID value,
        Optional<UUID> transponderId,
        Optional<UUID> scheduleExecutionId,
        Optional<UUID> stopId,
        RouteId legacyRouteId
) {
    public DockRequestId {
        Objects.requireNonNull(value, "value");
        transponderId = Objects.requireNonNull(transponderId, "transponderId");
        scheduleExecutionId = Objects.requireNonNull(scheduleExecutionId, "scheduleExecutionId");
        stopId = Objects.requireNonNull(stopId, "stopId");
        Objects.requireNonNull(legacyRouteId, "legacyRouteId");
    }

    public boolean stable() {
        return transponderId.isPresent() && scheduleExecutionId.isPresent();
    }

    public static DockRequestId scheduled(
            UUID transponderId,
            UUID scheduleExecutionId,
            Optional<UUID> stopId,
            RouteId routeId
    ) {
        return new DockRequestId(
                scheduleExecutionId,
                Optional.of(transponderId),
                Optional.of(scheduleExecutionId),
                stopId,
                routeId
        );
    }

    public static DockRequestId legacy(
            RouteId routeId,
            Optional<UUID> transponderId,
            Optional<UUID> stopId
    ) {
        return new DockRequestId(
                UUID.nameUUIDFromBytes(("legacy-dock-request:" + routeId.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                transponderId,
                Optional.empty(),
                stopId,
                routeId
        );
    }
}

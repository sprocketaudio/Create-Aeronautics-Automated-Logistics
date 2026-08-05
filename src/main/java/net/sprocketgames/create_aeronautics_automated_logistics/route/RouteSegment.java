package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public record RouteSegment(
        RouteSegmentId id,
        TransportMode transportMode,
        UUID startStationId,
        String startStationName,
        UUID endStationId,
        String endStationName,
        UUID transponderId,
        String shipName,
        Optional<UUID> runtimeShipId,
        ResourceKey<Level> dimension,
        List<RoutePoint> points,
        VehicleControllerRef controllerRef,
        long createdGameTime,
        long createdEpochMillis,
        Optional<UUID> ownerId
) {
    public RouteSegment(
            RouteSegmentId id,
            UUID startStationId,
            String startStationName,
            UUID endStationId,
            String endStationName,
            UUID transponderId,
            String shipName,
            Optional<UUID> runtimeShipId,
            ResourceKey<Level> dimension,
            List<RoutePoint> points,
            VehicleControllerRef controllerRef,
            long createdGameTime,
            long createdEpochMillis,
            Optional<UUID> ownerId
    ) {
        this(
                id,
                TransportMode.DEFAULT,
                startStationId,
                startStationName,
                endStationId,
                endStationName,
                transponderId,
                shipName,
                runtimeShipId,
                dimension,
                points,
                controllerRef,
                createdGameTime,
                createdEpochMillis,
                ownerId
        );
    }

    public RouteSegment {
        Objects.requireNonNull(id, "id");
        transportMode = transportMode == null ? TransportMode.DEFAULT : transportMode;
        Objects.requireNonNull(startStationId, "startStationId");
        Objects.requireNonNull(startStationName, "startStationName");
        Objects.requireNonNull(endStationId, "endStationId");
        Objects.requireNonNull(endStationName, "endStationName");
        Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(shipName, "shipName");
        runtimeShipId = Objects.requireNonNull(runtimeShipId, "runtimeShipId");
        Objects.requireNonNull(dimension, "dimension");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        Objects.requireNonNull(controllerRef, "controllerRef");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
    }

    public boolean connects(UUID startStationId, UUID endStationId, UUID transponderId) {
        return this.startStationId.equals(startStationId)
                && this.endStationId.equals(endStationId)
                && this.transponderId.equals(transponderId);
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public record Route(
        RouteId id,
        String name,
        TransportMode transportMode,
        ResourceKey<Level> dimension,
        List<RoutePoint> points,
        VehicleControllerRef linkedController,
        PlaybackMode playbackMode,
        RouteStatus status,
        List<RouteStop> stops,
        Optional<UUID> ownerId
) {
    public Route(
            RouteId id,
            String name,
            ResourceKey<Level> dimension,
            List<RoutePoint> points,
            VehicleControllerRef linkedController,
            PlaybackMode playbackMode,
            RouteStatus status
    ) {
        this(id, name, TransportMode.DEFAULT, dimension, points, linkedController, playbackMode, status, List.of(), Optional.empty());
    }

    public Route(
            RouteId id,
            String name,
            ResourceKey<Level> dimension,
            List<RoutePoint> points,
            VehicleControllerRef linkedController,
            PlaybackMode playbackMode,
            RouteStatus status,
            List<RouteStop> stops,
            Optional<UUID> ownerId
    ) {
        this(id, name, TransportMode.DEFAULT, dimension, points, linkedController, playbackMode, status, stops, ownerId);
    }

    public Route(
            RouteId id,
            String name,
            ResourceKey<Level> dimension,
            List<RoutePoint> points,
            VehicleControllerRef linkedController,
            PlaybackMode playbackMode,
            RouteStatus status,
            Optional<UUID> ownerId
    ) {
        this(id, name, TransportMode.DEFAULT, dimension, points, linkedController, playbackMode, status, List.of(), ownerId);
    }

    public Route {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        transportMode = transportMode == null ? TransportMode.DEFAULT : transportMode;
        Objects.requireNonNull(dimension, "dimension");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        Objects.requireNonNull(linkedController, "linkedController");
        Objects.requireNonNull(playbackMode, "playbackMode");
        Objects.requireNonNull(status, "status");
        stops = List.copyOf(Objects.requireNonNull(stops, "stops"));
        ownerId = Objects.requireNonNull(ownerId, "ownerId");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        for (RouteStop stop : stops) {
            if (stop.pointIndex() >= points.size()) {
                throw new IllegalArgumentException("stop pointIndex outside route points");
            }
        }
    }

    public Route withStatus(RouteStatus status) {
        return new Route(id, name, transportMode, dimension, points, linkedController, playbackMode, status, stops, ownerId);
    }

    public Route withStops(List<RouteStop> stops) {
        return new Route(id, name, transportMode, dimension, points, linkedController, playbackMode, status, stops, ownerId);
    }
}

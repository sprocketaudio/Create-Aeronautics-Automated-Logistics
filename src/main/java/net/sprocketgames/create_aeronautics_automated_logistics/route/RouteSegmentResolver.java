package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;

public final class RouteSegmentResolver {
    private RouteSegmentResolver() {
    }

    public static List<RouteSegment> validOutgoingSegments(
            AirshipStationBlockEntity station,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        if (station.getLevel() == null || station.getLevel().isClientSide || !(station.getLevel().getServer() instanceof MinecraftServer server)) {
            return RouteSegmentRegistry.matchingAnyStart(station.stationId(), dimension, transponderId).stream()
                    .filter(segment -> segment.dimension().equals(dimension))
                    .filter(segment -> segment.startStationId().equals(station.stationId()))
                    .sorted(newestFirst())
                    .toList();
        }
        return AutomatedLogisticsServices.ROUTES.matchingAnyStart(server, station.stationId(), dimension, transponderId).stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(station.stationId()))
                .sorted(newestFirst())
                .toList();
    }

    public static List<RouteSegment> validLocalSegments(
            AirshipStationBlockEntity station,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        if (station.getLevel() == null || station.getLevel().isClientSide || !(station.getLevel().getServer() instanceof MinecraftServer server)) {
            return RouteSegmentRegistry.connectedToStation(station.stationId()).stream()
                    .filter(segment -> segment.dimension().equals(dimension))
                    .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                    .sorted(newestFirst())
                    .toList();
        }
        return AutomatedLogisticsServices.ROUTES.connectedToStation(server, station.stationId()).stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(newestFirst())
                .toList();
    }

    public static Optional<RouteSegment> newestFor(
            AirshipStationBlockEntity station,
            UUID targetStationId,
            ResourceKey<Level> dimension,
            UUID transponderId
    ) {
        return validOutgoingSegments(station, dimension, Optional.of(transponderId)).stream()
                .filter(segment -> segment.endStationId().equals(targetStationId))
                .findFirst();
    }

    public static Optional<RouteSegment> newestFor(
            UUID startStationId,
            UUID targetStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return RouteSegmentRegistry.matching(startStationId, targetStationId, dimension, transponderId).stream().findFirst();
    }

    public static Optional<RouteSegment> newestFor(
            MinecraftServer server,
            UUID startStationId,
            UUID targetStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return AutomatedLogisticsServices.ROUTES.matching(
                server,
                startStationId,
                targetStationId,
                dimension,
                transponderId
        ).stream().findFirst();
    }

    private static Comparator<RouteSegment> newestFirst() {
        return Comparator
                .comparingLong(RouteSegment::createdEpochMillis)
                .reversed()
                .thenComparing(segment -> segment.id().value().toString());
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;

public final class RouteSegmentResolver {
    private RouteSegmentResolver() {
    }

    public static List<RouteSegment> validOutgoingSegments(
            AirshipStationBlockEntity station,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        if (station.getLevel() instanceof ServerLevel serverLevel) {
            return mergeDirectoryAndRegistry(
                    RouteSegmentDirectorySavedData.matchingAnyStart(serverLevel.getServer(), station.stationId(), dimension, transponderId),
                    RouteSegmentRegistry.matchingAnyStart(station.stationId(), dimension, transponderId)
            );
        }
        return RouteSegmentRegistry.matchingAnyStart(station.stationId(), dimension, transponderId).stream()
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
        if (station.getLevel() instanceof ServerLevel serverLevel) {
            List<RouteSegment> directorySegments = RouteSegmentDirectorySavedData.connectedToStation(serverLevel.getServer(), station.stationId()).stream()
                    .filter(segment -> segment.dimension().equals(dimension))
                    .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                    .sorted(newestFirst())
                    .toList();
            List<RouteSegment> registrySegments = RouteSegmentRegistry.connectedToStation(station.stationId()).stream()
                    .filter(segment -> segment.dimension().equals(dimension))
                    .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                    .sorted(newestFirst())
                    .toList();
            return mergeDirectoryAndRegistry(directorySegments, registrySegments);
        }
        return RouteSegmentRegistry.connectedToStation(station.stationId()).stream()
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

    public static Optional<RouteSegment> byId(ServerLevel level, RouteSegmentId segmentId) {
        return RouteSegmentDirectorySavedData.byId(level.getServer(), segmentId)
                .or(() -> RouteSegmentRegistry.byId(segmentId));
    }

    public static Optional<RouteSegment> endingAt(
            ServerLevel level,
            UUID endStationId,
            Optional<UUID> transponderId
    ) {
        return RouteSegmentDirectorySavedData.endingAt(level.getServer(), endStationId, level.dimension(), transponderId).stream()
                .findFirst()
                .or(() -> RouteSegmentRegistry.endingAt(endStationId, level.dimension(), transponderId).stream().findFirst());
    }

    public static Optional<RouteSegment> newestFor(
            ServerLevel level,
            UUID startStationId,
            UUID targetStationId,
            Optional<UUID> transponderId
    ) {
        return RouteSegmentDirectorySavedData.matching(
                        level.getServer(),
                        startStationId,
                        targetStationId,
                        level.dimension(),
                        transponderId
                ).stream()
                .findFirst()
                .or(() -> RouteSegmentRegistry.matching(startStationId, targetStationId, level.dimension(), transponderId)
                        .stream()
                        .findFirst());
    }

    public static Optional<RouteSegment> scheduledSegment(
            ServerLevel level,
            UUID startStationId,
            UUID targetStationId,
            Optional<RouteSegmentId> pinnedSegmentId,
            UUID transponderId
    ) {
        return pinnedSegmentId
                .flatMap(segmentId -> byId(level, segmentId))
                .filter(candidate -> candidate.startStationId().equals(startStationId))
                .filter(candidate -> candidate.endStationId().equals(targetStationId))
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(transponderId))
                .or(() -> newestFor(level, startStationId, targetStationId, Optional.of(transponderId)));
    }

    public static Optional<RouteSegment> newestFor(
            UUID startStationId,
            UUID targetStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return RouteSegmentRegistry.matching(startStationId, targetStationId, dimension, transponderId).stream().findFirst();
    }

    private static Comparator<RouteSegment> newestFirst() {
        return Comparator
                .comparingLong(RouteSegment::createdEpochMillis)
                .reversed()
                .thenComparing(segment -> segment.id().value().toString());
    }

    private static List<RouteSegment> mergeDirectoryAndRegistry(
            List<RouteSegment> directorySegments,
            List<RouteSegment> registrySegments
    ) {
        java.util.LinkedHashMap<RouteSegmentId, RouteSegment> merged = new java.util.LinkedHashMap<>();
        directorySegments.forEach(segment -> merged.put(segment.id(), segment));
        registrySegments.forEach(segment -> merged.putIfAbsent(segment.id(), segment));
        return merged.values().stream()
                .sorted(newestFirst())
                .toList();
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class RouteSegmentRegistry {
    private static final Map<RouteSegmentId, RouteSegment> SEGMENTS = new ConcurrentHashMap<>();

    private RouteSegmentRegistry() {
    }

    public static void registerAll(List<RouteSegment> segments) {
        for (RouteSegment segment : segments) {
            SEGMENTS.put(segment.id(), segment);
        }
    }

    public static void replaceForStartStation(UUID stationId, List<RouteSegment> segments) {
        SEGMENTS.values().removeIf(segment -> segment.startStationId().equals(stationId));
        registerAll(segments);
    }

    public static void unregisterStartStation(UUID stationId) {
        SEGMENTS.values().removeIf(segment -> segment.startStationId().equals(stationId));
    }

    public static Optional<RouteSegment> byId(RouteSegmentId segmentId) {
        return Optional.ofNullable(SEGMENTS.get(segmentId));
    }

    public static void unregister(RouteSegmentId segmentId) {
        SEGMENTS.remove(segmentId);
    }

    public static List<RouteSegment> connectedToStation(UUID stationId) {
        return SEGMENTS.values().stream()
                .filter(segment -> segment.startStationId().equals(stationId) || segment.endStationId().equals(stationId))
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    public static List<RouteSegment> forTransponder(UUID transponderId) {
        return SEGMENTS.values().stream()
                .filter(segment -> segment.transponderId().equals(transponderId))
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    public static List<RouteSegment> endingAt(
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return SEGMENTS.values().stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.endStationId().equals(endStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    public static List<RouteSegment> matching(
            UUID startStationId,
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return SEGMENTS.values().stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> segment.endStationId().equals(endStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    public static List<RouteSegment> matchingAnyStart(
            UUID startStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return SEGMENTS.values().stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }
}

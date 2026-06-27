package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class RouteSegmentDirectorySavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_route_segment_directory";
    private static final String SEGMENTS = "segments";
    private static final String PENDING_DELETES = "pendingDeletes";
    private static final String STATION_ID = "stationId";
    private static final String SEGMENT_IDS = "segmentIds";

    private final Map<RouteSegmentId, RouteSegment> segments = new LinkedHashMap<>();
    private final Map<UUID, Set<RouteSegmentId>> pendingDeletesByStation = new LinkedHashMap<>();

    public static SavedData.Factory<RouteSegmentDirectorySavedData> factory() {
        return new SavedData.Factory<>(RouteSegmentDirectorySavedData::new, RouteSegmentDirectorySavedData::load);
    }

    public static RouteSegmentDirectorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void upsert(MinecraftServer server, RouteSegment segment) {
        get(server).upsert(segment);
    }

    public static void remove(MinecraftServer server, RouteSegmentId segmentId) {
        get(server).remove(segmentId);
    }

    public static Optional<RouteSegment> byId(MinecraftServer server, RouteSegmentId segmentId) {
        return get(server).byId(segmentId);
    }

    public static List<RouteSegment> connectedToStation(MinecraftServer server, UUID stationId) {
        return get(server).connectedToStation(stationId);
    }

    public static List<RouteSegment> forTransponder(MinecraftServer server, UUID transponderId) {
        return get(server).forTransponder(transponderId);
    }

    public static List<RouteSegment> endingAt(
            MinecraftServer server,
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return get(server).endingAt(endStationId, dimension, transponderId);
    }

    public static List<RouteSegment> matching(
            MinecraftServer server,
            UUID startStationId,
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return get(server).matching(startStationId, endStationId, dimension, transponderId);
    }

    public static List<RouteSegment> matchingAnyStart(
            MinecraftServer server,
            UUID startStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return get(server).matchingAnyStart(startStationId, dimension, transponderId);
    }

    public static void addPendingDelete(MinecraftServer server, UUID stationId, RouteSegmentId segmentId) {
        get(server).addPendingDelete(stationId, segmentId);
    }

    public static Set<RouteSegmentId> consumePendingDeletes(MinecraftServer server, UUID stationId) {
        return get(server).consumePendingDeletes(stationId);
    }

    public Map<UUID, Set<RouteSegmentId>> pendingDeletes() {
        Map<UUID, Set<RouteSegmentId>> copy = new LinkedHashMap<>();
        pendingDeletesByStation.forEach((stationId, segmentIds) -> copy.put(stationId, Set.copyOf(segmentIds)));
        return Map.copyOf(copy);
    }

    private void upsert(RouteSegment segment) {
        RouteSegment previous = segments.put(segment.id(), segment);
        if (!segment.equals(previous)) {
            setDirty();
        }
    }

    private void remove(RouteSegmentId segmentId) {
        if (segments.remove(segmentId) != null) {
            setDirty();
        }
    }

    private Optional<RouteSegment> byId(RouteSegmentId segmentId) {
        return Optional.ofNullable(segments.get(segmentId));
    }

    private List<RouteSegment> connectedToStation(UUID stationId) {
        return segments.values().stream()
                .filter(segment -> segment.startStationId().equals(stationId) || segment.endStationId().equals(stationId))
                .sorted(segmentSort())
                .toList();
    }

    private List<RouteSegment> forTransponder(UUID transponderId) {
        return segments.values().stream()
                .filter(segment -> segment.transponderId().equals(transponderId))
                .sorted(segmentSort())
                .toList();
    }

    private List<RouteSegment> endingAt(
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return segments.values().stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.endStationId().equals(endStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(segmentSort())
                .toList();
    }

    private List<RouteSegment> matching(
            UUID startStationId,
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return segments.values().stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> segment.endStationId().equals(endStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(segmentSort())
                .toList();
    }

    private List<RouteSegment> matchingAnyStart(
            UUID startStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        return segments.values().stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(segmentSort())
                .toList();
    }

    private static Comparator<RouteSegment> segmentSort() {
        return Comparator
                .comparingLong(RouteSegment::createdEpochMillis)
                .reversed()
                .thenComparing(segment -> segment.id().value().toString());
    }

    private void addPendingDelete(UUID stationId, RouteSegmentId segmentId) {
        Set<RouteSegmentId> segmentIds = pendingDeletesByStation.computeIfAbsent(stationId, ignored -> new HashSet<>());
        if (segmentIds.add(segmentId)) {
            setDirty();
        }
    }

    private Set<RouteSegmentId> consumePendingDeletes(UUID stationId) {
        Set<RouteSegmentId> removed = pendingDeletesByStation.remove(stationId);
        if (removed == null || removed.isEmpty()) {
            return Set.of();
        }
        setDirty();
        return Set.copyOf(removed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(SEGMENTS, RouteSegmentNbtSerializer.writeSegments(List.copyOf(segments.values())));

        ListTag pendingDeletes = new ListTag();
        for (Map.Entry<UUID, Set<RouteSegmentId>> entry : pendingDeletesByStation.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag stationTag = new CompoundTag();
            stationTag.putUUID(STATION_ID, entry.getKey());
            ListTag segmentIds = new ListTag();
            entry.getValue().stream()
                    .map(RouteSegmentId::value)
                    .sorted(Comparator.comparing(UUID::toString))
                    .forEach(segmentId -> {
                        CompoundTag segmentTag = new CompoundTag();
                        segmentTag.putUUID("id", segmentId);
                        segmentIds.add(segmentTag);
                    });
            stationTag.put(SEGMENT_IDS, segmentIds);
            pendingDeletes.add(stationTag);
        }
        tag.put(PENDING_DELETES, pendingDeletes);
        return tag;
    }

    private static RouteSegmentDirectorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RouteSegmentDirectorySavedData data = new RouteSegmentDirectorySavedData();
        if (tag.contains(SEGMENTS, Tag.TAG_LIST)) {
            for (RouteSegment segment : RouteSegmentNbtSerializer.readSegments(tag.getList(SEGMENTS, Tag.TAG_COMPOUND))) {
                data.segments.put(segment.id(), segment);
            }
        }
        if (tag.contains(PENDING_DELETES, Tag.TAG_LIST)) {
            ListTag pendingDeletes = tag.getList(PENDING_DELETES, Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingDeletes.size(); i++) {
                readPendingDelete(pendingDeletes.getCompound(i)).ifPresent(entry ->
                        data.pendingDeletesByStation.put(entry.stationId(), entry.segmentIds()));
            }
        }
        return data;
    }

    private static Optional<PendingDeleteEntry> readPendingDelete(CompoundTag tag) {
        if (!tag.hasUUID(STATION_ID) || !tag.contains(SEGMENT_IDS, Tag.TAG_LIST)) {
            return Optional.empty();
        }
        Set<RouteSegmentId> segmentIds = new HashSet<>();
        ListTag segmentList = tag.getList(SEGMENT_IDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < segmentList.size(); i++) {
            CompoundTag segmentTag = segmentList.getCompound(i);
            if (segmentTag.hasUUID("id")) {
                segmentIds.add(new RouteSegmentId(segmentTag.getUUID("id")));
            }
        }
        return Optional.of(new PendingDeleteEntry(tag.getUUID(STATION_ID), segmentIds));
    }

    private record PendingDeleteEntry(UUID stationId, Set<RouteSegmentId> segmentIds) {
        private PendingDeleteEntry {
            segmentIds = Set.copyOf(segmentIds);
        }
    }
}

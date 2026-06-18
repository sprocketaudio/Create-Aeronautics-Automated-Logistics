package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;

public class RouteSegmentDirectorySavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_route_segment_directory";
    private static final String STORED_SEGMENTS = "StoredSegments";
    private static final String PENDING_DELETIONS = "PendingDeletions";
    private static final String HOLDER_STATION_ID = "holderStationId";
    private static final String SEGMENT_ID = "segmentId";
    private static final String START_STATION_ID = "startStationId";
    private static final String END_STATION_ID = "endStationId";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String DIMENSION = "dimension";

    private final Map<StoredSegmentKey, StoredSegmentRecord> storedSegments = new ConcurrentHashMap<>();
    private final Map<UUID, Set<RouteSegmentId>> pendingDeletionsByHolder = new ConcurrentHashMap<>();

    public static SavedData.Factory<RouteSegmentDirectorySavedData> factory() {
        return new SavedData.Factory<>(RouteSegmentDirectorySavedData::new, RouteSegmentDirectorySavedData::load);
    }

    public static RouteSegmentDirectorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void replaceStoredSegments(MinecraftServer server, UUID holderStationId, List<RouteSegment> segments) {
        RouteSegmentDirectorySavedData data = get(server);
        boolean changed = data.removeStoredSegmentsForHolderInternal(holderStationId);
        for (RouteSegment segment : segments) {
            StoredSegmentRecord record = StoredSegmentRecord.from(holderStationId, segment);
            StoredSegmentRecord previous = data.storedSegments.put(record.key(), record);
            if (!record.equals(previous)) {
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    public static void removeStoredSegment(MinecraftServer server, UUID holderStationId, RouteSegmentId segmentId) {
        RouteSegmentDirectorySavedData data = get(server);
        if (data.storedSegments.remove(new StoredSegmentKey(holderStationId, segmentId)) != null) {
            data.setDirty();
        }
    }

    public static void removeStoredSegmentsForHolder(MinecraftServer server, UUID holderStationId) {
        RouteSegmentDirectorySavedData data = get(server);
        boolean changed = data.removeStoredSegmentsForHolderInternal(holderStationId);
        if (changed) {
            data.setDirty();
        }
    }

    public static List<StoredSegmentRecord> connectedToStation(MinecraftServer server, UUID stationId) {
        return get(server).storedSegments.values().stream()
                .filter(record -> record.startStationId().equals(stationId) || record.endStationId().equals(stationId))
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public static List<StoredSegmentRecord> forTransponder(MinecraftServer server, UUID transponderId) {
        return get(server).storedSegments.values().stream()
                .filter(record -> record.transponderId().equals(transponderId))
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public static List<StoredSegmentRecord> storedCopiesForSegment(MinecraftServer server, RouteSegmentId segmentId) {
        return get(server).storedSegments.values().stream()
                .filter(record -> record.segmentId().equals(segmentId))
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public static List<StoredSegmentRecord> allStoredSegments(MinecraftServer server) {
        return get(server).storedSegments.values().stream()
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public static boolean queuePendingDeletion(MinecraftServer server, UUID holderStationId, RouteSegmentId segmentId) {
        RouteSegmentDirectorySavedData data = get(server);
        boolean added = data.pendingDeletionsByHolder
                .computeIfAbsent(holderStationId, ignored -> new LinkedHashSet<>())
                .add(segmentId);
        if (added) {
            data.setDirty();
        }
        return added;
    }

    public static List<RouteSegmentId> pendingDeletionsForHolder(MinecraftServer server, UUID holderStationId) {
        return get(server).pendingDeletionsByHolder.getOrDefault(holderStationId, Set.of()).stream()
                .sorted(Comparator.comparing(id -> id.value().toString()))
                .toList();
    }

    public static List<PendingDeletionRecord> allPendingDeletions(MinecraftServer server) {
        return get(server).pendingDeletionsByHolder.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(segmentId -> new PendingDeletionRecord(entry.getKey(), segmentId)))
                .sorted(PendingDeletionRecord.sortOrder())
                .toList();
    }

    public static boolean clearPendingDeletion(MinecraftServer server, UUID holderStationId, RouteSegmentId segmentId) {
        RouteSegmentDirectorySavedData data = get(server);
        Set<RouteSegmentId> pending = data.pendingDeletionsByHolder.get(holderStationId);
        if (pending == null || !pending.remove(segmentId)) {
            return false;
        }
        if (pending.isEmpty()) {
            data.pendingDeletionsByHolder.remove(holderStationId);
        }
        data.setDirty();
        return true;
    }

    public static void clearPendingDeletionsForHolder(MinecraftServer server, UUID holderStationId) {
        RouteSegmentDirectorySavedData data = get(server);
        if (data.pendingDeletionsByHolder.remove(holderStationId) != null) {
            data.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag storedList = new ListTag();
        for (StoredSegmentRecord record : storedSegments.values().stream().sorted(StoredSegmentRecord.sortOrder()).toList()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID(HOLDER_STATION_ID, record.holderStationId());
            recordTag.putUUID(SEGMENT_ID, record.segmentId().value());
            recordTag.putUUID(START_STATION_ID, record.startStationId());
            recordTag.putUUID(END_STATION_ID, record.endStationId());
            recordTag.putUUID(TRANSPONDER_ID, record.transponderId());
            recordTag.putString(DIMENSION, record.dimension().location().toString());
            storedList.add(recordTag);
        }
        tag.put(STORED_SEGMENTS, storedList);

        ListTag pendingList = new ListTag();
        pendingDeletionsByHolder.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag pendingTag = new CompoundTag();
                    pendingTag.putUUID(HOLDER_STATION_ID, entry.getKey());
                    ListTag segmentList = new ListTag();
                    entry.getValue().stream()
                            .sorted(Comparator.comparing(segmentId -> segmentId.value().toString()))
                            .forEach(segmentId -> {
                                CompoundTag segmentTag = new CompoundTag();
                                segmentTag.putUUID(SEGMENT_ID, segmentId.value());
                                segmentList.add(segmentTag);
                            });
                    pendingTag.put(PENDING_DELETIONS, segmentList);
                    pendingList.add(pendingTag);
                });
        tag.put(PENDING_DELETIONS, pendingList);
        return tag;
    }

    private static RouteSegmentDirectorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RouteSegmentDirectorySavedData data = new RouteSegmentDirectorySavedData();
        if (tag.contains(STORED_SEGMENTS, Tag.TAG_LIST)) {
            ListTag storedList = tag.getList(STORED_SEGMENTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < storedList.size(); i++) {
                readStoredSegment(storedList.getCompound(i))
                        .ifPresent(record -> data.storedSegments.put(record.key(), record));
            }
        }
        if (tag.contains(PENDING_DELETIONS, Tag.TAG_LIST)) {
            ListTag pendingList = tag.getList(PENDING_DELETIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingList.size(); i++) {
                CompoundTag pendingTag = pendingList.getCompound(i);
                if (!pendingTag.hasUUID(HOLDER_STATION_ID) || !pendingTag.contains(PENDING_DELETIONS, Tag.TAG_LIST)) {
                    continue;
                }
                UUID holderStationId = pendingTag.getUUID(HOLDER_STATION_ID);
                ListTag segmentList = pendingTag.getList(PENDING_DELETIONS, Tag.TAG_COMPOUND);
                Set<RouteSegmentId> segmentIds = new LinkedHashSet<>();
                for (int j = 0; j < segmentList.size(); j++) {
                    CompoundTag segmentTag = segmentList.getCompound(j);
                    if (segmentTag.hasUUID(SEGMENT_ID)) {
                        segmentIds.add(new RouteSegmentId(segmentTag.getUUID(SEGMENT_ID)));
                    }
                }
                if (!segmentIds.isEmpty()) {
                    data.pendingDeletionsByHolder.put(holderStationId, segmentIds);
                }
            }
        }
        return data;
    }

    private boolean removeStoredSegmentsForHolderInternal(UUID holderStationId) {
        List<StoredSegmentKey> keysToRemove = storedSegments.keySet().stream()
                .filter(key -> key.holderStationId().equals(holderStationId))
                .toList();
        if (keysToRemove.isEmpty()) {
            return false;
        }
        keysToRemove.forEach(storedSegments::remove);
        return true;
    }

    private static Optional<StoredSegmentRecord> readStoredSegment(CompoundTag tag) {
        if (!tag.hasUUID(HOLDER_STATION_ID)
                || !tag.hasUUID(SEGMENT_ID)
                || !tag.hasUUID(START_STATION_ID)
                || !tag.hasUUID(END_STATION_ID)
                || !tag.hasUUID(TRANSPONDER_ID)
                || !tag.contains(DIMENSION, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        if (dimensionId == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredSegmentRecord(
                tag.getUUID(HOLDER_STATION_ID),
                new RouteSegmentId(tag.getUUID(SEGMENT_ID)),
                tag.getUUID(START_STATION_ID),
                tag.getUUID(END_STATION_ID),
                tag.getUUID(TRANSPONDER_ID),
                ResourceKey.create(Registries.DIMENSION, dimensionId)
        ));
    }

    private record StoredSegmentKey(UUID holderStationId, RouteSegmentId segmentId) {
    }

    public record StoredSegmentRecord(
            UUID holderStationId,
            RouteSegmentId segmentId,
            UUID startStationId,
            UUID endStationId,
            UUID transponderId,
            ResourceKey<Level> dimension
    ) {
        private StoredSegmentKey key() {
            return new StoredSegmentKey(holderStationId, segmentId);
        }

        private static StoredSegmentRecord from(UUID holderStationId, RouteSegment segment) {
            return new StoredSegmentRecord(
                    holderStationId,
                    segment.id(),
                    segment.startStationId(),
                    segment.endStationId(),
                    segment.transponderId(),
                    segment.dimension()
            );
        }

        public static Comparator<StoredSegmentRecord> sortOrder() {
            return Comparator
                    .comparing((StoredSegmentRecord record) -> record.segmentId().value().toString())
                    .thenComparing(record -> record.holderStationId().toString());
        }
    }

    public record PendingDeletionRecord(UUID holderStationId, RouteSegmentId segmentId) {
        public static Comparator<PendingDeletionRecord> sortOrder() {
            return Comparator
                    .comparing((PendingDeletionRecord record) -> record.holderStationId().toString())
                    .thenComparing(record -> record.segmentId().value().toString());
        }
    }
}

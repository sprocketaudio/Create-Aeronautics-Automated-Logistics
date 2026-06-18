package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteSegmentDirectorySavedData.StoredSegmentRecord;

public final class RouteRepository {
    public Optional<RouteSegment> byId(MinecraftServer server, RouteSegmentId segmentId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(segmentId, "segmentId");
        return allSegments(server, false).stream()
                .filter(segment -> segment.id().equals(segmentId))
                .findFirst();
    }

    public List<RouteSegment> connectedToStation(MinecraftServer server, UUID stationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(stationId, "stationId");
        return allSegments(server, false).stream()
                .filter(segment -> segment.startStationId().equals(stationId) || segment.endStationId().equals(stationId))
                .sorted(newestFirst())
                .toList();
    }

    public List<RouteSegment> connectedToStationForExplicitCleanup(MinecraftServer server, UUID stationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(stationId, "stationId");
        return allSegments(server, true).stream()
                .filter(segment -> segment.startStationId().equals(stationId) || segment.endStationId().equals(stationId))
                .sorted(newestFirst())
                .toList();
    }

    public List<RouteSegment> forTransponder(MinecraftServer server, UUID transponderId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transponderId, "transponderId");
        return allSegments(server, false).stream()
                .filter(segment -> segment.transponderId().equals(transponderId))
                .sorted(newestFirst())
                .toList();
    }

    public List<RouteSegment> forTransponderForExplicitCleanup(MinecraftServer server, UUID transponderId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transponderId, "transponderId");
        return allSegments(server, true).stream()
                .filter(segment -> segment.transponderId().equals(transponderId))
                .sorted(newestFirst())
                .toList();
    }

    public List<RouteSegment> endingAt(
            MinecraftServer server,
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(endStationId, "endStationId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(transponderId, "transponderId");
        return allSegments(server, false).stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.endStationId().equals(endStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(newestFirst())
                .toList();
    }

    public List<RouteSegment> matching(
            MinecraftServer server,
            UUID startStationId,
            UUID endStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(startStationId, "startStationId");
        Objects.requireNonNull(endStationId, "endStationId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(transponderId, "transponderId");
        return allSegments(server, false).stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> segment.endStationId().equals(endStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(newestFirst())
                .toList();
    }

    public List<RouteSegment> matchingAnyStart(
            MinecraftServer server,
            UUID startStationId,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(startStationId, "startStationId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(transponderId, "transponderId");
        return allSegments(server, false).stream()
                .filter(segment -> segment.dimension().equals(dimension))
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> transponderId.map(id -> id.equals(segment.transponderId())).orElse(true))
                .sorted(newestFirst())
                .toList();
    }

    public void refreshIndexForStartStation(UUID stationId, List<RouteSegment> segments) {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(segments, "segments");
        RouteSegmentRegistry.replaceForStartStation(stationId, segments);
    }

    public void syncStoredSegments(MinecraftServer server, UUID holderStationId, List<RouteSegment> segments) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        Objects.requireNonNull(segments, "segments");
        RouteSegmentDirectorySavedData.replaceStoredSegments(server, holderStationId, segments);
    }

    public void evictIndexedSegment(RouteSegmentId segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        RouteSegmentRegistry.unregister(segmentId);
    }

    public int rebuildLoadedRouteIndex(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        List<RouteSegment> loadedSegments = allSegments(server, false);
        RouteSegmentRegistry.replaceAll(loadedSegments);
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Runtime restore rebuilt route segment cache from loaded persistent stations segments={}",
                loadedSegments.size()
        );
        return loadedSegments.size();
    }

    public List<StoredSegmentRecord> storedSegmentsConnectedToStation(MinecraftServer server, UUID stationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(stationId, "stationId");
        return RouteSegmentDirectorySavedData.connectedToStation(server, stationId);
    }

    public List<StoredSegmentRecord> storedSegmentsConnectedToStationForExplicitCleanup(MinecraftServer server, UUID stationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(stationId, "stationId");
        return explicitCleanupStoredRecords(server).stream()
                .filter(record -> record.startStationId().equals(stationId) || record.endStationId().equals(stationId))
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public List<StoredSegmentRecord> storedSegmentsForTransponder(MinecraftServer server, UUID transponderId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transponderId, "transponderId");
        return RouteSegmentDirectorySavedData.forTransponder(server, transponderId);
    }

    public List<StoredSegmentRecord> storedSegmentsForTransponderForExplicitCleanup(MinecraftServer server, UUID transponderId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transponderId, "transponderId");
        return explicitCleanupStoredRecords(server).stream()
                .filter(record -> record.transponderId().equals(transponderId))
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public List<StoredSegmentRecord> storedCopiesForSegment(MinecraftServer server, RouteSegmentId segmentId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(segmentId, "segmentId");
        return RouteSegmentDirectorySavedData.storedCopiesForSegment(server, segmentId);
    }

    public List<StoredSegmentRecord> storedCopiesForSegmentForExplicitCleanup(MinecraftServer server, RouteSegmentId segmentId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(segmentId, "segmentId");
        return explicitCleanupStoredRecords(server).stream()
                .filter(record -> record.segmentId().equals(segmentId))
                .sorted(StoredSegmentRecord.sortOrder())
                .toList();
    }

    public boolean queuePendingDeletion(MinecraftServer server, UUID holderStationId, RouteSegmentId segmentId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        Objects.requireNonNull(segmentId, "segmentId");
        return RouteSegmentDirectorySavedData.queuePendingDeletion(server, holderStationId, segmentId);
    }

    public List<RouteSegmentId> pendingDeletionsForHolder(MinecraftServer server, UUID holderStationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        return RouteSegmentDirectorySavedData.pendingDeletionsForHolder(server, holderStationId);
    }

    public boolean clearPendingDeletion(MinecraftServer server, UUID holderStationId, RouteSegmentId segmentId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        Objects.requireNonNull(segmentId, "segmentId");
        return RouteSegmentDirectorySavedData.clearPendingDeletion(server, holderStationId, segmentId);
    }

    public void clearPendingDeletionsForHolder(MinecraftServer server, UUID holderStationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        RouteSegmentDirectorySavedData.clearPendingDeletionsForHolder(server, holderStationId);
    }

    public void removeStoredSegmentRecord(MinecraftServer server, UUID holderStationId, RouteSegmentId segmentId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        Objects.requireNonNull(segmentId, "segmentId");
        RouteSegmentDirectorySavedData.removeStoredSegment(server, holderStationId, segmentId);
    }

    public void removeStoredSegmentsForHolder(MinecraftServer server, UUID holderStationId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(holderStationId, "holderStationId");
        RouteSegmentDirectorySavedData.removeStoredSegmentsForHolder(server, holderStationId);
    }

    public int applyPendingDeletions(ServerLevel level, AirshipStationBlockEntity station) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(station, "station");
        List<RouteSegmentId> pending = pendingDeletionsForHolder(level.getServer(), station.stationId());
        if (pending.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (RouteSegmentId segmentId : pending) {
            boolean removed = station.removeRouteSegment(segmentId.value());
            if (removed) {
                applied++;
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Applied deferred route deletion station={} segment={} on load/register",
                        station.stationId(),
                        segmentId.value()
                );
            } else {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Deferred route deletion found no stored segment station={} segment={}; clearing pending entry",
                        station.stationId(),
                        segmentId.value()
                );
                removeStoredSegmentRecord(level.getServer(), station.stationId(), segmentId);
            }
            clearPendingDeletion(level.getServer(), station.stationId(), segmentId);
        }
        return applied;
    }

    private List<StoredSegmentRecord> explicitCleanupStoredRecords(MinecraftServer server) {
        Map<String, StoredSegmentRecord> merged = new LinkedHashMap<>();
        mergeStoredRecords(merged, RouteSegmentDirectorySavedData.allStoredSegments(server));
        mergeStoredRecords(merged, scanAndBackfillStoredSegments(server));
        return new ArrayList<>(merged.values());
    }

    private List<RouteSegment> allSegments(MinecraftServer server, boolean loadStations) {
        IdentityDirectorySavedData identities = IdentityDirectorySavedData.get(server);
        Map<RouteSegmentId, RouteSegment> segments = new LinkedHashMap<>();
        for (IdentityDirectorySavedData.PersistedStationIdentity stationIdentity : identities.allStations()) {
            ServerLevel level = server.getLevel(stationIdentity.dimension());
            if (level == null) {
                continue;
            }
            if (loadStations) {
                level.getChunkAt(stationIdentity.stationPos());
            }
            if (!(level.getBlockEntity(stationIdentity.stationPos()) instanceof AirshipStationBlockEntity station)) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Route repository skipped station {} at {} in {} while scanning persistent routes loadStations={}",
                        stationIdentity.stationId(),
                        stationIdentity.stationPos(),
                        stationIdentity.dimension().location(),
                        loadStations
                );
                continue;
            }
            for (RouteSegment segment : station.routeSegments()) {
                segments.put(segment.id(), segment);
            }
        }
        return new ArrayList<>(segments.values());
    }

    private List<StoredSegmentRecord> scanAndBackfillStoredSegments(MinecraftServer server) {
        List<StoredSegmentRecord> scanned = new ArrayList<>();
        for (IdentityDirectorySavedData.PersistedStationIdentity stationIdentity : IdentityDirectorySavedData.get(server).allStations()) {
            ServerLevel level = server.getLevel(stationIdentity.dimension());
            if (level == null) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Explicit cleanup directory backfill skipped station={} pos={} dimension={} reason=level_unavailable",
                        stationIdentity.stationId(),
                        stationIdentity.stationPos(),
                        stationIdentity.dimension().location()
                );
                continue;
            }
            level.getChunkAt(stationIdentity.stationPos());
            if (!(level.getBlockEntity(stationIdentity.stationPos()) instanceof AirshipStationBlockEntity station)) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Explicit cleanup directory backfill skipped station={} pos={} dimension={} reason=holder_station_unavailable",
                        stationIdentity.stationId(),
                        stationIdentity.stationPos(),
                        stationIdentity.dimension().location()
                );
                continue;
            }
            List<RouteSegment> stationSegments = station.routeSegments();
            syncStoredSegments(server, station.stationId(), stationSegments);
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Explicit cleanup directory backfill station={} pos={} dimension={} storedSegments={}",
                    station.stationId(),
                    station.getBlockPos().toShortString(),
                    level.dimension().location(),
                    stationSegments.size()
            );
            for (RouteSegment segment : stationSegments) {
                scanned.add(new StoredSegmentRecord(
                        station.stationId(),
                        segment.id(),
                        segment.startStationId(),
                        segment.endStationId(),
                        segment.transponderId(),
                        segment.dimension()
                ));
            }
        }
        return scanned;
    }

    private void mergeStoredRecords(Map<String, StoredSegmentRecord> records, List<StoredSegmentRecord> additions) {
        for (StoredSegmentRecord record : additions) {
            records.put(storedRecordKey(record), record);
        }
    }

    private String storedRecordKey(StoredSegmentRecord record) {
        return record.holderStationId() + "|" + record.segmentId().value();
    }

    private static Comparator<RouteSegment> newestFirst() {
        return Comparator
                .comparingLong(RouteSegment::createdEpochMillis)
                .reversed()
                .thenComparing(segment -> segment.id().value().toString());
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteSegmentDirectorySavedData.StoredSegmentRecord;

public final class ScheduleRouteCleanup {
    private ScheduleRouteCleanup() {
    }

    public static void deleteStationRoutes(ServerLevel level, UUID stationId) {
        List<StoredSegmentRecord> affected = AutomatedLogisticsServices.ROUTES.storedSegmentsConnectedToStationForExplicitCleanup(level.getServer(), stationId);
        logExplicitCleanupRequest("station_delete", "stationId=" + stationId, affected);
        if (affected.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Explicit cleanup refused source=station_delete stationId={} reason=no_persistent_route_proof",
                    stationId
            );
            removeLoadedScheduleEntriesForDeletedStation(level, stationId, affected);
            return;
        }
        for (StoredSegmentRecord record : affected) {
            applyImmediateOrDeferredDeletion(level, record, "station_delete", "stationId=" + stationId);
        }
        AutomatedLogisticsServices.ROUTES.removeStoredSegmentsForHolder(level.getServer(), stationId);
        AutomatedLogisticsServices.ROUTES.clearPendingDeletionsForHolder(level.getServer(), stationId);
        removeLoadedScheduleEntriesForDeletedStation(level, stationId, affected);
    }

    public static void deleteTransponderRoutes(ServerLevel level, UUID transponderId) {
        List<StoredSegmentRecord> affected = AutomatedLogisticsServices.ROUTES.storedSegmentsForTransponderForExplicitCleanup(level.getServer(), transponderId);
        logExplicitCleanupRequest("transponder_delete", "transponderId=" + transponderId, affected);
        for (var snapshot : AirshipStationRegistry.knownStations(level.dimension())) {
            if (!(level.getBlockEntity(snapshot.stationPos()) instanceof AirshipStationBlockEntity station)) {
                continue;
            }
            if (station.selectedTransponderId().filter(transponderId::equals).isPresent()) {
                station.clearSelectedShip();
            }
        }
        if (affected.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Explicit cleanup refused source=transponder_delete transponderId={} reason=no_persistent_route_proof",
                    transponderId
            );
            return;
        }
        for (StoredSegmentRecord record : affected) {
            applyImmediateOrDeferredDeletion(level, record, "transponder_delete", "transponderId=" + transponderId);
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Schedule cleanup scoped source=transponder_delete transponder={} reason=deleted_transponder_owns_schedule no_unrelated_repair=true",
                transponderId
        );
    }

    public static void deleteStopAssociatedSegments(ServerLevel level, List<RouteSegment> segments) {
        if (segments.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Explicit cleanup refused source=stop_delete reason=no_segments_resolved"
            );
            return;
        }
        List<StoredSegmentRecord> affected = new ArrayList<>();
        for (RouteSegment segment : segments) {
            affected.addAll(AutomatedLogisticsServices.ROUTES.storedCopiesForSegmentForExplicitCleanup(level.getServer(), segment.id()));
        }
        logExplicitCleanupRequest(
                "stop_delete",
                "segmentIds=" + segments.stream().map(segment -> segment.id().value()).toList(),
                affected
        );
        if (affected.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Explicit cleanup refused source=stop_delete segmentIds={} reason=no_persistent_route_proof",
                    segments.stream().map(segment -> segment.id().value()).toList()
            );
            return;
        }
        for (StoredSegmentRecord record : affected) {
            applyImmediateOrDeferredDeletion(level, record, "stop_delete", "segmentId=" + record.segmentId().value());
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Schedule cleanup scoped source=stop_delete affectedSegments={} reason=menu_stop_delete_already_removed_selected_entry no_unrelated_repair=true",
                affected.stream().map(record -> record.segmentId().value()).distinct().toList()
        );
    }

    private static void removeLoadedScheduleEntriesForDeletedStation(
            ServerLevel level,
            UUID deletedStationId,
            List<StoredSegmentRecord> affected
    ) {
        var server = level.getServer();
        Map<UUID, Set<RouteSegmentId>> affectedSegmentsByTransponder = new HashMap<>();
        Set<UUID> affectedTransponders = new HashSet<>();
        for (StoredSegmentRecord record : affected) {
            affectedTransponders.add(record.transponderId());
            affectedSegmentsByTransponder
                    .computeIfAbsent(record.transponderId(), ignored -> new HashSet<>())
                    .add(record.segmentId());
        }

        for (IdentityDirectorySavedData.PersistedShipIdentity shipIdentity : IdentityDirectorySavedData.get(server).allShips()) {
            ServerLevel shipLevel = server.getLevel(shipIdentity.dimension());
            if (shipLevel == null) {
                if (affectedTransponders.contains(shipIdentity.transponderId())) {
                    CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                            "Schedule cleanup refused source=station_delete transponder={} station={} reason=transponder_level_unavailable applied=false",
                            shipIdentity.transponderId(),
                            deletedStationId
                    );
                }
                continue;
            }
            shipLevel.getChunkAt(shipIdentity.transponderPos());
            if (!(shipLevel.getBlockEntity(shipIdentity.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
                if (affectedTransponders.contains(shipIdentity.transponderId())) {
                    CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                            "Schedule cleanup refused source=station_delete transponder={} station={} reason=transponder_block_entity_unavailable applied=false",
                            shipIdentity.transponderId(),
                            deletedStationId
                    );
                }
                continue;
            }
            AirshipSchedule schedule = transponder.ownedSchedule();
            if (schedule.entries().isEmpty()) {
                continue;
            }
            UUID transponderId = shipIdentity.transponderId();
            Set<RouteSegmentId> affectedSegments = affectedSegmentsByTransponder.getOrDefault(transponderId, Set.of());
            List<AirshipScheduleEntry> kept = new ArrayList<>();
            boolean changed = false;
            for (int i = 0; i < schedule.entries().size(); i++) {
                AirshipScheduleEntry entry = schedule.entries().get(i);
                boolean stationMatched = entry.targetStationId().filter(deletedStationId::equals).isPresent();
                boolean segmentMatched = entry.pinnedSegmentId().filter(affectedSegments::contains).isPresent();
                if (stationMatched || segmentMatched) {
                    changed = true;
                    logScheduleMutation(
                            "station_delete",
                            "applied",
                            transponderId,
                            i,
                            entry.targetStationId().orElse(deletedStationId),
                            entry.pinnedSegmentId(),
                            stationMatched ? "target_station_id_matches_deleted_station" : "pinned_segment_matches_persistent_route_directory"
                    );
                    continue;
                }
                kept.add(entry);
            }
            if (changed) {
                transponder.setOwnedSchedule(schedule.withEntries(kept));
            } else if (affectedTransponders.contains(transponderId)) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Schedule cleanup refused source=station_delete transponder={} station={} segment={} proof=affected_route_directory reason=no_matching_schedule_entry applied=false",
                        transponderId,
                        deletedStationId,
                        affectedSegments.stream().map(segmentId -> segmentId.value().toString()).toList()
                );
            }
        }
    }

    private static void logScheduleMutation(
            String source,
            String result,
            UUID transponderId,
            int entryIndex,
            UUID stationId,
            Optional<RouteSegmentId> segmentId,
            String proof
    ) {
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Schedule cleanup {} source={} transponder={} entryIndex={} station={} segment={} proof={} applied={}",
                result,
                source,
                transponderId,
                entryIndex,
                stationId,
                segmentId.map(id -> id.value().toString()).orElse("none"),
                proof,
                "applied".equals(result)
        );
    }

    private static void applyImmediateOrDeferredDeletion(
            ServerLevel level,
            StoredSegmentRecord record,
            String source,
            String subject
    ) {
        Optional<AirshipStationBlockEntity> holderStation = loadedHolderStation(level, record.holderStationId());
        boolean holderLoaded = holderStation.isPresent();
        if (holderLoaded) {
            boolean removed = holderStation.get().removeRouteSegment(record.segmentId().value());
            AutomatedLogisticsServices.ROUTES.clearPendingDeletion(level.getServer(), record.holderStationId(), record.segmentId());
            if (removed) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Explicit cleanup applied source={} {} segment={} holderStation={} ownerStation={} targetStation={} holderLoaded=true deferred=false",
                        source,
                        subject,
                        record.segmentId().value(),
                        record.holderStationId(),
                        record.startStationId(),
                        record.endStationId()
                );
            } else {
                AutomatedLogisticsServices.ROUTES.removeStoredSegmentRecord(level.getServer(), record.holderStationId(), record.segmentId());
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Explicit cleanup found missing stored segment source={} {} segment={} holderStation={} ownerStation={} targetStation={} holderLoaded=true deferred=false",
                        source,
                        subject,
                        record.segmentId().value(),
                        record.holderStationId(),
                        record.startStationId(),
                        record.endStationId()
                );
            }
            AutomatedLogisticsServices.ROUTES.evictIndexedSegment(record.segmentId());
            return;
        }
        boolean deferred = AutomatedLogisticsServices.ROUTES.queuePendingDeletion(level.getServer(), record.holderStationId(), record.segmentId());
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Explicit cleanup deferred source={} {} segment={} holderStation={} ownerStation={} targetStation={} holderLoaded=false deferred={}",
                source,
                subject,
                record.segmentId().value(),
                record.holderStationId(),
                record.startStationId(),
                record.endStationId(),
                deferred
        );
        AutomatedLogisticsServices.ROUTES.evictIndexedSegment(record.segmentId());
    }

    private static Optional<AirshipStationBlockEntity> loadedHolderStation(ServerLevel level, UUID holderStationId) {
        return IdentityDirectorySavedData.get(level.getServer()).station(holderStationId)
                .filter(identity -> identity.dimension().equals(level.dimension()))
                .map(identity -> level.getBlockEntity(identity.stationPos()))
                .filter(AirshipStationBlockEntity.class::isInstance)
                .map(AirshipStationBlockEntity.class::cast);
    }

    private static void logExplicitCleanupRequest(String source, String subject, List<StoredSegmentRecord> affected) {
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Explicit cleanup request source={} {} affectedEntries={} affectedSegments={}",
                source,
                subject,
                affected.size(),
                affected.stream().map(record -> record.segmentId().value()).distinct().toList()
        );
    }

}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteSegmentDirectorySavedData.StoredSegmentRecord;

public final class ScheduleRouteCleanup {
    private ScheduleRouteCleanup() {
    }

    public static int pruneOwnedSchedule(ServerLevel level, ShipTransponderBlockEntity transponder) {
        AirshipSchedule current = transponder.ownedSchedule();
        AirshipSchedule cleaned = pruneInvalidEntries(level, transponder.transponderId(), current);
        int removed = current.entries().size() - cleaned.entries().size();
        if (removed > 0) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Pruned {} stale schedule entr(y/ies) from transponder {}",
                    removed,
                    transponder.transponderId()
            );
            transponder.setOwnedSchedule(cleaned);
        }
        return removed;
    }

    public static AirshipSchedule pruneInvalidEntries(ServerLevel level, UUID transponderId, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        boolean changed = true;
        while (changed && !entries.isEmpty()) {
            changed = false;
            Optional<UUID> currentStationId = deriveStartStationId(level, transponderId, entries);
            if (currentStationId.isEmpty()) {
                entries.remove(0);
                changed = true;
                continue;
            }
            UUID current = currentStationId.get();
            for (int i = 0; i < entries.size(); i++) {
                AirshipScheduleEntry entry = entries.get(i);
                if (entry.targetStationId().isEmpty()
                        || stationMissing(level, entry.targetStationId().get())) {
                    entries.remove(i);
                    changed = true;
                    break;
                }
                UUID target = entry.targetStationId().get();
                Optional<RouteSegment> resolved = resolveSegment(level, current, target, entry.pinnedSegmentId(), transponderId);
                if (resolved.isEmpty()) {
                    entries.remove(i);
                    changed = true;
                    break;
                }
                current = target;
            }
        }
        return schedule.withEntries(entries);
    }

    public static void removeRoutesForDeletedStation(ServerLevel level, UUID stationId) {
        List<StoredSegmentRecord> affected = AutomatedLogisticsServices.ROUTES.storedSegmentsConnectedToStation(level.getServer(), stationId);
        logExplicitCleanupRequest("station_delete", "stationId=" + stationId, affected);
        if (affected.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Explicit cleanup refused source=station_delete stationId={} reason=no_persistent_route_proof",
                    stationId
            );
            return;
        }
        for (StoredSegmentRecord record : affected) {
            applyImmediateOrDeferredDeletion(level, record, "station_delete", "stationId=" + stationId);
        }
        AutomatedLogisticsServices.ROUTES.removeStoredSegmentsForHolder(level.getServer(), stationId);
        AutomatedLogisticsServices.ROUTES.clearPendingDeletionsForHolder(level.getServer(), stationId);
        pruneLoadedTransponderSchedules(level);
    }

    public static void removeRoutesForDeletedTransponder(ServerLevel level, UUID transponderId) {
        List<StoredSegmentRecord> affected = AutomatedLogisticsServices.ROUTES.storedSegmentsForTransponder(level.getServer(), transponderId);
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
        pruneLoadedTransponderSchedules(level);
    }

    public static void removeRoutesForDeletedStop(ServerLevel level, List<RouteSegment> segments) {
        if (segments.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Explicit cleanup refused source=stop_delete reason=no_segments_resolved"
            );
            return;
        }
        List<StoredSegmentRecord> affected = new ArrayList<>();
        for (RouteSegment segment : segments) {
            affected.addAll(AutomatedLogisticsServices.ROUTES.storedCopiesForSegment(level.getServer(), segment.id()));
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
        pruneLoadedTransponderSchedules(level);
    }

    public static void pruneLoadedSchedulesForChangedRoutes(ServerLevel level) {
        pruneLoadedTransponderSchedules(level);
    }

    public static int pruneInvalidRouteSegments(ServerLevel level, AirshipStationBlockEntity station) {
        List<UUID> invalidSegmentIds = station.routeSegments().stream()
                .filter(segment -> !segment.dimension().equals(level.dimension())
                        || stationMissing(level, segment.startStationId())
                        || stationMissing(level, segment.endStationId())
                        || transponderMissing(level, segment.transponderId()))
                .map(segment -> segment.id().value())
                .toList();
        if (!invalidSegmentIds.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Pruning {} invalid route segment(s) from station {} at {} ids={}",
                    invalidSegmentIds.size(),
                    station.stationId(),
                    station.getBlockPos(),
                    invalidSegmentIds
            );
        }
        invalidSegmentIds.forEach(station::removeRouteSegment);
        return invalidSegmentIds.size();
    }

    private static void pruneLoadedTransponderSchedules(ServerLevel level) {
        for (var snapshot : ShipTransponderRegistry.allShips()) {
            if (!snapshot.dimension().equals(level.dimension())) {
                continue;
            }
            if (level.getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
                pruneOwnedSchedule(level, transponder);
            }
        }
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

    private static Optional<UUID> deriveStartStationId(
            ServerLevel level,
            UUID transponderId,
            List<AirshipScheduleEntry> entries
    ) {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        AirshipScheduleEntry firstEntry = entries.get(0);
        if (firstEntry.targetStationId().isEmpty()) {
            return Optional.empty();
        }
        UUID firstTarget = firstEntry.targetStationId().get();
        Optional<UUID> pinnedStart = firstEntry.pinnedSegmentId()
                .flatMap(segmentId -> AutomatedLogisticsServices.ROUTES.byId(level.getServer(), segmentId))
                .filter(segment -> segment.dimension().equals(level.dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .filter(segment -> segment.endStationId().equals(firstTarget))
                .filter(segment -> !stationMissing(level, segment.startStationId()))
                .map(RouteSegment::startStationId);
        if (pinnedStart.isPresent()) {
            return pinnedStart;
        }
        return AutomatedLogisticsServices.ROUTES.endingAt(
                        level.getServer(),
                        firstTarget,
                        level.dimension(),
                        Optional.of(transponderId)
                ).stream()
                .filter(segment -> !stationMissing(level, segment.startStationId()))
                .map(RouteSegment::startStationId)
                .findFirst();
    }

    private static Optional<RouteSegment> resolveSegment(
            ServerLevel level,
            UUID startStationId,
            UUID targetStationId,
            Optional<RouteSegmentId> pinnedSegmentId,
            UUID transponderId
    ) {
        return pinnedSegmentId
                .flatMap(segmentId -> AutomatedLogisticsServices.ROUTES.byId(level.getServer(), segmentId))
                .filter(candidate -> candidate.startStationId().equals(startStationId))
                .filter(candidate -> candidate.endStationId().equals(targetStationId))
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(transponderId))
                .filter(candidate -> !stationMissing(level, candidate.startStationId()))
                .filter(candidate -> !stationMissing(level, candidate.endStationId()))
                .or(() -> RouteSegmentResolver.newestFor(
                        level.getServer(),
                        startStationId,
                        targetStationId,
                        level.dimension(),
                        Optional.of(transponderId)
                ).filter(candidate -> !stationMissing(level, candidate.startStationId()))
                        .filter(candidate -> !stationMissing(level, candidate.endStationId())));
    }

    private static boolean stationMissing(ServerLevel level, UUID stationId) {
        return IdentityDirectorySavedData.get(level.getServer()).station(stationId).isEmpty();
    }

    private static boolean transponderMissing(ServerLevel level, UUID transponderId) {
        return IdentityDirectorySavedData.get(level.getServer()).ship(transponderId).isEmpty();
    }

}

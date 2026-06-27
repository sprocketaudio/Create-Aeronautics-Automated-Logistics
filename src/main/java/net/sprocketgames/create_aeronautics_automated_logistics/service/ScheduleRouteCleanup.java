package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;

public final class ScheduleRouteCleanup {
    private ScheduleRouteCleanup() {
    }

    public static boolean removeRoutesForDeletedStation(ServerLevel level, UUID stationId, BlockPos deletedPos) {
        if (!net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData
                .ownsStationPosition(level.getServer(), stationId, level.dimension(), deletedPos)) {
            var knownIdentity = net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData
                    .get(level.getServer())
                    .station(stationId);
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Explicit route cleanup refused source=station_delete station={} deletedPos={} deletedDimension={} canonicalPos={} canonicalDimension={} reason=deleted_block_does_not_own_station_identity",
                    stationId,
                    deletedPos,
                    level.dimension().location(),
                    knownIdentity.map(identity -> identity.stationPos().toShortString()).orElse("missing"),
                    knownIdentity.map(identity -> identity.dimension().location().toString()).orElse("missing")
            );
            return false;
        }
        List<RouteSegment> affected = affectedForDeletedStation(level, stationId);
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Explicit route cleanup request received source=station_delete station={} affectedSegments={}",
                stationId,
                affected.stream().map(segment -> segment.id().value()).toList()
        );
        if (affected.isEmpty()) {
            return true;
        }
        for (RouteSegment segment : affected) {
            RouteSegmentDirectorySavedData.remove(level.getServer(), segment.id());
            removeSegmentFromLoadedStations(level, "station_delete", stationId, segment);
            RouteSegmentRegistry.unregister(segment.id());
        }
        return true;
    }

    public static void removeRoutesForDeletedTransponder(ServerLevel level, UUID transponderId) {
        List<RouteSegment> affected = affectedForDeletedTransponder(level, transponderId);
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Explicit route cleanup request received source=transponder_delete transponder={} affectedSegments={}",
                transponderId,
                affected.stream().map(segment -> segment.id().value()).toList()
        );
        for (var snapshot : AirshipStationRegistry.knownStations(level.dimension())) {
            if (!(level.getBlockEntity(snapshot.stationPos()) instanceof AirshipStationBlockEntity station)) {
                continue;
            }
            if (station.selectedTransponderId().filter(transponderId::equals).isPresent()) {
                station.clearSelectedShip();
            }
        }
        if (affected.isEmpty()) {
            return;
        }
        for (RouteSegment segment : affected) {
            RouteSegmentDirectorySavedData.remove(level.getServer(), segment.id());
            removeSegmentFromLoadedStations(level, "transponder_delete", null, segment);
            RouteSegmentRegistry.unregister(segment.id());
        }
    }

    public static int removeRoutesForDeletedScheduleEntries(
            ServerLevel level,
            UUID transponderId,
            List<AirshipScheduleEntry> removedEntries
    ) {
        Map<RouteSegmentId, RouteSegment> affected = new LinkedHashMap<>();
        for (AirshipScheduleEntry entry : removedEntries) {
            if (entry.pinnedSegmentId().isEmpty()) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Explicit route cleanup refused source=stop_delete transponder={} stop={} reason=missing_pinned_segment_proof action=route_preserved",
                        transponderId,
                        entry.displayStationName()
                );
                continue;
            }
            RouteSegmentId segmentId = entry.pinnedSegmentId().get();
            RouteSegmentDirectorySavedData.byId(level.getServer(), segmentId)
                    .filter(segment -> segment.transponderId().equals(transponderId))
                    .filter(segment -> segment.dimension().equals(level.dimension()))
                    .ifPresentOrElse(
                            segment -> affected.put(segment.id(), segment),
                            () -> CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                                    "Explicit route cleanup refused source=stop_delete transponder={} stop={} segment={} reason=persistent_segment_proof_unavailable action=route_preserved",
                                    transponderId,
                                    entry.displayStationName(),
                                    segmentId.value()
                            )
                    );
        }
        if (affected.isEmpty()) {
            return 0;
        }

        Set<RouteSegmentId> affectedIds = Set.copyOf(affected.keySet());
        AutomatedLogisticsServices.SCHEDULES.stopForExplicitDeletedSegments(level, transponderId, affectedIds);
        for (RouteSegment segment : affected.values()) {
            CreateAeronauticsAutomatedLogistics.LOGGER.info(
                    "Explicit route cleanup applied source=stop_delete transponder={} stopTargetStation={} segment={} startStation={} endStation={} proof=pinned_persistent_segment",
                    transponderId,
                    segment.endStationId(),
                    segment.id().value(),
                    segment.startStationId(),
                    segment.endStationId()
            );
            RouteSegmentDirectorySavedData.remove(level.getServer(), segment.id());
            removeSegmentFromLoadedStations(level, "stop_delete", null, segment);
            RouteSegmentRegistry.unregister(segment.id());
        }
        return affected.size();
    }

    private static List<RouteSegment> affectedForDeletedStation(ServerLevel level, UUID stationId) {
        Map<RouteSegmentId, RouteSegment> affected = new LinkedHashMap<>();
        for (RouteSegment segment : RouteSegmentDirectorySavedData.connectedToStation(level.getServer(), stationId)) {
            affected.put(segment.id(), segment);
        }
        for (RouteSegment segment : RouteSegmentRegistry.connectedToStation(stationId)) {
            affected.putIfAbsent(segment.id(), segment);
        }
        return List.copyOf(affected.values());
    }

    private static List<RouteSegment> affectedForDeletedTransponder(ServerLevel level, UUID transponderId) {
        Map<RouteSegmentId, RouteSegment> affected = new LinkedHashMap<>();
        for (RouteSegment segment : RouteSegmentDirectorySavedData.forTransponder(level.getServer(), transponderId)) {
            affected.put(segment.id(), segment);
        }
        for (RouteSegment segment : RouteSegmentRegistry.forTransponder(transponderId)) {
            affected.putIfAbsent(segment.id(), segment);
        }
        return List.copyOf(affected.values());
    }

    private static void removeSegmentFromLoadedStations(
            ServerLevel level,
            String source,
            UUID deletedStationId,
            RouteSegment segment
    ) {
        removeSegmentFromLoadedStation(level, source, deletedStationId, segment.startStationId(), segment.id());
        removeSegmentFromLoadedStation(level, source, deletedStationId, segment.endStationId(), segment.id());
    }

    private static void removeSegmentFromLoadedStation(
            ServerLevel level,
            String source,
            UUID deletedStationId,
            UUID stationId,
            RouteSegmentId segmentId
    ) {
        if (deletedStationId != null && deletedStationId.equals(stationId)) {
            return;
        }
        boolean removed = AirshipStationRegistry.snapshot(stationId)
                .map(snapshot -> level.getBlockEntity(snapshot.stationPos()))
                .filter(AirshipStationBlockEntity.class::isInstance)
                .map(AirshipStationBlockEntity.class::cast)
                .map(station -> station.removeRouteSegment(segmentId.value()))
                .orElse(false);
        if (removed) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Explicit route cleanup applied source={} owningStation={} segment={} loaded=true",
                    source,
                    stationId,
                    segmentId.value()
            );
            return;
        }

        RouteSegmentDirectorySavedData.addPendingDelete(level.getServer(), stationId, segmentId);
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Explicit route cleanup deferred source={} owningStation={} segment={} loaded=false",
                source,
                stationId,
                segmentId.value()
        );
    }

}

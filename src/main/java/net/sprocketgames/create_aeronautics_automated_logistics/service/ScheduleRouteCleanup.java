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
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;

public final class ScheduleRouteCleanup {
    private ScheduleRouteCleanup() {
    }

    public static int pruneOwnedSchedule(ServerLevel level, ShipTransponderBlockEntity transponder) {
        AirshipSchedule current = transponder.ownedSchedule();
        AirshipSchedule cleaned = pruneInvalidEntries(level, transponder.transponderId(), current);
        int removed = current.entries().size() - cleaned.entries().size();
        if (removed > 0) {
            CreateAeronauticsAutomatedLogistics.debugLog(
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
        List<RouteSegment> affected = RouteSegmentRegistry.connectedToStation(stationId);
        if (affected.isEmpty()) {
            return;
        }
        for (RouteSegment segment : affected) {
            removeSegmentFromLoadedStations(level, stationId, segment);
            RouteSegmentRegistry.unregister(segment.id());
        }
        pruneLoadedTransponderSchedules(level);
    }

    public static void removeRoutesForDeletedTransponder(ServerLevel level, UUID transponderId) {
        List<RouteSegment> affected = RouteSegmentRegistry.forTransponder(transponderId);
        if (affected.isEmpty()) {
            return;
        }
        for (RouteSegment segment : affected) {
            removeSegmentFromLoadedStations(level, null, segment);
            RouteSegmentRegistry.unregister(segment.id());
        }
        pruneLoadedTransponderSchedules(level);
    }

    public static int pruneInvalidRouteSegments(ServerLevel level, AirshipStationBlockEntity station) {
        List<UUID> invalidSegmentIds = station.routeSegments().stream()
                .filter(segment -> !segment.dimension().equals(level.dimension())
                        || shipMissing(level, segment.transponderId())
                        || stationMissing(level, segment.startStationId())
                        || stationMissing(level, segment.endStationId()))
                .map(segment -> segment.id().value())
                .toList();
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

    private static void removeSegmentFromLoadedStations(ServerLevel level, UUID deletedStationId, RouteSegment segment) {
        removeSegmentFromLoadedStation(level, deletedStationId, segment.startStationId(), segment.id());
        removeSegmentFromLoadedStation(level, deletedStationId, segment.endStationId(), segment.id());
    }

    private static void removeSegmentFromLoadedStation(
            ServerLevel level,
            UUID deletedStationId,
            UUID stationId,
            RouteSegmentId segmentId
    ) {
        if (deletedStationId != null && deletedStationId.equals(stationId)) {
            return;
        }
        AirshipStationRegistry.snapshot(stationId)
                .map(snapshot -> level.getBlockEntity(snapshot.stationPos()))
                .filter(AirshipStationBlockEntity.class::isInstance)
                .map(AirshipStationBlockEntity.class::cast)
                .ifPresent(station -> station.removeRouteSegment(segmentId.value()));
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
                .flatMap(RouteSegmentRegistry::byId)
                .filter(segment -> segment.dimension().equals(level.dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .filter(segment -> segment.endStationId().equals(firstTarget))
                .filter(segment -> !stationMissing(level, segment.startStationId()))
                .map(RouteSegment::startStationId);
        if (pinnedStart.isPresent()) {
            return pinnedStart;
        }
        return RouteSegmentRegistry.endingAt(firstTarget, level.dimension(), Optional.of(transponderId)).stream()
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
                .flatMap(RouteSegmentRegistry::byId)
                .filter(candidate -> candidate.startStationId().equals(startStationId))
                .filter(candidate -> candidate.endStationId().equals(targetStationId))
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(transponderId))
                .filter(candidate -> !stationMissing(level, candidate.startStationId()))
                .filter(candidate -> !stationMissing(level, candidate.endStationId()))
                .filter(candidate -> !shipMissing(level, candidate.transponderId()))
                .or(() -> RouteSegmentResolver.newestFor(
                        startStationId,
                        targetStationId,
                        level.dimension(),
                        Optional.of(transponderId)
                ).filter(candidate -> !stationMissing(level, candidate.startStationId()))
                        .filter(candidate -> !stationMissing(level, candidate.endStationId()))
                        .filter(candidate -> !shipMissing(level, candidate.transponderId())));
    }

    private static boolean stationMissing(ServerLevel level, UUID stationId) {
        return IdentityDirectorySavedData.get(level.getServer()).station(stationId).isEmpty();
    }

    private static boolean shipMissing(ServerLevel level, UUID transponderId) {
        return IdentityDirectorySavedData.get(level.getServer()).ship(transponderId).isEmpty();
    }
}

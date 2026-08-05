package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

record ActiveAirshipSchedule(
        UUID transponderId,
        UUID executionId,
        BlockPos transponderPos,
        ResourceKey<Level> dimension,
        AirshipSchedule schedule,
        UUID startStationId,
        String startStationName,
        BlockPos startStationPos,
        UUID currentStationId,
        String currentStationName,
        BlockPos currentStationPos,
        int entryIndex,
        Optional<RouteId> activeRouteId
) {
    AirshipScheduleEntry currentEntry() {
        return schedule.entries().get(entryIndex);
    }

    ActiveAirshipSchedule withActiveRoute(RouteId routeId) {
        return new ActiveAirshipSchedule(
                transponderId,
                executionId,
                transponderPos,
                dimension,
                schedule,
                startStationId,
                startStationName,
                startStationPos,
                currentStationId,
                currentStationName,
                currentStationPos,
                entryIndex,
                Optional.of(routeId)
        );
    }

    ActiveAirshipSchedule advance() {
        AirshipScheduleEntry completed = currentEntry();
        UUID nextStationId = completed.targetStationId().orElse(currentStationId);
        String nextStationName = completed.displayStationName();
        BlockPos nextStationPos = completed.targetStationId()
                .flatMap(net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry::snapshot)
                .map(snapshot -> snapshot.stationPos().immutable())
                .orElse(currentStationPos);
        return new ActiveAirshipSchedule(
                transponderId,
                executionId,
                transponderPos,
                dimension,
                schedule,
                startStationId,
                startStationName,
                startStationPos,
                nextStationId,
                nextStationName,
                nextStationPos,
                entryIndex + 1,
                Optional.empty()
        );
    }

    ActiveAirshipSchedule restart() {
        return new ActiveAirshipSchedule(
                transponderId,
                executionId,
                transponderPos,
                dimension,
                schedule,
                startStationId,
                startStationName,
                startStationPos,
                startStationId,
                startStationName,
                startStationPos,
                0,
                Optional.empty()
        );
    }

    ActiveAirshipSchedule resetProgress() {
        return new ActiveAirshipSchedule(
                transponderId,
                executionId,
                transponderPos,
                dimension,
                schedule,
                startStationId,
                startStationName,
                startStationPos,
                currentStationId,
                currentStationName,
                currentStationPos,
                0,
                activeRouteId
        );
    }

    boolean isFinished() {
        return entryIndex >= schedule.entries().size();
    }
}

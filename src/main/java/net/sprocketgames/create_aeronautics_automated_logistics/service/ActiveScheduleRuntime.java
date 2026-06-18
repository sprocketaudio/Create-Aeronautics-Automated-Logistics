package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

record ActiveScheduleRuntime(
        UUID transponderId,
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
        Optional<RouteId> activeRouteId,
        RuntimeState state
) {
    AirshipScheduleEntry currentEntry() {
        return schedule.entries().get(entryIndex);
    }

    Optional<AirshipScheduleEntry> currentEntryOptional() {
        return hasValidEntryIndex() ? Optional.of(schedule.entries().get(entryIndex)) : Optional.empty();
    }

    boolean hasValidEntryIndex() {
        return entryIndex >= 0 && entryIndex < schedule.entries().size();
    }

    ActiveScheduleRuntime withActiveRoute(RouteId routeId) {
        return new ActiveScheduleRuntime(
                transponderId,
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
                Optional.of(routeId),
                state
        );
    }

    ActiveScheduleRuntime withoutActiveRoute() {
        return new ActiveScheduleRuntime(
                transponderId,
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
                Optional.empty(),
                state
        );
    }

    ActiveScheduleRuntime withState(RuntimeState runtimeState) {
        return new ActiveScheduleRuntime(
                transponderId,
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
                activeRouteId,
                runtimeState
        );
    }

    ActiveScheduleRuntime advance() {
        AirshipScheduleEntry completed = currentEntry();
        UUID nextStationId = completed.targetStationId().orElse(currentStationId);
        String nextStationName = completed.displayStationName();
        BlockPos nextStationPos = completed.targetStationId()
                .flatMap(net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry::snapshot)
                .map(snapshot -> snapshot.stationPos().immutable())
                .orElse(currentStationPos);
        return new ActiveScheduleRuntime(
                transponderId,
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
                Optional.empty(),
                RuntimeState.STARTING
        );
    }

    ActiveScheduleRuntime restart() {
        return new ActiveScheduleRuntime(
                transponderId,
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
                Optional.empty(),
                RuntimeState.STARTING
        );
    }

    ActiveScheduleRuntime resetProgress() {
        return new ActiveScheduleRuntime(
                transponderId,
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
                activeRouteId,
                state
        );
    }

    boolean isFinished() {
        return entryIndex >= schedule.entries().size();
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public final class RuntimeRestoreCoordinator {
    private RuntimeRestoreCoordinator() {
    }

    public static void restore(MinecraftServer server, CompoundTag scheduleTag, CompoundTag playbackTag) {
        logPhase("raw runtime snapshot loaded", "begin");
        AutomatedLogisticsServices.SCHEDULES.loadRuntime(server, scheduleTag);
        AutomatedLogisticsServices.PLAYBACK.loadRuntimeForRestoreCoordinator(server, playbackTag);

        logPhase("persistent route model loaded", "available");
        int indexedRoutes = AutomatedLogisticsServices.ROUTES.rebuildLoadedRouteIndex(server);
        logPhase("route indexes rebuilt", "indexedRoutes=" + indexedRoutes);

        Set<RouteId> activeScheduleRouteIds = AutomatedLogisticsServices.SCHEDULES.rebindRestoredSchedules(server);
        logPhase("runtime schedules rebound to persistent schedules", "scheduledRoutes=" + activeScheduleRouteIds.size());

        AutomatedLogisticsServices.PLAYBACK.holdUnscheduledPlaybacks(
                server,
                activeScheduleRouteIds,
                PlaybackFailure.INVALID_ROUTE
        );
        AutomatedLogisticsServices.PLAYBACK.logPendingPlaybackRebinds(activeScheduleRouteIds);
        logPhase(
                "playback records rebound to runtime schedules",
                "playbackRecords=" + AutomatedLogisticsServices.PLAYBACK.routeIdsWithRuntimeRecords().size()
        );

        AutomatedLogisticsServices.PLAYBACK.restorePendingRuntime(server);
        logPhase("materialization requests submitted", "pendingRestoreTickComplete");

        AutomatedLogisticsServices.SCHEDULES.rebindRestoredSchedules(server);
        logPhase("resume or pause fault", "complete");
    }

    private static void logPhase(String phase, String detail) {
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Runtime restore coordinator phase='{}' detail={}",
                phase,
                detail
        );
    }
}

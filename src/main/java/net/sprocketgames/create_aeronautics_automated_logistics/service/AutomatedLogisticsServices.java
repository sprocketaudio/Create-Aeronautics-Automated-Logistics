package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.server.MinecraftServer;

public final class AutomatedLogisticsServices {
    public static final VehicleRouteRecordingService RECORDING = new VehicleRouteRecordingService();
    public static final VehicleRoutePlaybackService PLAYBACK = new VehicleRoutePlaybackService();
    public static final AirshipScheduleExecutionService SCHEDULES = new AirshipScheduleExecutionService();
    public static final ShipMaterializationService MATERIALIZATION = new ShipMaterializationService();
    private static MinecraftServer loadedRuntimeServer;

    private AutomatedLogisticsServices() {
    }

    public static void ensureRuntimeLoaded(MinecraftServer server) {
        if (loadedRuntimeServer == server) {
            return;
        }
        clearRuntime();
        AutomationRuntimeSavedData.get(server).apply(server);
        loadedRuntimeServer = server;
    }

    public static void clearRuntime() {
        RECORDING.resetRuntime();
        PLAYBACK.resetRuntime();
        SCHEDULES.resetRuntime();
        loadedRuntimeServer = null;
    }
}

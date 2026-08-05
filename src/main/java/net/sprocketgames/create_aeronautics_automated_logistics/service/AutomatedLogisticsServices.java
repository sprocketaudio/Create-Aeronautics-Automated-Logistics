package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.server.MinecraftServer;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockEndpointResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockHandshakeService;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockReservationService;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingCoordinator;

public final class AutomatedLogisticsServices {
    public static final DockEndpointResolver DOCK_ENDPOINTS = new DockEndpointResolver();
    public static final DockReservationService DOCK_RESERVATIONS = new DockReservationService();
    public static final DockHandshakeService DOCK_HANDSHAKE =
            new DockHandshakeService(DOCK_ENDPOINTS, DOCK_RESERVATIONS);
    public static final DockingCoordinator DOCKING =
            new DockingCoordinator(DOCK_ENDPOINTS, DOCK_RESERVATIONS, DOCK_HANDSHAKE);
    public static final StationWaitRuntime STATION_WAITS = new StationWaitRuntime();
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

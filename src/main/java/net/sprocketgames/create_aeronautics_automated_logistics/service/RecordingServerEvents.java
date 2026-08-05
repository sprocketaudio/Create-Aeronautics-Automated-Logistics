package net.sprocketgames.create_aeronautics_automated_logistics.service;

import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapProjectionService;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalPreviewMarkersPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalPreviewRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncShipMapMarkersPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncVisibleLogisticsTerminalPreviewsPayload;

public final class RecordingServerEvents {
    private RecordingServerEvents() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        AutomatedLogisticsServices.ensureRuntimeLoaded(event.getServer());
        AutomatedLogisticsServices.RECORDING.tickAll(event.getServer());
        AutomatedLogisticsServices.SCHEDULES.tickAll(event.getServer());
        AutomatedLogisticsServices.PLAYBACK.tickAll(event.getServer());
        if (event.getServer().getTickCount() % 10 == 0) {
            event.getServer().getPlayerList().getPlayers().forEach(player -> {
                PacketDistributor.sendToPlayer(
                        player,
                        new SyncShipMapMarkersPayload(ShipMapProjectionService.snapshotsFor(player))
                );
                SyncLogisticsTerminalRoutesPayload.sendTo(player);
                SyncLogisticsTerminalPreviewMarkersPayload.sendTo(player);
                SyncLogisticsTerminalPreviewRoutesPayload.sendTo(player);
                SyncVisibleLogisticsTerminalPreviewsPayload.sendTo(player);
            });
        }
        if (event.getServer().getTickCount() % 20 == 0) {
            AutomationRuntimeSavedData.capture(event.getServer());
        }
    }

    public static void onSablePrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        AutomatedLogisticsServices.ensureRuntimeLoaded(event.getPhysicsSystem().getLevel().getServer());
        AutomatedLogisticsServices.PLAYBACK.holdPausedVehicles(event.getPhysicsSystem().getLevel());
    }

    public static void onSablePostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        AutomatedLogisticsServices.ensureRuntimeLoaded(event.getPhysicsSystem().getLevel().getServer());
        AutomatedLogisticsServices.PLAYBACK.holdPausedVehicles(event.getPhysicsSystem().getLevel());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        AutomatedLogisticsServices.ensureRuntimeLoaded(event.getServer());
        AutomationRuntimeSavedData.captureForShutdown(event.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        AutomatedLogisticsServices.clearRuntime();
    }
}

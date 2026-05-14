package net.sprocketgames.create_aeronautics_automated_logistics.service;

import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RecordingServerEvents {
    private RecordingServerEvents() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        AutomatedLogisticsServices.ensureRuntimeLoaded(event.getServer());
        AutomatedLogisticsServices.RECORDING.tickAll(event.getServer());
        AutomatedLogisticsServices.PLAYBACK.tickAll(event.getServer());
        AutomatedLogisticsServices.SCHEDULES.tickAll(event.getServer());
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

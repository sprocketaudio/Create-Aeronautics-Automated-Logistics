package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;

/**
 * Shared recording-mode state used whenever a Ship Transponder menu is opened
 * or reopened. Keeping this outside the menu makes the standard workflow
 * reusable by future controller variants without changing its behavior.
 */
public record ShipTransponderRecordingState(
        boolean recordingMode,
        boolean recordingSessionActive,
        boolean appendToSchedule
) {
    public static ShipTransponderRecordingState resolve(
            ServerPlayer player,
            ShipTransponderBlockEntity transponder,
            boolean preferredMode
    ) {
        boolean sessionActive = isActiveFor(player, transponder);
        return new ShipTransponderRecordingState(
                preferredMode || sessionActive,
                sessionActive,
                transponder.appendToSchedule()
        );
    }

    private static boolean isActiveFor(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        Optional<RecordingSession> session = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (session.isEmpty()) {
            return false;
        }
        RecordingSession active = session.get();
        Optional<UUID> activeVehicleId = active.controllerRef().vehicleId();
        Optional<UUID> runtimeVehicleId = transponder.runtimeShipId();
        if (activeVehicleId.isPresent() && runtimeVehicleId.isPresent() && activeVehicleId.get().equals(runtimeVehicleId.get())) {
            return true;
        }
        if (player.serverLevel().getBlockEntity(active.stationPos()) instanceof AirshipStationBlockEntity station) {
            return station.selectedTransponderId().filter(transponder.transponderId()::equals).isPresent();
        }
        return false;
    }
}

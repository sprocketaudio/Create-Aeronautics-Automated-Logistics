package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;

public final class RuntimeProjectionService {
    private RuntimeProjectionService() {
    }

    public static ShipTransponderMenu createTransponderMenu(
            int containerId,
            Inventory playerInventory,
            ServerPlayer player,
            ShipTransponderBlockEntity transponder,
            boolean requestedRecordingMode
    ) {
        TransponderProjection projection = projectTransponderMenu(player, transponder, requestedRecordingMode);
        return new ShipTransponderMenu(
                containerId,
                playerInventory,
                transponder.getBlockPos(),
                projection.recordingState().recordingMode(),
                projection.recordingState().recordingSessionActive(),
                projection.recordingState().appendToSchedule(),
                transponder.recordingDestinationStationId(),
                projection.projectedRuntimeStatus(),
                transponder.dockOutputActive(),
                transponder.hasOwnedStops(),
                transponder.ownedSchedule(),
                transponder.linkedCargoRevision(),
                transponder.linkedCargoSummary(),
                transponder.linkedCargo(),
                AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId()),
                AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId()),
                projection.statusSnapshot()
        );
    }

    public static ShipTransponderMenu.StatusSnapshot buildTransponderStatusSnapshot(
            ServerPlayer player,
            ShipTransponderBlockEntity transponder,
            boolean requestedRecordingMode
    ) {
        return projectTransponderMenu(player, transponder, requestedRecordingMode).statusSnapshot();
    }

    public static void writeTransponderOpenData(
            FriendlyByteBuf buffer,
            ServerPlayer player,
            ShipTransponderBlockEntity transponder,
            boolean requestedRecordingMode
    ) {
        TransponderProjection projection = projectTransponderMenu(player, transponder, requestedRecordingMode);
        buffer.writeBlockPos(transponder.getBlockPos());
        buffer.writeBoolean(projection.recordingState().recordingMode());
        buffer.writeBoolean(projection.recordingState().recordingSessionActive());
        buffer.writeBoolean(projection.recordingState().appendToSchedule());
        buffer.writeBoolean(transponder.recordingDestinationStationId().isPresent());
        transponder.recordingDestinationStationId().ifPresent(buffer::writeUUID);
        buffer.writeEnum(projection.projectedRuntimeStatus());
        buffer.writeBoolean(transponder.dockOutputActive());
        buffer.writeBoolean(transponder.hasOwnedStops());
        buffer.writeNbt(AirshipScheduleNbtSerializer.write(transponder.ownedSchedule()));
        ShipTransponderMenu.writeCargoRevision(buffer, transponder.linkedCargoRevision());
        ShipTransponderMenu.writeCargoSummary(buffer, transponder.linkedCargoSummary());
        ShipTransponderMenu.writeLinkedCargoEntries(buffer, transponder.linkedCargo());
        ShipTransponderMenu.writeCargoFailureContext(
                buffer,
                AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId())
        );
        ShipTransponderMenu.writePlaybackFailure(
                buffer,
                AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId())
        );
        ShipTransponderMenu.writeStatusSnapshot(buffer, projection.statusSnapshot());
    }

    public static AirshipStationMenu createStationMenu(
            int containerId,
            Inventory playerInventory,
            ServerPlayer player,
            AirshipStationBlockEntity station
    ) {
        StationProjection projection = projectStationMenu(player, station);
        AirshipStationMenu menu = new AirshipStationMenu(
                containerId,
                playerInventory,
                station.getBlockPos(),
                station.selectedTransponderId(),
                station.selectedShipName(),
                station.linkedCargoRevision(),
                station.linkedCargoSummary(),
                station.linkedCargo(),
                station.selectedTransponderId().flatMap(AutomatedLogisticsServices.SCHEDULES::lastCargoFailureContext),
                projection.clientState().routeChoices()
        );
        menu.setClientState(projection.clientState());
        return menu;
    }

    public static AirshipStationMenu.ClientState buildStationClientState(
            ServerPlayer player,
            AirshipStationBlockEntity station
    ) {
        return projectStationMenu(player, station).clientState();
    }

    public static void writeStationOpenData(
            FriendlyByteBuf buffer,
            ServerPlayer player,
            AirshipStationBlockEntity station
    ) {
        StationProjection projection = projectStationMenu(player, station);
        buffer.writeBlockPos(station.getBlockPos());
        buffer.writeBoolean(station.selectedTransponderId().isPresent());
        station.selectedTransponderId().ifPresent(buffer::writeUUID);
        buffer.writeUtf(station.selectedShipName(), 64);
        ShipTransponderMenu.writeCargoRevision(buffer, station.linkedCargoRevision());
        ShipTransponderMenu.writeCargoSummary(buffer, station.linkedCargoSummary());
        ShipTransponderMenu.writeLinkedCargoEntries(buffer, station.linkedCargo());
        ShipTransponderMenu.writeCargoFailureContext(
                buffer,
                station.selectedTransponderId().flatMap(AutomatedLogisticsServices.SCHEDULES::lastCargoFailureContext)
        );
        AirshipStationMenu.writeRouteChoiceSummaries(buffer, projection.clientState().routeChoices());
        AirshipStationMenu.writeClientState(buffer, projection.clientState());
    }

    private static TransponderProjection projectTransponderMenu(
            ServerPlayer player,
            ShipTransponderBlockEntity transponder,
            boolean requestedRecordingMode
    ) {
        ShipTransponderMenu.InitialRecordingState recordingState =
                ShipTransponderMenu.resolveInitialRecordingState(player, transponder, requestedRecordingMode);
        RouteStatus projectedRuntimeStatus = AutomatedLogisticsServices.SCHEDULES
                .projectedRuntimeStatus(player.serverLevel(), transponder);
        ShipTransponderMenu statusMenu = new ShipTransponderMenu(
                0,
                player.getInventory(),
                transponder.getBlockPos(),
                recordingState.recordingMode(),
                recordingState.recordingSessionActive(),
                recordingState.appendToSchedule(),
                transponder.recordingDestinationStationId(),
                projectedRuntimeStatus,
                transponder.dockOutputActive(),
                transponder.hasOwnedStops(),
                transponder.ownedSchedule(),
                transponder.linkedCargoRevision(),
                transponder.linkedCargoSummary(),
                transponder.linkedCargo(),
                AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId()),
                AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId()),
                ShipTransponderMenu.StatusSnapshot.idle()
        );
        return new TransponderProjection(
                recordingState,
                projectedRuntimeStatus,
                statusMenu.buildStatusSnapshot(player)
        );
    }

    private static StationProjection projectStationMenu(
            ServerPlayer player,
            AirshipStationBlockEntity station
    ) {
        AirshipStationMenu.ClientState state = AirshipStationMenu.buildClientState(player, station);
        return new StationProjection(state);
    }

    private record TransponderProjection(
            ShipTransponderMenu.InitialRecordingState recordingState,
            RouteStatus projectedRuntimeStatus,
            ShipTransponderMenu.StatusSnapshot statusSnapshot
    ) {
    }

    private record StationProjection(AirshipStationMenu.ClientState clientState) {
    }
}

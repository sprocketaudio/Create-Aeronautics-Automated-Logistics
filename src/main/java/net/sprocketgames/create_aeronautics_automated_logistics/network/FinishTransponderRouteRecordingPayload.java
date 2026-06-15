package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteOperationResult;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import java.util.ArrayList;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

public record FinishTransponderRouteRecordingPayload(BlockPos transponderPos, UUID destinationStationId, boolean appendToSchedule)
        implements CustomPacketPayload {
    public static final Type<FinishTransponderRouteRecordingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "finish_transponder_route_recording")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, FinishTransponderRouteRecordingPayload> STREAM_CODEC =
            StreamCodec.ofMember(FinishTransponderRouteRecordingPayload::write, FinishTransponderRouteRecordingPayload::read);

    private static FinishTransponderRouteRecordingPayload read(RegistryFriendlyByteBuf buffer) {
        return new FinishTransponderRouteRecordingPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeUUID(destinationStationId);
        buffer.writeBoolean(appendToSchedule);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FinishTransponderRouteRecordingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
            StartTransponderRouteRecordingPayload.fail(
                    player,
                    Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable")
            );
            return;
        }
        if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
            PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, Optional.empty()));
            return;
        }
        Optional<AirshipStationSnapshot> destinationSnapshot = AirshipStationRegistry.snapshot(payload.destinationStationId())
                .filter(station -> station.dimension().equals(level.dimension()));
        if (destinationSnapshot.isEmpty()) {
            StartTransponderRouteRecordingPayload.fail(
                    player,
                    Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.station_missing")
            );
            return;
        }
        if (!(level.getBlockEntity(destinationSnapshot.get().stationPos()) instanceof net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity destinationStation)) {
            StartTransponderRouteRecordingPayload.fail(
                    player,
                    Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.station_missing")
            );
            return;
        }
        if (!StationPermissionService.ensureCanControl(player, destinationStation)) {
            PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, Optional.empty()));
            return;
        }
        Optional<VehicleController> controller = ShipTransponderRegistry.snapshot(transponder.transponderId())
                .filter(ship -> ship.dimension().equals(level.dimension()))
                .flatMap(ShipTransponderSnapshot::controllerRef)
                .flatMap(controllerRef -> VehicleControllerResolver.resolve(level, controllerRef));
        if (controller.isEmpty()) {
            StartTransponderRouteRecordingPayload.fail(
                    player,
                    Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable")
            );
            return;
        }
        if (!StartTransponderRouteRecordingPayload.isAtStation(controller.get(), destinationSnapshot.get())) {
            StartTransponderRouteRecordingPayload.actionBar(
                    player,
                    Component.translatable(
                            "message.create_aeronautics_automated_logistics.transponder_recording.move_to_destination",
                            destinationSnapshot.get().stationName()
                    )
            );
            PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(true, Optional.of(destinationSnapshot.get().stationId())));
            return;
        }

        RouteOperationResult<RouteSegment> result = AutomatedLogisticsServices.RECORDING.finishSegmentRecording(
                player,
                destinationSnapshot.get().stationPos(),
                false
        );
        result.value().ifPresentOrElse(
                segment -> {
                    appendRecordedSegmentToTransponderSchedule(transponder, segment);
                    AutomatedLogisticsServices.SCHEDULES.clearLastFailure(transponder.transponderId());
                    transponder.setRecordingDestinationStationId(Optional.empty());
                    StartTransponderRouteRecordingPayload.actionBar(player, Component.translatable(
                            "message.create_aeronautics_automated_logistics.segment_recording.saved",
                            segment.startStationName(),
                            segment.endStationName(),
                            segment.shipName(),
                            segment.points().size()
                    ));
                    PacketDistributor.sendToPlayer(
                            player,
                            new SyncTransponderOwnedSchedulePayload(
                                    transponder.getBlockPos(),
                                    AirshipScheduleNbtSerializer.write(transponder.ownedSchedule())
                            )
                    );
                    PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, Optional.empty()));
                },
                () -> result.failure().ifPresent(failure -> StartTransponderRouteRecordingPayload.fail(
                        player,
                        StartTransponderRouteRecordingPayload.recordingFailureMessage(failure)
                ))
        );
    }

    private static void appendRecordedSegmentToTransponderSchedule(ShipTransponderBlockEntity transponder, RouteSegment segment) {
        AirshipSchedule schedule = transponder.ownedSchedule();
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.add(
                AirshipScheduleEntry.blankTravel()
                        .withTargetStation(segment.endStationId(), segment.endStationName())
                        .withPinnedSegment(Optional.of(segment.id()))
        );
        transponder.setOwnedSchedule(schedule.withEntries(entries));
    }
}

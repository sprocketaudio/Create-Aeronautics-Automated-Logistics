package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingFailure;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteOperationResult;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingSession;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ShipMaterializationService;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;

public record StartTransponderRouteRecordingPayload(BlockPos transponderPos, UUID originStationId, UUID destinationStationId)
        implements CustomPacketPayload {
    public static final Type<StartTransponderRouteRecordingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "start_transponder_route_recording")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StartTransponderRouteRecordingPayload> STREAM_CODEC =
            StreamCodec.ofMember(StartTransponderRouteRecordingPayload::write, StartTransponderRouteRecordingPayload::read);

    private static StartTransponderRouteRecordingPayload read(RegistryFriendlyByteBuf buffer) {
        return new StartTransponderRouteRecordingPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeUUID(originStationId);
        buffer.writeUUID(destinationStationId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartTransponderRouteRecordingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
            fail(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return;
        }
        if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
            PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, Optional.empty()));
            return;
        }
        Optional<AirshipStationSnapshot> originSnapshot = AirshipStationRegistry.snapshot(payload.originStationId())
                .filter(station -> station.dimension().equals(level.dimension()));
        Optional<AirshipStationSnapshot> destinationSnapshot = AirshipStationRegistry.snapshot(payload.destinationStationId())
                .filter(station -> station.dimension().equals(level.dimension()));
        if (originSnapshot.isEmpty() || destinationSnapshot.isEmpty()) {
            fail(player, Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.station_missing"));
            return;
        }
        if (!(level.getBlockEntity(originSnapshot.get().stationPos()) instanceof AirshipStationBlockEntity originStation)) {
            fail(player, Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.station_missing"));
            return;
        }
        if (!(level.getBlockEntity(destinationSnapshot.get().stationPos()) instanceof AirshipStationBlockEntity destinationStation)) {
            fail(player, Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.station_missing"));
            return;
        }
        if (!StationPermissionService.ensureCanControl(player, originStation)
                || !StationPermissionService.ensureCanControl(player, destinationStation)) {
            PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, Optional.empty()));
            return;
        }
        if (originStation.isRecording()) {
            fail(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.busy"));
            return;
        }
        Optional<ShipTransponderSnapshot> shipSnapshot = ShipTransponderRegistry.snapshot(transponder.transponderId())
                .filter(ship -> ship.dimension().equals(level.dimension()));
        Optional<VehicleController> controller = shipSnapshot
                .flatMap(ShipTransponderSnapshot::controllerRef)
                .flatMap(controllerRef -> resolveLiveController(level, transponder.transponderId(), controllerRef, "start_transponder_route_recording"));
        if (shipSnapshot.isEmpty() || controller.isEmpty()) {
            fail(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return;
        }
        if (!isAtStation(controller.get(), originSnapshot.get())) {
            fail(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.transponder_recording.move_to_origin",
                    originSnapshot.get().stationName()
            ));
            return;
        }

        originStation.selectShip(shipSnapshot.get());
        RouteOperationResult<RecordingSession> result = AutomatedLogisticsServices.RECORDING.startRecording(
                player,
                originSnapshot.get().stationPos(),
                controller.get()
        );
        result.value().ifPresentOrElse(
                session -> {
                    AutomatedLogisticsServices.SCHEDULES.clearLastFailure(transponder.transponderId());
                    transponder.setRecordingDestinationStationId(Optional.of(destinationSnapshot.get().stationId()));
                    actionBar(player, Component.translatable(
                            "message.create_aeronautics_automated_logistics.transponder_recording.started",
                            originSnapshot.get().stationName(),
                            destinationSnapshot.get().stationName()
                    ));
                    PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(true, Optional.of(destinationSnapshot.get().stationId())));
                },
                () -> result.failure().ifPresent(failure -> fail(player, recordingFailureMessage(failure)))
        );
    }

    static boolean isAtStation(VehicleController controller, AirshipStationSnapshot station) {
        double landingRadius = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        double landingRadiusSqr = landingRadius * landingRadius;
        Vec3 position = controller.position();
        return station.stationPos().distToCenterSqr(position.x, position.y, position.z) <= landingRadiusSqr;
    }

    static Optional<VehicleController> resolveLiveController(
            ServerLevel level,
            UUID transponderId,
            net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef controllerRef,
            String source
    ) {
        return AutomatedLogisticsServices.MATERIALIZATION.resolveLiveBody(
                new ShipMaterializationService.LiveBodyLookupRequest(
                        level.getServer(),
                        level.dimension(),
                        controllerRef,
                        Optional.of(transponderId),
                        controllerRef.vehicleId(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        source,
                        "recording_live_lookup"
                )
        ).controller();
    }

    static Component recordingFailureMessage(RecordingFailure failure) {
        return Component.translatable(
                "message.create_aeronautics_automated_logistics.recording.failed",
                Component.translatable("failure.create_aeronautics_automated_logistics." + failure.name().toLowerCase(Locale.ROOT))
        );
    }

    static void fail(ServerPlayer player, Component message) {
        actionBar(player, message);
        PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, Optional.empty()));
    }

    static void actionBar(ServerPlayer player, Component message) {
        SetMenuActionBarMessagePayload.send(player, message);
    }
}

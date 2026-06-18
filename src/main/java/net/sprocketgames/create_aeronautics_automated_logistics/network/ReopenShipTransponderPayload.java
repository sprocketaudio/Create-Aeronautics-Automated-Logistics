package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RuntimeProjectionService;

public record ReopenShipTransponderPayload(BlockPos transponderPos, boolean recordingMode) implements CustomPacketPayload {
    public static final Type<ReopenShipTransponderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "reopen_ship_transponder")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ReopenShipTransponderPayload> STREAM_CODEC =
            StreamCodec.ofMember(ReopenShipTransponderPayload::write, ReopenShipTransponderPayload::read);

    private static ReopenShipTransponderPayload read(RegistryFriendlyByteBuf buffer) {
        return new ReopenShipTransponderPayload(buffer.readBlockPos(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeBoolean(recordingMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReopenShipTransponderPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
            return;
        }
        net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus projectedRuntimeStatus =
                net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices.SCHEDULES
                        .projectedRuntimeStatus(player.serverLevel(), transponder);
        boolean projectedScheduleActive =
                net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices.SCHEDULES
                        .projectedScheduleActive(player.serverLevel(), transponder);
        boolean projectedScheduleHeld =
                net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices.SCHEDULES
                        .projectedScheduleHeld(player.serverLevel(), transponder);
        var statusSnapshot = RuntimeProjectionService.buildTransponderStatusSnapshot(
                player,
                transponder,
                payload.recordingMode()
        );
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder reopenMenu id={} pos={} player={} spectator={} recordingMode={} runtimeStatus={} scheduleActive={} scheduleHeld={} dockOutput={} hasOwnedStops={} ownedStopsCount={} snapshotText='{}' snapshotColor={}",
                transponder.transponderId(),
                payload.transponderPos(),
                player.getName().getString(),
                player.isSpectator(),
                payload.recordingMode(),
                projectedRuntimeStatus,
                projectedScheduleActive,
                projectedScheduleHeld,
                transponder.dockOutputActive(),
                transponder.hasOwnedStops(),
                transponder.ownedSchedule().entries().size(),
                statusSnapshot.text(),
                Integer.toHexString(statusSnapshot.color())
        );
        player.openMenu(transponder, buffer ->
                RuntimeProjectionService.writeTransponderOpenData(buffer, player, transponder, payload.recordingMode()));
    }
}

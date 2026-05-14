package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record CancelTransponderRouteRecordingPayload(BlockPos transponderPos) implements CustomPacketPayload {
    public static final Type<CancelTransponderRouteRecordingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "cancel_transponder_route_recording")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelTransponderRouteRecordingPayload> STREAM_CODEC =
            StreamCodec.ofMember(CancelTransponderRouteRecordingPayload::write, CancelTransponderRouteRecordingPayload::read);

    private static CancelTransponderRouteRecordingPayload read(RegistryFriendlyByteBuf buffer) {
        return new CancelTransponderRouteRecordingPayload(buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelTransponderRouteRecordingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
            if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
                PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, java.util.Optional.empty()));
                return;
            }
            AutomatedLogisticsServices.RECORDING.cancelRecording(player);
            AutomatedLogisticsServices.SCHEDULES.clearLastFailure(transponder.transponderId());
            transponder.setRecordingDestinationStationId(java.util.Optional.empty());
        } else {
            AutomatedLogisticsServices.RECORDING.cancelRecording(player);
        }
        StartTransponderRouteRecordingPayload.actionBar(
                player,
                Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.cancelled")
        );
        PacketDistributor.sendToPlayer(player, new SetTransponderRecordingStatePayload(false, java.util.Optional.empty()));
    }
}

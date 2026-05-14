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
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;

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
        transponder.refreshRuntimeShip(player.serverLevel());
        ShipTransponderMenu.InitialRecordingState recordingState =
                ShipTransponderMenu.resolveInitialRecordingState(player, transponder, payload.recordingMode());
        player.openMenu(transponder, buffer -> {
            buffer.writeBlockPos(payload.transponderPos());
            buffer.writeBoolean(recordingState.recordingMode());
            buffer.writeBoolean(recordingState.recordingSessionActive());
            buffer.writeBoolean(recordingState.appendToSchedule());
            buffer.writeBoolean(transponder.recordingDestinationStationId().isPresent());
            transponder.recordingDestinationStationId().ifPresent(buffer::writeUUID);
        });
    }
}

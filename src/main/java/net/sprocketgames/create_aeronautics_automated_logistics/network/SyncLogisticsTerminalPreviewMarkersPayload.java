package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsTerminalPreviewClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapProjectionService;

public record SyncLogisticsTerminalPreviewMarkersPayload(List<ShipMapMarker> markers) implements CustomPacketPayload {
    public static final Type<SyncLogisticsTerminalPreviewMarkersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "sync_logistics_terminal_preview_markers")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLogisticsTerminalPreviewMarkersPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncLogisticsTerminalPreviewMarkersPayload::write, SyncLogisticsTerminalPreviewMarkersPayload::read);

    public SyncLogisticsTerminalPreviewMarkersPayload {
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncLogisticsTerminalPreviewMarkersPayload(ShipMapProjectionService.previewSnapshotsFor(player)));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(markers.size());
        for (ShipMapMarker marker : markers) {
            buffer.writeUUID(marker.transponderId());
            buffer.writeUtf(marker.shipName(), 64);
            buffer.writeUtf(marker.transportMode().serializedName(), 16);
            buffer.writeResourceLocation(marker.dimension().location());
            buffer.writeDouble(marker.position().x);
            buffer.writeDouble(marker.position().y);
            buffer.writeDouble(marker.position().z);
            buffer.writeUtf(marker.state(), 32);
        }
    }

    private static SyncLogisticsTerminalPreviewMarkersPayload read(RegistryFriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_IDENTITY_SNAPSHOTS, "logistics terminal preview markers");
        List<ShipMapMarker> markers = java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> SyncShipMapMarkersPayload.readMarker(buffer))
                .toList();
        return new SyncLogisticsTerminalPreviewMarkersPayload(markers);
    }

    public static void handle(SyncLogisticsTerminalPreviewMarkersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LogisticsTerminalPreviewClientState.replaceMarkers(payload.markers()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

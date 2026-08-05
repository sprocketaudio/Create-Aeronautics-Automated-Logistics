package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.map.ShipMapClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

public record SyncShipMapMarkersPayload(List<ShipMapMarker> markers) implements CustomPacketPayload {
    public static final Type<SyncShipMapMarkersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "sync_ship_map_markers")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncShipMapMarkersPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncShipMapMarkersPayload::write, SyncShipMapMarkersPayload::read);

    public SyncShipMapMarkersPayload {
        markers = markers == null ? List.of() : List.copyOf(markers);
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

    private static SyncShipMapMarkersPayload read(RegistryFriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_IDENTITY_SNAPSHOTS, "ship map markers");
        List<ShipMapMarker> markers = java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> readMarker(buffer))
                .toList();
        return new SyncShipMapMarkersPayload(markers);
    }

    static ShipMapMarker readMarker(RegistryFriendlyByteBuf buffer) {
        UUID transponderId = buffer.readUUID();
        String shipName = buffer.readUtf(64);
        TransportMode transportMode = TransportMode.fromSerializedName(buffer.readUtf(16));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
        Vec3 position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new ShipMapMarker(transponderId, shipName, transportMode, dimension, position, buffer.readUtf(32));
    }

    public static void handle(SyncShipMapMarkersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ShipMapClientState.replace(payload.markers()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

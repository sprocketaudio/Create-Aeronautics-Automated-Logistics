package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;

public record SetFlightPathPreviewPayload(boolean enabled, List<Vec3> points, List<Integer> legEndIndices) implements CustomPacketPayload {
    public static final Type<SetFlightPathPreviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_flight_path_preview")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFlightPathPreviewPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetFlightPathPreviewPayload::write, SetFlightPathPreviewPayload::read);

    private static SetFlightPathPreviewPayload read(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<Vec3> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        int legCount = buffer.readVarInt();
        List<Integer> legEndIndices = new ArrayList<>(legCount);
        for (int i = 0; i < legCount; i++) {
            legEndIndices.add(buffer.readVarInt());
        }
        return new SetFlightPathPreviewPayload(enabled, points, legEndIndices);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeVarInt(points.size());
        for (Vec3 point : points) {
            buffer.writeDouble(point.x);
            buffer.writeDouble(point.y);
            buffer.writeDouble(point.z);
        }
        buffer.writeVarInt(legEndIndices.size());
        for (Integer legEndIndex : legEndIndices) {
            buffer.writeVarInt(legEndIndex == null ? 0 : Math.max(0, legEndIndex));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetFlightPathPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.enabled()) {
                LogisticsClientOverlays.setFlightPath(payload.points(), payload.legEndIndices());
            } else {
                LogisticsClientOverlays.clearFlightPath();
            }
        });
    }
}

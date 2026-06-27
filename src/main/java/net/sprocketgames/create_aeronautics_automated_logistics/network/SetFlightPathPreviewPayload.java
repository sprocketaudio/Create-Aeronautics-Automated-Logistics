package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;

public record SetFlightPathPreviewPayload(
        boolean enabled,
        List<Vec3> points,
        List<Integer> legEndIndices,
        List<UUID> legPaletteKeys,
        List<Integer> legColorSlots,
        List<Integer> breakStartIndices,
        List<UUID> previewRouteIds,
        Optional<BlockPos> transponderPos,
        Optional<UUID> stationId,
        Optional<UUID> stationFilterTransponderId
) implements CustomPacketPayload {
    public static final Type<SetFlightPathPreviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_flight_path_preview")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFlightPathPreviewPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetFlightPathPreviewPayload::write, SetFlightPathPreviewPayload::read);

    private static SetFlightPathPreviewPayload read(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int count = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_FLIGHT_PATH_POINTS, "flight path points");
        List<Vec3> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        int legCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_FLIGHT_PATH_METADATA, "flight path leg ends");
        List<Integer> legEndIndices = new ArrayList<>(legCount);
        for (int i = 0; i < legCount; i++) {
            legEndIndices.add(buffer.readVarInt());
        }
        int paletteKeyCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_FLIGHT_PATH_METADATA, "flight path palette keys");
        List<UUID> legPaletteKeys = new ArrayList<>(paletteKeyCount);
        for (int i = 0; i < paletteKeyCount; i++) {
            legPaletteKeys.add(buffer.readUUID());
        }
        int legColorSlotCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_FLIGHT_PATH_METADATA, "flight path color slots");
        List<Integer> legColorSlots = new ArrayList<>(legColorSlotCount);
        for (int i = 0; i < legColorSlotCount; i++) {
            legColorSlots.add(buffer.readVarInt());
        }
        int breakStartCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_FLIGHT_PATH_METADATA, "flight path breaks");
        List<Integer> breakStartIndices = new ArrayList<>(breakStartCount);
        for (int i = 0; i < breakStartCount; i++) {
            breakStartIndices.add(buffer.readVarInt());
        }
        int previewRouteCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_PREVIEW_ROUTE_IDS, "flight path preview route ids");
        List<UUID> previewRouteIds = new ArrayList<>(previewRouteCount);
        for (int i = 0; i < previewRouteCount; i++) {
            previewRouteIds.add(buffer.readUUID());
        }
        Optional<BlockPos> transponderPos = buffer.readBoolean()
                ? Optional.of(buffer.readBlockPos())
                : Optional.empty();
        Optional<UUID> stationId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        Optional<UUID> stationFilterTransponderId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        return new SetFlightPathPreviewPayload(
                enabled,
                points,
                legEndIndices,
                legPaletteKeys,
                legColorSlots,
                breakStartIndices,
                previewRouteIds,
                transponderPos,
                stationId,
                stationFilterTransponderId
        );
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
        buffer.writeVarInt(legPaletteKeys.size());
        for (UUID legPaletteKey : legPaletteKeys) {
            buffer.writeUUID(legPaletteKey);
        }
        buffer.writeVarInt(legColorSlots.size());
        for (Integer legColorSlot : legColorSlots) {
            buffer.writeVarInt(legColorSlot == null ? 0 : Math.max(0, legColorSlot));
        }
        buffer.writeVarInt(breakStartIndices.size());
        for (Integer breakStartIndex : breakStartIndices) {
            buffer.writeVarInt(breakStartIndex == null ? 0 : Math.max(0, breakStartIndex));
        }
        buffer.writeVarInt(previewRouteIds.size());
        for (UUID previewRouteId : previewRouteIds) {
            buffer.writeUUID(previewRouteId);
        }
        buffer.writeBoolean(transponderPos.isPresent());
        transponderPos.ifPresent(buffer::writeBlockPos);
        buffer.writeBoolean(stationId.isPresent());
        stationId.ifPresent(buffer::writeUUID);
        buffer.writeBoolean(stationFilterTransponderId.isPresent());
        stationFilterTransponderId.ifPresent(buffer::writeUUID);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetFlightPathPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.enabled()) {
                LogisticsClientOverlays.setFlightPath(
                        payload.points(),
                        payload.legEndIndices(),
                        payload.legPaletteKeys(),
                        payload.legColorSlots(),
                        payload.breakStartIndices(),
                        payload.previewRouteIds(),
                        payload.transponderPos().orElse(null),
                        payload.stationId().orElse(null),
                        payload.stationFilterTransponderId().orElse(null)
                );
            } else {
                LogisticsClientOverlays.clearFlightPath();
            }
        });
    }
}

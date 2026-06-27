package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;

public record PreviewStationRoutesPayload(
        int containerId,
        BlockPos stationPos,
        List<UUID> routeIds
) implements CustomPacketPayload {
    public static final Type<PreviewStationRoutesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "preview_station_routes")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewStationRoutesPayload> STREAM_CODEC =
            StreamCodec.ofMember(PreviewStationRoutesPayload::write, PreviewStationRoutesPayload::read);

    private static PreviewStationRoutesPayload read(RegistryFriendlyByteBuf buffer) {
        int routeCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_PREVIEW_ROUTE_IDS, "preview route ids");
        List<UUID> routeIds = new ArrayList<>(routeCount);
        for (int i = 0; i < routeCount; i++) {
            routeIds.add(buffer.readUUID());
        }
        return new PreviewStationRoutesPayload(buffer.readVarInt(), buffer.readBlockPos(), List.copyOf(routeIds));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(routeIds.size());
        for (UUID routeId : routeIds) {
            buffer.writeUUID(routeId);
        }
        buffer.writeVarInt(containerId);
        buffer.writeBlockPos(stationPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PreviewStationRoutesPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AirshipStationMenu menu)) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station preview selection rejected currentMenu={} player={} spectator={} routeCount={} pos={}",
                    player.containerMenu == null ? "<null>" : player.containerMenu.getClass().getSimpleName(),
                    player.getName().getString(),
                    player.isSpectator(),
                    payload.routeIds().size(),
                    payload.stationPos()
            );
            return;
        }
        if (menu.containerId != payload.containerId() || !menu.stationPos().equals(payload.stationPos())) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station preview selection rejected mismatch menuId={} payloadId={} menuPos={} payloadPos={} player={} spectator={} routeCount={}",
                    menu.containerId,
                    payload.containerId(),
                    menu.stationPos(),
                    payload.stationPos(),
                    player.getName().getString(),
                    player.isSpectator(),
                    payload.routeIds().size()
            );
            return;
        }
        menu.previewSelectedRoutes(player, payload.routeIds());
    }
}

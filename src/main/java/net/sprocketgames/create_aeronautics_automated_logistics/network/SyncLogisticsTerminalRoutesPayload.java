package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.LogisticsTerminalClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record SyncLogisticsTerminalRoutesPayload(List<RouteSegment> routes) implements CustomPacketPayload {
    public static final Type<SyncLogisticsTerminalRoutesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "sync_logistics_terminal_routes"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLogisticsTerminalRoutesPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncLogisticsTerminalRoutesPayload::write, SyncLogisticsTerminalRoutesPayload::read);

    public SyncLogisticsTerminalRoutesPayload {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncLogisticsTerminalRoutesPayload(visibleRoutesFor(player)));
    }

    private static List<RouteSegment> visibleRoutesFor(ServerPlayer player) {
        Map<UUID, AirshipStationSnapshot> stations = new LinkedHashMap<>();
        IdentityDirectorySavedData.get(player.server).stationSnapshots().forEach(station -> stations.put(station.stationId(), station));
        AirshipStationRegistry.allStations().forEach(station -> stations.put(station.stationId(), station));

        Map<UUID, RouteSegment> visibleRoutes = new LinkedHashMap<>();
        for (AirshipStationSnapshot station : stations.values()) {
            RouteSegmentDirectorySavedData.connectedToStation(player.server, station.stationId()).stream()
                    .filter(route -> route.dimension().equals(player.serverLevel().dimension()))
                    .filter(route -> TransponderPermissionService.canControl(player, route.ownerId()))
                    .forEach(route -> visibleRoutes.put(route.id().value(), route));
            RouteSegmentRegistry.connectedToStation(station.stationId()).stream()
                    .filter(route -> route.dimension().equals(player.serverLevel().dimension()))
                    .filter(route -> TransponderPermissionService.canControl(player, route.ownerId()))
                    .forEach(route -> visibleRoutes.putIfAbsent(route.id().value(), route));
        }
        return collapseSupersededRouteHistory(visibleRoutes.values().stream()
                .sorted(java.util.Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .thenComparing(route -> route.id().value().toString()))
                .toList());
    }

    private static List<RouteSegment> collapseSupersededRouteHistory(List<RouteSegment> routes) {
        routes = routes.stream()
                .sorted(java.util.Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(route -> route.id().value().toString()))
                .toList();

        Map<RouteHistoryKey, RouteSegment> newestByKey = new LinkedHashMap<>();
        for (RouteSegment route : routes) {
            newestByKey.putIfAbsent(
                    new RouteHistoryKey(
                            route.startStationId(),
                            route.endStationId(),
                            route.transponderId(),
                            route.dimension()
                    ),
                    route
            );
        }
        return newestByKey.values().stream()
                .sorted(java.util.Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(route -> route.id().value().toString()))
                .toList();
    }

    private static SyncLogisticsTerminalRoutesPayload read(RegistryFriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_TERMINAL_ROUTE_SEGMENTS, "logistics terminal routes");
        List<RouteSegment> routes = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompoundTag tag = buffer.readNbt();
            if (tag != null) {
                RouteSegmentNbtSerializer.read(tag).ifPresent(routes::add);
            }
        }
        return new SyncLogisticsTerminalRoutesPayload(routes);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(routes.size());
        for (RouteSegment route : routes) {
            buffer.writeNbt(RouteSegmentNbtSerializer.write(route));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncLogisticsTerminalRoutesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) {
                return;
            }
            LogisticsTerminalClientState.replaceRoutes(payload.routes());
        });
    }

    private record RouteHistoryKey(
            UUID startStationId,
            UUID endStationId,
            UUID transponderId,
            ResourceKey<Level> dimension
    ) {
    }
}

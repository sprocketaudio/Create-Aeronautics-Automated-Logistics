package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;

public record SyncIdentityDirectoryPayload(
        List<AirshipStationSnapshot> stations,
        List<ShipTransponderSnapshot> ships
) implements CustomPacketPayload {
    public static final Type<SyncIdentityDirectoryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "sync_identity_directory"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncIdentityDirectoryPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncIdentityDirectoryPayload::write, SyncIdentityDirectoryPayload::read);

    public SyncIdentityDirectoryPayload {
        stations = stations == null ? List.of() : List.copyOf(stations);
        ships = ships == null ? List.of() : List.copyOf(ships);
    }

    public static void sendTo(ServerPlayer player) {
        IdentityDirectorySavedData identities = IdentityDirectorySavedData.get(player.server);
        PacketDistributor.sendToPlayer(player, new SyncIdentityDirectoryPayload(
                stationSnapshots(identities),
                shipSnapshots(identities)
        ));
    }

    private static List<AirshipStationSnapshot> stationSnapshots(IdentityDirectorySavedData identities) {
        Map<UUID, AirshipStationSnapshot> snapshots = new LinkedHashMap<>();
        for (AirshipStationSnapshot station : identities.stationSnapshots()) {
            snapshots.put(station.stationId(), station);
        }
        for (AirshipStationSnapshot station : AirshipStationRegistry.allStations()) {
            snapshots.put(station.stationId(), station);
        }
        return List.copyOf(snapshots.values());
    }

    private static List<ShipTransponderSnapshot> shipSnapshots(IdentityDirectorySavedData identities) {
        Map<UUID, ShipTransponderSnapshot> snapshots = new LinkedHashMap<>();
        for (ShipTransponderSnapshot ship : identities.shipSnapshots()) {
            snapshots.put(ship.transponderId(), ship);
        }
        for (ShipTransponderSnapshot ship : ShipTransponderRegistry.allShips()) {
            snapshots.put(ship.transponderId(), ship);
        }
        return List.copyOf(snapshots.values());
    }

    private static SyncIdentityDirectoryPayload read(RegistryFriendlyByteBuf buffer) {
        int stationCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_IDENTITY_SNAPSHOTS, "identity station snapshots");
        List<AirshipStationSnapshot> stations = java.util.stream.IntStream.range(0, stationCount)
                .mapToObj(ignored -> readStation(buffer))
                .toList();
        int shipCount = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_IDENTITY_SNAPSHOTS, "identity ship snapshots");
        List<ShipTransponderSnapshot> ships = java.util.stream.IntStream.range(0, shipCount)
                .mapToObj(ignored -> readShip(buffer))
                .toList();
        return new SyncIdentityDirectoryPayload(stations, ships);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(stations.size());
        for (AirshipStationSnapshot station : stations) {
            writeStation(buffer, station);
        }
        buffer.writeVarInt(ships.size());
        for (ShipTransponderSnapshot ship : ships) {
            writeShip(buffer, ship);
        }
    }

    private static void writeStation(RegistryFriendlyByteBuf buffer, AirshipStationSnapshot station) {
        buffer.writeUUID(station.stationId());
        buffer.writeUtf(station.stationName(), 64);
        buffer.writeResourceLocation(station.dimension().location());
        buffer.writeBlockPos(station.stationPos());
        buffer.writeBoolean(station.ownerId().isPresent());
        station.ownerId().ifPresent(buffer::writeUUID);
        buffer.writeUtf(station.ownerName(), 64);
    }

    private static AirshipStationSnapshot readStation(RegistryFriendlyByteBuf buffer) {
        UUID stationId = buffer.readUUID();
        String stationName = buffer.readUtf(64);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
        BlockPos stationPos = buffer.readBlockPos();
        Optional<UUID> ownerId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        String ownerName = buffer.readUtf(64);
        return new AirshipStationSnapshot(stationId, stationName, dimension, stationPos, ownerId, ownerName);
    }

    private static void writeShip(RegistryFriendlyByteBuf buffer, ShipTransponderSnapshot ship) {
        buffer.writeUUID(ship.transponderId());
        buffer.writeUtf(ship.shipName(), 64);
        buffer.writeResourceLocation(ship.dimension().location());
        buffer.writeBlockPos(ship.transponderPos());
        buffer.writeBoolean(ship.runtimeShipId().isPresent());
        ship.runtimeShipId().ifPresent(buffer::writeUUID);
        buffer.writeBoolean(ship.lastKnownPosition().isPresent());
        ship.lastKnownPosition().ifPresent(position -> {
            buffer.writeDouble(position.x);
            buffer.writeDouble(position.y);
            buffer.writeDouble(position.z);
        });
        buffer.writeLong(ship.lastSeenGameTime());
    }

    private static ShipTransponderSnapshot readShip(RegistryFriendlyByteBuf buffer) {
        UUID transponderId = buffer.readUUID();
        String shipName = buffer.readUtf(64);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
        BlockPos transponderPos = buffer.readBlockPos();
        Optional<UUID> runtimeShipId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        Optional<Vec3> lastKnownPosition = buffer.readBoolean()
                ? Optional.of(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()))
                : Optional.empty();
        long lastSeenGameTime = buffer.readLong();
        return new ShipTransponderSnapshot(
                transponderId,
                shipName,
                dimension,
                transponderPos,
                runtimeShipId,
                Optional.empty(),
                lastKnownPosition,
                lastSeenGameTime
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncIdentityDirectoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            AirshipStationRegistry.replaceAll(payload.stations());
            ShipTransponderRegistry.replaceAll(payload.ships().stream()
                    .map(SyncIdentityDirectoryPayload::mergeShip)
                    .toList());
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Identity directory sync received stations={} ships={}",
                    payload.stations().size(),
                    payload.ships().size()
            );
        });
    }

    private static ShipTransponderSnapshot mergeShip(ShipTransponderSnapshot incoming) {
        Optional<ShipTransponderSnapshot> existing = ShipTransponderRegistry.snapshot(incoming.transponderId());
        if (existing.isEmpty()) {
            return incoming;
        }
        ShipTransponderSnapshot current = existing.get();
        return new ShipTransponderSnapshot(
                incoming.transponderId(),
                incoming.shipName(),
                incoming.dimension(),
                incoming.transponderPos(),
                current.runtimeShipId().or(incoming::runtimeShipId),
                current.controllerRef().or(incoming::controllerRef),
                current.lastKnownPosition().or(incoming::lastKnownPosition),
                Math.max(current.lastSeenGameTime(), incoming.lastSeenGameTime())
        );
    }
}

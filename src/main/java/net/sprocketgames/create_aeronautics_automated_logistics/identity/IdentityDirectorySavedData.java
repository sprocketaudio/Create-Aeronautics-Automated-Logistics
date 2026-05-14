package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class IdentityDirectorySavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_identity_directory";
    private static final String SHIPS = "Ships";
    private static final String STATIONS = "Stations";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SHIP_NAME = "shipName";
    private static final String STATION_ID = "stationId";
    private static final String STATION_NAME = "stationName";
    private static final String OWNER_ID = "ownerId";
    private static final String OWNER_NAME = "ownerName";
    private static final String DIMENSION = "dimension";
    private static final String POS = "pos";
    private static final String LAST_X = "lastX";
    private static final String LAST_Y = "lastY";
    private static final String LAST_Z = "lastZ";
    private static final String LAST_SEEN = "lastSeen";

    private final Map<UUID, PersistedShipIdentity> ships = new ConcurrentHashMap<>();
    private final Map<UUID, PersistedStationIdentity> stations = new ConcurrentHashMap<>();

    public static SavedData.Factory<IdentityDirectorySavedData> factory() {
        return new SavedData.Factory<>(IdentityDirectorySavedData::new, IdentityDirectorySavedData::load);
    }

    public static IdentityDirectorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void upsertShip(MinecraftServer server, ShipTransponderSnapshot snapshot) {
        IdentityDirectorySavedData data = get(server);
        PersistedShipIdentity identity = PersistedShipIdentity.from(snapshot);
        PersistedShipIdentity previous = data.ships.put(identity.transponderId(), identity);
        if (!identity.equals(previous)) {
            data.setDirty();
        }
    }

    public static void removeShip(MinecraftServer server, UUID transponderId) {
        IdentityDirectorySavedData data = get(server);
        if (data.ships.remove(transponderId) != null) {
            data.setDirty();
        }
    }

    public static void upsertStation(MinecraftServer server, AirshipStationSnapshot snapshot) {
        IdentityDirectorySavedData data = get(server);
        PersistedStationIdentity identity = PersistedStationIdentity.from(snapshot);
        PersistedStationIdentity previous = data.stations.put(identity.stationId(), identity);
        if (!identity.equals(previous)) {
            data.setDirty();
        }
    }

    public static void removeStation(MinecraftServer server, UUID stationId) {
        IdentityDirectorySavedData data = get(server);
        if (data.stations.remove(stationId) != null) {
            data.setDirty();
        }
    }

    public List<PersistedShipIdentity> allShips() {
        return ships.values().stream()
                .sorted(Comparator.comparing(PersistedShipIdentity::shipName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(identity -> identity.transponderId().toString()))
                .toList();
    }

    public List<PersistedStationIdentity> allStations() {
        return stations.values().stream()
                .sorted(Comparator.comparing(PersistedStationIdentity::stationName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(identity -> identity.stationId().toString()))
                .toList();
    }

    public Optional<PersistedShipIdentity> ship(UUID transponderId) {
        return Optional.ofNullable(ships.get(transponderId));
    }

    public Optional<PersistedStationIdentity> station(UUID stationId) {
        return Optional.ofNullable(stations.get(stationId));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag shipList = new ListTag();
        for (PersistedShipIdentity ship : allShips()) {
            CompoundTag shipTag = new CompoundTag();
            shipTag.putUUID(TRANSPONDER_ID, ship.transponderId());
            shipTag.putString(SHIP_NAME, ship.shipName());
            shipTag.putString(DIMENSION, ship.dimension().location().toString());
            shipTag.put(POS, NbtUtils.writeBlockPos(ship.transponderPos()));
            ship.lastKnownPosition().ifPresent(pos -> {
                shipTag.putDouble(LAST_X, pos.x);
                shipTag.putDouble(LAST_Y, pos.y);
                shipTag.putDouble(LAST_Z, pos.z);
            });
            shipTag.putLong(LAST_SEEN, ship.lastSeenGameTime());
            shipList.add(shipTag);
        }
        tag.put(SHIPS, shipList);

        ListTag stationList = new ListTag();
        for (PersistedStationIdentity station : allStations()) {
            CompoundTag stationTag = new CompoundTag();
            stationTag.putUUID(STATION_ID, station.stationId());
            stationTag.putString(STATION_NAME, station.stationName());
            station.ownerId().ifPresent(id -> stationTag.putUUID(OWNER_ID, id));
            stationTag.putString(OWNER_NAME, station.ownerName());
            stationTag.putString(DIMENSION, station.dimension().location().toString());
            stationTag.put(POS, NbtUtils.writeBlockPos(station.stationPos()));
            stationList.add(stationTag);
        }
        tag.put(STATIONS, stationList);
        return tag;
    }

    private static IdentityDirectorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        IdentityDirectorySavedData data = new IdentityDirectorySavedData();
        if (tag.contains(SHIPS, Tag.TAG_LIST)) {
            ListTag shipList = tag.getList(SHIPS, Tag.TAG_COMPOUND);
            for (int i = 0; i < shipList.size(); i++) {
                readShip(shipList.getCompound(i)).ifPresent(identity -> data.ships.put(identity.transponderId(), identity));
            }
        }
        if (tag.contains(STATIONS, Tag.TAG_LIST)) {
            ListTag stationList = tag.getList(STATIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < stationList.size(); i++) {
                readStation(stationList.getCompound(i)).ifPresent(identity -> data.stations.put(identity.stationId(), identity));
            }
        }
        return data;
    }

    private static Optional<PersistedShipIdentity> readShip(CompoundTag tag) {
        if (!tag.hasUUID(TRANSPONDER_ID)
                || !tag.contains(SHIP_NAME, Tag.TAG_STRING)
                || !tag.contains(DIMENSION, Tag.TAG_STRING)
                || !tag.contains(POS, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        if (dimensionId == null) {
            return Optional.empty();
        }
        return NbtUtils.readBlockPos(tag, POS).map(pos -> new PersistedShipIdentity(
                tag.getUUID(TRANSPONDER_ID),
                IdentityNames.sanitize(tag.getString(SHIP_NAME)),
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                pos.immutable(),
                tag.contains(LAST_X, Tag.TAG_ANY_NUMERIC)
                        && tag.contains(LAST_Y, Tag.TAG_ANY_NUMERIC)
                        && tag.contains(LAST_Z, Tag.TAG_ANY_NUMERIC)
                        ? Optional.of(new Vec3(tag.getDouble(LAST_X), tag.getDouble(LAST_Y), tag.getDouble(LAST_Z)))
                        : Optional.empty(),
                tag.contains(LAST_SEEN, Tag.TAG_ANY_NUMERIC) ? tag.getLong(LAST_SEEN) : -1L
        ));
    }

    private static Optional<PersistedStationIdentity> readStation(CompoundTag tag) {
        if (!tag.hasUUID(STATION_ID)
                || !tag.contains(STATION_NAME, Tag.TAG_STRING)
                || !tag.contains(DIMENSION, Tag.TAG_STRING)
                || !tag.contains(POS, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        if (dimensionId == null) {
            return Optional.empty();
        }
        return NbtUtils.readBlockPos(tag, POS).map(pos -> new PersistedStationIdentity(
                tag.getUUID(STATION_ID),
                IdentityNames.sanitize(tag.getString(STATION_NAME)),
                tag.hasUUID(OWNER_ID) ? Optional.of(tag.getUUID(OWNER_ID)) : Optional.empty(),
                tag.contains(OWNER_NAME, Tag.TAG_STRING) ? IdentityNames.sanitize(tag.getString(OWNER_NAME)) : "",
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                pos.immutable()
        ));
    }

    public record PersistedShipIdentity(
            UUID transponderId,
            String shipName,
            ResourceKey<Level> dimension,
            BlockPos transponderPos,
            Optional<Vec3> lastKnownPosition,
            long lastSeenGameTime
    ) {
        public PersistedShipIdentity {
            shipName = IdentityNames.sanitize(shipName);
        }

        public static PersistedShipIdentity from(ShipTransponderSnapshot snapshot) {
            return new PersistedShipIdentity(
                    snapshot.transponderId(),
                    snapshot.shipName(),
                    snapshot.dimension(),
                    snapshot.transponderPos().immutable(),
                    snapshot.lastKnownPosition(),
                    snapshot.lastSeenGameTime()
            );
        }
    }

    public record PersistedStationIdentity(
            UUID stationId,
            String stationName,
            Optional<UUID> ownerId,
            String ownerName,
            ResourceKey<Level> dimension,
            BlockPos stationPos
    ) {
        public PersistedStationIdentity {
            stationName = IdentityNames.sanitize(stationName);
            ownerId = ownerId == null ? Optional.empty() : ownerId;
            ownerName = IdentityNames.sanitize(ownerName);
        }

        public static PersistedStationIdentity from(AirshipStationSnapshot snapshot) {
            return new PersistedStationIdentity(
                    snapshot.stationId(),
                    snapshot.stationName(),
                    snapshot.ownerId(),
                    snapshot.ownerName(),
                    snapshot.dimension(),
                    snapshot.stationPos().immutable()
            );
        }
    }
}

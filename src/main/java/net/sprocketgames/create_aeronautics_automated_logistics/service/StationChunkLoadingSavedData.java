package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class StationChunkLoadingSavedData extends SavedData {
    private static final String DATA_NAME = "automated_logistics_station_chunk_loading";
    private static final String STATIONS = "stations";
    private static final String STATION_ID = "stationId";
    private static final String DIMENSION = "dimension";
    private static final String CHUNK = "chunk";

    private final Map<UUID, ForcedStationChunk> stations = new LinkedHashMap<>();

    public static SavedData.Factory<StationChunkLoadingSavedData> factory() {
        return new SavedData.Factory<>(StationChunkLoadingSavedData::new, StationChunkLoadingSavedData::load);
    }

    public static StationChunkLoadingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public Map<UUID, ForcedStationChunk> stations() {
        return Map.copyOf(stations);
    }

    public Optional<ForcedStationChunk> station(UUID stationId) {
        return Optional.ofNullable(stations.get(stationId));
    }

    public void put(UUID stationId, ForcedStationChunk chunk) {
        ForcedStationChunk previous = stations.put(stationId, chunk);
        if (!chunk.equals(previous)) {
            setDirty();
        }
    }

    public Optional<ForcedStationChunk> remove(UUID stationId) {
        ForcedStationChunk removed = stations.remove(stationId);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    public void replaceAll(Map<UUID, ForcedStationChunk> replacements) {
        if (!stations.equals(replacements)) {
            stations.clear();
            stations.putAll(replacements);
            setDirty();
        }
    }

    public boolean anyOtherStationUses(UUID stationId, ForcedStationChunk chunk) {
        return stations.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(stationId) && entry.getValue().equals(chunk));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stationList = new ListTag();
        for (Map.Entry<UUID, ForcedStationChunk> entry : stations.entrySet()) {
            CompoundTag stationTag = new CompoundTag();
            stationTag.putUUID(STATION_ID, entry.getKey());
            stationTag.putString(DIMENSION, entry.getValue().dimension().location().toString());
            stationTag.put(CHUNK, NbtUtils.writeBlockPos(entry.getValue().chunkPos()));
            stationList.add(stationTag);
        }
        tag.put(STATIONS, stationList);
        return tag;
    }

    private static StationChunkLoadingSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StationChunkLoadingSavedData data = new StationChunkLoadingSavedData();
        if (!tag.contains(STATIONS, Tag.TAG_LIST)) {
            return data;
        }
        ListTag stationList = tag.getList(STATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < stationList.size(); i++) {
            CompoundTag stationTag = stationList.getCompound(i);
            if (!stationTag.hasUUID(STATION_ID)
                    || !stationTag.contains(DIMENSION, Tag.TAG_STRING)
                    || !stationTag.contains(CHUNK, Tag.TAG_COMPOUND)) {
                continue;
            }
            Optional<BlockPos> chunkPos = NbtUtils.readBlockPos(stationTag, CHUNK);
            if (chunkPos.isEmpty()) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(stationTag.getString(DIMENSION))
            );
            data.stations.put(
                    stationTag.getUUID(STATION_ID),
                    new ForcedStationChunk(dimension, chunkPos.get().immutable())
            );
        }
        return data;
    }

    public record ForcedStationChunk(ResourceKey<Level> dimension, BlockPos chunkPos) {
        public ForcedStationChunk {
            chunkPos = chunkPos.immutable();
        }
    }
}

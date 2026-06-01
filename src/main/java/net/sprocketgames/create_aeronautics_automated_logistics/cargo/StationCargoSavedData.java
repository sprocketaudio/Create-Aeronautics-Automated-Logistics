package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public class StationCargoSavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_station_cargo";
    private static final String STATIONS = "Stations";
    private static final String STATION_ID = "stationId";
    private static final String LINKED_CARGO = "linkedCargo";
    private static final String RELATIVE = "relative";

    private final Map<UUID, StoredCargo> cargoByStation = new ConcurrentHashMap<>();

    public static SavedData.Factory<StationCargoSavedData> factory() {
        return new SavedData.Factory<>(StationCargoSavedData::new, StationCargoSavedData::load);
    }

    public static StationCargoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static List<LinkedCargoEntry> entries(MinecraftServer server, UUID stationId, BlockPos anchorPos) {
        StoredCargo storedCargo = get(server).cargoByStation.get(stationId);
        if (storedCargo == null) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "StationCargoSavedData entries miss id={} anchor={}",
                    stationId,
                    anchorPos
            );
            return List.of();
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "StationCargoSavedData entries hit id={} anchor={} storedCount={} relative={}",
                stationId,
                anchorPos,
                storedCargo.entries().size(),
                storedCargo.relative()
        );
        if (!storedCargo.relative()) {
            return List.copyOf(storedCargo.entries());
        }
        return storedCargo.entries().stream()
                .map(entry -> new LinkedCargoEntry(
                        anchorPos.offset(entry.pos()),
                        entry.itemStorage(),
                        entry.fluidStorage()
                ))
                .toList();
    }

    public static void put(MinecraftServer server, UUID stationId, BlockPos anchorPos, List<LinkedCargoEntry> entries) {
        StationCargoSavedData data = get(server);
        StoredCargo normalized = new StoredCargo(
                true,
                entries.stream()
                        .map(entry -> new LinkedCargoEntry(
                                new BlockPos(
                                        entry.pos().getX() - anchorPos.getX(),
                                        entry.pos().getY() - anchorPos.getY(),
                                        entry.pos().getZ() - anchorPos.getZ()
                                ),
                                entry.itemStorage(),
                                entry.fluidStorage()
                        ))
                        .toList()
        );
        StoredCargo previous = data.cargoByStation.put(stationId, normalized);
        if (!normalized.equals(previous)) {
            data.setDirty();
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "StationCargoSavedData put id={} anchor={} count={} dirty={}",
                stationId,
                anchorPos,
                normalized.entries().size(),
                !normalized.equals(previous)
        );
    }

    public static void remove(MinecraftServer server, UUID stationId) {
        StationCargoSavedData data = get(server);
        if (data.cargoByStation.remove(stationId) != null) {
            data.setDirty();
            CreateAeronauticsAutomatedLogistics.debugLog("StationCargoSavedData remove id={} removed=true", stationId);
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugLog("StationCargoSavedData remove id={} removed=false", stationId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stationList = new ListTag();
        for (Map.Entry<UUID, StoredCargo> entry : cargoByStation.entrySet()) {
            CompoundTag stationTag = new CompoundTag();
            stationTag.putUUID(STATION_ID, entry.getKey());
            stationTag.putBoolean(RELATIVE, entry.getValue().relative());
            ListTag cargoList = new ListTag();
            for (LinkedCargoEntry linkedCargoEntry : entry.getValue().entries()) {
                cargoList.add(linkedCargoEntry.write());
            }
            stationTag.put(LINKED_CARGO, cargoList);
            stationList.add(stationTag);
        }
        tag.put(STATIONS, stationList);
        return tag;
    }

    private static StationCargoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StationCargoSavedData data = new StationCargoSavedData();
        if (!tag.contains(STATIONS, Tag.TAG_LIST)) {
            CreateAeronauticsAutomatedLogistics.debugLog("StationCargoSavedData load no stations tag");
            return data;
        }
        ListTag stationList = tag.getList(STATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < stationList.size(); i++) {
            CompoundTag stationTag = stationList.getCompound(i);
            if (!stationTag.hasUUID(STATION_ID) || !stationTag.contains(LINKED_CARGO, Tag.TAG_LIST)) {
                continue;
            }
            ListTag cargoList = stationTag.getList(LINKED_CARGO, Tag.TAG_COMPOUND);
            List<LinkedCargoEntry> entries = cargoList.stream()
                    .filter(CompoundTag.class::isInstance)
                    .map(CompoundTag.class::cast)
                    .map(LinkedCargoEntry::read)
                    .flatMap(java.util.Optional::stream)
                    .toList();
            if (!entries.isEmpty()) {
                data.cargoByStation.put(
                        stationTag.getUUID(STATION_ID),
                        new StoredCargo(stationTag.getBoolean(RELATIVE), entries)
                );
            }
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "StationCargoSavedData load loadedStations={}",
                data.cargoByStation.size()
        );
        return data;
    }

    private record StoredCargo(boolean relative, List<LinkedCargoEntry> entries) {
    }
}

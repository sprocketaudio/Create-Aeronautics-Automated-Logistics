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

public class TransponderCargoSavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_transponder_cargo";
    private static final String TRANSPONDERS = "Transponders";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String LINKED_CARGO = "linkedCargo";
    private static final String RELATIVE = "relative";

    private final Map<UUID, StoredCargo> cargoByTransponder = new ConcurrentHashMap<>();

    public static SavedData.Factory<TransponderCargoSavedData> factory() {
        return new SavedData.Factory<>(TransponderCargoSavedData::new, TransponderCargoSavedData::load);
    }

    public static TransponderCargoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static List<LinkedCargoEntry> entries(MinecraftServer server, UUID transponderId, BlockPos anchorPos) {
        StoredCargo storedCargo = get(server).cargoByTransponder.get(transponderId);
        if (storedCargo == null) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "TransponderCargoSavedData entries miss id={} anchor={}",
                    transponderId,
                    anchorPos
            );
            return List.of();
        }
        CreateAeronauticsAutomatedLogistics.debugCargo(
                "TransponderCargoSavedData entries hit id={} anchor={} storedCount={} relative={}",
                transponderId,
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

    public static void put(MinecraftServer server, UUID transponderId, BlockPos anchorPos, List<LinkedCargoEntry> entries) {
        TransponderCargoSavedData data = get(server);
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
        StoredCargo previous = data.cargoByTransponder.put(transponderId, normalized);
        if (!normalized.equals(previous)) {
            data.setDirty();
        }
        CreateAeronauticsAutomatedLogistics.debugCargo(
                "TransponderCargoSavedData put id={} anchor={} count={} dirty={}",
                transponderId,
                anchorPos,
                normalized.entries().size(),
                !normalized.equals(previous)
        );
    }

    public static void remove(MinecraftServer server, UUID transponderId) {
        TransponderCargoSavedData data = get(server);
        if (data.cargoByTransponder.remove(transponderId) != null) {
            data.setDirty();
            CreateAeronauticsAutomatedLogistics.debugCargo("TransponderCargoSavedData remove id={} removed=true", transponderId);
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugCargo("TransponderCargoSavedData remove id={} removed=false", transponderId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag transponderList = new ListTag();
        for (Map.Entry<UUID, StoredCargo> entry : cargoByTransponder.entrySet()) {
            CompoundTag transponderTag = new CompoundTag();
            transponderTag.putUUID(TRANSPONDER_ID, entry.getKey());
            transponderTag.putBoolean(RELATIVE, entry.getValue().relative());
            ListTag cargoList = new ListTag();
            for (LinkedCargoEntry linkedCargoEntry : entry.getValue().entries()) {
                cargoList.add(linkedCargoEntry.write());
            }
            transponderTag.put(LINKED_CARGO, cargoList);
            transponderList.add(transponderTag);
        }
        tag.put(TRANSPONDERS, transponderList);
        return tag;
    }

    private static TransponderCargoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TransponderCargoSavedData data = new TransponderCargoSavedData();
        if (!tag.contains(TRANSPONDERS, Tag.TAG_LIST)) {
            CreateAeronauticsAutomatedLogistics.debugCargo("TransponderCargoSavedData load no transponders tag");
            return data;
        }
        ListTag transponderList = tag.getList(TRANSPONDERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < transponderList.size(); i++) {
            CompoundTag transponderTag = transponderList.getCompound(i);
            if (!transponderTag.hasUUID(TRANSPONDER_ID) || !transponderTag.contains(LINKED_CARGO, Tag.TAG_LIST)) {
                continue;
            }
            ListTag cargoList = transponderTag.getList(LINKED_CARGO, Tag.TAG_COMPOUND);
            List<LinkedCargoEntry> entries = cargoList.stream()
                    .filter(CompoundTag.class::isInstance)
                    .map(CompoundTag.class::cast)
                    .map(LinkedCargoEntry::read)
                    .flatMap(java.util.Optional::stream)
                    .toList();
            if (!entries.isEmpty()) {
                data.cargoByTransponder.put(
                        transponderTag.getUUID(TRANSPONDER_ID),
                        new StoredCargo(transponderTag.getBoolean(RELATIVE), entries)
                );
            }
        }
        CreateAeronauticsAutomatedLogistics.debugCargo(
                "TransponderCargoSavedData load loadedTransponders={}",
                data.cargoByTransponder.size()
        );
        return data;
    }

    private record StoredCargo(boolean relative, List<LinkedCargoEntry> entries) {
    }
}

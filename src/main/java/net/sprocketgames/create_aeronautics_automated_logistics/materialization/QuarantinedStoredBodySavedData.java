package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Records stored-body pointers removed from Sable's active index without deleting the stored payload. */
public final class QuarantinedStoredBodySavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_quarantined_stored_bodies";
    private static final int CURRENT_DATA_VERSION = 1;

    private static final String DATA_VERSION = "dataVersion";
    private static final String ENTRIES = "entries";
    private static final String KEY = "key";
    private static final String DIMENSION = "dimension";
    private static final String SABLE_SHIP_ID = "sableShipId";
    private static final String POINTER = "pointer";
    private static final String REASON = "reason";
    private static final String GAME_TIME = "gameTime";

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static SavedData.Factory<QuarantinedStoredBodySavedData> factory() {
        return new SavedData.Factory<>(QuarantinedStoredBodySavedData::new, QuarantinedStoredBodySavedData::load);
    }

    public static QuarantinedStoredBodySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public void record(
            ResourceKey<Level> dimension,
            UUID sableShipId,
            StoredBodyPointer pointer,
            String reason,
            long gameTime
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(sableShipId, "sableShipId");
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(reason, "reason");
        Entry entry = new Entry(dimension, sableShipId, pointer, reason, gameTime);
        Entry previous = entries.put(entry.key(), entry);
        if (!entry.equals(previous)) {
            setDirty();
        }
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(KEY, entry.key());
            entryTag.putString(DIMENSION, entry.dimension().location().toString());
            entryTag.putUUID(SABLE_SHIP_ID, entry.sableShipId());
            entryTag.putString(POINTER, entry.pointer().selector());
            entryTag.putString(REASON, entry.reason());
            entryTag.putLong(GAME_TIME, entry.gameTime());
            list.add(entryTag);
        }
        tag.put(ENTRIES, list);
        return tag;
    }

    private static QuarantinedStoredBodySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuarantinedStoredBodySavedData data = new QuarantinedStoredBodySavedData();
        if (!tag.contains(ENTRIES, Tag.TAG_LIST)) {
            return data;
        }
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            readEntry(list.getCompound(i)).ifPresent(entry -> data.entries.put(entry.key(), entry));
        }
        return data;
    }

    private static java.util.Optional<Entry> readEntry(CompoundTag tag) {
        if (!tag.contains(DIMENSION, Tag.TAG_STRING)
                || !tag.hasUUID(SABLE_SHIP_ID)
                || !tag.contains(POINTER, Tag.TAG_STRING)) {
            return java.util.Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        java.util.Optional<StoredBodyPointer> pointer = StoredBodyPointer.parse(tag.getString(POINTER));
        if (dimensionId == null || pointer.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Entry(
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                tag.getUUID(SABLE_SHIP_ID),
                pointer.get(),
                tag.contains(REASON, Tag.TAG_STRING) ? tag.getString(REASON) : "unknown",
                tag.contains(GAME_TIME, Tag.TAG_ANY_NUMERIC) ? tag.getLong(GAME_TIME) : -1L
        ));
    }

    public record Entry(
            ResourceKey<Level> dimension,
            UUID sableShipId,
            StoredBodyPointer pointer,
            String reason,
            long gameTime
    ) {
        public Entry {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(sableShipId, "sableShipId");
            Objects.requireNonNull(pointer, "pointer");
            reason = Objects.requireNonNull(reason, "reason");
        }

        private String key() {
            return dimension.location() + "|" + sableShipId + "|" + pointer.selector();
        }
    }
}

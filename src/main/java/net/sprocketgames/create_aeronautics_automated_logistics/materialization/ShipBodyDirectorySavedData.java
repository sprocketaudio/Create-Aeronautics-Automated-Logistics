package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent identity metadata for Sable bodies owned by logistics transponders.
 * This directory records verified observations; it never loads, moves, or deletes a body.
 */
public final class ShipBodyDirectorySavedData extends SavedData {
    private static final String DATA_NAME = "create_aeronautics_automated_logistics_ship_body_directory";
    private static final int CURRENT_DATA_VERSION = 1;
    private static final long VERIFICATION_HEARTBEAT_TICKS = 200L;

    private static final String DATA_VERSION = "dataVersion";
    private static final String MIGRATION_VERSION = "migrationVersion";
    private static final String ENTRIES = "entries";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SABLE_SHIP_ID = "sableShipId";
    private static final String DIMENSION = "dimension";
    private static final String LOCAL_CONTROLLER_POS = "localControllerPos";
    private static final String TRACKING_POINT_ID = "trackingPointId";
    private static final String POINTER = "canonicalPointer";
    private static final String CHUNK_X = "chunkX";
    private static final String CHUNK_Z = "chunkZ";
    private static final String STORAGE_INDEX = "storageIndex";
    private static final String SUBLEVEL_INDEX = "subLevelIndex";
    private static final String VERIFICATION_STATE = "verificationState";
    private static final String LAST_VERIFIED = "lastVerifiedGameTime";

    private final Map<UUID, BodyIdentity> entriesByTransponder = new LinkedHashMap<>();
    private int migrationVersion;

    public static SavedData.Factory<ShipBodyDirectorySavedData> factory() {
        return new SavedData.Factory<>(ShipBodyDirectorySavedData::new, ShipBodyDirectorySavedData::load);
    }

    public static ShipBodyDirectorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static void observeLiveBody(
            MinecraftServer server,
            UUID transponderId,
            UUID sableShipId,
            ResourceKey<Level> dimension,
            BlockPos localControllerPos,
            Optional<UUID> trackingPointId,
            Optional<GlobalSavedSubLevelPointer> serializationPointer,
            long gameTime
    ) {
        get(server).observeLiveBody(
                transponderId,
                sableShipId,
                dimension,
                localControllerPos,
                trackingPointId,
                serializationPointer.map(StoredBodyPointer::fromSable),
                gameTime
        );
    }

    public static void removeForDeletedTransponder(MinecraftServer server, UUID transponderId) {
        ShipBodyDirectorySavedData data = get(server);
        if (data.entriesByTransponder.remove(transponderId) != null) {
            data.setDirty();
        }
    }

    public static boolean observeOwnershipState(
            MinecraftServer server,
            UUID transponderId,
            UUID sableShipId,
            ResourceKey<Level> dimension,
            BlockPos localControllerPos,
            Optional<StoredBodyPointer> canonicalPointer,
            VerificationState verificationState,
            long gameTime
    ) {
        return get(server).observeOwnershipState(
                transponderId,
                sableShipId,
                dimension,
                localControllerPos,
                canonicalPointer,
                verificationState,
                gameTime
        );
    }

    public static boolean selectCanonicalPointer(
            MinecraftServer server,
            UUID transponderId,
            UUID expectedSableShipId,
            StoredBodyPointer pointer,
            long gameTime
    ) {
        ShipBodyDirectorySavedData data = get(server);
        BodyIdentity previous = data.entriesByTransponder.get(transponderId);
        if (previous == null || !previous.sableShipId().equals(expectedSableShipId)) {
            return false;
        }
        BodyIdentity selected = new BodyIdentity(
                previous.transponderId(),
                previous.sableShipId(),
                previous.dimension(),
                previous.localControllerPos(),
                previous.trackingPointId(),
                Optional.of(pointer),
                VerificationState.ADMIN_SELECTED,
                gameTime
        );
        if (!selected.equals(previous)) {
            data.entriesByTransponder.put(transponderId, selected);
            data.setDirty();
        }
        return true;
    }

    static boolean migrateStoredBody(
            MinecraftServer server,
            UUID transponderId,
            UUID sableShipId,
            ResourceKey<Level> dimension,
            BlockPos localControllerPos,
            Optional<StoredBodyPointer> canonicalPointer,
            VerificationState verificationState,
            long gameTime
    ) {
        ShipBodyDirectorySavedData data = get(server);
        if (data.entriesByTransponder.containsKey(transponderId)) {
            return false;
        }
        data.entriesByTransponder.put(transponderId, new BodyIdentity(
                transponderId,
                sableShipId,
                dimension,
                localControllerPos,
                Optional.empty(),
                canonicalPointer,
                verificationState,
                gameTime
        ));
        data.setDirty();
        return true;
    }

    int migrationVersion() {
        return migrationVersion;
    }

    void completeMigration(int version) {
        if (migrationVersion >= version) {
            return;
        }
        migrationVersion = version;
        setDirty();
    }

    public Optional<BodyIdentity> byTransponder(UUID transponderId) {
        return Optional.ofNullable(entriesByTransponder.get(transponderId));
    }

    public List<BodyIdentity> bySableShipId(UUID sableShipId) {
        return entriesByTransponder.values().stream()
                .filter(entry -> entry.sableShipId().equals(sableShipId))
                .sorted(Comparator.comparing(entry -> entry.transponderId().toString()))
                .toList();
    }

    public List<BodyIdentity> entries() {
        return entriesByTransponder.values().stream()
                .sorted(Comparator.comparing(entry -> entry.transponderId().toString()))
                .toList();
    }

    private void observeLiveBody(
            UUID transponderId,
            UUID sableShipId,
            ResourceKey<Level> dimension,
            BlockPos localControllerPos,
            Optional<UUID> observedTrackingPointId,
            Optional<StoredBodyPointer> serializationPointer,
            long gameTime
    ) {
        Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(sableShipId, "sableShipId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(localControllerPos, "localControllerPos");
        Objects.requireNonNull(observedTrackingPointId, "observedTrackingPointId");
        Objects.requireNonNull(serializationPointer, "serializationPointer");

        BodyIdentity previous = entriesByTransponder.get(transponderId);
        boolean sameBody = previous != null
                && previous.sableShipId().equals(sableShipId)
                && previous.dimension().equals(dimension);
        Optional<UUID> trackingPointId = observedTrackingPointId.isPresent()
                ? observedTrackingPointId
                : sameBody ? previous.trackingPointId() : Optional.empty();
        Optional<StoredBodyPointer> canonicalPointer = serializationPointer.isPresent()
                ? serializationPointer
                : sameBody ? previous.canonicalPointer() : Optional.empty();
        long lastVerified = previous != null
                && sameBody
                && previous.verificationState() == VerificationState.LIVE_VERIFIED
                && previous.localControllerPos().equals(localControllerPos)
                && previous.canonicalPointer().equals(canonicalPointer)
                && gameTime - previous.lastVerifiedGameTime() < VERIFICATION_HEARTBEAT_TICKS
                ? previous.lastVerifiedGameTime()
                : gameTime;

        BodyIdentity updated = new BodyIdentity(
                transponderId,
                sableShipId,
                dimension,
                localControllerPos.immutable(),
                trackingPointId,
                canonicalPointer,
                VerificationState.LIVE_VERIFIED,
                lastVerified
        );
        if (!updated.equals(previous)) {
            entriesByTransponder.put(transponderId, updated);
            setDirty();
        }
    }

    private boolean observeOwnershipState(
            UUID transponderId,
            UUID sableShipId,
            ResourceKey<Level> dimension,
            BlockPos localControllerPos,
            Optional<StoredBodyPointer> observedCanonicalPointer,
            VerificationState verificationState,
            long gameTime
    ) {
        Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(sableShipId, "sableShipId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(localControllerPos, "localControllerPos");
        observedCanonicalPointer = Objects.requireNonNull(observedCanonicalPointer, "observedCanonicalPointer");
        Objects.requireNonNull(verificationState, "verificationState");

        BodyIdentity previous = entriesByTransponder.get(transponderId);
        boolean sameBody = previous != null
                && previous.sableShipId().equals(sableShipId)
                && previous.dimension().equals(dimension);
        Optional<StoredBodyPointer> canonicalPointer = observedCanonicalPointer.isPresent()
                ? observedCanonicalPointer
                : sameBody ? previous.canonicalPointer() : Optional.empty();
        Optional<UUID> trackingPointId = sameBody ? previous.trackingPointId() : Optional.empty();
        BodyIdentity updated = new BodyIdentity(
                transponderId,
                sableShipId,
                dimension,
                localControllerPos.immutable(),
                trackingPointId,
                canonicalPointer,
                verificationState,
                gameTime
        );
        if (!updated.equals(previous)) {
            entriesByTransponder.put(transponderId, updated);
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putInt(MIGRATION_VERSION, migrationVersion);
        ListTag entries = new ListTag();
        for (BodyIdentity entry : entries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TRANSPONDER_ID, entry.transponderId());
            entryTag.putUUID(SABLE_SHIP_ID, entry.sableShipId());
            entryTag.putString(DIMENSION, entry.dimension().location().toString());
            entryTag.put(LOCAL_CONTROLLER_POS, NbtUtils.writeBlockPos(entry.localControllerPos()));
            entry.trackingPointId().ifPresent(id -> entryTag.putUUID(TRACKING_POINT_ID, id));
            entry.canonicalPointer().ifPresent(pointer -> entryTag.put(POINTER, writePointer(pointer)));
            entryTag.putString(VERIFICATION_STATE, entry.verificationState().name());
            entryTag.putLong(LAST_VERIFIED, entry.lastVerifiedGameTime());
            entries.add(entryTag);
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    private static ShipBodyDirectorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipBodyDirectorySavedData data = new ShipBodyDirectorySavedData();
        data.migrationVersion = tag.contains(MIGRATION_VERSION, Tag.TAG_ANY_NUMERIC)
                ? Math.max(0, tag.getInt(MIGRATION_VERSION))
                : 0;
        if (!tag.contains(ENTRIES, Tag.TAG_LIST)) {
            return data;
        }
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            readEntry(entries.getCompound(i)).ifPresent(entry ->
                    data.entriesByTransponder.put(entry.transponderId(), entry));
        }
        return data;
    }

    private static Optional<BodyIdentity> readEntry(CompoundTag tag) {
        if (!tag.hasUUID(TRANSPONDER_ID)
                || !tag.hasUUID(SABLE_SHIP_ID)
                || !tag.contains(DIMENSION, Tag.TAG_STRING)
                || !tag.contains(LOCAL_CONTROLLER_POS, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        Optional<BlockPos> localControllerPos = NbtUtils.readBlockPos(tag, LOCAL_CONTROLLER_POS);
        if (dimensionId == null || localControllerPos.isEmpty()) {
            return Optional.empty();
        }
        VerificationState state;
        try {
            state = VerificationState.valueOf(tag.getString(VERIFICATION_STATE));
        } catch (IllegalArgumentException ignored) {
            state = VerificationState.UNVERIFIED;
        }
        return Optional.of(new BodyIdentity(
                tag.getUUID(TRANSPONDER_ID),
                tag.getUUID(SABLE_SHIP_ID),
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                localControllerPos.get().immutable(),
                tag.hasUUID(TRACKING_POINT_ID) ? Optional.of(tag.getUUID(TRACKING_POINT_ID)) : Optional.empty(),
                tag.contains(POINTER, Tag.TAG_COMPOUND)
                        ? readPointer(tag.getCompound(POINTER))
                        : Optional.empty(),
                state,
                tag.contains(LAST_VERIFIED, Tag.TAG_ANY_NUMERIC) ? tag.getLong(LAST_VERIFIED) : -1L
        ));
    }

    private static CompoundTag writePointer(StoredBodyPointer pointer) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(CHUNK_X, pointer.chunkPos().x);
        tag.putInt(CHUNK_Z, pointer.chunkPos().z);
        tag.putShort(STORAGE_INDEX, pointer.storageIndex());
        tag.putShort(SUBLEVEL_INDEX, pointer.subLevelIndex());
        return tag;
    }

    private static Optional<StoredBodyPointer> readPointer(CompoundTag tag) {
        if (!tag.contains(CHUNK_X, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(CHUNK_Z, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(STORAGE_INDEX, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(SUBLEVEL_INDEX, Tag.TAG_ANY_NUMERIC)) {
            return Optional.empty();
        }
        return Optional.of(new StoredBodyPointer(
                new ChunkPos(tag.getInt(CHUNK_X), tag.getInt(CHUNK_Z)),
                tag.getShort(STORAGE_INDEX),
                tag.getShort(SUBLEVEL_INDEX)
        ));
    }

    public record BodyIdentity(
            UUID transponderId,
            UUID sableShipId,
            ResourceKey<Level> dimension,
            BlockPos localControllerPos,
            Optional<UUID> trackingPointId,
            Optional<StoredBodyPointer> canonicalPointer,
            VerificationState verificationState,
            long lastVerifiedGameTime
    ) {
        public BodyIdentity {
            Objects.requireNonNull(transponderId, "transponderId");
            Objects.requireNonNull(sableShipId, "sableShipId");
            Objects.requireNonNull(dimension, "dimension");
            localControllerPos = Objects.requireNonNull(localControllerPos, "localControllerPos").immutable();
            trackingPointId = Objects.requireNonNull(trackingPointId, "trackingPointId");
            canonicalPointer = Objects.requireNonNull(canonicalPointer, "canonicalPointer");
            Objects.requireNonNull(verificationState, "verificationState");
        }
    }

    public enum VerificationState {
        UNVERIFIED,
        LIVE_VERIFIED,
        STORED_VERIFIED,
        ADMIN_SELECTED,
        HELD_BY_SABLE,
        LOADING,
        FAULTED,
        AMBIGUOUS,
        MISSING,
        CORRUPT
    }
}

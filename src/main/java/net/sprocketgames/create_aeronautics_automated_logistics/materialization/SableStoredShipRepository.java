package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

/** Read-only discovery and canonical selection for stored Sable bodies. */
public final class SableStoredShipRepository {
    private static final long SCAN_CACHE_TICKS = 200L;
    private static final long SCAN_LOG_HEARTBEAT_TICKS = 1200L;
    private static final Map<MinecraftServer, ScanCache> CACHE_BY_SERVER = new WeakHashMap<>();
    private static final Map<MinecraftServer, ScanLogState> SCAN_LOGS_BY_SERVER = new WeakHashMap<>();

    private SableStoredShipRepository() {
    }

    public static StoredBodyLookupResult lookup(
            MinecraftServer server,
            Optional<UUID> transponderId,
            ResourceKey<Level> dimension,
            UUID sableShipId
    ) {
        Objects.requireNonNull(server, "server");
        transponderId = Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(sableShipId, "sableShipId");

        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = resolveIdentity(
                server,
                transponderId,
                dimension,
                sableShipId
        );
        Optional<StoredBodyPointer> trackingPointer = identity.flatMap(entry -> trackingPointer(server, entry));
        Optional<StoredBodyPointer> directoryPointer = identity.flatMap(ShipBodyDirectorySavedData.BodyIdentity::canonicalPointer);
        boolean adminSelected = identity
                .map(ShipBodyDirectorySavedData.BodyIdentity::verificationState)
                .filter(ShipBodyDirectorySavedData.VerificationState.ADMIN_SELECTED::equals)
                .isPresent();

        List<StoredBodyCandidate> candidates = matchingCandidates(server, dimension, sableShipId, false);
        StoredBodyLookupResult result = evaluate(candidates, trackingPointer, directoryPointer, adminSelected);
        if (result.status() == StoredBodyLookupResult.Status.NOT_FOUND
                || result.status() == StoredBodyLookupResult.Status.READ_FAILED) {
            candidates = matchingCandidates(server, dimension, sableShipId, true);
            result = evaluate(candidates, trackingPointer, directoryPointer, adminSelected);
        }
        return result;
    }

    public static List<StoredBodyCandidate> candidates(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId
    ) {
        return matchingCandidates(server, dimension, sableShipId, false);
    }

    public static List<StoredBodyCandidate> allCandidates(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return allCandidates(server, false);
    }

    public static List<DanglingStoredBodyIndex> danglingIndexes(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        List<DanglingStoredBodyIndex> dangling = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null || container.getHoldingChunkMap() == null) {
                continue;
            }
            SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
            File[] regionFiles = storage.getFolder().toFile().listFiles((directory, name) -> name.endsWith(".slvlr"));
            if (regionFiles == null) {
                continue;
            }
            for (File regionFile : regionFiles) {
                scanDanglingRegionFile(level, storage, regionFile, dangling);
            }
        }
        return List.copyOf(dangling);
    }

    public static boolean quarantineDanglingIndex(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            StoredBodyPointer pointer,
            String reason
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(reason, "reason");

        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Dangling stored-body index quarantine refused: dimension={} pointer={} reason={} proof=dimension_unavailable action=retained_index",
                    dimension.location(),
                    pointer,
                    reason
            );
            return false;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null || container.getHoldingChunkMap() == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Dangling stored-body index quarantine refused: dimension={} pointer={} reason={} proof=sable_container_unavailable action=retained_index",
                    dimension.location(),
                    pointer,
                    reason
            );
            return false;
        }

        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(pointer.chunkPos());
        if (holdingChunk == null || !holdingChunk.getSubLevelPointers().contains(pointer.toSable().local())) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Dangling stored-body index quarantine refused: dimension={} pointer={} reason={} proof=pointer_not_indexed action=retained_index",
                    dimension.location(),
                    pointer,
                    reason
            );
            return false;
        }
        SubLevelData data = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.toSable().local());
        if (data != null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Dangling stored-body index quarantine refused: dimension={} pointer={} sableShip={} reason={} proof=payload_readable action=retained_index",
                    dimension.location(),
                    pointer,
                    data.uuid(),
                    reason
            );
            return false;
        }

        PointerIndexRemoval removal = removeDanglingPointerIndex(container.getHoldingChunkMap(), pointer);
        if (!removal.removedPointerIndex()) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Dangling stored-body index quarantine refused: dimension={} pointer={} reason={} proof=remove_failed action=retained_index",
                    dimension.location(),
                    pointer,
                    reason
            );
            return false;
        }
        invalidate(server);
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "Dangling stored-body index quarantine applied: dimension={} pointer={} reason={} proof=payload_missing payload=none action=index_removed liveHoldingRemoved={} liveCacheUpdated={}",
                dimension.location(),
                pointer,
                reason,
                removal.removedLiveHolding(),
                removal.updatedLiveCache()
        );
        return true;
    }

    public static void auditStartupStorage(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        int quarantined = quarantineVerifiedDuplicateIndexes(server, "startup_storage_audit");
        Map<StoredBodyKey, List<StoredBodyCandidate>> grouped = new HashMap<>();
        for (StoredBodyCandidate candidate : allCandidates(server, true)) {
            grouped.computeIfAbsent(
                    new StoredBodyKey(candidate.dimension(), candidate.sableShipId()),
                    ignored -> new ArrayList<>()
            ).add(candidate);
        }

        int duplicateEntries = 0;
        int corruptEntries = 0;
        for (Map.Entry<StoredBodyKey, List<StoredBodyCandidate>> entry : grouped.entrySet()) {
            List<StoredBodyCandidate> candidates = entry.getValue();
            corruptEntries += (int) candidates.stream().filter(candidate -> !candidate.readable()).count();
            if (candidates.size() <= 1) {
                continue;
            }
            duplicateEntries += candidates.size();
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Startup Sable storage audit found duplicate candidates: dimension={} sableShip={} count={} pointers={} action=retained_all",
                    entry.getKey().dimension().location(),
                    entry.getKey().sableShipId(),
                    candidates.size(),
                    candidates.stream().map(StoredBodyCandidate::pointer).toList()
            );
        }
        if (duplicateEntries > 0 || corruptEntries > 0) {
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Startup Sable storage audit found {} duplicate candidate entries and {} structurally corrupt entries. quarantinedIndexes={} no stored body payload was deleted.",
                    duplicateEntries,
                    corruptEntries,
                    quarantined
            );
        }
    }

    public static int quarantineVerifiedDuplicateIndexes(MinecraftServer server, String reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reason, "reason");
        int quarantined = 0;
        Map<StoredBodyKey, List<StoredBodyCandidate>> grouped = new HashMap<>();
        for (StoredBodyCandidate candidate : allCandidates(server, true)) {
            grouped.computeIfAbsent(
                    new StoredBodyKey(candidate.dimension(), candidate.sableShipId()),
                    ignored -> new ArrayList<>()
            ).add(candidate);
        }
        for (Map.Entry<StoredBodyKey, List<StoredBodyCandidate>> entry : grouped.entrySet()) {
            List<StoredBodyCandidate> candidates = entry.getValue();
            if (candidates.stream().filter(StoredBodyCandidate::readable).count() <= 1L) {
                continue;
            }
            Optional<StoredBodyPointer> selected = canonicalPointerForDuplicateGroup(
                    server,
                    entry.getKey().dimension(),
                    entry.getKey().sableShipId(),
                    candidates
            );
            if (selected.isEmpty()) {
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                        "Stored-body duplicate quarantine refused: dimension={} sableShip={} candidates={} reason={} proof=insufficient action=retained_all",
                        entry.getKey().dimension().location(),
                        entry.getKey().sableShipId(),
                        candidates.stream().map(StoredBodyCandidate::pointer).toList(),
                        reason
                );
                continue;
            }
            quarantined += quarantineDuplicateIndexesExcept(
                    server,
                    entry.getKey().dimension(),
                    entry.getKey().sableShipId(),
                    selected.get(),
                    reason
            );
        }
        if (quarantined > 0) {
            invalidate(server);
        }
        return quarantined;
    }

    public static int quarantineDuplicateIndexesExcept(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            StoredBodyPointer keepPointer,
            String reason
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(sableShipId, "sableShipId");
        Objects.requireNonNull(keepPointer, "keepPointer");
        Objects.requireNonNull(reason, "reason");

        List<StoredBodyCandidate> candidates = matchingCandidates(server, dimension, sableShipId, true);
        long readableCount = candidates.stream().filter(StoredBodyCandidate::readable).count();
        if (readableCount <= 1L) {
            return 0;
        }
        if (candidates.stream().noneMatch(candidate -> candidate.pointer().equals(keepPointer) && candidate.readable())) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Stored-body duplicate quarantine refused: dimension={} sableShip={} keepPointer={} candidates={} reason={} proof=canonical_pointer_not_readable action=retained_all",
                    dimension.location(),
                    sableShipId,
                    keepPointer,
                    candidates.stream().map(StoredBodyCandidate::pointer).toList(),
                    reason
            );
            return 0;
        }

        int removed = 0;
        for (StoredBodyCandidate candidate : candidates) {
            if (candidate.pointer().equals(keepPointer) || !candidate.readable()) {
                continue;
            }
            if (removePointerIndex(server, candidate, reason)) {
                removed++;
            }
        }
        if (removed > 0) {
            invalidate(server);
        }
        return removed;
    }

    public static boolean quarantineMaterializedPointerIndex(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            StoredBodyPointer pointer,
            String reason
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(sableShipId, "sableShipId");
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(reason, "reason");

        return quarantinePointerIndexPreservingPayload(
                server,
                dimension,
                sableShipId,
                pointer,
                reason,
                "Stored-body materialized pointer index quarantine",
                "live_body_materialized",
                "removed_consumed_active_holding_index"
        );
    }

    public static boolean quarantineAdminPointerIndex(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            StoredBodyPointer pointer,
            String reason
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(sableShipId, "sableShipId");
        Objects.requireNonNull(pointer, "pointer");
        Objects.requireNonNull(reason, "reason");

        return quarantinePointerIndexPreservingPayload(
                server,
                dimension,
                sableShipId,
                pointer,
                reason,
                "Stored-body admin pointer index quarantine",
                "admin_selected_noncanonical_duplicate",
                "removed_from_active_holding_index"
        );
    }

    private static boolean quarantinePointerIndexPreservingPayload(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            StoredBodyPointer pointer,
            String reason,
            String event,
            String proof,
            String action
    ) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "{} refused: dimension={} sableShip={} pointer={} reason={} proof=dimension_unavailable action=retained_payload",
                    event,
                    dimension.location(),
                    sableShipId,
                    pointer,
                    reason
            );
            return false;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "{} refused: dimension={} sableShip={} pointer={} reason={} proof=sable_container_unavailable action=retained_payload",
                    event,
                    dimension.location(),
                    sableShipId,
                    pointer,
                    reason
            );
            return false;
        }
        PointerIndexRemoval removal = removePointerIndexPreservingPayload(
                container.getHoldingChunkMap(),
                pointer,
                sableShipId,
                event,
                reason
        );
        if (!removal.removedPointerIndex()) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "{} refused: dimension={} sableShip={} pointer={} reason={} proof=pointer_not_indexed action=retained_payload",
                    event,
                    dimension.location(),
                    sableShipId,
                    pointer,
                    reason
            );
            return false;
        }
        QuarantinedStoredBodySavedData.get(server).record(
                dimension,
                sableShipId,
                pointer,
                reason,
                server.overworld().getGameTime()
        );
        boolean removed = true;
        if (removed) {
            invalidate(server);
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "{} applied: dimension={} sableShip={} pointer={} reason={} proof={} payload=preserved action={} liveHoldingRemoved={} liveCacheUpdated={}",
                    event,
                    dimension.location(),
                    sableShipId,
                    pointer,
                    reason,
                    proof,
                    action,
                    removal.removedLiveHolding(),
                    removal.updatedLiveCache()
            );
        }
        return removed;
    }

    public static void invalidate(MinecraftServer server) {
        synchronized (CACHE_BY_SERVER) {
            CACHE_BY_SERVER.remove(server);
        }
    }

    private static StoredBodyLookupResult evaluate(
            List<StoredBodyCandidate> candidates,
            Optional<StoredBodyPointer> trackingPointer,
            Optional<StoredBodyPointer> directoryPointer,
            boolean adminSelected
    ) {
        List<StoredBodyCandidate> readable = candidates.stream()
                .filter(StoredBodyCandidate::readable)
                .toList();
        if (adminSelected) {
            Optional<StoredBodyCandidate> selected = directoryPointer.flatMap(pointer -> findByPointer(readable, pointer));
            if (selected.isPresent()) {
                return StoredBodyLookupResult.verifiedPointer(candidates, selected.get(), "admin_selected_pointer_verified");
            }
        }
        Optional<StoredBodyCandidate> tracked = trackingPointer.flatMap(pointer -> findByPointer(readable, pointer));
        if (tracked.isPresent()) {
            return StoredBodyLookupResult.verifiedPointer(candidates, tracked.get(), "sable_tracking_pointer_verified");
        }
        Optional<StoredBodyCandidate> recorded = directoryPointer.flatMap(pointer -> findByPointer(readable, pointer));
        if (recorded.isPresent()) {
            return StoredBodyLookupResult.verifiedPointer(candidates, recorded.get(), "directory_pointer_verified");
        }
        if (readable.size() == 1) {
            return StoredBodyLookupResult.verifiedSingle(readable.getFirst());
        }
        if (readable.size() > 1) {
            return StoredBodyLookupResult.unresolved(
                    StoredBodyLookupResult.Status.AMBIGUOUS,
                    candidates,
                    "multiple_readable_candidates_without_verified_pointer"
            );
        }
        if (!candidates.isEmpty()) {
            return StoredBodyLookupResult.unresolved(
                    StoredBodyLookupResult.Status.READ_FAILED,
                    candidates,
                    "matching_candidates_structurally_corrupt"
            );
        }
        return StoredBodyLookupResult.unresolved(
                StoredBodyLookupResult.Status.NOT_FOUND,
                List.of(),
                trackingPointer.isPresent() || directoryPointer.isPresent()
                        ? "verified_pointer_not_readable_after_rescan"
                        : "no_matching_stored_body_after_rescan"
        );
    }

    private static Optional<StoredBodyCandidate> findByPointer(
            List<StoredBodyCandidate> candidates,
            StoredBodyPointer pointer
    ) {
        return candidates.stream().filter(candidate -> candidate.pointer().equals(pointer)).findFirst();
    }

    private static Optional<StoredBodyPointer> canonicalPointerForDuplicateGroup(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            List<StoredBodyCandidate> candidates
    ) {
        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = resolveIdentity(
                server,
                Optional.empty(),
                dimension,
                sableShipId
        );
        Optional<StoredBodyPointer> trackingPointer = identity.flatMap(entry -> trackingPointer(server, entry));
        if (trackingPointer.flatMap(pointer -> findByPointer(candidates, pointer)).isPresent()) {
            return trackingPointer;
        }
        Optional<StoredBodyPointer> directoryPointer = identity.flatMap(ShipBodyDirectorySavedData.BodyIdentity::canonicalPointer);
        if (directoryPointer.flatMap(pointer -> findByPointer(candidates, pointer)).isPresent()) {
            return directoryPointer;
        }
        return Optional.empty();
    }

    private static boolean removePointerIndex(MinecraftServer server, StoredBodyCandidate candidate, String reason) {
        ServerLevel level = server.getLevel(candidate.dimension());
        if (level == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Stored-body duplicate quarantine refused: dimension={} sableShip={} pointer={} reason={} proof=dimension_unloaded action=retained",
                    candidate.dimension().location(),
                    candidate.sableShipId(),
                    candidate.pointer(),
                    reason
            );
            return false;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null || container.getHoldingChunkMap() == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Stored-body duplicate quarantine refused: dimension={} sableShip={} pointer={} reason={} proof=sable_container_unavailable action=retained",
                    candidate.dimension().location(),
                    candidate.sableShipId(),
                    candidate.pointer(),
                    reason
            );
            return false;
        }
        PointerIndexRemoval removal = removePointerIndexPreservingPayload(
                container.getHoldingChunkMap(),
                candidate.pointer(),
                candidate.sableShipId(),
                "Stored-body duplicate quarantine",
                reason
        );
        if (!removal.removedPointerIndex()) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Stored-body duplicate quarantine refused: dimension={} sableShip={} pointer={} reason={} proof=pointer_not_indexed action=retained_payload",
                    candidate.dimension().location(),
                    candidate.sableShipId(),
                    candidate.pointer(),
                    reason
            );
            return false;
        }
        QuarantinedStoredBodySavedData.get(server).record(
                candidate.dimension(),
                candidate.sableShipId(),
                candidate.pointer(),
                reason,
                server.overworld().getGameTime()
        );
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "Stored-body duplicate quarantine applied: dimension={} sableShip={} pointer={} reason={} proof=canonical_duplicate payload=preserved action=removed_from_active_holding_index liveHoldingRemoved={} liveCacheUpdated={}",
                candidate.dimension().location(),
                candidate.sableShipId(),
                candidate.pointer(),
                reason,
                removal.removedLiveHolding(),
                removal.updatedLiveCache()
        );
        return true;
    }

    private static PointerIndexRemoval removePointerIndexPreservingPayload(
            SubLevelHoldingChunkMap holdingMap,
            StoredBodyPointer pointer,
            UUID sableShipId,
            String event,
            String reason
    ) {
        try {
            Method getOrLoad = SubLevelHoldingChunkMap.class.getDeclaredMethod(
                    "getOrLoadHoldingChunk",
                    ChunkPos.class,
                    boolean.class
            );
            getOrLoad.setAccessible(true);
            SubLevelHoldingChunk holdingChunk = (SubLevelHoldingChunk) getOrLoad.invoke(
                    holdingMap,
                    pointer.chunkPos(),
                    false
            );
            if (holdingChunk == null) {
                return PointerIndexRemoval.NONE;
            }

            boolean removedPointer = holdingChunk.getSubLevelPointers().remove(pointer.toSable().local());
            boolean removedLiveHolding = removeLoadedHoldingSubLevel(holdingMap, holdingChunk, pointer, sableShipId);
            if (removedPointer || removedLiveHolding) {
                Method setDirty = SubLevelHoldingChunkMap.class.getDeclaredMethod("setDirty", ChunkPos.class);
                setDirty.setAccessible(true);
                setDirty.invoke(holdingMap, pointer.chunkPos());
                holdingMap.getStorage().attemptSaveHoldingChunk(pointer.chunkPos(), holdingChunk);
                return new PointerIndexRemoval(removedPointer, removedLiveHolding, true);
            }
            return PointerIndexRemoval.NONE;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "{} live-cache update unavailable: pointer={} sableShip={} reason={} fallback=storage_only error={}",
                    event,
                    pointer,
                    sableShipId,
                    reason,
                    exception.toString()
            );
            return removePointerIndexFromStorageOnly(holdingMap, pointer);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean removeLoadedHoldingSubLevel(
            SubLevelHoldingChunkMap holdingMap,
            SubLevelHoldingChunk holdingChunk,
            StoredBodyPointer pointer,
            UUID sableShipId
    ) throws ReflectiveOperationException {
        Field loadedByChunkField = SubLevelHoldingChunk.class.getDeclaredField("loadedHoldingSubLevels");
        loadedByChunkField.setAccessible(true);
        Map<UUID, HoldingSubLevel> loadedByChunk = (Map<UUID, HoldingSubLevel>) loadedByChunkField.get(holdingChunk);

        Field allHoldingField = SubLevelHoldingChunkMap.class.getDeclaredField("allHoldingSubLevels");
        allHoldingField.setAccessible(true);
        Map<UUID, HoldingSubLevel> allHolding = (Map<UUID, HoldingSubLevel>) allHoldingField.get(holdingMap);

        boolean removed = false;
        Iterator<Map.Entry<UUID, HoldingSubLevel>> iterator = loadedByChunk.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HoldingSubLevel> entry = iterator.next();
            HoldingSubLevel holding = entry.getValue();
            boolean sameShip = entry.getKey().equals(sableShipId) || holding.data().uuid().equals(sableShipId);
            boolean samePointer = Objects.equals(holding.pointer(), pointer.toSable());
            if (!sameShip && !samePointer) {
                continue;
            }
            iterator.remove();
            allHolding.remove(entry.getKey());
            removed = true;
        }
        allHolding.remove(sableShipId);
        return removed;
    }

    private static PointerIndexRemoval removePointerIndexFromStorageOnly(
            SubLevelHoldingChunkMap holdingMap,
            StoredBodyPointer pointer
    ) {
        SubLevelStorage storage = holdingMap.getStorage();
        SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(pointer.chunkPos());
        if (holdingChunk == null || !holdingChunk.getSubLevelPointers().remove(pointer.toSable().local())) {
            return PointerIndexRemoval.NONE;
        }
        storage.attemptSaveHoldingChunk(pointer.chunkPos(), holdingChunk);
        return new PointerIndexRemoval(true, false, false);
    }

    private static PointerIndexRemoval removeDanglingPointerIndex(
            SubLevelHoldingChunkMap holdingMap,
            StoredBodyPointer pointer
    ) {
        try {
            Method getOrLoad = SubLevelHoldingChunkMap.class.getDeclaredMethod(
                    "getOrLoadHoldingChunk",
                    ChunkPos.class,
                    boolean.class
            );
            getOrLoad.setAccessible(true);
            SubLevelHoldingChunk holdingChunk = (SubLevelHoldingChunk) getOrLoad.invoke(
                    holdingMap,
                    pointer.chunkPos(),
                    false
            );
            if (holdingChunk == null) {
                return removePointerIndexFromStorageOnly(holdingMap, pointer);
            }

            boolean removedPointer = holdingChunk.getSubLevelPointers().remove(pointer.toSable().local());
            if (!removedPointer) {
                return PointerIndexRemoval.NONE;
            }
            Method setDirty = SubLevelHoldingChunkMap.class.getDeclaredMethod("setDirty", ChunkPos.class);
            setDirty.setAccessible(true);
            setDirty.invoke(holdingMap, pointer.chunkPos());
            holdingMap.getStorage().attemptSaveHoldingChunk(pointer.chunkPos(), holdingChunk);
            return new PointerIndexRemoval(true, false, true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Dangling stored-body index live-cache update unavailable: pointer={} fallback=storage_only error={}",
                    pointer,
                    exception.toString()
            );
            return removePointerIndexFromStorageOnly(holdingMap, pointer);
        }
    }

    private record PointerIndexRemoval(boolean removedPointerIndex, boolean removedLiveHolding, boolean updatedLiveCache) {
        private static final PointerIndexRemoval NONE = new PointerIndexRemoval(false, false, false);
    }

    public record DanglingStoredBodyIndex(
            ResourceKey<Level> dimension,
            StoredBodyPointer pointer,
            String reason
    ) {
        public DanglingStoredBodyIndex {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pointer, "pointer");
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    private static Optional<ShipBodyDirectorySavedData.BodyIdentity> resolveIdentity(
            MinecraftServer server,
            Optional<UUID> transponderId,
            ResourceKey<Level> dimension,
            UUID sableShipId
    ) {
        ShipBodyDirectorySavedData directory = ShipBodyDirectorySavedData.get(server);
        Optional<ShipBodyDirectorySavedData.BodyIdentity> exact = transponderId
                .flatMap(directory::byTransponder)
                .filter(entry -> entry.sableShipId().equals(sableShipId))
                .filter(entry -> entry.dimension().equals(dimension));
        if (exact.isPresent()) {
            return exact;
        }
        List<ShipBodyDirectorySavedData.BodyIdentity> matches = directory.bySableShipId(sableShipId).stream()
                .filter(entry -> entry.dimension().equals(dimension))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static Optional<StoredBodyPointer> trackingPointer(
            MinecraftServer server,
            ShipBodyDirectorySavedData.BodyIdentity identity
    ) {
        ServerLevel level = server.getLevel(identity.dimension());
        if (level == null || identity.trackingPointId().isEmpty()) {
            return Optional.empty();
        }
        TrackingPoint point = SubLevelTrackingPointSavedData.getOrLoad(level)
                .getTrackingPoint(identity.trackingPointId().get());
        if (point == null
                || !point.inSubLevel()
                || !identity.sableShipId().equals(point.subLevelID())
                || point.lastSavedSubLevelPointer() == null) {
            return Optional.empty();
        }
        return Optional.of(StoredBodyPointer.fromSable(point.lastSavedSubLevelPointer()));
    }

    private static List<StoredBodyCandidate> matchingCandidates(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            boolean forceRefresh
    ) {
        return allCandidates(server, forceRefresh).stream()
                .filter(candidate -> candidate.dimension().equals(dimension))
                .filter(candidate -> candidate.sableShipId().equals(sableShipId))
                .sorted(Comparator.comparing(candidate -> candidate.pointer().toString()))
                .toList();
    }

    private static List<StoredBodyCandidate> allCandidates(MinecraftServer server, boolean forceRefresh) {
        long gameTime = server.overworld().getGameTime();
        synchronized (CACHE_BY_SERVER) {
            ScanCache cached = CACHE_BY_SERVER.get(server);
            if (!forceRefresh && cached != null && gameTime - cached.gameTime() <= SCAN_CACHE_TICKS) {
                if (!cached.cacheReuseLogged()) {
                    cached.markCacheReuseLogged();
                    CreateAeronauticsAutomatedLogistics.debugVehicle(
                            "Stored-body repository scan reused: source=cache ageTicks={} candidates={}",
                            Math.max(0L, gameTime - cached.gameTime()),
                            cached.candidates().size()
                    );
                }
                return cached.candidates();
            }
        }
        List<StoredBodyCandidate> scanned = scan(server);
        logScanSummary(server, forceRefresh, scanned);
        synchronized (CACHE_BY_SERVER) {
            CACHE_BY_SERVER.put(server, new ScanCache(gameTime, scanned));
        }
        return scanned;
    }

    private static void logScanSummary(
            MinecraftServer server,
            boolean forceRefresh,
            List<StoredBodyCandidate> scanned
    ) {
        long gameTime = server.overworld().getGameTime();
        long readable = scanned.stream().filter(StoredBodyCandidate::readable).count();
        long corrupt = scanned.stream().filter(candidate -> !candidate.readable()).count();
        String signature = scanned.size() + "|" + readable + "|" + corrupt;
        synchronized (SCAN_LOGS_BY_SERVER) {
            ScanLogState previous = SCAN_LOGS_BY_SERVER.get(server);
            if (!forceRefresh
                    && previous != null
                    && previous.signature().equals(signature)
                    && gameTime - previous.gameTime() < SCAN_LOG_HEARTBEAT_TICKS) {
                return;
            }
            SCAN_LOGS_BY_SERVER.put(server, new ScanLogState(signature, gameTime));
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Stored-body repository scan completed: source=fresh_rescan forced={} candidates={} readable={} corrupt={}",
                forceRefresh,
                scanned.size(),
                readable,
                corrupt
        );
    }

    private static List<StoredBodyCandidate> scan(MinecraftServer server) {
        List<StoredBodyCandidate> discovered = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
            if (holdingChunkMap == null) {
                continue;
            }
            SubLevelStorage storage = holdingChunkMap.getStorage();
            File[] regionFiles = storage.getFolder().toFile().listFiles((directory, name) -> name.endsWith(".slvlr"));
            if (regionFiles == null) {
                continue;
            }
            for (File regionFile : regionFiles) {
                scanRegionFile(level, storage, regionFile, discovered);
            }
        }
        return List.copyOf(discovered);
    }

    private static void scanRegionFile(
            ServerLevel level,
            SubLevelStorage storage,
            File regionFile,
            List<StoredBodyCandidate> discovered
    ) {
        String fileName = regionFile.getName();
        String trimmed = fileName.substring(0, fileName.length() - ".slvlr".length());
        String[] parts = trimmed.split("\\.");
        if (parts.length != 3) {
            return;
        }
        final int regionX;
        final int regionZ;
        try {
            regionX = Integer.parseInt(parts[1]);
            regionZ = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return;
        }

        for (int x = 0; x < SubLevelRegionFile.SIDE_LENGTH; x++) {
            for (int z = 0; z < SubLevelRegionFile.SIDE_LENGTH; z++) {
                ChunkPos chunkPos = new ChunkPos(
                        regionX * SubLevelRegionFile.SIDE_LENGTH + x,
                        regionZ * SubLevelRegionFile.SIDE_LENGTH + z
                );
                SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
                if (holdingChunk == null) {
                    continue;
                }
                for (SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                    SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                    if (data != null) {
                        discovered.add(toCandidate(level, chunkPos, pointer, data));
                    }
                }
            }
        }
    }

    private static void scanDanglingRegionFile(
            ServerLevel level,
            SubLevelStorage storage,
            File regionFile,
            List<DanglingStoredBodyIndex> dangling
    ) {
        String fileName = regionFile.getName();
        String trimmed = fileName.substring(0, fileName.length() - ".slvlr".length());
        String[] parts = trimmed.split("\\.");
        if (parts.length != 3) {
            return;
        }
        final int regionX;
        final int regionZ;
        try {
            regionX = Integer.parseInt(parts[1]);
            regionZ = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return;
        }

        for (int x = 0; x < SubLevelRegionFile.SIDE_LENGTH; x++) {
            for (int z = 0; z < SubLevelRegionFile.SIDE_LENGTH; z++) {
                ChunkPos chunkPos = new ChunkPos(
                        regionX * SubLevelRegionFile.SIDE_LENGTH + x,
                        regionZ * SubLevelRegionFile.SIDE_LENGTH + z
                );
                SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
                if (holdingChunk == null) {
                    continue;
                }
                for (SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                    SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                    if (data == null) {
                        dangling.add(new DanglingStoredBodyIndex(
                                level.dimension(),
                                new StoredBodyPointer(chunkPos, pointer.storageIndex(), pointer.subLevelIndex()),
                                "payload_missing_or_unreadable"
                        ));
                    }
                }
            }
        }
    }

    private static StoredBodyCandidate toCandidate(
            ServerLevel level,
            ChunkPos chunkPos,
            SavedSubLevelPointer pointer,
            SubLevelData data
    ) {
        CompoundTag fullTag = data.fullTag().copy();
        String displayName = fullTag.contains("display_name")
                ? fullTag.getString("display_name")
                : data.uuid().toString();
        Optional<Vec3> pose = readPose(fullTag);
        return new StoredBodyCandidate(
                data.uuid(),
                displayName,
                level.dimension(),
                pose.orElse(Vec3.ZERO),
                new StoredBodyPointer(chunkPos, pointer.storageIndex(), pointer.subLevelIndex()),
                List.copyOf(data.dependencies()),
                fullTag,
                pose.isPresent()
                        ? StoredBodyCandidate.Health.READABLE
                        : StoredBodyCandidate.Health.STRUCTURALLY_CORRUPT,
                pose.isPresent() ? "readable" : "missing_or_invalid_pose"
        );
    }

    private static Optional<Vec3> readPose(CompoundTag fullTag) {
        if (!fullTag.contains("pose", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag poseTag = fullTag.getCompound("pose");
        if (!poseTag.contains("position", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag position = poseTag.getCompound("position");
        double x = position.getDouble("x");
        double y = position.getDouble("y");
        double z = position.getDouble("z");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? Optional.of(new Vec3(x, y, z))
                : Optional.empty();
    }

    private static final class ScanCache {
        private final long gameTime;
        private final List<StoredBodyCandidate> candidates;
        private boolean cacheReuseLogged;

        private ScanCache(long gameTime, List<StoredBodyCandidate> candidates) {
            this.gameTime = gameTime;
            this.candidates = List.copyOf(candidates);
        }

        private long gameTime() {
            return gameTime;
        }

        private List<StoredBodyCandidate> candidates() {
            return candidates;
        }

        private boolean cacheReuseLogged() {
            return cacheReuseLogged;
        }

        private void markCacheReuseLogged() {
            cacheReuseLogged = true;
        }
    }

    private record StoredBodyKey(ResourceKey<Level> dimension, UUID sableShipId) {
    }

    private record ScanLogState(String signature, long gameTime) {
    }
}

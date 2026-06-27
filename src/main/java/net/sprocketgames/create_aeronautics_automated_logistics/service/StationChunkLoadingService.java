package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.SableStoredShipRepository;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.ShipBodyDirectoryMigration;

public final class StationChunkLoadingService {
    private static final int STARTUP_SABLE_STORAGE_GRACE_TICKS = 100;
    private static final int INTERACTION_REQUEST_TTL_TICKS = 60;
    private static int startupGraceTicks;
    private static boolean reconcilePending;
    private static final Map<UUID, InteractionRequest> interactionRequests = new LinkedHashMap<>();
    private static final Map<UUID, Set<StationChunkLoadingSavedData.ForcedStationChunk>> forcedInteractionChunks = new LinkedHashMap<>();

    private StationChunkLoadingService() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        interactionRequests.clear();
        forcedInteractionChunks.clear();
        ShipBodyDirectoryMigration.migrateIfNeeded(event.getServer());
        SableStoredShipRepository.auditStartupStorage(event.getServer());
        startupGraceTicks = STARTUP_SABLE_STORAGE_GRACE_TICKS;
        reconcilePending = true;
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Startup Sable storage audit completed before restore grace; deferring station chunk loading reconcile for {} ticks while Sable restores holding sublevels",
                startupGraceTicks
        );
    }

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        if (startupGraceTicks <= 0) {
            return;
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (startupGraceTicks > 0) {
            startupGraceTicks--;
        }
        if (startupGraceTicks <= 0 && reconcilePending) {
            reconcilePending = false;
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Running deferred station chunk loading reconcile"
            );
            reconcile(event.getServer());
        }
        reconcileInteractionChunks(event.getServer());
    }

    public static boolean isStartupSableStorageGraceActive() {
        return startupGraceTicks > 0;
    }

    public static boolean isStartupRestoreReady() {
        return startupGraceTicks <= 0 && !reconcilePending;
    }

    public static void track(ServerLevel level, UUID stationId, BlockPos stationPos) {
        MinecraftServer server = level.getServer();
        if (!AutomatedLogisticsConfig.forceLoadStationChunks()) {
            untrack(server, stationId);
            return;
        }

        StationChunkLoadingSavedData data = StationChunkLoadingSavedData.get(server);
        StationChunkLoadingSavedData.ForcedStationChunk next = forcedChunk(level, stationPos);
        StationChunkLoadingSavedData.ForcedStationChunk previous = data.station(stationId).orElse(null);
        if (Objects.equals(previous, next)) {
            return;
        }

        data.put(stationId, next);
        if (isStartupSableStorageGraceActive()) {
            reconcilePending = true;
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Deferred station chunk loading update for station {} while Sable startup grace is active",
                    stationId
            );
            return;
        }

        if (previous != null && !data.anyOtherStationUses(stationId, previous) && !isInteractionChunkForced(stationId, previous)) {
            setForced(server, previous, false, true);
        }
        setForced(server, next, true, true);
    }

    public static void untrack(MinecraftServer server, UUID stationId) {
        StationChunkLoadingSavedData data = StationChunkLoadingSavedData.get(server);
        releaseInteractionChunks(server, stationId, Set.of());
        interactionRequests.remove(stationId);
        data.remove(stationId).ifPresent(removed -> {
            if (!data.anyOtherStationUses(stationId, removed) && !isInteractionChunkForced(stationId, removed)) {
                setForced(server, removed, false, true);
            }
        });
    }

    public static void requestInteractionLoading(ServerLevel level, UUID stationId, BlockPos stationPos, String reason) {
        if (!AutomatedLogisticsConfig.forceLoadStationChunks()) {
            return;
        }
        Set<StationChunkLoadingSavedData.ForcedStationChunk> chunks = forcedChunks(level, stationPos, AutomatedLogisticsConfig.stationInteractionChunkRadius());
        long expiresAt = level.getGameTime() + INTERACTION_REQUEST_TTL_TICKS;
        InteractionRequest previous = interactionRequests.put(
                stationId,
                new InteractionRequest(level.dimension(), stationPos.immutable(), reason, expiresAt, chunks)
        );
        if (previous == null || !previous.chunks().equals(chunks)) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Station interaction chunk loading requested: station={} pos={} dimension={} radiusChunks={} chunks={} reason={}",
                    stationId,
                    stationPos.toShortString(),
                    level.dimension().location(),
                    AutomatedLogisticsConfig.stationInteractionChunkRadius(),
                    chunks.size(),
                    reason
            );
        }
    }

    public static void reconcile(MinecraftServer server) {
        if (isStartupSableStorageGraceActive()) {
            reconcilePending = true;
            return;
        }

        StationChunkLoadingSavedData data = StationChunkLoadingSavedData.get(server);
        Map<UUID, StationChunkLoadingSavedData.ForcedStationChunk> tracked = data.stations();
        Map<UUID, StationChunkLoadingSavedData.ForcedStationChunk> desired = new LinkedHashMap<>();

        if (AutomatedLogisticsConfig.forceLoadStationChunks()) {
            for (IdentityDirectorySavedData.PersistedStationIdentity station : IdentityDirectorySavedData.get(server).allStations()) {
                ServerLevel level = server.getLevel(station.dimension());
                if (level == null) {
                    continue;
                }
                desired.put(
                        station.stationId(),
                        forcedChunk(level, station.stationPos())
                );
            }
        }

        for (Map.Entry<UUID, StationChunkLoadingSavedData.ForcedStationChunk> entry : tracked.entrySet()) {
            StationChunkLoadingSavedData.ForcedStationChunk desiredChunk = desired.get(entry.getKey());
            if (!entry.getValue().equals(desiredChunk)
                    && desired.values().stream().noneMatch(entry.getValue()::equals)
                    && !isInteractionChunkForced(entry.getKey(), entry.getValue())) {
                setForced(server, entry.getValue(), false, true);
            }
        }
        for (StationChunkLoadingSavedData.ForcedStationChunk chunk : desired.values()) {
            setForced(server, chunk, true, true);
            releaseLegacyMisconvertedChunk(server, chunk, desired.values());
        }
        for (UUID stationId : Set.copyOf(interactionRequests.keySet())) {
            if (!desired.containsKey(stationId)) {
                interactionRequests.remove(stationId);
                releaseInteractionChunks(server, stationId, Set.of());
            }
        }
        for (UUID stationId : Set.copyOf(forcedInteractionChunks.keySet())) {
            if (!desired.containsKey(stationId)) {
                releaseInteractionChunks(server, stationId, Set.of());
            }
        }
        data.replaceAll(desired);
    }

    private static StationChunkLoadingSavedData.ForcedStationChunk forcedChunk(ServerLevel level, BlockPos stationPos) {
        return new StationChunkLoadingSavedData.ForcedStationChunk(
                level.dimension(),
                new BlockPos(SectionPos.blockToSectionCoord(stationPos.getX()), 0, SectionPos.blockToSectionCoord(stationPos.getZ()))
        );
    }

    private static Set<StationChunkLoadingSavedData.ForcedStationChunk> forcedChunks(ServerLevel level, BlockPos stationPos, int radiusChunks) {
        int centerX = SectionPos.blockToSectionCoord(stationPos.getX());
        int centerZ = SectionPos.blockToSectionCoord(stationPos.getZ());
        int radius = Math.max(0, radiusChunks);
        Set<StationChunkLoadingSavedData.ForcedStationChunk> chunks = new LinkedHashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(new StationChunkLoadingSavedData.ForcedStationChunk(
                        level.dimension(),
                        new BlockPos(centerX + dx, 0, centerZ + dz)
                ));
            }
        }
        return Set.copyOf(chunks);
    }

    private static void reconcileInteractionChunks(MinecraftServer server) {
        if (isStartupSableStorageGraceActive()) {
            return;
        }
        if (!AutomatedLogisticsConfig.forceLoadStationChunks()) {
            for (UUID stationId : Set.copyOf(forcedInteractionChunks.keySet())) {
                releaseInteractionChunks(server, stationId, Set.of());
            }
            interactionRequests.clear();
            return;
        }
        long gameTime = server.overworld().getGameTime();
        for (UUID stationId : Set.copyOf(interactionRequests.keySet())) {
            InteractionRequest request = interactionRequests.get(stationId);
            if (request == null) {
                continue;
            }
            if (request.expiresAtGameTime() < gameTime) {
                interactionRequests.remove(stationId);
                releaseInteractionChunks(server, stationId, Set.of());
                CreateAeronauticsAutomatedLogistics.debugVehicle(
                        "Station interaction chunk loading expired: station={} pos={} reason={}",
                        stationId,
                        request.stationPos().toShortString(),
                        request.reason()
                );
                continue;
            }
            applyInteractionChunks(server, stationId, request);
        }
    }

    private static void applyInteractionChunks(MinecraftServer server, UUID stationId, InteractionRequest request) {
        Set<StationChunkLoadingSavedData.ForcedStationChunk> previous =
                forcedInteractionChunks.getOrDefault(stationId, Set.of());
        Set<StationChunkLoadingSavedData.ForcedStationChunk> desired = request.chunks();
        if (previous.equals(desired)) {
            return;
        }
        releaseInteractionChunks(server, stationId, desired);
        int newlyForced = 0;
        for (StationChunkLoadingSavedData.ForcedStationChunk chunk : desired) {
            if (!previous.contains(chunk)) {
                setForced(server, chunk, true, false);
                newlyForced++;
            }
        }
        forcedInteractionChunks.put(stationId, desired);
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Station interaction chunk loading applied: station={} pos={} dimension={} chunks={} newlyForced={} reason={}",
                stationId,
                request.stationPos().toShortString(),
                request.dimension().location(),
                desired.size(),
                newlyForced,
                request.reason()
        );
    }

    private static void releaseInteractionChunks(
            MinecraftServer server,
            UUID stationId,
            Set<StationChunkLoadingSavedData.ForcedStationChunk> keep
    ) {
        Set<StationChunkLoadingSavedData.ForcedStationChunk> previous = forcedInteractionChunks.getOrDefault(stationId, Set.of());
        if (previous.isEmpty()) {
            return;
        }
        StationChunkLoadingSavedData data = StationChunkLoadingSavedData.get(server);
        int released = 0;
        for (StationChunkLoadingSavedData.ForcedStationChunk chunk : previous) {
            if (keep.contains(chunk)) {
                continue;
            }
            boolean anchorStillUses = data.stations().values().stream().anyMatch(chunk::equals);
            boolean otherInteractionUses = forcedInteractionChunks.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(stationId))
                    .flatMap(entry -> entry.getValue().stream())
                    .anyMatch(chunk::equals);
            if (!anchorStillUses && !otherInteractionUses) {
                setForced(server, chunk, false, false);
                released++;
            }
        }
        if (keep.isEmpty()) {
            forcedInteractionChunks.remove(stationId);
        } else {
            forcedInteractionChunks.put(stationId, keep);
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Station interaction chunk loading released: station={} released={} remaining={}",
                stationId,
                released,
                keep.size()
        );
    }

    private static boolean isInteractionChunkForced(UUID excludingStationId, StationChunkLoadingSavedData.ForcedStationChunk chunk) {
        return forcedInteractionChunks.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(excludingStationId))
                .flatMap(entry -> entry.getValue().stream())
                .anyMatch(chunk::equals);
    }

    private static void setForced(MinecraftServer server, StationChunkLoadingSavedData.ForcedStationChunk chunk, boolean forced, boolean log) {
        ServerLevel level = server.getLevel(chunk.dimension());
        if (level == null) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(chunk.chunkPos().getX(), chunk.chunkPos().getZ());
        level.setChunkForced(chunkPos.x, chunkPos.z, forced);
        if (log) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Station chunk loading {} {} in {}",
                    forced ? "enabled for" : "released for",
                    chunkPos,
                    chunk.dimension().location()
            );
        }
    }

    private static void releaseLegacyMisconvertedChunk(
            MinecraftServer server,
            StationChunkLoadingSavedData.ForcedStationChunk chunk,
            Collection<StationChunkLoadingSavedData.ForcedStationChunk> desiredChunks
    ) {
        ChunkPos correct = new ChunkPos(chunk.chunkPos().getX(), chunk.chunkPos().getZ());
        ChunkPos legacy = new ChunkPos(chunk.chunkPos());
        if (legacy.equals(correct)) {
            return;
        }
        boolean wanted = desiredChunks.stream()
                .filter(desired -> desired.dimension().equals(chunk.dimension()))
                .map(desired -> new ChunkPos(desired.chunkPos().getX(), desired.chunkPos().getZ()))
                .anyMatch(legacy::equals);
        if (!wanted) {
            ServerLevel level = server.getLevel(chunk.dimension());
            if (level != null) {
                level.setChunkForced(legacy.x, legacy.z, false);
            }
        }
    }

    private record InteractionRequest(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            BlockPos stationPos,
            String reason,
            long expiresAtGameTime,
            Set<StationChunkLoadingSavedData.ForcedStationChunk> chunks
    ) {
    }
}

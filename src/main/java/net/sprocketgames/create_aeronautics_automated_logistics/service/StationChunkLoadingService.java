package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

public final class StationChunkLoadingService {
    private static final int STARTUP_SABLE_STORAGE_GRACE_TICKS = 100;
    private static int startupGraceTicks;
    private static boolean reconcilePending;

    private StationChunkLoadingService() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        startupGraceTicks = STARTUP_SABLE_STORAGE_GRACE_TICKS;
        reconcilePending = true;
        SableStoredShipGarbageCollector.pruneDanglingStoredShipEntries(
                event.getServer(),
                "server_started",
                "startup_sable_storage_grace_begin"
        );
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Deferring station chunk loading reconcile for {} ticks while Sable restores holding sublevels",
                startupGraceTicks
        );
    }

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        if (startupGraceTicks <= 0) {
            return;
        }
        SableStoredShipGarbageCollector.pruneDanglingStoredShipEntries(
                event.getServer(),
                "server_tick_pre",
                "startup_sable_storage_grace_active"
        );
        SableStoredShipGarbageCollector.pruneLoadedDuplicateStoredShips(
                event.getServer(),
                "server_tick_pre",
                "startup_sable_storage_grace_active"
        );
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (startupGraceTicks > 0) {
            startupGraceTicks--;
        }
        if (startupGraceTicks <= 0 && reconcilePending) {
            reconcilePending = false;
            SableStoredShipGarbageCollector.pruneDanglingStoredShipEntries(
                    event.getServer(),
                    "server_tick_post",
                    "startup_sable_storage_grace_complete"
            );
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Running deferred station chunk loading reconcile"
            );
            reconcile(event.getServer());
        }
    }

    public static boolean isStartupSableStorageGraceActive() {
        return startupGraceTicks > 0;
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

        if (previous != null && !data.anyOtherStationUses(stationId, previous)) {
            setForced(server, previous, false);
        }
        setForced(server, next, true);
    }

    public static void untrack(MinecraftServer server, UUID stationId) {
        StationChunkLoadingSavedData data = StationChunkLoadingSavedData.get(server);
        data.remove(stationId).ifPresent(removed -> {
            if (!data.anyOtherStationUses(stationId, removed)) {
                setForced(server, removed, false);
            }
        });
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
            if (!entry.getValue().equals(desiredChunk) && desired.values().stream().noneMatch(entry.getValue()::equals)) {
                setForced(server, entry.getValue(), false);
            }
        }
        for (StationChunkLoadingSavedData.ForcedStationChunk chunk : desired.values()) {
            setForced(server, chunk, true);
            releaseLegacyMisconvertedChunk(server, chunk, desired.values());
        }
        data.replaceAll(desired);
    }

    private static StationChunkLoadingSavedData.ForcedStationChunk forcedChunk(ServerLevel level, BlockPos stationPos) {
        return new StationChunkLoadingSavedData.ForcedStationChunk(
                level.dimension(),
                new BlockPos(SectionPos.blockToSectionCoord(stationPos.getX()), 0, SectionPos.blockToSectionCoord(stationPos.getZ()))
        );
    }

    private static void setForced(MinecraftServer server, StationChunkLoadingSavedData.ForcedStationChunk chunk, boolean forced) {
        ServerLevel level = server.getLevel(chunk.dimension());
        if (level == null) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(chunk.chunkPos().getX(), chunk.chunkPos().getZ());
        level.setChunkForced(chunkPos.x, chunkPos.z, forced);
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Station chunk loading {} {} in {}",
                forced ? "enabled for" : "released for",
                chunkPos,
                chunk.dimension().location()
        );
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
}

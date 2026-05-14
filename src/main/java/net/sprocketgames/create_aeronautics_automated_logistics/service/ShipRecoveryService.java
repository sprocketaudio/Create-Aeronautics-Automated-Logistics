package net.sprocketgames.create_aeronautics_automated_logistics.service;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

public final class ShipRecoveryService {
    private static final long STORED_SCAN_CACHE_TICKS = 200L;

    private ShipRecoveryService() {
    }

    public static RecoveryResult recoverToPlayer(ServerPlayer targetPlayer, ShipSelector selector) {
        Objects.requireNonNull(targetPlayer, "targetPlayer");
        Objects.requireNonNull(selector, "selector");
        return recover(selector, new Destination(
                targetPlayer.serverLevel(),
                targetPlayer.position().add(targetPlayer.getLookAngle().scale(8.0D)).add(0.0D, 4.0D, 0.0D),
                "player " + targetPlayer.getGameProfile().getName()
        ));
    }

    public static RecoveryResult recoverToStation(ServerLevel shipLevel, ShipSelector selector, StationSelector stationSelector) {
        Objects.requireNonNull(shipLevel, "shipLevel");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(stationSelector, "stationSelector");

        StationLookup stationLookup = resolveStation(shipLevel, stationSelector);
        if (!stationLookup.success()) {
            return stationLookup.asRecoveryFailure();
        }

        ServerLevel stationLevel = shipLevel.getServer().getLevel(stationLookup.snapshot().dimension());
        if (stationLevel == null) {
            return RecoveryResult.failure("The chosen station's dimension is not loaded.");
        }

        return recover(selector, new Destination(
                stationLevel,
                Vec3.atCenterOf(stationLookup.snapshot().stationPos()).add(0.0D, 4.0D, 0.0D),
                "station " + stationLookup.snapshot().stationName()
        ));
    }

    public static RecoveryResult recover(ShipSelector selector, Destination destination) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(destination, "destination");

        ShipLookup shipLookup = resolveShip(destination.level(), selector);
        if (!shipLookup.success()) {
            return shipLookup.asRecoveryFailure();
        }

        return RecoveryResult.failure(
                "Ship teleport recovery is disabled because Sable relocation is not reliable from this command. "
                        + "Use /aal tp_to_ship to find the ship, then use Sable's teleport command if you need to move it."
        );
    }

    public static RecoveryResult teleportPlayerToShip(ServerPlayer player, ShipSelector selector) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(selector, "selector");

        ShipLookup shipLookup = resolveShip(player.serverLevel(), selector);
        if (!shipLookup.success()) {
            return shipLookup.asRecoveryFailure();
        }

        ServerLevel targetLevel = player.getServer() == null ? null : player.getServer().getLevel(shipLookup.dimension());
        if (targetLevel == null) {
            return RecoveryResult.failure("The ship's dimension is not loaded.");
        }

        Vec3 target = shipLookup.lastKnownPosition()
                .orElseGet(() -> shipLookup.indexedShip()
                        .map(ship -> Vec3.atCenterOf(ship.transponderPos()).add(0.0D, 2.0D, 0.0D))
                        .orElse(null));
        if (target == null) {
            return RecoveryResult.failure("No stored location is known for " + shipLookup.displayName() + ".");
        }
        player.teleportTo(targetLevel, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        return RecoveryResult.success(
                "Teleported to " + shipLookup.displayName() + "."
        );
    }

    private static void clearAutomationState(ServerLevel level, IdentityDirectorySavedData.PersistedShipIdentity ship) {
        AutomatedLogisticsServices.SCHEDULES.stop(level, ship.transponderId());
        ShipTransponderRegistry.snapshot(ship.transponderId())
                .flatMap(ShipTransponderSnapshot::controllerRef)
                .ifPresent(controllerRef -> {
            AutomatedLogisticsServices.PLAYBACK.stopLinkedPlaybacks(level, controllerRef, FailureReason.NONE);
            AutomatedLogisticsServices.RECORDING.cancelRecordingForController(level, controllerRef);
        });
        if (level.getBlockEntity(ship.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
            transponder.setRecordingDestinationStationId(Optional.empty());
            transponder.setDockOutputActive(false);
            transponder.refreshRuntimeShip(level);
            transponder.refreshShipDockLink(level);
        }
    }

    private static void refreshTransponder(ServerLevel level, BlockPos transponderPos) {
        if (level.getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            transponder.refreshRuntimeShip(level);
            transponder.refreshShipDockLink(level);
        }
    }

    private static ShipLookup resolveShip(ServerLevel preferredLevel, ShipSelector selector) {
        IdentityDirectorySavedData data = IdentityDirectorySavedData.get(preferredLevel.getServer());
        return switch (selector) {
            case ShipSelector.ByTransponder(UUID transponderId) -> data.ship(transponderId)
                    .map(ShipLookup::success)
                    .orElseGet(() -> ShipLookup.failure("No ship transponder found for " + transponderId + "."));
            case ShipSelector.ByStoredSubLevelId(UUID subLevelId) -> storedShips(preferredLevel.getServer()).stream()
                    .filter(snapshot -> snapshot.subLevelId().equals(subLevelId))
                    .findFirst()
                    .map(ShipLookup::success)
                    .orElseGet(() -> ShipLookup.failure("No stored Sable ship found for " + subLevelId + "."));
            case ShipSelector.ByName(String shipName) -> {
                List<IdentityDirectorySavedData.PersistedShipIdentity> matches = data.allShips().stream()
                        .filter(snapshot -> snapshot.shipName().equalsIgnoreCase(shipName))
                        .toList();
                if (matches.isEmpty()) {
                    List<StoredShipIdentity> storedMatches = storedShips(preferredLevel.getServer()).stream()
                            .filter(snapshot -> snapshot.displayName().equalsIgnoreCase(shipName))
                            .toList();
                    if (storedMatches.isEmpty()) {
                        yield ShipLookup.failure("No ship named \"" + shipName + "\" is currently known.");
                    }
                    if (storedMatches.size() == 1) {
                        yield ShipLookup.success(storedMatches.getFirst());
                    }
                    List<StoredShipIdentity> sameDimensionStored = storedMatches.stream()
                            .filter(snapshot -> snapshot.dimension().equals(preferredLevel.dimension()))
                            .toList();
                    if (sameDimensionStored.size() == 1) {
                        yield ShipLookup.success(sameDimensionStored.getFirst());
                    }
                    yield ShipLookup.failure("Ship name \"" + shipName + "\" is ambiguous across stored sublevels.");
                }
                if (matches.size() == 1) {
                    yield ShipLookup.success(matches.getFirst());
                }
                List<IdentityDirectorySavedData.PersistedShipIdentity> sameDimension = matches.stream()
                        .filter(snapshot -> snapshot.dimension().equals(preferredLevel.dimension()))
                        .toList();
                if (sameDimension.size() == 1) {
                    yield ShipLookup.success(sameDimension.getFirst());
                }
                yield ShipLookup.failure("Ship name \"" + shipName + "\" is ambiguous. Use the transponder id instead.");
            }
        };
    }

    private static StationLookup resolveStation(ServerLevel preferredLevel, StationSelector selector) {
        IdentityDirectorySavedData data = IdentityDirectorySavedData.get(preferredLevel.getServer());
        return switch (selector) {
            case StationSelector.ByStationId(UUID stationId) -> data.station(stationId)
                    .map(StationLookup::success)
                    .orElseGet(() -> StationLookup.failure("No station found for " + stationId + "."));
            case StationSelector.ByName(String stationName) -> {
                List<IdentityDirectorySavedData.PersistedStationIdentity> matches = data.allStations().stream()
                        .filter(snapshot -> snapshot.stationName().equalsIgnoreCase(stationName))
                        .toList();
                if (matches.isEmpty()) {
                    yield StationLookup.failure("No station named \"" + stationName + "\" is currently known.");
                }
                if (matches.size() == 1) {
                    yield StationLookup.success(matches.getFirst());
                }
                List<IdentityDirectorySavedData.PersistedStationIdentity> sameDimension = matches.stream()
                        .filter(snapshot -> snapshot.dimension().equals(preferredLevel.dimension()))
                        .toList();
                if (sameDimension.size() == 1) {
                    yield StationLookup.success(sameDimension.getFirst());
                }
                yield StationLookup.failure("Station name \"" + stationName + "\" is ambiguous. Use the station id instead.");
            }
        };
    }

    public static List<String> knownShipNames(net.minecraft.server.MinecraftServer server) {
        Set<String> names = new HashSet<>();
        IdentityDirectorySavedData.get(server).allShips().stream()
                .map(IdentityDirectorySavedData.PersistedShipIdentity::shipName)
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        storedShips(server).stream()
                .map(StoredShipIdentity::displayName)
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownStoredShipIds(MinecraftServer server) {
        return storedShips(server).stream()
                .map(identity -> identity.subLevelId().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownShipIds(net.minecraft.server.MinecraftServer server) {
        return IdentityDirectorySavedData.get(server).allShips().stream()
                .map(identity -> identity.transponderId().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownStationNames(net.minecraft.server.MinecraftServer server) {
        return IdentityDirectorySavedData.get(server).allStations().stream()
                .map(IdentityDirectorySavedData.PersistedStationIdentity::stationName)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownStationIds(net.minecraft.server.MinecraftServer server) {
        return IdentityDirectorySavedData.get(server).allStations().stream()
                .map(identity -> identity.stationId().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static ShipSelector parseStoredShipSelector(String raw) {
        String normalized = raw.trim();
        try {
            return new ShipSelector.ByStoredSubLevelId(UUID.fromString(normalized));
        } catch (IllegalArgumentException ignored) {
            return new ShipSelector.ByName(normalized);
        }
    }

    public static ShipSelector parseShipNameSelector(String raw) {
        return new ShipSelector.ByName(raw.trim());
    }

    public static ShipSelector parseTransponderSelector(String raw) {
        return new ShipSelector.ByTransponder(UUID.fromString(raw.trim()));
    }

    public static StationSelector parseStationSelector(String raw) {
        String normalized = raw.trim();
        try {
            return new StationSelector.ByStationId(UUID.fromString(normalized));
        } catch (IllegalArgumentException ignored) {
            return new StationSelector.ByName(normalized);
        }
    }

    public sealed interface ShipSelector permits ShipSelector.ByTransponder, ShipSelector.ByStoredSubLevelId, ShipSelector.ByName {
        record ByTransponder(UUID transponderId) implements ShipSelector {
        }

        record ByStoredSubLevelId(UUID subLevelId) implements ShipSelector {
        }

        record ByName(String shipName) implements ShipSelector {
        }
    }

    public sealed interface StationSelector permits StationSelector.ByStationId, StationSelector.ByName {
        record ByStationId(UUID stationId) implements StationSelector {
        }

        record ByName(String stationName) implements StationSelector {
        }
    }

    public record RecoveryResult(boolean success, String message) {
        public static RecoveryResult success(String message) {
            return new RecoveryResult(true, message);
        }

        public static RecoveryResult failure(String message) {
            return new RecoveryResult(false, message);
        }
    }

    public record Destination(ServerLevel level, Vec3 position, String description) {
    }

    private record ShipLookup(
            IdentityDirectorySavedData.PersistedShipIdentity ship,
            StoredShipIdentity storedShip,
            String error
    ) {
        static ShipLookup success(IdentityDirectorySavedData.PersistedShipIdentity ship) {
            return new ShipLookup(ship, null, null);
        }

        static ShipLookup success(StoredShipIdentity ship) {
            return new ShipLookup(null, ship, null);
        }

        static ShipLookup failure(String error) {
            return new ShipLookup(null, null, error);
        }

        boolean success() {
            return ship != null || storedShip != null;
        }

        Optional<IdentityDirectorySavedData.PersistedShipIdentity> indexedShip() {
            return Optional.ofNullable(ship);
        }

        Optional<StoredShipIdentity> storedShipOptional() {
            return Optional.ofNullable(storedShip);
        }

        String displayName() {
            if (ship != null) {
                return ship.shipName();
            }
            return storedShip == null ? "ship" : storedShip.displayName();
        }

        ResourceKey<net.minecraft.world.level.Level> dimension() {
            if (ship != null) {
                return ship.dimension();
            }
            return storedShip.dimension();
        }

        Optional<Vec3> lastKnownPosition() {
            if (ship != null) {
                return ship.lastKnownPosition();
            }
            return Optional.of(storedShip.position());
        }

        RecoveryResult asRecoveryFailure() {
            return RecoveryResult.failure(error);
        }
    }

    private record StationLookup(IdentityDirectorySavedData.PersistedStationIdentity snapshot, String error) {
        static StationLookup success(IdentityDirectorySavedData.PersistedStationIdentity snapshot) {
            return new StationLookup(snapshot, null);
        }

        static StationLookup failure(String error) {
            return new StationLookup(null, error);
        }

        boolean success() {
            return snapshot != null;
        }

        RecoveryResult asRecoveryFailure() {
            return RecoveryResult.failure(error);
        }
    }

    private static Optional<StoredShipIdentity> findStoredShipFallback(MinecraftServer server, ShipLookup lookup) {
        if (lookup.storedShipOptional().isPresent()) {
            return lookup.storedShipOptional();
        }
        return lookup.indexedShip()
                .flatMap(indexed -> storedShips(server).stream()
                        .filter(candidate -> candidate.displayName().equalsIgnoreCase(indexed.shipName()))
                        .filter(candidate -> candidate.dimension().equals(indexed.dimension()))
                        .findFirst());
    }

    private static RecoveryResult moveStoredShip(MinecraftServer server, StoredShipIdentity storedShip, Destination destination) {
        if (!storedShip.dimension().equals(destination.level().dimension())) {
            return RecoveryResult.failure("Cross-dimension stored ship recovery is not supported.");
        }

        ServerLevel level = server.getLevel(storedShip.dimension());
        if (level == null) {
            return RecoveryResult.failure("The stored ship's dimension is not loaded.");
        }

        Map<UUID, StoredShipIdentity> storedByUuid = new LinkedHashMap<>();
        for (StoredShipIdentity candidate : storedShips(server)) {
            if (candidate.dimension().equals(storedShip.dimension())) {
                storedByUuid.put(candidate.subLevelId(), candidate);
            }
        }

        List<StoredShipIdentity> chain = collectStoredDependencyChain(storedShip, storedByUuid);
        Vec3 delta = destination.position().subtract(storedShip.position());
        net.minecraft.world.level.ChunkPos destinationChunk = new net.minecraft.world.level.ChunkPos(
                net.minecraft.core.BlockPos.containing(destination.position())
        );

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return RecoveryResult.failure("Sable sublevel container is not available for the stored ship's dimension.");
        }
        SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        SubLevelStorage storage = holdingChunkMap.getStorage();
        Map<UUID, SubLevelData> translatedDataById = new LinkedHashMap<>();
        Map<UUID, GlobalSavedSubLevelPointer> oldPointersById = new LinkedHashMap<>();

        for (StoredShipIdentity member : chain) {
            SubLevelData data = storage.attemptLoadSubLevel(member.pointer().chunkPos(), member.pointer().local());
            if (data == null) {
                return RecoveryResult.failure("Failed to load stored sublevel data for " + member.displayName() + ".");
            }
            translateStoredSubLevel(data.fullTag(), delta);
            SubLevelData translated = SubLevelSerializer.fromData(data.fullTag().copy());
            translated.setOriginLoadedChunk(destinationChunk);
            translatedDataById.put(member.subLevelId(), translated);
            oldPointersById.put(member.subLevelId(), member.pointer());
        }

        Map<net.minecraft.world.level.ChunkPos, SubLevelHoldingChunk> holdingChunks = new LinkedHashMap<>();
        java.util.function.Function<net.minecraft.world.level.ChunkPos, SubLevelHoldingChunk> loadHoldingChunk = chunkPos ->
                holdingChunks.computeIfAbsent(
                        chunkPos,
                        pos -> Optional.ofNullable(storage.attemptLoadHoldingChunk(pos)).orElse(new SubLevelHoldingChunk(pos))
                );

        for (GlobalSavedSubLevelPointer oldPointer : oldPointersById.values()) {
            loadHoldingChunk.apply(oldPointer.chunkPos()).getSubLevelPointers().remove(oldPointer.local());
        }

        for (StoredShipIdentity member : chain) {
            SubLevelData translated = translatedDataById.get(member.subLevelId());
            GlobalSavedSubLevelPointer newPointer = storage.attemptSaveSubLevel(destinationChunk, translated);
            if (newPointer == null) {
                return RecoveryResult.failure("Failed to save moved stored sublevel data for " + member.displayName() + ".");
            }
            loadHoldingChunk.apply(destinationChunk).getSubLevelPointers().add(newPointer.local());
        }

        for (GlobalSavedSubLevelPointer oldPointer : oldPointersById.values()) {
            storage.attemptSaveSubLevel(oldPointer, null);
        }
        for (Map.Entry<net.minecraft.world.level.ChunkPos, SubLevelHoldingChunk> entry : holdingChunks.entrySet()) {
            storage.attemptSaveHoldingChunk(entry.getKey(), entry.getValue());
        }
        refreshHoldingChunkMap(holdingChunkMap, destinationChunk, oldPointersById.values());
        invalidateStoredShipCache(server);
        return RecoveryResult.success(
                "Recovered stored ship " + storedShip.displayName() + " to " + destination.description()
                        + ". Load the destination area to instantiate it."
        );
    }

    private static void refreshHoldingChunkMap(
            SubLevelHoldingChunkMap holdingChunkMap,
            net.minecraft.world.level.ChunkPos destinationChunk,
            Iterable<GlobalSavedSubLevelPointer> oldPointers
    ) {
        Set<net.minecraft.world.level.ChunkPos> affectedChunks = new LinkedHashSet<>();
        affectedChunks.add(destinationChunk);
        for (GlobalSavedSubLevelPointer oldPointer : oldPointers) {
            affectedChunks.add(oldPointer.chunkPos());
        }

        for (net.minecraft.world.level.ChunkPos chunkPos : affectedChunks) {
            holdingChunkMap.updateChunkStatus(chunkPos, false);
        }
        holdingChunkMap.processChanges();
        for (net.minecraft.world.level.ChunkPos chunkPos : affectedChunks) {
            holdingChunkMap.updateChunkStatus(chunkPos, true);
        }
        holdingChunkMap.processChanges();
    }

    private static List<StoredShipIdentity> collectStoredDependencyChain(
            StoredShipIdentity root,
            Map<UUID, StoredShipIdentity> storedByUuid
    ) {
        List<StoredShipIdentity> resolved = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<StoredShipIdentity> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            StoredShipIdentity current = queue.removeFirst();
            if (!visited.add(current.subLevelId())) {
                continue;
            }
            resolved.add(current);
            for (UUID dependencyId : current.dependencies()) {
                StoredShipIdentity dependency = storedByUuid.get(dependencyId);
                if (dependency != null) {
                    queue.addLast(dependency);
                }
            }
        }
        return resolved;
    }

    private static void translateStoredSubLevel(CompoundTag fullTag, Vec3 delta) {
        CompoundTag poseTag = fullTag.getCompound("pose");
        CompoundTag positionTag = poseTag.getCompound("position");
        positionTag.putDouble("x", positionTag.getDouble("x") + delta.x);
        positionTag.putDouble("y", positionTag.getDouble("y") + delta.y);
        positionTag.putDouble("z", positionTag.getDouble("z") + delta.z);
        poseTag.put("position", positionTag);
        fullTag.put("pose", poseTag);

        CompoundTag boundsTag = fullTag.getCompound("world_bounds");
        boundsTag.putDouble("minX", boundsTag.getDouble("minX") + delta.x);
        boundsTag.putDouble("minY", boundsTag.getDouble("minY") + delta.y);
        boundsTag.putDouble("minZ", boundsTag.getDouble("minZ") + delta.z);
        boundsTag.putDouble("maxX", boundsTag.getDouble("maxX") + delta.x);
        boundsTag.putDouble("maxY", boundsTag.getDouble("maxY") + delta.y);
        boundsTag.putDouble("maxZ", boundsTag.getDouble("maxZ") + delta.z);
        fullTag.put("world_bounds", boundsTag);

        fullTag.remove("linear_velocity");
        fullTag.remove("angular_velocity");
    }

    private static List<StoredShipIdentity> storedShips(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld == null ? 0L : overworld.getGameTime();
        StoredShipScanCache cache = StoredShipScanCacheHolder.CACHE_BY_SERVER.get(server);
        if (cache != null && now - cache.gameTime() <= STORED_SCAN_CACHE_TICKS) {
            return cache.ships();
        }

        List<StoredShipIdentity> scanned = scanStoredShips(server);
        StoredShipScanCacheHolder.CACHE_BY_SERVER.put(server, new StoredShipScanCache(now, scanned));
        return scanned;
    }

    private static void invalidateStoredShipCache(MinecraftServer server) {
        StoredShipScanCacheHolder.CACHE_BY_SERVER.remove(server);
    }

    private static List<StoredShipIdentity> scanStoredShips(MinecraftServer server) {
        Map<UUID, StoredShipIdentity> discovered = new LinkedHashMap<>();
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
            java.io.File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(".slvlr"));
            if (regionFiles == null) {
                continue;
            }
            for (java.io.File regionFile : regionFiles) {
                scanRegionFile(level, storage, regionFile, discovered);
            }
        }
        return discovered.values().stream()
                .sorted(Comparator.comparing(StoredShipIdentity::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(identity -> identity.subLevelId().toString()))
                .toList();
    }

    private static void scanRegionFile(
            ServerLevel level,
            SubLevelStorage storage,
            java.io.File regionFile,
            Map<UUID, StoredShipIdentity> discovered
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
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(
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
                        continue;
                    }
                    StoredShipIdentity identity = toStoredShipIdentity(level, chunkPos, pointer, data);
                    discovered.putIfAbsent(identity.subLevelId(), identity);
                }
            }
        }
    }

    private static StoredShipIdentity toStoredShipIdentity(
            ServerLevel level,
            net.minecraft.world.level.ChunkPos chunkPos,
            SavedSubLevelPointer pointer,
            SubLevelData data
    ) {
        CompoundTag fullTag = data.fullTag();
        String displayName = fullTag.contains("display_name")
                ? fullTag.getString("display_name")
                : data.uuid().toString();
        CompoundTag poseTag = fullTag.getCompound("pose").getCompound("position");
        Vec3 position = new Vec3(
                poseTag.getDouble("x"),
                poseTag.getDouble("y"),
                poseTag.getDouble("z")
        );
        return new StoredShipIdentity(
                data.uuid(),
                displayName,
                level.dimension(),
                position,
                new GlobalSavedSubLevelPointer(chunkPos, pointer.storageIndex(), pointer.subLevelIndex()),
                List.copyOf(data.dependencies())
        );
    }

    private record StoredShipIdentity(
            UUID subLevelId,
            String displayName,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            Vec3 position,
            GlobalSavedSubLevelPointer pointer,
            List<UUID> dependencies
    ) {
    }

    private record StoredShipScanCache(long gameTime, List<StoredShipIdentity> ships) {
    }

    private static final class StoredShipScanCacheHolder {
        private static final Map<MinecraftServer, StoredShipScanCache> CACHE_BY_SERVER = new HashMap<>();

        private StoredShipScanCacheHolder() {
        }
    }
}

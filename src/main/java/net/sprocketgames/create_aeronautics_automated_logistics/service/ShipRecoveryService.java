package net.sprocketgames.create_aeronautics_automated_logistics.service;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;
import org.joml.Quaterniond;
import org.joml.Vector3d;

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

    public static RecoveryResult moveStoredShipControllerTo(
            MinecraftServer server,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId,
            BlockPos localControllerPos,
            Vec3 destinationControllerPosition,
            String description
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(subLevelId, "subLevelId");
        Objects.requireNonNull(localControllerPos, "localControllerPos");
        Objects.requireNonNull(destinationControllerPosition, "destinationControllerPosition");
        Objects.requireNonNull(description, "description");

        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return RecoveryResult.failure("The stored ship's dimension is not loaded.");
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container != null && container.getSubLevel(subLevelId) != null) {
            return RecoveryResult.success(
                    "Ship " + subLevelId + " is already loaded; no stored relocation was needed for " + description + "."
            );
        }

        StoredShipLookup storedShip = findStoredShipById(server, dimension, subLevelId);
        if (storedShip.identity().isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Stored Sable lookup failed for sublevel {} in {} using {}",
                    subLevelId,
                    dimension.location(),
                    storedShip.source()
            );
            return RecoveryResult.failure("No stored Sable ship found for " + subLevelId + ".");
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Stored Sable lookup resolved sublevel {} in {} using {}",
                subLevelId,
                dimension.location(),
                storedShip.source()
        );

        StoredShipIdentity ship = storedShip.identity().get();
        Optional<ControllerPoseCalculation> calculation = ship.posePositionForController(localControllerPos, destinationControllerPosition);
        if (calculation.isEmpty()) {
            return RecoveryResult.failure(
                    "Stored Sable pose data for " + ship.displayName() + " is incomplete or unsafe to relocate."
            );
        }
        Vec3 destinationPosePosition = calculation.get().posePosition();
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(destinationControllerPosition))
                || !level.getWorldBorder().isWithinBounds(BlockPos.containing(destinationPosePosition))) {
            return RecoveryResult.failure(
                    "Refusing to relocate stored ship outside the world border. destinationController="
                            + destinationControllerPosition + ", destinationPose=" + destinationPosePosition
            );
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Prepared stored Sable relocation for {} sublevel={} localController={} targetController={} storedPose={} destinationPose={} anchorOffset={} maxExpectedAnchorOffset={}",
                ship.displayName(),
                ship.subLevelId(),
                localControllerPos,
                destinationControllerPosition,
                ship.position(),
                destinationPosePosition,
                calculation.get().rotatedAnchorOffset(),
                calculation.get().maxExpectedAnchorOffset()
        );
        return moveStoredShip(
                server,
                ship,
                new Destination(level, destinationPosePosition, description)
        );
    }

    public static RecoveryResult materializeStoredShipControllerAt(
            MinecraftServer server,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId,
            BlockPos localControllerPos,
            Vec3 destinationControllerPosition,
            String description
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(subLevelId, "subLevelId");
        Objects.requireNonNull(localControllerPos, "localControllerPos");
        Objects.requireNonNull(destinationControllerPosition, "destinationControllerPosition");
        Objects.requireNonNull(description, "description");

        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return RecoveryResult.failure("The stored ship's dimension is not loaded.");
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return RecoveryResult.failure("Sable sublevel container is not available for the stored ship's dimension.");
        }
        if (container.getSubLevel(subLevelId) != null) {
            return RecoveryResult.success("Ship " + subLevelId + " is already loaded for " + description + ".");
        }

        RecoveryResult moveResult = moveStoredShipControllerTo(
                server,
                dimension,
                subLevelId,
                localControllerPos,
                destinationControllerPosition,
                description
        );
        if (!moveResult.success()) {
            return moveResult;
        }
        if (container.getSubLevel(subLevelId) != null) {
            return RecoveryResult.success(
                    "Materialized stored ship " + subLevelId + " at " + description
                            + " during holding-chunk refresh."
            );
        }

        StoredShipLookup storedShip = findStoredShipById(server, dimension, subLevelId);
        if (storedShip.identity().isEmpty()) {
            return RecoveryResult.failure(
                    "Stored Sable ship " + subLevelId + " was relocated for " + description
                            + " but could not be found for loading. lookup=" + storedShip.source()
            );
        }

        StoredShipIdentity ship = storedShip.identity().get();
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        SubLevelData data = storage.attemptLoadSubLevel(ship.pointer().chunkPos(), ship.pointer().local());
        if (data == null) {
            return RecoveryResult.failure(
                    "Stored Sable ship " + ship.displayName() + " was moved for " + description
                            + " but the moved sublevel data could not be loaded from " + ship.pointer() + "."
            );
        }
        Optional<String> loadBlocker = movedSubLevelLoadBlocker(level, data);
        if (loadBlocker.isPresent()) {
            return RecoveryResult.failure(
                    "Stored Sable ship " + ship.displayName() + " was moved for " + description
                            + " but is not safe to load yet: " + loadBlocker.get()
            );
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Loading moved stored Sable ship {} sublevel={} pointer={} directly into loaded route context for {}",
                ship.displayName(),
                ship.subLevelId(),
                ship.pointer(),
                description
        );
        purgeInMemoryHoldingSubLevel(container.getHoldingChunkMap(), subLevelId, description);
        container.getHoldingChunkMap().loadHoldingSubLevel(new HoldingSubLevel(data, ship.pointer()));
        invalidateStoredShipCache(server);
        if (container.getSubLevel(subLevelId) == null) {
            return RecoveryResult.failure(
                    "Stored Sable ship " + ship.displayName() + " was moved for " + description
                            + " but Sable did not instantiate the sublevel."
            );
        }
        return RecoveryResult.success("Materialized stored ship " + ship.displayName() + " at " + description + ".");
    }

    private static Optional<String> movedSubLevelLoadBlocker(ServerLevel level, SubLevelData data) {
        BoundingBox3dc bounds = data.bounds();
        int minChunkX = net.minecraft.util.Mth.floor(bounds.minX() - 1.0D) >> 4;
        int maxChunkX = net.minecraft.util.Mth.floor(bounds.maxX() + 1.0D) >> 4;
        int minChunkZ = net.minecraft.util.Mth.floor(bounds.minZ() - 1.0D) >> 4;
        int maxChunkZ = net.minecraft.util.Mth.floor(bounds.maxZ() + 1.0D) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return Optional.of("chunk " + chunkX + "," + chunkZ + " is not loaded");
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static void purgeInMemoryHoldingSubLevel(
            SubLevelHoldingChunkMap holdingChunkMap,
            UUID subLevelId,
            String description
    ) {
        try {
            Field allHoldingSubLevelsField = SubLevelHoldingChunkMap.class.getDeclaredField("allHoldingSubLevels");
            allHoldingSubLevelsField.setAccessible(true);
            Object allHoldingSubLevels = allHoldingSubLevelsField.get(holdingChunkMap);
            if (allHoldingSubLevels instanceof Map<?, ?> map) {
                ((Map<UUID, ?>) map).remove(subLevelId);
            }

            Field loadedHoldingChunksField = SubLevelHoldingChunkMap.class.getDeclaredField("loadedHoldingChunks");
            loadedHoldingChunksField.setAccessible(true);
            Object loadedHoldingChunks = loadedHoldingChunksField.get(holdingChunkMap);
            if (!(loadedHoldingChunks instanceof Map<?, ?> chunks)) {
                return;
            }

            Field loadedHoldingSubLevelsField = SubLevelHoldingChunk.class.getDeclaredField("loadedHoldingSubLevels");
            loadedHoldingSubLevelsField.setAccessible(true);
            int removed = 0;
            for (Object chunk : chunks.values()) {
                if (!(chunk instanceof SubLevelHoldingChunk holdingChunk)) {
                    continue;
                }
                Object loadedHoldingSubLevels = loadedHoldingSubLevelsField.get(holdingChunk);
                if (loadedHoldingSubLevels instanceof Map<?, ?> map
                        && ((Map<UUID, ?>) map).remove(subLevelId) != null) {
                    removed++;
                }
            }
            if (removed > 0) {
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                        "Purged {} stale in-memory Sable holding entr{} for sublevel {} before {}",
                        removed,
                        removed == 1 ? "y" : "ies",
                        subLevelId,
                        description
                );
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Could not purge stale in-memory Sable holding entries for sublevel {} before {}: {}",
                    subLevelId,
                    description,
                    exception.toString()
            );
        }
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
        Map<UUID, SubLevelData> originalDataById = new LinkedHashMap<>();
        Map<UUID, SubLevelData> translatedDataById = new LinkedHashMap<>();
        Map<UUID, List<GlobalSavedSubLevelPointer>> oldPointersById = new LinkedHashMap<>();
        Map<UUID, List<StoredShipIdentity>> storedEntriesById = scanStoredShipsByUuid(level);

        for (StoredShipIdentity member : chain) {
            List<StoredShipIdentity> existingEntries = storedEntriesById.getOrDefault(member.subLevelId(), List.of());
            StoredShipIdentity sourceEntry = existingEntries.isEmpty() ? member : existingEntries.getFirst();
            SubLevelData data = storage.attemptLoadSubLevel(sourceEntry.pointer().chunkPos(), sourceEntry.pointer().local());
            if (data == null) {
                return RecoveryResult.failure("Failed to load stored sublevel data for " + member.displayName() + ".");
            }
            SubLevelData original = SubLevelSerializer.fromData(data.fullTag().copy());
            CompoundTag translatedTag = data.fullTag().copy();
            translateStoredSubLevel(translatedTag, delta);
            SubLevelData translated = SubLevelSerializer.fromData(translatedTag);
            translated.setOriginLoadedChunk(destinationChunk);
            originalDataById.put(member.subLevelId(), original);
            translatedDataById.put(member.subLevelId(), translated);
            oldPointersById.put(
                    member.subLevelId(),
                    existingEntries.isEmpty()
                            ? List.of(member.pointer())
                            : existingEntries.stream()
                                    .map(StoredShipIdentity::pointer)
                                    .distinct()
                                    .toList()
            );
        }

        Map<net.minecraft.world.level.ChunkPos, SubLevelHoldingChunk> holdingChunks = new LinkedHashMap<>();
        java.util.function.Function<net.minecraft.world.level.ChunkPos, SubLevelHoldingChunk> loadHoldingChunk = chunkPos ->
                holdingChunks.computeIfAbsent(
                        chunkPos,
                        pos -> Optional.ofNullable(storage.attemptLoadHoldingChunk(pos)).orElse(new SubLevelHoldingChunk(pos))
                );

        Map<UUID, GlobalSavedSubLevelPointer> newPointersById = new LinkedHashMap<>();
        for (StoredShipIdentity member : chain) {
            SubLevelData translated = translatedDataById.get(member.subLevelId());
            GlobalSavedSubLevelPointer newPointer = storage.attemptSaveSubLevel(destinationChunk, translated);
            if (newPointer == null) {
                cleanupNewStoredPointers(storage, newPointersById.values());
                return RecoveryResult.failure("Failed to save moved stored sublevel data for " + member.displayName() + ".");
            }
            SubLevelData verification = storage.attemptLoadSubLevel(newPointer.chunkPos(), newPointer.local());
            if (verification == null || !verification.uuid().equals(member.subLevelId())) {
                cleanupNewStoredPointers(storage, java.util.List.of(newPointer));
                cleanupNewStoredPointers(storage, newPointersById.values());
                return RecoveryResult.failure(
                        "Saved moved stored sublevel data for " + member.displayName()
                                + " but could not verify it before removing the old pointer."
                );
            }
            newPointersById.put(member.subLevelId(), newPointer);
            loadHoldingChunk.apply(destinationChunk).getSubLevelPointers().add(newPointer.local());
        }

        for (List<GlobalSavedSubLevelPointer> oldPointers : oldPointersById.values()) {
            for (GlobalSavedSubLevelPointer oldPointer : oldPointers) {
                loadHoldingChunk.apply(oldPointer.chunkPos()).getSubLevelPointers().remove(oldPointer.local());
            }
        }

        for (List<GlobalSavedSubLevelPointer> oldPointers : oldPointersById.values()) {
            for (GlobalSavedSubLevelPointer oldPointer : oldPointers) {
                storage.attemptSaveSubLevel(oldPointer, null);
            }
        }
        for (Map.Entry<net.minecraft.world.level.ChunkPos, SubLevelHoldingChunk> entry : holdingChunks.entrySet()) {
            storage.attemptSaveHoldingChunk(entry.getKey(), entry.getValue());
        }
        refreshHoldingChunkMap(
                container,
                holdingChunkMap,
                destinationChunk,
                oldPointersById.values().stream().flatMap(List::stream).toList(),
                translatedDataById.keySet()
        );
        invalidateStoredShipCache(server);
        Optional<StoredShipIdentity> movedRoot = findStoredShipById(server, storedShip.dimension(), storedShip.subLevelId()).identity();
        if (movedRoot.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Stored Sable relocation for {} sublevel={} was not discoverable after move; restoring old stored pointers",
                    storedShip.displayName(),
                    storedShip.subLevelId()
            );
            restoreOldStoredPointers(
                    storage,
                    holdingChunkMap,
                    oldPointersById,
                    originalDataById,
                    newPointersById
            );
            invalidateStoredShipCache(server);
            return RecoveryResult.failure(
                    "Moved stored ship " + storedShip.displayName()
                            + " could not be rediscovered after relocation, so the old stored location was restored."
            );
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Verified stored Sable relocation for {} sublevel={} newPointer={} newPose={}",
                storedShip.displayName(),
                storedShip.subLevelId(),
                movedRoot.get().pointer(),
                movedRoot.get().position()
        );
        return RecoveryResult.success(
                "Recovered stored ship " + storedShip.displayName() + " to " + destination.description()
                        + ". Load the destination area to instantiate it."
        );
    }

    public static String describeStoredShip(
            MinecraftServer server,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId
    ) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return "dimension=" + dimension.location() + " is not loaded";
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        int loadedCount = container == null ? -1 : container.getAllSubLevels().size();
        boolean loaded = container != null && container.getSubLevel(subLevelId) != null;
        StoredShipLookup storedShip = findStoredShipById(server, dimension, subLevelId);
        if (storedShip.identity().isEmpty()) {
            return "dimension=" + dimension.location()
                    + ", loadedSublevels=" + loadedCount
                    + ", loadedById=" + loaded
                    + ", stored=missing"
                    + ", lookup=" + storedShip.source();
        }
        StoredShipIdentity ship = storedShip.identity().get();
        return "dimension=" + dimension.location()
                + ", loadedSublevels=" + loadedCount
                + ", loadedById=" + loaded
                + ", storedPointer=" + ship.pointer()
                + ", storedPose=" + ship.position()
                + ", lookup=" + storedShip.source();
    }

    public static boolean hasStoredShip(
            MinecraftServer server,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId
    ) {
        return findStoredShipById(server, dimension, subLevelId).identity().isPresent();
    }

    private static void cleanupNewStoredPointers(
            SubLevelStorage storage,
            Iterable<GlobalSavedSubLevelPointer> newPointers
    ) {
        for (GlobalSavedSubLevelPointer newPointer : newPointers) {
            storage.attemptSaveSubLevel(newPointer, null);
        }
    }

    private static void restoreOldStoredPointers(
            SubLevelStorage storage,
            SubLevelHoldingChunkMap holdingChunkMap,
            Map<UUID, List<GlobalSavedSubLevelPointer>> oldPointersById,
            Map<UUID, SubLevelData> originalDataById,
            Map<UUID, GlobalSavedSubLevelPointer> newPointersById
    ) {
        Set<net.minecraft.world.level.ChunkPos> affectedChunks = new LinkedHashSet<>();
        for (GlobalSavedSubLevelPointer newPointer : newPointersById.values()) {
            SubLevelHoldingChunk holdingChunk = Optional.ofNullable(storage.attemptLoadHoldingChunk(newPointer.chunkPos()))
                    .orElse(new SubLevelHoldingChunk(newPointer.chunkPos()));
            holdingChunk.getSubLevelPointers().remove(newPointer.local());
            storage.attemptSaveHoldingChunk(newPointer.chunkPos(), holdingChunk);
            storage.attemptSaveSubLevel(newPointer, null);
            affectedChunks.add(newPointer.chunkPos());
        }
        for (Map.Entry<UUID, List<GlobalSavedSubLevelPointer>> entry : oldPointersById.entrySet()) {
            SubLevelData original = originalDataById.get(entry.getKey());
            if (original == null) {
                continue;
            }
            for (GlobalSavedSubLevelPointer oldPointer : entry.getValue()) {
                SubLevelHoldingChunk holdingChunk = Optional.ofNullable(storage.attemptLoadHoldingChunk(oldPointer.chunkPos()))
                        .orElse(new SubLevelHoldingChunk(oldPointer.chunkPos()));
                if (!holdingChunk.getSubLevelPointers().contains(oldPointer.local())) {
                    holdingChunk.getSubLevelPointers().add(oldPointer.local());
                }
                storage.attemptSaveHoldingChunk(oldPointer.chunkPos(), holdingChunk);
                storage.attemptSaveSubLevel(oldPointer, original);
                affectedChunks.add(oldPointer.chunkPos());
            }
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

    public static void pruneLoadedDuplicateStoredShips(MinecraftServer server) {
        Objects.requireNonNull(server, "server");

        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Scanning Sable storage for stored entries that duplicate currently loaded sublevels"
        );
        boolean changed = false;
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null) {
                CreateAeronauticsAutomatedLogistics.debugVehicle(
                        "Skipping loaded-duplicate cleanup in {} because no Sable sublevel container is available",
                        level.dimension().location()
                );
                continue;
            }
            Set<UUID> loadedIds = container.getAllSubLevels().stream()
                    .filter(subLevel -> !subLevel.isRemoved())
                    .map(ServerSubLevel::getUniqueId)
                    .collect(java.util.stream.Collectors.toSet());
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Loaded-duplicate cleanup in {} sees {} loaded Sable sublevel(s)",
                    level.dimension().location(),
                    loadedIds.size()
            );
            if (loadedIds.isEmpty()) {
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

            int removedForLevel = 0;
            for (java.io.File regionFile : regionFiles) {
                String fileName = regionFile.getName();
                String trimmed = fileName.substring(0, fileName.length() - ".slvlr".length());
                String[] parts = trimmed.split("\\.");
                if (parts.length != 3) {
                    continue;
                }

                final int regionX;
                final int regionZ;
                try {
                    regionX = Integer.parseInt(parts[1]);
                    regionZ = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
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

                        boolean chunkChanged = false;
                        Iterator<SavedSubLevelPointer> pointerIterator = holdingChunk.getSubLevelPointers().iterator();
                        while (pointerIterator.hasNext()) {
                            SavedSubLevelPointer pointer = pointerIterator.next();
                            SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                            if (data == null || !loadedIds.contains(data.uuid())) {
                                continue;
                            }
                            pointerIterator.remove();
                            storage.attemptSaveSubLevel(new GlobalSavedSubLevelPointer(chunkPos, pointer.storageIndex(), pointer.subLevelIndex()), null);
                            removedForLevel++;
                            chunkChanged = true;
                            changed = true;
                            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                                    "Pruned stale stored holding entry for loaded Sable sublevel {} in chunk {} pointer storage={} sublevel={}",
                                    data.uuid(),
                                    chunkPos,
                                    pointer.storageIndex(),
                                    pointer.subLevelIndex()
                            );
                        }

                        if (chunkChanged) {
                            storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
                        }
                    }
                }
            }

            if (removedForLevel > 0) {
                CreateAeronauticsAutomatedLogistics.debugVehicle(
                        "Pruned {} stale stored holding sublevel entr{} in {} because the live sublevel was already loaded",
                        removedForLevel,
                        removedForLevel == 1 ? "y" : "ies",
                        level.dimension().location()
                );
            }
        }

        if (changed) {
            invalidateStoredShipCache(server);
        }
    }

    public static int pruneStoredEntriesForLoadedShip(
            MinecraftServer server,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(subLevelId, "subLevelId");

        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return 0;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return 0;
        }
        var loaded = container.getSubLevel(subLevelId);
        if (!(loaded instanceof ServerSubLevel loadedSubLevel) || loadedSubLevel.isRemoved()) {
            return 0;
        }
        GlobalSavedSubLevelPointer currentPointer = loadedSubLevel.getLastSerializationPointer();
        SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        if (holdingChunkMap == null) {
            return 0;
        }
        SubLevelStorage storage = holdingChunkMap.getStorage();
        java.io.File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(".slvlr"));
        if (regionFiles == null) {
            return 0;
        }

        int removed = 0;
        Set<net.minecraft.world.level.ChunkPos> affectedChunks = new LinkedHashSet<>();
        for (java.io.File regionFile : regionFiles) {
            removed += scanHoldingRegion(
                    storage,
                    regionFile,
                    (chunkPos, pointer, data) -> {
                        if (data == null) {
                            return PointerCleanupDecision.removePointerOnly(
                                    "missing stored sublevel data"
                            );
                        }
                        if (!data.uuid().equals(subLevelId)) {
                            return PointerCleanupDecision.keep();
                        }
                        GlobalSavedSubLevelPointer globalPointer = new GlobalSavedSubLevelPointer(
                                chunkPos,
                                pointer.storageIndex(),
                                pointer.subLevelIndex()
                        );
                        if (currentPointer != null && currentPointer.equals(globalPointer)) {
                            return PointerCleanupDecision.keep();
                        }
                        return PointerCleanupDecision.removePointerAndData(
                                "duplicate stored pointer for already-loaded sublevel " + subLevelId
                        );
                    },
                    affectedChunks
            );
        }
        if (removed > 0) {
            invalidateStoredShipCache(server);
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Pruned {} stale Sable holding pointer{} for loaded sublevel {} in {} while keeping current pointer {}",
                    removed,
                    removed == 1 ? "" : "s",
                    subLevelId,
                    dimension.location(),
                    currentPointer
            );
        }
        return removed;
    }

    public static int pruneDanglingStoredShipEntries(MinecraftServer server) {
        Objects.requireNonNull(server, "server");

        int removed = 0;
        boolean changed = false;
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null || container.getHoldingChunkMap() == null) {
                continue;
            }
            SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
            java.io.File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(".slvlr"));
            if (regionFiles == null) {
                continue;
            }

            Map<LocalPlotKey, UUID> loadedPlots = loadedPlotOccupants(container);
            Set<net.minecraft.world.level.ChunkPos> affectedChunks = new LinkedHashSet<>();
            int removedForLevel = 0;
            for (java.io.File regionFile : regionFiles) {
                removedForLevel += scanHoldingRegion(
                        storage,
                        regionFile,
                        (chunkPos, pointer, data) -> {
                            if (data == null) {
                                return PointerCleanupDecision.removePointerOnly("missing stored sublevel data");
                            }
                            Optional<LocalPlotKey> storedPlot = storedLocalPlot(data);
                            if (storedPlot.isEmpty()) {
                                return PointerCleanupDecision.keep();
                            }
                            UUID occupyingId = loadedPlots.get(storedPlot.get());
                            if (occupyingId != null && !occupyingId.equals(data.uuid())) {
                                return PointerCleanupDecision.removePointerAndData(
                                        "stored sublevel " + data.uuid()
                                                + " targets occupied local Sable plot " + storedPlot.get()
                                                + " already used by loaded sublevel " + occupyingId
                                );
                            }
                            return PointerCleanupDecision.keep();
                        },
                        affectedChunks
                );
            }
            if (removedForLevel > 0) {
                changed = true;
                removed += removedForLevel;
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                        "Pruned {} stale/colliding Sable holding pointer{} in {}",
                        removedForLevel,
                        removedForLevel == 1 ? "" : "s",
                        level.dimension().location()
                );
            }
        }
        if (changed) {
            invalidateStoredShipCache(server);
        }
        return removed;
    }

    private static Map<LocalPlotKey, UUID> loadedPlotOccupants(ServerSubLevelContainer container) {
        Map<LocalPlotKey, UUID> occupants = new HashMap<>();
        var origin = container.getOrigin();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            int localX = subLevel.getPlot().plotPos.x - origin.x;
            int localZ = subLevel.getPlot().plotPos.z - origin.y;
            occupants.put(new LocalPlotKey(localX, localZ), subLevel.getUniqueId());
        }
        return occupants;
    }

    private static Optional<LocalPlotKey> storedLocalPlot(SubLevelData data) {
        CompoundTag fullTag = data.fullTag();
        if (!fullTag.contains("plot", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag plotTag = fullTag.getCompound("plot");
        if (!plotTag.contains("plot_x", Tag.TAG_ANY_NUMERIC)
                || !plotTag.contains("plot_z", Tag.TAG_ANY_NUMERIC)) {
            return Optional.empty();
        }
        return Optional.of(new LocalPlotKey(plotTag.getInt("plot_x"), plotTag.getInt("plot_z")));
    }

    private record LocalPlotKey(int x, int z) {
    }

    private static int scanHoldingRegion(
            SubLevelStorage storage,
            java.io.File regionFile,
            PointerCleanupRule cleanupRule,
            Set<net.minecraft.world.level.ChunkPos> affectedChunks
    ) {
        String fileName = regionFile.getName();
        String trimmed = fileName.substring(0, fileName.length() - ".slvlr".length());
        String[] parts = trimmed.split("\\.");
        if (parts.length != 3) {
            return 0;
        }

        final int regionX;
        final int regionZ;
        try {
            regionX = Integer.parseInt(parts[1]);
            regionZ = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return 0;
        }

        int removed = 0;
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

                boolean chunkChanged = false;
                Iterator<SavedSubLevelPointer> pointerIterator = holdingChunk.getSubLevelPointers().iterator();
                while (pointerIterator.hasNext()) {
                    SavedSubLevelPointer pointer = pointerIterator.next();
                    SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                    PointerCleanupDecision decision = cleanupRule.decide(chunkPos, pointer, data);
                    if (!decision.removePointer()) {
                        continue;
                    }

                    pointerIterator.remove();
                    GlobalSavedSubLevelPointer globalPointer = new GlobalSavedSubLevelPointer(
                            chunkPos,
                            pointer.storageIndex(),
                            pointer.subLevelIndex()
                    );
                    if (decision.removeData()) {
                        storage.attemptSaveSubLevel(globalPointer, null);
                    }
                    removed++;
                    chunkChanged = true;
                    affectedChunks.add(chunkPos);
                    CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                            "Pruned Sable holding pointer {} because {}",
                            globalPointer,
                            decision.reason()
                    );
                }

                if (chunkChanged) {
                    storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
                }
            }
        }
        return removed;
    }

    @FunctionalInterface
    private interface PointerCleanupRule {
        PointerCleanupDecision decide(
                net.minecraft.world.level.ChunkPos chunkPos,
                SavedSubLevelPointer pointer,
                SubLevelData data
        );
    }

    private record PointerCleanupDecision(boolean removePointer, boolean removeData, String reason) {
        static PointerCleanupDecision keep() {
            return new PointerCleanupDecision(false, false, "");
        }

        static PointerCleanupDecision removePointerOnly(String reason) {
            return new PointerCleanupDecision(true, false, reason);
        }

        static PointerCleanupDecision removePointerAndData(String reason) {
            return new PointerCleanupDecision(true, true, reason);
        }
    }

    public static void pruneDuplicateStoredShipEntries(MinecraftServer server) {
        pruneDuplicateStoredShipEntries(server, Map.of());
    }

    public static void pruneDuplicateStoredShipEntries(MinecraftServer server, Map<UUID, Vec3> preferredPositions) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(preferredPositions, "preferredPositions");

        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Scanning Sable storage for duplicate stored ship entries; preferred positions={}",
                preferredPositions.keySet()
        );
        boolean changed = false;
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
            if (container == null) {
                CreateAeronauticsAutomatedLogistics.debugVehicle(
                        "Skipping duplicate stored-ship cleanup in {} because no Sable sublevel container is available",
                        level.dimension().location()
                );
                continue;
            }
            SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
            if (holdingChunkMap == null) {
                continue;
            }
            SubLevelStorage storage = holdingChunkMap.getStorage();
            Map<UUID, List<StoredShipIdentity>> storedByUuid = scanStoredShipsByUuid(level);
            Set<net.minecraft.world.level.ChunkPos> affectedChunks = new LinkedHashSet<>();

            for (Map.Entry<UUID, List<StoredShipIdentity>> entry : storedByUuid.entrySet()) {
                List<StoredShipIdentity> candidates = entry.getValue().stream()
                        .filter(candidate -> candidate.dimension().equals(level.dimension()))
                        .toList();
                if (candidates.size() <= 1) {
                    continue;
                }

                StoredShipIdentity keep = selectPreferredStoredShip(candidates, preferredPositions.get(entry.getKey()));
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                        "Duplicate stored Sable entries found for {} in {}: count={} keeping={} position={} preferred={}",
                        entry.getKey(),
                        level.dimension().location(),
                        candidates.size(),
                        keep.pointer(),
                        keep.position(),
                        preferredPositions.get(entry.getKey())
                );
                int removedForUuid = 0;
                for (StoredShipIdentity candidate : candidates) {
                    if (candidate.pointer().equals(keep.pointer())) {
                        continue;
                    }
                    SubLevelHoldingChunk holdingChunk = Optional.ofNullable(storage.attemptLoadHoldingChunk(candidate.pointer().chunkPos()))
                            .orElse(new SubLevelHoldingChunk(candidate.pointer().chunkPos()));
                    if (holdingChunk.getSubLevelPointers().remove(candidate.pointer().local())) {
                        storage.attemptSaveHoldingChunk(candidate.pointer().chunkPos(), holdingChunk);
                    }
                    storage.attemptSaveSubLevel(candidate.pointer(), null);
                    affectedChunks.add(candidate.pointer().chunkPos());
                    removedForUuid++;
                    changed = true;
                    CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                            "Pruned duplicate stored Sable entry for {} in {} pointer={} position={} keptPointer={}",
                            entry.getKey(),
                            level.dimension().location(),
                            candidate.pointer(),
                            candidate.position(),
                            keep.pointer()
                    );
                }

                if (removedForUuid > 0) {
                    CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                            "Pruned {} duplicate stored holding entr{} for Sable sublevel {} in {}. Keeping pointer {} at position {}",
                            removedForUuid,
                            removedForUuid == 1 ? "y" : "ies",
                            entry.getKey(),
                            level.dimension().location(),
                            keep.pointer(),
                            keep.position()
                    );
                }
            }

            if (!affectedChunks.isEmpty()) {
                for (net.minecraft.world.level.ChunkPos chunkPos : affectedChunks) {
                    holdingChunkMap.updateChunkStatus(chunkPos, false);
                }
                holdingChunkMap.processChanges();
            }
        }

        if (changed) {
            invalidateStoredShipCache(server);
        }
    }

    private static void refreshHoldingChunkMap(
            ServerSubLevelContainer container,
            SubLevelHoldingChunkMap holdingChunkMap,
            net.minecraft.world.level.ChunkPos destinationChunk,
            Iterable<GlobalSavedSubLevelPointer> oldPointers,
            Set<UUID> movedSubLevelIds
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
        boolean movedChainAlreadyLoaded = movedSubLevelIds.stream().anyMatch(id -> container.getSubLevel(id) != null);
        if (!movedChainAlreadyLoaded) {
            holdingChunkMap.processChanges();
        }
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

    private static StoredShipLookup findStoredShipById(
            MinecraftServer server,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId
    ) {
        Optional<StoredShipIdentity> cachedMatch = findStoredShipByIdIn(storedShips(server), dimension, subLevelId);
        if (cachedMatch.isPresent()) {
            return new StoredShipLookup(cachedMatch, StoredShipLookupSource.CACHE);
        }

        Optional<StoredShipIdentity> refreshedMatch = findStoredShipByIdIn(refreshStoredShips(server), dimension, subLevelId);
        if (refreshedMatch.isPresent()) {
            return new StoredShipLookup(refreshedMatch, StoredShipLookupSource.FRESH_RESCAN);
        }
        return new StoredShipLookup(Optional.empty(), StoredShipLookupSource.NOT_FOUND_AFTER_RESCAN);
    }

    private static Optional<StoredShipIdentity> findStoredShipByIdIn(
            List<StoredShipIdentity> candidates,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID subLevelId
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.subLevelId().equals(subLevelId))
                .filter(candidate -> candidate.dimension().equals(dimension))
                .findFirst();
    }

    private static List<StoredShipIdentity> refreshStoredShips(MinecraftServer server) {
        List<StoredShipIdentity> scanned = scanStoredShips(server);
        ServerLevel overworld = server.overworld();
        long now = overworld == null ? 0L : overworld.getGameTime();
        StoredShipScanCacheHolder.CACHE_BY_SERVER.put(server, new StoredShipScanCache(now, scanned));
        return scanned;
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

    private static Map<UUID, List<StoredShipIdentity>> scanStoredShipsByUuid(ServerLevel level) {
        Map<UUID, List<StoredShipIdentity>> discovered = new LinkedHashMap<>();
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return discovered;
        }
        SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        if (holdingChunkMap == null) {
            return discovered;
        }
        SubLevelStorage storage = holdingChunkMap.getStorage();
        java.io.File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(".slvlr"));
        if (regionFiles == null) {
            return discovered;
        }
        for (java.io.File regionFile : regionFiles) {
            scanRegionFile(level, storage, regionFile, identity -> discovered.computeIfAbsent(identity.subLevelId(), ignored -> new ArrayList<>()).add(identity));
        }
        return discovered;
    }

    private static void scanRegionFile(
            ServerLevel level,
            SubLevelStorage storage,
            java.io.File regionFile,
            Map<UUID, StoredShipIdentity> discovered
    ) {
        scanRegionFile(level, storage, regionFile, identity -> discovered.putIfAbsent(identity.subLevelId(), identity));
    }

    private static void scanRegionFile(
            ServerLevel level,
            SubLevelStorage storage,
            java.io.File regionFile,
            java.util.function.Consumer<StoredShipIdentity> consumer
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
                    consumer.accept(identity);
                }
            }
        }
    }

    private static StoredShipIdentity selectPreferredStoredShip(List<StoredShipIdentity> candidates, Vec3 preferredPosition) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates");
        }
        if (preferredPosition == null) {
            return candidates.getFirst();
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(preferredPosition)))
                .orElse(candidates.getFirst());
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
                List.copyOf(data.dependencies()),
                fullTag.copy()
        );
    }

    private record StoredShipIdentity(
            UUID subLevelId,
            String displayName,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            Vec3 position,
            GlobalSavedSubLevelPointer pointer,
            List<UUID> dependencies,
            CompoundTag fullTag
    ) {
        Optional<ControllerPoseCalculation> posePositionForController(BlockPos localControllerPos, Vec3 destinationControllerPosition) {
            CompoundTag poseTag = fullTag.getCompound("pose");
            if (!poseTag.contains("position") || !poseTag.contains("rotation_point") || !poseTag.contains("orientation")) {
                return Optional.empty();
            }
            CompoundTag rotationPointTag = poseTag.getCompound("rotation_point");
            CompoundTag orientationTag = poseTag.getCompound("orientation");
            Quaterniond orientation = new Quaterniond(
                    orientationTag.getDouble("x"),
                    orientationTag.getDouble("y"),
                    orientationTag.getDouble("z"),
                    orientationTag.getDouble("w")
            ).normalize();
            Vector3d rotatedAnchorOffset = new Vector3d(
                    localControllerPos.getX() + 0.5D - rotationPointTag.getDouble("x"),
                    localControllerPos.getY() + 0.5D - rotationPointTag.getDouble("y"),
                    localControllerPos.getZ() + 0.5D - rotationPointTag.getDouble("z")
            );
            double maxExpectedAnchorOffset = maxExpectedAnchorOffset();
            if (!isFinite(rotatedAnchorOffset.x, rotatedAnchorOffset.y, rotatedAnchorOffset.z)
                    || rotatedAnchorOffset.length() > maxExpectedAnchorOffset) {
                return Optional.empty();
            }
            orientation.transform(rotatedAnchorOffset);
            if (!isFinite(rotatedAnchorOffset.x, rotatedAnchorOffset.y, rotatedAnchorOffset.z)
                    || rotatedAnchorOffset.length() > maxExpectedAnchorOffset) {
                return Optional.empty();
            }
            Vec3 posePosition = new Vec3(
                    destinationControllerPosition.x - rotatedAnchorOffset.x,
                    destinationControllerPosition.y - rotatedAnchorOffset.y,
                    destinationControllerPosition.z - rotatedAnchorOffset.z
            );
            if (!isFinite(posePosition.x, posePosition.y, posePosition.z)) {
                return Optional.empty();
            }
            return Optional.of(new ControllerPoseCalculation(
                    posePosition,
                    new Vec3(rotatedAnchorOffset.x, rotatedAnchorOffset.y, rotatedAnchorOffset.z),
                    maxExpectedAnchorOffset
            ));
        }

        private double maxExpectedAnchorOffset() {
            CompoundTag boundsTag = fullTag.getCompound("world_bounds");
            double sizeX = Math.abs(boundsTag.getDouble("maxX") - boundsTag.getDouble("minX"));
            double sizeY = Math.abs(boundsTag.getDouble("maxY") - boundsTag.getDouble("minY"));
            double sizeZ = Math.abs(boundsTag.getDouble("maxZ") - boundsTag.getDouble("minZ"));
            double diagonal = Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ);
            if (!Double.isFinite(diagonal) || diagonal <= 0.0D) {
                return 1024.0D;
            }
            return Math.max(1024.0D, diagonal * 2.0D);
        }
    }

    private record ControllerPoseCalculation(Vec3 posePosition, Vec3 rotatedAnchorOffset, double maxExpectedAnchorOffset) {
    }

    private static boolean isFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private record StoredShipScanCache(long gameTime, List<StoredShipIdentity> ships) {
    }

    private record StoredShipLookup(Optional<StoredShipIdentity> identity, StoredShipLookupSource source) {
    }

    private enum StoredShipLookupSource {
        CACHE,
        FRESH_RESCAN,
        NOT_FOUND_AFTER_RESCAN
    }

    private static final class StoredShipScanCacheHolder {
        private static final Map<MinecraftServer, StoredShipScanCache> CACHE_BY_SERVER = new HashMap<>();

        private StoredShipScanCacheHolder() {
        }
    }

}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.SableStoredShipRepository;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.ShipBodyDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.StoredBodyCandidate;

/** Command-facing ship and station lookup. Physical body recovery belongs to ShipMaterializationService. */
public final class ShipRecoveryService {
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

    public static RecoveryResult recoverToStation(
            ServerLevel shipLevel,
            ShipSelector selector,
            StationSelector stationSelector
    ) {
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
        Optional<ShipBodyDirectorySavedData.BodyIdentity> bodyIdentity = shipLookup.bodyIdentity(
                destination.level().getServer()
        );
        if (bodyIdentity.isEmpty()) {
            return RecoveryResult.failure(
                    "No verified body-directory metadata exists for " + shipLookup.displayName()
                            + "; refusing recovery without a proven Sable UUID and local controller position."
            );
        }
        ShipBodyDirectorySavedData.BodyIdentity body = bodyIdentity.get();
        if (!body.dimension().equals(destination.level().dimension())) {
            return RecoveryResult.failure("Cross-dimension ship recovery is not supported.");
        }
        ShipMaterializationService.MaterializationResult result = AutomatedLogisticsServices.MATERIALIZATION.materializeStoredBodyAt(
                new ShipMaterializationService.MaterializationRequest(
                        destination.level().getServer(),
                        body.dimension(),
                        Optional.of(body.transponderId()),
                        Optional.of(body.sableShipId()),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(body.localControllerPos()),
                        destination.position(),
                        "admin recovery to " + destination.description(),
                        "admin_recovery",
                        true,
                        true
                )
        );
        if (result.type() == ShipMaterializationService.MaterializationResultType.LOADED_BODY_AVAILABLE) {
            return RecoveryResult.failure(
                    "Ship " + shipLookup.displayName()
                            + " is already loaded; use Sable's teleport command for explicit live-body movement."
            );
        }
        return result.success()
                ? RecoveryResult.success("Recovered " + shipLookup.displayName() + " to " + destination.description() + ".")
                : RecoveryResult.failure("Recovery refused: " + result.message() + " (" + result.reasonCode() + ")");
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
        Vec3 target = shipLookup.lastKnownPosition().orElse(null);
        if (target == null) {
            return RecoveryResult.failure("No stored location is known for " + shipLookup.displayName() + ".");
        }
        player.teleportTo(targetLevel, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        return RecoveryResult.success("Teleported to " + shipLookup.displayName() + ".");
    }

    private static ShipLookup resolveShip(ServerLevel preferredLevel, ShipSelector selector) {
        IdentityDirectorySavedData identities = IdentityDirectorySavedData.get(preferredLevel.getServer());
        return switch (selector) {
            case ShipSelector.ByTransponder(UUID transponderId) -> identities.ship(transponderId)
                    .map(ShipLookup::success)
                    .orElseGet(() -> ShipLookup.failure("No ship transponder found for " + transponderId + "."));
            case ShipSelector.ByStoredSubLevelId(UUID subLevelId) -> resolveStoredById(
                    preferredLevel.getServer(),
                    preferredLevel.dimension(),
                    subLevelId
            );
            case ShipSelector.ByName(String shipName) -> {
                List<IdentityDirectorySavedData.PersistedShipIdentity> matches = identities.allShips().stream()
                        .filter(identity -> identity.shipName().equalsIgnoreCase(shipName))
                        .toList();
                if (!matches.isEmpty()) {
                    yield selectPersistedByDimension(matches, preferredLevel.dimension(), shipName);
                }
                yield selectStoredByName(preferredLevel.getServer(), preferredLevel.dimension(), shipName);
            }
        };
    }

    private static ShipLookup resolveStoredById(
            MinecraftServer server,
            ResourceKey<Level> preferredDimension,
            UUID sableShipId
    ) {
        List<ResourceKey<Level>> dimensions = SableStoredShipRepository.allCandidates(server).stream()
                .filter(candidate -> candidate.sableShipId().equals(sableShipId))
                .map(StoredBodyCandidate::dimension)
                .distinct()
                .toList();
        if (dimensions.isEmpty()) {
            return ShipLookup.failure("No stored Sable ship found for " + sableShipId + ".");
        }
        ResourceKey<Level> dimension = dimensions.contains(preferredDimension)
                ? preferredDimension
                : dimensions.size() == 1 ? dimensions.getFirst() : null;
        if (dimension == null) {
            return ShipLookup.failure("Stored Sable ship " + sableShipId + " exists in multiple dimensions.");
        }
        return SableStoredShipRepository.lookup(server, Optional.empty(), dimension, sableShipId)
                .selected()
                .map(ShipLookup::success)
                .orElseGet(() -> ShipLookup.failure(
                        "Stored Sable ship " + sableShipId + " is present but cannot be selected safely."
                ));
    }

    private static ShipLookup selectPersistedByDimension(
            List<IdentityDirectorySavedData.PersistedShipIdentity> matches,
            ResourceKey<Level> preferredDimension,
            String shipName
    ) {
        if (matches.size() == 1) {
            return ShipLookup.success(matches.getFirst());
        }
        List<IdentityDirectorySavedData.PersistedShipIdentity> sameDimension = matches.stream()
                .filter(identity -> identity.dimension().equals(preferredDimension))
                .toList();
        return sameDimension.size() == 1
                ? ShipLookup.success(sameDimension.getFirst())
                : ShipLookup.failure("Ship name \"" + shipName + "\" is ambiguous. Use the transponder id instead.");
    }

    private static ShipLookup selectStoredByName(
            MinecraftServer server,
            ResourceKey<Level> preferredDimension,
            String shipName
    ) {
        List<StoredBodyCandidate> matches = SableStoredShipRepository.allCandidates(server).stream()
                .filter(candidate -> candidate.displayName().equalsIgnoreCase(shipName))
                .toList();
        if (matches.isEmpty()) {
            return ShipLookup.failure("No ship named \"" + shipName + "\" is currently known.");
        }
        List<StoredBodyKey> keys = matches.stream()
                .map(candidate -> new StoredBodyKey(candidate.dimension(), candidate.sableShipId()))
                .distinct()
                .toList();
        List<StoredBodyKey> preferred = keys.stream()
                .filter(key -> key.dimension().equals(preferredDimension))
                .toList();
        StoredBodyKey selectedKey = preferred.size() == 1
                ? preferred.getFirst()
                : keys.size() == 1 ? keys.getFirst() : null;
        if (selectedKey == null) {
            return ShipLookup.failure("Ship name \"" + shipName + "\" is ambiguous across stored Sable bodies.");
        }
        return SableStoredShipRepository.lookup(
                        server,
                        Optional.empty(),
                        selectedKey.dimension(),
                        selectedKey.sableShipId()
                )
                .selected()
                .map(ShipLookup::success)
                .orElseGet(() -> ShipLookup.failure(
                        "Stored ship \"" + shipName + "\" is present but cannot be selected safely."
                ));
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
                yield sameDimension.size() == 1
                        ? StationLookup.success(sameDimension.getFirst())
                        : StationLookup.failure(
                                "Station name \"" + stationName + "\" is ambiguous. Use the station id instead."
                        );
            }
        };
    }

    public static List<String> knownShipNames(MinecraftServer server) {
        Set<String> names = new HashSet<>();
        IdentityDirectorySavedData.get(server).allShips().stream()
                .map(IdentityDirectorySavedData.PersistedShipIdentity::shipName)
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        SableStoredShipRepository.allCandidates(server).stream()
                .map(StoredBodyCandidate::displayName)
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static List<String> knownStoredShipIds(MinecraftServer server) {
        return SableStoredShipRepository.allCandidates(server).stream()
                .map(candidate -> candidate.sableShipId().toString())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownShipIds(MinecraftServer server) {
        return IdentityDirectorySavedData.get(server).allShips().stream()
                .map(identity -> identity.transponderId().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownStationNames(MinecraftServer server) {
        return IdentityDirectorySavedData.get(server).allStations().stream()
                .map(IdentityDirectorySavedData.PersistedStationIdentity::stationName)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static List<String> knownStationIds(MinecraftServer server) {
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

    private record StoredBodyKey(ResourceKey<Level> dimension, UUID sableShipId) {
    }

    private record ShipLookup(
            IdentityDirectorySavedData.PersistedShipIdentity indexedShip,
            StoredBodyCandidate storedBody,
            String error
    ) {
        static ShipLookup success(IdentityDirectorySavedData.PersistedShipIdentity ship) {
            return new ShipLookup(ship, null, null);
        }

        static ShipLookup success(StoredBodyCandidate body) {
            return new ShipLookup(null, body, null);
        }

        static ShipLookup failure(String error) {
            return new ShipLookup(null, null, error);
        }

        boolean success() {
            return indexedShip != null || storedBody != null;
        }

        ResourceKey<Level> dimension() {
            return indexedShip != null ? indexedShip.dimension() : storedBody.dimension();
        }

        String displayName() {
            return indexedShip != null ? indexedShip.shipName() : storedBody.displayName();
        }

        Optional<Vec3> lastKnownPosition() {
            return indexedShip != null ? indexedShip.lastKnownPosition() : Optional.of(storedBody.posePosition());
        }

        RecoveryResult asRecoveryFailure() {
            return RecoveryResult.failure(error);
        }

        Optional<ShipBodyDirectorySavedData.BodyIdentity> bodyIdentity(MinecraftServer server) {
            ShipBodyDirectorySavedData directory = ShipBodyDirectorySavedData.get(server);
            if (indexedShip != null) {
                return directory.byTransponder(indexedShip.transponderId());
            }
            List<ShipBodyDirectorySavedData.BodyIdentity> matches = directory.bySableShipId(storedBody.sableShipId()).stream()
                    .filter(identity -> identity.dimension().equals(storedBody.dimension()))
                    .toList();
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
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
}

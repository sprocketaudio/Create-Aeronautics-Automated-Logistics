package net.sprocketgames.create_aeronautics_automated_logistics.compat;

import java.util.Optional;
import java.util.UUID;
import net.neoforged.fml.ModList;

public final class FtbTeamsCompat {
    private static final String MOD_ID = "ftbteams";

    private FtbTeamsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean areSameTeam(UUID firstPlayerId, UUID secondPlayerId) {
        return isLoaded() && FtbTeamsApiBridge.areSameTeam(firstPlayerId, secondPlayerId);
    }

    public static boolean areAllied(UUID assetOwnerId, UUID playerId) {
        return isLoaded() && FtbTeamsApiBridge.areAllied(assetOwnerId, playerId);
    }

    public static UUID activeVehicleLimitBucket(UUID ownerId) {
        return isLoaded() ? FtbTeamsApiBridge.activeVehicleLimitBucket(ownerId) : ownerId;
    }

    public static Optional<UUID> teamIdForPlayer(UUID playerId) {
        return isLoaded() ? FtbTeamsApiBridge.teamIdForPlayer(playerId) : Optional.empty();
    }

    public static Optional<Boolean> canControlClientSide(UUID playerId, UUID assetOwnerId, boolean allowTeam, boolean allowAllies) {
        return isLoaded()
                ? FtbTeamsApiBridge.canControlClientSide(playerId, assetOwnerId, allowTeam, allowAllies)
                : Optional.of(false);
    }

    public static Optional<Boolean> canControlClientSide(
            UUID playerId,
            UUID assetOwnerId,
            Optional<UUID> fallbackOwnerTeamId,
            boolean allowTeam,
            boolean allowAllies
    ) {
        return isLoaded()
                ? FtbTeamsApiBridge.canControlClientSide(playerId, assetOwnerId, fallbackOwnerTeamId, allowTeam, allowAllies)
                : Optional.of(false);
    }
}

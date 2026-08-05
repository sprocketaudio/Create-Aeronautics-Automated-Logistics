package net.sprocketgames.create_aeronautics_automated_logistics.compat;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import java.util.Optional;
import java.util.UUID;

final class FtbTeamsApiBridge {
    private FtbTeamsApiBridge() {
    }

    static boolean areSameTeam(UUID firstPlayerId, UUID secondPlayerId) {
        return manager().map(manager -> manager.arePlayersInSameTeam(firstPlayerId, secondPlayerId)).orElse(false);
    }

    static boolean areAllied(UUID assetOwnerId, UUID playerId) {
        return manager()
                .flatMap(manager -> manager.getTeamForPlayerID(assetOwnerId))
                .map(team -> team.getRankForPlayer(playerId).isAllyOrBetter()
                        && !team.getMembers().contains(playerId))
                .orElse(false);
    }

    static UUID activeVehicleLimitBucket(UUID ownerId) {
        return manager()
                .flatMap(manager -> manager.getTeamForPlayerID(ownerId))
                .map(Team::getTeamId)
                .orElse(ownerId);
    }

    static Optional<UUID> teamIdForPlayer(UUID playerId) {
        return manager()
                .flatMap(manager -> manager.getTeamForPlayerID(playerId))
                .map(Team::getTeamId);
    }

    static Optional<Boolean> canControlClientSide(
            UUID playerId,
            UUID assetOwnerId,
            boolean allowTeam,
            boolean allowAllies
    ) {
        return canControlClientSide(playerId, assetOwnerId, Optional.empty(), allowTeam, allowAllies);
    }

    static Optional<Boolean> canControlClientSide(
            UUID playerId,
            UUID assetOwnerId,
            Optional<UUID> fallbackOwnerTeamId,
            boolean allowTeam,
            boolean allowAllies
    ) {
        try {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (!api.isClientManagerLoaded()) {
                return Optional.empty();
            }
            var manager = api.getClientManager();
            Optional<UUID> playerTeamId = manager.getKnownPlayer(playerId).map(player -> player.teamId());
            Optional<UUID> ownerTeamId = manager.getKnownPlayer(assetOwnerId).map(player -> player.teamId())
                    .or(() -> fallbackOwnerTeamId);
            if (playerTeamId.isEmpty() || ownerTeamId.isEmpty()) {
                return Optional.empty();
            }
            if (allowTeam && playerTeamId.get().equals(ownerTeamId.get())) {
                return Optional.of(true);
            }
            if (!allowAllies) {
                return Optional.of(false);
            }
            return ownerTeamId
                    .flatMap(manager::getTeamByID)
                    .map(team -> team.getRankForPlayer(playerId).isAllyOrBetter()
                            && !team.getMembers().contains(playerId));
        } catch (IllegalStateException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<TeamManager> manager() {
        try {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            return api.isManagerLoaded() ? Optional.of(api.getManager()) : Optional.empty();
        } catch (IllegalStateException | NullPointerException ignored) {
            return Optional.empty();
        }
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;

public final class StationPermissionService {
    private static final Component DENIED_MESSAGE =
            Component.translatable("message.create_aeronautics_automated_logistics.station.permission_denied");
    private static final Component USE_DENIED_MESSAGE =
            Component.translatable("message.create_aeronautics_automated_logistics.station.use_denied");

    private StationPermissionService() {
    }

    public static boolean isEnabled() {
        return AutomatedLogisticsConfig.ownershipPermissionsEnabled();
    }

    public static boolean canControl(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!isEnabled()) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        Optional<UUID> ownerId = station.ownerId();
        return ownerId.isEmpty() || canControlOwner(player.getUUID(), ownerId.get());
    }

    public static boolean canUse(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!isEnabled()) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        Optional<UUID> ownerId = station.ownerId();
        return ownerId.isEmpty() || canUseOwner(player.getUUID(), ownerId.get());
    }

    public static boolean canControl(UUID playerId, boolean isOp, AirshipStationSnapshot station) {
        if (!isEnabled()) {
            return true;
        }
        if (isOp) {
            return true;
        }
        Optional<UUID> ownerId = station.ownerId();
        if (ownerId.isEmpty() || ownerId.get().equals(playerId)) {
            return true;
        }
        return FtbTeamsCompat.canControlClientSide(
                        playerId,
                        ownerId.get(),
                        AutomatedLogisticsConfig.ALLOW_TEAM_STATION_CONTROL.get(),
                        AutomatedLogisticsConfig.ALLOW_ALLIED_STATION_CONTROL.get())
                .orElse(true);
    }

    public static boolean canUse(UUID playerId, boolean isOp, AirshipStationSnapshot station) {
        if (!isEnabled()) {
            return true;
        }
        if (isOp) {
            return true;
        }
        Optional<UUID> ownerId = station.ownerId();
        return ownerId.isEmpty() || canUseOwner(playerId, ownerId.get());
    }

    public static boolean ensureCanControl(ServerPlayer player, AirshipStationBlockEntity station) {
        if (canControl(player, station)) {
            return true;
        }
        SetMenuActionBarMessagePayload.send(player, DENIED_MESSAGE);
        return false;
    }

    public static boolean ensureCanUse(ServerPlayer player, AirshipStationBlockEntity station) {
        if (canUse(player, station)) {
            return true;
        }
        SetMenuActionBarMessagePayload.send(player, USE_DENIED_MESSAGE);
        return false;
    }

    private static boolean canControlOwner(UUID playerId, UUID ownerId) {
        if (ownerId.equals(playerId)) {
            return true;
        }
        if (AutomatedLogisticsConfig.ALLOW_TEAM_STATION_CONTROL.get()
                && FtbTeamsCompat.areSameTeam(playerId, ownerId)) {
            return true;
        }
        return AutomatedLogisticsConfig.ALLOW_ALLIED_STATION_CONTROL.get()
                && FtbTeamsCompat.areAllied(ownerId, playerId);
    }

    private static boolean canUseOwner(UUID playerId, UUID ownerId) {
        if (canControlOwner(playerId, ownerId)) {
            return true;
        }
        if (ownerId.equals(playerId)) {
            return true;
        }
        if (AutomatedLogisticsConfig.ALLOW_TEAM_STATION_USE.get()
                && FtbTeamsCompat.areSameTeam(playerId, ownerId)) {
            return true;
        }
        return AutomatedLogisticsConfig.ALLOW_ALLIED_STATION_USE.get()
                && FtbTeamsCompat.areAllied(ownerId, playerId);
    }
}

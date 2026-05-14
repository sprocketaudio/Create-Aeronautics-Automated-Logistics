package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;

public final class StationPermissionService {
    private static final Component DENIED_MESSAGE =
            Component.translatable("message.create_aeronautics_automated_logistics.station.permission_denied");

    private StationPermissionService() {
    }

    public static boolean isEnabled() {
        return AutomatedLogisticsConfig.RESTRICT_TRANSPONDER_CONTROL_TO_OWNER.get();
    }

    public static boolean canControl(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!isEnabled()) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        Optional<UUID> ownerId = station.ownerId();
        return ownerId.isEmpty() || ownerId.get().equals(player.getUUID());
    }

    public static boolean canControl(UUID playerId, boolean isOp, AirshipStationSnapshot station) {
        if (!isEnabled()) {
            return true;
        }
        if (isOp) {
            return true;
        }
        Optional<UUID> ownerId = station.ownerId();
        return ownerId.isEmpty() || ownerId.get().equals(playerId);
    }

    public static boolean ensureCanControl(ServerPlayer player, AirshipStationBlockEntity station) {
        if (canControl(player, station)) {
            return true;
        }
        SetMenuActionBarMessagePayload.send(player, DENIED_MESSAGE);
        return false;
    }
}

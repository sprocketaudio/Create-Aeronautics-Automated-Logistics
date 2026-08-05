package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;

public final class TransponderPermissionService {
    private static final Component DENIED_MESSAGE =
            Component.translatable("message.create_aeronautics_automated_logistics.transponder.permission_denied");

    private TransponderPermissionService() {
    }

    public static boolean isEnabled() {
        return AutomatedLogisticsConfig.ownershipPermissionsEnabled();
    }

    public static boolean canControl(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        return canControl(player, transponder.ownerId());
    }

    public static boolean canControl(ServerPlayer player, Optional<UUID> ownerId) {
        if (!isEnabled()) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        if (ownerId.isEmpty() || ownerId.get().equals(player.getUUID())) {
            return true;
        }
        if (AutomatedLogisticsConfig.ALLOW_TEAM_TRANSPONDER_CONTROL.get()
                && FtbTeamsCompat.areSameTeam(player.getUUID(), ownerId.get())) {
            return true;
        }
        return AutomatedLogisticsConfig.ALLOW_ALLIED_TRANSPONDER_CONTROL.get()
                && FtbTeamsCompat.areAllied(ownerId.get(), player.getUUID());
    }

    public static boolean ensureCanControl(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (canControl(player, transponder)) {
            return true;
        }
        SetMenuActionBarMessagePayload.send(player, DENIED_MESSAGE);
        return false;
    }
}

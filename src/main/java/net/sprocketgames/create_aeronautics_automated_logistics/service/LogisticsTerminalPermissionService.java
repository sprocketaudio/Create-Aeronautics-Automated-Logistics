package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.TerminalAudience;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.LogisticsTerminalBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;

public final class LogisticsTerminalPermissionService {
    private static final Component DENIED_MESSAGE =
            Component.translatable("message.create_aeronautics_automated_logistics.logistics_terminal.permission_denied");

    private LogisticsTerminalPermissionService() {
    }

    public static boolean canOpen(ServerPlayer player, LogisticsTerminalBlockEntity terminal) {
        return canAccess(player.getUUID(), player.hasPermissions(2), terminal.ownerId(), AutomatedLogisticsConfig.logisticsTerminalAccess());
    }

    public static boolean canSeePreview(ServerPlayer player, LogisticsTerminalBlockEntity terminal) {
        return canAccess(
                player.getUUID(),
                player.hasPermissions(2),
                terminal.ownerId(),
                terminal.ownerTeamId(),
                AutomatedLogisticsConfig.logisticsTerminalPreviewVisibility(),
                false
        );
    }

    public static boolean canReceivePreviewData(ServerPlayer player, UUID ownerId) {
        return canAccess(
                player.getUUID(),
                player.hasPermissions(2),
                Optional.of(ownerId),
                FtbTeamsCompat.teamIdForPlayer(ownerId),
                AutomatedLogisticsConfig.logisticsTerminalPreviewVisibility(),
                false
        );
    }

    public static boolean ensureCanOpen(ServerPlayer player, LogisticsTerminalBlockEntity terminal) {
        if (canOpen(player, terminal)) {
            return true;
        }
        SetMenuActionBarMessagePayload.send(player, DENIED_MESSAGE);
        return false;
    }

    public static boolean canSeePreviewClient(LogisticsTerminalBlockEntity terminal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return canAccess(
                minecraft.player.getUUID(),
                false,
                terminal.ownerId(),
                terminal.ownerTeamId(),
                AutomatedLogisticsConfig.logisticsTerminalPreviewVisibility(),
                true
        );
    }

    private static boolean canAccess(UUID playerId, boolean isOp, Optional<UUID> ownerId, TerminalAudience audience) {
        return canAccess(playerId, isOp, ownerId, Optional.empty(), audience, false);
    }

    private static boolean canAccess(
            UUID playerId,
            boolean isOp,
            Optional<UUID> ownerId,
            Optional<UUID> ownerTeamId,
            TerminalAudience audience,
            boolean clientSide
    ) {
        if (isOp) {
            return true;
        }
        if (ownerId.isEmpty() || ownerId.get().equals(playerId)) {
            return true;
        }
        return switch (audience) {
            case OWNER_ONLY -> false;
            case OWNER_AND_TEAM -> clientSide
                    ? FtbTeamsCompat.canControlClientSide(playerId, ownerId.get(), ownerTeamId, true, false).orElse(false)
                    : FtbTeamsCompat.areSameTeam(playerId, ownerId.get());
            case OWNER_TEAM_AND_ALLIES -> clientSide
                    ? FtbTeamsCompat.canControlClientSide(playerId, ownerId.get(), ownerTeamId, true, true).orElse(false)
                    : FtbTeamsCompat.areSameTeam(playerId, ownerId.get())
                            || FtbTeamsCompat.areAllied(ownerId.get(), playerId);
            case PUBLIC -> true;
        };
    }
}

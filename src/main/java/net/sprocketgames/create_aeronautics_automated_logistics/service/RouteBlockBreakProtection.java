package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;

public final class RouteBlockBreakProtection {
    private RouteBlockBreakProtection() {
    }

    public static boolean shouldBlockBreak(Player player) {
        return AutomatedLogisticsConfig.requireCrouchToBreakRouteBlocks() && !player.isCrouching();
    }

    public static void warnIfBlocked(Level level, Player player, Component message) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || !shouldBlockBreak(player)) {
            return;
        }
        SetMenuActionBarMessagePayload.send(serverPlayer, message);
    }
}

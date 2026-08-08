package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.LogisticsTerminalBlockEntity;

/** Tracks loaded terminals so preview visibility never has to query chunk generation. */
public final class LogisticsTerminalRegistry {
    private static final int MAX_PREVIEW_DISTANCE = 96;
    private static final Map<ResourceKey<Level>, Map<BlockPos, LogisticsTerminalBlockEntity>> TERMINALS = new LinkedHashMap<>();

    private LogisticsTerminalRegistry() {
    }

    public static void register(ServerLevel level, LogisticsTerminalBlockEntity terminal) {
        TERMINALS.computeIfAbsent(level.dimension(), ignored -> new LinkedHashMap<>())
                .put(terminal.getBlockPos().immutable(), terminal);
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Map<BlockPos, LogisticsTerminalBlockEntity> terminals = TERMINALS.get(level.dimension());
        if (terminals == null) {
            return;
        }
        terminals.remove(pos);
        if (terminals.isEmpty()) {
            TERMINALS.remove(level.dimension());
        }
    }

    public static List<BlockPos> visibleTerminalPositions(ServerPlayer player) {
        Map<BlockPos, LogisticsTerminalBlockEntity> terminals = TERMINALS.get(player.serverLevel().dimension());
        if (terminals == null || terminals.isEmpty()) {
            return List.of();
        }

        double maxDistanceSquared = (double) MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE;
        List<BlockPos> visible = new ArrayList<>();
        var iterator = terminals.entrySet().iterator();
        while (iterator.hasNext()) {
            LogisticsTerminalBlockEntity terminal = iterator.next().getValue();
            if (terminal.isRemoved() || terminal.getLevel() != player.serverLevel()) {
                iterator.remove();
                continue;
            }
            if (terminal.getBlockPos().distToCenterSqr(player.position()) > maxDistanceSquared) {
                continue;
            }
            if (LogisticsTerminalPermissionService.canSeePreview(player, terminal)) {
                visible.add(terminal.getBlockPos().immutable());
            }
        }
        if (terminals.isEmpty()) {
            TERMINALS.remove(player.serverLevel().dimension());
        }
        return List.copyOf(visible);
    }

    public static void clear() {
        TERMINALS.clear();
    }
}

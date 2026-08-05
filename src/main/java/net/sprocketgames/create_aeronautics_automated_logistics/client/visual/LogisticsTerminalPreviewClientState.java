package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;

public final class LogisticsTerminalPreviewClientState {
    private static final Set<BlockPos> TERMINALS = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> VISIBLE_TERMINALS = ConcurrentHashMap.newKeySet();
    private static volatile List<RouteSegment> routes = List.of();
    private static volatile List<ShipMapMarker> markers = List.of();

    private LogisticsTerminalPreviewClientState() {
    }

    public static void register(BlockPos pos) {
        TERMINALS.add(pos.immutable());
    }

    public static void unregister(BlockPos pos) {
        TERMINALS.remove(pos);
    }

    public static List<BlockPos> terminals() {
        return List.copyOf(TERMINALS);
    }

    public static boolean canRender(BlockPos pos) {
        return VISIBLE_TERMINALS.contains(pos);
    }

    public static void replaceVisibleTerminals(List<BlockPos> updated) {
        VISIBLE_TERMINALS.clear();
        if (updated != null) {
            updated.forEach(pos -> VISIBLE_TERMINALS.add(pos.immutable()));
        }
    }

    public static List<RouteSegment> routes() {
        return routes;
    }

    public static void replaceRoutes(List<RouteSegment> updated) {
        routes = updated == null ? List.of() : List.copyOf(updated);
    }

    public static List<ShipMapMarker> markers() {
        return markers;
    }

    public static void replaceMarkers(List<ShipMapMarker> updated) {
        markers = updated == null ? List.of() : List.copyOf(updated);
    }

    public static void clearIfWorldMissing() {
        if (Minecraft.getInstance().level == null) {
            TERMINALS.clear();
            VISIBLE_TERMINALS.clear();
            routes = List.of();
            markers = List.of();
        }
    }
}

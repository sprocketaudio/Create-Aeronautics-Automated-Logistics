package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;

public final class LogisticsTerminalClientState {
    private static volatile List<RouteSegment> routes = List.of();

    private LogisticsTerminalClientState() {
    }

    public static List<RouteSegment> routes() {
        return routes;
    }

    public static void replaceRoutes(List<RouteSegment> updated) {
        routes = updated == null ? List.of() : List.copyOf(updated);
    }

    public static void clearIfWorldMissing() {
        if (Minecraft.getInstance().level == null && !routes.isEmpty()) {
            routes = List.of();
        }
    }
}

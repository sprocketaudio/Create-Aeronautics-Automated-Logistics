package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;

public final class ShipMapClientState {
    private static volatile List<ShipMapMarker> markers = List.of();

    private ShipMapClientState() {
    }

    public static List<ShipMapMarker> markers() {
        return markers;
    }

    public static void replace(List<ShipMapMarker> updated) {
        markers = List.copyOf(updated);
        refreshMapCompat();
    }

    public static void clearIfWorldMissing() {
        if (Minecraft.getInstance().level == null && !markers.isEmpty()) {
            markers = List.of();
            refreshMapCompat();
        }
    }

    private static void refreshMapCompat() {
        FtbChunksShipMapCompat.refresh();
        JourneyMapShipMapCompat.refresh();
    }
}

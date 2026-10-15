package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsClientConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;

public final class ShipMapClientState {
    private static volatile List<ShipMapMarker> markers = List.of();
    private static boolean ftbChunksEnabled = true;
    private static boolean journeyMapEnabled = true;
    private static boolean xaeroMapsEnabled = true;

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
        refreshConfigIfChanged();
        if (Minecraft.getInstance().level == null && !markers.isEmpty()) {
            markers = List.of();
            refreshMapCompat();
        }
    }

    private static void refreshConfigIfChanged() {
        boolean newFtbChunksEnabled = AutomatedLogisticsClientConfig.ftbChunksIntegrationEnabled();
        boolean newJourneyMapEnabled = AutomatedLogisticsClientConfig.journeyMapIntegrationEnabled();
        boolean newXaeroMapsEnabled = AutomatedLogisticsClientConfig.xaeroMapsIntegrationEnabled();
        if (ftbChunksEnabled == newFtbChunksEnabled
                && journeyMapEnabled == newJourneyMapEnabled
                && xaeroMapsEnabled == newXaeroMapsEnabled) {
            return;
        }
        ftbChunksEnabled = newFtbChunksEnabled;
        journeyMapEnabled = newJourneyMapEnabled;
        xaeroMapsEnabled = newXaeroMapsEnabled;
        refreshMapCompat();
    }

    private static void refreshMapCompat() {
        FtbChunksShipMapCompat.refresh();
        JourneyMapShipMapCompat.refresh();
    }
}

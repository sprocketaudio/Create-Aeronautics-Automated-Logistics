package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.ModList;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsClientConfig;

public final class FtbChunksShipMapCompat {
    private FtbChunksShipMapCompat() {
    }

    public static void register() {
        if (ModList.get().isLoaded("ftbchunks")) {
            FtbChunksShipMapBridge.register();
        }
    }

    public static void refresh() {
        if (ModList.get().isLoaded("ftbchunks")) {
            FtbChunksShipMapBridge.refreshIfOpen();
        }
    }

    public static void renderLargeMapOverlay(ScreenEvent.Render.Post event) {
        if (!AutomatedLogisticsClientConfig.ftbChunksIntegrationEnabled()) {
            return;
        }
        FtbChunksLargeMapOverlayRenderer.render(event);
    }
}

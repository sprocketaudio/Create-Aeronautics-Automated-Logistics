package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.util.UIState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.map.ShipMapClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;

final class JourneyMapFullscreenOverlayRenderer {
    private JourneyMapFullscreenOverlayRenderer() {
    }

    static void render(FullscreenRenderEvent event) {
        IFullscreen fullscreen = event.getFullscreen();
        UIState state = fullscreen.getUiState();
        if (state == null || state.ui != Context.UI.Fullscreen || !state.active) {
            return;
        }

        Screen screen = fullscreen.getScreen();
        double centerX = fullscreen.getCenterBlockX(true);
        double centerZ = fullscreen.getCenterBlockZ(true);
        double scale = state.blockSize / ((double) fullscreen.getMinecraft().getWindow().getScreenWidth()
                / fullscreen.getMinecraft().getWindow().getGuiScaledWidth());
        GuiGraphics graphics = event.getGraphics();

        for (ShipMapMarker marker : ShipMapClientState.markers()) {
            if (!marker.dimension().equals(fullscreen.getMinecraft().level.dimension())) {
                continue;
            }
            int drawX = (int) Math.round(screen.width / 2.0D + (marker.position().x - centerX) * scale);
            int drawY = (int) Math.round(screen.height / 2.0D + (marker.position().z - centerZ) * scale);
            if (drawX < -24 || drawX > screen.width + 24 || drawY < -24 || drawY > screen.height + 24) {
                continue;
            }
            MapMarkerRenderUtil.drawMarker(graphics, marker, drawX, drawY);
        }
    }
}

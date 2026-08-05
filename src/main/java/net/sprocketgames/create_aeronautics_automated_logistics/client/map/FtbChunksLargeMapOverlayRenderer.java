package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.client.map.ShipMapClientState;

final class FtbChunksLargeMapOverlayRenderer {
    private static final int BLOCKS_PER_REGION = 16 * 32;

    private FtbChunksLargeMapOverlayRenderer() {
    }

    static void render(ScreenEvent.Render.Post event) {
        if (!ModList.get().isLoaded("ftbchunks")) {
            return;
        }

        LargeMapScreen largeMapScreen = getAsLargeMapScreen(event.getScreen());
        if (largeMapScreen == null) {
            return;
        }

        Object panel = ObfuscationReflectionHelper.getPrivateValue(LargeMapScreen.class, largeMapScreen, "regionPanel");
        if (!(panel instanceof RegionMapPanel regionMapPanel)) {
            return;
        }

        int regionMinX = ObfuscationReflectionHelper.getPrivateValue(RegionMapPanel.class, regionMapPanel, "regionMinX");
        int regionMinZ = ObfuscationReflectionHelper.getPrivateValue(RegionMapPanel.class, regionMapPanel, "regionMinZ");
        double minX = regionMapPanel.getScrollX();
        double minY = regionMapPanel.getScrollY();
        float tileSize = largeMapScreen.getRegionTileSize();
        GuiGraphics graphics = event.getGuiGraphics();

        for (ShipMapMarker marker : ShipMapClientState.markers()) {
            if (!marker.dimension().equals(largeMapScreen.currentDimension())) {
                continue;
            }
            int centerX = (int) Math.round((marker.position().x / BLOCKS_PER_REGION - regionMinX) * tileSize - minX);
            int centerY = (int) Math.round((marker.position().z / BLOCKS_PER_REGION - regionMinZ) * tileSize - minY);
            if (centerX < -24 || centerX > largeMapScreen.width + 24 || centerY < -24 || centerY > largeMapScreen.height + 24) {
                continue;
            }
            MapMarkerRenderUtil.drawMarker(graphics, marker, centerX, centerY);
        }
    }

    private static LargeMapScreen getAsLargeMapScreen(Screen screen) {
        if (!(screen instanceof ScreenWrapper screenWrapper)) {
            return null;
        }
        BaseScreen wrapped = screenWrapper.getGui();
        return wrapped instanceof LargeMapScreen largeMapScreen ? largeMapScreen : null;
    }
}

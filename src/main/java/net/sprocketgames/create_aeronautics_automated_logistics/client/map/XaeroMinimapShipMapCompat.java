package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsClientConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.minimap.XaeroMinimap;

/** Optional Xaero Minimap element registration. The marker is transformed by Xaero's own map layer. */
public final class XaeroMinimapShipMapCompat {
    private static boolean registered;
    private static boolean unavailable;

    private XaeroMinimapShipMapCompat() {
    }

    public static void registerIfAvailable() {
        if (registered || unavailable || !ModList.get().isLoaded("xaerominimap")) {
            return;
        }
        try {
            XaeroMinimap minimap = XaeroMinimap.instance;
            if (minimap == null || !minimap.isLoadedClient() || minimap.getMinimap() == null) {
                return;
            }
            minimap.getMinimap().getOverMapRendererHandler().add(new ShipMarkerRenderer());
            registered = true;
        } catch (RuntimeException exception) {
            unavailable = true;
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Xaero Minimap ship marker integration could not register; disabling it for this session",
                    exception
            );
        }
    }

    private static final class ShipMarkerProvider extends MinimapElementRenderProvider<ShipMapMarker, Void> {
        private Iterator<ShipMapMarker> iterator = java.util.List.<ShipMapMarker>of().iterator();

        @Override
        public void begin(MinimapElementRenderLocation location, Void context) {
            iterator = ShipMapClientState.markers().iterator();
        }

        @Override
        public boolean hasNext(MinimapElementRenderLocation location, Void context) {
            return iterator.hasNext();
        }

        @Override
        public ShipMapMarker getNext(MinimapElementRenderLocation location, Void context) {
            return iterator.next();
        }

        @Override
        public void end(MinimapElementRenderLocation location, Void context) {
        }
    }

    private static final class ShipMarkerReader extends MinimapElementReader<ShipMapMarker, Void> {
        @Override
        public boolean isHidden(ShipMapMarker marker, Void context) {
            return false;
        }

        @Override
        public double getRenderX(ShipMapMarker marker, Void context, float partialTicks) {
            return marker.position().x;
        }

        @Override
        public double getRenderY(ShipMapMarker marker, Void context, float partialTicks) {
            return marker.position().y;
        }

        @Override
        public double getRenderZ(ShipMapMarker marker, Void context, float partialTicks) {
            return marker.position().z;
        }

        @Override public int getInteractionBoxLeft(ShipMapMarker marker, Void context, float partialTicks) { return -halfIconSize(); }
        @Override public int getInteractionBoxRight(ShipMapMarker marker, Void context, float partialTicks) { return halfIconSize(); }
        @Override public int getInteractionBoxTop(ShipMapMarker marker, Void context, float partialTicks) { return -halfIconSize(); }
        @Override public int getInteractionBoxBottom(ShipMapMarker marker, Void context, float partialTicks) { return halfIconSize(); }
        @Override public int getRenderBoxLeft(ShipMapMarker marker, Void context, float partialTicks) { return -halfIconSize(); }
        @Override public int getRenderBoxRight(ShipMapMarker marker, Void context, float partialTicks) { return halfIconSize(); }
        @Override public int getRenderBoxTop(ShipMapMarker marker, Void context, float partialTicks) { return -halfIconSize(); }
        @Override public int getRenderBoxBottom(ShipMapMarker marker, Void context, float partialTicks) { return halfIconSize(); }
        @Override public int getLeftSideLength(ShipMapMarker marker, Minecraft minecraft) { return 0; }
        @Override public String getMenuName(ShipMapMarker marker) { return marker.shipName(); }
        @Override public String getFilterName(ShipMapMarker marker) { return marker.shipName(); }
        @Override public int getMenuTextFillLeftPadding(ShipMapMarker marker) { return 0; }
        @Override public int getRightClickTitleBackgroundColor(ShipMapMarker marker) { return 0; }
        @Override public boolean shouldScaleBoxWithOptionalScale() { return true; }

        private static int halfIconSize() {
            return Math.round(14 * AutomatedLogisticsClientConfig.mapMarkerIconScale());
        }
    }

    private static final class ShipMarkerRenderer extends MinimapElementRenderer<ShipMapMarker, Void> {
        private ShipMarkerRenderer() {
            super(new ShipMarkerReader(), new ShipMarkerProvider(), null);
        }

        @Override
        public boolean renderElement(
                ShipMapMarker marker,
                boolean hovered,
                boolean selected,
                double depth,
                float partialTicks,
                double distance,
                double angle,
                MinimapElementRenderInfo info,
                GuiGraphics graphics,
                MultiBufferSource.BufferSource bufferSource
        ) {
            if (!AutomatedLogisticsClientConfig.xaeroMapsIntegrationEnabled()
                    || !marker.dimension().equals(info.mapDimension)) {
                return false;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0.0D, 0.0D, depth);
            MapMarkerRenderUtil.drawMarker(graphics, marker, 0, 0);
            graphics.pose().popPose();
            return true;
        }

        @Override public void preRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource bufferSource, xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider provider) { }
        @Override public void postRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource bufferSource, xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider provider) { }
        @Override public boolean shouldRender(MinimapElementRenderLocation location) { return location == MinimapElementRenderLocation.OVER_MINIMAP; }
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.api.client.icon.MapType;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

final class FtbChunksShipMapBridge {
    private static final ResourceLocation AIRSHIP_ICON_LOCATION = ResourceLocation.fromNamespaceAndPath(
            "create_aeronautics_automated_logistics",
            "textures/gui/blimp.png"
    );
    private static final ResourceLocation TRAIN_ICON_LOCATION = ResourceLocation.fromNamespaceAndPath(
            "create_aeronautics_automated_logistics",
            "textures/gui/train.png"
    );
    private static final ImageIcon AIRSHIP_ICON = new ImageIcon(AIRSHIP_ICON_LOCATION);
    private static final ImageIcon TRAIN_ICON = new ImageIcon(TRAIN_ICON_LOCATION);

    private FtbChunksShipMapBridge() {
    }

    static void register() {
        MapIconEvent.MINIMAP.register(FtbChunksShipMapBridge::addMarkers);
    }

    static void refreshIfOpen() {
        LargeMapScreen.refreshIconsIfOpen();
    }

    private static void addMarkers(MapIconEvent event) {
        ShipMapClientState.markers().stream()
                .filter(marker -> marker.dimension().equals(event.getDimension()))
                .map(ShipIcon::new)
                .forEach(event::add);
    }

    private static final class ShipIcon implements MapIcon {
        private final ShipMapMarker marker;

        private ShipIcon(ShipMapMarker marker) {
            this.marker = marker;
        }

        @Override
        public Vec3 getPos(float partialTick) {
            return marker.position();
        }

        @Override
        public int getPriority() {
            return 50;
        }

        @Override
        public double getIconScale(MapType mapType) {
            return mapType.isMinimap() ? 1.0D : 1.15D;
        }

        @Override
        public void addTooltip(TooltipList list) {
            list.styledString(marker.shipName(), ChatFormatting.GOLD);
            list.styledString(marker.displayState(), ChatFormatting.GRAY);
            MapIcon.super.addTooltip(list);
        }

        @Override
        public boolean onMousePressed(BaseScreen screen, MouseButton button) {
            return false;
        }

        @Override
        public boolean onKeyPressed(BaseScreen screen, Key key) {
            return false;
        }

        @Override
        public void draw(
                MapType mapType,
                GuiGraphics graphics,
                int x,
                int y,
                int width,
                int height,
                boolean outsideVisibleArea,
                int iconAlpha
        ) {
            ImageIcon icon = marker.transportMode() == TransportMode.TRAIN ? TRAIN_ICON : AIRSHIP_ICON;
            icon.draw(graphics, x, y, width, height);

            String label = marker.shipName();
            if (label.isBlank()) {
                return;
            }

            var font = Minecraft.getInstance().font;
            int labelWidth = font.width(label);
            int labelX = x + (width - labelWidth) / 2;
            int labelY = y - font.lineHeight - 2;
            int backgroundPadding = 2;
            graphics.fill(
                    labelX - backgroundPadding,
                    labelY - 1,
                    labelX + labelWidth + backgroundPadding,
                    labelY + font.lineHeight - 1,
                    0x80000000
            );
            graphics.drawString(font, Component.literal(label), labelX, labelY, 0xFFF4D78A, false);
        }
    }
}

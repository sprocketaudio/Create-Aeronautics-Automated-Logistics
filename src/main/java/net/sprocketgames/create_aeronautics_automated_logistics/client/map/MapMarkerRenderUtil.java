package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

final class MapMarkerRenderUtil {
    private static final int ICON_BLIT_OFFSET = 400;
    private static final int LABEL_Z_OFFSET = 450;
    private static final ResourceLocation AIRSHIP_ICON = ResourceLocation.fromNamespaceAndPath(
            "create_aeronautics_automated_logistics",
            "textures/gui/blimp.png"
    );
    private static final ResourceLocation TRAIN_ICON = ResourceLocation.fromNamespaceAndPath(
            "create_aeronautics_automated_logistics",
            "textures/gui/train.png"
    );
    private static final int ICON_WIDTH = 28;
    private static final int ICON_HEIGHT = 28;
    private static final int ICON_TEXTURE_SIZE = 56;
    private static final int LABEL_COLOR = 0xFFF4D78A;
    private static final int LABEL_BACKGROUND = 0x80000000;

    private MapMarkerRenderUtil() {
    }

    static void drawMarker(GuiGraphics graphics, ShipMapMarker marker, int centerX, int centerY) {
        int iconX = centerX - ICON_WIDTH / 2;
        int iconY = centerY - ICON_HEIGHT / 2;
        float iconScale = ICON_WIDTH / (float) ICON_TEXTURE_SIZE;
        graphics.pose().pushPose();
        graphics.pose().translate(iconX, iconY, ICON_BLIT_OFFSET);
        graphics.pose().scale(iconScale, iconScale, 1.0F);
        graphics.blit(
                iconFor(marker.transportMode()),
                0,
                0,
                0.0F,
                0.0F,
                ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE
        );
        graphics.pose().popPose();

        drawLabel(graphics, marker.shipName(), centerX, iconY);
    }

    static void drawLabel(GuiGraphics graphics, String label, int centerX, int iconY) {
        Minecraft minecraft = Minecraft.getInstance();

        if (label.isBlank()) {
            return;
        }

        int labelWidth = minecraft.font.width(label);
        int labelX = centerX - labelWidth / 2;
        int labelY = iconY - minecraft.font.lineHeight - 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, LABEL_Z_OFFSET);
        graphics.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2, labelY + minecraft.font.lineHeight - 1, LABEL_BACKGROUND);
        graphics.drawString(minecraft.font, Component.literal(label), labelX, labelY, LABEL_COLOR, false);
        graphics.pose().popPose();
    }

    private static ResourceLocation iconFor(TransportMode transportMode) {
        return transportMode == TransportMode.TRAIN ? TRAIN_ICON : AIRSHIP_ICON;
    }
}

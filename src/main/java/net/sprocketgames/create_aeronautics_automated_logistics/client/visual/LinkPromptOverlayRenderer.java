package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;

final class LinkPromptOverlayRenderer {
    private static final int SIDE_PADDING = 16;
    private static final int MAX_WRAP_WIDTH = 320;
    private static final float SCREEN_WRAP_RATIO = 0.55F;
    private static final int LINE_SPACING = 4;
    private static final int RENDER_Z = 500;

    private LinkPromptOverlayRenderer() {
    }

    static void render(GuiGraphics guiGraphics, Component prompt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Font font = minecraft.font;
        int ratioWidth = (int) (guiGraphics.guiWidth() * SCREEN_WRAP_RATIO);
        int wrapWidth = Math.min(
                MAX_WRAP_WIDTH,
                Math.max(160, Math.min(ratioWidth, guiGraphics.guiWidth() - SIDE_PADDING * 2))
        );
        List<FormattedCharSequence> lines = font.split(prompt, wrapWidth);
        if (lines.isEmpty()) {
            return;
        }

        int blockHeight = lines.size() * (font.lineHeight + LINE_SPACING) - LINE_SPACING;
        int y = MenuActionBarClientState.promptY(guiGraphics, blockHeight);
        int textColor = FastColor.ARGB32.color(255, 255, 220, 96);
        int backdropColor = FastColor.ARGB32.color(120, 0, 0, 0);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, 0.0D, RENDER_Z);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineWidth = font.width(line);
            int x = (guiGraphics.guiWidth() - lineWidth) / 2;
            int lineY = y + i * (font.lineHeight + LINE_SPACING);
            guiGraphics.fill(x - 3, lineY - 2, x + lineWidth + 3, lineY + font.lineHeight + 2, backdropColor);
            guiGraphics.drawString(font, line, x, lineY, textColor, false);
        }
        guiGraphics.pose().popPose();
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FastColor;
import java.util.List;

public final class MenuActionBarClientState {
    private static final int SIDE_PADDING = 16;
    private static final int MAX_WRAP_WIDTH = 260;
    private static final float SCREEN_WRAP_RATIO = 0.42F;
    private static final int LINE_SPACING = 2;
    private static Component message;
    private static long expiresAtGameTime = -1L;

    private MenuActionBarClientState() {
    }

    public static void show(Component component, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        message = component;
        expiresAtGameTime = minecraft.level.getGameTime() + Math.max(1, durationTicks);
    }

    public static void clear() {
        message = null;
        expiresAtGameTime = -1L;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        if (message == null || expiresAtGameTime < 0L || minecraft.level.getGameTime() > expiresAtGameTime) {
            clear();
        }
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || minecraft.level == null || message == null || expiresAtGameTime < 0L) {
            return;
        }

        long remaining = expiresAtGameTime - minecraft.level.getGameTime();
        int alpha = (int) Math.min(255L, remaining * 255L / 20L);
        if (alpha <= 8) {
            return;
        }

        Font font = minecraft.font;
        int color = FastColor.ARGB32.color(alpha, 255, 255, 255);
        int backdropColor = FastColor.ARGB32.color(Math.min(alpha, 140), 0, 0, 0);
        int ratioWidth = (int) (guiGraphics.guiWidth() * SCREEN_WRAP_RATIO);
        int wrapWidth = Math.min(
                MAX_WRAP_WIDTH,
                Math.max(120, Math.min(ratioWidth, guiGraphics.guiWidth() - SIDE_PADDING * 2))
        );
        List<FormattedCharSequence> lines = font.split(message, wrapWidth);
        if (lines.isEmpty()) {
            return;
        }

        int blockWidth = 0;
        for (FormattedCharSequence line : lines) {
            blockWidth = Math.max(blockWidth, font.width(line));
        }
        int lineHeight = font.lineHeight + LINE_SPACING;
        int blockHeight = lines.size() * lineHeight - LINE_SPACING;
        int y = guiGraphics.guiHeight() - 68 - Math.max(0, blockHeight - font.lineHeight);
        int backdropWidth = Math.min(wrapWidth, blockWidth);

        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineWidth = font.width(line);
            int x = (guiGraphics.guiWidth() - lineWidth) / 2;
            int lineY = y + i * lineHeight;
            guiGraphics.fill(x - 3, lineY - 2, x + lineWidth + 3, lineY + font.lineHeight + 2, backdropColor);
            guiGraphics.drawString(font, line, x, lineY, color, false);
        }
    }
}

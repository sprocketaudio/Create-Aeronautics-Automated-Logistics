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
    private static final int LINE_SPACING = 4;
    private static final int RENDER_Z = 500;
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
        if (minecraft.level == null || message == null || expiresAtGameTime < 0L) {
            return;
        }

        long remaining = expiresAtGameTime - minecraft.level.getGameTime();
        int alpha = (int) Math.min(255L, remaining * 255L / 20L);
        if (alpha <= 8) {
            return;
        }

        Layout layout = layout(minecraft.font, guiGraphics, message);
        if (layout.lines().isEmpty()) {
            return;
        }

        int color = FastColor.ARGB32.color(alpha, 255, 255, 255);
        int backdropColor = FastColor.ARGB32.color(Math.min(alpha, 140), 0, 0, 0);
        Font font = minecraft.font;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, 0.0D, RENDER_Z);
        for (int i = 0; i < layout.lines().size(); i++) {
            FormattedCharSequence line = layout.lines().get(i);
            int lineWidth = font.width(line);
            int x = (guiGraphics.guiWidth() - lineWidth) / 2;
            int lineY = layout.y() + i * layout.lineHeight();
            guiGraphics.fill(x - 3, lineY - 2, x + lineWidth + 3, lineY + font.lineHeight + 2, backdropColor);
            guiGraphics.drawString(font, line, x, lineY, color, false);
        }
        guiGraphics.pose().popPose();
    }

    public static int promptY(GuiGraphics guiGraphics, int promptHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || message == null || expiresAtGameTime < 0L) {
            return guiGraphics.guiHeight() - 88 - Math.max(0, promptHeight - minecraft.font.lineHeight);
        }
        Layout layout = layout(minecraft.font, guiGraphics, message);
        return layout.y() - 10 - promptHeight;
    }

    private static Layout layout(Font font, GuiGraphics guiGraphics, Component component) {
        int ratioWidth = (int) (guiGraphics.guiWidth() * SCREEN_WRAP_RATIO);
        int wrapWidth = Math.min(
                MAX_WRAP_WIDTH,
                Math.max(120, Math.min(ratioWidth, guiGraphics.guiWidth() - SIDE_PADDING * 2))
        );
        List<FormattedCharSequence> lines = font.split(component, wrapWidth);
        int blockWidth = 0;
        for (FormattedCharSequence line : lines) {
            blockWidth = Math.max(blockWidth, font.width(line));
        }
        int lineHeight = font.lineHeight + LINE_SPACING;
        int blockHeight = lines.isEmpty() ? 0 : lines.size() * lineHeight - LINE_SPACING;
        int y = guiGraphics.guiHeight() - 68 - Math.max(0, blockHeight - font.lineHeight);
        return new Layout(lines, blockWidth, lineHeight, blockHeight, y);
    }

    private record Layout(
            List<FormattedCharSequence> lines,
            int blockWidth,
            int lineHeight,
            int blockHeight,
            int y
    ) {
    }
}

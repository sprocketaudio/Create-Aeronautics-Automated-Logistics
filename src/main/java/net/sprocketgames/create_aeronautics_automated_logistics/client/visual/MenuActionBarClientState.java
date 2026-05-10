package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

public final class MenuActionBarClientState {
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
        int width = font.width(message);
        int x = (guiGraphics.guiWidth() - width) / 2;
        int y = guiGraphics.guiHeight() - 68;
        guiGraphics.drawStringWithBackdrop(font, message, x, y, width, color);
    }
}

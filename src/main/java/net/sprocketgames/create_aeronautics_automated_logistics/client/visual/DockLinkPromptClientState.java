package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DockLinkPromptClientState {
    private static long expiresAtGameTime = -1L;
    private static boolean shipPrompt;
    private static Optional<BlockPos> sourcePos = Optional.empty();

    private DockLinkPromptClientState() {
    }

    public static void show(boolean forShip, BlockPos pendingSourcePos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        shipPrompt = forShip;
        sourcePos = Optional.of(pendingSourcePos.immutable());
        expiresAtGameTime = minecraft.level.getGameTime() + 20L * 30L;
    }

    public static void clear() {
        expiresAtGameTime = -1L;
        sourcePos = Optional.empty();
    }

    public static boolean isPendingForStation(BlockPos stationPos) {
        return isActive() && !shipPrompt && sourcePos.filter(stationPos::equals).isPresent();
    }

    public static boolean isPendingForTransponder(BlockPos transponderPos) {
        return isActive() && shipPrompt && sourcePos.filter(transponderPos::equals).isPresent();
    }

    private static boolean isActive() {
        return expiresAtGameTime >= 0L;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gui == null) {
            clear();
            return;
        }
        if (expiresAtGameTime < 0L || minecraft.level.getGameTime() > expiresAtGameTime) {
            clear();
            return;
        }
        long ticks = minecraft.level.getGameTime();
        boolean bright = (ticks / 10L) % 2L == 0L;
        ChatFormatting color = bright ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
        Component text = Component.translatable(
                shipPrompt
                        ? "gui.create_aeronautics_automated_logistics.dock_link.prompt.ship"
                        : "gui.create_aeronautics_automated_logistics.dock_link.prompt.station"
        ).withStyle(color);
        minecraft.gui.setOverlayMessage(text, false);
    }
}

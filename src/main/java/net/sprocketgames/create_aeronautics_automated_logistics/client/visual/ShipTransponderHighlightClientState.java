package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ShipTransponderHighlightClientState {
    private static long expiresAtGameTime = -1L;
    private static Optional<BlockPos> transponderPos = Optional.empty();

    private ShipTransponderHighlightClientState() {
    }

    public static void show(BlockPos pos, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        transponderPos = Optional.of(pos.immutable());
        LogisticsClientOverlays.setShipTransponderHighlight(pos);
        expiresAtGameTime = minecraft.level.getGameTime() + Math.max(1, durationTicks);
    }

    public static void clear() {
        expiresAtGameTime = -1L;
        transponderPos.ifPresent(ignored -> LogisticsClientOverlays.clearShipTransponderHighlight());
        transponderPos = Optional.empty();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        if (expiresAtGameTime < 0L || minecraft.level.getGameTime() > expiresAtGameTime) {
            clear();
        }
    }
}

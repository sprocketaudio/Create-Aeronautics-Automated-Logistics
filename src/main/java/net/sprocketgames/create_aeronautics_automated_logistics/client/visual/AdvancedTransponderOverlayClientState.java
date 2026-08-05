package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class AdvancedTransponderOverlayClientState {
    private static final Set<BlockPos> ENABLED_TRANSPONDERS = ConcurrentHashMap.newKeySet();

    private AdvancedTransponderOverlayClientState() {
    }

    public static boolean isEnabled(BlockPos transponderPos) {
        return transponderPos != null && ENABLED_TRANSPONDERS.contains(transponderPos);
    }

    public static void toggle(BlockPos transponderPos) {
        if (transponderPos == null) {
            return;
        }
        BlockPos immutablePos = transponderPos.immutable();
        if (!ENABLED_TRANSPONDERS.add(immutablePos)) {
            ENABLED_TRANSPONDERS.remove(immutablePos);
        }
    }

    public static void clearIfWorldMissing() {
        if (Minecraft.getInstance().level == null) {
            ENABLED_TRANSPONDERS.clear();
        }
    }
}

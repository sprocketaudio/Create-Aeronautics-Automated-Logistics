package net.sprocketgames.create_aeronautics_automated_logistics.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class NetworkLimits {
    static final int MAX_PREVIEW_ROUTE_IDS = 4_096;
    static final int MAX_FLIGHT_PATH_POINTS = 100_000;
    static final int MAX_FLIGHT_PATH_METADATA = 100_000;
    static final int MAX_LINK_PROMPT_GROUPS = 1_024;
    static final int MAX_LINK_PROMPT_POSITIONS = 4_096;
    static final int MAX_ACTIVE_VISUAL_SHIPS = 4_096;
    static final int MAX_IDENTITY_SNAPSHOTS = 8_192;
    static final int MAX_SCHEDULE_ENTRIES = 512;
    static final int MAX_SCHEDULE_CONDITION_GROUPS = 4_096;
    static final int MAX_SCHEDULE_CONDITIONS = 8_192;

    private NetworkLimits() {
    }

    static int readBoundedCount(RegistryFriendlyByteBuf buffer, int max, String fieldName) {
        int count = buffer.readVarInt();
        if (count < 0 || count > max) {
            throw new DecoderException(fieldName + " count " + count + " outside 0.." + max);
        }
        return count;
    }
}

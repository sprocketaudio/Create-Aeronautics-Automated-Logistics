package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public enum TransportMode {
    AIRSHIP,
    TRAIN;

    public static final TransportMode DEFAULT = AIRSHIP;
    public static final String NBT_KEY = "transportMode";

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean allowsRestoreRelocation() {
        return this == AIRSHIP;
    }

    public static TransportMode fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return TransportMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    public static TransportMode read(CompoundTag tag) {
        return tag.contains(NBT_KEY, Tag.TAG_STRING)
                ? fromSerializedName(tag.getString(NBT_KEY))
                : DEFAULT;
    }

    public static void write(CompoundTag tag, TransportMode transportMode) {
        tag.putString(NBT_KEY, (transportMode == null ? DEFAULT : transportMode).serializedName());
    }
}

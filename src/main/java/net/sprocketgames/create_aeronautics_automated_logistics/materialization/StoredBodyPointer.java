package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;

/** A stable, serializable value copy of a Sable storage pointer. */
public record StoredBodyPointer(ChunkPos chunkPos, short storageIndex, short subLevelIndex) {
    public StoredBodyPointer {
        Objects.requireNonNull(chunkPos, "chunkPos");
    }

    public static StoredBodyPointer fromSable(GlobalSavedSubLevelPointer pointer) {
        Objects.requireNonNull(pointer, "pointer");
        return new StoredBodyPointer(pointer.chunkPos(), pointer.storageIndex(), pointer.subLevelIndex());
    }

    public GlobalSavedSubLevelPointer toSable() {
        return new GlobalSavedSubLevelPointer(chunkPos, storageIndex, subLevelIndex);
    }

    public String selector() {
        return chunkPos.x + "," + chunkPos.z + "," + storageIndex + "," + subLevelIndex;
    }

    public static Optional<StoredBodyPointer> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String[] parts = raw.trim().split(",");
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);
            int storage = Integer.parseInt(parts[2]);
            int subLevel = Integer.parseInt(parts[3]);
            if (storage < Short.MIN_VALUE || storage > Short.MAX_VALUE
                    || subLevel < Short.MIN_VALUE || subLevel > Short.MAX_VALUE) {
                return Optional.empty();
            }
            return Optional.of(new StoredBodyPointer(
                    new ChunkPos(chunkX, chunkZ),
                    (short) storage,
                    (short) subLevel
            ));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}

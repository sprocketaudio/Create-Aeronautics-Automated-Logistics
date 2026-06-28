package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record DockTargetId(
        ResourceKey<Level> dimension,
        Optional<UUID> stationId,
        BlockPos stationPos,
        Optional<BlockPos> stationDockPos
) {
    public DockTargetId {
        Objects.requireNonNull(dimension, "dimension");
        stationId = Objects.requireNonNull(stationId, "stationId");
        stationPos = Objects.requireNonNull(stationPos, "stationPos").immutable();
        stationDockPos = Objects.requireNonNull(stationDockPos, "stationDockPos").map(BlockPos::immutable);
    }

    public boolean exact() {
        return stationId.isPresent() && stationDockPos.isPresent();
    }
}

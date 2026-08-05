package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

public record AirshipStationSnapshot(
        UUID stationId,
        String stationName,
        TransportMode transportMode,
        ResourceKey<Level> dimension,
        BlockPos stationPos,
        Optional<UUID> ownerId,
        String ownerName
) {
    public AirshipStationSnapshot(
            UUID stationId,
            String stationName,
            ResourceKey<Level> dimension,
            BlockPos stationPos,
            Optional<UUID> ownerId,
            String ownerName
    ) {
        this(stationId, stationName, TransportMode.DEFAULT, dimension, stationPos, ownerId, ownerName);
    }

    public AirshipStationSnapshot {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(stationName, "stationName");
        transportMode = transportMode == null ? TransportMode.DEFAULT : transportMode;
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(stationPos, "stationPos");
        ownerId = ownerId == null ? Optional.empty() : ownerId;
        ownerName = ownerName == null ? "" : ownerName;
    }
}

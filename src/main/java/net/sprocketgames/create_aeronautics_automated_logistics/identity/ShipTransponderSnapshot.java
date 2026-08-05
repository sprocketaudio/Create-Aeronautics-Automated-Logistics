package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public record ShipTransponderSnapshot(
        UUID transponderId,
        String shipName,
        TransportMode transportMode,
        ResourceKey<Level> dimension,
        BlockPos transponderPos,
        Optional<UUID> runtimeShipId,
        Optional<VehicleControllerRef> controllerRef,
        Optional<UUID> ownerId,
        Optional<Vec3> lastKnownPosition,
        long lastSeenGameTime
) {
    public ShipTransponderSnapshot {
        Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(shipName, "shipName");
        transportMode = transportMode == null ? TransportMode.DEFAULT : transportMode;
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(transponderPos, "transponderPos");
        runtimeShipId = Objects.requireNonNull(runtimeShipId, "runtimeShipId");
        controllerRef = Objects.requireNonNull(controllerRef, "controllerRef");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        lastKnownPosition = Objects.requireNonNull(lastKnownPosition, "lastKnownPosition");
    }
}

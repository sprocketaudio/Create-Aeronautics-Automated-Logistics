package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record VehicleControllerRef(
        ResourceLocation controllerType,
        ResourceKey<Level> dimension,
        Optional<UUID> vehicleId,
        Optional<BlockPos> controllerPos
) {
    public VehicleControllerRef {
        Objects.requireNonNull(controllerType, "controllerType");
        Objects.requireNonNull(dimension, "dimension");
        vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos");

        if (vehicleId.isEmpty() && controllerPos.isEmpty()) {
            throw new IllegalArgumentException("vehicleId or controllerPos must be present");
        }
    }

    public boolean matches(VehicleControllerRef other) {
        Objects.requireNonNull(other, "other");
        if (!controllerType.equals(other.controllerType) || !dimension.equals(other.dimension)) {
            return false;
        }
        if (vehicleId.isPresent() && other.vehicleId.isPresent() && vehicleId.get().equals(other.vehicleId.get())) {
            return true;
        }
        return controllerPos.isPresent()
                && other.controllerPos.isPresent()
                && controllerPos.get().equals(other.controllerPos.get());
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** One readable stored-body candidate. Discovery does not imply that the candidate is canonical. */
public record StoredBodyCandidate(
        UUID sableShipId,
        String displayName,
        ResourceKey<Level> dimension,
        Vec3 posePosition,
        StoredBodyPointer pointer,
        List<UUID> dependencies,
        CompoundTag serializedData,
        Health health,
        String healthReason
) {
    public StoredBodyCandidate {
        Objects.requireNonNull(sableShipId, "sableShipId");
        displayName = Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(posePosition, "posePosition");
        Objects.requireNonNull(pointer, "pointer");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        serializedData = Objects.requireNonNull(serializedData, "serializedData").copy();
        Objects.requireNonNull(health, "health");
        healthReason = Objects.requireNonNull(healthReason, "healthReason");
    }

    @Override
    public CompoundTag serializedData() {
        return serializedData.copy();
    }

    public boolean readable() {
        return health == Health.READABLE;
    }

    public enum Health {
        READABLE,
        STRUCTURALLY_CORRUPT
    }
}

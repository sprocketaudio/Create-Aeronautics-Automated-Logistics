package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record RouteStop(
        UUID id,
        String name,
        int pointIndex,
        WaitCondition waitCondition,
        Optional<BlockPos> dockPos,
        java.util.List<java.util.List<AirshipScheduleCondition>> conditionGroups
) {
    public RouteStop {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(waitCondition, "waitCondition");
        dockPos = Objects.requireNonNull(dockPos, "dockPos");
        conditionGroups = copyConditionGroups(conditionGroups);
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (pointIndex < 0) {
            throw new IllegalArgumentException("pointIndex must not be negative");
        }
    }

    public static RouteStop create(String name, int pointIndex, WaitCondition waitCondition) {
        return new RouteStop(UUID.randomUUID(), name, pointIndex, waitCondition, Optional.empty(), java.util.List.of());
    }

    public static RouteStop create(String name, int pointIndex, WaitCondition waitCondition, Optional<BlockPos> dockPos) {
        return new RouteStop(UUID.randomUUID(), name, pointIndex, waitCondition, dockPos, java.util.List.of());
    }

    public static RouteStop create(
            String name,
            int pointIndex,
            WaitCondition waitCondition,
            Optional<BlockPos> dockPos,
            java.util.List<java.util.List<AirshipScheduleCondition>> conditionGroups
    ) {
        return new RouteStop(UUID.randomUUID(), name, pointIndex, waitCondition, dockPos, conditionGroups);
    }

    public RouteStop withWaitCondition(WaitCondition waitCondition) {
        return new RouteStop(id, name, pointIndex, waitCondition, dockPos, conditionGroups);
    }

    public RouteStop withConditionGroups(java.util.List<java.util.List<AirshipScheduleCondition>> conditionGroups) {
        return new RouteStop(id, name, pointIndex, waitCondition, dockPos, conditionGroups);
    }

    public java.util.List<java.util.List<AirshipScheduleCondition>> effectiveConditionGroups() {
        if (!conditionGroups.isEmpty()) {
            return conditionGroups;
        }
        if (!waitCondition.waits()) {
            return java.util.List.of();
        }
        return java.util.List.of(java.util.List.of(AirshipScheduleCondition.fromWaitCondition(waitCondition)));
    }

    private static java.util.List<java.util.List<AirshipScheduleCondition>> copyConditionGroups(
            java.util.List<java.util.List<AirshipScheduleCondition>> conditionGroups
    ) {
        Objects.requireNonNull(conditionGroups, "conditionGroups");
        java.util.List<java.util.List<AirshipScheduleCondition>> copied = new java.util.ArrayList<>();
        for (java.util.List<AirshipScheduleCondition> group : conditionGroups) {
            copied.add(java.util.List.copyOf(Objects.requireNonNull(group, "group")));
        }
        return java.util.List.copyOf(copied);
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public record AirshipScheduleEntry(
        AirshipScheduleEntryType type,
        Optional<UUID> targetStationId,
        String targetStationName,
        WaitCondition waitCondition,
        WaitDurationUnit waitUnit,
        Optional<RouteSegmentId> pinnedSegmentId,
        List<List<AirshipScheduleCondition>> conditionGroups
) {
    public AirshipScheduleEntry {
        Objects.requireNonNull(type, "type");
        targetStationId = Objects.requireNonNull(targetStationId, "targetStationId");
        targetStationName = Objects.requireNonNull(targetStationName, "targetStationName");
        Objects.requireNonNull(waitCondition, "waitCondition");
        Objects.requireNonNull(waitUnit, "waitUnit");
        pinnedSegmentId = Objects.requireNonNull(pinnedSegmentId, "pinnedSegmentId");
        conditionGroups = normalizeConditionGroups(waitCondition, conditionGroups);
        waitCondition = primaryWaitCondition(waitCondition, conditionGroups);
    }

    public static AirshipScheduleEntry blankTravel() {
        return new AirshipScheduleEntry(
                AirshipScheduleEntryType.TRAVEL_TO_STATION,
                Optional.empty(),
                "",
                WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS),
                WaitDurationUnit.SECONDS,
                Optional.empty(),
                List.of(List.of(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS))))
        );
    }

    public String displayStationName() {
        return targetStationName == null || targetStationName.isBlank() ? "Unset Station" : targetStationName;
    }

    public AirshipScheduleEntry withWaitCondition(WaitCondition waitCondition) {
        List<List<AirshipScheduleCondition>> groups = conditionGroups.isEmpty()
                ? List.of(List.of(AirshipScheduleCondition.scheduledDelay(waitCondition)))
                : replaceFirstScheduledDelay(waitCondition);
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, groups);
    }

    public AirshipScheduleEntry withTargetStation(UUID stationId, String stationName) {
        return new AirshipScheduleEntry(type, Optional.of(stationId), stationName, waitCondition, waitUnit, Optional.empty(), conditionGroups);
    }

    public AirshipScheduleEntry withWaitUnit(WaitDurationUnit waitUnit) {
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, conditionGroups);
    }

    public AirshipScheduleEntry withPinnedSegment(Optional<RouteSegmentId> pinnedSegmentId) {
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, conditionGroups);
    }

    public AirshipScheduleEntry withConditionGroups(List<List<AirshipScheduleCondition>> conditionGroups) {
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, conditionGroups);
    }

    public List<List<AirshipScheduleCondition>> effectiveConditionGroups() {
        return conditionGroups;
    }

    public WaitCondition primaryEffectiveWaitCondition() {
        return waitCondition;
    }

    public boolean hasEffectiveWaitCondition(Predicate<WaitCondition> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return conditionGroups.stream()
                .flatMap(List::stream)
                .map(AirshipScheduleCondition::waitCondition)
                .anyMatch(predicate);
    }

    public AirshipScheduleEntry withAddedCondition() {
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups();
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>());
        }
        groups.get(0).add(AirshipScheduleCondition.scheduledDelay(waitCondition));
        return withConditionGroups(groups);
    }

    public AirshipScheduleEntry withAddedAlternativeConditionGroup() {
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups();
        groups.add(new ArrayList<>(List.of(AirshipScheduleCondition.scheduledDelay(waitCondition))));
        return withConditionGroups(groups);
    }

    private List<List<AirshipScheduleCondition>> replaceFirstScheduledDelay(WaitCondition waitCondition) {
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups();
        for (List<AirshipScheduleCondition> group : groups) {
            for (int i = 0; i < group.size(); i++) {
                if (group.get(i).type() == AirshipScheduleConditionType.SCHEDULED_DELAY) {
                    group.set(i, AirshipScheduleCondition.scheduledDelay(waitCondition));
                    return groups;
                }
            }
        }
        groups.get(0).add(AirshipScheduleCondition.scheduledDelay(waitCondition));
        return groups;
    }

    private List<List<AirshipScheduleCondition>> mutableConditionGroups() {
        List<List<AirshipScheduleCondition>> groups = new ArrayList<>();
        for (List<AirshipScheduleCondition> group : conditionGroups) {
            groups.add(new ArrayList<>(group));
        }
        return groups;
    }

    private static List<List<AirshipScheduleCondition>> normalizeConditionGroups(
            WaitCondition waitCondition,
            List<List<AirshipScheduleCondition>> conditionGroups
    ) {
        Objects.requireNonNull(conditionGroups, "conditionGroups");
        List<List<AirshipScheduleCondition>> normalized = new ArrayList<>();
        for (List<AirshipScheduleCondition> group : conditionGroups) {
            List<AirshipScheduleCondition> normalizedGroup = new ArrayList<>();
            for (AirshipScheduleCondition condition : Objects.requireNonNull(group, "group")) {
                if (condition.waitCondition().waits()) {
                    normalizedGroup.add(condition);
                }
            }
            if (!normalizedGroup.isEmpty()) {
                normalized.add(List.copyOf(normalizedGroup));
            }
        }
        if (normalized.isEmpty() && waitCondition.waits()) {
            normalized.add(List.of(AirshipScheduleCondition.fromWaitCondition(waitCondition)));
        }
        return List.copyOf(normalized);
    }

    private static WaitCondition primaryWaitCondition(
            WaitCondition fallback,
            List<List<AirshipScheduleCondition>> conditionGroups
    ) {
        return conditionGroups.stream()
                .flatMap(List::stream)
                .map(AirshipScheduleCondition::waitCondition)
                .findFirst()
                .orElse(fallback);
    }
}

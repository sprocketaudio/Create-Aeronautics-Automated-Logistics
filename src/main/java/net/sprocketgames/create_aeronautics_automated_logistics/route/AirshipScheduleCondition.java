package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;

public record AirshipScheduleCondition(
        AirshipScheduleConditionType type,
        WaitCondition waitCondition
) {
    public AirshipScheduleCondition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(waitCondition, "waitCondition");
    }

    public static AirshipScheduleCondition scheduledDelay(WaitCondition waitCondition) {
        return new AirshipScheduleCondition(AirshipScheduleConditionType.SCHEDULED_DELAY, waitCondition);
    }

    public static AirshipScheduleCondition untilDocked(WaitCondition waitCondition) {
        return new AirshipScheduleCondition(AirshipScheduleConditionType.SCHEDULED_DELAY, waitCondition);
    }

    public static AirshipScheduleCondition fromWaitCondition(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL ->
                    new AirshipScheduleCondition(AirshipScheduleConditionType.ITEM_CARGO_CONDITION, waitCondition);
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL ->
                    new AirshipScheduleCondition(AirshipScheduleConditionType.FLUID_CARGO_CONDITION, waitCondition);
            case REDSTONE_LINK ->
                    new AirshipScheduleCondition(AirshipScheduleConditionType.REDSTONE_LINK, waitCondition);
            case TIME_OF_DAY ->
                    new AirshipScheduleCondition(AirshipScheduleConditionType.TIME_OF_DAY, waitCondition);
            default -> scheduledDelay(waitCondition);
        };
    }
}

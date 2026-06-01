package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public record WaitCondition(
        WaitConditionType type,
        int durationTicks,
        int idleTicks,
        int maxTicks,
        boolean failOnTimeout,
        int cargoStabilityTicks,
        int cargoOperator,
        int cargoMeasure,
        ItemStack cargoFilter,
        CargoWaitTarget cargoTarget,
        ItemStack redstoneFrequencyFirst,
        ItemStack redstoneFrequencySecond,
        boolean redstonePowered,
        int timeOfDayHour,
        int timeOfDayMinute,
        int timeOfDayRotation
) {
    public static final int DEFAULT_TIMED_WAIT_TICKS = 20 * 5;

    public WaitCondition {
        Objects.requireNonNull(type, "type");
        durationTicks = Math.max(0, durationTicks);
        idleTicks = Math.max(0, idleTicks);
        maxTicks = Math.max(0, maxTicks);
        cargoStabilityTicks = Math.max(0, cargoStabilityTicks);
        cargoOperator = Math.max(0, cargoOperator);
        cargoMeasure = Math.max(0, cargoMeasure);
        cargoFilter = safeCopy(cargoFilter);
        cargoTarget = cargoTarget == null ? CargoWaitTarget.SHIP_CARGO : cargoTarget;
        redstoneFrequencyFirst = safeCopy(redstoneFrequencyFirst);
        redstoneFrequencySecond = safeCopy(redstoneFrequencySecond);
        timeOfDayHour = Math.max(0, Math.min(23, timeOfDayHour));
        timeOfDayMinute = Math.max(0, Math.min(59, timeOfDayMinute));
        timeOfDayRotation = Math.max(0, Math.min(9, timeOfDayRotation));
    }

    public static WaitCondition none() {
        return base(WaitConditionType.NONE);
    }

    public static WaitCondition timed(int durationTicks) {
        return new WaitCondition(WaitConditionType.TIMED, durationTicks, 0, durationTicks, false, 0, 0, 0, ItemStack.EMPTY,
                CargoWaitTarget.SHIP_CARGO, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition untilDocked(int maxTicks) {
        return new WaitCondition(WaitConditionType.UNTIL_DOCKED, 0, 0, maxTicks, true, 0, 0, 0, ItemStack.EMPTY,
                CargoWaitTarget.SHIP_CARGO, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition untilIdle(int idleTicks, int maxTicks) {
        return new WaitCondition(WaitConditionType.UNTIL_IDLE, 0, idleTicks, maxTicks, true, 0, 0, 0, ItemStack.EMPTY,
                CargoWaitTarget.SHIP_CARGO, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition redstoneLink(ItemStack firstFrequency, ItemStack secondFrequency, boolean powered) {
        return new WaitCondition(WaitConditionType.REDSTONE_LINK, 0, 0, 0, true, 0, 0, 0, ItemStack.EMPTY,
                CargoWaitTarget.SHIP_CARGO, firstFrequency, secondFrequency, powered, 8, 0, 0);
    }

    public static WaitCondition timeOfDay(int hour, int minute, int rotation) {
        return new WaitCondition(WaitConditionType.TIME_OF_DAY, 0, 0, 0, false, 0, 0, 0, ItemStack.EMPTY,
                CargoWaitTarget.SHIP_CARGO, ItemStack.EMPTY, ItemStack.EMPTY, true, hour, minute, rotation);
    }

    public static WaitCondition itemThreshold(int itemCount, int maxTicks) {
        return itemThreshold(itemCount, maxTicks, 0, 0, ItemStack.EMPTY, CargoWaitTarget.SHIP_CARGO);
    }

    public static WaitCondition itemThreshold(int itemCount, int maxTicks, int operator, int measure, ItemStack filter) {
        return itemThreshold(itemCount, maxTicks, operator, measure, filter, CargoWaitTarget.SHIP_CARGO);
    }

    public static WaitCondition itemThreshold(int itemCount, int maxTicks, int operator, int measure, ItemStack filter, CargoWaitTarget target) {
        return itemThreshold(itemCount, maxTicks, 0, operator, measure, filter, target);
    }

    public static WaitCondition itemThreshold(int itemCount, int maxTicks, int cargoStabilityTicks, int operator, int measure, ItemStack filter, CargoWaitTarget target) {
        return new WaitCondition(WaitConditionType.UNTIL_ITEM_THRESHOLD, itemCount, 0, maxTicks, true, cargoStabilityTicks, operator, measure,
                filter, target, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition fluidThreshold(int fluidAmount, int maxTicks) {
        return fluidThreshold(fluidAmount, maxTicks, 0, 0, ItemStack.EMPTY, CargoWaitTarget.SHIP_CARGO);
    }

    public static WaitCondition fluidThreshold(int fluidAmount, int maxTicks, int operator, int measure, ItemStack filter) {
        return fluidThreshold(fluidAmount, maxTicks, operator, measure, filter, CargoWaitTarget.SHIP_CARGO);
    }

    public static WaitCondition fluidThreshold(int fluidAmount, int maxTicks, int operator, int measure, ItemStack filter, CargoWaitTarget target) {
        return fluidThreshold(fluidAmount, maxTicks, 0, operator, measure, filter, target);
    }

    public static WaitCondition fluidThreshold(int fluidAmount, int maxTicks, int cargoStabilityTicks, int operator, int measure, ItemStack filter, CargoWaitTarget target) {
        return new WaitCondition(WaitConditionType.UNTIL_FLUID_THRESHOLD, fluidAmount, 0, maxTicks, true, cargoStabilityTicks, operator, measure,
                filter, target, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition itemEmpty(int maxTicks, CargoWaitTarget target) {
        return itemEmpty(0, maxTicks, target);
    }

    public static WaitCondition itemEmpty(int cargoStabilityTicks, int maxTicks, CargoWaitTarget target) {
        return new WaitCondition(WaitConditionType.UNTIL_ITEM_EMPTY, 0, 0, maxTicks, true, cargoStabilityTicks, 0, 0, ItemStack.EMPTY,
                target, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition itemFull(int maxTicks, CargoWaitTarget target) {
        return itemFull(0, maxTicks, target);
    }

    public static WaitCondition itemFull(int cargoStabilityTicks, int maxTicks, CargoWaitTarget target) {
        return new WaitCondition(WaitConditionType.UNTIL_ITEM_FULL, 0, 0, maxTicks, true, cargoStabilityTicks, 0, 0, ItemStack.EMPTY,
                target, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition fluidEmpty(int maxTicks, CargoWaitTarget target) {
        return fluidEmpty(0, maxTicks, target);
    }

    public static WaitCondition fluidEmpty(int cargoStabilityTicks, int maxTicks, CargoWaitTarget target) {
        return new WaitCondition(WaitConditionType.UNTIL_FLUID_EMPTY, 0, 0, maxTicks, true, cargoStabilityTicks, 0, 0, ItemStack.EMPTY,
                target, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public static WaitCondition fluidFull(int maxTicks, CargoWaitTarget target) {
        return fluidFull(0, maxTicks, target);
    }

    public static WaitCondition fluidFull(int cargoStabilityTicks, int maxTicks, CargoWaitTarget target) {
        return new WaitCondition(WaitConditionType.UNTIL_FLUID_FULL, 0, 0, maxTicks, true, cargoStabilityTicks, 0, 0, ItemStack.EMPTY,
                target, ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    public boolean waits() {
        return type != WaitConditionType.NONE;
    }

    public int runtimeTicks() {
        return switch (type) {
            case NONE -> 0;
            case TIMED -> durationTicks;
            case UNTIL_IDLE -> idleTicks;
            default -> maxTicks > 0 ? maxTicks : durationTicks;
        };
    }

    public int timeOfDayRotationTicks() {
        return switch (timeOfDayRotation) {
            case 9 -> 250;
            case 8 -> 500;
            case 7 -> 750;
            case 6 -> 1000;
            case 5 -> 2000;
            case 4 -> 3000;
            case 3 -> 4000;
            case 2 -> 6000;
            case 1 -> 12000;
            default -> 24000;
        };
    }

    private static WaitCondition base(WaitConditionType type) {
        return new WaitCondition(type, 0, 0, 0, false, 0, 0, 0, ItemStack.EMPTY, CargoWaitTarget.SHIP_CARGO,
                ItemStack.EMPTY, ItemStack.EMPTY, true, 8, 0, 0);
    }

    private static ItemStack safeCopy(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }
}

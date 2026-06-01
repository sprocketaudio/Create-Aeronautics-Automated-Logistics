package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AirshipScheduleNbtSerializer {
    private static final String DATA_VERSION = "dataVersion";
    private static final String TITLE = "title";
    private static final String LOOP = "loop";
    private static final String ASSIGNED_TRANSPONDER_ID = "assignedTransponderId";
    private static final String ASSIGNED_SHIP_NAME = "assignedShipName";
    private static final String ENTRIES = "entries";
    private static final String TYPE = "type";
    private static final String TARGET_STATION_ID = "targetStationId";
    private static final String TARGET_STATION_NAME = "targetStationName";
    private static final String WAIT_TYPE = "waitType";
    private static final String WAIT_TICKS = "waitTicks";
    private static final String WAIT_UNIT = "waitUnit";
    private static final String PINNED_SEGMENT_ID = "pinnedSegmentId";
    private static final String CONDITION_GROUPS = "conditionGroups";
    private static final String CONDITIONS = "conditions";
    private static final String CONDITION_TYPE = "conditionType";
    private static final String CONDITION_WAIT_TYPE = "conditionWaitType";
    private static final String CONDITION_WAIT_TICKS = "conditionWaitTicks";
    private static final String CONDITION_CARGO_STABLE_TICKS = "conditionCargoStableTicks";
    private static final String CONDITION_CARGO_OPERATOR = "conditionCargoOperator";
    private static final String CONDITION_CARGO_MEASURE = "conditionCargoMeasure";
    private static final String CONDITION_CARGO_FILTER = "conditionCargoFilter";
    private static final String CONDITION_CARGO_TARGET = "conditionCargoTarget";
    private static final String CONDITION_REDSTONE_FIRST = "conditionRedstoneFirst";
    private static final String CONDITION_REDSTONE_SECOND = "conditionRedstoneSecond";
    private static final String CONDITION_REDSTONE_POWERED = "conditionRedstonePowered";
    private static final String CONDITION_TIME_HOUR = "conditionTimeHour";
    private static final String CONDITION_TIME_MINUTE = "conditionTimeMinute";
    private static final String CONDITION_TIME_ROTATION = "conditionTimeRotation";
    private static final String WAIT_CARGO_TARGET = "waitCargoTarget";
    private static final String WAIT_CARGO_STABLE_TICKS = "waitCargoStableTicks";
    private static final String WAIT_REDSTONE_FIRST = "waitRedstoneFirst";
    private static final String WAIT_REDSTONE_SECOND = "waitRedstoneSecond";
    private static final String WAIT_REDSTONE_POWERED = "waitRedstonePowered";
    private static final String WAIT_TIME_HOUR = "waitTimeHour";
    private static final String WAIT_TIME_MINUTE = "waitTimeMinute";
    private static final String WAIT_TIME_ROTATION = "waitTimeRotation";
    private static final int CURRENT_DATA_VERSION = 3;

    private AirshipScheduleNbtSerializer() {
    }

    public static CompoundTag write(AirshipSchedule schedule) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putString(TITLE, schedule.title());
        tag.putBoolean(LOOP, schedule.loop());
        schedule.assignedTransponderId().ifPresent(id -> tag.putUUID(ASSIGNED_TRANSPONDER_ID, id));
        if (!schedule.assignedShipName().isBlank()) {
            tag.putString(ASSIGNED_SHIP_NAME, schedule.assignedShipName());
        }
        ListTag entries = new ListTag();
        for (AirshipScheduleEntry entry : schedule.entries()) {
            entries.add(writeEntry(entry));
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    public static AirshipSchedule read(CompoundTag tag) {
        String title = tag.contains(TITLE, Tag.TAG_STRING) ? tag.getString(TITLE) : "Airship Schedule";
        boolean loop = !tag.contains(LOOP, Tag.TAG_BYTE) || tag.getBoolean(LOOP);
        Optional<UUID> assignedTransponderId = tag.hasUUID(ASSIGNED_TRANSPONDER_ID)
                ? Optional.of(tag.getUUID(ASSIGNED_TRANSPONDER_ID))
                : Optional.empty();
        String assignedShipName = tag.contains(ASSIGNED_SHIP_NAME, Tag.TAG_STRING) ? tag.getString(ASSIGNED_SHIP_NAME) : "";
        List<AirshipScheduleEntry> entries = new ArrayList<>();
        if (tag.contains(ENTRIES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                readEntry(list.getCompound(i)).ifPresent(entries::add);
            }
        }
        return new AirshipSchedule(title, loop, assignedTransponderId, assignedShipName, entries);
    }

    public static boolean isLegacyUnbound(CompoundTag tag) {
        int dataVersion = tag.contains(DATA_VERSION, Tag.TAG_ANY_NUMERIC) ? tag.getInt(DATA_VERSION) : 0;
        return dataVersion < 2 && !tag.hasUUID(ASSIGNED_TRANSPONDER_ID);
    }

    private static CompoundTag writeEntry(AirshipScheduleEntry entry) {
        CompoundTag tag = new CompoundTag();
        WaitCondition primaryWait = entry.primaryEffectiveWaitCondition();
        List<List<AirshipScheduleCondition>> conditionGroups = entry.effectiveConditionGroups();
        tag.putString(TYPE, entry.type().name());
        entry.targetStationId().ifPresent(id -> tag.putUUID(TARGET_STATION_ID, id));
        tag.putString(TARGET_STATION_NAME, entry.targetStationName());
        tag.putString(WAIT_TYPE, primaryWait.type().name());
        tag.putInt(WAIT_TICKS, serializedWaitTicks(primaryWait));
        tag.putString(WAIT_CARGO_TARGET, primaryWait.cargoTarget().name());
        tag.putInt(WAIT_CARGO_STABLE_TICKS, primaryWait.cargoStabilityTicks());
        writeRedstoneAndTime(primaryWait, tag, WAIT_REDSTONE_FIRST, WAIT_REDSTONE_SECOND, WAIT_REDSTONE_POWERED, WAIT_TIME_HOUR, WAIT_TIME_MINUTE, WAIT_TIME_ROTATION);
        tag.putString(WAIT_UNIT, entry.waitUnit().name());
        entry.pinnedSegmentId().ifPresent(id -> tag.putUUID(PINNED_SEGMENT_ID, id.value()));
        tag.put(CONDITION_GROUPS, writeConditionGroups(conditionGroups));
        return tag;
    }

    private static Optional<AirshipScheduleEntry> readEntry(CompoundTag tag) {
        try {
            AirshipScheduleEntryType type = AirshipScheduleEntryType.valueOf(tag.getString(TYPE));
            Optional<UUID> targetStationId = tag.hasUUID(TARGET_STATION_ID)
                    ? Optional.of(tag.getUUID(TARGET_STATION_ID))
                    : Optional.empty();
            String targetStationName = tag.contains(TARGET_STATION_NAME, Tag.TAG_STRING)
                    ? tag.getString(TARGET_STATION_NAME)
                    : "";
            WaitConditionType waitType = tag.contains(WAIT_TYPE, Tag.TAG_STRING)
                    ? WaitConditionType.valueOf(tag.getString(WAIT_TYPE))
                    : WaitConditionType.TIMED;
            int waitTicks = tag.contains(WAIT_TICKS, Tag.TAG_ANY_NUMERIC)
                    ? Math.max(0, tag.getInt(WAIT_TICKS))
                    : WaitCondition.DEFAULT_TIMED_WAIT_TICKS;
            WaitCondition waitCondition = readWaitCondition(
                    waitType,
                    waitTicks,
                    0,
                    0,
                    ItemStack.EMPTY,
                    readCargoTarget(tag, WAIT_CARGO_TARGET),
                    tag.contains(WAIT_CARGO_STABLE_TICKS, Tag.TAG_ANY_NUMERIC) ? tag.getInt(WAIT_CARGO_STABLE_TICKS) : 0,
                    readOptionalItem(tag, WAIT_REDSTONE_FIRST),
                    readOptionalItem(tag, WAIT_REDSTONE_SECOND),
                    !tag.contains(WAIT_REDSTONE_POWERED, Tag.TAG_BYTE) || tag.getBoolean(WAIT_REDSTONE_POWERED),
                    tag.contains(WAIT_TIME_HOUR, Tag.TAG_ANY_NUMERIC) ? tag.getInt(WAIT_TIME_HOUR) : 8,
                    tag.contains(WAIT_TIME_MINUTE, Tag.TAG_ANY_NUMERIC) ? tag.getInt(WAIT_TIME_MINUTE) : 0,
                    tag.contains(WAIT_TIME_ROTATION, Tag.TAG_ANY_NUMERIC) ? tag.getInt(WAIT_TIME_ROTATION) : 0
            );
            WaitDurationUnit waitUnit = tag.contains(WAIT_UNIT, Tag.TAG_STRING)
                    ? WaitDurationUnit.valueOf(tag.getString(WAIT_UNIT))
                    : WaitDurationUnit.SECONDS;
            Optional<RouteSegmentId> pinnedSegmentId = tag.hasUUID(PINNED_SEGMENT_ID)
                    ? Optional.of(new RouteSegmentId(tag.getUUID(PINNED_SEGMENT_ID)))
                    : Optional.empty();
            List<List<AirshipScheduleCondition>> conditionGroups = tag.contains(CONDITION_GROUPS, Tag.TAG_LIST)
                    ? readConditionGroups(tag.getList(CONDITION_GROUPS, Tag.TAG_COMPOUND))
                    : List.of(List.of(AirshipScheduleCondition.scheduledDelay(waitCondition)));
            return Optional.of(new AirshipScheduleEntry(
                    type,
                    targetStationId,
                    targetStationName,
                    waitCondition,
                    waitUnit,
                    pinnedSegmentId,
                    conditionGroups
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static ListTag writeConditionGroups(List<List<AirshipScheduleCondition>> conditionGroups) {
        ListTag groups = new ListTag();
        for (List<AirshipScheduleCondition> group : conditionGroups) {
            CompoundTag groupTag = new CompoundTag();
            ListTag conditions = new ListTag();
            for (AirshipScheduleCondition condition : group) {
                CompoundTag conditionTag = new CompoundTag();
                conditionTag.putString(CONDITION_TYPE, condition.type().name());
                conditionTag.putString(CONDITION_WAIT_TYPE, condition.waitCondition().type().name());
                conditionTag.putInt(CONDITION_WAIT_TICKS, serializedWaitTicks(condition.waitCondition()));
                conditionTag.putInt(CONDITION_CARGO_STABLE_TICKS, condition.waitCondition().cargoStabilityTicks());
                conditionTag.putInt(CONDITION_CARGO_OPERATOR, condition.waitCondition().cargoOperator());
                conditionTag.putInt(CONDITION_CARGO_MEASURE, condition.waitCondition().cargoMeasure());
                conditionTag.putString(CONDITION_CARGO_TARGET, condition.waitCondition().cargoTarget().name());
                if (!condition.waitCondition().cargoFilter().isEmpty()) {
                    conditionTag.putString(CONDITION_CARGO_FILTER, BuiltInRegistries.ITEM.getKey(condition.waitCondition().cargoFilter().getItem()).toString());
                }
                writeRedstoneAndTime(condition.waitCondition(), conditionTag, CONDITION_REDSTONE_FIRST, CONDITION_REDSTONE_SECOND, CONDITION_REDSTONE_POWERED, CONDITION_TIME_HOUR, CONDITION_TIME_MINUTE, CONDITION_TIME_ROTATION);
                conditions.add(conditionTag);
            }
            groupTag.put(CONDITIONS, conditions);
            groups.add(groupTag);
        }
        return groups;
    }

    private static List<List<AirshipScheduleCondition>> readConditionGroups(ListTag groupsTag) {
        List<List<AirshipScheduleCondition>> groups = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groupsTag.size(); groupIndex++) {
            CompoundTag groupTag = groupsTag.getCompound(groupIndex);
            List<AirshipScheduleCondition> conditions = new ArrayList<>();
            if (groupTag.contains(CONDITIONS, Tag.TAG_LIST)) {
                ListTag conditionTags = groupTag.getList(CONDITIONS, Tag.TAG_COMPOUND);
                for (int conditionIndex = 0; conditionIndex < conditionTags.size(); conditionIndex++) {
                    readCondition(conditionTags.getCompound(conditionIndex)).ifPresent(conditions::add);
                }
            }
            if (!conditions.isEmpty()) {
                groups.add(conditions);
            }
        }
        return List.copyOf(groups);
    }

    private static Optional<AirshipScheduleCondition> readCondition(CompoundTag tag) {
        try {
            AirshipScheduleConditionType type = tag.contains(CONDITION_TYPE, Tag.TAG_STRING)
                    ? AirshipScheduleConditionType.valueOf(tag.getString(CONDITION_TYPE))
                    : AirshipScheduleConditionType.SCHEDULED_DELAY;
            WaitConditionType waitType = tag.contains(CONDITION_WAIT_TYPE, Tag.TAG_STRING)
                    ? WaitConditionType.valueOf(tag.getString(CONDITION_WAIT_TYPE))
                    : WaitConditionType.TIMED;
            int waitTicks = tag.contains(CONDITION_WAIT_TICKS, Tag.TAG_ANY_NUMERIC)
                    ? Math.max(0, tag.getInt(CONDITION_WAIT_TICKS))
                    : WaitCondition.DEFAULT_TIMED_WAIT_TICKS;
            int operator = tag.contains(CONDITION_CARGO_OPERATOR, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_CARGO_OPERATOR) : 0;
            int measure = tag.contains(CONDITION_CARGO_MEASURE, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_CARGO_MEASURE) : 0;
            ItemStack filter = readFilter(tag);
            WaitCondition waitCondition = readWaitCondition(
                    waitType,
                    waitTicks,
                    operator,
                    measure,
                    filter,
                    readCargoTarget(tag, CONDITION_CARGO_TARGET),
                    tag.contains(CONDITION_CARGO_STABLE_TICKS, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_CARGO_STABLE_TICKS) : 0,
                    readOptionalItem(tag, CONDITION_REDSTONE_FIRST),
                    readOptionalItem(tag, CONDITION_REDSTONE_SECOND),
                    !tag.contains(CONDITION_REDSTONE_POWERED, Tag.TAG_BYTE) || tag.getBoolean(CONDITION_REDSTONE_POWERED),
                    tag.contains(CONDITION_TIME_HOUR, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_TIME_HOUR) : 8,
                    tag.contains(CONDITION_TIME_MINUTE, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_TIME_MINUTE) : 0,
                    tag.contains(CONDITION_TIME_ROTATION, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_TIME_ROTATION) : 0
            );
            return Optional.of(new AirshipScheduleCondition(type, waitCondition));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static ItemStack readFilter(CompoundTag tag) {
        if (!tag.contains(CONDITION_CARGO_FILTER, Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(CONDITION_CARGO_FILTER));
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static int serializedWaitTicks(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case UNTIL_DOCKED -> waitCondition.maxTicks();
            case UNTIL_IDLE -> waitCondition.idleTicks();
            default -> waitCondition.durationTicks();
        };
    }

    private static WaitCondition readWaitCondition(
            WaitConditionType type,
            int ticks,
            int operator,
            int measure,
            ItemStack filter,
            CargoWaitTarget target,
            int cargoStableTicks,
            ItemStack redstoneFirst,
            ItemStack redstoneSecond,
            boolean redstonePowered,
            int timeHour,
            int timeMinute,
            int timeRotation
    ) {
        return switch (type) {
            case NONE -> WaitCondition.none();
            case TIMED -> WaitCondition.timed(ticks);
            case UNTIL_DOCKED -> WaitCondition.untilDocked(ticks);
            case UNTIL_IDLE -> WaitCondition.untilIdle(ticks, 0);
            case REDSTONE_LINK, REDSTONE -> WaitCondition.redstoneLink(redstoneFirst, redstoneSecond, redstonePowered);
            case TIME_OF_DAY -> WaitCondition.timeOfDay(timeHour, timeMinute, timeRotation);
            case UNTIL_ITEM_THRESHOLD -> WaitCondition.itemThreshold(ticks, 0, cargoStableTicks, operator, measure, filter, target);
            case UNTIL_FLUID_THRESHOLD -> WaitCondition.fluidThreshold(ticks, 0, cargoStableTicks, operator, measure, filter, target);
            case UNTIL_ITEM_EMPTY -> WaitCondition.itemEmpty(cargoStableTicks, ticks, target);
            case UNTIL_ITEM_FULL -> WaitCondition.itemFull(cargoStableTicks, ticks, target);
            case UNTIL_FLUID_EMPTY -> WaitCondition.fluidEmpty(cargoStableTicks, ticks, target);
            case UNTIL_FLUID_FULL -> WaitCondition.fluidFull(cargoStableTicks, ticks, target);
            case UNTIL_EMPTY -> WaitCondition.itemEmpty(cargoStableTicks, ticks, target);
            case UNTIL_FULL -> WaitCondition.itemFull(cargoStableTicks, ticks, target);
            default -> new WaitCondition(type, ticks, 0, ticks, true, cargoStableTicks, operator, measure, filter, target,
                    redstoneFirst, redstoneSecond, redstonePowered, timeHour, timeMinute, timeRotation);
        };
    }

    private static void writeRedstoneAndTime(
            WaitCondition waitCondition,
            CompoundTag tag,
            String redstoneFirstKey,
            String redstoneSecondKey,
            String redstonePoweredKey,
            String timeHourKey,
            String timeMinuteKey,
            String timeRotationKey
    ) {
        if (!waitCondition.redstoneFrequencyFirst().isEmpty()) {
            tag.putString(redstoneFirstKey, BuiltInRegistries.ITEM.getKey(waitCondition.redstoneFrequencyFirst().getItem()).toString());
        }
        if (!waitCondition.redstoneFrequencySecond().isEmpty()) {
            tag.putString(redstoneSecondKey, BuiltInRegistries.ITEM.getKey(waitCondition.redstoneFrequencySecond().getItem()).toString());
        }
        tag.putBoolean(redstonePoweredKey, waitCondition.redstonePowered());
        tag.putInt(timeHourKey, waitCondition.timeOfDayHour());
        tag.putInt(timeMinuteKey, waitCondition.timeOfDayMinute());
        tag.putInt(timeRotationKey, waitCondition.timeOfDayRotation());
    }

    private static ItemStack readOptionalItem(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static CargoWaitTarget readCargoTarget(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return CargoWaitTarget.SHIP_CARGO;
        }
        try {
            return CargoWaitTarget.valueOf(tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return CargoWaitTarget.SHIP_CARGO;
        }
    }
}

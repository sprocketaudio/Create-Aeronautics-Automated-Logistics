package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public final class RouteNbtSerializer {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String DIMENSION = "dimension";
    private static final String POINTS = "points";
    private static final String STOPS = "stops";
    private static final String CONTROLLER = "controller";
    private static final String PLAYBACK_MODE = "playbackMode";
    private static final String STATUS = "status";
    private static final String OWNER_ID = "ownerId";
    private static final String CONDITION_GROUPS = "conditionGroups";
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

    private RouteNbtSerializer() {
    }

    public static CompoundTag write(Route route) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, route.id().value());
        tag.putString(NAME, route.name());
        tag.putString(DIMENSION, route.dimension().location().toString());
        tag.put(POINTS, writePoints(route.points()));
        tag.put(STOPS, writeStops(route.stops()));
        tag.put(CONTROLLER, writeController(route.linkedController()));
        tag.putString(PLAYBACK_MODE, route.playbackMode().name());
        tag.putString(STATUS, route.status().name());
        route.ownerId().ifPresent(ownerId -> tag.putUUID(OWNER_ID, ownerId));
        return tag;
    }

    public static Optional<Route> read(CompoundTag tag) {
        try {
            RouteId id = new RouteId(tag.getUUID(ID));
            String name = tag.getString(NAME);
            ResourceKey<Level> dimension = readDimension(tag.getString(DIMENSION));
            List<RoutePoint> points = readPoints(tag.getList(POINTS, Tag.TAG_COMPOUND), dimension);
            List<RouteStop> stops = tag.contains(STOPS, Tag.TAG_LIST)
                    ? readStops(tag.getList(STOPS, Tag.TAG_COMPOUND), points.size())
                    : List.of();
            VehicleControllerRef controller = readController(tag.getCompound(CONTROLLER));
            PlaybackMode playbackMode = PlaybackMode.valueOf(tag.getString(PLAYBACK_MODE));
            RouteStatus status = RouteStatus.valueOf(tag.getString(STATUS));
            Optional<UUID> ownerId = tag.hasUUID(OWNER_ID) ? Optional.of(tag.getUUID(OWNER_ID)) : Optional.empty();

            if (points.size() < 2 || !controller.dimension().equals(dimension)) {
                return Optional.empty();
            }

            return Optional.of(new Route(id, name, dimension, points, controller, playbackMode, status, stops, ownerId));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static ListTag writeStops(List<RouteStop> stops) {
        ListTag list = new ListTag();
        for (RouteStop stop : stops) {
            CompoundTag stopTag = new CompoundTag();
            stopTag.putUUID("id", stop.id());
            stopTag.putString("name", stop.name());
            stopTag.putInt("pointIndex", stop.pointIndex());
            stopTag.put("waitCondition", writeWaitCondition(stop.waitCondition()));
            if (!stop.conditionGroups().isEmpty()) {
                stopTag.put(CONDITION_GROUPS, writeConditionGroups(stop.conditionGroups()));
            }
            stop.dockPos().ifPresent(pos -> {
                stopTag.putInt("dockX", pos.getX());
                stopTag.putInt("dockY", pos.getY());
                stopTag.putInt("dockZ", pos.getZ());
            });
            stop.stationId().ifPresent(id -> stopTag.putUUID("stationId", id));
            list.add(stopTag);
        }
        return list;
    }

    public static List<RouteStop> readStops(ListTag stopsTag, int pointCount) {
        List<RouteStop> stops = new ArrayList<>();
        for (int i = 0; i < stopsTag.size(); i++) {
            CompoundTag stopTag = stopsTag.getCompound(i);
            int pointIndex = stopTag.getInt("pointIndex");
            if (pointIndex < 0 || pointIndex >= pointCount) {
                throw new IllegalArgumentException("route stop point index outside route points");
            }
            Optional<BlockPos> dockPos = stopTag.contains("dockX", Tag.TAG_ANY_NUMERIC)
                    && stopTag.contains("dockY", Tag.TAG_ANY_NUMERIC)
                    && stopTag.contains("dockZ", Tag.TAG_ANY_NUMERIC)
                    ? Optional.of(new BlockPos(stopTag.getInt("dockX"), stopTag.getInt("dockY"), stopTag.getInt("dockZ")))
                    : Optional.empty();
            stops.add(new RouteStop(
                    stopTag.hasUUID("id") ? stopTag.getUUID("id") : UUID.randomUUID(),
                    stopTag.getString("name").isBlank() ? "Stop " + (i + 1) : stopTag.getString("name"),
                    pointIndex,
                    readWaitCondition(stopTag.getCompound("waitCondition")),
                    dockPos,
                    stopTag.hasUUID("stationId") ? Optional.of(stopTag.getUUID("stationId")) : Optional.empty(),
                    stopTag.contains(CONDITION_GROUPS, Tag.TAG_LIST)
                            ? readConditionGroups(stopTag.getList(CONDITION_GROUPS, Tag.TAG_LIST))
                            : List.of()
            ));
        }
        return stops;
    }

    private static ListTag writeConditionGroups(List<List<AirshipScheduleCondition>> groups) {
        ListTag outer = new ListTag();
        for (List<AirshipScheduleCondition> group : groups) {
            ListTag inner = new ListTag();
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
                inner.add(conditionTag);
            }
            outer.add(inner);
        }
        return outer;
    }

    private static List<List<AirshipScheduleCondition>> readConditionGroups(ListTag groupsTag) {
        List<List<AirshipScheduleCondition>> groups = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groupsTag.size(); groupIndex++) {
            Tag groupTag = groupsTag.get(groupIndex);
            if (!(groupTag instanceof ListTag listTag)) {
                continue;
            }
            List<AirshipScheduleCondition> conditions = new ArrayList<>();
            for (int conditionIndex = 0; conditionIndex < listTag.size(); conditionIndex++) {
                Tag conditionTag = listTag.get(conditionIndex);
                if (!(conditionTag instanceof CompoundTag compoundTag)) {
                    continue;
                }
                readCondition(compoundTag).ifPresent(conditions::add);
            }
            if (!conditions.isEmpty()) {
                groups.add(List.copyOf(conditions));
            }
        }
        return List.copyOf(groups);
    }

    private static CompoundTag writeWaitCondition(WaitCondition waitCondition) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", waitCondition.type().name());
        tag.putInt("durationTicks", waitCondition.durationTicks());
        tag.putInt("idleTicks", waitCondition.idleTicks());
        tag.putInt("maxTicks", waitCondition.maxTicks());
        tag.putBoolean("failOnTimeout", waitCondition.failOnTimeout());
        tag.putInt("cargoStabilityTicks", waitCondition.cargoStabilityTicks());
        tag.putInt("cargoOperator", waitCondition.cargoOperator());
        tag.putInt("cargoMeasure", waitCondition.cargoMeasure());
        tag.putString("cargoTarget", waitCondition.cargoTarget().name());
        if (!waitCondition.cargoFilter().isEmpty()) {
            tag.putString("cargoFilter", BuiltInRegistries.ITEM.getKey(waitCondition.cargoFilter().getItem()).toString());
        }
        writeRedstoneAndTime(waitCondition, tag, "redstoneFirst", "redstoneSecond", "redstonePowered", "timeHour", "timeMinute", "timeRotation");
        return tag;
    }

    private static WaitCondition readWaitCondition(CompoundTag tag) {
        if (!tag.contains("type", Tag.TAG_STRING)) {
            return WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS);
        }
        return new WaitCondition(
                WaitConditionType.valueOf(tag.getString("type")),
                tag.getInt("durationTicks"),
                tag.getInt("idleTicks"),
                tag.getInt("maxTicks"),
                tag.getBoolean("failOnTimeout"),
                tag.contains("cargoStabilityTicks", Tag.TAG_ANY_NUMERIC) ? tag.getInt("cargoStabilityTicks") : 0,
                tag.contains("cargoOperator", Tag.TAG_ANY_NUMERIC) ? tag.getInt("cargoOperator") : 0,
                tag.contains("cargoMeasure", Tag.TAG_ANY_NUMERIC) ? tag.getInt("cargoMeasure") : 0,
                readCargoFilter(tag),
                readCargoTarget(tag),
                readItem(tag, "redstoneFirst"),
                readItem(tag, "redstoneSecond"),
                !tag.contains("redstonePowered", Tag.TAG_BYTE) || tag.getBoolean("redstonePowered"),
                tag.contains("timeHour", Tag.TAG_ANY_NUMERIC) ? tag.getInt("timeHour") : 8,
                tag.contains("timeMinute", Tag.TAG_ANY_NUMERIC) ? tag.getInt("timeMinute") : 0,
                tag.contains("timeRotation", Tag.TAG_ANY_NUMERIC) ? tag.getInt("timeRotation") : 0
        );
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
            ItemStack filter = readConditionFilter(tag);
            WaitCondition waitCondition = readConditionWaitCondition(
                    waitType,
                    waitTicks,
                    operator,
                    measure,
                    filter,
                    readConditionCargoTarget(tag),
                    tag.contains(CONDITION_CARGO_STABLE_TICKS, Tag.TAG_ANY_NUMERIC) ? tag.getInt(CONDITION_CARGO_STABLE_TICKS) : 0,
                    readItem(tag, CONDITION_REDSTONE_FIRST),
                    readItem(tag, CONDITION_REDSTONE_SECOND),
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

    private static ItemStack readConditionFilter(CompoundTag tag) {
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

    private static CargoWaitTarget readConditionCargoTarget(CompoundTag tag) {
        if (!tag.contains(CONDITION_CARGO_TARGET, Tag.TAG_STRING)) {
            return CargoWaitTarget.SHIP_CARGO;
        }
        try {
            return CargoWaitTarget.valueOf(tag.getString(CONDITION_CARGO_TARGET));
        } catch (IllegalArgumentException ignored) {
            return CargoWaitTarget.SHIP_CARGO;
        }
    }

    private static int serializedWaitTicks(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case UNTIL_DOCKED -> waitCondition.maxTicks();
            case UNTIL_IDLE -> waitCondition.idleTicks();
            default -> waitCondition.durationTicks();
        };
    }

    private static WaitCondition readConditionWaitCondition(
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

    private static ItemStack readItem(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ItemStack readCargoFilter(CompoundTag tag) {
        if (!tag.contains("cargoFilter", Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("cargoFilter"));
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static CargoWaitTarget readCargoTarget(CompoundTag tag) {
        if (!tag.contains("cargoTarget", Tag.TAG_STRING)) {
            return CargoWaitTarget.SHIP_CARGO;
        }
        try {
            return CargoWaitTarget.valueOf(tag.getString("cargoTarget"));
        } catch (IllegalArgumentException ignored) {
            return CargoWaitTarget.SHIP_CARGO;
        }
    }

    private static ListTag writePoints(List<RoutePoint> points) {
        ListTag list = new ListTag();
        for (RoutePoint point : points) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble("x", point.position().x());
            pointTag.putDouble("y", point.position().y());
            pointTag.putDouble("z", point.position().z());
            point.yaw().ifPresent(yaw -> pointTag.putFloat("yaw", yaw));
            point.rotation().ifPresent(rotation -> {
                pointTag.putDouble("rotX", rotation.x());
                pointTag.putDouble("rotY", rotation.y());
                pointTag.putDouble("rotZ", rotation.z());
                pointTag.putDouble("rotW", rotation.w());
            });
            pointTag.putLong("tickOffset", point.tickOffset());
            pointTag.putString(DIMENSION, point.dimension().location().toString());
            list.add(pointTag);
        }
        return list;
    }

    private static List<RoutePoint> readPoints(ListTag pointsTag, ResourceKey<Level> routeDimension) {
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < pointsTag.size(); i++) {
            CompoundTag pointTag = pointsTag.getCompound(i);
            ResourceKey<Level> pointDimension = readDimension(pointTag.getString(DIMENSION));
            if (!pointDimension.equals(routeDimension)) {
                throw new IllegalArgumentException("route point dimension mismatch");
            }

            Optional<Float> yaw = pointTag.contains("yaw", Tag.TAG_FLOAT)
                    ? Optional.of(pointTag.getFloat("yaw"))
                    : Optional.empty();
            Optional<RouteRotation> rotation = pointTag.contains("rotX", Tag.TAG_DOUBLE)
                    && pointTag.contains("rotY", Tag.TAG_DOUBLE)
                    && pointTag.contains("rotZ", Tag.TAG_DOUBLE)
                    && pointTag.contains("rotW", Tag.TAG_DOUBLE)
                    ? Optional.of(new RouteRotation(
                            pointTag.getDouble("rotX"),
                            pointTag.getDouble("rotY"),
                            pointTag.getDouble("rotZ"),
                            pointTag.getDouble("rotW")
                    ))
                    : Optional.empty();
            points.add(new RoutePoint(
                    new Vec3(pointTag.getDouble("x"), pointTag.getDouble("y"), pointTag.getDouble("z")),
                    yaw,
                    rotation,
                    pointTag.getLong("tickOffset"),
                    pointDimension
            ));
        }
        return points;
    }

    private static CompoundTag writeController(VehicleControllerRef controller) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", controller.controllerType().toString());
        tag.putString(DIMENSION, controller.dimension().location().toString());
        controller.vehicleId().ifPresent(vehicleId -> tag.putUUID("vehicleId", vehicleId));
        controller.controllerPos().ifPresent(pos -> {
            tag.putInt("controllerX", pos.getX());
            tag.putInt("controllerY", pos.getY());
            tag.putInt("controllerZ", pos.getZ());
        });
        return tag;
    }

    private static VehicleControllerRef readController(CompoundTag tag) {
        ResourceLocation controllerType = ResourceLocation.parse(tag.getString("type"));
        ResourceKey<Level> dimension = readDimension(tag.getString(DIMENSION));
        Optional<UUID> vehicleId = tag.hasUUID("vehicleId") ? Optional.of(tag.getUUID("vehicleId")) : Optional.empty();
        Optional<BlockPos> controllerPos = tag.contains("controllerX", Tag.TAG_ANY_NUMERIC)
                && tag.contains("controllerY", Tag.TAG_ANY_NUMERIC)
                && tag.contains("controllerZ", Tag.TAG_ANY_NUMERIC)
                ? Optional.of(new BlockPos(tag.getInt("controllerX"), tag.getInt("controllerY"), tag.getInt("controllerZ")))
                : Optional.empty();
        return new VehicleControllerRef(controllerType, dimension, vehicleId, controllerPos);
    }

    private static ResourceKey<Level> readDimension(String value) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(value));
    }
}

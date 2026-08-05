package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public final class RouteSegmentNbtSerializer {
    private static final String DATA_VERSION = "dataVersion";
    private static final String ID = "id";
    private static final String START_STATION_ID = "startStationId";
    private static final String START_STATION_NAME = "startStationName";
    private static final String END_STATION_ID = "endStationId";
    private static final String END_STATION_NAME = "endStationName";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SHIP_NAME = "shipName";
    private static final String RUNTIME_SHIP_ID = "runtimeShipId";
    private static final String DIMENSION = "dimension";
    private static final String POINTS = "points";
    private static final String CONTROLLER = "controller";
    private static final String CONTROLLER_TYPE = "controllerType";
    private static final String VEHICLE_ID = "vehicleId";
    private static final String CONTROLLER_X = "controllerX";
    private static final String CONTROLLER_Y = "controllerY";
    private static final String CONTROLLER_Z = "controllerZ";
    private static final String CREATED_GAME_TIME = "createdGameTime";
    private static final String CREATED_EPOCH_MILLIS = "createdEpochMillis";
    private static final String OWNER_ID = "ownerId";
    private static final int CURRENT_DATA_VERSION = 1;

    private RouteSegmentNbtSerializer() {
    }

    public static CompoundTag write(RouteSegment segment) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putUUID(ID, segment.id().value());
        TransportMode.write(tag, segment.transportMode());
        tag.putUUID(START_STATION_ID, segment.startStationId());
        tag.putString(START_STATION_NAME, segment.startStationName());
        tag.putUUID(END_STATION_ID, segment.endStationId());
        tag.putString(END_STATION_NAME, segment.endStationName());
        tag.putUUID(TRANSPONDER_ID, segment.transponderId());
        tag.putString(SHIP_NAME, segment.shipName());
        segment.runtimeShipId().ifPresent(id -> tag.putUUID(RUNTIME_SHIP_ID, id));
        tag.putString(DIMENSION, segment.dimension().location().toString());
        tag.put(POINTS, writePoints(segment.points()));
        tag.put(CONTROLLER, writeController(segment.controllerRef()));
        tag.putLong(CREATED_GAME_TIME, segment.createdGameTime());
        tag.putLong(CREATED_EPOCH_MILLIS, segment.createdEpochMillis());
        segment.ownerId().ifPresent(id -> tag.putUUID(OWNER_ID, id));
        return tag;
    }

    public static Optional<RouteSegment> read(CompoundTag tag) {
        try {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(DIMENSION)));
            List<RoutePoint> points = readPoints(tag.getList(POINTS, Tag.TAG_COMPOUND), dimension);
            VehicleControllerRef controllerRef = readController(tag.getCompound(CONTROLLER), dimension);
            Optional<UUID> runtimeShipId = tag.hasUUID(RUNTIME_SHIP_ID)
                    ? Optional.of(tag.getUUID(RUNTIME_SHIP_ID))
                    : Optional.empty();
            Optional<UUID> ownerId = tag.hasUUID(OWNER_ID)
                    ? Optional.of(tag.getUUID(OWNER_ID))
                    : Optional.empty();
            return Optional.of(new RouteSegment(
                    new RouteSegmentId(tag.getUUID(ID)),
                    TransportMode.read(tag),
                    tag.getUUID(START_STATION_ID),
                    tag.getString(START_STATION_NAME),
                    tag.getUUID(END_STATION_ID),
                    tag.getString(END_STATION_NAME),
                    tag.getUUID(TRANSPONDER_ID),
                    tag.getString(SHIP_NAME),
                    runtimeShipId,
                    dimension,
                    points,
                    controllerRef,
                    tag.getLong(CREATED_GAME_TIME),
                    tag.getLong(CREATED_EPOCH_MILLIS),
                    ownerId
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static ListTag writeSegments(List<RouteSegment> segments) {
        ListTag list = new ListTag();
        for (RouteSegment segment : segments) {
            list.add(write(segment));
        }
        return list;
    }

    public static List<RouteSegment> readSegments(ListTag list) {
        List<RouteSegment> segments = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            read(list.getCompound(i)).ifPresent(segments::add);
        }
        return segments;
    }

    private static ListTag writePoints(List<RoutePoint> points) {
        ListTag list = new ListTag();
        for (RoutePoint point : points) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("x", point.position().x);
            tag.putDouble("y", point.position().y);
            tag.putDouble("z", point.position().z);
            point.yaw().ifPresent(yaw -> tag.putFloat("yaw", yaw));
            point.rotation().ifPresent(rotation -> {
                tag.putDouble("rotX", rotation.x());
                tag.putDouble("rotY", rotation.y());
                tag.putDouble("rotZ", rotation.z());
                tag.putDouble("rotW", rotation.w());
            });
            tag.putLong("tickOffset", point.tickOffset());
            tag.putString(DIMENSION, point.dimension().location().toString());
            list.add(tag);
        }
        return list;
    }

    private static List<RoutePoint> readPoints(ListTag list, ResourceKey<Level> fallbackDimension) {
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceKey<Level> dimension = tag.contains(DIMENSION, Tag.TAG_STRING)
                    ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(DIMENSION)))
                    : fallbackDimension;
            Optional<Float> yaw = tag.contains("yaw", Tag.TAG_ANY_NUMERIC)
                    ? Optional.of(tag.getFloat("yaw"))
                    : Optional.empty();
            Optional<RouteRotation> rotation = tag.contains("rotW", Tag.TAG_ANY_NUMERIC)
                    ? Optional.of(new RouteRotation(
                            tag.getDouble("rotX"),
                            tag.getDouble("rotY"),
                            tag.getDouble("rotZ"),
                            tag.getDouble("rotW")
                    ))
                    : Optional.empty();
            points.add(new RoutePoint(
                    new net.minecraft.world.phys.Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                    yaw,
                    rotation,
                    tag.getLong("tickOffset"),
                    dimension
            ));
        }
        return points;
    }

    private static CompoundTag writeController(VehicleControllerRef controller) {
        CompoundTag tag = new CompoundTag();
        tag.putString(CONTROLLER_TYPE, controller.controllerType().toString());
        controller.vehicleId().ifPresent(id -> tag.putUUID(VEHICLE_ID, id));
        controller.controllerPos().ifPresent(pos -> {
            tag.putInt(CONTROLLER_X, pos.getX());
            tag.putInt(CONTROLLER_Y, pos.getY());
            tag.putInt(CONTROLLER_Z, pos.getZ());
        });
        return tag;
    }

    private static VehicleControllerRef readController(CompoundTag tag, ResourceKey<Level> dimension) {
        ResourceLocation controllerType = ResourceLocation.parse(tag.getString(CONTROLLER_TYPE));
        Optional<UUID> vehicleId = tag.hasUUID(VEHICLE_ID) ? Optional.of(tag.getUUID(VEHICLE_ID)) : Optional.empty();
        Optional<BlockPos> controllerPos = tag.contains(CONTROLLER_X, Tag.TAG_ANY_NUMERIC)
                && tag.contains(CONTROLLER_Y, Tag.TAG_ANY_NUMERIC)
                && tag.contains(CONTROLLER_Z, Tag.TAG_ANY_NUMERIC)
                ? Optional.of(new BlockPos(tag.getInt(CONTROLLER_X), tag.getInt(CONTROLLER_Y), tag.getInt(CONTROLLER_Z)))
                : Optional.empty();
        return new VehicleControllerRef(controllerType, dimension, vehicleId, controllerPos);
    }
}

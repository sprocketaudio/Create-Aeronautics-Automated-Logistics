package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;

public class VehicleRouteRecordingService implements RouteRecordingService {
    private static final double ROTATION_SAMPLE_THRESHOLD_DEGREES = 2.0D;
    private static final int STATIONARY_SAMPLE_INTERVAL_TICKS = 40;

    private final Map<RouteId, ActiveRecording> activeRecordings = new HashMap<>();

    public void resetRuntime() {
        activeRecordings.clear();
    }

    @Override
    public RouteOperationResult<RecordingSession> startRecording(ServerPlayer player, BlockPos stationPos, VehicleController controller) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(controller, "controller");

        ServerLevel level = player.serverLevel();
        Optional<AirshipStationBlockEntity> station = stationAt(level, stationPos);
        if (station.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.STATION_MISSING);
        }
        if (station.get().isRecording()) {
            return RouteOperationResult.failure(RecordingFailure.STATION_BUSY);
        }
        if (!controller.dimension().equals(level.dimension())) {
            return RouteOperationResult.failure(RecordingFailure.DIMENSION_MISMATCH);
        }
        if (!controller.isAssembled() || !controller.isAutomationCapable()) {
            return RouteOperationResult.failure(RecordingFailure.VEHICLE_MISSING);
        }
        if (!controller.isLoaded(level)) {
            return RouteOperationResult.failure(RecordingFailure.VEHICLE_UNLOADED);
        }
        if (!controller.isControlledByPlayer(player.getUUID())) {
            return RouteOperationResult.failure(RecordingFailure.VEHICLE_NOT_CONTROLLED);
        }

        RecordingSession session = RecordingSession.create(player, stationPos, controller.ref());
        ActiveRecording activeRecording = new ActiveRecording(session, level.dimension(), controller);
        activeRecordings.put(session.routeId(), activeRecording);
        station.get().startRecording(session);
        sample(
                level,
                station.get(),
                activeRecording,
                controller.recordingPosition(player),
                controller.yaw(),
                controller.routeRotation(),
                level.getGameTime(),
                true
        );
        return RouteOperationResult.success(session);
    }

    @Override
    public RouteOperationResult<Route> stopRecording(ServerPlayer player, RouteId routeId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(routeId, "routeId");

        ActiveRecording activeRecording = activeRecordings.remove(routeId);
        if (activeRecording == null || !activeRecording.session().playerId().equals(player.getUUID())) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }

        ServerLevel level = player.serverLevel();
        Optional<AirshipStationBlockEntity> station = stationAt(level, activeRecording.session().stationPos());
        if (station.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.STATION_MISSING);
        }

        sample(
                level,
                station.get(),
                activeRecording,
                activeRecording.controller().recordingPosition(player),
                activeRecording.controller().yaw(),
                activeRecording.controller().routeRotation(),
                level.getGameTime(),
                true
        );

        if (activeRecording.points().size() < 2) {
            station.get().failRecording(RecordingFailure.ROUTE_TOO_SHORT.failureReason());
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }
        if (!hasMeaningfulTravel(activeRecording.points())) {
            station.get().failRecording(RecordingFailure.ROUTE_TOO_SHORT.failureReason());
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }

        Route route = new Route(
                activeRecording.session().routeId(),
                "Recorded Route " + activeRecording.session().routeId().value().toString().substring(0, 8),
                activeRecording.dimension(),
                activeRecording.points(),
                activeRecording.session().controllerRef(),
                AutomatedLogisticsConfig.PLAYBACK_MODE.get(),
                RouteStatus.RECORDED,
                activeRecording.stops(),
                Optional.of(activeRecording.session().playerId())
        );
        station.get().finishRecording(route);
        return RouteOperationResult.success(route);
    }

    @Override
    public RouteOperationResult<RouteSegment> finishSegmentRecording(ServerPlayer player, BlockPos endStationPos) {
        return finishSegmentRecording(player, endStationPos, true);
    }

    @Override
    public RouteOperationResult<RouteSegment> finishSegmentRecording(ServerPlayer player, BlockPos endStationPos, boolean continueRecording) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(endStationPos, "endStationPos");

        Optional<ActiveRecording> activeRecording = activeRecordingFor(player.getUUID());
        if (activeRecording.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }

        ServerLevel level = player.serverLevel();
        if (!activeRecording.get().dimension().equals(level.dimension())) {
            return RouteOperationResult.failure(RecordingFailure.DIMENSION_MISMATCH);
        }

        Optional<AirshipStationBlockEntity> startStation = stationAt(level, activeRecording.get().session().stationPos());
        Optional<AirshipStationBlockEntity> endStation = stationAt(level, endStationPos);
        if (startStation.isEmpty() || endStation.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.STATION_MISSING);
        }
        Optional<UUID> selectedTransponderId = startStation.get().selectedTransponderId();
        if (selectedTransponderId.isEmpty()) {
            startStation.get().failRecording(RecordingFailure.VEHICLE_MISSING.failureReason());
            return RouteOperationResult.failure(RecordingFailure.VEHICLE_MISSING);
        }

        ActiveRecording recording = activeRecording.get();
        RecordingFailure sampleFailure = sample(
                level,
                startStation.get(),
                recording,
                recording.controller().recordingPosition(player),
                recording.controller().yaw(),
                recording.controller().routeRotation(),
                level.getGameTime(),
                true
        );
        if (sampleFailure != null) {
            activeRecordings.remove(recording.session().routeId());
            startStation.get().failRecording(sampleFailure.failureReason());
            return RouteOperationResult.failure(sampleFailure);
        }

        if (recording.points().size() < 2) {
            activeRecordings.remove(recording.session().routeId());
            startStation.get().failRecording(RecordingFailure.ROUTE_TOO_SHORT.failureReason());
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }
        if (!hasMeaningfulTravel(recording.points())) {
            activeRecordings.remove(recording.session().routeId());
            startStation.get().failRecording(RecordingFailure.ROUTE_TOO_SHORT.failureReason());
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }

        Optional<ShipTransponderSnapshot> ship = ShipTransponderRegistry.snapshot(selectedTransponderId.get());
        String shipName = ship.map(ShipTransponderSnapshot::shipName).orElseGet(startStation.get()::selectedShipName);
        Optional<UUID> runtimeShipId = ship.flatMap(ShipTransponderSnapshot::runtimeShipId)
                .or(() -> recording.controller().ref().vehicleId());

        Route route = new Route(
                recording.session().routeId(),
                startStation.get().stationName() + " -> " + endStation.get().stationName(),
                recording.dimension(),
                recording.points(),
                recording.controller().ref(),
                AutomatedLogisticsConfig.PLAYBACK_MODE.get(),
                RouteStatus.RECORDED,
                recording.stops(),
                Optional.of(recording.session().playerId())
        );
        RouteSegment segment = new RouteSegment(
                RouteSegmentId.create(),
                startStation.get().stationId(),
                startStation.get().stationName(),
                endStation.get().stationId(),
                endStation.get().stationName(),
                selectedTransponderId.get(),
                shipName,
                runtimeShipId,
                recording.dimension(),
                recording.points(),
                recording.controller().ref(),
                level.getGameTime(),
                System.currentTimeMillis(),
                Optional.of(recording.session().playerId())
        );

        startStation.get().finishRecording(route);
        startStation.get().addRouteSegment(segment);
        if (!startStation.get().stationId().equals(endStation.get().stationId())) {
            endStation.get().addRouteSegment(segment);
        }
        if (continueRecording) {
            continueRecordingFromStation(player, level, recording, endStation.get(), ship);
        } else {
            activeRecordings.remove(recording.session().routeId());
        }
        return RouteOperationResult.success(segment);
    }

    @Override
    public RouteOperationResult<Boolean> cancelRecording(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        Optional<ActiveRecording> activeRecording = activeRecordingFor(player.getUUID());
        if (activeRecording.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }

        ActiveRecording recording = activeRecording.get();
        activeRecordings.remove(recording.session().routeId());
        ServerLevel level = player.serverLevel();
        if (!recording.dimension().equals(level.dimension())) {
            return RouteOperationResult.failure(RecordingFailure.DIMENSION_MISMATCH);
        }
        stationAt(level, recording.session().stationPos()).ifPresent(AirshipStationBlockEntity::cancelRecording);
        return RouteOperationResult.success(Boolean.TRUE);
    }

    public boolean cancelRecordingForController(ServerLevel level, net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef controllerRef) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controllerRef, "controllerRef");

        boolean cancelled = false;
        Iterator<Map.Entry<RouteId, ActiveRecording>> iterator = activeRecordings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RouteId, ActiveRecording> entry = iterator.next();
            ActiveRecording recording = entry.getValue();
            if (!recording.dimension().equals(level.dimension())
                    || !recording.session().controllerRef().matches(controllerRef)) {
                continue;
            }
            iterator.remove();
            stationAt(level, recording.session().stationPos()).ifPresent(AirshipStationBlockEntity::cancelRecording);
            cancelled = true;
        }
        return cancelled;
    }

    private void continueRecordingFromStation(
            ServerPlayer player,
            ServerLevel level,
            ActiveRecording previousRecording,
            AirshipStationBlockEntity nextStartStation,
            Optional<ShipTransponderSnapshot> ship
    ) {
        activeRecordings.remove(previousRecording.session().routeId());
        RecordingSession nextSession = new RecordingSession(
                RouteId.create(),
                previousRecording.session().playerId(),
                nextStartStation.getBlockPos().immutable(),
                previousRecording.session().controllerRef(),
                level.getGameTime()
        );
        ActiveRecording nextRecording = new ActiveRecording(nextSession, previousRecording.dimension(), previousRecording.controller());
        activeRecordings.put(nextSession.routeId(), nextRecording);
        ship.ifPresent(nextStartStation::selectShip);
        nextStartStation.startRecording(nextSession);
        sample(
                level,
                nextStartStation,
                nextRecording,
                previousRecording.controller().recordingPosition(player),
                previousRecording.controller().yaw(),
                previousRecording.controller().routeRotation(),
                level.getGameTime(),
                true
        );
    }

    @Override
    public RouteOperationResult<RouteStop> markStop(ServerPlayer player, RouteId routeId, WaitCondition waitCondition) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(waitCondition, "waitCondition");

        ActiveRecording activeRecording = activeRecordings.get(routeId);
        if (activeRecording == null || !activeRecording.session().playerId().equals(player.getUUID())) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }

        ServerLevel level = player.serverLevel();
        Optional<AirshipStationBlockEntity> station = stationAt(level, activeRecording.session().stationPos());
        if (station.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.STATION_MISSING);
        }

        RecordingFailure failure = sample(
                level,
                station.get(),
                activeRecording,
                activeRecording.controller().recordingPosition(player),
                activeRecording.controller().yaw(),
                activeRecording.controller().routeRotation(),
                level.getGameTime(),
                true
        );
        if (failure != null) {
            return RouteOperationResult.failure(failure);
        }

        int pointIndex = activeRecording.points().size() - 1;
        RouteStop stop = RouteStop.create("Stop " + (activeRecording.stops().size() + 1), pointIndex, waitCondition);
        activeRecording.stops().add(stop);
        station.get().addRecordingStop(stop);
        return RouteOperationResult.success(stop);
    }

    @Override
    public RouteOperationResult<RouteStop> markStop(ServerPlayer player, WaitCondition waitCondition) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(waitCondition, "waitCondition");

        Optional<ActiveRecording> activeRecording = activeRecordingFor(player.getUUID());
        return activeRecording
                .map(recording -> markStop(player, recording.session().routeId(), waitCondition))
                .orElseGet(() -> RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE));
    }

    @Override
    public RouteOperationResult<RouteStop> updateLastStopWait(ServerPlayer player, RouteId routeId, WaitCondition waitCondition) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(waitCondition, "waitCondition");

        ActiveRecording activeRecording = activeRecordings.get(routeId);
        if (activeRecording == null || !activeRecording.session().playerId().equals(player.getUUID())) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }
        if (activeRecording.stops().isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }

        ServerLevel level = player.serverLevel();
        Optional<AirshipStationBlockEntity> station = stationAt(level, activeRecording.session().stationPos());
        if (station.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.STATION_MISSING);
        }

        RouteStop updated = activeRecording.stops().getLast().withWaitCondition(waitCondition);
        activeRecording.stops().set(activeRecording.stops().size() - 1, updated);
        station.get().replaceLastRecordingStop(updated);
        return RouteOperationResult.success(updated);
    }

    @Override
    public RouteOperationResult<RouteStop> updateLastStopWait(ServerPlayer player, WaitCondition waitCondition) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(waitCondition, "waitCondition");

        Optional<ActiveRecording> activeRecording = activeRecordingFor(player.getUUID());
        return activeRecording
                .map(recording -> updateLastStopWait(player, recording.session().routeId(), waitCondition))
                .orElseGet(() -> RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE));
    }

    @Override
    public RouteOperationResult<RouteStop> cycleLastStopWait(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        Optional<ActiveRecording> activeRecording = activeRecordingFor(player.getUUID());
        if (activeRecording.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }
        if (activeRecording.get().stops().isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }

        WaitCondition current = activeRecording.get().stops().getLast().waitCondition();
        WaitCondition next = current.type() == WaitConditionType.NONE
                ? WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                : WaitCondition.none();
        return updateLastStopWait(player, activeRecording.get().session().routeId(), next);
    }

    @Override
    public RouteOperationResult<RouteStop> lastStop(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        Optional<ActiveRecording> activeRecording = activeRecordingFor(player.getUUID());
        if (activeRecording.isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.UNKNOWN_ROUTE);
        }
        if (activeRecording.get().stops().isEmpty()) {
            return RouteOperationResult.failure(RecordingFailure.ROUTE_TOO_SHORT);
        }
        return RouteOperationResult.success(activeRecording.get().stops().getLast());
    }

    @Override
    public boolean hasActiveRecording(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return activeRecordingFor(player.getUUID()).isPresent();
    }

    @Override
    public Optional<RecordingSession> activeRecordingForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return activeRecordingFor(playerId).map(ActiveRecording::session);
    }

    @Override
    public void tickRecording(ServerPlayer player, RouteId routeId) {
        ActiveRecording activeRecording = activeRecordings.get(routeId);
        if (activeRecording == null || !activeRecording.session().playerId().equals(player.getUUID())) {
            return;
        }

        tickOne(player.serverLevel(), activeRecording);
    }

    private boolean hasMeaningfulTravel(List<net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint> points) {
        if (points.size() < 2) {
            return false;
        }
        double threshold = Math.max(0.5D, AutomatedLogisticsConfig.MIN_DISTANCE_BETWEEN_POINTS.get());
        Vec3 origin = points.getFirst().position();
        double maxDisplacement = 0.0D;
        double totalTravel = 0.0D;
        Vec3 previous = origin;
        for (int i = 1; i < points.size(); i++) {
            Vec3 current = points.get(i).position();
            maxDisplacement = Math.max(maxDisplacement, current.distanceTo(origin));
            totalTravel += current.distanceTo(previous);
            previous = current;
        }
        return maxDisplacement >= threshold || totalTravel >= threshold;
    }

    public void tickAll(MinecraftServer server) {
        Iterator<ActiveRecording> iterator = activeRecordings.values().iterator();
        while (iterator.hasNext()) {
            ActiveRecording activeRecording = iterator.next();
            ServerLevel level = server.getLevel(activeRecording.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            RecordingFailure failure = tickOne(level, activeRecording);
            if (failure != null) {
                stationAt(level, activeRecording.session().stationPos())
                        .ifPresent(station -> station.failRecording(failure.failureReason()));
                iterator.remove();
            }
        }
    }

    private RecordingFailure tickOne(ServerLevel level, ActiveRecording activeRecording) {
        long gameTime = level.getGameTime();
        if (gameTime - activeRecording.lastSampleGameTime() < AutomatedLogisticsConfig.SAMPLE_INTERVAL_TICKS.get()) {
            return null;
        }

        Optional<AirshipStationBlockEntity> station = stationAt(level, activeRecording.session().stationPos());
        if (station.isEmpty()) {
            return RecordingFailure.STATION_MISSING;
        }

        VehicleController controller = activeRecording.controller();
        if (!controller.isLoaded(level)) {
            return RecordingFailure.VEHICLE_UNLOADED;
        }
        if (!controller.isAssembled() || !controller.isAutomationCapable()) {
            return RecordingFailure.VEHICLE_MISSING;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(activeRecording.session().playerId());
        if (player == null || !controller.isControlledByPlayer(player.getUUID())) {
            return RecordingFailure.VEHICLE_NOT_CONTROLLED;
        }

        return sample(
                level,
                station.get(),
                activeRecording,
                controller.recordingPosition(player),
                controller.yaw(),
                controller.routeRotation(),
                gameTime,
                false
        );
    }

    private RecordingFailure sample(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActiveRecording activeRecording,
            Vec3 position,
            Optional<Float> yaw,
            Optional<RouteRotation> rotation,
            long gameTime,
            boolean force
    ) {
        activeRecording.lastSampleGameTime(gameTime);
        long tickOffset = gameTime - activeRecording.session().startedAtGameTime();
        if (!activeRecording.points().isEmpty()) {
            RoutePoint previousPoint = activeRecording.points().getLast();
            Vec3 previous = previousPoint.position();
            double minDistance = AutomatedLogisticsConfig.MIN_DISTANCE_BETWEEN_POINTS.get();
            boolean movedEnough = previous.distanceToSqr(position) >= minDistance * minDistance;
            boolean rotatedEnough = rotationChangedEnough(previousPoint, yaw, rotation);
            boolean stationarySampleDue = activeRecording.hasMoved()
                    && tickOffset - previousPoint.tickOffset() >= STATIONARY_SAMPLE_INTERVAL_TICKS;
            if (!force && !movedEnough && !rotatedEnough && !stationarySampleDue) {
                return null;
            }
            if (movedEnough) {
                activeRecording.hasMoved(true);
            }
        }

        if (activeRecording.points().size() >= AutomatedLogisticsConfig.MAX_ROUTE_POINTS.get()) {
            station.failRecording(RecordingFailure.MAX_ROUTE_POINTS_REACHED.failureReason());
            return RecordingFailure.MAX_ROUTE_POINTS_REACHED;
        }

        RoutePoint point = new RoutePoint(
                position,
                yaw,
                rotation,
                tickOffset,
                level.dimension()
        );
        activeRecording.points().add(point);
        station.addRecordedPoint(point);
        return null;
    }

    private boolean rotationChangedEnough(
            RoutePoint previousPoint,
            Optional<Float> yaw,
            Optional<RouteRotation> rotation
    ) {
        if (previousPoint.rotation().isPresent() && rotation.isPresent()) {
            return angularDifferenceDegrees(previousPoint.rotation().get(), rotation.get()) >= ROTATION_SAMPLE_THRESHOLD_DEGREES;
        }
        if (previousPoint.yaw().isPresent() && yaw.isPresent()) {
            return wrappedYawDifferenceDegrees(previousPoint.yaw().get(), yaw.get()) >= ROTATION_SAMPLE_THRESHOLD_DEGREES;
        }
        return false;
    }

    private double angularDifferenceDegrees(RouteRotation current, RouteRotation target) {
        double dot = Math.abs(
                current.x() * target.x()
                        + current.y() * target.y()
                        + current.z() * target.z()
                        + current.w() * target.w()
        );
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        return Math.toDegrees(2.0D * Math.acos(dot));
    }

    private double wrappedYawDifferenceDegrees(float first, float second) {
        double difference = Math.abs(first - second) % 360.0D;
        return difference > 180.0D ? 360.0D - difference : difference;
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos stationPos) {
        if (level.getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    private Optional<ActiveRecording> activeRecordingFor(UUID playerId) {
        return activeRecordings.values().stream()
                .filter(activeRecording -> activeRecording.session().playerId().equals(playerId))
                .findFirst();
    }

    private static final class ActiveRecording {
        private final RecordingSession session;
        private final ResourceKey<Level> dimension;
        private final VehicleController controller;
        private final List<RoutePoint> points = new ArrayList<>();
        private final List<RouteStop> stops = new ArrayList<>();
        private long lastSampleGameTime;
        private boolean hasMoved;

        private ActiveRecording(RecordingSession session, ResourceKey<Level> dimension, VehicleController controller) {
            this.session = session;
            this.dimension = dimension;
            this.controller = controller;
            this.lastSampleGameTime = session.startedAtGameTime() - AutomatedLogisticsConfig.SAMPLE_INTERVAL_TICKS.get();
        }

        private RecordingSession session() {
            return session;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private VehicleController controller() {
            return controller;
        }

        private List<RoutePoint> points() {
            return points;
        }

        private List<RouteStop> stops() {
            return stops;
        }

        private long lastSampleGameTime() {
            return lastSampleGameTime;
        }

        private void lastSampleGameTime(long lastSampleGameTime) {
            this.lastSampleGameTime = lastSampleGameTime;
        }

        private boolean hasMoved() {
            return hasMoved;
        }

        private void hasMoved(boolean hasMoved) {
            this.hasMoved = hasMoved;
        }
    }
}

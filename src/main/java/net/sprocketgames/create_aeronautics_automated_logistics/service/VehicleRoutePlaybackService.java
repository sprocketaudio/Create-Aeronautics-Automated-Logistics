package net.sprocketgames.create_aeronautics_automated_logistics.service;

import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition.Ops;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetAutomatedShipVisualStatePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockTransferSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingRuntime;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleMotionResult;

public class VehicleRoutePlaybackService implements RoutePlaybackService {
    private static final double ARRIVAL_DISTANCE = 0.5D;
    private static final double ENDPOINT_ARRIVAL_DISTANCE = 0.2D;
    private static final double ENDPOINT_SETTLE_DISTANCE = 0.65D;
    private static final double ARRIVAL_ROTATION_TOLERANCE_DEGREES = 4.0D;
    private static final double MIN_PROGRESS_PER_TICK = 0.001D;
    private static final double MIN_REPLAY_SPEED = 0.08D;
    private static final double MAX_REPLAY_SPEED = 3.0D;
    private static final int JOIN_BLEND_SEGMENTS = 5;
    private static final int ENDPOINT_SETTLE_TICKS = 40;
    private static final int MAX_STALLED_TICKS = 200;
    private static final int INITIAL_STALL_GRACE_TICKS = 60;
    private static final int RESTORE_CATCH_TICKS = 200;
    private static final double STATIONARY_SEGMENT_DISTANCE = 0.1D;
    private static final String ACTIVE_PLAYBACKS = "activePlaybacks";
    private static final String ROUTE = "route";
    private static final String STATION_POS = "stationPos";
    private static final String JOIN_OFFSET = "joinOffset";
    private static final String JOIN_START_ROTATION = "joinStartRotation";
    private static final String TARGET_INDEX = "targetIndex";
    private static final String DIRECTION = "direction";
    private static final String SEGMENT_DURATION_TICKS = "segmentDurationTicks";
    private static final String SEGMENT_ELAPSED_TICKS = "segmentElapsedTicks";
    private static final String PREVIOUS_DISTANCE = "previousDistance";
    private static final String STALLED_TICKS = "stalledTicks";
    private static final String PLAYBACK_TICKS = "playbackTicks";
    private static final String INITIAL_JOIN_SEGMENTS_ADVANCED = "initialJoinSegmentsAdvanced";
    private static final String ENDPOINT_SETTLE_TICKS_TAG = "endpointSettleTicks";
    private static final String WAITING_STOP_ID = "waitingStopId";
    private static final String ACTIVE_DOCK_STATION_POS = "activeDockStationPos";
    private static final String WAIT_TICKS_REMAINING = "waitTicksRemaining";
    private static final String IDLE_WINDOW_TICKS = "idleWindowTicks";
    private static final String DOCK_TIMEOUT_TICKS_REMAINING = "dockTimeoutTicksRemaining";
    private static final String DOCK_IDLE_TIMEOUT_TICKS_REMAINING = "dockIdleTimeoutTicksRemaining";
    private static final String DOCK_CARGO_TIMEOUT_TICKS_REMAINING = "dockCargoTimeoutTicksRemaining";
    private static final String DOCK_LOCKED = "dockLocked";
    private static final String RESTORE_CATCH_TICKS_REMAINING = "restoreCatchTicksRemaining";
    private static final String COMPLETED = "completed";

    private final Map<RouteId, ActivePlayback> activePlaybacks = new HashMap<>();
    private final Map<RouteId, CompoundTag> pendingRuntimePlaybacks = new HashMap<>();
    private final Map<RouteId, Integer> pendingRuntimeRestoreCooldowns = new HashMap<>();
    private final Set<UUID> activeVisualShipIds = new HashSet<>();

    public void resetRuntime() {
        activePlaybacks.clear();
        pendingRuntimePlaybacks.clear();
        pendingRuntimeRestoreCooldowns.clear();
        activeVisualShipIds.clear();
    }

    public CompoundTag saveRuntime() {
        CompoundTag tag = new CompoundTag();
        ListTag playbacks = new ListTag();
        for (ActivePlayback activePlayback : activePlaybacks.values()) {
            playbacks.add(writeActivePlayback(activePlayback));
        }
        for (CompoundTag pendingTag : pendingRuntimePlaybacks.values()) {
            playbacks.add(pendingTag.copy());
        }
        tag.put(ACTIVE_PLAYBACKS, playbacks);
        return tag;
    }

    public void loadRuntime(MinecraftServer server, CompoundTag tag) {
        resetRuntime();
        if (tag == null || !tag.contains(ACTIVE_PLAYBACKS, Tag.TAG_LIST)) {
            CreateAeronauticsAutomatedLogistics.debugLog("Loaded route playback runtime: no saved active playbacks");
            return;
        }

        ListTag playbacks = tag.getList(ACTIVE_PLAYBACKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < playbacks.size(); i++) {
            CompoundTag playbackTag = playbacks.getCompound(i);
            routeIdFromRuntimeTag(playbackTag)
                    .ifPresent(routeId -> {
                        pendingRuntimePlaybacks.put(routeId, playbackTag.copy());
                        pendingRuntimeRestoreCooldowns.put(routeId, 0);
                    });
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Loaded route playback runtime: {} pending active playback(s)",
                pendingRuntimePlaybacks.size()
        );
        restorePendingRuntime(server);
    }

    @Override
    public PlaybackOperationResult<RouteId> startPlayback(ServerLevel level, BlockPos stationPos, Route route) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(route, "route");

        if (activePlaybacks.containsKey(route.id())) {
            return PlaybackOperationResult.failure(PlaybackFailure.ALREADY_RUNNING);
        }
        if (!route.dimension().equals(level.dimension())) {
            return PlaybackOperationResult.failure(PlaybackFailure.DIMENSION_MISMATCH);
        }
        if (route.points().size() < 2) {
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        if (route.points().stream().anyMatch(point -> !point.dimension().equals(route.dimension()))) {
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        Optional<AirshipStationBlockEntity> station = stationAt(level, stationPos);
        if (station.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.STATION_MISSING);
        }

        Optional<VehicleController> controller = VehicleControllerResolver.resolve(level, route.linkedController());
        if (controller.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.VEHICLE_MISSING);
        }
        if (!controller.get().isAutomationCapable()) {
            return PlaybackOperationResult.failure(PlaybackFailure.MISSING_CONTROLLER);
        }
        if (ActivePlayback.nearestEndpointDistance(route, controller.get()) > AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get()) {
            return PlaybackOperationResult.failure(PlaybackFailure.START_TOO_FAR_FROM_ROUTE);
        }

        ActivePlayback activePlayback = ActivePlayback.create(route, stationPos, controller.get());
        activePlaybacks.put(route.id(), activePlayback);
        station.get().startPlayback(route);
        setVisualsActive(level, activePlayback, true);
        return PlaybackOperationResult.success(route.id());
    }

    @Override
    public void stopPlayback(ServerLevel level, RouteId routeId, FailureReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(reason, "reason");

        pendingRuntimePlaybacks.remove(routeId);
        pendingRuntimeRestoreCooldowns.remove(routeId);
        ActivePlayback activePlayback = activePlaybacks.remove(routeId);
        if (activePlayback == null) {
            return;
        }

        setVisualsActive(level, activePlayback, false);
        activePlayback.controller().stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            if (reason == FailureReason.NONE) {
                station.stopPlayback();
            } else {
                station.failPlayback(reason);
            }
        });
    }

    @Override
    public void tickPlayback(ServerLevel level) {
        Iterator<ActivePlayback> iterator = activePlaybacks.values().iterator();
        while (iterator.hasNext()) {
            ActivePlayback activePlayback = iterator.next();
            if (!activePlayback.route().dimension().equals(level.dimension())) {
                continue;
            }

            PlaybackFailure failure = tickOne(level, activePlayback);
            if (failure != null) {
                fail(level, activePlayback, failure);
                iterator.remove();
            } else if (activePlayback.completed()) {
                iterator.remove();
            }
        }
    }

    public void tickAll(MinecraftServer server) {
        restorePendingRuntime(server);
        for (ServerLevel level : server.getAllLevels()) {
            tickPlayback(level);
        }
    }

    public boolean isRunning(RouteId routeId) {
        return activePlaybacks.containsKey(routeId);
    }

    public boolean isPending(RouteId routeId) {
        return pendingRuntimePlaybacks.containsKey(routeId);
    }

    public Set<UUID> activeVisualShipIds() {
        return Set.copyOf(activeVisualShipIds);
    }

    private PlaybackFailure tickOne(ServerLevel level, ActivePlayback activePlayback) {
        Optional<AirshipStationBlockEntity> station = stationAt(level, activePlayback.stationPos());
        if (station.isEmpty() && !activePlayback.isRestoring()) {
            return PlaybackFailure.STATION_MISSING;
        }

        VehicleController controller = activePlayback.controller();
        if (!controller.isLoaded(level)) {
            if (activePlayback.tickRestoreGrace()) {
                return null;
            }
            return PlaybackFailure.VEHICLE_UNLOADED;
        }
        if (!controller.isAssembled()) {
            if (activePlayback.tickRestoreGrace()) {
                return null;
            }
            return PlaybackFailure.VEHICLE_MISSING;
        }
        if (activePlayback.isRestoring()) {
            Vec3 catchPosition = activePlayback.guidancePosition();
            VehicleMotionResult motionResult = controller.moveToward(
                    level,
                    catchPosition,
                    activePlayback.guidanceRotation(),
                    AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                    0.0D
            );
            activePlayback.tickRestoreGrace();
            if (motionResult.successful()) {
                activePlayback.resetProgress(controller.position().distanceTo(catchPosition));
            }
            return null;
        }
        if (station.isEmpty()) {
            return PlaybackFailure.STATION_MISSING;
        }
        if (activePlayback.isWaiting()) {
            return tickWaiting(level, station.get(), activePlayback, controller);
        }

        if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get()
                && controller.collisionEntity()
                .map(entity -> entity.horizontalCollision || (entity.verticalCollision && !entity.verticalCollisionBelow))
                .orElse(false)) {
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }

        RoutePoint target = activePlayback.targetPoint();
        Vec3 targetPosition = activePlayback.targetPosition();
        Vec3 guidancePosition = activePlayback.guidancePosition();
        Optional<RouteRotation> targetRotation = activePlayback.targetRotation();
        Optional<RouteRotation> guidanceRotation = activePlayback.guidanceRotation();
        double distanceToTarget = controller.position().distanceTo(targetPosition);
        double arrivalDistance = activePlayback.arrivalDistance();
        boolean rotationAligned = activePlayback.isRotationAligned(targetRotation, controller);
        if (activePlayback.shouldHoldAtTarget(distanceToTarget)) {
            controller.moveToward(
                    level,
                    targetPosition,
                    targetRotation,
                    AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                    0.0D
            );
            activePlayback.tickSegment();
            activePlayback.resetProgress(distanceToTarget);
            return null;
        }
        if (distanceToTarget <= arrivalDistance && (!activePlayback.requiresRotationAlignmentForArrival() || rotationAligned)) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Playback {} reached point {} at distance {} position={} target={}",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex(),
                    distanceToTarget,
                    controller.position(),
                    targetPosition
            );
            if (activePlayback.beginWaitAtCurrentTarget()) {
                return beginWaitAtTarget(level, station.get(), activePlayback, controller, targetPosition, distanceToTarget);
            }
            if (activePlayback.isComplete()) {
                complete(level, activePlayback);
                return null;
            }
            activePlayback.advanceTarget();
            target = activePlayback.targetPoint();
            targetPosition = activePlayback.targetPosition();
            guidancePosition = activePlayback.guidancePosition();
            targetRotation = activePlayback.targetRotation();
            guidanceRotation = activePlayback.guidanceRotation();
            distanceToTarget = controller.position().distanceTo(targetPosition);
            arrivalDistance = activePlayback.arrivalDistance();
            activePlayback.resetProgress(distanceToTarget);
        } else if (activePlayback.shouldSettleEndpoint(distanceToTarget, rotationAligned)) {
            if (activePlayback.tickEndpointSettle()) {
                CreateAeronauticsAutomatedLogistics.debugLog(
                        "Playback {} accepted endpoint {} after {} settle ticks at distance {} position={} target={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        activePlayback.endpointSettleTicks(),
                        distanceToTarget,
                        controller.position(),
                        targetPosition
                );
                if (activePlayback.beginWaitAtCurrentTarget()) {
                    return beginWaitAtTarget(level, station.get(), activePlayback, controller, targetPosition, distanceToTarget);
                }
                if (activePlayback.isComplete()) {
                    complete(level, activePlayback);
                    return null;
                }
                activePlayback.advanceTarget();
                target = activePlayback.targetPoint();
                targetPosition = activePlayback.targetPosition();
                guidancePosition = activePlayback.guidancePosition();
                targetRotation = activePlayback.targetRotation();
                guidanceRotation = activePlayback.guidanceRotation();
                distanceToTarget = controller.position().distanceTo(targetPosition);
                arrivalDistance = activePlayback.arrivalDistance();
                activePlayback.resetProgress(distanceToTarget);
            }
        } else {
            activePlayback.resetEndpointSettle();
            if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get() && activePlayback.isStalled(distanceToTarget)) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Playback {} stalled toward point {}. distance={}, previousDistance={}, stalledTicks={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        distanceToTarget,
                        activePlayback.previousDistance(),
                        activePlayback.stalledTicks()
                );
                return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
            }
        }

        Vec3 collisionTargetPosition = guidancePosition;
        if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get()
                && controller.collisionEntity().map(entity -> willCollide(level, entity, collisionTargetPosition)).orElse(false)) {
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }

        double targetSpeed = activePlayback.targetSpeedBlocksPerTick();
        if (activePlayback.shouldLogProgress()) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Playback {} tick {} point {} distance={} targetSpeed={} position={} target={}",
                    activePlayback.route().id().value(),
                    activePlayback.playbackTicks(),
                    activePlayback.targetIndex(),
                    distanceToTarget,
                    targetSpeed,
                    controller.position(),
                    targetPosition
            );
        }

        VehicleMotionResult motionResult = controller.moveToward(
                level,
                guidancePosition,
                guidanceRotation,
                AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                targetSpeed
        );
        activePlayback.tickSegment();
        return motionResult.failureReason()
                .map(this::toPlaybackFailure)
                .orElse(null);
    }

    private PlaybackFailure tickWaiting(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        Vec3 targetPosition = activePlayback.targetPosition();
        Optional<RouteRotation> targetRotation = activePlayback.pointRotation(activePlayback.targetIndex());

        if (activePlayback.isDockingWait()) {
            Optional<AirshipStationBlockEntity> dockingStation = dockingStation(level, station, activePlayback);
            if (dockingStation.isEmpty()) {
                return PlaybackFailure.STATION_MISSING;
            }
            DockingRuntime.DockingWaitResult docking = DockingRuntime.tickDockingWait(level, dockingStation.get(), activePlayback.route());
            if (docking.failure().isPresent()) {
                return docking.failure().get();
            }
            if (docking.locked() && !activePlayback.dockLocked()) {
                activePlayback.dockLocked(true);
                if (activePlayback.isIdleWait()) {
                    DockingRuntime.transferSnapshot(level, dockingStation.get(), activePlayback.route())
                            .ifPresent(activePlayback::dockTransferSnapshot);
                    CreateAeronauticsAutomatedLogistics.debugLog(
                            "Playback {} docked at stop {} and will wait for {} idle ticks",
                            activePlayback.route().id().value(),
                            activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                            activePlayback.waitTicksRemaining()
                    );
                } else if (activePlayback.isCargoThresholdWait()) {
                    CreateAeronauticsAutomatedLogistics.debugLog(
                            "Playback {} docked at stop {} and will wait for cargo threshold {}",
                            activePlayback.route().id().value(),
                            activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                            activePlayback.waitingStop()
                                    .map(RouteStop::waitCondition)
                                    .map(WaitCondition::durationTicks)
                                    .orElse(0)
                    );
                } else {
                    CreateAeronauticsAutomatedLogistics.debugLog(
                            "Playback {} docked at stop {} and will hold for {} ticks",
                            activePlayback.route().id().value(),
                            activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                            activePlayback.waitTicksRemaining()
                    );
                }
                if (activePlayback.waitTicksRemaining() <= 0) {
                    return finishWaiting(level, station, activePlayback, controller);
                }
            } else if (!activePlayback.dockLocked() && activePlayback.tickDockTimeout()) {
                return PlaybackFailure.DOCK_LOCK_FAILED;
            }
        }

        PlaybackFailure failure = holdAtTarget(level, activePlayback, controller, targetPosition, targetRotation);
        if (failure != null) {
            return failure;
        }

        if (activePlayback.isDockingWait() && !activePlayback.dockLocked()) {
            return null;
        }

        if (activePlayback.isIdleWait()) {
            Optional<AirshipStationBlockEntity> dockingStation = dockingStation(level, station, activePlayback);
            if (dockingStation.isEmpty()) {
                return PlaybackFailure.STATION_MISSING;
            }
            if (tickDockIdleWait(level, dockingStation.get(), activePlayback)) {
                CreateAeronauticsAutomatedLogistics.debugLog(
                        "Playback {} finished dock idle wait at stop {}",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
                );
                return finishWaiting(level, station, activePlayback, controller);
            }
            return null;
        }

        if (activePlayback.isCargoThresholdWait()) {
            Optional<AirshipStationBlockEntity> dockingStation = dockingStation(level, station, activePlayback);
            if (dockingStation.isEmpty()) {
                return PlaybackFailure.STATION_MISSING;
            }
            if (tickDockCargoThresholdWait(level, dockingStation.get(), activePlayback)) {
                CreateAeronauticsAutomatedLogistics.debugLog(
                        "Playback {} finished dock cargo threshold wait at stop {}",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
                );
                return finishWaiting(level, station, activePlayback, controller);
            }
            return null;
        }

        if (activePlayback.tickWait()) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Playback {} finished waiting at stop {}",
                    activePlayback.route().id().value(),
                    activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
            );
            return finishWaiting(level, station, activePlayback, controller);
        }
        return null;
    }

    private boolean tickDockIdleWait(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback
    ) {
        Optional<DockTransferSnapshot> snapshot = DockingRuntime.transferSnapshot(level, station, activePlayback.route());
        if (snapshot.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Playback {} could not read locked dock transfer state at stop {}; falling back to timed idle window",
                    activePlayback.route().id().value(),
                    activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
            );
            return activePlayback.tickWait();
        }

        if (activePlayback.dockTransferSnapshot().isEmpty()) {
            activePlayback.dockTransferSnapshot(snapshot.get());
            return activePlayback.tickWait();
        }

        if (!snapshot.get().equals(activePlayback.dockTransferSnapshot().get())) {
            activePlayback.dockTransferSnapshot(snapshot.get());
            activePlayback.resetIdleWait();
            return false;
        }

        if (activePlayback.tickIdleTimeout()) {
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Playback {} dock idle wait reached max timeout at stop {}; continuing",
                    activePlayback.route().id().value(),
                    activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
            );
            return true;
        }
        return activePlayback.tickWait();
    }

    private boolean tickDockCargoThresholdWait(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback
    ) {
        Optional<DockTransferSnapshot> snapshot = DockingRuntime.transferSnapshot(level, station, activePlayback.route());
        if (snapshot.isEmpty()) {
            return false;
        }

        WaitCondition waitCondition = activePlayback.waitingStop()
                .map(RouteStop::waitCondition)
                .orElse(WaitCondition.none());
        Ops[] ops = Ops.values();
        Ops operator = ops[Math.max(0, Math.min(ops.length - 1, waitCondition.cargoOperator()))];
        int currentAmount = switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD -> snapshot.get().itemAmount(
                    level,
                    waitCondition.cargoFilter(),
                    waitCondition.cargoMeasure() == 1
            );
            case UNTIL_FLUID_THRESHOLD -> snapshot.get().fluidBuckets(level, waitCondition.cargoFilter());
            default -> 0;
        };
        if (operator.test(currentAmount, waitCondition.durationTicks())) {
            return true;
        }
        if (activePlayback.tickCargoTimeout()) {
            fail(level, activePlayback, PlaybackFailure.CARGO_CONDITION_TIMEOUT);
        }
        return false;
    }

    private PlaybackFailure beginWaitAtTarget(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback,
            VehicleController controller,
            Vec3 targetPosition,
            double distanceToTarget
    ) {
        station.waitPlayback(activePlayback.route());
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Playback {} waiting at stop {} for {} ticks",
                activePlayback.route().id().value(),
                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                activePlayback.waitTicksRemaining()
        );
        if (activePlayback.isDockingWait()) {
            Optional<AirshipStationBlockEntity> dockingStation = dockingStation(level, station, activePlayback);
            if (dockingStation.isEmpty()) {
                return PlaybackFailure.STATION_MISSING;
            }
            activePlayback.activeDockStationPos(Optional.of(dockingStation.get().getBlockPos().immutable()));
            Optional<PlaybackFailure> dockFailure = DockingRuntime.beginDockingWait(level, dockingStation.get(), activePlayback.route());
            if (dockFailure.isPresent()) {
                return dockFailure.get();
            }
        }
        activePlayback.resetProgress(distanceToTarget);
        return holdAtTarget(level, activePlayback, controller, targetPosition, activePlayback.pointRotation(activePlayback.targetIndex()));
    }

    private PlaybackFailure finishWaiting(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        boolean dockingWait = activePlayback.isDockingWait();
        activePlayback.clearWait();
        if (dockingWait) {
            clearDockOutputs(level, station, activePlayback);
        }
        if (activePlayback.isComplete()) {
            complete(level, activePlayback);
            return null;
        }
        station.resumePlayback();
        activePlayback.advanceTarget();
        activePlayback.resetProgress(controller.position().distanceTo(activePlayback.targetPosition()));
        return null;
    }

    private PlaybackFailure holdAtTarget(
            ServerLevel level,
            ActivePlayback activePlayback,
            VehicleController controller,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation
    ) {
        VehicleMotionResult motionResult = controller.moveToward(
                level,
                targetPosition,
                targetRotation,
                AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                0.0D
        );
        activePlayback.resetProgress(controller.position().distanceTo(targetPosition));
        return motionResult.failureReason()
                .map(this::toPlaybackFailure)
                .orElse(null);
    }

    private boolean willCollide(ServerLevel level, net.minecraft.world.entity.Entity vehicle, Vec3 targetPosition) {
        Vec3 delta = targetPosition.subtract(vehicle.position());
        double distance = delta.length();
        if (distance <= ARRIVAL_DISTANCE) {
            return false;
        }

        double maxSpeed = Math.max(0.02D, 0.35D * AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get());
        Vec3 velocity = delta.normalize().scale(Math.min(distance, maxSpeed));
        return !level.noCollision(vehicle, vehicle.getBoundingBox().move(velocity));
    }

    private PlaybackFailure toPlaybackFailure(FailureReason reason) {
        return switch (reason) {
            case COLLISION_OR_OBSTRUCTION -> PlaybackFailure.COLLISION_OR_OBSTRUCTION;
            case VEHICLE_DESTROYED_OR_MISSING -> PlaybackFailure.VEHICLE_MISSING;
            case VEHICLE_UNLOADED -> PlaybackFailure.VEHICLE_UNLOADED;
            case START_TOO_FAR_FROM_ROUTE -> PlaybackFailure.START_TOO_FAR_FROM_ROUTE;
            case MISSING_AUTOPILOT_CONTROLLER -> PlaybackFailure.MISSING_CONTROLLER;
            case MISSING_STATION -> PlaybackFailure.STATION_MISSING;
            case INVALID_ROUTE_DATA, DIMENSION_MISMATCH -> PlaybackFailure.INVALID_ROUTE;
            case MISSING_DOCK -> PlaybackFailure.MISSING_DOCK;
            case AMBIGUOUS_DOCK -> PlaybackFailure.AMBIGUOUS_DOCK;
            case DOCK_LOCK_FAILED -> PlaybackFailure.DOCK_LOCK_FAILED;
            case CARGO_CONDITION_TIMEOUT -> PlaybackFailure.CARGO_CONDITION_TIMEOUT;
            case MOVEMENT_FAILURE, NONE -> PlaybackFailure.MOVEMENT_FAILURE;
        };
    }

    private void fail(ServerLevel level, ActivePlayback activePlayback, PlaybackFailure failure) {
        CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                "Route playback {} failed with {} at point {} after {} tick(s)",
                activePlayback.route().id().value(),
                failure,
                activePlayback.targetIndex(),
                activePlayback.playbackTicks()
        );
        setVisualsActive(level, activePlayback, false);
        activePlayback.controller().stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            station.failPlayback(failure.failureReason());
        });
    }

    private void complete(ServerLevel level, ActivePlayback activePlayback) {
        setVisualsActive(level, activePlayback, false);
        activePlayback.controller().stop(level);
        activePlayback.completed(true);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            station.stopPlayback();
        });
    }

    private Optional<AirshipStationBlockEntity> dockingStation(
            ServerLevel level,
            AirshipStationBlockEntity fallbackStation,
            ActivePlayback activePlayback
    ) {
        return activePlayback.waitingStop()
                .flatMap(RouteStop::dockPos)
                .flatMap(dockStationPos -> stationAt(level, dockStationPos))
                .or(() -> Optional.of(fallbackStation));
    }

    private void clearDockOutputs(
            ServerLevel level,
            AirshipStationBlockEntity fallbackStation,
            ActivePlayback activePlayback
    ) {
        Optional<BlockPos> dockingStationPos = activePlayback.activeDockStationPos()
                .or(() -> activePlayback.waitingStop().flatMap(RouteStop::dockPos));
        dockingStationPos
                .flatMap(dockStationPos -> stationAt(level, dockStationPos))
                .ifPresent(dockingStation -> DockingRuntime.clearDockOutputs(level, dockingStation, activePlayback.route()));
        if (dockingStationPos.isPresent() && !dockingStationPos.get().equals(fallbackStation.getBlockPos())) {
            DockingRuntime.clearDockOutputs(level, fallbackStation, activePlayback.route());
        }
        activePlayback.activeDockStationPos(Optional.empty());
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos stationPos) {
        if (level.getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    private void setVisualsActive(ServerLevel level, ActivePlayback activePlayback, boolean active) {
        activePlayback.route().linkedController().vehicleId().ifPresent(shipId -> {
            if (active) {
                if (!activeVisualShipIds.add(shipId)) {
                    return;
                }
            } else if (!activeVisualShipIds.remove(shipId)) {
                return;
            }

            SetAutomatedShipVisualStatePayload payload = new SetAutomatedShipVisualStatePayload(shipId, active);
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
    }

    private void restorePendingRuntime(MinecraftServer server) {
        Iterator<Map.Entry<RouteId, CompoundTag>> iterator = pendingRuntimePlaybacks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RouteId, CompoundTag> entry = iterator.next();
            if (activePlaybacks.containsKey(entry.getKey())) {
                iterator.remove();
                pendingRuntimeRestoreCooldowns.remove(entry.getKey());
                continue;
            }
            int cooldown = pendingRuntimeRestoreCooldowns.getOrDefault(entry.getKey(), 0);
            if (cooldown > 0) {
                pendingRuntimeRestoreCooldowns.put(entry.getKey(), cooldown - 1);
                continue;
            }
            Optional<ActivePlayback> activePlayback = readActivePlayback(server, entry.getValue());
            if (activePlayback.isEmpty()) {
                CreateAeronauticsAutomatedLogistics.debugLog(
                        "Route playback {} is still pending restore; controller or level is not ready",
                        entry.getKey().value()
                );
                pendingRuntimeRestoreCooldowns.put(entry.getKey(), 20);
                continue;
            }
            ActivePlayback restored = activePlayback.get();
            activePlaybacks.put(restored.route().id(), restored);
            iterator.remove();
            pendingRuntimeRestoreCooldowns.remove(entry.getKey());
            ServerLevel level = server.getLevel(restored.route().dimension());
            if (level == null) {
                continue;
            }
            restored.beginRestoreCatch();
            restored.controller().stop(level);
            stationAt(level, restored.stationPos()).ifPresent(station -> {
                if (restored.isWaiting()) {
                    station.waitPlayback(restored.route());
                    if (restored.isDockingWait()) {
                        Optional<AirshipStationBlockEntity> dockingStation = dockingStation(level, station, restored);
                        dockingStation.ifPresent(docking -> DockingRuntime.beginDockingWait(level, docking, restored.route()));
                    }
                } else {
                    station.startPlayback(restored.route());
                }
            });
            setVisualsActive(level, restored, true);
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Restored active route playback {} after server reload",
                    restored.route().id().value()
            );
        }
    }

    private CompoundTag writeActivePlayback(ActivePlayback activePlayback) {
        CompoundTag tag = new CompoundTag();
        tag.put(ROUTE, RouteNbtSerializer.write(activePlayback.route()));
        tag.put(STATION_POS, NbtUtils.writeBlockPos(activePlayback.stationPos()));
        tag.put(JOIN_OFFSET, writeVec3(activePlayback.joinOffset()));
        activePlayback.joinStartRotation().ifPresent(rotation -> tag.put(JOIN_START_ROTATION, writeRotation(rotation)));
        tag.putInt(TARGET_INDEX, activePlayback.targetIndex());
        tag.putInt(DIRECTION, activePlayback.direction());
        tag.putLong(SEGMENT_DURATION_TICKS, activePlayback.segmentDurationTicks());
        tag.putInt(SEGMENT_ELAPSED_TICKS, activePlayback.segmentElapsedTicks());
        tag.putDouble(PREVIOUS_DISTANCE, activePlayback.previousDistance());
        tag.putInt(STALLED_TICKS, activePlayback.stalledTicks());
        tag.putInt(PLAYBACK_TICKS, activePlayback.playbackTicks());
        tag.putInt(INITIAL_JOIN_SEGMENTS_ADVANCED, activePlayback.initialJoinSegmentsAdvanced());
        tag.putInt(ENDPOINT_SETTLE_TICKS_TAG, activePlayback.endpointSettleTicks());
        activePlayback.waitingStop().ifPresent(stop -> tag.putUUID(WAITING_STOP_ID, stop.id()));
        activePlayback.activeDockStationPos().ifPresent(pos -> tag.put(ACTIVE_DOCK_STATION_POS, NbtUtils.writeBlockPos(pos)));
        tag.putInt(WAIT_TICKS_REMAINING, activePlayback.waitTicksRemaining());
        tag.putInt(IDLE_WINDOW_TICKS, activePlayback.idleWindowTicks());
        tag.putInt(DOCK_TIMEOUT_TICKS_REMAINING, activePlayback.dockTimeoutTicksRemaining());
        tag.putInt(DOCK_IDLE_TIMEOUT_TICKS_REMAINING, activePlayback.dockIdleTimeoutTicksRemaining());
        tag.putInt(DOCK_CARGO_TIMEOUT_TICKS_REMAINING, activePlayback.dockCargoTimeoutTicksRemaining());
        tag.putBoolean(DOCK_LOCKED, activePlayback.dockLocked());
        tag.putInt(RESTORE_CATCH_TICKS_REMAINING, activePlayback.restoreCatchTicksRemaining());
        tag.putBoolean(COMPLETED, activePlayback.completed());
        return tag;
    }

    private Optional<ActivePlayback> readActivePlayback(MinecraftServer server, CompoundTag tag) {
        if (!tag.contains(ROUTE, Tag.TAG_COMPOUND) || !tag.contains(STATION_POS)) {
            return Optional.empty();
        }

        Optional<Route> route = RouteNbtSerializer.read(tag.getCompound(ROUTE));
        if (route.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(route.get().dimension());
        if (level == null) {
            return Optional.empty();
        }
        Optional<VehicleController> controller = VehicleControllerResolver.resolve(level, route.get().linkedController());
        if (controller.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockPos> stationPos = NbtUtils.readBlockPos(tag, STATION_POS);
        if (stationPos.isEmpty()) {
            return Optional.empty();
        }

        Vec3 joinOffset = readVec3(tag.getCompound(JOIN_OFFSET));
        Optional<RouteRotation> joinStartRotation = tag.contains(JOIN_START_ROTATION, Tag.TAG_COMPOUND)
                ? readRotation(tag.getCompound(JOIN_START_ROTATION))
                : Optional.empty();
        int targetIndex = tag.getInt(TARGET_INDEX);
        if (targetIndex < 0 || targetIndex >= route.get().points().size()) {
            return Optional.empty();
        }
        int direction = tag.getInt(DIRECTION);
        if (direction != -1 && direction != 1) {
            return Optional.empty();
        }
        Optional<RouteStop> waitingStop = tag.hasUUID(WAITING_STOP_ID)
                ? route.get().stops().stream().filter(stop -> stop.id().equals(tag.getUUID(WAITING_STOP_ID))).findFirst()
                : Optional.empty();
        Optional<BlockPos> activeDockStationPos = tag.contains(ACTIVE_DOCK_STATION_POS)
                ? NbtUtils.readBlockPos(tag, ACTIVE_DOCK_STATION_POS).map(BlockPos::immutable)
                : Optional.empty();

        return Optional.of(ActivePlayback.restore(
                route.get(),
                stationPos.get().immutable(),
                controller.get(),
                joinOffset,
                joinStartRotation,
                targetIndex,
                direction,
                tag.contains(SEGMENT_DURATION_TICKS, Tag.TAG_ANY_NUMERIC) ? Math.max(1L, tag.getLong(SEGMENT_DURATION_TICKS)) : 1L,
                Math.max(0, tag.getInt(SEGMENT_ELAPSED_TICKS)),
                tag.contains(PREVIOUS_DISTANCE, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(PREVIOUS_DISTANCE) : Double.MAX_VALUE,
                Math.max(0, tag.getInt(STALLED_TICKS)),
                Math.max(0, tag.getInt(PLAYBACK_TICKS)),
                Math.max(0, tag.getInt(INITIAL_JOIN_SEGMENTS_ADVANCED)),
                Math.max(0, tag.getInt(ENDPOINT_SETTLE_TICKS_TAG)),
                waitingStop,
                activeDockStationPos,
                Math.max(0, tag.getInt(WAIT_TICKS_REMAINING)),
                Math.max(0, tag.getInt(IDLE_WINDOW_TICKS)),
                Math.max(0, tag.getInt(DOCK_TIMEOUT_TICKS_REMAINING)),
                Math.max(0, tag.getInt(DOCK_IDLE_TIMEOUT_TICKS_REMAINING)),
                Math.max(0, tag.getInt(DOCK_CARGO_TIMEOUT_TICKS_REMAINING)),
                tag.getBoolean(DOCK_LOCKED),
                Math.max(0, tag.getInt(RESTORE_CATCH_TICKS_REMAINING)),
                tag.getBoolean(COMPLETED)
        ));
    }

    private Optional<RouteId> routeIdFromRuntimeTag(CompoundTag tag) {
        if (!tag.contains(ROUTE, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return RouteNbtSerializer.read(tag.getCompound(ROUTE)).map(Route::id);
    }

    private CompoundTag writeVec3(Vec3 vec3) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vec3.x);
        tag.putDouble("y", vec3.y);
        tag.putDouble("z", vec3.z);
        return tag;
    }

    private Vec3 readVec3(CompoundTag tag) {
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    private CompoundTag writeRotation(RouteRotation rotation) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", rotation.x());
        tag.putDouble("y", rotation.y());
        tag.putDouble("z", rotation.z());
        tag.putDouble("w", rotation.w());
        return tag;
    }

    private Optional<RouteRotation> readRotation(CompoundTag tag) {
        try {
            return Optional.of(new RouteRotation(
                    tag.getDouble("x"),
                    tag.getDouble("y"),
                    tag.getDouble("z"),
                    tag.getDouble("w")
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static final class ActivePlayback {
        private final Route route;
        private final BlockPos stationPos;
        private final VehicleController controller;
        private final Vec3 joinOffset;
        private final Optional<RouteRotation> joinStartRotation;
        private int targetIndex;
        private int direction;
        private long segmentDurationTicks;
        private int segmentElapsedTicks;
        private double previousDistance = Double.MAX_VALUE;
        private int stalledTicks;
        private int playbackTicks;
        private int initialJoinSegmentsAdvanced;
        private int endpointSettleTicks;
        private Optional<RouteStop> waitingStop = Optional.empty();
        private Optional<BlockPos> activeDockStationPos = Optional.empty();
        private int waitTicksRemaining;
        private int idleWindowTicks;
        private int dockTimeoutTicksRemaining;
        private int dockIdleTimeoutTicksRemaining;
        private int dockCargoTimeoutTicksRemaining;
        private boolean dockLocked;
        private int restoreCatchTicksRemaining;
        private Optional<DockTransferSnapshot> dockTransferSnapshot = Optional.empty();
        private boolean completed;

        private ActivePlayback(
                Route route,
                BlockPos stationPos,
                VehicleController controller,
                Vec3 joinOffset,
                Optional<RouteRotation> joinStartRotation,
                int targetIndex,
                int direction
        ) {
            this.route = route;
            this.stationPos = stationPos;
            this.controller = controller;
            this.joinOffset = joinOffset;
            this.joinStartRotation = joinStartRotation;
            this.targetIndex = targetIndex;
            this.direction = direction;
            resetSegmentTiming();
        }

        private static ActivePlayback create(Route route, BlockPos stationPos, VehicleController controller) {
            Vec3 vehiclePosition = controller.position();
            Optional<RouteRotation> vehicleRotation = controller.routeRotation();
            Vec3 first = route.points().getFirst().position();
            Vec3 last = route.points().getLast().position();
            if (vehiclePosition.distanceToSqr(first) <= vehiclePosition.distanceToSqr(last)) {
                return new ActivePlayback(route, stationPos, controller, vehiclePosition.subtract(first), vehicleRotation, 1, 1);
            }
            int lastIndex = route.points().size() - 1;
            return new ActivePlayback(
                    route,
                    stationPos,
                    controller,
                    vehiclePosition.subtract(last),
                    vehicleRotation,
                    lastIndex - 1,
                    -1
            );
        }

        private static ActivePlayback restore(
                Route route,
                BlockPos stationPos,
                VehicleController controller,
                Vec3 joinOffset,
                Optional<RouteRotation> joinStartRotation,
                int targetIndex,
                int direction,
                long segmentDurationTicks,
                int segmentElapsedTicks,
                double previousDistance,
                int stalledTicks,
                int playbackTicks,
                int initialJoinSegmentsAdvanced,
                int endpointSettleTicks,
                Optional<RouteStop> waitingStop,
                Optional<BlockPos> activeDockStationPos,
                int waitTicksRemaining,
                int idleWindowTicks,
                int dockTimeoutTicksRemaining,
                int dockIdleTimeoutTicksRemaining,
                int dockCargoTimeoutTicksRemaining,
                boolean dockLocked,
                int restoreCatchTicksRemaining,
                boolean completed
        ) {
            ActivePlayback activePlayback = new ActivePlayback(
                    route,
                    stationPos,
                    controller,
                    joinOffset,
                    joinStartRotation,
                    targetIndex,
                    direction
            );
            activePlayback.segmentDurationTicks = Math.max(1L, segmentDurationTicks);
            activePlayback.segmentElapsedTicks = Math.max(0, segmentElapsedTicks);
            activePlayback.previousDistance = previousDistance;
            activePlayback.stalledTicks = Math.max(0, stalledTicks);
            activePlayback.playbackTicks = Math.max(0, playbackTicks);
            activePlayback.initialJoinSegmentsAdvanced = Math.max(0, initialJoinSegmentsAdvanced);
            activePlayback.endpointSettleTicks = Math.max(0, endpointSettleTicks);
            activePlayback.waitingStop = waitingStop;
            activePlayback.activeDockStationPos = activeDockStationPos;
            activePlayback.waitTicksRemaining = Math.max(0, waitTicksRemaining);
            activePlayback.idleWindowTicks = Math.max(0, idleWindowTicks);
            activePlayback.dockTimeoutTicksRemaining = Math.max(0, dockTimeoutTicksRemaining);
            activePlayback.dockIdleTimeoutTicksRemaining = Math.max(0, dockIdleTimeoutTicksRemaining);
            activePlayback.dockCargoTimeoutTicksRemaining = Math.max(0, dockCargoTimeoutTicksRemaining);
            activePlayback.dockLocked = dockLocked;
            activePlayback.restoreCatchTicksRemaining = Math.max(0, restoreCatchTicksRemaining);
            activePlayback.completed = completed;
            activePlayback.dockTransferSnapshot = Optional.empty();
            return activePlayback;
        }

        private static double nearestEndpointDistance(Route route, VehicleController controller) {
            Vec3 vehiclePosition = controller.position();
            Vec3 first = route.points().getFirst().position();
            Vec3 last = route.points().getLast().position();
            return Math.min(vehiclePosition.distanceTo(first), vehiclePosition.distanceTo(last));
        }

        private Route route() {
            return route;
        }

        private BlockPos stationPos() {
            return stationPos;
        }

        private VehicleController controller() {
            return controller;
        }

        private Vec3 joinOffset() {
            return joinOffset;
        }

        private Optional<RouteRotation> joinStartRotation() {
            return joinStartRotation;
        }

        private RoutePoint targetPoint() {
            return route.points().get(targetIndex);
        }

        private Vec3 targetPosition() {
            return pointPosition(targetIndex);
        }

        private int targetIndex() {
            return targetIndex;
        }

        private int direction() {
            return direction;
        }

        private double targetSpeedBlocksPerTick() {
            return adjacentSegmentSpeedBlocksPerTick();
        }

        private double adjacentSegmentSpeedBlocksPerTick() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return MIN_REPLAY_SPEED;
            }

            RoutePoint previous = route.points().get(previousIndex);
            RoutePoint target = targetPoint();
            long ticks = Math.max(1L, Math.abs(target.tickOffset() - previous.tickOffset()));
            double speed = previous.position().distanceTo(target.position()) / ticks;
            return Math.max(MIN_REPLAY_SPEED, Math.min(MAX_REPLAY_SPEED, speed));
        }

        private void advanceTarget() {
            initialJoinSegmentsAdvanced++;
            endpointSettleTicks = 0;
            if (route.playbackMode() == net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode.ONE_WAY
                    && targetIndex == route.points().size() - 1
                    && direction > 0) {
                return;
            }
            if (targetIndex == route.points().size() - 1) {
                direction = -1;
            } else if (targetIndex == 0) {
                direction = 1;
            }
            targetIndex += direction;
            resetSegmentTiming();
        }

        private boolean beginWaitAtCurrentTarget() {
            Optional<RouteStop> stop = route.stops().stream()
                    .filter(routeStop -> routeStop.pointIndex() == targetIndex)
                    .findFirst();
            if (stop.isEmpty()) {
                return false;
            }

            RouteStop routeStop = stop.get();
            if (!routeStop.waitCondition().waits()) {
                return false;
            }

            waitingStop = stop;
            waitTicksRemaining = Math.max(0, routeStop.waitCondition().runtimeTicks());
            if (routeStop.waitCondition().type() == WaitConditionType.UNTIL_IDLE && waitTicksRemaining <= 0) {
                waitTicksRemaining = WaitCondition.DEFAULT_TIMED_WAIT_TICKS;
            }
            idleWindowTicks = waitTicksRemaining;
            dockTimeoutTicksRemaining = isDockingWait()
                    ? AutomatedLogisticsConfig.DOCK_LOCK_TIMEOUT_TICKS.get()
                    : 0;
            dockIdleTimeoutTicksRemaining = routeStop.waitCondition().type() == WaitConditionType.UNTIL_IDLE
                    ? (routeStop.waitCondition().maxTicks() > 0
                    ? routeStop.waitCondition().maxTicks()
                    : AutomatedLogisticsConfig.DOCK_IDLE_TIMEOUT_TICKS.get())
                    : 0;
            dockCargoTimeoutTicksRemaining = isCargoThresholdWait()
                    ? (routeStop.waitCondition().maxTicks() > 0
                    ? routeStop.waitCondition().maxTicks()
                    : AutomatedLogisticsConfig.DOCK_CARGO_TIMEOUT_TICKS.get())
                    : 0;
            dockLocked = false;
            dockTransferSnapshot = Optional.empty();
            if (!isDockingWait() && waitTicksRemaining <= 0) {
                waitingStop = Optional.empty();
                return false;
            }
            return true;
        }

        private boolean isComplete() {
            return route.playbackMode() == net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode.ONE_WAY
                    && targetIndex == route.points().size() - 1
                    && direction > 0
                    && waitingStop.isEmpty();
        }

        private boolean completed() {
            return completed;
        }

        private void completed(boolean completed) {
            this.completed = completed;
        }

        private boolean isWaiting() {
            return waitingStop.isPresent();
        }

        private Optional<RouteStop> waitingStop() {
            return waitingStop;
        }

        private Optional<BlockPos> activeDockStationPos() {
            return activeDockStationPos;
        }

        private void activeDockStationPos(Optional<BlockPos> activeDockStationPos) {
            this.activeDockStationPos = activeDockStationPos;
        }

        private boolean isDockingWait() {
            return waitingStop
                    .map(RouteStop::waitCondition)
                    .map(wait -> wait.type() == WaitConditionType.UNTIL_DOCKED
                            || wait.type() == WaitConditionType.UNTIL_IDLE
                            || wait.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                            || wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD)
                    .orElse(false);
        }

        private boolean isIdleWait() {
            return waitingStop
                    .map(RouteStop::waitCondition)
                    .map(wait -> wait.type() == WaitConditionType.UNTIL_IDLE)
                    .orElse(false);
        }

        private boolean isCargoThresholdWait() {
            return waitingStop
                    .map(RouteStop::waitCondition)
                    .map(wait -> wait.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                            || wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD)
                    .orElse(false);
        }

        private int waitTicksRemaining() {
            return waitTicksRemaining;
        }

        private int idleWindowTicks() {
            return idleWindowTicks;
        }

        private boolean dockLocked() {
            return dockLocked;
        }

        private void beginRestoreCatch() {
            restoreCatchTicksRemaining = Math.max(restoreCatchTicksRemaining, RESTORE_CATCH_TICKS);
        }

        private boolean isRestoring() {
            return restoreCatchTicksRemaining > 0;
        }

        private boolean tickRestoreGrace() {
            if (restoreCatchTicksRemaining <= 0) {
                return false;
            }
            restoreCatchTicksRemaining--;
            return true;
        }

        private int restoreCatchTicksRemaining() {
            return restoreCatchTicksRemaining;
        }

        private int dockTimeoutTicksRemaining() {
            return dockTimeoutTicksRemaining;
        }

        private int dockIdleTimeoutTicksRemaining() {
            return dockIdleTimeoutTicksRemaining;
        }

        private int dockCargoTimeoutTicksRemaining() {
            return dockCargoTimeoutTicksRemaining;
        }

        private void dockLocked(boolean dockLocked) {
            this.dockLocked = dockLocked;
        }

        private Optional<DockTransferSnapshot> dockTransferSnapshot() {
            return dockTransferSnapshot;
        }

        private void dockTransferSnapshot(DockTransferSnapshot dockTransferSnapshot) {
            this.dockTransferSnapshot = Optional.of(dockTransferSnapshot);
        }

        private void resetIdleWait() {
            waitTicksRemaining = Math.max(1, idleWindowTicks);
        }

        private boolean tickWait() {
            if (waitTicksRemaining > 0) {
                waitTicksRemaining--;
            }
            return waitTicksRemaining <= 0;
        }

        private boolean tickDockTimeout() {
            if (dockTimeoutTicksRemaining > 0) {
                dockTimeoutTicksRemaining--;
            }
            return dockTimeoutTicksRemaining <= 0;
        }

        private boolean tickIdleTimeout() {
            if (dockIdleTimeoutTicksRemaining > 0) {
                dockIdleTimeoutTicksRemaining--;
            }
            return dockIdleTimeoutTicksRemaining <= 0;
        }

        private boolean tickCargoTimeout() {
            if (dockCargoTimeoutTicksRemaining > 0) {
                dockCargoTimeoutTicksRemaining--;
            }
            return dockCargoTimeoutTicksRemaining <= 0;
        }

        private void clearWait() {
            waitingStop = Optional.empty();
            waitTicksRemaining = 0;
            idleWindowTicks = 0;
            dockTimeoutTicksRemaining = 0;
            dockIdleTimeoutTicksRemaining = 0;
            dockCargoTimeoutTicksRemaining = 0;
            dockLocked = false;
            dockTransferSnapshot = Optional.empty();
        }

        private Vec3 pointPosition(int pointIndex) {
            return route.points().get(pointIndex).position().add(blendedJoinOffset(pointIndex));
        }

        private Vec3 blendedJoinOffset(int pointIndex) {
            double blendFactor = positionJoinBlendFactor(pointIndex);
            if (blendFactor <= 0.0D) {
                return Vec3.ZERO;
            }

            return joinOffset.scale(blendFactor);
        }

        private double positionJoinBlendFactor(int pointIndex) {
            int ordinal = initialJoinOrdinal(pointIndex);
            if (ordinal == Integer.MAX_VALUE) {
                return 0.0D;
            }
            if (ordinal <= 1) {
                return 1.0D;
            }
            if (ordinal > JOIN_BLEND_SEGMENTS) {
                return 0.0D;
            }

            return 1.0D - ((double) (ordinal - 1) / JOIN_BLEND_SEGMENTS);
        }

        private int initialJoinOrdinal(int pointIndex) {
            if (pointIndex == targetIndex) {
                return initialJoinSegmentsAdvanced + 1;
            }
            if (pointIndex == targetIndex - direction) {
                return initialJoinSegmentsAdvanced;
            }
            return Integer.MAX_VALUE;
        }

        private Optional<RouteRotation> targetRotation() {
            return pointRotation(targetIndex);
        }

        private Vec3 guidancePosition() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return targetPosition();
            }

            Vec3 previousPosition = pointPosition(previousIndex);
            Vec3 nextPosition = pointPosition(targetIndex);
            return previousPosition.lerp(nextPosition, segmentProgress());
        }

        private Optional<RouteRotation> guidanceRotation() {
            int previousIndex = targetIndex - direction;
            Optional<RouteRotation> targetRotation = pointRotation(targetIndex);
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return targetRotation;
            }

            Optional<RouteRotation> previousRotation = pointRotation(previousIndex);
            if (previousRotation.isEmpty()) {
                return targetRotation;
            }
            if (targetRotation.isEmpty()) {
                return previousRotation;
            }
            return Optional.of(previousRotation.get().slerp(targetRotation.get(), segmentProgress()));
        }

        private Optional<RouteRotation> pointRotation(int pointIndex) {
            Optional<RouteRotation> routeRotation = route.points().get(pointIndex).rotation();
            if (routeRotation.isEmpty() || joinStartRotation.isEmpty()) {
                return routeRotation;
            }

            int ordinal = initialJoinOrdinal(pointIndex);
            if (ordinal == Integer.MAX_VALUE) {
                return routeRotation;
            }
            if (ordinal <= 0) {
                return joinStartRotation;
            }
            if (ordinal > JOIN_BLEND_SEGMENTS) {
                return routeRotation;
            }

            double joinProgress = Math.min(1.0D, (double) ordinal / JOIN_BLEND_SEGMENTS);
            return Optional.of(joinStartRotation.get().slerp(routeRotation.get(), joinProgress));
        }

        private double segmentProgress() {
            if (segmentDurationTicks <= 0L) {
                return 1.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, (double) segmentElapsedTicks / (double) segmentDurationTicks));
        }

        private boolean isRotationAligned(Optional<RouteRotation> targetRotation, VehicleController controller) {
            if (targetRotation.isEmpty()) {
                return true;
            }
            return controller.routeRotation()
                    .map(currentRotation -> angularDifferenceDegrees(currentRotation, targetRotation.get()) <= ARRIVAL_ROTATION_TOLERANCE_DEGREES)
                    .orElse(true);
        }

        private double arrivalDistance() {
            return isPreciseArrivalPoint(targetIndex) ? ENDPOINT_ARRIVAL_DISTANCE : ARRIVAL_DISTANCE;
        }

        private boolean shouldHoldAtTarget(double distanceToTarget) {
            return isStationarySegment()
                    && distanceToTarget <= arrivalDistance()
                    && segmentElapsedTicks < segmentDurationTicks;
        }

        private boolean shouldSettleEndpoint(double distanceToTarget, boolean rotationAligned) {
            return isPreciseArrivalPoint(targetIndex)
                    && rotationAligned
                    && distanceToTarget <= ENDPOINT_SETTLE_DISTANCE;
        }

        private boolean tickEndpointSettle() {
            endpointSettleTicks++;
            return endpointSettleTicks >= ENDPOINT_SETTLE_TICKS;
        }

        private void resetEndpointSettle() {
            endpointSettleTicks = 0;
        }

        private boolean requiresRotationAlignmentForArrival() {
            return isPreciseArrivalPoint(targetIndex);
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

        private boolean isStalled(double currentDistance) {
            playbackTicks++;
            if (playbackTicks <= INITIAL_STALL_GRACE_TICKS) {
                resetProgress(currentDistance);
                return false;
            }

            if (currentDistance < previousDistance - MIN_PROGRESS_PER_TICK) {
                resetProgress(currentDistance);
                return false;
            }

            previousDistance = currentDistance;
            stalledTicks++;
            return stalledTicks >= MAX_STALLED_TICKS;
        }

        private void resetProgress(double currentDistance) {
            previousDistance = currentDistance;
            stalledTicks = 0;
        }

        private void resetSegmentTiming() {
            segmentDurationTicks = computeSegmentDurationTicks();
            segmentElapsedTicks = 0;
        }

        private long segmentDurationTicks() {
            return segmentDurationTicks;
        }

        private int segmentElapsedTicks() {
            return segmentElapsedTicks;
        }

        private long computeSegmentDurationTicks() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return 1L;
            }
            return Math.max(1L, Math.abs(route.points().get(targetIndex).tickOffset() - route.points().get(previousIndex).tickOffset()));
        }

        private boolean isStationarySegment() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return false;
            }
            return route.points().get(previousIndex).position().distanceTo(route.points().get(targetIndex).position()) <= STATIONARY_SEGMENT_DISTANCE;
        }

        private boolean isEndpoint(int pointIndex) {
            return pointIndex == 0 || pointIndex == route.points().size() - 1;
        }

        private boolean isPreciseArrivalPoint(int pointIndex) {
            return isEndpoint(pointIndex) || route.stops().stream().anyMatch(stop -> stop.pointIndex() == pointIndex);
        }

        private void tickSegment() {
            if (segmentElapsedTicks < Integer.MAX_VALUE) {
                segmentElapsedTicks++;
            }
        }

        private int playbackTicks() {
            return playbackTicks;
        }

        private double previousDistance() {
            return previousDistance;
        }

        private int stalledTicks() {
            return stalledTicks;
        }

        private int endpointSettleTicks() {
            return endpointSettleTicks;
        }

        private int initialJoinSegmentsAdvanced() {
            return initialJoinSegmentsAdvanced;
        }

        private boolean shouldLogProgress() {
            return playbackTicks <= 10 || playbackTicks % 20 == 0;
        }
    }
}

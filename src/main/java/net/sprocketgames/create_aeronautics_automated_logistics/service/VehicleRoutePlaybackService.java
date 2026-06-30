package net.sprocketgames.create_aeronautics_automated_logistics.service;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition.Ops;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetAutomatedShipVisualStatePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.ShowDockingIssueToastPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncAutomatedShipVisualsPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockTransferSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingRuntime;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleMotionResult;

public class VehicleRoutePlaybackService implements RoutePlaybackService {
    private static final double ARRIVAL_DISTANCE = 0.5D;
    private static final double ENDPOINT_ARRIVAL_DISTANCE = 0.2D;
    private static final double ENDPOINT_SETTLE_DISTANCE = 0.65D;
    private static final double DOCK_REACQUIRE_HANDOFF_DISTANCE = ENDPOINT_SETTLE_DISTANCE;
    private static final double ARRIVAL_ROTATION_TOLERANCE_DEGREES = 4.0D;
    private static final double MIN_EFFECTIVE_REPLAY_SPEED = 0.03D;
    private static final double MAX_REPLAY_SPEED = 3.0D;
    private static final double RESTORE_CATCH_SPEED = 0.08D;
    private static final double RESTORE_CATCH_COMPLETE_DISTANCE = 0.75D;
    private static final double RESTORE_RELOCATE_DISTANCE = 5.0D;
    private static final double WAIT_HOLD_SPEED = 0.25D;
    private static final double DOCK_QUEUE_HOLD_RELOCATE_DISTANCE = 0.75D;
    private static final int DOCK_QUEUE_SOFT_HOLD_TICKS = 10;
    private static final int ROUTE_START_STABILIZATION_TICKS = 40;
    private static final int JOIN_BLEND_SEGMENTS = 5;
    private static final int ENDPOINT_SETTLE_TICKS = 40;
    private static final int RESTORE_CATCH_TICKS = 200;
    private static final int UNLOADED_MATERIALIZE_RETRY_TICKS = 20;
    private static final int DOCKING_ISSUE_NOTICE_COOLDOWN_TICKS = 20 * 60;
    private static final double NOMINAL_TICK_NANOS = 50_000_000.0D;
    private static final double STATIONARY_SEGMENT_DISTANCE = 0.1D;
    private static final String ACTIVE_PLAYBACKS = "activePlaybacks";
    private static final String DOCK_RESERVATIONS = "dockReservations";
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
    private static final String CONDITION_STATES = "conditionStates";
    private static final String CONDITION_GROUP_INDEX = "conditionGroupIndex";
    private static final String CONDITION_INDEX = "conditionIndex";
    private static final String CONDITION_WAIT_TICKS = "conditionWaitTicks";
    private static final String CONDITION_IDLE_WINDOW_TICKS = "conditionIdleWindowTicks";
    private static final String CONDITION_IDLE_TIMEOUT_TICKS = "conditionIdleTimeoutTicks";
    private static final String CONDITION_CARGO_TIMEOUT_TICKS = "conditionCargoTimeoutTicks";
    private static final String CONDITION_SATISFIED = "conditionSatisfied";
    private static final String CONDITION_FAILURE = "conditionFailure";
    private static final String DOCK_WAIT_FAILURE = "dockWaitFailure";
    private static final String RESTORE_CATCH_TICKS_REMAINING = "restoreCatchTicksRemaining";
    private static final String PAUSE_STATE = "pauseState";
    private static final String HELD_FAILURE = "heldFailure";
    private static final String HOLD_POSITION = "holdPosition";
    private static final String HOLD_ROTATION = "holdRotation";
    private static final String DOCK_QUEUE_STOP_ID = "dockQueueStopId";
    private static final String DOCK_QUEUE_HOLD_LIVE_POSE = "dockQueueHoldLivePose";
    private static final String COMPLETED = "completed";
    private static final String RUNTIME_MODE = "runtimeMode";
    private static final String VALIDATED_LEG_START_INDEX = "validatedLegStartIndex";
    private static final String VALIDATED_LEG_TARGET_INDEX = "validatedLegTargetIndex";

    private final Map<RouteId, ActivePlayback> activePlaybacks = new HashMap<>();
    private final Map<RouteId, CompoundTag> pendingRuntimePlaybacks = new HashMap<>();
    private final Map<RouteId, Integer> pendingRuntimeRestoreCooldowns = new HashMap<>();
    private final Map<RouteId, PlaybackFailure> terminalRuntimeFailures = new HashMap<>();
    private final Map<RouteId, DeferredDockOutputClear> deferredDockOutputClears = new HashMap<>();
    private final Map<RouteId, Long> dockingIssueNoticeTicks = new HashMap<>();
    private final Set<UUID> activeVisualShipIds = new HashSet<>();
    private int visualResyncTicker;

    public void resetRuntime() {
        activePlaybacks.clear();
        pendingRuntimePlaybacks.clear();
        pendingRuntimeRestoreCooldowns.clear();
        terminalRuntimeFailures.clear();
        deferredDockOutputClears.clear();
        dockingIssueNoticeTicks.clear();
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
        tag.put(DOCK_RESERVATIONS, DockingRuntime.saveRuntime());
        return tag;
    }

    public void loadRuntime(MinecraftServer server, CompoundTag tag) {
        resetRuntime();
        if (tag == null || !tag.contains(ACTIVE_PLAYBACKS, Tag.TAG_LIST)) {
            CreateAeronauticsAutomatedLogistics.debugPlayback("Loaded route playback runtime: no saved active playbacks");
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
        DockingRuntime.loadRuntime(tag.getCompound(DOCK_RESERVATIONS));
        CreateAeronauticsAutomatedLogistics.debugPlayback(
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
        if (!withinActiveVehicleLimit(route.ownerId())) {
            return PlaybackOperationResult.failure(PlaybackFailure.MAX_ACTIVE_VEHICLES_REACHED);
        }
        Optional<AirshipStationBlockEntity> station = stationAt(level, stationPos);
        if (station.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.STATION_MISSING);
        }

        Optional<VehicleController> controller = resolveLiveController(level, route, "start_playback", "route_start_live_lookup");
        if (controller.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.VEHICLE_MISSING);
        }
        if (!controller.get().isAutomationCapable()) {
            return PlaybackOperationResult.failure(PlaybackFailure.MISSING_CONTROLLER);
        }
        if (ActivePlayback.nearestEndpointDistance(route, controller.get()) > AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get()) {
            return PlaybackOperationResult.failure(PlaybackFailure.START_TOO_FAR_FROM_ROUTE);
        }

        clearConflictingControllerPlaybacks(level, route);

        ActivePlayback activePlayback = ActivePlayback.create(route, stationPos, controller.get());
        if (!validateCurrentLegForDeparture(level, activePlayback)) {
            return PlaybackOperationResult.failure(PlaybackFailure.COLLISION_OR_OBSTRUCTION);
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Starting route playback {}: points={}, stops={}, initialTarget={}, direction={}",
                route.id().value(),
                route.points().size(),
                routeStopSummary(route),
                activePlayback.targetIndex(),
                activePlayback.direction()
        );
        activePlaybacks.put(route.id(), activePlayback);
        station.get().startPlayback(route);
        setVisualsActive(level, activePlayback, true);
        DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, controller.get());
        if (queueGate.failure().isPresent()) {
            activePlaybacks.remove(route.id());
            setVisualsActive(level, activePlayback, false);
            station.get().failPlayback(queueGate.failure().get().failureReason());
            return PlaybackOperationResult.failure(queueGate.failure().get());
        }
        if (queueGate.held()) {
            return PlaybackOperationResult.success(route.id());
        }
        PlaybackFailure primingFailure = primePlaybackMotion(level, activePlayback);
        if (primingFailure != null) {
            activePlaybacks.remove(route.id());
            setVisualsActive(level, activePlayback, false);
            DockingRuntime.releaseReservation(route.id());
            station.get().failPlayback(primingFailure.failureReason());
            return PlaybackOperationResult.failure(primingFailure);
        }
        return PlaybackOperationResult.success(route.id());
    }

    @Override
    public void stopPlayback(ServerLevel level, RouteId routeId, FailureReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(reason, "reason");

        pendingRuntimePlaybacks.remove(routeId);
        pendingRuntimeRestoreCooldowns.remove(routeId);
        terminalRuntimeFailures.remove(routeId);
        clearDeferredDockOutputs(level, routeId);
        ActivePlayback activePlayback = activePlaybacks.remove(routeId);
        if (activePlayback == null) {
            return;
        }

        DockingRuntime.releaseReservation(routeId);
        setVisualsActive(level, activePlayback, false);
        activePlayback.controller(level).stop(level);
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
        for (ActivePlayback activePlayback : List.copyOf(activePlaybacks.values())) {
            RouteId routeId = activePlayback.route().id();
            if (activePlaybacks.get(routeId) != activePlayback) {
                continue;
            }
            if (!activePlayback.route().dimension().equals(level.dimension())) {
                continue;
            }

            PlaybackFailure failure = tickOne(level, activePlayback);
            if (failure != null) {
                if (failure == PlaybackFailure.VEHICLE_UNLOADED) {
                    holdTransient(level, activePlayback, failure);
                    continue;
                }
                if (shouldHoldFault(failure)) {
                    holdFault(level, activePlayback, failure);
                    continue;
                }
                activePlaybacks.remove(routeId, activePlayback);
                fail(level, activePlayback, failure);
            } else if (activePlayback.completed()) {
                if (!activePlaybacks.remove(routeId, activePlayback)) {
                    continue;
                }
                AirshipScheduleExecutionService.CompletionAdvanceResult advanceResult =
                        AutomatedLogisticsServices.SCHEDULES.advanceCompletedRoute(
                                level,
                                activePlayback.route(),
                                activePlayback.stationPos(),
                                activePlayback.activeDockStationPos()
                        );
                if (advanceResult == AirshipScheduleExecutionService.CompletionAdvanceResult.STARTED_NEXT) {
                    continue;
                }
                if (advanceResult == AirshipScheduleExecutionService.CompletionAdvanceResult.HELD_FAULT) {
                    continue;
                }
                finishCompletedPlayback(
                        level,
                        activePlayback,
                        advanceResult != AirshipScheduleExecutionService.CompletionAdvanceResult.FAILED
                );
            }
        }
    }

    private boolean shouldHoldFault(PlaybackFailure failure) {
        return true;
    }

    public void tickAll(MinecraftServer server) {
        restorePendingRuntime(server);
        if (!StationChunkLoadingService.isStartupRestoreReady()) {
            holdStartupRestoredPlaybacks(server);
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            tickPlayback(level);
        }
        tickVisualResync(server);
    }

    private void holdStartupRestoredPlaybacks(MinecraftServer server) {
        for (ActivePlayback activePlayback : activePlaybacks.values()) {
            ServerLevel level = server.getLevel(activePlayback.route().dimension());
            if (level == null) {
                continue;
            }
            VehicleController controller = activePlayback.controller(level);
            if (!controller.isLoaded(level) || !controller.isAssembled()) {
                continue;
            }
            if (activePlayback.isHoldLocked() || activePlayback.isDockQueueHeld()) {
                controller.hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
            } else if (activePlayback.isWaiting()
                    && activePlayback.requiresDockLock()
                    && activePlayback.dockLocked()) {
                controller.hold(
                        level,
                        activePlayback.waitHoldPosition(controller),
                        activePlayback.waitHoldRotation(controller)
                );
            } else {
                controller.hold(
                        level,
                        activePlayback.restoreCatchPosition(),
                        activePlayback.restoreCatchRotation().or(() -> activePlayback.waitHoldRotation(controller))
                );
            }
        }
    }

    public void holdPausedVehicles(ServerLevel level) {
        for (ActivePlayback activePlayback : activePlaybacks.values()) {
            if (!activePlayback.route().dimension().equals(level.dimension())) {
                continue;
            }
            VehicleController controller = activePlayback.controller(level);
            if (!controller.isLoaded(level) || !controller.isAssembled()) {
                continue;
            }
            if (activePlayback.isRestoring()) {
                continue;
            }
            if (activePlayback.isHoldLocked()) {
                controller.hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
                logPhysicsGuidance(activePlayback, controller, "fault_hold", activePlayback.holdPosition(), WAIT_HOLD_SPEED);
            } else if (activePlayback.isWaiting() || activePlayback.completed()) {
                Vec3 holdPosition = activePlayback.waitHoldPosition(controller);
                Optional<RouteRotation> holdRotation = activePlayback.waitHoldRotation(controller);
                controller.hold(level, holdPosition, holdRotation);
                logPhysicsGuidance(activePlayback, controller, "wait_or_complete_hold", holdPosition, WAIT_HOLD_SPEED);
            }
        }
    }

    private void logPhysicsGuidance(
            ActivePlayback activePlayback,
            VehicleController controller,
            String branch,
            Vec3 guidancePosition,
            double targetSpeed
    ) {
        if (!activePlayback.shouldLogPhysicsGuidance()) {
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Physics guidance {} route={} point={} waiting={} completed={} stabilizing={} speed={} position={} guidance={}",
                branch,
                activePlayback.route().id().value(),
                activePlayback.targetIndex(),
                activePlayback.isWaiting(),
                activePlayback.completed(),
                activePlayback.isRouteStartStabilizing(),
                targetSpeed,
                controller.position(),
                guidancePosition
        );
    }

    public PlaybackOperationResult<RouteId> startHeldFaultPlayback(
            ServerLevel level,
            BlockPos stationPos,
            Route route,
            PlaybackFailure failure
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(failure, "failure");

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
        if (!withinActiveVehicleLimit(route.ownerId())) {
            return PlaybackOperationResult.failure(PlaybackFailure.MAX_ACTIVE_VEHICLES_REACHED);
        }
        Optional<VehicleController> controller = resolveLiveController(level, route, "start_fault_hold_playback", "fault_hold_live_lookup");
        if (controller.isEmpty()) {
            return PlaybackOperationResult.failure(PlaybackFailure.VEHICLE_MISSING);
        }
        if (!controller.get().isAutomationCapable()) {
            return PlaybackOperationResult.failure(PlaybackFailure.MISSING_CONTROLLER);
        }

        clearConflictingControllerPlaybacks(level, route);

        ActivePlayback activePlayback = ActivePlayback.create(route, stationPos, controller.get());
        activePlaybacks.put(route.id(), activePlayback);
        holdFault(level, activePlayback, failure);
        return PlaybackOperationResult.success(route.id());
    }

    public boolean isRunning(RouteId routeId) {
        return activePlaybacks.containsKey(routeId);
    }

    public boolean isPending(RouteId routeId) {
        return pendingRuntimePlaybacks.containsKey(routeId);
    }

    public Optional<PlaybackFailure> consumeTerminalRuntimeFailure(RouteId routeId) {
        Objects.requireNonNull(routeId, "routeId");
        return Optional.ofNullable(terminalRuntimeFailures.remove(routeId));
    }

    public boolean isHeld(RouteId routeId) {
        return Optional.ofNullable(activePlaybacks.get(routeId)).map(ActivePlayback::isPaused).orElse(false);
    }

    private void clearConflictingControllerPlaybacks(ServerLevel level, Route route) {
        int cleared = stopLinkedPlaybacks(level, route.linkedController(), FailureReason.NONE);
        if (cleared > 0) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Cleared {} conflicting playback(s) already linked to controller {} before starting route {}",
                    cleared,
                    route.linkedController(),
                    route.id().value()
            );
        }
    }

    private boolean withinActiveVehicleLimit(Optional<UUID> ownerId) {
        int limit = AutomatedLogisticsConfig.MAX_ACTIVE_VEHICLES_PER_PLAYER.get();
        if (limit <= 0 || ownerId.isEmpty()) {
            return true;
        }
        return activeVehicleCount(ownerId.get()) < limit;
    }

    private int activeVehicleCount(UUID ownerId) {
        int count = 0;
        for (ActivePlayback activePlayback : activePlaybacks.values()) {
            if (activePlayback.route().ownerId().filter(ownerId::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    public Optional<PlaybackFailure> heldFailure(RouteId routeId) {
        return Optional.ofNullable(activePlaybacks.get(routeId)).flatMap(ActivePlayback::heldFailure);
    }

    public Optional<CargoWaitTarget> heldCargoFailureTarget(RouteId routeId) {
        return heldCargoFailureContext(routeId).map(CargoFailureContext::target);
    }

    public Optional<CargoFailureContext> heldCargoFailureContext(RouteId routeId) {
        return Optional.ofNullable(activePlaybacks.get(routeId)).flatMap(ActivePlayback::heldCargoFailureContext);
    }

    public boolean canSkipCurrentStop(RouteId routeId) {
        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        return activePlayback != null && activePlayback.isWaiting();
    }

    public boolean skipCurrentStop(ServerLevel level, RouteId routeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routeId, "routeId");

        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        if (activePlayback == null || !activePlayback.isWaiting()) {
            return false;
        }
        boolean terminalScheduleStop = activePlayback.waitingStop()
                .filter(stop -> activePlayback.route().playbackMode()
                        == net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode.ONE_WAY)
                .filter(stop -> stop.pointIndex() == activePlayback.route().points().size() - 1)
                .filter(stop -> activePlayback.targetIndex() == stop.pointIndex())
                .isPresent();
        if (!terminalScheduleStop) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Refused schedule stop skip for playback {} because the current wait is not a terminal schedule stop",
                    routeId.value()
            );
            return false;
        }
        Optional<AirshipStationBlockEntity> station = stationAt(level, activePlayback.stationPos());
        if (station.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Refused schedule stop skip for playback {} because its station context is unavailable",
                    routeId.value()
            );
            return false;
        }
        VehicleController controller = activePlayback.controller(level);
        if (!controller.isLoaded(level) || !controller.isAssembled()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Refused schedule stop skip for playback {} because its live vehicle is unavailable",
                    routeId.value()
            );
            return false;
        }

        PlaybackFailure failure = finishWaiting(level, station.get(), activePlayback, controller);
        if (failure != null || !activePlayback.completed()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Refused schedule stop skip for playback {} because the current wait is not a terminal schedule stop; failure={}",
                    routeId.value(),
                    failure
            );
            return false;
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Completed terminal schedule wait through normal playback handoff after explicit skip: route={}",
                routeId.value()
        );
        return true;
    }

    public boolean holdPlayback(ServerLevel level, RouteId routeId, PlaybackFailure failure) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(failure, "failure");

        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        if (activePlayback == null) {
            return false;
        }
        holdFault(level, activePlayback, failure);
        return true;
    }

    public boolean pauseRuntimePlayback(MinecraftServer server, RouteId routeId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(routeId, "routeId");

        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        if (activePlayback == null) {
            return false;
        }
        if (activePlayback.isPaused()) {
            return true;
        }
        ServerLevel level = server.getLevel(activePlayback.route().dimension());
        if (level == null) {
            return false;
        }
        setVisualsActive(level, activePlayback, false);
        activePlayback.pauseManual();
        activePlayback.controller(level).hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            station.holdPlayback(activePlayback.route(), false, Optional.empty());
        });
        return true;
    }

    public boolean resumeHeldPlayback(ServerLevel level, RouteId routeId) {
        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        if (activePlayback == null || !activePlayback.isPaused()) {
            return false;
        }
        if (!canResumeFaultHoldAtCurrentPosition(level, activePlayback)) {
            return false;
        }
        if (activePlayback.heldFailure().filter(failure -> failure == PlaybackFailure.STATION_MISSING).isPresent()) {
            Optional<AirshipStationBlockEntity> station = stationAt(level, activePlayback.stationPos());
            if (station.isEmpty()) {
                return false;
            }
            if (activePlayback.requiresDockLock() && dockingStation(level, station.get(), activePlayback).isEmpty()) {
                return false;
            }
        }
        resumePausedPlayback(level, activePlayback);
        return true;
    }

    private boolean canResumeFaultHoldAtCurrentPosition(ServerLevel level, ActivePlayback activePlayback) {
        if (!activePlayback.isFaultHold()) {
            return true;
        }

        VehicleController controller = activePlayback.controller(level);
        if (!controller.isLoaded(level) || !controller.isAssembled()) {
            holdFault(level, activePlayback, PlaybackFailure.VEHICLE_MISSING);
            return false;
        }
        if (!controller.isAutomationCapable()) {
            holdFault(level, activePlayback, PlaybackFailure.MISSING_CONTROLLER);
            return false;
        }

        Vec3 expectedRoutePosition = activePlayback.restoreCatchPosition();
        double distance = controller.position().distanceTo(expectedRoutePosition);
        double maxResumeDistance = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        if (distance <= maxResumeDistance) {
            return true;
        }

        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                "Playback {} refused fault-hold resume because loaded ship is too far from expected route pose: distance={} max={} shipPosition={} expectedRoutePosition={} heldFailure={} {}",
                activePlayback.route().id().value(),
                distance,
                maxResumeDistance,
                controller.position(),
                expectedRoutePosition,
                activePlayback.heldFailure().map(Enum::name).orElse("none"),
                stationContextDiagnostic(level, activePlayback)
        );
        holdFault(level, activePlayback, PlaybackFailure.START_TOO_FAR_FROM_ROUTE);
        return false;
    }

    public boolean releaseHeldPlayback(ServerLevel level, RouteId routeId) {
        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        if (activePlayback == null || !activePlayback.isPaused()) {
            return false;
        }
        activePlayback.releaseFromHold();
        setVisualsActive(level, activePlayback, false);
        activePlayback.controller(level).stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station ->
                station.holdPlayback(activePlayback.route(), false, activePlayback.heldFailure().map(PlaybackFailure::failureReason)));
        return true;
    }

    public int stopLinkedPlaybacks(ServerLevel level, net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef controllerRef, FailureReason reason) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controllerRef, "controllerRef");
        Objects.requireNonNull(reason, "reason");

        int stopped = 0;
        java.util.List<RouteId> activeRouteIds = activePlaybacks.values().stream()
                .filter(activePlayback -> activePlayback.route().dimension().equals(level.dimension()))
                .filter(activePlayback -> activePlayback.route().linkedController().matches(controllerRef))
                .map(activePlayback -> activePlayback.route().id())
                .toList();
        for (RouteId routeId : activeRouteIds) {
            stopPlayback(level, routeId, reason);
            stopped++;
        }

        Iterator<Map.Entry<RouteId, CompoundTag>> iterator = pendingRuntimePlaybacks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RouteId, CompoundTag> entry = iterator.next();
            Optional<Route> route = entry.getValue().contains(ROUTE, Tag.TAG_COMPOUND)
                    ? RouteNbtSerializer.read(entry.getValue().getCompound(ROUTE))
                    : Optional.empty();
            if (route.isEmpty()
                    || !route.get().dimension().equals(level.dimension())
                    || !route.get().linkedController().matches(controllerRef)) {
                continue;
            }
            iterator.remove();
            pendingRuntimeRestoreCooldowns.remove(entry.getKey());
            stopped++;
        }
        return stopped;
    }

    public int stopLinkedPlaybacks(
            ServerLevel level,
            Optional<net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef> controllerRef,
            Optional<UUID> runtimeShipId,
            FailureReason reason
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controllerRef, "controllerRef");
        Objects.requireNonNull(runtimeShipId, "runtimeShipId");
        Objects.requireNonNull(reason, "reason");

        if (controllerRef.isPresent()) {
            return stopLinkedPlaybacks(level, controllerRef.get(), reason);
        }
        if (runtimeShipId.isEmpty()) {
            return 0;
        }

        int stopped = 0;
        UUID vehicleId = runtimeShipId.get();
        java.util.List<RouteId> activeRouteIds = activePlaybacks.values().stream()
                .filter(activePlayback -> activePlayback.route().dimension().equals(level.dimension()))
                .filter(activePlayback -> activePlayback.route().linkedController().vehicleId().filter(vehicleId::equals).isPresent())
                .map(activePlayback -> activePlayback.route().id())
                .toList();
        for (RouteId routeId : activeRouteIds) {
            stopPlayback(level, routeId, reason);
            stopped++;
        }

        Iterator<Map.Entry<RouteId, CompoundTag>> iterator = pendingRuntimePlaybacks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RouteId, CompoundTag> entry = iterator.next();
            Optional<Route> route = entry.getValue().contains(ROUTE, Tag.TAG_COMPOUND)
                    ? RouteNbtSerializer.read(entry.getValue().getCompound(ROUTE))
                    : Optional.empty();
            if (route.isEmpty()
                    || !route.get().dimension().equals(level.dimension())
                    || !route.get().linkedController().vehicleId().filter(vehicleId::equals).isPresent()) {
                continue;
            }
            iterator.remove();
            pendingRuntimeRestoreCooldowns.remove(entry.getKey());
            terminalRuntimeFailures.remove(entry.getKey());
            stopped++;
        }
        return stopped;
    }

    public void holdUnscheduledPlaybacks(MinecraftServer server, Set<RouteId> scheduledRouteIds, PlaybackFailure failure) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(scheduledRouteIds, "scheduledRouteIds");
        Objects.requireNonNull(failure, "failure");

        for (ActivePlayback activePlayback : activePlaybacks.values()) {
            if (scheduledRouteIds.contains(activePlayback.route().id()) || activePlayback.isPaused()) {
                continue;
            }
            ServerLevel level = server.getLevel(activePlayback.route().dimension());
            if (level == null) {
                continue;
            }
            holdFault(level, activePlayback, failure);
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Held orphan route playback {} because it is not owned by an active schedule",
                    activePlayback.route().id().value()
            );
        }
    }

    public void stopUnscheduledPlaybacks(MinecraftServer server, Set<RouteId> scheduledRouteIds, FailureReason reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(scheduledRouteIds, "scheduledRouteIds");
        Objects.requireNonNull(reason, "reason");

        java.util.List<RouteId> orphanedActiveRouteIds = activePlaybacks.values().stream()
                .map(activePlayback -> activePlayback.route().id())
                .filter(routeId -> !scheduledRouteIds.contains(routeId))
                .toList();
        for (RouteId routeId : orphanedActiveRouteIds) {
            ActivePlayback activePlayback = activePlaybacks.get(routeId);
            if (activePlayback == null) {
                continue;
            }
            ServerLevel level = server.getLevel(activePlayback.route().dimension());
            if (level == null) {
                continue;
            }
            stopPlayback(level, routeId, reason);
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Stopped orphan route playback {} because it is not owned by an active schedule",
                    routeId.value()
            );
        }

        Iterator<Map.Entry<RouteId, CompoundTag>> pendingIterator = pendingRuntimePlaybacks.entrySet().iterator();
        while (pendingIterator.hasNext()) {
            Map.Entry<RouteId, CompoundTag> entry = pendingIterator.next();
            if (scheduledRouteIds.contains(entry.getKey())) {
                continue;
            }
            pendingIterator.remove();
            pendingRuntimeRestoreCooldowns.remove(entry.getKey());
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Dropped orphan pending route playback {} because it is not owned by an active schedule",
                    entry.getKey().value()
            );
        }
    }

    public Set<UUID> activeVisualShipIds() {
        return Set.copyOf(activeVisualShipIds);
    }

    public List<RuntimePlaybackSummary> runtimePlaybackSummaries(MinecraftServer server) {
        Objects.requireNonNull(server, "server");

        List<RuntimePlaybackSummary> summaries = new java.util.ArrayList<>();
        for (ActivePlayback activePlayback : activePlaybacks.values()) {
            summaries.add(RuntimePlaybackSummary.fromActive(
                    activePlayback,
                    shipNameFor(activePlayback.route()),
                    transponderIdFor(activePlayback.route()),
                    transponderPosFor(activePlayback.route())
            ));
        }
        for (Map.Entry<RouteId, CompoundTag> entry : pendingRuntimePlaybacks.entrySet()) {
            RuntimePlaybackSummary.fromPending(
                    entry.getKey(),
                    entry.getValue(),
                    pendingRuntimeRestoreCooldowns.getOrDefault(entry.getKey(), 0),
                    this::shipNameFor,
                    this::transponderIdFor,
                    this::transponderPosFor
            ).ifPresent(summaries::add);
        }
        return summaries.stream()
                .sorted(java.util.Comparator
                        .comparing(RuntimePlaybackSummary::routeName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(summary -> summary.routeId().value().toString()))
                .toList();
    }

    public boolean stopRuntimePlayback(MinecraftServer server, RouteId routeId, FailureReason reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(reason, "reason");

        ActivePlayback activePlayback = activePlaybacks.get(routeId);
        if (activePlayback != null) {
            ServerLevel level = server.getLevel(activePlayback.route().dimension());
            if (level != null) {
                stopPlayback(level, routeId, reason);
            } else {
                pendingRuntimePlaybacks.remove(routeId);
                pendingRuntimeRestoreCooldowns.remove(routeId);
                terminalRuntimeFailures.remove(routeId);
                deferredDockOutputClears.remove(routeId);
                activePlaybacks.remove(routeId);
                activePlayback.route().linkedController().vehicleId().ifPresent(activeVisualShipIds::remove);
            }
            return true;
        }

        if (pendingRuntimePlaybacks.remove(routeId) != null) {
            pendingRuntimeRestoreCooldowns.remove(routeId);
            terminalRuntimeFailures.remove(routeId);
            deferredDockOutputClears.remove(routeId);
            return true;
        }
        return false;
    }

    private void cleanupStaleStoredPointersForLoadedSableBody(
            ServerLevel level,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        if (!activePlayback.needsStoredPointerCleanup()) {
            return;
        }
        if (!activePlayback.route().linkedController().controllerType().equals(SableSubLevelVehicleController.TYPE)) {
            activePlayback.markStoredPointerCleanupComplete();
            return;
        }
        Optional<UUID> shipId = activePlayback.route().linkedController().vehicleId();
        if (shipId.isEmpty()) {
            activePlayback.markStoredPointerCleanupComplete();
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Refused stale Sable holding pointer cleanup for playback {} because the controller reference has no Sable ship id",
                    activePlayback.route().id().value()
            );
            return;
        }
        if (!controller.isLoaded(level) || !controller.isAssembled()) {
            activePlayback.markStoredPointerCleanupNeeded();
            return;
        }

        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                "Refused automatic stale Sable holding pointer cleanup for playback {} ship {} because live-body confirmation is not proof that stored data is safe to delete",
                activePlayback.route().id().value(),
                shipId.get()
        );
        activePlayback.markStoredPointerCleanupComplete();
    }

    public void deferDockOutputClear(
            RouteId nextRouteId,
            Route completedRoute,
            BlockPos fallbackStationPos,
            Optional<BlockPos> activeDockStationPos
    ) {
        Objects.requireNonNull(nextRouteId, "nextRouteId");
        Objects.requireNonNull(completedRoute, "completedRoute");
        Objects.requireNonNull(fallbackStationPos, "fallbackStationPos");
        Objects.requireNonNull(activeDockStationPos, "activeDockStationPos");

        deferredDockOutputClears.put(
                nextRouteId,
                new DeferredDockOutputClear(completedRoute, fallbackStationPos.immutable(), activeDockStationPos.map(BlockPos::immutable))
        );
    }

    public void handoffDockReservation(
            ServerLevel level,
            RouteId completedRouteId,
            RouteId nextRouteId,
            BlockPos fallbackStationPos,
            Optional<BlockPos> activeDockStationPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(completedRouteId, "completedRouteId");
        Objects.requireNonNull(nextRouteId, "nextRouteId");
        Objects.requireNonNull(fallbackStationPos, "fallbackStationPos");
        Objects.requireNonNull(activeDockStationPos, "activeDockStationPos");

        DockingRuntime.DockReservationTransferResult transfer =
                DockingRuntime.transferReservation(completedRouteId, nextRouteId);
        ActivePlayback nextPlayback = activePlaybacks.get(nextRouteId);
        if (!transfer.transferred()) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation handoff skipped release seeding because completed route {} did not hold a dock reservation for next route {}",
                    completedRouteId.value(),
                    nextRouteId.value()
            );
            return;
        }
        if (nextPlayback == null) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation handoff skipped release seeding because next playback {} is not active",
                    nextRouteId.value()
            );
            return;
        }

        Vec3 releaseOrigin = transfer.stationDockPos()
                .map(Vec3::atCenterOf)
                .orElseGet(nextPlayback::currentLegStartPosition);
        nextPlayback.trackDockReservationReleaseFromCurrentLeg(releaseOrigin, transfer.stationDockPos());
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation handoff seeded release tracking: completedRoute={} nextRoute={} stationDock={} releaseOrigin={} releasePoint={}",
                completedRouteId.value(),
                nextRouteId.value(),
                transfer.stationDockPos().map(BlockPos::toShortString).orElse(fallbackStationPos.toShortString()),
                releaseOrigin,
                nextPlayback.dockReservationReleasePointSummary()
        );
    }

    private PlaybackFailure tickOne(ServerLevel level, ActivePlayback activePlayback) {
        Optional<AirshipStationBlockEntity> station = stationAt(level, activePlayback.stationPos());
        VehicleController controller = activePlayback.controller(level);
        cleanupStaleStoredPointersForLoadedSableBody(level, activePlayback, controller);
        if (activePlayback.isPaused()) {
            return tickPaused(level, station, activePlayback, controller);
        }
        if (station.isEmpty() && !activePlayback.isRestoring()) {
            return PlaybackFailure.STATION_MISSING;
        }
        if (!controller.isLoaded(level)) {
            requestStationInteractionLoading(level, station, activePlayback, "stored_body_materialization");
            activePlayback.markStoredPointerCleanupNeeded();
            return tickUnloadedTransit(level, station, activePlayback);
        }
        if (!controller.isAssembled()) {
            if (activePlayback.tickRestoreGrace()) {
                return null;
            }
            return PlaybackFailure.VEHICLE_MISSING;
        }
        if (activePlayback.isUnloadedTransit()) {
            requestStationInteractionLoading(level, station, activePlayback, "unloaded_transit_restore");
            if (!activePlayback.isRestoring()) {
                Vec3 restorePosition = activePlayback.restoreCatchPosition();
                Optional<RouteRotation> restoreRotation = activePlayback.restoreCatchRotation();
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} rehydrating from unloaded transit at point {} using {} restorePosition={} guidance={} target={} controllerWorld={} restoreOffset={} guidanceOffset={} {}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        activePlayback.restoreCatchMode(),
                        restorePosition,
                        activePlayback.guidancePosition(),
                        activePlayback.targetPosition(),
                        controller.position(),
                        formatVec3Offset(controller.position(), restorePosition),
                        formatVec3Offset(controller.position(), activePlayback.guidancePosition()),
                        stationContextDiagnostic(level, activePlayback)
                );
                activePlayback.beginRestoreCatch();
                controller.relocate(level, restorePosition, restoreRotation);
            }
        }
        if (activePlayback.isRestoring()) {
            requestStationInteractionLoading(level, station, activePlayback, "restore_catch");
            Vec3 catchPosition = activePlayback.restoreCatchPosition();
            Optional<RouteRotation> catchRotation = activePlayback.restoreCatchRotation();
            double catchDistance = controller.position().distanceTo(catchPosition);
            if (catchDistance > RESTORE_RELOCATE_DISTANCE) {
                Vec3 controllerBefore = controller.position();
                controller.relocate(level, catchPosition, catchRotation);
                catchDistance = controller.position().distanceTo(catchPosition);
                activePlayback.resetProgress(catchDistance);
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Playback {} teleported to restore catch pose because physical catch-up exceeded safe distance: distance={} threshold={} restoreMode={} restorePosition={} controllerBefore={} controllerAfter={}",
                        activePlayback.route().id().value(),
                        controllerBefore.distanceTo(catchPosition),
                        RESTORE_RELOCATE_DISTANCE,
                        activePlayback.restoreCatchMode(),
                        catchPosition,
                        controllerBefore,
                        controller.position()
                );
            }
            VehicleMotionResult motionResult = controller.moveToward(
                    level,
                    catchPosition,
                    catchRotation,
                    AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                    RESTORE_CATCH_SPEED
            );
            activePlayback.tickRestoreGrace();
            if (motionResult.successful()) {
                catchDistance = controller.position().distanceTo(catchPosition);
                activePlayback.resetProgress(catchDistance);
            }
            boolean catchRotationAligned = activePlayback.isRotationAligned(catchRotation, controller);
            if (catchDistance > RESTORE_CATCH_COMPLETE_DISTANCE || !catchRotationAligned) {
                activePlayback.beginRestoreCatch();
                if (!catchRotationAligned && activePlayback.shouldLogProgress()) {
                    CreateAeronauticsAutomatedLogistics.debugPlayback(
                            "Playback {} continuing restore catch until recorded rotation is aligned at point {}: currentRotation={} targetRotation={}",
                            activePlayback.route().id().value(),
                            activePlayback.targetIndex(),
                            controller.routeRotation().map(RouteRotation::toString).orElse("missing"),
                            catchRotation.map(RouteRotation::toString).orElse("none")
                    );
                }
                return motionResult.failureReason()
                        .map(this::toPlaybackFailure)
                        .orElse(null);
            }
            activePlayback.cancelRestoreCatch();
            if (activePlayback.isUnloadedTransit()) {
                activePlayback.leaveUnloadedTransit();
                setVisualsActive(level, activePlayback, true);
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} finished rehydrate catch at point {} and resumed fully loaded playback",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex()
                );
            }
            Optional<PlaybackFailure> restoreQueueFailure = reacquireDockQueueAfterRestore(level, station, activePlayback, controller);
            if (restoreQueueFailure.isPresent()) {
                return restoreQueueFailure.get();
            }
            return null;
        }
        if (station.isEmpty()) {
            return PlaybackFailure.STATION_MISSING;
        }
        if (activePlayback.isDockQueueHeld()
                || !activePlayback.isWaiting() && activePlayback.dockReservationStopForCurrentLeg().isPresent()) {
            requestStationInteractionLoading(level, station, activePlayback, "dock_queue_hold");
            DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, controller);
            if (queueGate.failure().isPresent()) {
                return queueGate.failure().get();
            }
            if (queueGate.held()) {
                return null;
            }
        }
        if (activePlayback.isWaiting()) {
            requestStationInteractionLoading(level, station, activePlayback, "station_wait");
            return tickWaiting(level, station.get(), activePlayback, controller);
        }

        if (activePlayback.dockReservationStopForCurrentLeg().isPresent()) {
            requestStationInteractionLoading(level, station, activePlayback, "dock_approach");
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
        boolean aligningAtRecordedStopPose = activePlayback.requiresRotationAlignmentForArrival()
                && distanceToTarget <= arrivalDistance
                && !rotationAligned;
        if (aligningAtRecordedStopPose) {
            activePlayback.resetProgress(distanceToTarget);
        }
        if (!aligningAtRecordedStopPose
                && AutomatedLogisticsConfig.STOP_ON_COLLISION.get()
                && activePlayback.shouldWatchProgress()) {
            Optional<PlaybackFailure> stalled = activePlayback.checkStalled(distanceToTarget);
            if (stalled.isPresent()) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Playback {} is stuck toward point {}. distance={}, bestOverdueDistance={}, stalledTicks={}, segmentElapsedTicks={}, segmentDurationTicks={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        distanceToTarget,
                        activePlayback.previousDistance(),
                        activePlayback.stalledTicks(),
                        activePlayback.segmentElapsedTicks(),
                        activePlayback.segmentDurationTicks()
                );
                return stalled.get();
            }
        }
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
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} reached point {} at distance {} position={} target={}",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex(),
                    distanceToTarget,
                    controller.position(),
                    targetPosition
            );
            releaseDockReservationIfCleared(level, activePlayback);
            if (activePlayback.beginWaitAtCurrentTarget()) {
                return beginWaitAtTarget(level, station.get(), activePlayback, controller, targetPosition, distanceToTarget);
            }
            if (activePlayback.isComplete()) {
                complete(level, activePlayback);
                return null;
            }
            activePlayback.advanceTarget();
            DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, controller);
            if (queueGate.failure().isPresent()) {
                return queueGate.failure().get();
            }
            if (queueGate.held()) {
                return null;
            }
            if (!validateCurrentLegForDeparture(level, activePlayback)) {
                return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
            }
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
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} accepted endpoint {} after {} settle ticks at distance {} position={} target={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        activePlayback.endpointSettleTicks(),
                        distanceToTarget,
                        controller.position(),
                        targetPosition
                );
                releaseDockReservationIfCleared(level, activePlayback);
                if (activePlayback.beginWaitAtCurrentTarget()) {
                    return beginWaitAtTarget(level, station.get(), activePlayback, controller, targetPosition, distanceToTarget);
                }
                if (activePlayback.isComplete()) {
                    complete(level, activePlayback);
                    return null;
                }
                activePlayback.advanceTarget();
                DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, controller);
                if (queueGate.failure().isPresent()) {
                    return queueGate.failure().get();
                }
                if (queueGate.held()) {
                    return null;
                }
                if (!validateCurrentLegForDeparture(level, activePlayback)) {
                    return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
                }
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
        }

        if (aligningAtRecordedStopPose) {
            guidancePosition = targetPosition;
            guidanceRotation = targetRotation;
        }

        Vec3 collisionTargetPosition = guidancePosition;
        if (AutomatedLogisticsConfig.STOP_ON_COLLISION.get()
                && controller.collisionEntity().map(entity -> willCollide(level, entity, collisionTargetPosition)).orElse(false)) {
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }

        double targetSpeed = activePlayback.targetSpeedBlocksPerTick();
        if (activePlayback.shouldLogProgress()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} tick {} point {} distance={} targetSpeed={} position={} guidance={} target={} targetOffset={} guidanceOffset={}",
                    activePlayback.route().id().value(),
                    activePlayback.playbackTicks(),
                    activePlayback.targetIndex(),
                    distanceToTarget,
                    targetSpeed,
                    controller.position(),
                    guidancePosition,
                    targetPosition,
                    formatVec3Offset(controller.position(), targetPosition),
                    formatVec3Offset(controller.position(), guidancePosition)
            );
        }

        VehicleMotionResult motionResult = controller.moveToward(
                level,
                guidancePosition,
                guidanceRotation,
                AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                targetSpeed
        );
        clearDeferredDockOutputs(level, activePlayback.route().id());
        activePlayback.tickSegment();
        activePlayback.tickRouteStartStabilization();
        return motionResult.failureReason()
                .map(this::toPlaybackFailure)
                .orElse(null);
    }

    private PlaybackFailure tickUnloadedTransit(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> station,
            ActivePlayback activePlayback
    ) {
        if (activePlayback.isWaiting()) {
            if (activePlayback.requiresStationContext()) {
                DockQueueGateResult unloadedDockGate = unloadedDockWaitGate(level, station, activePlayback);
                if (unloadedDockGate.failure().isPresent()) {
                    return unloadedDockGate.failure().get();
                }
                if (unloadedDockGate.held()) {
                    return null;
                }
                UnloadedMaterializationAttempt materialization = tryMaterializeUnloadedShip(level, activePlayback, false);
                if (materialization == UnloadedMaterializationAttempt.MATERIALIZED
                        || materialization == UnloadedMaterializationAttempt.PENDING) {
                    if (activePlayback.shouldLogProgress()) {
                        CreateAeronauticsAutomatedLogistics.debugPlayback(
                                "Playback {} keeping unloaded station-context wait active at stop {} point={} while Sable materialization is {}. {} {}",
                                activePlayback.route().id().value(),
                                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                                activePlayback.targetIndex(),
                                materialization.name().toLowerCase(Locale.ROOT),
                                playbackPositionSummary(activePlayback),
                                stationContextDiagnostic(level, activePlayback)
                        );
                    }
                    return null;
                }
                if (activePlayback.shouldLogProgress()) {
                    CreateAeronauticsAutomatedLogistics.debugPlayback(
                            "Playback {} holding unloaded at stop {} point={} because conditions require loaded station/ship context: {}. {} {}",
                            activePlayback.route().id().value(),
                            activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                            activePlayback.targetIndex(),
                            activePlayback.waitingStop()
                                    .map(VehicleRoutePlaybackService::routeStopConditionSummary)
                                    .orElse("none"),
                            playbackPositionSummary(activePlayback),
                            stationContextDiagnostic(level, activePlayback)
                    );
                }
                return PlaybackFailure.VEHICLE_UNLOADED;
            }
            if (activePlayback.shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} advancing unloaded non-physical wait at stop {} point={} conditions={}. {}",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                        activePlayback.targetIndex(),
                        activePlayback.waitingStop()
                                .map(VehicleRoutePlaybackService::routeStopConditionSummary)
                                .orElse("none"),
                        playbackPositionSummary(activePlayback)
                );
            }
            ConditionTickResult conditionResult = tickConditionGroups(level, Optional.empty(), activePlayback);
            if (conditionResult.satisfied()) {
                return finishUnloadedWaiting(level, station, activePlayback);
            }
            if (conditionResult.failure().isPresent()) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Playback {} unloaded wait at stop {} failed with {}",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                        conditionResult.failure().get()
                );
            }
            return conditionResult.failure().orElse(null);
        }
        if (activePlayback.isDockQueueHeld()) {
            if (station.isEmpty()) {
                return PlaybackFailure.STATION_MISSING;
            }
            DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, null, false);
            if (queueGate.failure().isPresent()) {
                return queueGate.failure().get();
            }
            if (queueGate.held()) {
                return null;
            }
        }
        if (activePlayback.isRestoring()) {
            activePlayback.cancelRestoreCatch();
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} lost loaded rehydrate mid-catch at point {}; resuming simulated unloaded transit",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex()
            );
        }
        if (!activePlayback.isUnloadedTransit()) {
            if (!validateCurrentLegForDeparture(level, activePlayback)) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Playback {} could not enter unloaded transit because current leg validation failed at point {}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex()
                );
                return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
            }
            activePlayback.enterUnloadedTransit();
            setVisualsActive(level, activePlayback, false);
            station.ifPresent(value -> value.startPlayback(activePlayback.route()));
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} entered unloaded transit: leg {} -> {} duration={}ticks guidance={} target={} {}",
                    activePlayback.route().id().value(),
                    activePlayback.validatedLegStartIndex(),
                    activePlayback.validatedLegTargetIndex(),
                    activePlayback.segmentDurationTicks(),
                    activePlayback.guidancePosition(),
                    activePlayback.targetPosition(),
                    stationContextDiagnostic(level, activePlayback)
            );
        }

        activePlayback.tickSegment();
        activePlayback.resetProgress(0.0D);
        if (!activePlayback.isCurrentSegmentElapsed()) {
            return null;
        }

        if (activePlayback.beginWaitAtCurrentTarget()
                || activePlayback.isComplete()
                || activePlayback.isPreciseArrivalPoint(activePlayback.targetIndex())) {
            releaseDockReservationIfCleared(level, activePlayback);
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} unloaded transit reached hold point at {} waiting={} complete={} preciseArrival={} {} {}",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex(),
                    activePlayback.isWaiting(),
                    activePlayback.isComplete(),
                    activePlayback.isPreciseArrivalPoint(activePlayback.targetIndex()),
                    playbackPositionSummary(activePlayback),
                    stationContextDiagnostic(level, activePlayback)
            );
            DockQueueGateResult unloadedDockGate = unloadedDockWaitGate(level, station, activePlayback);
            if (unloadedDockGate.failure().isPresent()) {
                return unloadedDockGate.failure().get();
            }
            if (unloadedDockGate.held()) {
                return null;
            }
            UnloadedMaterializationAttempt materialization = tryMaterializeUnloadedShip(level, activePlayback, true);
            if (materialization == UnloadedMaterializationAttempt.MATERIALIZED
                    || materialization == UnloadedMaterializationAttempt.PENDING) {
                if (materialization == UnloadedMaterializationAttempt.PENDING) {
                    CreateAeronauticsAutomatedLogistics.debugPlayback(
                            "Playback {} reached unloaded hold point {} and is keeping runtime active while Sable materialization is pending. {} {}",
                            activePlayback.route().id().value(),
                            activePlayback.targetIndex(),
                            playbackPositionSummary(activePlayback),
                            stationContextDiagnostic(level, activePlayback)
                    );
                }
                return null;
            }
            logUnloadedHoldAwaitingLoadedShip(level, activePlayback);
            return PlaybackFailure.VEHICLE_UNLOADED;
        }

        activePlayback.advanceTarget();
        if (station.isPresent()) {
            DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, null, false);
            if (queueGate.failure().isPresent()) {
                return queueGate.failure().get();
            }
            if (queueGate.held()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} paused unloaded transit at dock queue gate point={} targetPoint={}",
                        activePlayback.route().id().value(),
                        activePlayback.currentLegStartIndex(),
                        activePlayback.targetIndex()
                );
                return null;
            }
        }
        if (!validateCurrentLegForDeparture(level, activePlayback)) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} unloaded transit advanced to point {} but leg validation failed",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex()
            );
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} unloaded transit advanced to leg {} -> {} guidance={} target={} {}",
                activePlayback.route().id().value(),
                activePlayback.validatedLegStartIndex(),
                activePlayback.validatedLegTargetIndex(),
                activePlayback.guidancePosition(),
                activePlayback.targetPosition(),
                stationContextDiagnostic(level, activePlayback)
        );
        return null;
    }

    private enum UnloadedMaterializationAttempt {
        MATERIALIZED,
        PENDING,
        FAILED
    }

    private DockQueueGateResult unloadedDockWaitGate(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> fallbackStation,
            ActivePlayback activePlayback
    ) {
        if (!activePlayback.isWaiting() || !activePlayback.requiresDockLock()) {
            return DockQueueGateResult.ready();
        }
        Optional<RouteStop> waitingStop = activePlayback.waitingStop();
        if (waitingStop.isEmpty()) {
            return DockQueueGateResult.ready();
        }
        Optional<AirshipStationBlockEntity> dockingStation = resolveDockingStation(
                level,
                waitingStop.get(),
                fallbackStation,
                activePlayback.targetStationPos()
        );
        if (dockingStation.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} cannot gate unloaded dock wait at stop {} because the docking station is not loaded. {}",
                    activePlayback.route().id().value(),
                    waitingStop.get().name(),
                    stationContextDiagnostic(level, activePlayback)
            );
            return DockQueueGateResult.failed(PlaybackFailure.STATION_MISSING);
        }

        DockingRuntime.DockReservationResult reservation =
                DockingRuntime.requestApproachReservation(level, dockingStation.get(), activePlayback.route());
        if (reservation.failure().isPresent()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} unloaded dock wait reservation failed before materialization at stop {} failure={} {}",
                    activePlayback.route().id().value(),
                    waitingStop.get().name(),
                    reservation.failure().get(),
                    stationContextDiagnostic(level, activePlayback)
            );
            return DockQueueGateResult.failed(reservation.failure().get());
        }
        if (reservation.granted()) {
            if (activePlayback.isDockQueueHeld()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} unloaded dock queue released before materialization at stop {} queuePosition={} {}",
                        activePlayback.route().id().value(),
                        waitingStop.get().name(),
                        reservation.queuePosition(),
                        stationContextDiagnostic(level, activePlayback)
                );
            }
            activePlayback.clearDockQueueHold();
            return DockQueueGateResult.ready();
        }

        if (!activePlayback.isDockQueueHeld()) {
            activePlayback.markDockQueueHold(
                    waitingStop.get(),
                    activePlayback.dockQueueHoldPosition(null, false),
                    activePlayback.dockQueueHoldRotation(null, false),
                    false
            );
        }
        dockingStation.get().dockQueuePlayback(activePlayback.route());
        if (activePlayback.shouldLogProgress()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} staying stored/unloaded for dock queue before materialization at stop {} queuePosition={} holdPosition={} {}",
                    activePlayback.route().id().value(),
                    waitingStop.get().name(),
                    reservation.queuePosition(),
                    activePlayback.holdPosition(),
                    stationContextDiagnostic(level, activePlayback)
            );
        }
        return DockQueueGateResult.queued();
    }

    private UnloadedMaterializationAttempt tryMaterializeUnloadedShip(
            ServerLevel level,
            ActivePlayback activePlayback,
            boolean forceAtHoldPoint
    ) {
        Optional<UUID> subLevelId = activePlayback.route().linkedController().vehicleId();
        Optional<BlockPos> localControllerPos = activePlayback.route().linkedController().controllerPos();
        if (subLevelId.isEmpty() || localControllerPos.isEmpty()) {
            if (forceAtHoldPoint || activePlayback.shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Playback {} cannot materialize unloaded ship at point {} because controller ref is incomplete: vehicleId={} localController={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        subLevelId.map(UUID::toString).orElse("missing"),
                        localControllerPos.map(BlockPos::toShortString).orElse("missing")
                );
            }
            return UnloadedMaterializationAttempt.FAILED;
        }

        Vec3 materializePosition = activePlayback.restoreCatchPosition();
        BlockPos materializeBlock = BlockPos.containing(materializePosition);
        if (!level.isLoaded(materializeBlock)) {
            if (forceAtHoldPoint || activePlayback.shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} cannot materialize unloaded ship at point {} yet because simulated route pose chunk is not loaded: position={} chunk={} forceAtHold={} {}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        materializePosition,
                        new ChunkPos(materializeBlock),
                        forceAtHoldPoint,
                        stationContextDiagnostic(level, activePlayback)
                );
            }
            return UnloadedMaterializationAttempt.PENDING;
        }

        if (!StationChunkLoadingService.isStartupRestoreReady()) {
            if (activePlayback.shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} is deferring unloaded ship materialization until startup restore is ready. point={} position={} chunk={} forceAtHold={}",
                        activePlayback.route().id().value(),
                        activePlayback.targetIndex(),
                        materializePosition,
                        new ChunkPos(materializeBlock),
                        forceAtHoldPoint
                );
            }
            return UnloadedMaterializationAttempt.PENDING;
        }

        if (!activePlayback.canAttemptUnloadedMaterialize(forceAtHoldPoint)) {
            return UnloadedMaterializationAttempt.PENDING;
        }

        String description = (forceAtHoldPoint ? "route hold point " : "loaded route pose ")
                + activePlayback.targetIndex()
                + " for playback "
                + activePlayback.route().id().value();
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} attempting to materialize unloaded ship {} at simulated {} pose position={} localController={} {}",
                activePlayback.route().id().value(),
                subLevelId.get(),
                activePlayback.restoreCatchMode(),
                materializePosition,
                localControllerPos.get(),
                stationContextDiagnostic(level, activePlayback)
        );
        ShipMaterializationService.MaterializationResult result = AutomatedLogisticsServices.MATERIALIZATION.materializeStoredBodyAt(
                new ShipMaterializationService.MaterializationRequest(
                        level.getServer(),
                        activePlayback.route().dimension(),
                        transponderIdFor(activePlayback.route()),
                        subLevelId,
                        Optional.of(activePlayback.route().id()),
                        Optional.of(activePlayback.targetIndex()),
                        Optional.empty(),
                        localControllerPos,
                        materializePosition,
                        description,
                        "unloaded_playback_materialization",
                        forceAtHoldPoint,
                        true
                )
        );
        if (result.success()) {
            SableSubLevelForceLoadService.holdForDockStop(
                    level,
                    subLevelId.get(),
                    activePlayback.route().id(),
                    "unloaded_materialization_success"
            );
            activePlayback.beginRestoreCatch();
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} materialized unloaded ship {} at simulated route pose: {}",
                    activePlayback.route().id().value(),
                    subLevelId.get(),
                    result.message()
            );
            return UnloadedMaterializationAttempt.MATERIALIZED;
        }
        if (isPendingUnloadedMaterialization(result.type())) {
            if (result.type() == ShipMaterializationService.MaterializationResultType.CONTROLLER_REGISTRATION_WAITING) {
                SableSubLevelForceLoadService.holdForDockStop(
                        level,
                        subLevelId.get(),
                        activePlayback.route().id(),
                        "controller_registration_waiting"
                );
            }
            if (forceAtHoldPoint || activePlayback.shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} is waiting for unloaded ship {} materialization at simulated route pose: type={} reason={} message={}",
                        activePlayback.route().id().value(),
                        subLevelId.get(),
                        result.type(),
                        result.reasonCode(),
                        result.message()
                );
            }
            return UnloadedMaterializationAttempt.PENDING;
        }
        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                "Playback {} failed to materialize unloaded ship {} at simulated route pose: {}",
                activePlayback.route().id().value(),
                subLevelId.get(),
                result.message()
        );
        return UnloadedMaterializationAttempt.FAILED;
    }

    private static boolean isPendingUnloadedMaterialization(ShipMaterializationService.MaterializationResultType type) {
        return switch (type) {
            case SABLE_HOLDING_BODY_WAITING,
                    CONTROLLER_REGISTRATION_WAITING,
                    CHUNK_LOAD_NOT_READY,
                    STARTUP_GRACE_WAITING -> true;
            default -> false;
        };
    }

    private PlaybackFailure finishUnloadedWaiting(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> station,
            ActivePlayback activePlayback
    ) {
        String stopName = activePlayback.waitingStop().map(RouteStop::name).orElse("unknown");
        int previousTargetIndex = activePlayback.targetIndex();
        activePlayback.clearWait();
        SableSubLevelForceLoadService.releaseDockStop(level, activePlayback.route(), "unloaded_wait_finished");
        if (activePlayback.isComplete()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} finished unloaded terminal wait at stop {} point={}",
                    activePlayback.route().id().value(),
                    stopName,
                    previousTargetIndex
            );
            complete(level, activePlayback);
            return null;
        }

        station.ifPresent(AirshipStationBlockEntity::resumePlayback);
        activePlayback.advanceTarget();
        if (!validateCurrentLegForDeparture(level, activePlayback)) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} could not continue unloaded after stop {} because next leg validation failed",
                    activePlayback.route().id().value(),
                    stopName
            );
            return PlaybackFailure.COLLISION_OR_OBSTRUCTION;
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} finished unloaded wait at stop {} point={} -> nextPoint={} guidance={} target={} {}",
                activePlayback.route().id().value(),
                stopName,
                previousTargetIndex,
                activePlayback.targetIndex(),
                activePlayback.guidancePosition(),
                activePlayback.targetPosition(),
                stationContextDiagnostic(level, activePlayback)
        );
        return null;
    }

    private PlaybackFailure tickPaused(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> station,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        if (activePlayback.isTransientHold()) {
            if (!controller.isLoaded(level)) {
                return null;
            }
            if (!controller.isAssembled()) {
                return PlaybackFailure.VEHICLE_MISSING;
            }
            resumePausedPlayback(level, activePlayback);
            return null;
        }

        if (activePlayback.isHoldLocked()) {
            if (!controller.isLoaded(level)) {
                return null;
            }
            if (!controller.isAssembled()) {
                return PlaybackFailure.VEHICLE_MISSING;
            }
            controller.hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
        }
        return null;
    }

    private PlaybackFailure tickWaiting(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        Vec3 targetPosition = activePlayback.targetPosition();
        Optional<RouteRotation> targetRotation = activePlayback.pointRotation(activePlayback.targetIndex());
        Optional<AirshipStationBlockEntity> dockingStation = Optional.empty();
        if (activePlayback.shouldLogWaitDiagnostic()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} ticking station wait: stop={} point={} requiresStationContext={} requiresDockLock={} dockLocked={} dockFailure={} reacquireMotion={} releasedControl={} dockStation={} targetDistance={} position={} target={} groups={} timers={}",
                    activePlayback.route().id().value(),
                    activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                    activePlayback.targetIndex(),
                    activePlayback.requiresStationContext(),
                    activePlayback.requiresDockLock(),
                    activePlayback.dockLocked(),
                    activePlayback.dockWaitFailure().map(Enum::name).orElse("none"),
                    activePlayback.isDockReacquireMotionActive(),
                    activePlayback.hasReleasedDockReacquireControl(),
                    activePlayback.activeDockStationPos().map(BlockPos::toShortString).orElse("none"),
                    controller.position().distanceTo(targetPosition),
                    controller.position(),
                    targetPosition,
                    activePlayback.conditionGroupSummary(),
                    activePlayback.conditionTimerSummary()
            );
        }

        if (activePlayback.requiresStationContext()) {
            dockingStation = dockingStation(level, station, activePlayback);
            if (dockingStation.isEmpty()) {
                if (activePlayback.requiresDockLock()) {
                    activePlayback.dockWaitFailure(Optional.of(PlaybackFailure.STATION_MISSING));
                }
            } else if (activePlayback.requiresDockLock()) {
                activePlayback.activeDockStationPos(Optional.of(dockingStation.get().getBlockPos().immutable()));
                if (activePlayback.dockWaitFailure().isEmpty()
                        && !activePlayback.dockLocked()) {
                    if (activePlayback.isDockReacquireMotionActive()) {
                        double reacquireDistance = controller.position().distanceTo(targetPosition);
                        boolean reacquireRotationAligned = activePlayback.isRotationAligned(targetRotation, controller);
                        double dockHandoffDistance = Math.max(activePlayback.arrivalDistance(), DOCK_REACQUIRE_HANDOFF_DISTANCE);
                        if (reacquireDistance > dockHandoffDistance || !reacquireRotationAligned) {
                            VehicleMotionResult motionResult = controller.moveToward(
                                    level,
                                    targetPosition,
                                    targetRotation,
                                    AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                                    activePlayback.targetSpeedBlocksPerTick()
                            );
                            activePlayback.resetProgress(controller.position().distanceTo(targetPosition));
                            activePlayback.resetDockTimeoutClock();
                            if (activePlayback.shouldLogProgress()) {
                                CreateAeronauticsAutomatedLogistics.debugPlayback(
                                        "Playback {} approaching dock stop {} before starting dock handshake: distance={} rotationAligned={} position={} target={} targetRotation={}",
                                        activePlayback.route().id().value(),
                                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                                        reacquireDistance,
                                        reacquireRotationAligned,
                                        controller.position(),
                                        targetPosition,
                                        targetRotation.map(RouteRotation::toString).orElse("none")
                                );
                            }
                            return motionResult.failureReason()
                                    .map(this::toPlaybackFailure)
                                    .orElse(null);
                        }
                        CreateAeronauticsAutomatedLogistics.debugPlayback(
                                "Playback {} reached dock reacquire handoff for stop {}: distance={} threshold={} rotationAligned={} position={} target={}",
                                activePlayback.route().id().value(),
                                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                                reacquireDistance,
                                dockHandoffDistance,
                                reacquireRotationAligned,
                                controller.position(),
                                targetPosition
                        );
                    }
                    if (activePlayback.isDockReacquireMotionActive()
                            && !activePlayback.hasReleasedDockReacquireControl()) {
                        Optional<PlaybackFailure> dockFailure = DockingRuntime.beginDockingWait(
                                level,
                                dockingStation.get(),
                                activePlayback.route()
                        );
                        if (dockFailure.isPresent()) {
                            activePlayback.dockWaitFailure(Optional.of(dockFailure.get()));
                            notifyOwnerIfShipNotLoadedForDocking(level, activePlayback, dockFailure.get());
                        } else {
                            SableSubLevelForceLoadService.holdForDockStop(
                                    level,
                                    activePlayback.route(),
                                    "loaded_docking_wait_reacquired"
                            );
                        }
                    }
                    DockingRuntime.DockingWaitResult docking = DockingRuntime.tickDockingWait(level, dockingStation.get(), activePlayback.route());
                    if (docking.failure().isPresent()) {
                        activePlayback.dockWaitFailure(Optional.of(docking.failure().get()));
                        notifyOwnerIfShipNotLoadedForDocking(level, activePlayback, docking.failure().get());
                    } else if (docking.locked()) {
                        activePlayback.dockLocked(true);
                        activePlayback.captureHoldPose(controller);
                        activePlayback.endDockReacquireMotion();
                        CreateAeronauticsAutomatedLogistics.debugPlayback(
                                "Playback {} docked at stop {} while evaluating grouped stop conditions; conditionTimers={}",
                                activePlayback.route().id().value(),
                                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                                activePlayback.conditionTimerSummary()
                        );
                        SableSubLevelForceLoadService.logLeaseDiagnostic(
                                level,
                                activePlayback.route(),
                                "dock_lock_acquired"
                        );
                    } else if (activePlayback.tickDockTimeout()) {
                        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                                "Playback {} physical dock lock timed out at stop {} after configuredTimeoutTicks={}. {}",
                                activePlayback.route().id().value(),
                                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                                AutomatedLogisticsConfig.DOCK_LOCK_TIMEOUT_TICKS.get(),
                                DockingRuntime.lockDiagnostic(level, dockingStation.get(), activePlayback.route())
                        );
                        activePlayback.dockWaitFailure(Optional.of(PlaybackFailure.DOCK_LOCK_FAILED));
                    }
                }
            }
        }

        if (activePlayback.requiresDockLock()
                && activePlayback.dockLocked()
                && dockingStation.isPresent()) {
            DockingRuntime.DockingWaitResult docking = DockingRuntime.tickDockingWait(level, dockingStation.get(), activePlayback.route());
            if (docking.failure().isPresent()) {
                activePlayback.dockWaitFailure(Optional.of(docking.failure().get()));
                notifyOwnerIfShipNotLoadedForDocking(level, activePlayback, docking.failure().get());
            } else if (!docking.locked()) {
                activePlayback.dockLocked(false);
                activePlayback.beginDockReacquireMotion();
                activePlayback.resetDockTimeoutClock();
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} restored dock lock was not confirmed at stop {}; reacquiring physical dock before ticking wait conditions. {}",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                        DockingRuntime.lockDiagnostic(level, dockingStation.get(), activePlayback.route())
                );
                return null;
            }
        }

        if (activePlayback.requiresDockLock()
                && !activePlayback.dockLocked()
                && activePlayback.isDockReacquireMotionActive()) {
            if (!activePlayback.hasReleasedDockReacquireControl()) {
                controller.stop(level);
                activePlayback.markDockReacquireControlReleased();
                activePlayback.resetProgress(controller.position().distanceTo(targetPosition));
            }
        } else {
            Vec3 waitHoldPosition = activePlayback.waitHoldPosition(controller);
            Optional<RouteRotation> waitHoldRotation = activePlayback.waitHoldRotation(controller);
            PlaybackFailure holdFailure = holdAtTarget(level, activePlayback, controller, waitHoldPosition, waitHoldRotation);
            if (holdFailure != null) {
                return holdFailure;
            }
        }

        if (activePlayback.requiresDockLock() && !activePlayback.dockLocked()) {
            if (activePlayback.dockWaitFailure().isPresent()) {
                return activePlayback.dockWaitFailure().get();
            }
            if (activePlayback.shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} holding dock stop {} until dock lock is established; grouped wait conditions will not tick yet",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
                );
            }
            return null;
        }

        ConditionTickResult conditionResult = tickConditionGroups(level, dockingStation, activePlayback);
        if (conditionResult.satisfied()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} finished grouped conditions at stop {}",
                activePlayback.route().id().value(),
                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
            );
            return finishWaiting(level, station, activePlayback, controller);
        }
        return conditionResult.failure().orElse(null);
    }

    private ConditionTickResult tickConditionGroups(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> dockingStation,
            ActivePlayback activePlayback
    ) {
        List<List<AirshipScheduleCondition>> groups = activePlayback.conditionGroups();
        boolean anyPendingGroup = false;
        Optional<PlaybackFailure> firstFailure = Optional.empty();

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            List<AirshipScheduleCondition> group = groups.get(groupIndex);
            boolean groupPending = false;
            boolean groupFailed = false;

            for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                AirshipScheduleCondition condition = group.get(conditionIndex);
                ConditionRuntimeState state = activePlayback.conditionState(groupIndex, conditionIndex, condition.waitCondition());
                ConditionTickResult result = tickCondition(level, dockingStation, activePlayback, condition.waitCondition(), state);
                if (result.failure().isPresent()) {
                    groupFailed = true;
                    if (firstFailure.isEmpty()) {
                        firstFailure = result.failure();
                    }
                    break;
                }
                if (!result.satisfied()) {
                    groupPending = true;
                }
            }

            if (!groupFailed && !groupPending) {
                return ConditionTickResult.completedResult();
            }
            if (!groupFailed) {
                anyPendingGroup = true;
            }
        }

        if (anyPendingGroup) {
            return ConditionTickResult.pendingResult();
        }
        return ConditionTickResult.failedResult(firstFailure.orElse(PlaybackFailure.INVALID_ROUTE));
    }

    private ConditionTickResult tickCondition(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> dockingStation,
            ActivePlayback activePlayback,
            WaitCondition waitCondition,
            ConditionRuntimeState state
    ) {
        if (state.satisfied) {
            return ConditionTickResult.completedResult();
        }
        if (state.failure.isPresent()) {
            return ConditionTickResult.failedResult(state.failure.get());
        }

        switch (waitCondition.type()) {
            case NONE -> {
                state.markSatisfied();
                return ConditionTickResult.completedResult();
            }
            case TIMED -> {
                if (state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case UNTIL_DOCKED -> {
                if (activePlayback.dockWaitFailure().isPresent()) {
                    state.markFailure(activePlayback.dockWaitFailure().get());
                    return ConditionTickResult.failedResult(activePlayback.dockWaitFailure().get());
                }
                if (!activePlayback.dockLocked()) {
                    return ConditionTickResult.pendingResult();
                }
                if (state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case UNTIL_IDLE -> {
                if (activePlayback.dockWaitFailure().isPresent()) {
                    state.markFailure(activePlayback.dockWaitFailure().get());
                    return ConditionTickResult.failedResult(activePlayback.dockWaitFailure().get());
                }
                if (!activePlayback.dockLocked()) {
                    return ConditionTickResult.pendingResult();
                }
                if (dockingStation.isEmpty()) {
                    state.markFailure(PlaybackFailure.STATION_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.STATION_MISSING);
                }
                Optional<DockTransferSnapshot> snapshot = DockingRuntime.transferSnapshot(level, dockingStation.get(), activePlayback.route());
                if (snapshot.isEmpty()) {
                    if (state.tickWait()) {
                        state.markSatisfied();
                        return ConditionTickResult.completedResult();
                    }
                    return ConditionTickResult.pendingResult();
                }
                if (state.dockTransferSnapshot.isEmpty()) {
                    state.dockTransferSnapshot = snapshot;
                } else if (!snapshot.get().equals(state.dockTransferSnapshot.get())) {
                    state.dockTransferSnapshot = snapshot;
                    state.resetIdleWait();
                    return ConditionTickResult.pendingResult();
                }
                if (state.tickIdleTimeout() || state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case REDSTONE_LINK, REDSTONE -> {
                if (waitCondition.redstoneFrequencyFirst().isEmpty() || waitCondition.redstoneFrequencySecond().isEmpty()) {
                    state.markFailure(PlaybackFailure.REDSTONE_LINK_UNCONFIGURED);
                    return ConditionTickResult.failedResult(PlaybackFailure.REDSTONE_LINK_UNCONFIGURED);
                }
                if (redstoneLinkSatisfied(waitCondition)) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case TIME_OF_DAY -> {
                if (timeOfDaySatisfied(level, waitCondition)) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL, UNTIL_EMPTY, UNTIL_FULL -> {
                if (activePlayback.dockWaitFailure().isPresent()) {
                    state.markFailure(activePlayback.dockWaitFailure().get());
                    return ConditionTickResult.failedResult(activePlayback.dockWaitFailure().get());
                }
                if (!activePlayback.dockLocked()) {
                    return ConditionTickResult.pendingResult();
                }
                if (waitCondition.cargoTarget() == CargoWaitTarget.STATION_CARGO && dockingStation.isEmpty()) {
                    state.markFailure(PlaybackFailure.STATION_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.STATION_MISSING);
                }
                Optional<List<LinkedCargoEntry>> targetEntries =
                        cargoEntries(level, dockingStation, activePlayback, waitCondition.cargoTarget());
                if (targetEntries.isEmpty()) {
                    CreateAeronauticsAutomatedLogistics.debugCargo(
                            "Cargo wait {} on playback {} failed: no target entries for {}",
                            waitCondition.type(),
                            activePlayback.route().id().value(),
                            waitCondition.cargoTarget()
                    );
                    state.markFailure(PlaybackFailure.CARGO_STORAGE_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_STORAGE_MISSING);
                }
                if (cargoStorageMissing(level, targetEntries.get(), waitCondition, activePlayback.route().id().value().toString())) {
                    state.markFailure(PlaybackFailure.CARGO_STORAGE_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_STORAGE_MISSING);
                }
                Optional<LinkedCargoSnapshot> snapshot = targetEntries
                        .map(entries -> LinkedCargoSnapshot.capture(level, entries))
                        .filter(captured -> relevantStoragePresent(captured, waitCondition));
                if (snapshot.isEmpty()) {
                    CreateAeronauticsAutomatedLogistics.debugCargo(
                            "Cargo wait {} on playback {} failed: snapshot had no relevant {} storage for entries {}",
                            waitCondition.type(),
                            activePlayback.route().id().value(),
                            waitCondition.type(),
                            summarizeCargoEntries(targetEntries.get())
                    );
                    state.markFailure(PlaybackFailure.CARGO_STORAGE_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_STORAGE_MISSING);
                }
                if (cargoConditionSatisfied(level, snapshot.get(), waitCondition)) {
                    if (state.tickWait()) {
                        state.markSatisfied();
                        return ConditionTickResult.completedResult();
                    }
                    return ConditionTickResult.pendingResult();
                }
                logCargoConditionPending(activePlayback, waitCondition, snapshot.get(), state, targetEntries.get());
                state.resetIdleWait();
                if (state.tickCargoTimeout()) {
                    state.markFailure(PlaybackFailure.CARGO_CONDITION_TIMEOUT);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_CONDITION_TIMEOUT);
                }
                return ConditionTickResult.pendingResult();
            }
            default -> {
                if (state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
        }
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

    private Optional<PlaybackFailure> tickDockCargoWait(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback
    ) {
        WaitCondition waitCondition = activePlayback.waitingStop()
                .map(RouteStop::waitCondition)
                .orElse(WaitCondition.none());
        Optional<List<LinkedCargoEntry>> targetEntries =
                cargoEntries(level, Optional.of(station), activePlayback, waitCondition.cargoTarget());
        if (targetEntries.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "Dock cargo wait {} on playback {} failed: no target entries for {}",
                    waitCondition.type(),
                    activePlayback.route().id().value(),
                    waitCondition.cargoTarget()
            );
            return Optional.of(PlaybackFailure.CARGO_STORAGE_MISSING);
        }
        if (cargoStorageMissing(level, targetEntries.get(), waitCondition, activePlayback.route().id().value().toString())) {
            return Optional.of(PlaybackFailure.CARGO_STORAGE_MISSING);
        }
        Optional<LinkedCargoSnapshot> snapshot = targetEntries
                .map(entries -> LinkedCargoSnapshot.capture(level, entries))
                .filter(captured -> relevantStoragePresent(captured, waitCondition));
        if (snapshot.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "Dock cargo wait {} on playback {} failed: snapshot had no relevant {} storage for entries {}",
                    waitCondition.type(),
                    activePlayback.route().id().value(),
                    waitCondition.type(),
                    summarizeCargoEntries(targetEntries.get())
            );
            return Optional.of(PlaybackFailure.CARGO_STORAGE_MISSING);
        }
        if (cargoConditionSatisfied(level, snapshot.get(), waitCondition)) {
            activePlayback.cargoSatisfiedThisTick(true);
            return Optional.empty();
        }
        logCargoConditionPending(
                activePlayback,
                waitCondition,
                snapshot.get(),
                activePlayback.conditionState(0, 0, waitCondition),
                targetEntries.get()
        );
        activePlayback.cargoSatisfiedThisTick(false);
        if (activePlayback.tickCargoTimeout()) {
            return Optional.of(PlaybackFailure.CARGO_CONDITION_TIMEOUT);
        }
        return Optional.empty();
    }

    private void logCargoConditionPending(
            ActivePlayback activePlayback,
            WaitCondition waitCondition,
            LinkedCargoSnapshot snapshot,
            ConditionRuntimeState state,
            List<LinkedCargoEntry> entries
    ) {
        if (!activePlayback.shouldLogProgress()) {
            return;
        }
        if (waitCondition.type() != WaitConditionType.UNTIL_ITEM_EMPTY
                && waitCondition.type() != WaitConditionType.UNTIL_FLUID_EMPTY
                && waitCondition.type() != WaitConditionType.UNTIL_EMPTY
                && waitCondition.type() != WaitConditionType.UNTIL_FULL
                && waitCondition.type() != WaitConditionType.UNTIL_ITEM_FULL
                && waitCondition.type() != WaitConditionType.UNTIL_FLUID_FULL) {
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugCargo(
                "Cargo wait {} on playback {} pending: target={} itemStorage={} fluidStorage={} totalItems={} totalFluids={} stabilityTicksRemaining={} cargoTimeoutTicksRemaining={} entries={}",
                waitCondition.type(),
                activePlayback.route().id().value(),
                waitCondition.cargoTarget(),
                snapshot.hasItemStorage(),
                snapshot.hasFluidStorage(),
                snapshot.totalItemCount(),
                snapshot.totalFluidAmount(),
                state.waitTicksRemaining,
                state.cargoTimeoutTicksRemaining,
                summarizeCargoEntries(entries)
        );
    }

    private PlaybackFailure beginWaitAtTarget(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActivePlayback activePlayback,
            VehicleController controller,
            Vec3 targetPosition,
            double distanceToTarget
    ) {
        if (activePlayback.isDockQueueHeld()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} clearing dock queue hold at stop {} before docking wait begins",
                    activePlayback.route().id().value(),
                    activePlayback.waitingStop().map(RouteStop::name).orElse("unknown")
            );
            activePlayback.clearDockQueueHold();
        }
        station.waitPlayback(activePlayback.route());
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} waiting at stop {} for {} ticks",
                activePlayback.route().id().value(),
                activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                activePlayback.waitTicksRemaining()
        );
        if (activePlayback.requiresDockLock()) {
            Optional<AirshipStationBlockEntity> dockingStation = dockingStation(level, station, activePlayback);
            if (dockingStation.isEmpty()) {
                return PlaybackFailure.STATION_MISSING;
            }
            activePlayback.activeDockStationPos(Optional.of(dockingStation.get().getBlockPos().immutable()));
            Optional<BlockPos> releaseDockPos = dockingStation.get()
                    .refreshGroundDockLink(level)
                    .dockPos()
                    .map(BlockPos::immutable);
            Vec3 dockReleaseOrigin = releaseDockPos
                    .map(Vec3::atCenterOf)
                    .orElse(targetPosition);
            activePlayback.waitingStop().ifPresent(
                    stop -> activePlayback.trackDockReservationRelease(stop, dockReleaseOrigin, releaseDockPos)
            );
            Optional<PlaybackFailure> dockFailure = DockingRuntime.beginDockingWait(level, dockingStation.get(), activePlayback.route());
            if (dockFailure.isPresent()) {
                return dockFailure.get();
            }
            SableSubLevelForceLoadService.holdForDockStop(
                    level,
                    activePlayback.route(),
                    "loaded_docking_wait_started"
            );
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
        String stopName = activePlayback.waitingStop().map(RouteStop::name).orElse("unknown");
        int previousTargetIndex = activePlayback.targetIndex();
        Vec3 positionBeforeAdvance = controller.position();
        boolean dockingWait = activePlayback.requiresDockLock();
        activePlayback.clearWait();
        if (dockingWait) {
            SableSubLevelForceLoadService.logLeaseDiagnostic(
                    level,
                    activePlayback.route(),
                    "loaded_wait_finishing_before_release"
            );
            SableSubLevelForceLoadService.releaseDockStop(level, activePlayback.route(), "loaded_wait_finished");
        }
        if (activePlayback.isComplete()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} finished terminal wait at stop {} point={} dockingWait={} position={} target={}",
                    activePlayback.route().id().value(),
                    stopName,
                    previousTargetIndex,
                    dockingWait,
                    positionBeforeAdvance,
                    activePlayback.targetPosition()
            );
            if (dockingWait) {
                clearDockOutputs(level, station, activePlayback);
            }
            complete(level, activePlayback);
            return null;
        }
        if (dockingWait) {
            clearDockOutputs(level, station, activePlayback);
        }
        station.resumePlayback();
        activePlayback.advanceTarget();
        activePlayback.beginRouteStartStabilization();
        activePlayback.resetProgress(controller.position().distanceTo(activePlayback.targetPosition()));
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} finished wait at stop {} point={} -> nextPoint={} dockingWait={} position={} nextTarget={} nextGuidance={} speed={}",
                activePlayback.route().id().value(),
                stopName,
                previousTargetIndex,
                activePlayback.targetIndex(),
                dockingWait,
                controller.position(),
                activePlayback.targetPosition(),
                activePlayback.guidancePosition(),
                activePlayback.targetSpeedBlocksPerTick()
        );
        return primePlaybackMotion(level, activePlayback);
    }

    private PlaybackFailure holdAtTarget(
            ServerLevel level,
            ActivePlayback activePlayback,
            VehicleController controller,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation
    ) {
        controller.hold(level, targetPosition, targetRotation);
        activePlayback.resetProgress(controller.position().distanceTo(targetPosition));
        return null;
    }

    private DockQueueGateResult dockQueueGate(
            ServerLevel level,
            AirshipStationBlockEntity fallbackStation,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        return dockQueueGate(level, fallbackStation, activePlayback, controller, true);
    }

    private DockQueueGateResult dockQueueGate(
            ServerLevel level,
            AirshipStationBlockEntity fallbackStation,
            ActivePlayback activePlayback,
            VehicleController controller,
            boolean physicalHold
    ) {
        Optional<RouteStop> gatedStop = activePlayback.dockQueueStop()
                .or(activePlayback::dockReservationStopForCurrentLeg);
        if (gatedStop.isEmpty()) {
            activePlayback.clearDockQueueHold();
            return DockQueueGateResult.ready();
        }

        Optional<AirshipStationBlockEntity> dockingStation = resolveDockingStation(
                level,
                gatedStop.get(),
                Optional.of(fallbackStation),
                Optional.empty()
        );
        if (dockingStation.isEmpty()) {
            return DockQueueGateResult.failed(PlaybackFailure.STATION_MISSING);
        }

        DockingRuntime.DockReservationResult reservation =
                DockingRuntime.requestApproachReservation(level, dockingStation.get(), activePlayback.route());
        if (reservation.failure().isPresent()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} dock reservation request failed before stop {} routePoint={} targetPoint={} failure={}",
                    activePlayback.route().id().value(),
                    gatedStop.get().name(),
                    activePlayback.currentLegStartIndex(),
                    activePlayback.targetIndex(),
                    reservation.failure().get()
            );
            return DockQueueGateResult.failed(reservation.failure().get());
        }
        if (reservation.granted()) {
            boolean releasedFromQueueHold = activePlayback.isDockQueueHeld();
            if (releasedFromQueueHold) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} dock queue released for stop {} point={} targetPoint={} queuePosition={} heldStop={} livePosition={} holdPosition={} holdOffset={}",
                        activePlayback.route().id().value(),
                        gatedStop.get().name(),
                        activePlayback.currentLegStartIndex(),
                        activePlayback.targetIndex(),
                        reservation.queuePosition(),
                        activePlayback.dockQueueStop.map(RouteStop::name).orElse("unknown"),
                        controller != null ? controller.position() : activePlayback.holdPosition(),
                        activePlayback.holdPosition(),
                        controller != null
                                ? formatVec3Offset(controller.position(), activePlayback.holdPosition())
                                : "no_live_controller"
                );
                fallbackStation.resumePlayback();
            }
            if (reservation.changed() || releasedFromQueueHold) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} owns dock reservation for stop {} gatePoint={} targetPoint={} clearanceDistance={}",
                        activePlayback.route().id().value(),
                        gatedStop.get().name(),
                        activePlayback.currentLegStartIndex(),
                        activePlayback.targetIndex(),
                        AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get()
                );
            }
            activePlayback.clearDockQueueHold();
            if (releasedFromQueueHold && activePlayback.isWaiting() && activePlayback.requiresDockLock()) {
                activePlayback.prepareDockApproachAfterQueueRelease();
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} converted dock queue release into dock approach before handshake: stop={} position={} target={} rotation={}",
                        activePlayback.route().id().value(),
                        activePlayback.waitingStop().map(RouteStop::name).orElse("unknown"),
                        controller != null ? controller.position() : activePlayback.holdPosition(),
                        activePlayback.targetPosition(),
                        activePlayback.targetRotation().map(RouteRotation::toString).orElse("none")
                );
            }
            return DockQueueGateResult.ready();
        }

        boolean reusingExistingHold = activePlayback.isDockQueueHeld();
        boolean loadedLiveHold = physicalHold && controller != null;
        boolean adoptLivePose = loadedLiveHold && (!reusingExistingHold || !activePlayback.usesLiveDockQueueHoldPose());
        Vec3 holdPosition = adoptLivePose
                ? controller.position()
                : reusingExistingHold
                        ? activePlayback.holdPosition()
                        : activePlayback.dockQueueHoldPosition(controller, physicalHold);
        Optional<RouteRotation> holdRotation = adoptLivePose
                ? controller.routeRotation()
                : reusingExistingHold
                        ? activePlayback.holdRotation()
                        : activePlayback.dockQueueHoldRotation(controller, physicalHold);
        activePlayback.markDockQueueHold(gatedStop.get(), holdPosition, holdRotation, loadedLiveHold);
        if (adoptLivePose) {
            activePlayback.beginDockQueueSoftHold();
        }
        if (physicalHold) {
            if (activePlayback.isDockQueueSoftHolding()) {
                activePlayback.captureHoldPose(controller);
                controller.hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
                activePlayback.tickDockQueueSoftHold();
                activePlayback.resetProgress(0.0D);
            } else {
                double holdDrift = controller.position().distanceTo(holdPosition);
                if (reusingExistingHold
                        && activePlayback.usesLiveDockQueueHoldPose()
                        && holdDrift > DOCK_QUEUE_HOLD_RELOCATE_DISTANCE) {
                    CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                            "Playback {} dock queue hold drift exceeded threshold before stop {} routePoint={} targetPoint={} drift={} threshold={} livePosition={} holdPosition={} guidance={} target={}; relocating back to held pose",
                            activePlayback.route().id().value(),
                            gatedStop.get().name(),
                            activePlayback.currentLegStartIndex(),
                            activePlayback.targetIndex(),
                            holdDrift,
                            DOCK_QUEUE_HOLD_RELOCATE_DISTANCE,
                            controller.position(),
                            holdPosition,
                            activePlayback.guidancePosition(),
                            activePlayback.targetPosition()
                    );
                    controller.relocate(level, holdPosition, holdRotation);
                    holdDrift = 0.0D;
                }
                controller.hold(level, holdPosition, holdRotation);
                activePlayback.resetProgress(holdDrift);
            }
        } else {
            activePlayback.resetProgress(0.0D);
        }
        fallbackStation.dockQueuePlayback(activePlayback.route());
        if (activePlayback.shouldLogProgress()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} holding for dock queue before stop {} routePoint={} targetPoint={} queuePosition={} heldStop={} holdPosition={} livePosition={} holdOffset={} physicalHold={} holdMode={} guidance={} target={}",
                    activePlayback.route().id().value(),
                    gatedStop.get().name(),
                    activePlayback.currentLegStartIndex(),
                    activePlayback.targetIndex(),
                    reservation.queuePosition(),
                    activePlayback.dockQueueStop.map(RouteStop::name).orElse("none"),
                    holdPosition,
                    controller != null ? controller.position() : holdPosition,
                    controller != null
                            ? formatVec3Offset(controller.position(), holdPosition)
                            : "no_live_controller",
                    physicalHold,
                    activePlayback.isDockQueueSoftHolding() ? "soft_hold_live_pose"
                            : adoptLivePose ? "adopt_live_pose"
                            : reusingExistingHold ? "reuse_existing" : (physicalHold ? "freeze_current_pose" : "path_gate_pose"),
                    activePlayback.guidancePosition(),
                    activePlayback.targetPosition()
            );
        }
        return DockQueueGateResult.queued();
    }

    private void releaseDockReservationIfCleared(ServerLevel level, ActivePlayback activePlayback) {
        if (!activePlayback.shouldReleaseDockReservation(level)) {
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} releasing dock reservation after clearing point {} clearanceDistance={} stationDock={}",
                activePlayback.route().id().value(),
                activePlayback.targetIndex(),
                AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get(),
                activePlayback.dockReservationReleaseDockPos()
                        .map(BlockPos::toShortString)
                        .orElse("unknown")
        );
        Optional<BlockPos> releaseDockPos = activePlayback.dockReservationReleaseDockPos();
        if (releaseDockPos.isPresent()) {
            DockingRuntime.releaseReservation(level.dimension(), releaseDockPos.get(), activePlayback.route().id());
        } else {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} refused broad dock reservation release after clearing point {} because the departed dock identity is unknown",
                    activePlayback.route().id().value(),
                    activePlayback.targetIndex()
            );
        }
        activePlayback.clearDockReservationReleasePoint();
    }

    private Optional<PlaybackFailure> reacquireDockQueueAfterRestore(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> station,
            ActivePlayback activePlayback,
            VehicleController controller
    ) {
        if (station.isEmpty() || activePlayback.isWaiting() || activePlayback.isComplete()) {
            return Optional.empty();
        }
        if (activePlayback.dockReservationStopForCurrentLeg().isEmpty()) {
            return Optional.empty();
        }

        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} rechecking dock queue after restore on leg {} -> {} guidance={} target={} {}",
                activePlayback.route().id().value(),
                activePlayback.currentLegStartIndex(),
                activePlayback.targetIndex(),
                activePlayback.guidancePosition(),
                activePlayback.targetPosition(),
                stationContextDiagnostic(level, activePlayback)
        );
        DockQueueGateResult queueGate = dockQueueGate(level, station.get(), activePlayback, controller);
        if (queueGate.failure().isPresent()) {
            return queueGate.failure();
        }
        if (queueGate.held()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} re-entered dock queue hold after restore before stop {} routePoint={} targetPoint={}",
                    activePlayback.route().id().value(),
                    activePlayback.dockReservationStopForCurrentLeg().map(RouteStop::name).orElse("unknown"),
                    activePlayback.currentLegStartIndex(),
                    activePlayback.targetIndex()
            );
        }
        return Optional.empty();
    }

    private PlaybackFailure primePlaybackMotion(ServerLevel level, ActivePlayback activePlayback) {
        VehicleController controller = activePlayback.controller(level);
        activePlayback.ensurePrimedSegmentProgress();
        Vec3 guidancePosition = activePlayback.guidancePosition();
        double targetSpeed = activePlayback.targetSpeedBlocksPerTick();
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Priming playback {} point={} stabilizing={} speed={} position={} guidance={} target={}",
                activePlayback.route().id().value(),
                activePlayback.targetIndex(),
                activePlayback.isRouteStartStabilizing(),
                targetSpeed,
                controller.position(),
                guidancePosition,
                activePlayback.targetPosition()
        );
        VehicleMotionResult motionResult = controller.moveToward(
                level,
                guidancePosition,
                activePlayback.guidanceRotation(),
                AutomatedLogisticsConfig.MAX_SPEED_MULTIPLIER.get(),
                targetSpeed
        );
        return motionResult.failureReason()
                .map(this::toPlaybackFailure)
                .orElse(null);
    }

    private boolean validateCurrentLegForDeparture(ServerLevel level, ActivePlayback activePlayback) {
        if (activePlayback.isComplete()) {
            activePlayback.clearValidatedLeg();
            return true;
        }

        int targetIndex = activePlayback.targetIndex();
        int startIndex = targetIndex - activePlayback.direction();
        if (startIndex < 0 || startIndex >= activePlayback.route().points().size()) {
            activePlayback.clearValidatedLeg();
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} leg validation failed: startIndex={} targetIndex={} direction={} points={}",
                    activePlayback.route().id().value(),
                    startIndex,
                    targetIndex,
                    activePlayback.direction(),
                    activePlayback.route().points().size()
            );
            return false;
        }

        RoutePoint start = activePlayback.route().points().get(startIndex);
        RoutePoint target = activePlayback.route().points().get(targetIndex);
        if (!start.dimension().equals(level.dimension()) || !target.dimension().equals(level.dimension())) {
            activePlayback.clearValidatedLeg();
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} leg validation failed: dimension mismatch start={} target={} level={}",
                    activePlayback.route().id().value(),
                    start.dimension().location(),
                    target.dimension().location(),
                    level.dimension().location()
            );
            return false;
        }
        if (!isFinite(start.position()) || !isFinite(target.position())) {
            activePlayback.clearValidatedLeg();
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} leg validation failed: non-finite positions start={} target={}",
                    activePlayback.route().id().value(),
                    start.position(),
                    target.position()
            );
            return false;
        }

        activePlayback.validateLeg(startIndex, targetIndex);
        return true;
    }

    private boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
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
            case REDSTONE_LINK_UNCONFIGURED -> PlaybackFailure.REDSTONE_LINK_UNCONFIGURED;
            case CARGO_STORAGE_MISSING -> PlaybackFailure.CARGO_STORAGE_MISSING;
            case CARGO_CONDITION_TIMEOUT -> PlaybackFailure.CARGO_CONDITION_TIMEOUT;
            case MOVEMENT_FAILURE, NONE -> PlaybackFailure.MOVEMENT_FAILURE;
        };
    }

    private static String routeStopSummary(Route route) {
        if (route.stops().isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < route.stops().size(); i++) {
            RouteStop stop = route.stops().get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(stop.pointIndex())
                    .append(':')
                    .append(stop.name())
                    .append('=')
                    .append(routeStopConditionSummary(stop));
        }
        return builder.append(']').toString();
    }

    private static String routeStopConditionSummary(RouteStop stop) {
        List<List<AirshipScheduleCondition>> groups = stop.effectiveConditionGroups();
        if (groups.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            if (groupIndex > 0) {
                builder.append('|');
            }
            List<AirshipScheduleCondition> group = groups.get(groupIndex);
            for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                if (conditionIndex > 0) {
                    builder.append('+');
                }
                builder.append(group.get(conditionIndex).waitCondition().type().name());
            }
        }
        return builder.toString();
    }

    private void holdTransient(ServerLevel level, ActivePlayback activePlayback, PlaybackFailure failure) {
        CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                "Route playback {} paused in transient hold because {}",
                activePlayback.route().id().value(),
                failure
        );
        setVisualsActive(level, activePlayback, false);
        activePlayback.pauseTransient(failure);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            station.holdPlayback(activePlayback.route(), false, Optional.of(failure.failureReason()));
        });
    }

    private void holdFault(ServerLevel level, ActivePlayback activePlayback, PlaybackFailure failure) {
        CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                "Route playback {} paused in fault hold because {} at point {} after {} tick(s)",
                activePlayback.route().id().value(),
                failure,
                activePlayback.targetIndex(),
                activePlayback.playbackTicks()
        );
        setVisualsActive(level, activePlayback, false);
        activePlayback.pauseFault(failure);
        clearDeferredDockOutputs(level, activePlayback.route().id());
        activePlayback.controller(level).hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            station.holdPlayback(activePlayback.route(), true, Optional.of(failure.failureReason()));
        });
    }

    private void resumeStationState(AirshipStationBlockEntity station, ActivePlayback activePlayback) {
        if (activePlayback.isWaiting()) {
            station.waitPlayback(activePlayback.route());
            return;
        }
        station.startPlayback(activePlayback.route());
    }

    private void resumePausedPlayback(ServerLevel level, ActivePlayback activePlayback) {
        Optional<PlaybackFailure> heldFailure = activePlayback.heldFailure();
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} resuming from pause at point {} mode={} restorePosition={} holdPosition={} {} {}",
                activePlayback.route().id().value(),
                activePlayback.targetIndex(),
                activePlayback.restoreCatchMode(),
                activePlayback.restoreCatchPosition(),
                activePlayback.holdPosition(),
                playbackPositionSummary(activePlayback),
                stationContextDiagnostic(level, activePlayback)
        );
        activePlayback.resumeFromPause();
        activePlayback.clearRecoverableWaitFailures();
        activePlayback.resetDockHandshake();
        activePlayback.beginDockReacquireMotion();
        if (heldFailure.filter(failure -> failure == PlaybackFailure.VEHICLE_UNLOADED).isPresent()) {
            relocateRestoredPlaybackIfOffRoute(level, activePlayback, "transient unloaded resume");
        }
        activePlayback.beginRestoreCatch();
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> resumeStationState(station, activePlayback));
        setVisualsActive(level, activePlayback, true);
    }

    private void logUnloadedHoldAwaitingLoadedShip(ServerLevel level, ActivePlayback activePlayback) {
        Optional<UUID> subLevelId = activePlayback.route().linkedController().vehicleId();
        Optional<BlockPos> controllerPos = activePlayback.route().linkedController().controllerPos();
        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                "Playback {} reached unloaded hold point {}. Waiting for loaded Sable ship instead of relocating stored sublevel data. ship={} storedLocalController={} target={} restorePosition={} {} diagnostic={}",
                activePlayback.route().id().value(),
                activePlayback.targetIndex(),
                subLevelId.map(UUID::toString).orElse("missing"),
                controllerPos.map(BlockPos::toShortString).orElse("missing"),
                activePlayback.targetPosition(),
                activePlayback.restoreCatchPosition(),
                stationContextDiagnostic(level, activePlayback),
                subLevelId.map(shipId -> AutomatedLogisticsServices.MATERIALIZATION.describeStoredBody(
                        level.getServer(),
                        activePlayback.route().dimension(),
                        shipId
                ))
                        .orElse("missing Sable identity")
        );
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
        clearDeferredDockOutputs(level, activePlayback.route().id());
        SableSubLevelForceLoadService.releaseDockStop(level, activePlayback.route(), "playback_failed");
        DockingRuntime.releaseReservation(activePlayback.route().id());
        activePlayback.controller(level).stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            station.failPlayback(failure.failureReason());
        });
    }

    private void complete(ServerLevel level, ActivePlayback activePlayback) {
        setVisualsActive(level, activePlayback, false);
        activePlayback.completed(true);
    }

    private void finishCompletedPlayback(ServerLevel level, ActivePlayback activePlayback, boolean stopStationPlayback) {
        clearDeferredDockOutputs(level, activePlayback.route().id());
        SableSubLevelForceLoadService.releaseDockStop(level, activePlayback.route(), "playback_completed");
        DockingRuntime.releaseReservation(activePlayback.route().id());
        activePlayback.controller(level).stop(level);
        stationAt(level, activePlayback.stationPos()).ifPresent(station -> {
            clearDockOutputs(level, station, activePlayback);
            if (stopStationPlayback) {
                station.stopPlayback();
            }
        });
    }

    private Optional<AirshipStationBlockEntity> dockingStation(
            ServerLevel level,
            AirshipStationBlockEntity fallbackStation,
            ActivePlayback activePlayback
    ) {
        return activePlayback.waitingStop()
                .map(stop -> resolveDockingStation(
                        level,
                        stop,
                        Optional.of(fallbackStation),
                        Optional.empty()
                ))
                .orElseGet(() -> Optional.of(fallbackStation));
    }

    private Optional<AirshipStationBlockEntity> resolveDockingStation(
            ServerLevel level,
            RouteStop stop,
            Optional<AirshipStationBlockEntity> legacyFallbackStation,
            Optional<BlockPos> legacyTargetStationPos
    ) {
        if (stop.dockPos().isPresent()) {
            BlockPos explicitDockPos = stop.dockPos().get();
            Optional<AirshipStationBlockEntity> explicitStation = stationAt(level, explicitDockPos);
            if (explicitStation.isEmpty()) {
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Dock station resolution refused fallback: stop={} explicitDockPos={} dimension={} reason=explicit_dock_station_unavailable",
                        stop.name(),
                        explicitDockPos.toShortString(),
                        level.dimension().location()
                );
            }
            return explicitStation;
        }
        return legacyFallbackStation
                .or(() -> legacyTargetStationPos.flatMap(targetPos -> stationAt(level, targetPos)));
    }

    private void clearDockOutputs(
            ServerLevel level,
            AirshipStationBlockEntity fallbackStation,
            ActivePlayback activePlayback
    ) {
        Optional<BlockPos> activeDockStationPos = activePlayback.activeDockStationPos();
        Optional<BlockPos> dockingStationPos = activePlayback.activeDockStationPos()
                .or(() -> activePlayback.waitingStop().flatMap(RouteStop::dockPos));
        if (dockingStationPos.isPresent()) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Clearing dock outputs: route={} station={} fallbackStation={} activeDockStation={}",
                    activePlayback.route().id().value(),
                    dockingStationPos.get().toShortString(),
                    fallbackStation.getBlockPos().toShortString(),
                    activeDockStationPos.map(BlockPos::toShortString).orElse("-")
            );
        }
        dockingStationPos
                .flatMap(dockStationPos -> stationAt(level, dockStationPos))
                .ifPresent(dockingStation -> DockingRuntime.clearDockOutputs(level, dockingStation, activePlayback.route()));
        if (dockingStationPos.isPresent() && !dockingStationPos.get().equals(fallbackStation.getBlockPos())) {
            DockingRuntime.clearDockOutputs(level, fallbackStation, activePlayback.route());
        }
        activePlayback.activeDockStationPos(Optional.empty());
    }

    private void clearDeferredDockOutputs(ServerLevel level, RouteId routeId) {
        DeferredDockOutputClear deferred = deferredDockOutputClears.remove(routeId);
        if (deferred == null) {
            return;
        }
        deferred.activeDockStationPos()
                .flatMap(dockStationPos -> stationAt(level, dockStationPos))
                .ifPresentOrElse(
                        dockingStation -> DockingRuntime.clearDockOutputs(level, dockingStation, deferred.completedRoute()),
                        () -> stationAt(level, deferred.fallbackStationPos())
                                .ifPresent(station -> DockingRuntime.clearDockOutputs(level, station, deferred.completedRoute()))
                );
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos stationPos) {
        if (level.getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    private Optional<List<LinkedCargoEntry>> cargoEntries(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> station,
            ActivePlayback activePlayback,
            CargoWaitTarget target
    ) {
        return switch (target) {
            case SHIP_CARGO -> resolveShipTransponder(level, activePlayback.route())
                    .map(ShipTransponderBlockEntity::linkedCargo)
                    .map(List::copyOf);
            case STATION_CARGO -> station.map(AirshipStationBlockEntity::linkedCargo).map(List::copyOf);
        };
    }

    private Optional<ShipTransponderBlockEntity> resolveShipTransponder(ServerLevel level, Route route) {
        for (ShipTransponderSnapshot snapshot : ShipTransponderRegistry.knownShips(level.dimension())) {
            if (snapshot.controllerRef().filter(route.linkedController()::matches).isEmpty()) {
                continue;
            }
            if (level.getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
                return Optional.of(transponder);
            }
        }
        return Optional.empty();
    }

    private String shipNameFor(Route route) {
        return ShipTransponderRegistry.allShips().stream()
                .filter(snapshot -> snapshot.controllerRef().filter(route.linkedController()::matches).isPresent())
                .map(ShipTransponderSnapshot::shipName)
                .findFirst()
                .orElse(route.name());
    }

    private Optional<BlockPos> transponderPosFor(Route route) {
        return ShipTransponderRegistry.allShips().stream()
                .filter(snapshot -> snapshot.controllerRef().filter(route.linkedController()::matches).isPresent())
                .map(ShipTransponderSnapshot::transponderPos)
                .map(BlockPos::immutable)
                .findFirst();
    }

    private Optional<UUID> transponderIdFor(Route route) {
        return ShipTransponderRegistry.allShips().stream()
                .filter(snapshot -> snapshot.controllerRef().filter(route.linkedController()::matches).isPresent())
                .map(ShipTransponderSnapshot::transponderId)
                .findFirst();
    }

    private boolean cargoStorageMissing(
            ServerLevel level,
            List<LinkedCargoEntry> entries,
            WaitCondition waitCondition,
            String playbackId
    ) {
        if (entries.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "Cargo wait {} on playback {} failed storage validation: entries empty",
                    waitCondition.type(),
                    playbackId
            );
            return true;
        }
        LinkedCargoSummary summary = CargoLinkDiscovery.summarize(level, entries);
        if (summary.staleLinks() > 0) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "Cargo wait {} on playback {} failed storage validation: stale links in {}",
                    waitCondition.type(),
                    playbackId,
                    summary
            );
            return true;
        }
        boolean missing = switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL -> summary.itemLinks() <= 0;
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> summary.fluidLinks() <= 0;
            default -> false;
        };
        if (missing) {
            CreateAeronauticsAutomatedLogistics.debugCargo(
                    "Cargo wait {} on playback {} failed storage validation: summary {} for entries {}",
                    waitCondition.type(),
                    playbackId,
                    summary,
                    summarizeCargoEntries(entries)
            );
        }
        return missing;
    }

    private String summarizeCargoEntries(List<LinkedCargoEntry> entries) {
        return entries.stream()
                .map(entry -> entry.pos() + "[item=" + entry.itemStorage() + ",fluid=" + entry.fluidStorage() + "]")
                .toList()
                .toString();
    }

    private boolean relevantStoragePresent(LinkedCargoSnapshot snapshot, WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL -> snapshot.hasItemStorage();
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> snapshot.hasFluidStorage();
            default -> true;
        };
    }

    private static boolean isCargoWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || type == WaitConditionType.UNTIL_FLUID_THRESHOLD
                || type == WaitConditionType.UNTIL_ITEM_EMPTY
                || type == WaitConditionType.UNTIL_ITEM_FULL
                || type == WaitConditionType.UNTIL_FLUID_EMPTY
                || type == WaitConditionType.UNTIL_FLUID_FULL
                || type == WaitConditionType.UNTIL_EMPTY
                || type == WaitConditionType.UNTIL_FULL;
    }

    private boolean redstoneLinkSatisfied(WaitCondition waitCondition) {
        return Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(
                Couple.create(
                        Frequency.of(waitCondition.redstoneFrequencyFirst()),
                        Frequency.of(waitCondition.redstoneFrequencySecond())
                )
        ) == waitCondition.redstonePowered();
    }

    private boolean timeOfDaySatisfied(ServerLevel level, WaitCondition waitCondition) {
        int maxTickDiff = 40;
        int rotation = waitCondition.timeOfDayRotationTicks();
        int dayTime = (int) (level.getDayTime() % rotation);
        int targetTicks = (int) ((((waitCondition.timeOfDayHour() + 18) % 24) * 1000
                + Math.ceil(waitCondition.timeOfDayMinute() / 60f * 1000)) % rotation);
        int diff = dayTime - targetTicks;
        return diff >= 0 && diff <= maxTickDiff;
    }

    private boolean cargoConditionSatisfied(
            ServerLevel level,
            LinkedCargoSnapshot snapshot,
            WaitCondition waitCondition
    ) {
        Ops[] ops = Ops.values();
        Ops operator = ops[Math.max(0, Math.min(ops.length - 1, waitCondition.cargoOperator()))];
        return switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD -> operator.test(
                    snapshot.itemAmount(level, waitCondition.cargoFilter(), waitCondition.cargoMeasure() == 1),
                    waitCondition.durationTicks()
            );
            case UNTIL_FLUID_THRESHOLD -> operator.test(
                    snapshot.fluidBuckets(level, waitCondition.cargoFilter()),
                    waitCondition.durationTicks()
            );
            case UNTIL_ITEM_EMPTY, UNTIL_EMPTY -> snapshot.totalItemCount() <= 0;
            case UNTIL_ITEM_FULL, UNTIL_FULL -> snapshot.itemFull();
            case UNTIL_FLUID_EMPTY -> snapshot.totalFluidAmount() <= 0L;
            case UNTIL_FLUID_FULL -> snapshot.fluidFull();
            default -> false;
        };
    }

    private void notifyOwnerIfShipNotLoadedForDocking(
            ServerLevel level,
            ActivePlayback activePlayback,
            PlaybackFailure failure
    ) {
        if (failure != PlaybackFailure.MISSING_CONTROLLER && failure != PlaybackFailure.MISSING_DOCK) {
            return;
        }
        if (failure == PlaybackFailure.MISSING_DOCK && !linkedShipDockChunkMissing(level, activePlayback.route())) {
            return;
        }
        long gameTime = level.getGameTime();
        Long lastNotice = dockingIssueNoticeTicks.get(activePlayback.route().id());
        if (lastNotice != null && gameTime - lastNotice < DOCKING_ISSUE_NOTICE_COOLDOWN_TICKS) {
            return;
        }
        activePlayback.route().ownerId()
                .map(level.getServer().getPlayerList()::getPlayer)
                .ifPresent(owner -> {
                    PacketDistributor.sendToPlayer(owner, ShowDockingIssueToastPayload.INSTANCE);
                    owner.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.docking_issue"));
                    dockingIssueNoticeTicks.put(activePlayback.route().id(), gameTime);
                    CreateAeronauticsAutomatedLogistics.debugDocking(
                            "Docking issue notice sent to route owner: route={} owner={} failure={} stationPos={} targetIndex={}",
                            activePlayback.route().id().value(),
                            owner.getUUID(),
                            failure,
                            activePlayback.activeDockStationPos().map(BlockPos::toShortString).orElse("-"),
                            activePlayback.targetIndex()
                    );
                });
    }

    private boolean linkedShipDockChunkMissing(ServerLevel level, Route route) {
        return liveTransponderForRoute(level, route)
                .flatMap(ShipTransponderBlockEntity::shipDockPos)
                .map(pos -> !level.hasChunkAt(pos))
                .orElse(false);
    }

    private Optional<ShipTransponderBlockEntity> liveTransponderForRoute(ServerLevel level, Route route) {
        Optional<BlockPos> routeControllerPos = route.linkedController().controllerPos();
        if (routeControllerPos.isPresent()
                && level.getBlockEntity(routeControllerPos.get()) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }

        return ShipTransponderRegistry.knownShips(level.dimension()).stream()
                .filter(snapshot -> snapshot.controllerRef().filter(route.linkedController()::matches).isPresent())
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast)
                .findFirst();
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

    private void tickVisualResync(MinecraftServer server) {
        visualResyncTicker++;
        if (visualResyncTicker < 40) {
            return;
        }
        visualResyncTicker = 0;
        if (activeVisualShipIds.isEmpty()) {
            return;
        }
        SyncAutomatedShipVisualsPayload payload = new SyncAutomatedShipVisualsPayload(activeVisualShipIds.stream().toList());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private void restorePendingRuntime(MinecraftServer server) {
        List<Map.Entry<RouteId, CompoundTag>> restoreOrder = pendingRuntimePlaybacks.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingInt((Map.Entry<RouteId, CompoundTag> entry) ->
                                pendingDockRestorePriority(entry.getValue()))
                        .thenComparing(entry -> entry.getKey().value()))
                .toList();
        for (Map.Entry<RouteId, CompoundTag> entry : restoreOrder) {
            if (activePlaybacks.containsKey(entry.getKey())) {
                pendingRuntimePlaybacks.remove(entry.getKey());
                pendingRuntimeRestoreCooldowns.remove(entry.getKey());
                continue;
            }
            int cooldown = pendingRuntimeRestoreCooldowns.getOrDefault(entry.getKey(), 0);
            if (cooldown > 0) {
                pendingRuntimeRestoreCooldowns.put(entry.getKey(), cooldown - 1);
                continue;
            }
            boolean startupRestoreReady = StationChunkLoadingService.isStartupRestoreReady();
            Optional<ActivePlayback> activePlayback = readActivePlayback(server, entry.getValue());
            if (activePlayback.isEmpty()) {
                if (!startupRestoreReady) {
                    CreateAeronauticsAutomatedLogistics.debugPlayback(
                            "Route playback {} is waiting for startup restore readiness before stored-body materialization.",
                            entry.getKey().value()
                    );
                    pendingRuntimeRestoreCooldowns.put(entry.getKey(), 10);
                    continue;
                }
                if (tryMaterializePendingRuntime(server, entry.getKey(), entry.getValue())) {
                    pendingRuntimeRestoreCooldowns.put(entry.getKey(), 1);
                    continue;
                }
                Optional<PlaybackFailure> terminalFailure = pendingRuntimeTerminalFailure(server, entry.getValue());
                if (terminalFailure.isPresent()) {
                    CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                            "Keeping pending route playback {} after restore failure {} so the runtime remains inspectable. {}",
                            entry.getKey().value(),
                            terminalFailure.get(),
                            pendingStoredShipDiagnostic(server, entry.getValue())
                    );
                    pendingRuntimeRestoreCooldowns.put(entry.getKey(), 100);
                    continue;
                }
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Route playback {} is still pending restore; controller or level is not ready. {}",
                        entry.getKey().value(),
                        pendingStoredShipDiagnostic(server, entry.getValue())
                );
                pendingRuntimeRestoreCooldowns.put(entry.getKey(), 20);
                continue;
            }
            ActivePlayback restored = activePlayback.get();
            CompoundTag restoredRuntimeTag = entry.getValue();
            activePlaybacks.put(restored.route().id(), restored);
            pendingRuntimePlaybacks.remove(entry.getKey());
            pendingRuntimeRestoreCooldowns.remove(entry.getKey());
            ServerLevel level = server.getLevel(restored.route().dimension());
            if (level == null) {
                continue;
            }
            PendingDockRestoreGate liveRestoreGate = restored.isPaused()
                    ? PendingDockRestoreGate.notRequired()
                    : pendingDockRestoreGate(
                            level,
                            restored.route(),
                            restoredRuntimeTag,
                            restored.targetIndex()
                    );
            restored.clearRecoverableWaitFailures();
            if (!restored.dockLocked()) {
                restored.resetDockHandshake();
            }
            boolean restoredToDockQueueHold = restoreDockQueueHold(
                    level,
                    restored,
                    liveRestoreGate.queued() || !startupRestoreReady && liveRestoreGate.blocked()
            );
            boolean restoredDockLockedWait = restored.isWaiting()
                    && restored.requiresDockLock()
                    && restored.dockLocked();
            if (restored.isPaused()) {
                // Paused/fault-held ships must remain where the player left them; manual play will validate position.
            } else if (restoredToDockQueueHold) {
                restored.cancelRestoreCatch();
            } else if (liveRestoreGate.blocked()) {
                VehicleController controller = restored.controller(level);
                restored.pauseFault(PlaybackFailure.STATION_MISSING);
                controller.hold(level, controller.position(), controller.routeRotation());
                CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                        "Playback {} held at its live restore pose because dock reservation preflight was blocked: reason={} position={}",
                        restored.route().id().value(),
                        liveRestoreGate.reason(),
                        controller.position()
                );
            } else if (restoredDockLockedWait) {
                VehicleController controller = restored.controller(level);
                restored.cancelRestoreCatch();
                restored.endDockReacquireMotion();
                controller.hold(
                        level,
                        restored.waitHoldPosition(controller),
                        restored.waitHoldRotation(controller)
                );
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} restored at a physically locked dock; holding saved dock pose and resuming wait conditions without route catch-up.",
                        restored.route().id().value()
                );
            } else if (!startupRestoreReady) {
                restored.beginDockReacquireMotion();
                restored.beginRestoreCatch();
                relocateRestoredPlaybackIfOffRoute(level, restored, "server reload");
                clampRestoredPlaybackToCatchPose(level, restored);
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Restored route playback {} live controller before startup readiness; clamped and held without station/schedule validation until startup restore gate opens.",
                        restored.route().id().value()
                );
            } else {
                Optional<PlaybackFailure> restoreBlocker = AutomatedLogisticsServices.SCHEDULES.playbackBlocker(
                        level,
                        restored.route().id()
                );
                if (restoreBlocker.isPresent()) {
                    holdFault(level, restored, restoreBlocker.get());
                    CreateAeronauticsAutomatedLogistics.debugPlayback(
                            "Restored route playback {} directly into fault hold because {}",
                            restored.route().id().value(),
                            restoreBlocker.get()
                    );
                    continue;
                }
                restored.beginDockReacquireMotion();
                restored.beginRestoreCatch();
                relocateRestoredPlaybackIfOffRoute(level, restored, "server reload");
                clampRestoredPlaybackToCatchPose(level, restored);
            }
            if (restored.isHoldLocked()) {
                restored.controller(level).hold(level, restored.holdPosition(), restored.holdRotation());
            }
            stationAt(level, restored.stationPos()).ifPresent(station -> {
                if (restored.isPaused()) {
                    station.holdPlayback(
                            restored.route(),
                            restored.isFaultHold(),
                            restored.heldFailure().map(PlaybackFailure::failureReason)
                    );
                } else if (restored.isDockQueueHeld()) {
                    station.dockQueuePlayback(restored.route());
                } else if (restored.isWaiting()) {
                    station.waitPlayback(restored.route());
                } else {
                    station.startPlayback(restored.route());
                }
            });
            setVisualsActive(level, restored, !restored.isPaused());
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Restored active route playback {} after server reload paused={} waiting={} unloadedTransit={} targetPoint={} {} {}",
                    restored.route().id().value(),
                    restored.isPaused(),
                    restored.isWaiting(),
                    restored.isUnloadedTransit(),
                    restored.targetIndex(),
                    playbackPositionSummary(restored),
                    stationContextDiagnostic(level, restored)
            );
        }
    }

    private static int pendingDockRestorePriority(CompoundTag tag) {
        if (tag.getBoolean(DOCK_LOCKED)) {
            return 0;
        }
        if (tag.hasUUID(WAITING_STOP_ID)) {
            return 1;
        }
        if (tag.hasUUID(DOCK_QUEUE_STOP_ID)) {
            return 3;
        }
        return 2;
    }

    private boolean restoreDockQueueHold(
            ServerLevel level,
            ActivePlayback activePlayback,
            boolean queuedByRestorePreflight
    ) {
        DockingRuntime.DockReservationStatus reservation =
                DockingRuntime.reservationStatus(activePlayback.route().id());
        boolean savedQueueHold = activePlayback.dockQueueStop().isPresent();
        if (!savedQueueHold
                && !queuedByRestorePreflight
                && (!reservation.tracked() || reservation.granted())) {
            return false;
        }

        Optional<RouteStop> stop = activePlayback.dockQueueStop()
                .or(activePlayback::dockReservationStopForCurrentLeg)
                .or(activePlayback::waitingStop);
        if (stop.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Playback {} refused restore relocation because its dock reservation is queued but no matching dock stop could be resolved; holding the live body at its current pose",
                    activePlayback.route().id().value()
            );
            activePlayback.pauseFault(PlaybackFailure.DOCK_LOCK_FAILED);
            return true;
        }

        VehicleController controller = activePlayback.controller(level);
        activePlayback.markDockQueueHold(
                stop.get(),
                controller.position(),
                controller.routeRotation(),
                true
        );
        controller.hold(level, activePlayback.holdPosition(), activePlayback.holdRotation());
        activePlayback.resetProgress(0.0D);
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} restored into dock queue hold before any route relocation: stop={} reservationTracked={} reservationGranted={} queuePosition={} holdPosition={}",
                activePlayback.route().id().value(),
                stop.get().name(),
                reservation.tracked(),
                reservation.granted(),
                reservation.queuePosition(),
                activePlayback.holdPosition()
        );
        return true;
    }

    private boolean tryMaterializePendingRuntime(MinecraftServer server, RouteId routeId, CompoundTag playbackTag) {
        if (!playbackTag.contains(ROUTE, Tag.TAG_COMPOUND)) {
            return false;
        }
        Optional<Route> route = RouteNbtSerializer.read(playbackTag.getCompound(ROUTE));
        if (route.isEmpty()) {
            return false;
        }
        Optional<UUID> shipId = route.get().linkedController().vehicleId();
        Optional<BlockPos> localControllerPos = route.get().linkedController().controllerPos();
        if (shipId.isEmpty() || localControllerPos.isEmpty()) {
            return false;
        }
        int targetIndex = playbackTag.getInt(TARGET_INDEX);
        if (targetIndex < 0 || targetIndex >= route.get().points().size()) {
            return false;
        }
        ServerLevel level = server.getLevel(route.get().dimension());
        if (level == null) {
            return false;
        }
        PendingDockRestoreGate dockGate = pendingDockRestoreGate(level, route.get(), playbackTag, targetIndex);
        if (dockGate.blocked()) {
            return false;
        }
        boolean hasSavedHoldPose = playbackTag.contains(HOLD_POSITION, Tag.TAG_COMPOUND);
        boolean savedQueueHold = playbackTag.hasUUID(DOCK_QUEUE_STOP_ID) && hasSavedHoldPose;
        int direction = playbackTag.getInt(DIRECTION);
        int fallbackHoldIndex = direction == -1 || direction == 1
                ? Math.max(0, Math.min(route.get().points().size() - 1, targetIndex - direction))
                : targetIndex;
        Vec3 queueHoldPosition = (savedQueueHold || dockGate.queued() && hasSavedHoldPose)
                ? readVec3(playbackTag.getCompound(HOLD_POSITION))
                : route.get().points().get(fallbackHoldIndex).position();
        boolean queueHoldLoaded = level.isLoaded(BlockPos.containing(queueHoldPosition));
        if (dockGate.queued() && !queueHoldLoaded) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Route playback {} remains stored while queued because its hold area is not player-loaded: stop={} queuePosition={} holdPosition={}",
                    routeId.value(),
                    dockGate.stop().map(RouteStop::name).orElse("unknown"),
                    dockGate.queuePosition(),
                    queueHoldPosition
            );
            return false;
        }
        boolean restoreAtQueueHold = dockGate.queued() || savedQueueHold && queueHoldLoaded;
        Vec3 materializePosition = restoreAtQueueHold
                ? queueHoldPosition
                : route.get().points().get(targetIndex).position();
        BlockPos materializeBlock = BlockPos.containing(materializePosition);
        if (!level.isLoaded(materializeBlock)) {
            dockGate.stop()
                    .flatMap(RouteStop::dockPos)
                    .flatMap(pos -> stationAt(level, pos))
                    .ifPresent(station -> StationChunkLoadingService.requestInteractionLoading(
                            level,
                            station.stationId(),
                            station.getBlockPos(),
                            "pending_runtime_restore_materialization"
                    ));
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Route playback {} cannot materialize pending stored ship {} yet because {} chunk is not loaded: position={} chunk={} reservationState={}",
                    routeId.value(),
                    shipId.get(),
                    restoreAtQueueHold ? "dock queue hold" : "target",
                    materializePosition,
                    new ChunkPos(materializeBlock),
                    dockGate.summary()
            );
            return false;
        }
        ShipMaterializationService.MaterializationResult result = AutomatedLogisticsServices.MATERIALIZATION.materializeStoredBodyAt(
                new ShipMaterializationService.MaterializationRequest(
                        server,
                        route.get().dimension(),
                        transponderIdFor(route.get()),
                        shipId,
                        Optional.of(routeId),
                        Optional.of(targetIndex),
                        Optional.empty(),
                        localControllerPos,
                        materializePosition,
                        "pending runtime restore route " + routeId.value(),
                        "pending_runtime_restore",
                        false,
                        true
                )
        );
        if (result.success()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Route playback {} materialized pending stored ship {} during runtime restore: {}",
                    routeId.value(),
                    shipId.get(),
                    result.message()
            );
            return true;
        }
        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                "Route playback {} could not materialize pending stored ship {} during runtime restore: {}",
                routeId.value(),
                shipId.get(),
                result.message()
        );
        return false;
    }

    private PendingDockRestoreGate pendingDockRestoreGate(
            ServerLevel level,
            Route route,
            CompoundTag playbackTag,
            int targetIndex
    ) {
        Optional<RouteStop> stop = pendingDockStop(route, playbackTag, targetIndex);
        if (stop.isEmpty()) {
            return PendingDockRestoreGate.notRequired();
        }
        Optional<BlockPos> stationPos = stop.get().dockPos();
        if (stationPos.isEmpty()) {
            return PendingDockRestoreGate.notRequired();
        }
        Optional<AirshipStationBlockEntity> station = stationAt(level, stationPos.get());
        if (station.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Route playback {} deferring pending materialization until dock reservation station {} is loaded",
                    route.id().value(),
                    stationPos.get().toShortString()
            );
            return PendingDockRestoreGate.blocked(stop.get(), "dock_station_not_loaded");
        }

        DockingRuntime.DockReservationResult reservation =
                DockingRuntime.requestApproachReservation(level, station.get(), route);
        if (reservation.failure().isPresent()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Route playback {} deferring pending materialization because dock reservation preflight failed: stop={} failure={}",
                    route.id().value(),
                    stop.get().name(),
                    reservation.failure().get()
            );
            return PendingDockRestoreGate.blocked(stop.get(), reservation.failure().get().name());
        }
        if (!reservation.granted()) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Route playback {} remains stored at dock queue hold before materialization: stop={} queuePosition={}",
                    route.id().value(),
                    stop.get().name(),
                    reservation.queuePosition()
            );
            return PendingDockRestoreGate.queued(stop.get(), reservation.queuePosition());
        }
        return PendingDockRestoreGate.granted(stop.get());
    }

    private Optional<RouteStop> pendingDockStop(Route route, CompoundTag playbackTag, int targetIndex) {
        if (playbackTag.hasUUID(DOCK_QUEUE_STOP_ID)) {
            UUID stopId = playbackTag.getUUID(DOCK_QUEUE_STOP_ID);
            Optional<RouteStop> queuedStop = route.stops().stream()
                    .filter(stop -> stop.id().equals(stopId))
                    .filter(VehicleRoutePlaybackService::routeStopRequiresDockLock)
                    .findFirst();
            if (queuedStop.isPresent()) {
                return queuedStop;
            }
        }
        if (playbackTag.hasUUID(WAITING_STOP_ID)) {
            UUID stopId = playbackTag.getUUID(WAITING_STOP_ID);
            Optional<RouteStop> waitingStop = route.stops().stream()
                    .filter(stop -> stop.id().equals(stopId))
                    .filter(VehicleRoutePlaybackService::routeStopRequiresDockLock)
                    .findFirst();
            if (waitingStop.isPresent()) {
                return waitingStop;
            }
        }

        int direction = playbackTag.getInt(DIRECTION);
        if (direction != -1 && direction != 1) {
            return Optional.empty();
        }
        int startIndex = targetIndex - direction;
        double clearanceDistance = AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get();
        return route.stops().stream()
                .filter(VehicleRoutePlaybackService::routeStopRequiresDockLock)
                .filter(stop -> shouldGatePendingDockApproach(route, startIndex, direction, stop, clearanceDistance))
                .findFirst();
    }

    private static boolean shouldGatePendingDockApproach(
            Route route,
            int startIndex,
            int direction,
            RouteStop stop,
            double clearanceDistance
    ) {
        OptionalInt gatePoint = traverseRoutePointForDistance(
                route,
                stop.pointIndex(),
                -direction,
                clearanceDistance
        );
        if (gatePoint.isEmpty()) {
            return false;
        }
        int gate = gatePoint.getAsInt();
        return direction >= 0
                ? startIndex >= gate && startIndex < stop.pointIndex()
                : startIndex <= gate && startIndex > stop.pointIndex();
    }

    private static OptionalInt traverseRoutePointForDistance(
            Route route,
            int anchorPointIndex,
            int travelDirection,
            double requiredDistance
    ) {
        int currentIndex = anchorPointIndex;
        int candidateIndex = anchorPointIndex;
        double travelledDistance = 0.0D;
        while (true) {
            int nextIndex = currentIndex + travelDirection;
            if (nextIndex < 0 || nextIndex >= route.points().size()) {
                return candidateIndex == anchorPointIndex
                        ? OptionalInt.empty()
                        : OptionalInt.of(candidateIndex);
            }
            candidateIndex = nextIndex;
            travelledDistance += route.points().get(currentIndex).position()
                    .distanceTo(route.points().get(nextIndex).position());
            if (travelledDistance >= requiredDistance) {
                return OptionalInt.of(candidateIndex);
            }
            currentIndex = nextIndex;
        }
    }

    private static boolean routeStopRequiresDockLock(RouteStop stop) {
        return stop.effectiveConditionGroups().stream()
                .flatMap(List::stream)
                .map(AirshipScheduleCondition::waitCondition)
                .anyMatch(wait -> wait.type() == WaitConditionType.UNTIL_DOCKED
                        || wait.type() == WaitConditionType.UNTIL_IDLE
                        || isCargoWaitType(wait.type()));
    }

    private void requestStationInteractionLoading(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> fallbackStation,
            ActivePlayback activePlayback,
            String reason
    ) {
        Optional<BlockPos> requestedStationPos = activePlayback.activeDockStationPos()
                .or(activePlayback::targetStationPos)
                .or(() -> activePlayback.dockReservationStopForCurrentLeg().flatMap(RouteStop::dockPos));
        Optional<AirshipStationBlockEntity> station = requestedStationPos
                .flatMap(pos -> stationAt(level, pos))
                .or(() -> fallbackStation);
        station.ifPresent(stationBlockEntity -> StationChunkLoadingService.requestInteractionLoading(
                level,
                stationBlockEntity.stationId(),
                stationBlockEntity.getBlockPos(),
                reason
        ));
    }

    private static String playbackPositionSummary(ActivePlayback activePlayback) {
        String validatedStart = activePlayback.validatedLegStartIndex().isPresent()
                ? Integer.toString(activePlayback.validatedLegStartIndex().getAsInt())
                : "?";
        String validatedTarget = activePlayback.validatedLegTargetIndex().isPresent()
                ? Integer.toString(activePlayback.validatedLegTargetIndex().getAsInt())
                : "?";
        return "guidance=" + activePlayback.guidancePosition()
                + ", target=" + activePlayback.targetPosition()
                + ", restore=" + activePlayback.restoreCatchPosition()
                + ", leg=" + validatedStart
                + "->" + validatedTarget;
    }

    private void relocateRestoredPlaybackIfOffRoute(
            ServerLevel level,
            ActivePlayback activePlayback,
            String source
    ) {
        VehicleController controller = activePlayback.controller(level);
        if (!controller.isLoaded(level) || !controller.isAssembled()) {
            return;
        }

        Vec3 restorePosition = activePlayback.restoreCatchPosition();
        Vec3 controllerBefore = controller.position();
        double restoreDistance = controllerBefore.distanceTo(restorePosition);
        if (restoreDistance <= RESTORE_RELOCATE_DISTANCE) {
            return;
        }

        controller.relocate(level, restorePosition, activePlayback.restoreCatchRotation());
        CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                "Playback {} relocated loaded ship during {} because live position drifted off restore path: distance={} threshold={} restoreMode={} restorePosition={} controllerBefore={}",
                activePlayback.route().id().value(),
                source,
                restoreDistance,
                RESTORE_RELOCATE_DISTANCE,
                activePlayback.restoreCatchMode(),
                restorePosition,
                controllerBefore
        );
    }

    private void clampRestoredPlaybackToCatchPose(ServerLevel level, ActivePlayback activePlayback) {
        VehicleController controller = activePlayback.controller(level);
        if (!controller.isLoaded(level) || !controller.isAssembled()) {
            return;
        }

        Vec3 restorePosition = activePlayback.restoreCatchPosition();
        Optional<RouteRotation> restoreRotation = activePlayback.restoreCatchRotation();
        controller.hold(level, restorePosition, restoreRotation);
        activePlayback.resetProgress(controller.position().distanceTo(restorePosition));
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Playback {} clamped to restore catch pose immediately after reload: mode={} restorePosition={} controllerPosition={} targetPoint={}",
                activePlayback.route().id().value(),
                activePlayback.restoreCatchMode(),
                restorePosition,
                controller.position(),
                activePlayback.targetIndex()
        );
    }

    private String stationContextDiagnostic(ServerLevel level, ActivePlayback activePlayback) {
        Optional<BlockPos> stationPos = activePlayback.targetStationPos();
        if (stationPos.isEmpty()) {
            return "targetStation=none";
        }
        BlockPos pos = stationPos.get();
        ChunkPos chunkPos = new ChunkPos(pos);
        boolean chunkLoaded = level.hasChunk(chunkPos.x, chunkPos.z);
        boolean stationPresent = stationAt(level, pos).isPresent();
        return "targetStation=" + pos.toShortString()
                + ", chunk=" + chunkPos
                + ", chunkLoaded=" + chunkLoaded
                + ", stationPresent=" + stationPresent;
    }

    private static String formatVec3Offset(Vec3 actual, Vec3 intended) {
        Vec3 offset = intended.subtract(actual);
        return offset + " |dist=" + actual.distanceTo(intended);
    }

    private String pendingStoredShipDiagnostic(MinecraftServer server, CompoundTag playbackTag) {
        if (!playbackTag.contains(ROUTE, Tag.TAG_COMPOUND)) {
            return "No route data was present in the pending runtime tag.";
        }
        Optional<Route> route = RouteNbtSerializer.read(playbackTag.getCompound(ROUTE));
        if (route.isEmpty()) {
            return "Pending route data could not be decoded.";
        }
        Optional<UUID> shipId = route.get().linkedController().vehicleId();
        if (shipId.isEmpty()) {
            return "Pending route has no linked Sable vehicle id.";
        }
        return AutomatedLogisticsServices.MATERIALIZATION.describeStoredBody(
                server,
                route.get().dimension(),
                shipId.get()
        );
    }

    private Optional<PlaybackFailure> pendingRuntimeTerminalFailure(MinecraftServer server, CompoundTag playbackTag) {
        if (!playbackTag.contains(ROUTE, Tag.TAG_COMPOUND)) {
            return Optional.of(PlaybackFailure.INVALID_ROUTE);
        }
        Optional<Route> route = RouteNbtSerializer.read(playbackTag.getCompound(ROUTE));
        if (route.isEmpty()) {
            return Optional.of(PlaybackFailure.INVALID_ROUTE);
        }
        Optional<UUID> shipId = route.get().linkedController().vehicleId();
        if (shipId.isEmpty()) {
            return Optional.of(PlaybackFailure.MISSING_CONTROLLER);
        }
        ServerLevel level = server.getLevel(route.get().dimension());
        if (level == null) {
            return Optional.empty();
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container != null && container.getSubLevel(shipId.get()) != null) {
            return Optional.empty();
        }
        if (!AutomatedLogisticsServices.MATERIALIZATION.hasStoredBody(server, route.get().dimension(), shipId.get())) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Route playback {} stored ship is not currently visible during restore; keeping pending instead of treating missing storage as terminal. {}",
                    route.get().id().value(),
                    pendingStoredShipDiagnostic(server, playbackTag)
            );
        }
        return Optional.empty();
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
        if (!activePlayback.conditionStates().isEmpty()) {
            tag.put(CONDITION_STATES, writeConditionStates(activePlayback.conditionStates()));
        }
        activePlayback.dockWaitFailure().ifPresent(failure -> tag.putString(DOCK_WAIT_FAILURE, failure.name()));
        tag.putInt(RESTORE_CATCH_TICKS_REMAINING, activePlayback.restoreCatchTicksRemaining());
        tag.putString(PAUSE_STATE, activePlayback.pauseState().name());
        activePlayback.heldFailure().ifPresent(failure -> tag.putString(HELD_FAILURE, failure.name()));
        tag.put(HOLD_POSITION, writeVec3(activePlayback.holdPosition()));
        activePlayback.holdRotation().ifPresent(rotation -> tag.put(HOLD_ROTATION, writeRotation(rotation)));
        activePlayback.dockQueueStop().ifPresent(stop -> tag.putUUID(DOCK_QUEUE_STOP_ID, stop.id()));
        tag.putBoolean(DOCK_QUEUE_HOLD_LIVE_POSE, activePlayback.usesLiveDockQueueHoldPose());
        tag.putBoolean(COMPLETED, activePlayback.completed());
        tag.putString(RUNTIME_MODE, activePlayback.runtimeMode().name());
        activePlayback.validatedLegStartIndex().ifPresent(index -> tag.putInt(VALIDATED_LEG_START_INDEX, index));
        activePlayback.validatedLegTargetIndex().ifPresent(index -> tag.putInt(VALIDATED_LEG_TARGET_INDEX, index));
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
        Optional<VehicleController> controller = resolveLiveController(level, route.get(), "restore_active_playback", "runtime_restore_live_lookup");
        if (controller.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugPlaybackWarn(
                    "Pending route playback {} could not restore live controller; keeping saved playback pending for later materialization. {}",
                    route.get().id().value(),
                    pendingStoredShipDiagnostic(server, tag)
            );
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
        Map<ConditionKey, ConditionRuntimeState> conditionStates = tag.contains(CONDITION_STATES, Tag.TAG_LIST)
                ? readConditionStates(tag.getList(CONDITION_STATES, Tag.TAG_COMPOUND))
                : Map.of();
        Optional<PlaybackFailure> dockWaitFailure = readNamedPlaybackFailure(tag, DOCK_WAIT_FAILURE);
        PauseState pauseState = readPauseState(tag);
        Optional<PlaybackFailure> heldFailure = readHeldFailure(tag);
        RuntimeMode runtimeMode = readRuntimeMode(tag);
        OptionalInt validatedLegStartIndex = readLegIndex(tag, VALIDATED_LEG_START_INDEX, route.get());
        OptionalInt validatedLegTargetIndex = readLegIndex(tag, VALIDATED_LEG_TARGET_INDEX, route.get());
        Vec3 holdPosition = tag.contains(HOLD_POSITION, Tag.TAG_COMPOUND)
                ? readVec3(tag.getCompound(HOLD_POSITION))
                : controller.get().position();
        Optional<RouteRotation> holdRotation = tag.contains(HOLD_ROTATION, Tag.TAG_COMPOUND)
                ? readRotation(tag.getCompound(HOLD_ROTATION))
                : controller.get().routeRotation();
        Optional<RouteStop> dockQueueStop = tag.hasUUID(DOCK_QUEUE_STOP_ID)
                ? route.get().stops().stream()
                        .filter(stop -> stop.id().equals(tag.getUUID(DOCK_QUEUE_STOP_ID)))
                        .findFirst()
                : Optional.empty();
        boolean dockQueueHoldLivePose = tag.getBoolean(DOCK_QUEUE_HOLD_LIVE_POSE);

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
                conditionStates,
                dockWaitFailure,
                Math.max(0, tag.getInt(RESTORE_CATCH_TICKS_REMAINING)),
                pauseState,
                heldFailure,
                holdPosition,
                holdRotation,
                dockQueueStop,
                dockQueueHoldLivePose,
                tag.getBoolean(COMPLETED),
                runtimeMode,
                validatedLegStartIndex,
                validatedLegTargetIndex
        ));
    }

    private static Optional<VehicleController> resolveLiveController(
            ServerLevel level,
            Route route,
            String source,
            String reasonCode
    ) {
        ShipMaterializationService.LiveBodyLookupResult lookup = AutomatedLogisticsServices.MATERIALIZATION.resolveLiveBody(
                new ShipMaterializationService.LiveBodyLookupRequest(
                        level.getServer(),
                        route.dimension(),
                        route.linkedController(),
                        Optional.empty(),
                        route.linkedController().vehicleId(),
                        Optional.of(route.id()),
                        Optional.empty(),
                        Optional.empty(),
                        source,
                        reasonCode
                )
        );
        return lookup.controller();
    }

    private RuntimeMode readRuntimeMode(CompoundTag tag) {
        if (!tag.contains(RUNTIME_MODE, Tag.TAG_STRING)) {
            return RuntimeMode.LOADED;
        }
        try {
            return RuntimeMode.valueOf(tag.getString(RUNTIME_MODE));
        } catch (IllegalArgumentException ignored) {
            return RuntimeMode.LOADED;
        }
    }

    private OptionalInt readLegIndex(CompoundTag tag, String key, Route route) {
        if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
            return OptionalInt.empty();
        }
        int index = tag.getInt(key);
        if (index < 0 || index >= route.points().size()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(index);
    }

    private PauseState readPauseState(CompoundTag tag) {
        if (!tag.contains(PAUSE_STATE, Tag.TAG_STRING)) {
            return PauseState.NONE;
        }
        try {
            return PauseState.valueOf(tag.getString(PAUSE_STATE));
        } catch (IllegalArgumentException ignored) {
            return PauseState.NONE;
        }
    }

    private Optional<PlaybackFailure> readHeldFailure(CompoundTag tag) {
        if (!tag.contains(HELD_FAILURE, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlaybackFailure.valueOf(tag.getString(HELD_FAILURE)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<PlaybackFailure> readNamedPlaybackFailure(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlaybackFailure.valueOf(tag.getString(key)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private ListTag writeConditionStates(Map<ConditionKey, ConditionRuntimeState> conditionStates) {
        ListTag list = new ListTag();
        for (Map.Entry<ConditionKey, ConditionRuntimeState> entry : conditionStates.entrySet()) {
            CompoundTag conditionTag = new CompoundTag();
            conditionTag.putInt(CONDITION_GROUP_INDEX, entry.getKey().groupIndex());
            conditionTag.putInt(CONDITION_INDEX, entry.getKey().conditionIndex());
            conditionTag.putInt(CONDITION_WAIT_TICKS, entry.getValue().waitTicksRemaining);
            conditionTag.putInt(CONDITION_IDLE_WINDOW_TICKS, entry.getValue().idleWindowTicks);
            conditionTag.putInt(CONDITION_IDLE_TIMEOUT_TICKS, entry.getValue().idleTimeoutTicksRemaining);
            conditionTag.putInt(CONDITION_CARGO_TIMEOUT_TICKS, entry.getValue().cargoTimeoutTicksRemaining);
            conditionTag.putBoolean(CONDITION_SATISFIED, entry.getValue().satisfied);
            entry.getValue().failure.ifPresent(failure -> conditionTag.putString(CONDITION_FAILURE, failure.name()));
            list.add(conditionTag);
        }
        return list;
    }

    private Map<ConditionKey, ConditionRuntimeState> readConditionStates(ListTag list) {
        Map<ConditionKey, ConditionRuntimeState> states = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag conditionTag = list.getCompound(i);
            ConditionKey key = new ConditionKey(
                    Math.max(0, conditionTag.getInt(CONDITION_GROUP_INDEX)),
                    Math.max(0, conditionTag.getInt(CONDITION_INDEX))
            );
            ConditionRuntimeState state = new ConditionRuntimeState(
                    Math.max(0, conditionTag.getInt(CONDITION_WAIT_TICKS)),
                    Math.max(0, conditionTag.getInt(CONDITION_IDLE_WINDOW_TICKS)),
                    Math.max(0, conditionTag.getInt(CONDITION_IDLE_TIMEOUT_TICKS)),
                    Math.max(0, conditionTag.getInt(CONDITION_CARGO_TIMEOUT_TICKS)),
                    conditionTag.getBoolean(CONDITION_SATISFIED),
                    readNamedPlaybackFailure(conditionTag, CONDITION_FAILURE)
            );
            states.put(key, state);
        }
        return states;
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

    private enum PauseState {
        NONE,
        HELD_TRANSIENT,
        HELD_MANUAL,
        HELD_FAULTED,
        RELEASED
    }

    private enum RuntimeMode {
        LOADED,
        UNLOADED_TRANSIT
    }

    public record RuntimePlaybackSummary(
            RouteId routeId,
            String routeName,
            String shipName,
            Optional<UUID> ownerId,
            String state,
            ResourceKey<Level> dimension,
            Optional<BlockPos> stationPos,
            Optional<UUID> transponderId,
            Optional<BlockPos> transponderPos,
            Optional<Vec3> position,
            Optional<UUID> vehicleId,
            Optional<BlockPos> controllerPos,
            int restoreCooldownTicks
        ) {
        private static RuntimePlaybackSummary fromActive(
                ActivePlayback activePlayback,
                String shipName,
                Optional<UUID> transponderId,
                Optional<BlockPos> transponderPos
        ) {
            return new RuntimePlaybackSummary(
                    activePlayback.route().id(),
                    activePlayback.route().name(),
                    shipName,
                    activePlayback.route().ownerId(),
                    activePlaybackState(activePlayback),
                    activePlayback.route().dimension(),
                    Optional.of(activePlayback.stationPos().immutable()),
                    transponderId,
                    transponderPos.map(BlockPos::immutable),
                    Optional.of(activePlayback.holdPosition()),
                    activePlayback.route().linkedController().vehicleId(),
                    activePlayback.route().linkedController().controllerPos().map(BlockPos::immutable),
                    0
            );
        }

        private static Optional<RuntimePlaybackSummary> fromPending(
                RouteId routeId,
                CompoundTag runtimeTag,
                int restoreCooldownTicks,
                java.util.function.Function<Route, String> shipNameLookup,
                java.util.function.Function<Route, Optional<UUID>> transponderIdLookup,
                java.util.function.Function<Route, Optional<BlockPos>> transponderPosLookup
        ) {
            Optional<Route> route = runtimeTag.contains(ROUTE, Tag.TAG_COMPOUND)
                    ? RouteNbtSerializer.read(runtimeTag.getCompound(ROUTE))
                    : Optional.empty();
            if (route.isEmpty()) {
                return Optional.empty();
            }
            Optional<BlockPos> stationPos = runtimeTag.contains(STATION_POS, Tag.TAG_COMPOUND)
                    ? Optional.of(NbtUtils.readBlockPos(runtimeTag, STATION_POS).orElse(BlockPos.ZERO).immutable())
                    : Optional.empty();
            Optional<Vec3> position = runtimeTag.contains(HOLD_POSITION, Tag.TAG_COMPOUND)
                    ? Optional.of(readStaticVec3(runtimeTag.getCompound(HOLD_POSITION)))
                    : Optional.empty();
            return Optional.of(new RuntimePlaybackSummary(
                    routeId,
                    route.get().name(),
                    shipNameLookup.apply(route.get()),
                    route.get().ownerId(),
                    pendingState(runtimeTag),
                    route.get().dimension(),
                    stationPos,
                    transponderIdLookup.apply(route.get()),
                    transponderPosLookup.apply(route.get()).map(BlockPos::immutable),
                    position,
                    route.get().linkedController().vehicleId(),
                    route.get().linkedController().controllerPos().map(BlockPos::immutable),
                    Math.max(0, restoreCooldownTicks)
            ));
        }

        private static String activePlaybackState(ActivePlayback activePlayback) {
            if (activePlayback.isDockQueueHeld()) {
                return "DOCK_QUEUED";
            }
            if (activePlayback.isPaused()) {
                return "PAUSED";
            }
            if (activePlayback.runtimeMode() == RuntimeMode.UNLOADED_TRANSIT) {
                return "UNLOADED_TRANSIT";
            }
            if (activePlayback.isWaiting()) {
                return "WAITING";
            }
            if (activePlayback.completed()) {
                return "COMPLETED";
            }
            return "ACTIVE";
        }

        private static String pendingState(CompoundTag runtimeTag) {
            PauseState pauseState = readStaticPauseState(runtimeTag);
            RuntimeMode runtimeMode = readStaticRuntimeMode(runtimeTag);
            if (pauseState != PauseState.NONE && pauseState != PauseState.RELEASED) {
                return "PENDING_" + pauseState.name();
            }
            if (runtimeMode == RuntimeMode.UNLOADED_TRANSIT) {
                return "PENDING_UNLOADED_TRANSIT";
            }
            return "PENDING_RESTORE";
        }
    }

    private static Vec3 readStaticVec3(CompoundTag tag) {
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    private static PauseState readStaticPauseState(CompoundTag tag) {
        if (!tag.contains(PAUSE_STATE, Tag.TAG_STRING)) {
            return PauseState.NONE;
        }
        try {
            return PauseState.valueOf(tag.getString(PAUSE_STATE));
        } catch (IllegalArgumentException ignored) {
            return PauseState.NONE;
        }
    }

    private static RuntimeMode readStaticRuntimeMode(CompoundTag tag) {
        if (!tag.contains(RUNTIME_MODE, Tag.TAG_STRING)) {
            return RuntimeMode.LOADED;
        }
        try {
            return RuntimeMode.valueOf(tag.getString(RUNTIME_MODE));
        } catch (IllegalArgumentException ignored) {
            return RuntimeMode.LOADED;
        }
    }

    private record ConditionKey(int groupIndex, int conditionIndex) {
    }

    private static final class ConditionRuntimeState {
        private int waitTicksRemaining;
        private int idleWindowTicks;
        private int idleTimeoutTicksRemaining;
        private int cargoTimeoutTicksRemaining;
        private Optional<DockTransferSnapshot> dockTransferSnapshot = Optional.empty();
        private boolean satisfied;
        private Optional<PlaybackFailure> failure;

        private ConditionRuntimeState(
                int waitTicksRemaining,
                int idleWindowTicks,
                int idleTimeoutTicksRemaining,
                int cargoTimeoutTicksRemaining,
                boolean satisfied,
                Optional<PlaybackFailure> failure
        ) {
            this.waitTicksRemaining = Math.max(0, waitTicksRemaining);
            this.idleWindowTicks = Math.max(0, idleWindowTicks);
            this.idleTimeoutTicksRemaining = Math.max(0, idleTimeoutTicksRemaining);
            this.cargoTimeoutTicksRemaining = Math.max(0, cargoTimeoutTicksRemaining);
            this.satisfied = satisfied;
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        private static ConditionRuntimeState initialize(WaitCondition waitCondition) {
            int waitTicks = isCargoWaitType(waitCondition.type())
                    ? Math.max(0, waitCondition.cargoStabilityTicks())
                    : Math.max(0, waitCondition.runtimeTicks());
            if (waitCondition.type() == WaitConditionType.UNTIL_IDLE && waitTicks <= 0) {
                waitTicks = WaitCondition.DEFAULT_TIMED_WAIT_TICKS;
            }
            int idleTimeout = waitCondition.type() == WaitConditionType.UNTIL_IDLE
                    ? (waitCondition.maxTicks() > 0
                    ? waitCondition.maxTicks()
                    : AutomatedLogisticsConfig.DOCK_IDLE_TIMEOUT_TICKS.get())
                    : 0;
            int cargoTimeout = isCargoWaitType(waitCondition.type())
                    ? Math.max(0, waitCondition.maxTicks())
                    : 0;
            return new ConditionRuntimeState(waitTicks, waitTicks, idleTimeout, cargoTimeout, false, Optional.empty());
        }

        private void markSatisfied() {
            this.satisfied = true;
            this.failure = Optional.empty();
        }

        private void markFailure(PlaybackFailure failure) {
            this.satisfied = false;
            this.failure = Optional.of(failure);
        }

        private void clearFailure() {
            this.failure = Optional.empty();
        }

        private boolean tickWait() {
            if (waitTicksRemaining > 0) {
                waitTicksRemaining--;
            }
            return waitTicksRemaining <= 0;
        }

        private void resetIdleWait() {
            waitTicksRemaining = Math.max(1, idleWindowTicks);
        }

        private void resetWaitWindow() {
            waitTicksRemaining = Math.max(0, idleWindowTicks);
        }

        private String summary() {
            return "wait="
                    + waitTicksRemaining
                    + "/"
                    + idleWindowTicks
                    + ",idleTimeout="
                    + idleTimeoutTicksRemaining
                    + ",cargoTimeout="
                    + cargoTimeoutTicksRemaining
                    + ",satisfied="
                    + satisfied
                    + ",failure="
                    + failure.map(Enum::name).orElse("none");
        }

        private boolean tickIdleTimeout() {
            if (idleTimeoutTicksRemaining > 0) {
                idleTimeoutTicksRemaining--;
            }
            return idleTimeoutTicksRemaining <= 0;
        }

        private boolean tickCargoTimeout() {
            if (cargoTimeoutTicksRemaining <= 0) {
                return false;
            }
            if (cargoTimeoutTicksRemaining > 0) {
                cargoTimeoutTicksRemaining--;
            }
            return cargoTimeoutTicksRemaining <= 0;
        }
    }

    private record ConditionTickResult(boolean satisfied, Optional<PlaybackFailure> failure) {
        private static ConditionTickResult completedResult() {
            return new ConditionTickResult(true, Optional.empty());
        }

        private static ConditionTickResult pendingResult() {
            return new ConditionTickResult(false, Optional.empty());
        }

        private static ConditionTickResult failedResult(PlaybackFailure failure) {
            return new ConditionTickResult(false, Optional.of(failure));
        }
    }

    private record DeferredDockOutputClear(
            Route completedRoute,
            BlockPos fallbackStationPos,
            Optional<BlockPos> activeDockStationPos
    ) {
    }

    private record DockQueueGateResult(boolean held, Optional<PlaybackFailure> failure) {
        private static DockQueueGateResult ready() {
            return new DockQueueGateResult(false, Optional.empty());
        }

        private static DockQueueGateResult queued() {
            return new DockQueueGateResult(true, Optional.empty());
        }

        private static DockQueueGateResult failed(PlaybackFailure failure) {
            return new DockQueueGateResult(false, Optional.of(failure));
        }
    }

    private record PendingDockRestoreGate(
            Optional<RouteStop> stop,
            boolean queued,
            boolean blocked,
            int queuePosition,
            String reason
    ) {
        private static PendingDockRestoreGate notRequired() {
            return new PendingDockRestoreGate(Optional.empty(), false, false, -1, "not_required");
        }

        private static PendingDockRestoreGate granted(RouteStop stop) {
            return new PendingDockRestoreGate(Optional.of(stop), false, false, 0, "granted");
        }

        private static PendingDockRestoreGate queued(RouteStop stop, int queuePosition) {
            return new PendingDockRestoreGate(Optional.of(stop), true, false, queuePosition, "queued");
        }

        private static PendingDockRestoreGate blocked(RouteStop stop, String reason) {
            return new PendingDockRestoreGate(Optional.of(stop), false, true, -1, reason);
        }

        private String summary() {
            return reason + (queuePosition > 0 ? ":" + queuePosition : "");
        }
    }

    private static final class ActivePlayback {
        private final Route route;
        private final BlockPos stationPos;
        private VehicleController controller;
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
        private long dockTimeoutLastSampleNanos;
        private double dockTimeoutTickAccumulator;
        private int dockIdleTimeoutTicksRemaining;
        private int dockCargoTimeoutTicksRemaining;
        private boolean dockLocked;
        private boolean dockReacquireMotionActive;
        private boolean dockReacquireReleasedControl;
        private final Map<ConditionKey, ConditionRuntimeState> conditionStates = new HashMap<>();
        private Optional<PlaybackFailure> dockWaitFailure = Optional.empty();
        private int restoreCatchTicksRemaining;
        private int routeStartStabilizationTicksRemaining;
        private Optional<DockTransferSnapshot> dockTransferSnapshot = Optional.empty();
        private boolean cargoSatisfiedThisTick;
        private PauseState pauseState = PauseState.NONE;
        private Optional<PlaybackFailure> heldFailure = Optional.empty();
        private Vec3 holdPosition;
        private Optional<RouteRotation> holdRotation;
        private boolean completed;
        private int physicsGuidanceLogCooldown;
        private int waitDiagnosticLogCooldown;
        private RuntimeMode runtimeMode = RuntimeMode.LOADED;
        private OptionalInt validatedLegStartIndex = OptionalInt.empty();
        private OptionalInt validatedLegTargetIndex = OptionalInt.empty();
        private Optional<RouteStop> dockQueueStop = Optional.empty();
        private boolean dockQueueHoldLivePose = true;
        private int dockQueueSoftHoldTicksRemaining;
        private OptionalInt dockReservationReleasePoint = OptionalInt.empty();
        private Optional<Vec3> dockReservationReleaseOrigin = Optional.empty();
        private Optional<BlockPos> dockReservationReleaseDockPos = Optional.empty();
        private int dockReservationReleaseDirection;
        private int unloadedMaterializeCooldownTicks;
        private boolean storedPointerCleanupNeeded = true;

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
            this.holdPosition = controller.position();
            this.holdRotation = controller.routeRotation();
            resetSegmentTiming();
        }

        private static ActivePlayback create(Route route, BlockPos stationPos, VehicleController controller) {
            Vec3 vehiclePosition = controller.position();
            Optional<RouteRotation> vehicleRotation = controller.routeRotation();
            Vec3 first = route.points().getFirst().position();
            Vec3 last = route.points().getLast().position();
            if (vehiclePosition.distanceToSqr(first) <= vehiclePosition.distanceToSqr(last)) {
                ActivePlayback playback = new ActivePlayback(route, stationPos, controller, vehiclePosition.subtract(first), vehicleRotation, 1, 1);
                playback.routeStartStabilizationTicksRemaining = ROUTE_START_STABILIZATION_TICKS;
                return playback;
            }
            int lastIndex = route.points().size() - 1;
            ActivePlayback playback = new ActivePlayback(
                    route,
                    stationPos,
                    controller,
                    vehiclePosition.subtract(last),
                    vehicleRotation,
                    lastIndex - 1,
                    -1
            );
            playback.routeStartStabilizationTicksRemaining = ROUTE_START_STABILIZATION_TICKS;
            return playback;
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
                Map<ConditionKey, ConditionRuntimeState> conditionStates,
                Optional<PlaybackFailure> dockWaitFailure,
                int restoreCatchTicksRemaining,
                PauseState pauseState,
                Optional<PlaybackFailure> heldFailure,
                Vec3 holdPosition,
                Optional<RouteRotation> holdRotation,
                Optional<RouteStop> dockQueueStop,
                boolean dockQueueHoldLivePose,
                boolean completed,
                RuntimeMode runtimeMode,
                OptionalInt validatedLegStartIndex,
                OptionalInt validatedLegTargetIndex
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
            activePlayback.resetDockTimeoutClock();
            activePlayback.dockIdleTimeoutTicksRemaining = Math.max(0, dockIdleTimeoutTicksRemaining);
            activePlayback.dockCargoTimeoutTicksRemaining = Math.max(0, dockCargoTimeoutTicksRemaining);
            activePlayback.dockLocked = dockLocked;
            activePlayback.conditionStates.clear();
            activePlayback.conditionStates.putAll(conditionStates);
            activePlayback.dockWaitFailure = dockWaitFailure;
            activePlayback.cargoSatisfiedThisTick = false;
            activePlayback.restoreCatchTicksRemaining = Math.max(0, restoreCatchTicksRemaining);
            activePlayback.routeStartStabilizationTicksRemaining = 0;
            activePlayback.pauseState = pauseState;
            activePlayback.heldFailure = heldFailure;
            activePlayback.holdPosition = holdPosition;
            activePlayback.holdRotation = holdRotation;
            activePlayback.dockQueueStop = dockQueueStop;
            activePlayback.dockQueueHoldLivePose = dockQueueHoldLivePose;
            activePlayback.completed = completed;
            activePlayback.runtimeMode = runtimeMode;
            activePlayback.validatedLegStartIndex = validatedLegStartIndex;
            activePlayback.validatedLegTargetIndex = validatedLegTargetIndex;
            activePlayback.dockTransferSnapshot = Optional.empty();
            if (activePlayback.waitingStop.isPresent() && activePlayback.conditionStates.isEmpty()) {
                activePlayback.restoreLegacyConditionState();
            }
            if (activePlayback.waitingStop.isPresent()
                    && activePlayback.requiresDockLock()
                    && activePlayback.dockLocked
                    && activePlayback.dockWaitFailure.isEmpty()) {
                activePlayback.restartDockConditionTimersFromSchedule("restore_dock_locked_wait");
            }
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

        private VehicleController controller(ServerLevel level) {
            if (!controller.isLoaded(level) || !controller.isAssembled()) {
                resolveLiveController(level, route, "active_playback_controller_refresh", "live_controller_refresh")
                        .ifPresent(resolved -> controller = resolved);
            }
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

        private Optional<RouteStop> stopAtTargetPoint() {
            return route.stops().stream()
                    .filter(routeStop -> routeStop.pointIndex() == targetIndex)
                    .findFirst();
        }

        private Optional<BlockPos> targetStationPos() {
            return stopAtTargetPoint().flatMap(RouteStop::dockPos);
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
            double speed = adjacentSegmentSpeedBlocksPerTick();
            if (routeStartStabilizationTicksRemaining > 0) {
                return Math.max(speed, WAIT_HOLD_SPEED);
            }
            return speed;
        }

        private void tickRouteStartStabilization() {
            if (routeStartStabilizationTicksRemaining > 0) {
                routeStartStabilizationTicksRemaining--;
            }
        }

        private void beginRouteStartStabilization() {
            routeStartStabilizationTicksRemaining = ROUTE_START_STABILIZATION_TICKS;
        }

        private boolean isRouteStartStabilizing() {
            return routeStartStabilizationTicksRemaining > 0;
        }

        private boolean shouldLogPhysicsGuidance() {
            if (physicsGuidanceLogCooldown > 0) {
                physicsGuidanceLogCooldown--;
                return false;
            }
            physicsGuidanceLogCooldown = 40;
            return true;
        }

        private boolean shouldLogWaitDiagnostic() {
            if (waitDiagnosticLogCooldown > 0) {
                waitDiagnosticLogCooldown--;
                return false;
            }
            waitDiagnosticLogCooldown = 40;
            return true;
        }

        private boolean needsStoredPointerCleanup() {
            return storedPointerCleanupNeeded;
        }

        private void markStoredPointerCleanupNeeded() {
            storedPointerCleanupNeeded = true;
        }

        private void markStoredPointerCleanupComplete() {
            storedPointerCleanupNeeded = false;
        }

        private double adjacentSegmentSpeedBlocksPerTick() {
            int previousIndex = targetIndex - direction;
            if (previousIndex < 0 || previousIndex >= route.points().size()) {
                return 0.0D;
            }
            if (isStationarySegment()) {
                return MIN_EFFECTIVE_REPLAY_SPEED;
            }

            RoutePoint previous = route.points().get(previousIndex);
            RoutePoint target = targetPoint();
            long ticks = Math.max(1L, Math.abs(target.tickOffset() - previous.tickOffset()));
            double speed = previous.position().distanceTo(target.position()) / ticks;
            return Math.min(MAX_REPLAY_SPEED, Math.max(MIN_EFFECTIVE_REPLAY_SPEED, speed));
        }

        private void advanceTarget() {
            clearValidatedLeg();
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

        private Optional<RouteStop> dockReservationStopForCurrentLeg() {
            int startIndex = currentLegStartIndex();
            double clearanceDistance = AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get();
            return route.stops().stream()
                    .filter(this::stopRequiresDockLock)
                    .filter(stop -> shouldGateDockApproach(startIndex, stop, clearanceDistance))
                    .findFirst();
        }

        private boolean shouldGateDockApproach(int startIndex, RouteStop stop, double clearanceDistance) {
            OptionalInt gatePoint = gatePointIndexForStop(stop, clearanceDistance);
            if (gatePoint.isEmpty()) {
                return false;
            }
            int gate = gatePoint.getAsInt();
            int stopIndex = stop.pointIndex();
            return direction >= 0
                    ? startIndex >= gate && startIndex < stopIndex
                    : startIndex <= gate && startIndex > stopIndex;
        }

        private boolean stopRequiresDockLock(RouteStop stop) {
            return stop.effectiveConditionGroups().stream()
                    .flatMap(List::stream)
                    .map(AirshipScheduleCondition::waitCondition)
                    .anyMatch(wait -> wait.type() == WaitConditionType.UNTIL_DOCKED
                            || wait.type() == WaitConditionType.UNTIL_IDLE
                            || isCargoWaitType(wait.type()));
        }

        private int currentLegStartIndex() {
            return targetIndex - direction;
        }

        private Vec3 currentLegStartPosition() {
            int startIndex = currentLegStartIndex();
            if (startIndex < 0 || startIndex >= route.points().size()) {
                return targetPosition();
            }
            return pointPosition(startIndex);
        }

        private Optional<RouteRotation> currentLegStartRotation() {
            int startIndex = currentLegStartIndex();
            if (startIndex < 0 || startIndex >= route.points().size()) {
                return targetRotation();
            }
            return pointRotation(startIndex);
        }

        private Vec3 dockQueueHoldPosition(VehicleController controller, boolean physicalHold) {
            if (physicalHold && controller != null) {
                return controller.position();
            }
            return currentLegStartPosition();
        }

        private Optional<RouteRotation> dockQueueHoldRotation(VehicleController controller, boolean physicalHold) {
            if (physicalHold && controller != null) {
                return controller.routeRotation();
            }
            return currentLegStartRotation();
        }

        private void markDockQueueHold(
                RouteStop stop,
                Vec3 holdPosition,
                Optional<RouteRotation> holdRotation,
                boolean livePose
        ) {
            dockQueueStop = Optional.of(stop);
            this.holdPosition = holdPosition;
            this.holdRotation = holdRotation;
            this.dockQueueHoldLivePose = livePose;
            heldFailure = Optional.empty();
        }

        private boolean isDockQueueHeld() {
            return dockQueueStop.isPresent();
        }

        private Optional<RouteStop> dockQueueStop() {
            return dockQueueStop;
        }

        private boolean usesLiveDockQueueHoldPose() {
            return dockQueueHoldLivePose;
        }

        private void beginDockQueueSoftHold() {
            dockQueueSoftHoldTicksRemaining = DOCK_QUEUE_SOFT_HOLD_TICKS;
        }

        private boolean isDockQueueSoftHolding() {
            return dockQueueSoftHoldTicksRemaining > 0;
        }

        private void tickDockQueueSoftHold() {
            if (dockQueueSoftHoldTicksRemaining > 0) {
                dockQueueSoftHoldTicksRemaining--;
            }
        }

        private void clearDockQueueHold() {
            dockQueueStop = Optional.empty();
            dockQueueHoldLivePose = true;
            dockQueueSoftHoldTicksRemaining = 0;
        }

        private void trackDockReservationRelease(
                RouteStop stop,
                Vec3 releaseOrigin,
                Optional<BlockPos> releaseDockPos
        ) {
            double clearanceDistance = AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get();
            OptionalInt releasePoint = releasePointIndexForStop(stop, clearanceDistance);
            dockReservationReleasePoint = releasePoint.isPresent()
                    ? releasePoint
                    : OptionalInt.of(stop.pointIndex());
            dockReservationReleaseOrigin = Optional.of(releaseOrigin);
            dockReservationReleaseDockPos = releaseDockPos.map(BlockPos::immutable);
            dockReservationReleaseDirection = direction;
        }

        private void trackDockReservationReleaseFromCurrentLeg(
                Vec3 releaseOrigin,
                Optional<BlockPos> releaseDockPos
        ) {
            double clearanceDistance = AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get();
            int departureAnchorIndex = currentLegStartIndex();
            OptionalInt releasePoint = traversePointIndexForDistance(departureAnchorIndex, direction, clearanceDistance);
            dockReservationReleasePoint = releasePoint.isPresent()
                    ? releasePoint
                    : OptionalInt.of(departureAnchorIndex);
            dockReservationReleaseOrigin = Optional.of(releaseOrigin);
            dockReservationReleaseDockPos = releaseDockPos.map(BlockPos::immutable);
            dockReservationReleaseDirection = direction;
        }

        private OptionalInt gatePointIndexForStop(RouteStop stop, double clearanceDistance) {
            return traversePointIndexForDistance(stop.pointIndex(), -direction, clearanceDistance);
        }

        private OptionalInt releasePointIndexForStop(RouteStop stop, double clearanceDistance) {
            return traversePointIndexForDistance(stop.pointIndex(), direction, clearanceDistance);
        }

        private OptionalInt traversePointIndexForDistance(int anchorPointIndex, int travelDirection, double requiredDistance) {
            if (route.points().isEmpty()) {
                return OptionalInt.empty();
            }
            int currentIndex = anchorPointIndex;
            int candidateIndex = anchorPointIndex;
            double travelledDistance = 0.0D;
            while (true) {
                int nextIndex = currentIndex + travelDirection;
                if (nextIndex < 0 || nextIndex >= route.points().size()) {
                    return candidateIndex == anchorPointIndex ? OptionalInt.empty() : OptionalInt.of(candidateIndex);
                }
                candidateIndex = nextIndex;
                travelledDistance += pointPosition(currentIndex).distanceTo(pointPosition(nextIndex));
                if (travelledDistance >= requiredDistance) {
                    return OptionalInt.of(candidateIndex);
                }
                currentIndex = nextIndex;
            }
        }

        private boolean shouldReleaseDockReservation(ServerLevel level) {
            if (dockReservationReleasePoint.isEmpty()) {
                return false;
            }
            int releasePoint = dockReservationReleasePoint.getAsInt();
            boolean reachedReleasePoint = dockReservationReleaseDirection >= 0
                    ? targetIndex >= releasePoint
                    : targetIndex <= releasePoint;
            if (!reachedReleasePoint) {
                return false;
            }

            if (runtimeMode != RuntimeMode.LOADED || dockReservationReleaseOrigin.isEmpty()) {
                return true;
            }

            double clearanceDistance = AutomatedLogisticsConfig.DOCK_RESERVATION_CLEARANCE_DISTANCE.get();
            double distanceFromDock = controller(level).position().distanceTo(dockReservationReleaseOrigin.get());
            if (distanceFromDock < clearanceDistance && shouldLogProgress()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} holding dock reservation past release point {} until physical clearance {} / {} from dock anchor {}",
                        route.id().value(),
                        releasePoint,
                        distanceFromDock,
                        clearanceDistance,
                        dockReservationReleaseOrigin.get()
                );
            }
            return distanceFromDock >= clearanceDistance;
        }

        private void clearDockReservationReleasePoint() {
            dockReservationReleasePoint = OptionalInt.empty();
            dockReservationReleaseOrigin = Optional.empty();
            dockReservationReleaseDockPos = Optional.empty();
            dockReservationReleaseDirection = 0;
        }

        private Optional<BlockPos> dockReservationReleaseDockPos() {
            return dockReservationReleaseDockPos;
        }

        private String dockReservationReleasePointSummary() {
            return dockReservationReleasePoint.isPresent()
                    ? Integer.toString(dockReservationReleasePoint.getAsInt())
                    : "none";
        }

        private boolean beginWaitAtCurrentTarget() {
            Optional<RouteStop> stop = route.stops().stream()
                    .filter(routeStop -> routeStop.pointIndex() == targetIndex)
                    .findFirst();
            if (stop.isEmpty()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} reached point {} with no route stop match. stops={}",
                        route.id().value(),
                        targetIndex,
                        routeStopSummary(route)
                );
                return false;
            }

            RouteStop routeStop = stop.get();
            if (routeStop.effectiveConditionGroups().isEmpty()) {
                CreateAeronauticsAutomatedLogistics.debugPlayback(
                        "Playback {} reached stop {} at point {} but it has no wait conditions",
                        route.id().value(),
                        routeStop.name(),
                        targetIndex
                );
                return false;
            }

            waitingStop = stop;
            conditionStates.clear();
            for (int groupIndex = 0; groupIndex < routeStop.effectiveConditionGroups().size(); groupIndex++) {
                List<AirshipScheduleCondition> group = routeStop.effectiveConditionGroups().get(groupIndex);
                for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                    WaitCondition waitCondition = group.get(conditionIndex).waitCondition();
                    conditionStates.put(
                            new ConditionKey(groupIndex, conditionIndex),
                            ConditionRuntimeState.initialize(waitCondition)
                    );
                }
            }
            waitTicksRemaining = Math.max(0, routeStop.waitCondition().runtimeTicks());
            if (routeStop.waitCondition().type() == WaitConditionType.UNTIL_IDLE && waitTicksRemaining <= 0) {
                waitTicksRemaining = WaitCondition.DEFAULT_TIMED_WAIT_TICKS;
            }
            idleWindowTicks = waitTicksRemaining;
            dockTimeoutTicksRemaining = requiresDockLock()
                    ? AutomatedLogisticsConfig.DOCK_LOCK_TIMEOUT_TICKS.get()
                    : 0;
            resetDockTimeoutClock();
            dockIdleTimeoutTicksRemaining = 0;
            dockCargoTimeoutTicksRemaining = 0;
            dockLocked = false;
            dockWaitFailure = Optional.empty();
            dockTransferSnapshot = Optional.empty();
            cargoSatisfiedThisTick = false;
            if (!requiresStationContext() && allConditionsSatisfied()) {
                waitingStop = Optional.empty();
                return false;
            }
            return true;
        }

        private void restoreLegacyConditionState() {
            RouteStop routeStop = waitingStop.orElse(null);
            if (routeStop == null) {
                return;
            }
            List<List<AirshipScheduleCondition>> groups = routeStop.effectiveConditionGroups();
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                List<AirshipScheduleCondition> group = groups.get(groupIndex);
                for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                    WaitCondition waitCondition = group.get(conditionIndex).waitCondition();
                    ConditionRuntimeState state = ConditionRuntimeState.initialize(waitCondition);
                    if (groupIndex == 0 && conditionIndex == 0) {
                        state.waitTicksRemaining = Math.max(0, waitTicksRemaining);
                        state.idleWindowTicks = Math.max(0, idleWindowTicks);
                        state.idleTimeoutTicksRemaining = Math.max(0, dockIdleTimeoutTicksRemaining);
                        state.cargoTimeoutTicksRemaining = Math.max(0, dockCargoTimeoutTicksRemaining);
                    }
                    conditionStates.put(new ConditionKey(groupIndex, conditionIndex), state);
                }
            }
        }

        private List<List<AirshipScheduleCondition>> conditionGroups() {
            return waitingStop.map(RouteStop::effectiveConditionGroups).orElse(List.of());
        }

        private ConditionRuntimeState conditionState(int groupIndex, int conditionIndex, WaitCondition waitCondition) {
            return conditionStates.computeIfAbsent(
                    new ConditionKey(groupIndex, conditionIndex),
                    ignored -> ConditionRuntimeState.initialize(waitCondition)
            );
        }

        private boolean allConditionsSatisfied() {
            List<List<AirshipScheduleCondition>> groups = conditionGroups();
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                List<AirshipScheduleCondition> group = groups.get(groupIndex);
                boolean groupSatisfied = true;
                for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                    ConditionRuntimeState state = conditionStates.get(new ConditionKey(groupIndex, conditionIndex));
                    if (state == null || !state.satisfied) {
                        groupSatisfied = false;
                        break;
                    }
                }
                if (groupSatisfied) {
                    return true;
                }
            }
            return false;
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

        private RuntimeMode runtimeMode() {
            return runtimeMode;
        }

        private boolean isUnloadedTransit() {
            return runtimeMode == RuntimeMode.UNLOADED_TRANSIT;
        }

        private void enterUnloadedTransit() {
            runtimeMode = RuntimeMode.UNLOADED_TRANSIT;
            restoreCatchTicksRemaining = 0;
            unloadedMaterializeCooldownTicks = Math.min(unloadedMaterializeCooldownTicks, UNLOADED_MATERIALIZE_RETRY_TICKS);
        }

        private void leaveUnloadedTransit() {
            runtimeMode = RuntimeMode.LOADED;
            unloadedMaterializeCooldownTicks = 0;
        }

        private boolean canAttemptUnloadedMaterialize(boolean force) {
            if (force) {
                unloadedMaterializeCooldownTicks = UNLOADED_MATERIALIZE_RETRY_TICKS;
                return true;
            }
            if (unloadedMaterializeCooldownTicks > 0) {
                unloadedMaterializeCooldownTicks--;
                return false;
            }
            unloadedMaterializeCooldownTicks = UNLOADED_MATERIALIZE_RETRY_TICKS;
            return true;
        }

        private OptionalInt validatedLegStartIndex() {
            return validatedLegStartIndex;
        }

        private OptionalInt validatedLegTargetIndex() {
            return validatedLegTargetIndex;
        }

        private void validateLeg(int startIndex, int targetIndex) {
            validatedLegStartIndex = OptionalInt.of(startIndex);
            validatedLegTargetIndex = OptionalInt.of(targetIndex);
        }

        private void clearValidatedLeg() {
            validatedLegStartIndex = OptionalInt.empty();
            validatedLegTargetIndex = OptionalInt.empty();
        }

        private PauseState pauseState() {
            return pauseState;
        }

        private boolean isPaused() {
            return pauseState != PauseState.NONE;
        }

        private boolean isTransientHold() {
            return pauseState == PauseState.HELD_TRANSIENT;
        }

        private boolean isFaultHold() {
            return pauseState == PauseState.HELD_FAULTED;
        }

        private boolean isHoldLocked() {
            return pauseState == PauseState.HELD_MANUAL || pauseState == PauseState.HELD_FAULTED;
        }

        private Optional<PlaybackFailure> heldFailure() {
            return heldFailure;
        }

        private Optional<CargoFailureContext> heldCargoFailureContext() {
            Optional<PlaybackFailure> failure = heldFailure;
            if (failure.filter(value -> value == PlaybackFailure.CARGO_STORAGE_MISSING
                    || value == PlaybackFailure.CARGO_CONDITION_TIMEOUT).isEmpty()) {
                return Optional.empty();
            }

            List<List<AirshipScheduleCondition>> groups = conditionGroups();
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                List<AirshipScheduleCondition> group = groups.get(groupIndex);
                for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                    ConditionRuntimeState state = conditionStates.get(new ConditionKey(groupIndex, conditionIndex));
                    if (state == null || state.failure.isEmpty() || state.failure.get() != failure.get()) {
                        continue;
                    }

                    WaitCondition waitCondition = group.get(conditionIndex).waitCondition();
                    if (isCargoWaitType(waitCondition.type())) {
                        return Optional.of(new CargoFailureContext(waitCondition.cargoTarget(), waitCondition.type()));
                    }
                }
            }

            return groups.stream()
                    .flatMap(List::stream)
                    .map(AirshipScheduleCondition::waitCondition)
                    .filter(waitCondition -> isCargoWaitType(waitCondition.type()))
                    .map(waitCondition -> new CargoFailureContext(waitCondition.cargoTarget(), waitCondition.type()))
                    .findFirst();
        }

        private Vec3 holdPosition() {
            return holdPosition;
        }

        private Optional<RouteRotation> holdRotation() {
            return holdRotation;
        }

        private void captureHoldPose(VehicleController controller) {
            holdPosition = controller.position();
            holdRotation = controller.routeRotation();
        }

        private Vec3 waitHoldPosition(VehicleController controller) {
            if (isWaiting() && requiresDockLock() && dockLocked()) {
                return holdPosition;
            }
            return targetPosition();
        }

        private Optional<RouteRotation> waitHoldRotation(VehicleController controller) {
            if (isWaiting() && requiresDockLock() && dockLocked()) {
                return holdRotation;
            }
            return targetRotation();
        }

        private void pauseTransient(PlaybackFailure failure) {
            pauseState = PauseState.HELD_TRANSIENT;
            heldFailure = Optional.of(failure);
            restoreCatchTicksRemaining = 0;
        }

        private void pauseManual() {
            pauseState = PauseState.HELD_MANUAL;
            heldFailure = Optional.empty();
            holdPosition = controller.position();
            holdRotation = controller.routeRotation();
            restoreCatchTicksRemaining = 0;
        }

        private void pauseFault(PlaybackFailure failure) {
            pauseState = PauseState.HELD_FAULTED;
            heldFailure = Optional.of(failure);
            holdPosition = controller.position();
            holdRotation = controller.routeRotation();
            restoreCatchTicksRemaining = 0;
        }

        private void resumeFromPause() {
            pauseState = PauseState.NONE;
            heldFailure = Optional.empty();
            holdPosition = controller.position();
            holdRotation = controller.routeRotation();
        }

        private void clearRecoverableWaitFailures() {
            dockWaitFailure = Optional.empty();
            cargoSatisfiedThisTick = false;
            for (ConditionRuntimeState state : conditionStates.values()) {
                state.clearFailure();
            }
        }

        private void resetDockHandshake() {
            if (!isWaiting() || !requiresDockLock()) {
                return;
            }
            dockLocked = false;
            dockReacquireMotionActive = false;
            dockReacquireReleasedControl = false;
            dockWaitFailure = Optional.empty();
            activeDockStationPos = Optional.empty();
            dockTransferSnapshot = Optional.empty();
            dockTimeoutTicksRemaining = AutomatedLogisticsConfig.DOCK_LOCK_TIMEOUT_TICKS.get();
            resetDockTimeoutClock();
        }

        private void prepareDockApproachAfterQueueRelease() {
            if (!isWaiting() || !requiresDockLock()) {
                return;
            }
            dockLocked = false;
            dockWaitFailure = Optional.empty();
            activeDockStationPos = Optional.empty();
            dockTransferSnapshot = Optional.empty();
            dockTimeoutTicksRemaining = AutomatedLogisticsConfig.DOCK_LOCK_TIMEOUT_TICKS.get();
            resetDockTimeoutClock();
            beginDockReacquireMotion();
        }

        private void releaseFromHold() {
            pauseState = PauseState.RELEASED;
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

        private boolean requiresStationContext() {
            return conditionGroups().stream()
                    .flatMap(List::stream)
                    .map(AirshipScheduleCondition::waitCondition)
                    .anyMatch(wait -> wait.type() == WaitConditionType.UNTIL_DOCKED
                            || wait.type() == WaitConditionType.UNTIL_IDLE
                            || isCargoWaitType(wait.type()));
        }

        private boolean requiresDockLock() {
            return conditionGroups().stream()
                    .flatMap(List::stream)
                    .map(AirshipScheduleCondition::waitCondition)
                    .anyMatch(wait -> wait.type() == WaitConditionType.UNTIL_DOCKED
                            || wait.type() == WaitConditionType.UNTIL_IDLE
                            || isCargoWaitType(wait.type()));
        }

        private Map<ConditionKey, ConditionRuntimeState> conditionStates() {
            return conditionStates;
        }

        private Optional<PlaybackFailure> dockWaitFailure() {
            return dockWaitFailure;
        }

        private void dockWaitFailure(Optional<PlaybackFailure> dockWaitFailure) {
            this.dockWaitFailure = dockWaitFailure;
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

        private void cancelRestoreCatch() {
            restoreCatchTicksRemaining = 0;
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
            boolean newlyLocked = dockLocked && !this.dockLocked;
            this.dockLocked = dockLocked;
            if (dockLocked) {
                dockReacquireMotionActive = false;
                dockReacquireReleasedControl = false;
                if (newlyLocked) {
                    resetDockConditionTimersOnPhysicalLock();
                }
            }
        }

        private void resetDockConditionTimersOnPhysicalLock() {
            conditionStates.values().forEach(ConditionRuntimeState::resetWaitWindow);
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} reset dock condition timers after physical dock lock at stop {} timers={}",
                    route.id().value(),
                    waitingStop.map(RouteStop::name).orElse("unknown"),
                    conditionTimerSummary()
            );
        }

        private void restartDockConditionTimersFromSchedule(String reason) {
            RouteStop routeStop = waitingStop.orElse(null);
            if (routeStop == null) {
                return;
            }
            conditionStates.clear();
            for (int groupIndex = 0; groupIndex < routeStop.effectiveConditionGroups().size(); groupIndex++) {
                List<AirshipScheduleCondition> group = routeStop.effectiveConditionGroups().get(groupIndex);
                for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                    WaitCondition waitCondition = group.get(conditionIndex).waitCondition();
                    conditionStates.put(
                            new ConditionKey(groupIndex, conditionIndex),
                            ConditionRuntimeState.initialize(waitCondition)
                    );
                }
            }
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Playback {} restarted dock wait timers from schedule at stop {} after restore reason={} timers={}",
                    route.id().value(),
                    routeStop.name(),
                    reason,
                    conditionTimerSummary()
            );
        }

        private String conditionTimerSummary() {
            if (conditionStates.isEmpty()) {
                return "none";
            }
            return conditionStates.entrySet().stream()
                    .map(entry -> entry.getKey().groupIndex()
                            + "."
                            + entry.getKey().conditionIndex()
                            + "="
                            + entry.getValue().summary())
                    .collect(java.util.stream.Collectors.joining(","));
        }

        private String conditionGroupSummary() {
            List<List<AirshipScheduleCondition>> groups = conditionGroups();
            if (groups.isEmpty()) {
                return "none";
            }
            StringBuilder builder = new StringBuilder();
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                if (groupIndex > 0) {
                    builder.append(";");
                }
                builder.append(groupIndex).append("[");
                List<AirshipScheduleCondition> group = groups.get(groupIndex);
                for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                    if (conditionIndex > 0) {
                        builder.append("+");
                    }
                    builder.append(conditionIndex)
                            .append(":")
                            .append(group.get(conditionIndex).waitCondition().type().name());
                }
                builder.append("]");
            }
            return builder.toString();
        }

        private void beginDockReacquireMotion() {
            if (isWaiting() && requiresDockLock()) {
                dockReacquireMotionActive = true;
                dockReacquireReleasedControl = false;
            }
        }

        private void endDockReacquireMotion() {
            dockReacquireMotionActive = false;
            dockReacquireReleasedControl = false;
        }

        private boolean isDockReacquireMotionActive() {
            return dockReacquireMotionActive;
        }

        private boolean hasReleasedDockReacquireControl() {
            return dockReacquireReleasedControl;
        }

        private void markDockReacquireControlReleased() {
            dockReacquireReleasedControl = true;
        }

        private Optional<DockTransferSnapshot> dockTransferSnapshot() {
            return dockTransferSnapshot;
        }

        private void dockTransferSnapshot(DockTransferSnapshot dockTransferSnapshot) {
            this.dockTransferSnapshot = Optional.of(dockTransferSnapshot);
        }

        private boolean cargoSatisfiedThisTick() {
            return cargoSatisfiedThisTick;
        }

        private void cargoSatisfiedThisTick(boolean cargoSatisfiedThisTick) {
            this.cargoSatisfiedThisTick = cargoSatisfiedThisTick;
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
            long now = System.nanoTime();
            if (dockTimeoutLastSampleNanos == 0L) {
                dockTimeoutLastSampleNanos = now;
                return dockTimeoutTicksRemaining <= 0;
            }

            double elapsedTicks = Math.min(
                    1.0D,
                    Math.max(0.0D, (now - dockTimeoutLastSampleNanos) / NOMINAL_TICK_NANOS)
            );
            dockTimeoutLastSampleNanos = now;
            dockTimeoutTickAccumulator += elapsedTicks;
            int elapsedWholeTicks = (int) dockTimeoutTickAccumulator;
            if (elapsedWholeTicks > 0) {
                dockTimeoutTicksRemaining = Math.max(0, dockTimeoutTicksRemaining - elapsedWholeTicks);
                dockTimeoutTickAccumulator -= elapsedWholeTicks;
            }
            return dockTimeoutTicksRemaining <= 0;
        }

        private void resetDockTimeoutClock() {
            dockTimeoutLastSampleNanos = 0L;
            dockTimeoutTickAccumulator = 0.0D;
        }

        private boolean tickIdleTimeout() {
            if (dockIdleTimeoutTicksRemaining > 0) {
                dockIdleTimeoutTicksRemaining--;
            }
            return dockIdleTimeoutTicksRemaining <= 0;
        }

        private boolean tickCargoTimeout() {
            if (dockCargoTimeoutTicksRemaining <= 0) {
                return false;
            }
            if (dockCargoTimeoutTicksRemaining > 0) {
                dockCargoTimeoutTicksRemaining--;
            }
            return dockCargoTimeoutTicksRemaining <= 0;
        }

        private void clearWait() {
            waitingStop = Optional.empty();
            conditionStates.clear();
            waitTicksRemaining = 0;
            idleWindowTicks = 0;
            dockTimeoutTicksRemaining = 0;
            dockIdleTimeoutTicksRemaining = 0;
            dockCargoTimeoutTicksRemaining = 0;
            dockLocked = false;
            dockWaitFailure = Optional.empty();
            dockTransferSnapshot = Optional.empty();
            cargoSatisfiedThisTick = false;
        }

        private boolean isCurrentSegmentElapsed() {
            return segmentElapsedTicks >= segmentDurationTicks;
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

        private boolean shouldRestoreToTargetPoint() {
            return isWaiting() || completed() || isPreciseArrivalPoint(targetIndex);
        }

        private String restoreCatchMode() {
            return shouldRestoreToTargetPoint() ? "target" : "guidance";
        }

        private Vec3 restoreCatchPosition() {
            return shouldRestoreToTargetPoint() ? targetPosition() : guidancePosition();
        }

        private Optional<RouteRotation> restoreCatchRotation() {
            return shouldRestoreToTargetPoint() ? targetRotation() : guidanceRotation();
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
                    && (rotationAligned || targetRotation().isEmpty() && isStationarySegment())
                    && distanceToTarget <= ENDPOINT_SETTLE_DISTANCE;
        }

        private boolean shouldWatchProgress() {
            return !isStationarySegment();
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

        private Optional<PlaybackFailure> checkStalled(double currentDistance) {
            if (segmentElapsedTicks <= overdueSegmentThresholdTicks()) {
                resetProgress(currentDistance);
                return Optional.empty();
            }

            if (currentDistance < previousDistance - AutomatedLogisticsConfig.MIN_MEANINGFUL_PROGRESS_DISTANCE.get()) {
                resetProgress(currentDistance);
                return Optional.empty();
            }

            stalledTicks++;
            if (stalledTicks >= AutomatedLogisticsConfig.STUCK_TIMEOUT_TICKS.get()) {
                return Optional.of(PlaybackFailure.STUCK);
            }
            return Optional.empty();
        }

        private void resetProgress(double currentDistance) {
            previousDistance = currentDistance;
            stalledTicks = 0;
        }

        private double currentDistanceToTarget(Vec3 currentPosition) {
            return currentPosition.distanceTo(targetPosition());
        }

        private void resetSegmentTiming() {
            segmentDurationTicks = computeSegmentDurationTicks();
            segmentElapsedTicks = 0;
        }

        private long overdueSegmentThresholdTicks() {
            long scaledDuration = (long) Math.ceil(segmentDurationTicks * AutomatedLogisticsConfig.SEGMENT_OVERRUN_MULTIPLIER.get());
            return Math.max(scaledDuration, AutomatedLogisticsConfig.STUCK_TIMEOUT_TICKS.get());
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
            if (playbackTicks < Integer.MAX_VALUE) {
                playbackTicks++;
            }
            if (segmentElapsedTicks < Integer.MAX_VALUE) {
                segmentElapsedTicks++;
            }
        }

        private void ensurePrimedSegmentProgress() {
            if (segmentElapsedTicks <= 0) {
                tickSegment();
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

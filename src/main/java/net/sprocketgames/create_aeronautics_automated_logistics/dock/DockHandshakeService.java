package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public final class DockHandshakeService {
    private static final double NOMINAL_TICK_NANOS = 50_000_000.0D;
    private static final long PENDING_LOG_INTERVAL_TICKS = 40L;

    private final DockEndpointResolver endpoints;
    private final DockReservationService reservations;
    private final Map<UUID, LockClock> lockClocks = new HashMap<>();
    private final Map<UUID, Long> pendingDiagnosticTicks = new HashMap<>();
    private final Map<UUID, Long> transferDiagnosticTicks = new HashMap<>();

    public DockHandshakeService(
            DockEndpointResolver endpoints,
            DockReservationService reservations
    ) {
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    public HandshakeResult begin(HandshakeRequest request) {
        return inspectAndAdvance(request, true);
    }

    public HandshakeResult tick(HandshakeRequest request) {
        return inspectAndAdvance(request, false);
    }

    public Optional<DockTransferSnapshot> transferSnapshot(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockEndpointResolver.PhysicalResult endpoint = endpoints.resolvePhysical(level, station, route);
        if (!endpoint.ready()) {
            return Optional.empty();
        }
        BlockPos stationDock = endpoint.stationDockPos().orElseThrow();
        BlockPos shipDock = endpoint.shipDockPos().orElseThrow();
        if (!DockingConnectorDiscovery.isLockedPair(level, stationDock, shipDock)) {
            return Optional.empty();
        }
        return DockingConnectorDiscovery.dockingConnector(level, stationDock)
                .flatMap(stationConnector -> DockingConnectorDiscovery.dockingConnector(level, shipDock)
                        .map(shipConnector -> DockTransferSnapshot.capture(stationConnector, shipConnector)));
    }

    public ClearResult clearOwnedOutputs(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            Optional<DockRequestId> requestId
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(requestId, "requestId");

        boolean stationCleared = station.releaseDockOutput(route.id());
        Optional<ShipTransponderBlockEntity> transponder = endpoints.resolveTransponder(level, route);
        boolean shipCleared = transponder.map(value -> value.releaseDockOutput(route.id())).orElse(true);
        requestId.ifPresent(request -> {
            lockClocks.remove(request.value());
            pendingDiagnosticTicks.remove(request.value());
            transferDiagnosticTicks.remove(request.value());
        });
        ClearResult result = new ClearResult(
                stationCleared,
                shipCleared,
                transponder.isPresent(),
                stationCleared && shipCleared
        );
        if (!result.cleared()) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock handshake output clear refused: request={} route={} station={} stationCleared={} shipPresent={} shipCleared={} reason=owner_mismatch",
                    requestId.map(DockRequestId::value).map(UUID::toString).orElse("unknown"),
                    route.id().value(),
                    station.stationId(),
                    stationCleared,
                    transponder.isPresent(),
                    shipCleared
            );
        }
        return result;
    }

    public void clearScheduleOutputs(
            Optional<AirshipStationBlockEntity> station,
            Optional<ShipTransponderBlockEntity> transponder,
            Optional<RouteId> routeId,
            String reason
    ) {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(transponder, "transponder");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(reason, "reason");
        if (routeId.isPresent()) {
            boolean stationCleared = station
                    .map(value -> value.releaseDockOutput(routeId.get()))
                    .orElse(true);
            boolean shipCleared = transponder
                    .map(value -> value.releaseDockOutput(routeId.get()))
                    .orElse(true);
            if (!stationCleared || !shipCleared) {
                CreateAeronauticsAutomatedLogistics.debugDocking(
                        "Dock handshake schedule clear refused: route={} station={} transponder={} stationCleared={} shipCleared={} reason={}",
                        routeId.get().value(),
                        station.map(AirshipStationBlockEntity::stationId)
                                .map(UUID::toString)
                                .orElse("unavailable"),
                        transponder.map(ShipTransponderBlockEntity::transponderId)
                                .map(UUID::toString)
                                .orElse("unavailable"),
                        stationCleared,
                        shipCleared,
                        reason
                );
            }
            return;
        }
        station.ifPresent(value -> value.forceClearDockOutput(reason));
        transponder.ifPresent(value -> value.forceClearDockOutput(reason));
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock handshake schedule outputs force-cleared: station={} transponder={} reason={} proof=explicit_schedule_stop_without_route",
                station.map(AirshipStationBlockEntity::stationId)
                        .map(UUID::toString)
                        .orElse("unavailable"),
                transponder.map(ShipTransponderBlockEntity::transponderId)
                        .map(UUID::toString)
                        .orElse("unavailable"),
                reason
        );
    }

    public ResetResult resetExpectedPair(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            String reason
    ) {
        Objects.requireNonNull(reason, "reason");
        DockEndpointResolver.PhysicalResult endpoint = endpoints.resolvePhysical(level, station, route);
        if (!endpoint.ready()) {
            return ResetResult.failed(endpoint.failure().orElse(PlaybackFailure.MISSING_DOCK));
        }
        BlockPos stationDock = endpoint.stationDockPos().orElseThrow();
        BlockPos shipDock = endpoint.shipDockPos().orElseThrow();
        if (DockingConnectorDiscovery.dockingConnector(level, stationDock).isEmpty()
                || DockingConnectorDiscovery.dockingConnector(level, shipDock).isEmpty()) {
            return ResetResult.failed(PlaybackFailure.MISSING_DOCK);
        }
        DockingConnectorDiscovery.dockingConnector(level, stationDock).ifPresent(connector -> connector.unDock());
        DockingConnectorDiscovery.dockingConnector(level, shipDock).ifPresent(connector -> connector.unDock());
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock handshake expected pair reset: route={} stationDock={} shipDock={} reason={}",
                route.id().value(),
                stationDock.toShortString(),
                shipDock.toShortString(),
                reason
        );
        return ResetResult.success();
    }

    public String diagnostic(ServerLevel level, AirshipStationBlockEntity station, Route route) {
        DockEndpointResolver.PhysicalResult endpoint = endpoints.resolvePhysical(level, station, route);
        if (!endpoint.ready()) {
            return "endpoint=" + endpoint.status();
        }
        return DockingConnectorDiscovery.lockDiagnostic(
                level,
                endpoint.stationDockPos().orElseThrow(),
                endpoint.shipDockPos().orElseThrow()
        );
    }

    public void resetRuntime(String reason) {
        int clockCount = lockClocks.size();
        lockClocks.clear();
        pendingDiagnosticTicks.clear();
        transferDiagnosticTicks.clear();
        if (clockCount > 0) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock handshake runtime reset: reason={} lockClocks={}",
                    reason,
                    clockCount
            );
        }
    }

    public void releaseRequest(DockRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        pauseLockClock(requestId);
        transferDiagnosticTicks.remove(requestId.value());
    }

    private HandshakeResult inspectAndAdvance(HandshakeRequest request, boolean begin) {
        Objects.requireNonNull(request, "request");
        DockReservationService.ReservationStatus reservation = reservations.status(request.requestId());
        if (!reservation.granted()) {
            pauseLockClock(request.requestId());
            return HandshakeResult.pending(
                    HandshakeStatus.RESERVATION_REQUIRED,
                    request.lockTimeoutTicksRemaining(),
                    false,
                    "reservation_not_granted"
            );
        }

        DockEndpointResolver.PhysicalResult endpoint =
                endpoints.resolvePhysical(request.level(), request.station(), request.route());
        if (!endpoint.ready()) {
            return HandshakeResult.failed(
                    HandshakeStatus.ENDPOINT_UNAVAILABLE,
                    endpoint.failure().orElse(PlaybackFailure.MISSING_DOCK),
                    request.lockTimeoutTicksRemaining(),
                    endpoint.status().name()
            );
        }

        BlockPos stationDock = endpoint.stationDockPos().orElseThrow();
        BlockPos shipDock = endpoint.shipDockPos().orElseThrow();
        boolean expectedPairLocked = DockingConnectorDiscovery.isLockedPair(request.level(), stationDock, shipDock);
        RouteId routeId = request.route().id();
        AirshipStationBlockEntity station = endpoint.station().orElseThrow();
        ShipTransponderBlockEntity transponder = endpoint.transponder().orElseThrow();
        boolean outputsAlreadyOwned = station.dockOutputActive()
                && station.dockOutputOwner().filter(routeId::equals).isPresent()
                && transponder.dockOutputActive()
                && transponder.dockOutputOwner().filter(routeId::equals).isPresent();
        if (isLockedToDifferentConnector(request.level(), stationDock, shipDock)
                || isLockedToDifferentConnector(request.level(), shipDock, stationDock)) {
            return HandshakeResult.failed(
                    HandshakeStatus.WRONG_CONNECTOR_LOCKED,
                    PlaybackFailure.DOCK_LOCK_FAILED,
                    request.lockTimeoutTicksRemaining(),
                    "unexpected_connector_pair"
            );
        }

        if (!expectedPairLocked && !outputsAlreadyOwned && !request.poseProof().positionConfirmed()) {
            clearIfOwned(endpoint, request.route().id());
            pauseLockClock(request.requestId());
            return HandshakeResult.pending(
                    HandshakeStatus.POSITION_NOT_CONFIRMED,
                    request.lockTimeoutTicksRemaining(),
                    false,
                    "position_error=" + request.poseProof().positionError()
            );
        }
        if (!expectedPairLocked && !outputsAlreadyOwned && !request.poseProof().rotationConfirmed()) {
            clearIfOwned(endpoint, request.route().id());
            pauseLockClock(request.requestId());
            return HandshakeResult.pending(
                    HandshakeStatus.ROTATION_NOT_CONFIRMED,
                    request.lockTimeoutTicksRemaining(),
                    false,
                    "rotation_error=" + request.poseProof().rotationErrorDegrees()
            );
        }

        Optional<PlaybackFailure> ownershipFailure = claimOutputs(endpoint, request);
        if (ownershipFailure.isPresent()) {
            return HandshakeResult.failed(
                    HandshakeStatus.OUTPUT_OWNED_BY_OTHER,
                    ownershipFailure.get(),
                    request.lockTimeoutTicksRemaining(),
                    "output_owner_mismatch"
            );
        }
        if (expectedPairLocked) {
            lockClocks.remove(request.requestId().value());
            pendingDiagnosticTicks.remove(request.requestId().value());
            logTransferDiagnostic(request, stationDock, shipDock);
            return HandshakeResult.locked(request.lockTimeoutTicksRemaining());
        }

        LockClock clock = lockClocks.computeIfAbsent(
                request.requestId().value(),
                ignored -> new LockClock(request.lockTimeoutTicksRemaining())
        );
        clock.restoreIfGreater(request.lockTimeoutTicksRemaining());
        int remaining = begin ? clock.remainingTicks() : clock.tick(System.nanoTime());
        if (remaining <= 0) {
            if (DockingConnectorDiscovery.isExpectedPairExtended(request.level(), stationDock, shipDock)) {
                clock.reset(AutomatedLogisticsConfig.DOCK_LOCK_TIMEOUT_TICKS.get());
                remaining = clock.remainingTicks();
                CreateAeronauticsAutomatedLogistics.debugDocking(
                        "Dock handshake lock timeout deferred because expected connector pair is still extended: request={} route={} stationDock={} shipDock={} resetTicks={} diagnostic={}",
                        request.requestId().value(),
                        request.route().id().value(),
                        stationDock.toShortString(),
                        shipDock.toShortString(),
                        remaining,
                        DockingConnectorDiscovery.lockDiagnostic(request.level(), stationDock, shipDock)
                );
                logPending(request, endpoint, remaining);
                return HandshakeResult.pending(
                        HandshakeStatus.LOCK_PENDING,
                        remaining,
                        true,
                        "expected_pair_extended_waiting_for_lock"
                );
            }
            return HandshakeResult.failed(
                    HandshakeStatus.TIMED_OUT,
                    PlaybackFailure.DOCK_LOCK_FAILED,
                    0,
                    "lock_timeout"
            );
        }
        logPending(request, endpoint, remaining);
        return HandshakeResult.pending(HandshakeStatus.LOCK_PENDING, remaining, true, "expected_pair_not_locked");
    }

    private void pauseLockClock(DockRequestId requestId) {
        lockClocks.remove(requestId.value());
        pendingDiagnosticTicks.remove(requestId.value());
    }

    private Optional<PlaybackFailure> claimOutputs(
            DockEndpointResolver.PhysicalResult endpoint,
            HandshakeRequest request
    ) {
        AirshipStationBlockEntity station = endpoint.station().orElseThrow();
        ShipTransponderBlockEntity transponder = endpoint.transponder().orElseThrow();
        RouteId routeId = request.route().id();
        if (station.dockOutputActive() && station.dockOutputOwner().filter(routeId::equals).isEmpty()) {
            return Optional.of(PlaybackFailure.DOCK_LOCK_FAILED);
        }
        if (transponder.dockOutputActive() && transponder.dockOutputOwner().filter(routeId::equals).isEmpty()) {
            return Optional.of(PlaybackFailure.DOCK_LOCK_FAILED);
        }
        boolean changed = !station.dockOutputActive()
                || station.dockOutputOwner().filter(routeId::equals).isEmpty()
                || !transponder.dockOutputActive()
                || transponder.dockOutputOwner().filter(routeId::equals).isEmpty();
        station.claimDockOutput(routeId);
        transponder.claimDockOutput(routeId);
        if (changed) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock handshake outputs claimed: request={} route={} station={} transponder={} stationDock={} shipDock={} positionError={} rotationError={}",
                    request.requestId().value(),
                    routeId.value(),
                    station.stationId(),
                    transponder.transponderId(),
                    endpoint.stationDockPos().orElseThrow().toShortString(),
                    endpoint.shipDockPos().orElseThrow().toShortString(),
                    request.poseProof().positionError(),
                    request.poseProof().rotationErrorDegrees()
            );
        }
        return Optional.empty();
    }

    private void clearIfOwned(DockEndpointResolver.PhysicalResult endpoint, RouteId routeId) {
        endpoint.station().ifPresent(station -> station.releaseDockOutput(routeId));
        endpoint.transponder().ifPresent(transponder -> transponder.releaseDockOutput(routeId));
    }

    private boolean isLockedToDifferentConnector(ServerLevel level, BlockPos dockPos, BlockPos expectedOther) {
        return DockingConnectorDiscovery.dockingConnector(level, dockPos)
                .filter(connector -> connector.isLocked())
                .map(connector -> connector.otherConnectorPosition)
                .filter(otherPos -> otherPos != null && !otherPos.equals(expectedOther))
                .isPresent();
    }

    private void logPending(
            HandshakeRequest request,
            DockEndpointResolver.PhysicalResult endpoint,
            int remainingTicks
    ) {
        long gameTime = request.level().getGameTime();
        Long previous = pendingDiagnosticTicks.get(request.requestId().value());
        if (previous != null && gameTime - previous < PENDING_LOG_INTERVAL_TICKS) {
            return;
        }
        pendingDiagnosticTicks.put(request.requestId().value(), gameTime);
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock handshake lock pending: request={} route={} station={} transponder={} stationDock={} shipDock={} remainingTicks={} diagnostic={}",
                request.requestId().value(),
                request.route().id().value(),
                endpoint.station().orElseThrow().stationId(),
                endpoint.transponder().orElseThrow().transponderId(),
                endpoint.stationDockPos().orElseThrow().toShortString(),
                endpoint.shipDockPos().orElseThrow().toShortString(),
                remainingTicks,
                DockingConnectorDiscovery.lockDiagnostic(
                        request.level(),
                        endpoint.stationDockPos().orElseThrow(),
                        endpoint.shipDockPos().orElseThrow()
                )
        );
    }

    private void logTransferDiagnostic(
            HandshakeRequest request,
            BlockPos stationDockPos,
            BlockPos shipDockPos
    ) {
        if (!AutomatedLogisticsConfig.debugDocking()) {
            return;
        }
        long gameTime = request.level().getGameTime();
        Long previous = transferDiagnosticTicks.get(request.requestId().value());
        if (previous != null && gameTime - previous < PENDING_LOG_INTERVAL_TICKS) {
            return;
        }
        transferDiagnosticTicks.put(request.requestId().value(), gameTime);
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock transfer physical diagnostic: request={} route={} gameTime={} stationDock={} shipDock={} stationTicking={} shipTicking={} stationContainers={} shipContainers={}",
                request.requestId().value(),
                request.route().id().value(),
                gameTime,
                stationDockPos.toShortString(),
                shipDockPos.toShortString(),
                request.level().shouldTickBlocksAt(stationDockPos.asLong()),
                request.level().shouldTickBlocksAt(shipDockPos.asLong()),
                nearbyContainers(request.level(), stationDockPos),
                nearbyContainers(request.level(), shipDockPos)
        );
    }

    private static List<String> nearbyContainers(ServerLevel level, BlockPos center) {
        List<String> containers = new ArrayList<>();
        BlockPos.betweenClosedStream(center.offset(-2, -2, -2), center.offset(2, 2, 2))
                .map(BlockPos::immutable)
                .forEach(pos -> {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (!(blockEntity instanceof Container container)) {
                        return;
                    }
                    int itemCount = 0;
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        itemCount += container.getItem(slot).getCount();
                    }
                    containers.add(
                            blockEntity.getClass().getSimpleName()
                                    + "@"
                                    + pos.toShortString()
                                    + "="
                                    + itemCount
                    );
                });
        return List.copyOf(containers);
    }

    public enum HandshakeStatus {
        RESERVATION_REQUIRED,
        POSITION_NOT_CONFIRMED,
        ROTATION_NOT_CONFIRMED,
        ENDPOINT_UNAVAILABLE,
        OUTPUT_OWNED_BY_OTHER,
        LOCK_PENDING,
        LOCKED,
        WRONG_CONNECTOR_LOCKED,
        TIMED_OUT
    }

    public record HandshakeRequest(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            DockRequestId requestId,
            DockPoseProof poseProof,
            int lockTimeoutTicksRemaining
    ) {
        public HandshakeRequest {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(poseProof, "poseProof");
            lockTimeoutTicksRemaining = Math.max(0, lockTimeoutTicksRemaining);
        }
    }

    public record HandshakeResult(
            HandshakeStatus status,
            Optional<PlaybackFailure> failure,
            int lockTimeoutTicksRemaining,
            boolean expectedPairLocked,
            boolean outputsOwned,
            String reason
    ) {
        private static HandshakeResult pending(
                HandshakeStatus status,
                int remaining,
                boolean outputsOwned,
                String reason
        ) {
            return new HandshakeResult(
                    status,
                    Optional.empty(),
                    Math.max(0, remaining),
                    false,
                    outputsOwned,
                    reason
            );
        }

        private static HandshakeResult locked(int remaining) {
            return new HandshakeResult(
                    HandshakeStatus.LOCKED,
                    Optional.empty(),
                    Math.max(0, remaining),
                    true,
                    true,
                    "expected_pair_locked"
            );
        }

        private static HandshakeResult failed(
                HandshakeStatus status,
                PlaybackFailure failure,
                int remaining,
                String reason
        ) {
            return new HandshakeResult(
                    status,
                    Optional.of(failure),
                    Math.max(0, remaining),
                    false,
                    false,
                    reason
            );
        }

        public boolean handshakeActive() {
            return status == HandshakeStatus.LOCK_PENDING || status == HandshakeStatus.LOCKED;
        }
    }

    public record ClearResult(
            boolean stationCleared,
            boolean shipCleared,
            boolean shipPresent,
            boolean cleared
    ) {
    }

    public record ResetResult(boolean applied, Optional<PlaybackFailure> failure) {
        private static ResetResult success() {
            return new ResetResult(true, Optional.empty());
        }

        private static ResetResult failed(PlaybackFailure failure) {
            return new ResetResult(false, Optional.of(failure));
        }
    }

    static final class LockClock {
        private int remainingTicks;
        private long lastSampleNanos;
        private double tickAccumulator;

        LockClock(int remainingTicks) {
            this.remainingTicks = Math.max(0, remainingTicks);
        }

        int tick(long nowNanos) {
            if (lastSampleNanos == 0L) {
                lastSampleNanos = nowNanos;
                return remainingTicks;
            }
            double elapsedTicks = Math.min(
                    1.0D,
                    Math.max(0.0D, (nowNanos - lastSampleNanos) / NOMINAL_TICK_NANOS)
            );
            lastSampleNanos = nowNanos;
            tickAccumulator += elapsedTicks;
            int wholeTicks = (int) tickAccumulator;
            if (wholeTicks > 0) {
                remainingTicks = Math.max(0, remainingTicks - wholeTicks);
                tickAccumulator -= wholeTicks;
            }
            return remainingTicks;
        }

        int remainingTicks() {
            return remainingTicks;
        }

        void restoreIfGreater(int restoredTicks) {
            if (restoredTicks <= remainingTicks) {
                return;
            }
            remainingTicks = restoredTicks;
            lastSampleNanos = 0L;
            tickAccumulator = 0.0D;
        }

        void reset(int ticks) {
            remainingTicks = Math.max(0, ticks);
            lastSampleNanos = 0L;
            tickAccumulator = 0.0D;
        }
    }
}

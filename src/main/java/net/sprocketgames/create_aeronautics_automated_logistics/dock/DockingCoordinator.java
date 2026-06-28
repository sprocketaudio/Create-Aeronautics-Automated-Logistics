package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockTargetId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingEvidence;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingPhase;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingReason;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingRuntimeMode;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingSession;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public final class DockingCoordinator {
    private final DockEndpointResolver endpoints;
    private final DockReservationService reservations;
    private final DockHandshakeService handshake;

    public DockingCoordinator(
            DockEndpointResolver endpoints,
            DockReservationService reservations,
            DockHandshakeService handshake
    ) {
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.handshake = Objects.requireNonNull(handshake, "handshake");
    }

    public DockingSession idleSession(
            Route route,
            DockRequestId requestId,
            DockingRuntimeMode runtimeMode,
            long gameTime
    ) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(runtimeMode, "runtimeMode");
        return new DockingSession(
                DockingSession.CURRENT_SCHEMA_VERSION,
                requestId,
                route.id(),
                Optional.empty(),
                DockingPhase.NONE,
                Optional.empty(),
                runtimeMode,
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.NO_DOCK_CONTEXT,
                Optional.empty(),
                gameTime
        );
    }

    public QueueOutcome requestLoadedApproach(QueueRequest request) {
        return requestApproach(request, DockingRuntimeMode.LOADED);
    }

    public QueueOutcome requestUnloadedApproach(QueueRequest request) {
        return requestApproach(request, DockingRuntimeMode.UNLOADED);
    }

    private QueueOutcome requestApproach(QueueRequest request, DockingRuntimeMode runtimeMode) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(runtimeMode, "runtimeMode");
        long gameTime = request.level().getGameTime();
        DockingSession runtimeSession = withRuntimeMode(
                request.session(),
                runtimeMode,
                gameTime,
                "approach_request"
        );
        DockEndpointResolver.TargetResult targetResult =
                endpoints.resolveTarget(request.level(), request.station());
        if (!targetResult.ready()) {
            PlaybackFailure failure = endpointFailure(targetResult.status());
            return QueueOutcome.failed(
                    fault(runtimeSession, failure, gameTime),
                    failure,
                    "dock_target_unavailable"
            );
        }

        DockReservationService.ReservationResult reservation = reservations.request(
                targetResult.target().orElseThrow(),
                request.requestId(),
                DockingRuntime::isActiveRequest
        );
        return observeReservation(
                runtimeSession,
                targetResult.target(),
                new ReservationObservation(
                        reservation.granted(),
                        reservation.queuePosition(),
                        reservation.changed(),
                        reservation.reason(),
                        reservation.reason() == DockReservationService.ReservationReason.INCOMPLETE_TARGET
                                ? Optional.of(PlaybackFailure.MISSING_DOCK)
                                : Optional.empty()
                ),
                gameTime
        );
    }

    public DockingSession withRuntimeMode(
            DockingSession session,
            DockingRuntimeMode runtimeMode,
            long gameTime,
            String detail
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(runtimeMode, "runtimeMode");
        if (session.runtimeMode() == runtimeMode) {
            return session;
        }
        DockingSession updated = new DockingSession(
                session.schemaVersion(),
                session.requestId(),
                session.routeId(),
                session.target(),
                session.phase(),
                session.resumePhase(),
                runtimeMode,
                session.queuePosition(),
                session.outputsOwned(),
                session.expectedConnectorLocked(),
                session.waitActive(),
                session.paused(),
                session.reason(),
                session.reasonDetail(),
                gameTime
        );
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Docking coordinator runtime mode transition: request={} route={} phase={} from={} to={} detail={}",
                session.requestId().value(),
                session.routeId().value(),
                session.phase(),
                session.runtimeMode(),
                runtimeMode,
                detail
        );
        return updated;
    }

    QueueOutcome observeReservation(
            DockingSession session,
            Optional<DockTargetId> target,
            ReservationObservation reservation,
            long gameTime
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reservation, "reservation");
        if (reservation.failure().isPresent()) {
            PlaybackFailure failure = reservation.failure().get();
            return QueueOutcome.failed(
                    fault(session, failure, gameTime),
                    failure,
                    "reservation_failed"
            );
        }
        if (reservation.reason() == DockReservationService.ReservationReason.HELD_OTHER_TARGET) {
            return new QueueOutcome(
                    session,
                    QueueDirective.DEFERRED_FOR_DEPARTURE,
                    reservation.queuePosition(),
                    reservation.changed(),
                    Optional.empty(),
                    "previous_dock_clearance_active"
            );
        }

        DockingSession targeted = withTarget(
                session,
                target,
                gameTime
        );
        if (reservation.granted()) {
            DockingSession approaching = enterApproach(targeted, gameTime);
            return new QueueOutcome(
                    approaching,
                    QueueDirective.APPROACH,
                    reservation.queuePosition(),
                    reservation.changed(),
                    Optional.empty(),
                    "reservation_granted"
            );
        }

        DockingSession queued = enterQueue(targeted, reservation.queuePosition(), gameTime);
        return new QueueOutcome(
                queued,
                QueueDirective.HOLD,
                reservation.queuePosition(),
                reservation.changed(),
                Optional.empty(),
                "reservation_queued"
        );
    }

    public HandshakeOutcome advanceLoadedHandshake(HandshakeRequest request) {
        Objects.requireNonNull(request, "request");
        DockHandshakeService.HandshakeRequest handshakeRequest =
                new DockHandshakeService.HandshakeRequest(
                        request.level(),
                        request.station(),
                        request.route(),
                        request.requestId(),
                        request.poseProof(),
                        request.lockTimeoutTicksRemaining()
                );
        boolean begin = request.session().phase() != DockingPhase.LOCKING
                && request.session().outputsOwned() != DockingEvidence.CONFIRMED;
        DockHandshakeService.HandshakeResult result = begin
                ? handshake.begin(handshakeRequest)
                : handshake.tick(handshakeRequest);
        long gameTime = request.level().getGameTime();
        DockingSession session;
        HandshakeDirective directive;
        switch (result.status()) {
            case LOCKED -> {
                session = enterWaiting(request.session(), gameTime);
                directive = session.phase() == DockingPhase.WAITING
                        ? HandshakeDirective.LOCKED
                        : HandshakeDirective.FAULTED;
            }
            case LOCK_PENDING -> {
                session = enterLocking(request.session(), gameTime);
                directive = request.session().phase() == DockingPhase.LOCKING
                        ? HandshakeDirective.HOLD_FOR_LOCK
                        : HandshakeDirective.RELEASE_MOTION_CONTROL;
            }
            case POSITION_NOT_CONFIRMED, ROTATION_NOT_CONFIRMED -> {
                session = enterAlignment(request.session(), result.reason(), gameTime);
                directive = HandshakeDirective.APPROACH_AND_ALIGN;
            }
            case RESERVATION_REQUIRED -> {
                session = blocked(
                        request.session(),
                        DockingPhase.APPROACHING,
                        result.reason(),
                        gameTime
                );
                directive = HandshakeDirective.WAIT_FOR_RESERVATION;
            }
            case ENDPOINT_UNAVAILABLE -> {
                session = blocked(
                        request.session(),
                        resumePhase(request.session()),
                        result.reason(),
                        gameTime
                );
                directive = HandshakeDirective.BLOCKED;
            }
            case OUTPUT_OWNED_BY_OTHER, WRONG_CONNECTOR_LOCKED, TIMED_OUT -> {
                PlaybackFailure failure = result.failure().orElse(PlaybackFailure.DOCK_LOCK_FAILED);
                session = fault(request.session(), failure, gameTime);
                directive = HandshakeDirective.FAULTED;
            }
            default -> throw new IllegalStateException("Unhandled handshake status " + result.status());
        }
        return new HandshakeOutcome(
                session,
                directive,
                result.lockTimeoutTicksRemaining(),
                result.failure(),
                result.reason()
        );
    }

    public DockingSession beginRelease(DockingSession session, long gameTime) {
        return transition(
                session,
                DockingPhase.RELEASING,
                Optional.empty(),
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.DEPARTURE_CLEARANCE,
                Optional.of("wait_conditions_complete"),
                gameTime
        );
    }

    public DockingSession beginDepartureClearance(DockingSession session, long gameTime) {
        return transition(
                session,
                DockingPhase.DEPARTING_CLEARANCE,
                Optional.empty(),
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.DEPARTURE_CLEARANCE,
                Optional.empty(),
                gameTime
        );
    }

    public DockingSession inheritDepartureClearance(
            DockingSession session,
            DockTargetId departedTarget,
            long gameTime
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(departedTarget, "departedTarget");
        DockingSession inherited = new DockingSession(
                session.schemaVersion(),
                session.requestId(),
                session.routeId(),
                Optional.of(departedTarget),
                DockingPhase.DEPARTING_CLEARANCE,
                Optional.empty(),
                session.runtimeMode(),
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.DEPARTURE_CLEARANCE,
                Optional.of("inherited_across_route_handoff"),
                gameTime
        );
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Docking coordinator inherited departure clearance: request={} route={} target={}",
                inherited.requestId().value(),
                inherited.routeId().value(),
                targetSummary(departedTarget)
        );
        return inherited;
    }

    public DockingSession completeDeparture(DockingSession session, long gameTime) {
        DockingSession complete = transition(
                session,
                DockingPhase.COMPLETE,
                Optional.empty(),
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.PLAYBACK_COMPLETED,
                Optional.of("dock_clearance_complete"),
                gameTime
        );
        return transition(
                complete,
                DockingPhase.NONE,
                Optional.empty(),
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.NO_DOCK_CONTEXT,
                Optional.empty(),
                gameTime
        );
    }

    public DockingSession pause(DockingSession session, String detail, long gameTime) {
        if (!session.hasDockingContext()) {
            return session;
        }
        return copy(
                session,
                session.phase(),
                session.resumePhase(),
                session.queuePosition(),
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                true,
                DockingReason.PLAYBACK_PAUSED,
                Optional.ofNullable(detail),
                gameTime
        );
    }

    public DockingSession resume(DockingSession session, long gameTime) {
        if (!session.paused()) {
            return session;
        }
        DockingPhase next = session.phase() == DockingPhase.FAULTED
                ? DockingPhase.RECOVERING
                : session.phase();
        return transition(
                session,
                next,
                session.resumePhase(),
                session.queuePosition(),
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                next == DockingPhase.RECOVERING
                        ? DockingReason.APPROACH_REACQUIRE
                        : session.reason(),
                Optional.of("runtime_resumed"),
                gameTime
        );
    }

    public DockingSession cancel(DockingSession session, String detail, long gameTime) {
        if (!session.hasDockingContext()) {
            return session;
        }
        return transition(
                session,
                DockingPhase.CANCELLED,
                Optional.empty(),
                -1,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.PLAYBACK_COMPLETED,
                Optional.ofNullable(detail),
                gameTime
        );
    }

    private DockingSession enterQueue(DockingSession session, int queuePosition, long gameTime) {
        DockingSession requested = session.phase() == DockingPhase.NONE
                ? transition(
                        session,
                        DockingPhase.QUEUE_REQUESTED,
                        Optional.empty(),
                        -1,
                        DockingEvidence.NOT_CONFIRMED,
                        DockingEvidence.NOT_CONFIRMED,
                        false,
                        false,
                        DockingReason.RESERVATION_REQUESTED,
                        Optional.empty(),
                        gameTime
                )
                : session;
        if (requested.phase() == DockingPhase.QUEUED) {
            return copy(
                    requested,
                    DockingPhase.QUEUED,
                    Optional.empty(),
                    Math.max(1, queuePosition),
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.RESERVATION_QUEUED,
                    Optional.empty(),
                    gameTime
            );
        }
        if (requested.phase() != DockingPhase.QUEUE_REQUESTED) {
            return blocked(requested, DockingPhase.APPROACHING, "reservation_grant_lost", gameTime);
        }
        return transition(
                requested,
                DockingPhase.QUEUED,
                Optional.empty(),
                Math.max(1, queuePosition),
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.RESERVATION_QUEUED,
                Optional.empty(),
                gameTime
        );
    }

    private DockingSession enterApproach(DockingSession session, long gameTime) {
        DockingSession requested = session.phase() == DockingPhase.NONE
                ? transition(
                        session,
                        DockingPhase.QUEUE_REQUESTED,
                        Optional.empty(),
                        -1,
                        DockingEvidence.NOT_CONFIRMED,
                        DockingEvidence.NOT_CONFIRMED,
                        false,
                        false,
                        DockingReason.RESERVATION_REQUESTED,
                        Optional.empty(),
                        gameTime
                )
                : session;
        if (requested.phase() == DockingPhase.APPROACHING
                || requested.phase() == DockingPhase.ALIGNING
                || requested.phase() == DockingPhase.LOCKING
                || requested.phase() == DockingPhase.DOCKED
                || requested.phase() == DockingPhase.WAITING) {
            boolean enteringApproach = requested.phase() == DockingPhase.APPROACHING;
            return copy(
                    requested,
                    requested.phase(),
                    requested.resumePhase(),
                    0,
                    requested.outputsOwned(),
                    requested.expectedConnectorLocked(),
                    requested.waitActive(),
                    false,
                    enteringApproach ? DockingReason.RESERVATION_GRANTED : requested.reason(),
                    enteringApproach ? Optional.empty() : requested.reasonDetail(),
                    gameTime
            );
        }
        if (requested.phase() == DockingPhase.BLOCKED
                && requested.resumePhase().filter(DockingPhase.APPROACHING::equals).isPresent()) {
            return transition(
                    requested,
                    DockingPhase.APPROACHING,
                    Optional.empty(),
                    0,
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.RESERVATION_GRANTED,
                    Optional.of("reservation_reacquired"),
                    gameTime
            );
        }
        return transition(
                requested,
                DockingPhase.APPROACHING,
                Optional.empty(),
                0,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.RESERVATION_GRANTED,
                Optional.empty(),
                gameTime
        );
    }

    private DockingSession enterAlignment(DockingSession session, String detail, long gameTime) {
        DockingSession current = session;
        if (current.phase() == DockingPhase.WAITING
                || current.phase() == DockingPhase.DOCKED
                || current.phase() == DockingPhase.LOCKING) {
            current = transition(
                    current,
                    DockingPhase.RECOVERING,
                    Optional.of(DockingPhase.APPROACHING),
                    0,
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.APPROACH_REACQUIRE,
                    Optional.of("dock_pose_lost"),
                    gameTime
            );
        }
        if ((current.phase() == DockingPhase.BLOCKED || current.phase() == DockingPhase.RECOVERING)
                && current.resumePhase().filter(DockingPhase.APPROACHING::equals).isEmpty()) {
            current = copy(
                    current,
                    current.phase(),
                    Optional.of(DockingPhase.APPROACHING),
                    current.queuePosition(),
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.APPROACH_REACQUIRE,
                    Optional.of(detail),
                    gameTime
            );
        }
        current = recoverTo(current, DockingPhase.APPROACHING, gameTime);
        if (current.phase() == DockingPhase.ALIGNING) {
            return copy(
                    current,
                    DockingPhase.ALIGNING,
                    Optional.empty(),
                    0,
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.APPROACH_REACQUIRE,
                    Optional.of(detail),
                    gameTime
            );
        }
        return transition(
                current,
                DockingPhase.ALIGNING,
                Optional.empty(),
                0,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.APPROACH_REACQUIRE,
                Optional.of(detail),
                gameTime
        );
    }

    private DockingSession enterLocking(DockingSession session, long gameTime) {
        DockingSession current = session;
        if (current.phase() == DockingPhase.WAITING || current.phase() == DockingPhase.DOCKED) {
            current = transition(
                    current,
                    DockingPhase.RECOVERING,
                    Optional.of(DockingPhase.LOCKING),
                    0,
                    DockingEvidence.CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.CONNECTOR_LOCK_PENDING,
                    Optional.of("physical_lock_lost"),
                    gameTime
            );
        }
        if (current.phase() == DockingPhase.BLOCKED || current.phase() == DockingPhase.RECOVERING) {
            current = recoverTo(
                    current,
                    current.resumePhase().orElse(DockingPhase.APPROACHING),
                    gameTime
            );
        }
        if (current.phase() == DockingPhase.APPROACHING) {
            current = transition(
                    current,
                    DockingPhase.ALIGNING,
                    Optional.empty(),
                    0,
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.APPROACH_REACQUIRE,
                    Optional.of("pose_confirmed"),
                    gameTime
            );
        }
        if (current.phase() == DockingPhase.ALIGNING) {
            current = transition(
                    current,
                    DockingPhase.LOCKING,
                    Optional.empty(),
                    0,
                    DockingEvidence.CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.CONNECTOR_LOCK_PENDING,
                    Optional.empty(),
                    gameTime
            );
        }
        if (current.phase() != DockingPhase.LOCKING) {
            return current;
        }
        return copy(
                current,
                DockingPhase.LOCKING,
                Optional.empty(),
                0,
                DockingEvidence.CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.CONNECTOR_LOCK_PENDING,
                Optional.empty(),
                gameTime
        );
    }

    private DockingSession enterWaiting(DockingSession session, long gameTime) {
        if (session.phase() == DockingPhase.WAITING
                && session.expectedConnectorLocked() == DockingEvidence.CONFIRMED) {
            return copy(
                    session,
                    DockingPhase.WAITING,
                    Optional.empty(),
                    0,
                    DockingEvidence.CONFIRMED,
                    DockingEvidence.CONFIRMED,
                    true,
                    false,
                    DockingReason.CONDITIONS_ACTIVE,
                    Optional.empty(),
                    gameTime
            );
        }
        DockingSession locking = enterLocking(session, gameTime);
        DockingSession docked = transition(
                locking,
                DockingPhase.DOCKED,
                Optional.empty(),
                0,
                DockingEvidence.CONFIRMED,
                DockingEvidence.CONFIRMED,
                false,
                false,
                DockingReason.CONNECTOR_LOCK_CONFIRMED,
                Optional.empty(),
                gameTime
        );
        return transition(
                docked,
                DockingPhase.WAITING,
                Optional.empty(),
                0,
                DockingEvidence.CONFIRMED,
                DockingEvidence.CONFIRMED,
                true,
                false,
                DockingReason.CONDITIONS_ACTIVE,
                Optional.empty(),
                gameTime
        );
    }

    private DockingSession recoverTo(DockingSession session, DockingPhase resumePhase, long gameTime) {
        if ((session.phase() == DockingPhase.BLOCKED || session.phase() == DockingPhase.RECOVERING)
                && session.resumePhase().filter(resumePhase::equals).isPresent()) {
            return transition(
                    session,
                    resumePhase,
                    Optional.empty(),
                    0,
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.APPROACH_REACQUIRE,
                    Optional.of("resume_context_available"),
                    gameTime
            );
        }
        return session;
    }

    private DockingSession blocked(
            DockingSession session,
            DockingPhase resumePhase,
            String detail,
            long gameTime
    ) {
        if (session.phase() == DockingPhase.BLOCKED) {
            return copy(
                    session,
                    DockingPhase.BLOCKED,
                    Optional.of(resumePhase),
                    session.queuePosition(),
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    false,
                    DockingReason.APPROACH_REACQUIRE,
                    Optional.of(detail),
                    gameTime
            );
        }
        return transition(
                session,
                DockingPhase.BLOCKED,
                Optional.of(resumePhase),
                session.queuePosition(),
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                false,
                DockingReason.APPROACH_REACQUIRE,
                Optional.of(detail),
                gameTime
        );
    }

    private DockingSession fault(DockingSession session, PlaybackFailure failure, long gameTime) {
        return fault(session, failure, failure.name(), gameTime);
    }

    public DockingSession fault(
            DockingSession session,
            PlaybackFailure failure,
            String detail,
            long gameTime
    ) {
        if (session.phase() == DockingPhase.NONE) {
            return copy(
                    session,
                    DockingPhase.NONE,
                    Optional.empty(),
                    -1,
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    true,
                    DockingReason.PLAYBACK_FAULTED,
                    Optional.of(failure.name() + ":" + detail),
                    gameTime
            );
        }
        if (session.phase() == DockingPhase.FAULTED) {
            return copy(
                    session,
                    DockingPhase.FAULTED,
                    session.resumePhase(),
                    session.queuePosition(),
                    DockingEvidence.NOT_CONFIRMED,
                    DockingEvidence.NOT_CONFIRMED,
                    false,
                    true,
                    DockingReason.PLAYBACK_FAULTED,
                    Optional.of(failure.name() + ":" + detail),
                    gameTime
            );
        }
        return transition(
                session,
                DockingPhase.FAULTED,
                Optional.of(resumePhase(session)),
                session.queuePosition(),
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                true,
                DockingReason.PLAYBACK_FAULTED,
                Optional.of(failure.name() + ":" + detail),
                gameTime
        );
    }

    private DockingPhase resumePhase(DockingSession session) {
        return switch (session.phase()) {
            case DOCKED, WAITING, LOCKING -> DockingPhase.LOCKING;
            case ALIGNING -> DockingPhase.ALIGNING;
            case QUEUED, QUEUE_REQUESTED -> DockingPhase.QUEUED;
            default -> DockingPhase.APPROACHING;
        };
    }

    private DockingSession withTarget(
            DockingSession session,
            Optional<DockTargetId> target,
            long gameTime
    ) {
        return new DockingSession(
                session.schemaVersion(),
                session.requestId(),
                session.routeId(),
                target,
                session.phase(),
                session.resumePhase(),
                session.runtimeMode(),
                session.queuePosition(),
                session.outputsOwned(),
                session.expectedConnectorLocked(),
                session.waitActive(),
                session.paused(),
                session.reason(),
                session.reasonDetail(),
                gameTime
        );
    }

    private DockingSession transition(
            DockingSession session,
            DockingPhase next,
            Optional<DockingPhase> resumePhase,
            int queuePosition,
            DockingEvidence outputsOwned,
            DockingEvidence expectedConnectorLocked,
            boolean waitActive,
            boolean paused,
            DockingReason reason,
            Optional<String> detail,
            long gameTime
    ) {
        if (!session.validateTransitionTo(next).allowed()) {
            CreateAeronauticsAutomatedLogistics.debugDockingWarn(
                    "Docking coordinator refused transition: request={} route={} from={} to={} reason={} detail={}",
                    session.requestId().value(),
                    session.routeId().value(),
                    session.phase(),
                    next,
                    reason,
                    detail.orElse("none")
            );
            return session;
        }
        DockingSession nextSession = copy(
                session,
                next,
                resumePhase,
                queuePosition,
                outputsOwned,
                expectedConnectorLocked,
                waitActive,
                paused,
                reason,
                detail,
                gameTime
        );
        if (session.phase() != next || session.reason() != reason) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Docking coordinator transition: request={} route={} from={} to={} reason={} detail={} target={} queuePosition={} outputs={} lock={} waitActive={}",
                    session.requestId().value(),
                    session.routeId().value(),
                    session.phase(),
                    next,
                    reason,
                    detail.orElse("none"),
                    nextSession.target().map(DockingCoordinator::targetSummary).orElse("none"),
                    queuePosition,
                    outputsOwned,
                    expectedConnectorLocked,
                    waitActive
            );
        }
        return nextSession;
    }

    private static PlaybackFailure endpointFailure(DockEndpointResolver.Status status) {
        return switch (status) {
            case STATION_NOT_LOADED, STATION_MISSING, STATION_ID_MISMATCH -> PlaybackFailure.STATION_MISSING;
            case STATION_DOCK_AMBIGUOUS -> PlaybackFailure.AMBIGUOUS_DOCK;
            case READY -> throw new IllegalArgumentException("ready endpoint has no failure");
            default -> PlaybackFailure.MISSING_DOCK;
        };
    }

    private static String targetSummary(DockTargetId target) {
        return target.dimension().location()
                + ":"
                + target.stationPos().toShortString()
                + ":"
                + target.stationDockPos().map(pos -> pos.toShortString()).orElse("dock_unknown");
    }

    private DockingSession copy(
            DockingSession session,
            DockingPhase phase,
            Optional<DockingPhase> resumePhase,
            int queuePosition,
            DockingEvidence outputsOwned,
            DockingEvidence expectedConnectorLocked,
            boolean waitActive,
            boolean paused,
            DockingReason reason,
            Optional<String> detail,
            long gameTime
    ) {
        return new DockingSession(
                session.schemaVersion(),
                session.requestId(),
                session.routeId(),
                session.target(),
                phase,
                resumePhase,
                session.runtimeMode(),
                queuePosition,
                outputsOwned,
                expectedConnectorLocked,
                waitActive,
                paused,
                reason,
                detail,
                gameTime
        );
    }

    public enum QueueDirective {
        APPROACH,
        HOLD,
        DEFERRED_FOR_DEPARTURE,
        FAULTED
    }

    public record QueueRequest(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            DockRequestId requestId,
            DockingSession session
    ) {
        public QueueRequest {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(session, "session");
        }
    }

    public record QueueOutcome(
            DockingSession session,
            QueueDirective directive,
            int queuePosition,
            boolean changed,
            Optional<PlaybackFailure> failure,
            String reason
    ) {
        private static QueueOutcome failed(
                DockingSession session,
                PlaybackFailure failure,
                String reason
        ) {
            return new QueueOutcome(
                    session,
                    QueueDirective.FAULTED,
                    -1,
                    false,
                    Optional.of(failure),
                    reason
            );
        }

        public boolean held() {
            return directive == QueueDirective.HOLD;
        }
    }

    record ReservationObservation(
            boolean granted,
            int queuePosition,
            boolean changed,
            DockReservationService.ReservationReason reason,
            Optional<PlaybackFailure> failure
    ) {
        ReservationObservation {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(failure, "failure");
        }
    }

    public enum HandshakeDirective {
        WAIT_FOR_RESERVATION,
        APPROACH_AND_ALIGN,
        RELEASE_MOTION_CONTROL,
        HOLD_FOR_LOCK,
        LOCKED,
        BLOCKED,
        FAULTED
    }

    public record HandshakeRequest(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            DockRequestId requestId,
            DockingSession session,
            DockPoseProof poseProof,
            int lockTimeoutTicksRemaining
    ) {
        public HandshakeRequest {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(poseProof, "poseProof");
            lockTimeoutTicksRemaining = Math.max(0, lockTimeoutTicksRemaining);
        }
    }

    public record HandshakeOutcome(
            DockingSession session,
            HandshakeDirective directive,
            int lockTimeoutTicksRemaining,
            Optional<PlaybackFailure> failure,
            String reason
    ) {
    }
}

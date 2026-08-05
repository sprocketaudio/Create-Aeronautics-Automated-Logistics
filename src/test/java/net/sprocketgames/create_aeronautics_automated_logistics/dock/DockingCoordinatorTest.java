package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockTargetId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingEvidence;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingPhase;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingReason;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingRuntimeMode;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingSession;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import org.junit.jupiter.api.Test;

class DockingCoordinatorTest {
    private static final RouteId ROUTE_ID =
            new RouteId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
    private static final DockRequestId REQUEST_ID = DockRequestId.legacy(
            ROUTE_ID,
            Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000302")),
            Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000303"))
    );
    private static final DockTargetId TARGET = new DockTargetId(
            Level.OVERWORLD,
            Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000304")),
            new BlockPos(10, 64, 20),
            Optional.of(new BlockPos(11, 64, 20))
    );

    private final DockingCoordinator coordinator = coordinator();

    @Test
    void queueGrantTransitionsThroughQueuedToApproaching() {
        DockingCoordinator.QueueOutcome queued = coordinator.observeReservation(
                session(DockingPhase.NONE),
                Optional.of(TARGET),
                observation(false, 2, DockReservationService.ReservationReason.QUEUED),
                10L
        );

        assertEquals(DockingPhase.QUEUED, queued.session().phase());
        assertEquals(2, queued.session().queuePosition());
        assertEquals(DockingCoordinator.QueueDirective.HOLD, queued.directive());

        DockingCoordinator.QueueOutcome granted = coordinator.observeReservation(
                queued.session(),
                Optional.of(TARGET),
                observation(true, 0, DockReservationService.ReservationReason.GRANTED),
                20L
        );

        assertEquals(DockingPhase.APPROACHING, granted.session().phase());
        assertEquals(DockingCoordinator.QueueDirective.APPROACH, granted.directive());
    }

    @Test
    void unloadedQueueGrantKeepsUnloadedRuntimeMode() {
        DockingCoordinator.QueueOutcome queued = coordinator.observeReservation(
                session(DockingPhase.NONE, DockingRuntimeMode.UNLOADED),
                Optional.of(TARGET),
                observation(false, 2, DockReservationService.ReservationReason.QUEUED),
                10L
        );

        assertEquals(DockingPhase.QUEUED, queued.session().phase());
        assertEquals(DockingRuntimeMode.UNLOADED, queued.session().runtimeMode());

        DockingCoordinator.QueueOutcome granted = coordinator.observeReservation(
                queued.session(),
                Optional.of(TARGET),
                observation(true, 0, DockReservationService.ReservationReason.GRANTED),
                20L
        );

        assertEquals(DockingPhase.APPROACHING, granted.session().phase());
        assertEquals(DockingRuntimeMode.UNLOADED, granted.session().runtimeMode());
    }

    @Test
    void runtimeModeTransitionPreservesRestoredSessionState() {
        DockingSession restoredQueued = session(DockingPhase.QUEUED);

        DockingSession unloaded = coordinator.withRuntimeMode(
                restoredQueued,
                DockingRuntimeMode.UNLOADED,
                30L,
                "restore_test"
        );

        assertEquals(DockingPhase.QUEUED, unloaded.phase());
        assertEquals(Optional.of(TARGET), unloaded.target());
        assertEquals(restoredQueued.requestId(), unloaded.requestId());
        assertEquals(restoredQueued.queuePosition(), unloaded.queuePosition());
        assertEquals(DockingRuntimeMode.UNLOADED, unloaded.runtimeMode());
    }

    @Test
    void lostGrantBlocksThenResumesApproachWithoutQueueShortcut() {
        DockingCoordinator.QueueOutcome lost = coordinator.observeReservation(
                session(DockingPhase.APPROACHING),
                Optional.of(TARGET),
                observation(false, 1, DockReservationService.ReservationReason.QUEUED),
                30L
        );

        assertEquals(DockingPhase.BLOCKED, lost.session().phase());
        assertEquals(Optional.of(DockingPhase.APPROACHING), lost.session().resumePhase());

        DockingCoordinator.QueueOutcome regained = coordinator.observeReservation(
                lost.session(),
                Optional.of(TARGET),
                observation(true, 0, DockReservationService.ReservationReason.GRANTED),
                40L
        );

        assertEquals(DockingPhase.APPROACHING, regained.session().phase());
    }

    @Test
    void releaseAndClearanceUseExplicitTerminalPhases() {
        DockingSession waiting = session(
                DockingPhase.WAITING,
                DockingEvidence.CONFIRMED,
                DockingEvidence.CONFIRMED,
                true
        );

        DockingSession releasing = coordinator.beginRelease(waiting, 50L);
        DockingSession departing = coordinator.beginDepartureClearance(releasing, 60L);
        DockingSession complete = coordinator.completeDeparture(departing, 70L);

        assertEquals(DockingPhase.RELEASING, releasing.phase());
        assertEquals(DockingPhase.DEPARTING_CLEARANCE, departing.phase());
        assertEquals(DockingPhase.NONE, complete.phase());
    }

    @Test
    void routeHandoffInheritsDepartureClearanceForStableRequest() {
        DockingSession inherited = coordinator.inheritDepartureClearance(
                session(DockingPhase.NONE),
                TARGET,
                75L
        );

        assertEquals(DockingPhase.DEPARTING_CLEARANCE, inherited.phase());
        assertEquals(Optional.of(TARGET), inherited.target());
        assertEquals(DockingReason.DEPARTURE_CLEARANCE, inherited.reason());
    }

    @Test
    void faultRemainsInspectableAndResumesThroughRecovering() {
        DockingSession faulted = coordinator.fault(
                session(DockingPhase.LOCKING),
                net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure.DOCK_LOCK_FAILED,
                "test",
                80L
        );

        assertEquals(DockingPhase.FAULTED, faulted.phase());
        assertEquals(DockingReason.PLAYBACK_FAULTED, faulted.reason());

        DockingSession recovering = coordinator.resume(faulted, 90L);

        assertEquals(DockingPhase.RECOVERING, recovering.phase());
    }

    private static DockingCoordinator coordinator() {
        DockEndpointResolver endpoints = new DockEndpointResolver();
        DockReservationService reservations = new DockReservationService();
        return new DockingCoordinator(
                endpoints,
                reservations,
                new DockHandshakeService(endpoints, reservations)
        );
    }

    private static DockingCoordinator.ReservationObservation observation(
            boolean granted,
            int queuePosition,
            DockReservationService.ReservationReason reason
    ) {
        return new DockingCoordinator.ReservationObservation(
                granted,
                queuePosition,
                true,
                reason,
                Optional.empty()
        );
    }

    private static DockingSession session(DockingPhase phase) {
        return session(phase, DockingRuntimeMode.LOADED);
    }

    private static DockingSession session(DockingPhase phase, DockingRuntimeMode runtimeMode) {
        return session(
                phase,
                DockingEvidence.NOT_CONFIRMED,
                DockingEvidence.NOT_CONFIRMED,
                false,
                runtimeMode
        );
    }

    private static DockingSession session(
            DockingPhase phase,
            DockingEvidence outputs,
            DockingEvidence lock,
            boolean waitActive
    ) {
        return session(
                phase,
                outputs,
                lock,
                waitActive,
                DockingRuntimeMode.LOADED
        );
    }

    private static DockingSession session(
            DockingPhase phase,
            DockingEvidence outputs,
            DockingEvidence lock,
            boolean waitActive,
            DockingRuntimeMode runtimeMode
    ) {
        return new DockingSession(
                DockingSession.CURRENT_SCHEMA_VERSION,
                REQUEST_ID,
                ROUTE_ID,
                Optional.of(TARGET),
                phase,
                Optional.empty(),
                runtimeMode,
                phase == DockingPhase.QUEUED ? 1 : 0,
                outputs,
                lock,
                waitActive,
                false,
                DockingReason.UNKNOWN,
                Optional.empty(),
                0L
        );
    }
}

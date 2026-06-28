package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import org.junit.jupiter.api.Test;

class DockingSessionTest {
    private static final RouteId ROUTE_ID =
            new RouteId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final UUID TRANSPONDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID STOP_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");

    @Test
    void projectsQueuedLoadedPlaybackWithoutClaimingUnknownOutputOwnership() {
        DockingSessionProjection.ProjectionResult result = project(
                false, false, false, true, true, false, 2,
                false, true, false, false, false, DockingRuntimeMode.LOADED
        );

        assertEquals(DockingPhase.QUEUED, result.session().phase());
        assertEquals(DockingReason.RESERVATION_QUEUED, result.session().reason());
        assertEquals(2, result.session().queuePosition());
        assertEquals(DockingEvidence.UNKNOWN, result.session().outputsOwned());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void projectsUnloadedDockWaitAndFaultedStateWithDeterministicPrecedence() {
        DockingSessionProjection.ProjectionResult waiting = project(
                false, false, false, false, true, true, 0,
                true, true, true, false, false, DockingRuntimeMode.UNLOADED
        );
        assertEquals(DockingPhase.WAITING, waiting.session().phase());
        assertEquals(DockingRuntimeMode.UNLOADED, waiting.session().runtimeMode());

        DockingSessionProjection.ProjectionResult faulted = project(
                false, true, true, true, true, false, 1,
                true, true, false, true, false, DockingRuntimeMode.LOADED
        );
        assertEquals(DockingPhase.FAULTED, faulted.session().phase());
        assertEquals(Optional.of(DockingPhase.QUEUED), faulted.session().resumePhase());
    }

    @Test
    void projectsApproachAndDepartureClearanceSeparately() {
        DockingSessionProjection.ProjectionResult approaching = project(
                false, false, false, false, true, true, 0,
                true, true, false, true, false, DockingRuntimeMode.LOADED
        );
        assertEquals(DockingPhase.APPROACHING, approaching.session().phase());
        assertEquals(DockingReason.APPROACH_REACQUIRE, approaching.session().reason());

        DockingSessionProjection.ProjectionResult departing = project(
                false, false, false, false, true, true, 0,
                false, false, false, false, true, DockingRuntimeMode.LOADED
        );
        assertEquals(DockingPhase.DEPARTING_CLEARANCE, departing.session().phase());
    }

    @Test
    void reportsImpossibleLegacyCombinationsWithoutRefusingProjection() {
        DockingSessionProjection.ProjectionResult result = project(
                false, false, false, true, true, true, 3,
                false, true, true, false, false, DockingRuntimeMode.LOADED
        );

        assertEquals(DockingPhase.APPROACHING, result.session().phase());
        assertEquals(DockingReason.INCONSISTENT_LEGACY_STATE, result.session().reason());
        assertTrue(result.diagnostics().contains("queue_hold_present_while_reservation_granted"));
        assertTrue(result.diagnostics().contains("connector_locked_without_wait"));
        assertTrue(result.diagnostics().contains("granted_reservation_has_queue_position"));
    }

    @Test
    void codecRoundTripsVersionedSessionAndDistinguishesLegacyFromInvalid() {
        DockingSession original = project(
                false, false, false, true, true, false, 1,
                false, true, false, false, false, DockingRuntimeMode.UNLOADED
        ).session();

        DockingSessionReadResult restored = DockingSessionCodec.read(DockingSessionCodec.write(original));
        assertEquals(DockingSessionReadResult.Status.RESTORED, restored.status(), restored.diagnostics().toString());
        assertEquals(original, restored.session().orElseThrow());

        DockingSessionReadResult legacy = DockingSessionCodec.read(new CompoundTag());
        assertEquals(DockingSessionReadResult.Status.LEGACY_MISSING, legacy.status());
        assertTrue(legacy.session().isEmpty());

        CompoundTag invalid = DockingSessionCodec.write(original);
        invalid.putInt("schemaVersion", DockingSession.CURRENT_SCHEMA_VERSION + 1);
        DockingSessionReadResult refused = DockingSessionCodec.read(invalid);
        assertEquals(DockingSessionReadResult.Status.INVALID, refused.status());
        assertTrue(refused.session().isEmpty());
    }

    @Test
    void transitionValidationRejectsQueueToWaitingShortcut() {
        DockingSession queued = project(
                false, false, false, true, true, false, 1,
                false, true, false, false, false, DockingRuntimeMode.LOADED
        ).session();

        assertFalse(queued.validateTransitionTo(DockingPhase.WAITING).allowed());
        assertTrue(queued.validateTransitionTo(DockingPhase.APPROACHING).allowed());
    }

    private static DockingSessionProjection.ProjectionResult project(
            boolean completed,
            boolean paused,
            boolean faulted,
            boolean queueHeld,
            boolean reservationTracked,
            boolean reservationGranted,
            int queuePosition,
            boolean waitActive,
            boolean requiresDockLock,
            boolean connectorLocked,
            boolean reacquireMotion,
            boolean departingClearance,
            DockingRuntimeMode runtimeMode
    ) {
        return DockingSessionProjection.project(new DockingSessionProjection.LegacyState(
                ROUTE_ID,
                Optional.empty(),
                Optional.of(TRANSPONDER_ID),
                Optional.empty(),
                Optional.of(STOP_ID),
                Optional.of(new DockTargetId(
                        Level.OVERWORLD,
                        Optional.empty(),
                        new BlockPos(10, 64, 20),
                        Optional.empty()
                )),
                runtimeMode,
                completed,
                paused,
                faulted,
                queueHeld,
                reservationTracked,
                reservationGranted,
                queuePosition,
                waitActive,
                requiresDockLock,
                connectorLocked,
                reacquireMotion,
                departingClearance,
                DockingEvidence.UNKNOWN,
                Optional.of("test"),
                42L
        ));
    }
}

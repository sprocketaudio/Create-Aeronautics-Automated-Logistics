package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public final class DockingSessionProjection {
    private DockingSessionProjection() {
    }

    public static ProjectionResult project(LegacyState state) {
        Objects.requireNonNull(state, "state");
        List<String> diagnostics = new ArrayList<>();
        DockingPhase phase;
        DockingReason reason;
        Optional<DockingPhase> resumePhase = Optional.empty();

        if (state.completed()) {
            phase = DockingPhase.COMPLETE;
            reason = DockingReason.PLAYBACK_COMPLETED;
        } else if (state.faulted()) {
            phase = DockingPhase.FAULTED;
            reason = DockingReason.PLAYBACK_FAULTED;
            resumePhase = safeResumePhase(state);
        } else if (state.paused()) {
            phase = DockingPhase.BLOCKED;
            reason = DockingReason.PLAYBACK_PAUSED;
            resumePhase = safeResumePhase(state);
        } else if (state.departingClearance()) {
            phase = DockingPhase.DEPARTING_CLEARANCE;
            reason = DockingReason.DEPARTURE_CLEARANCE;
        } else if (state.queueHeld()) {
            if (state.reservationGranted()) {
                phase = DockingPhase.APPROACHING;
                reason = DockingReason.RESERVATION_GRANTED;
                diagnostics.add("queue_hold_present_while_reservation_granted");
            } else if (state.reservationTracked()) {
                phase = DockingPhase.QUEUED;
                reason = DockingReason.RESERVATION_QUEUED;
            } else {
                phase = DockingPhase.QUEUE_REQUESTED;
                reason = DockingReason.RESERVATION_REQUESTED;
                diagnostics.add("queue_hold_without_tracked_reservation");
            }
        } else if (state.waitActive() && state.requiresDockLock()) {
            if (state.connectorLocked()) {
                phase = DockingPhase.WAITING;
                reason = DockingReason.CONNECTOR_LOCK_CONFIRMED;
            } else if (state.reacquireMotionActive()) {
                phase = DockingPhase.APPROACHING;
                reason = DockingReason.APPROACH_REACQUIRE;
            } else {
                phase = DockingPhase.LOCKING;
                reason = DockingReason.CONNECTOR_LOCK_PENDING;
            }
        } else if (state.waitActive()) {
            phase = DockingPhase.WAITING;
            reason = DockingReason.CONDITIONS_ACTIVE;
        } else if (state.reservationGranted()) {
            phase = DockingPhase.APPROACHING;
            reason = DockingReason.RESERVATION_GRANTED;
        } else {
            phase = DockingPhase.NONE;
            reason = state.runtimeMode() == DockingRuntimeMode.UNLOADED
                    ? DockingReason.LEGACY_UNLOADED_TRANSIT
                    : DockingReason.NO_DOCK_CONTEXT;
        }

        if (state.connectorLocked() && !state.waitActive()) {
            diagnostics.add("connector_locked_without_wait");
        }
        if (state.queuePosition() > 0 && state.reservationGranted()) {
            diagnostics.add("granted_reservation_has_queue_position");
        }
        if (state.requiresDockLock() && state.target().isEmpty()) {
            diagnostics.add("dock_required_without_target");
        }

        DockingSession session = new DockingSession(
                DockingSession.CURRENT_SCHEMA_VERSION,
                state.requestId().orElseGet(() -> state.scheduleExecutionId()
                        .flatMap(executionId -> state.transponderId().map(transponderId -> DockRequestId.scheduled(
                                transponderId,
                                executionId,
                                state.stopId(),
                                state.routeId()
                        )))
                        .orElseGet(() -> DockRequestId.legacy(
                                state.routeId(),
                                state.transponderId(),
                                state.stopId()
                        ))),
                state.routeId(),
                state.target(),
                phase,
                resumePhase,
                state.runtimeMode(),
                state.queuePosition(),
                state.outputsOwned(),
                state.connectorLocked() ? DockingEvidence.CONFIRMED : DockingEvidence.NOT_CONFIRMED,
                state.waitActive(),
                state.paused(),
                diagnostics.isEmpty() ? reason : DockingReason.INCONSISTENT_LEGACY_STATE,
                state.reasonDetail(),
                state.observedGameTime()
        );
        return new ProjectionResult(session, diagnostics);
    }

    private static Optional<DockingPhase> safeResumePhase(LegacyState state) {
        if (state.queueHeld()) {
            return Optional.of(DockingPhase.QUEUED);
        }
        if (state.waitActive() && state.connectorLocked()) {
            return Optional.of(DockingPhase.WAITING);
        }
        if (state.waitActive() && state.requiresDockLock()) {
            return Optional.of(DockingPhase.LOCKING);
        }
        return Optional.of(DockingPhase.NONE);
    }

    public record ProjectionResult(DockingSession session, List<String> diagnostics) {
        public ProjectionResult {
            Objects.requireNonNull(session, "session");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record LegacyState(
            RouteId routeId,
            Optional<DockRequestId> requestId,
            Optional<UUID> transponderId,
            Optional<UUID> scheduleExecutionId,
            Optional<UUID> stopId,
            Optional<DockTargetId> target,
            DockingRuntimeMode runtimeMode,
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
            boolean reacquireMotionActive,
            boolean departingClearance,
            DockingEvidence outputsOwned,
            Optional<String> reasonDetail,
            long observedGameTime
    ) {
        public LegacyState {
            Objects.requireNonNull(routeId, "routeId");
            requestId = Objects.requireNonNull(requestId, "requestId");
            transponderId = Objects.requireNonNull(transponderId, "transponderId");
            scheduleExecutionId = Objects.requireNonNull(scheduleExecutionId, "scheduleExecutionId");
            stopId = Objects.requireNonNull(stopId, "stopId");
            target = Objects.requireNonNull(target, "target");
            Objects.requireNonNull(runtimeMode, "runtimeMode");
            if (queuePosition < -1) {
                throw new IllegalArgumentException("queuePosition must be -1 or greater");
            }
            Objects.requireNonNull(outputsOwned, "outputsOwned");
            reasonDetail = Objects.requireNonNull(reasonDetail, "reasonDetail");
        }

        public static LegacyState idle(RouteId routeId, ResourceKey<Level> dimension, BlockPos stationPos) {
            return new LegacyState(
                    routeId,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new DockTargetId(dimension, Optional.empty(), stationPos, Optional.empty())),
                    DockingRuntimeMode.LOADED,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    -1,
                    false,
                    false,
                    false,
                    false,
                    false,
                    DockingEvidence.UNKNOWN,
                    Optional.empty(),
                    0L
            );
        }
    }
}

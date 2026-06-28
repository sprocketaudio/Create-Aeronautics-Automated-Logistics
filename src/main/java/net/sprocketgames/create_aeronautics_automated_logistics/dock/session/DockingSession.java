package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public record DockingSession(
        int schemaVersion,
        DockRequestId requestId,
        RouteId routeId,
        Optional<DockTargetId> target,
        DockingPhase phase,
        Optional<DockingPhase> resumePhase,
        DockingRuntimeMode runtimeMode,
        int queuePosition,
        DockingEvidence outputsOwned,
        DockingEvidence expectedConnectorLocked,
        boolean waitActive,
        boolean paused,
        DockingReason reason,
        Optional<String> reasonDetail,
        long observedGameTime
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final Map<DockingPhase, EnumSet<DockingPhase>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(DockingPhase.NONE, EnumSet.of(DockingPhase.NONE, DockingPhase.QUEUE_REQUESTED)),
            Map.entry(DockingPhase.QUEUE_REQUESTED, EnumSet.of(
                    DockingPhase.QUEUE_REQUESTED, DockingPhase.QUEUED, DockingPhase.APPROACHING,
                    DockingPhase.BLOCKED, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.QUEUED, EnumSet.of(
                    DockingPhase.QUEUED, DockingPhase.APPROACHING, DockingPhase.BLOCKED,
                    DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.APPROACHING, EnumSet.of(
                    DockingPhase.APPROACHING, DockingPhase.ALIGNING, DockingPhase.BLOCKED,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.ALIGNING, EnumSet.of(
                    DockingPhase.ALIGNING, DockingPhase.LOCKING, DockingPhase.BLOCKED,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.LOCKING, EnumSet.of(
                    DockingPhase.LOCKING, DockingPhase.DOCKED, DockingPhase.BLOCKED,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.DOCKED, EnumSet.of(
                    DockingPhase.DOCKED, DockingPhase.WAITING, DockingPhase.RELEASING,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.WAITING, EnumSet.of(
                    DockingPhase.WAITING, DockingPhase.RELEASING, DockingPhase.BLOCKED,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.RELEASING, EnumSet.of(
                    DockingPhase.RELEASING, DockingPhase.DEPARTING_CLEARANCE,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.DEPARTING_CLEARANCE, EnumSet.of(
                    DockingPhase.DEPARTING_CLEARANCE, DockingPhase.COMPLETE,
                    DockingPhase.RECOVERING, DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.COMPLETE, EnumSet.of(DockingPhase.COMPLETE, DockingPhase.NONE)),
            Map.entry(DockingPhase.BLOCKED, EnumSet.of(
                    DockingPhase.BLOCKED, DockingPhase.RECOVERING,
                    DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.RECOVERING, EnumSet.of(
                    DockingPhase.RECOVERING, DockingPhase.BLOCKED,
                    DockingPhase.FAULTED, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.FAULTED, EnumSet.of(
                    DockingPhase.FAULTED, DockingPhase.RECOVERING, DockingPhase.CANCELLED)),
            Map.entry(DockingPhase.CANCELLED, EnumSet.of(DockingPhase.CANCELLED, DockingPhase.NONE))
    );

    public DockingSession {
        if (schemaVersion <= 0 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported docking session schema " + schemaVersion);
        }
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeId, "routeId");
        target = Objects.requireNonNull(target, "target");
        Objects.requireNonNull(phase, "phase");
        resumePhase = Objects.requireNonNull(resumePhase, "resumePhase");
        Objects.requireNonNull(runtimeMode, "runtimeMode");
        if (queuePosition < -1) {
            throw new IllegalArgumentException("queuePosition must be -1 or greater");
        }
        Objects.requireNonNull(outputsOwned, "outputsOwned");
        Objects.requireNonNull(expectedConnectorLocked, "expectedConnectorLocked");
        Objects.requireNonNull(reason, "reason");
        reasonDetail = Objects.requireNonNull(reasonDetail, "reasonDetail")
                .map(String::trim)
                .filter(detail -> !detail.isEmpty());
    }

    public DockingTransitionValidation validateTransitionTo(DockingPhase next) {
        Objects.requireNonNull(next, "next");
        if ((phase == DockingPhase.BLOCKED || phase == DockingPhase.RECOVERING)
                && resumePhase.filter(next::equals).isPresent()) {
            return DockingTransitionValidation.accepted();
        }
        if (ALLOWED_TRANSITIONS.getOrDefault(phase, EnumSet.noneOf(DockingPhase.class)).contains(next)) {
            return DockingTransitionValidation.accepted();
        }
        return DockingTransitionValidation.refused("transition_" + phase + "_to_" + next + "_not_allowed");
    }

    public boolean hasDockingContext() {
        return phase != DockingPhase.NONE && phase != DockingPhase.COMPLETE;
    }
}

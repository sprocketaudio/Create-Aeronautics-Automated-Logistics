package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

public enum DockingPhase {
    NONE,
    QUEUE_REQUESTED,
    QUEUED,
    APPROACHING,
    ALIGNING,
    LOCKING,
    DOCKED,
    WAITING,
    RELEASING,
    DEPARTING_CLEARANCE,
    COMPLETE,
    BLOCKED,
    RECOVERING,
    FAULTED,
    CANCELLED
}

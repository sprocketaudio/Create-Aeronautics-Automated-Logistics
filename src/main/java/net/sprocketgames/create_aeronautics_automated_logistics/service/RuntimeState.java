package net.sprocketgames.create_aeronautics_automated_logistics.service;

public enum RuntimeState {
    IDLE,
    STARTING,
    RUNNING_LOADED,
    RUNNING_UNLOADED,
    MATERIALIZING,
    WAITING,
    PAUSED_MANUAL,
    PAUSED_FAULT,
    MISSING_PLAYBACK,
    MISSING_CONTROLLER,
    ROUTE_MISSING,
    RESTORE_FAILED,
    INVALID_RUNTIME,
    RECOVERING,
    KILLED,
    COMPLETED
}

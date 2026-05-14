package net.sprocketgames.create_aeronautics_automated_logistics.route;

public enum RouteStatus {
    IDLE,
    RECORDING,
    RECORDED,
    RUNNING,
    WAITING,
    HELD,
    HELD_FAULTED,
    FAILED,
    BLOCKED,
    MISSING_VEHICLE,
    INVALID_ROUTE
}

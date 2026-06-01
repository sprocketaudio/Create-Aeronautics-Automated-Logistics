package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;

public enum PlaybackFailure {
    STATION_MISSING(FailureReason.MISSING_STATION),
    VEHICLE_MISSING(FailureReason.VEHICLE_DESTROYED_OR_MISSING),
    VEHICLE_UNLOADED(FailureReason.VEHICLE_UNLOADED),
    START_TOO_FAR_FROM_ROUTE(FailureReason.START_TOO_FAR_FROM_ROUTE),
    WRONG_START_STATION(FailureReason.START_TOO_FAR_FROM_ROUTE),
    MISSING_CONTROLLER(FailureReason.MISSING_AUTOPILOT_CONTROLLER),
    INVALID_ROUTE(FailureReason.INVALID_ROUTE_DATA),
    DIMENSION_MISMATCH(FailureReason.DIMENSION_MISMATCH),
    ALREADY_RUNNING(FailureReason.INVALID_ROUTE_DATA),
    COLLISION_OR_OBSTRUCTION(FailureReason.COLLISION_OR_OBSTRUCTION),
    MISSING_DOCK(FailureReason.MISSING_DOCK),
    AMBIGUOUS_DOCK(FailureReason.AMBIGUOUS_DOCK),
    DOCK_LOCK_FAILED(FailureReason.DOCK_LOCK_FAILED),
    REDSTONE_LINK_UNCONFIGURED(FailureReason.REDSTONE_LINK_UNCONFIGURED),
    CARGO_STORAGE_MISSING(FailureReason.CARGO_STORAGE_MISSING),
    CARGO_CONDITION_TIMEOUT(FailureReason.CARGO_CONDITION_TIMEOUT),
    STUCK(FailureReason.MOVEMENT_FAILURE),
    MOVEMENT_FAILURE(FailureReason.MOVEMENT_FAILURE);

    private final FailureReason failureReason;

    PlaybackFailure(FailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public FailureReason failureReason() {
        return failureReason;
    }
}

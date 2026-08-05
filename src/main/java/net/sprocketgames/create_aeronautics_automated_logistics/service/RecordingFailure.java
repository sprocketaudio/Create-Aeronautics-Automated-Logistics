package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;

public enum RecordingFailure {
    STATION_MISSING(FailureReason.MISSING_STATION),
    STATION_BUSY(FailureReason.INVALID_ROUTE_DATA),
    STATION_TYPE_MISMATCH(FailureReason.INVALID_ROUTE_DATA),
    VEHICLE_MISSING(FailureReason.VEHICLE_DESTROYED_OR_MISSING),
    VEHICLE_UNLOADED(FailureReason.VEHICLE_UNLOADED),
    VEHICLE_NOT_CONTROLLED(FailureReason.MISSING_AUTOPILOT_CONTROLLER),
    DIMENSION_MISMATCH(FailureReason.DIMENSION_MISMATCH),
    MAX_ACTIVE_VEHICLES_REACHED(FailureReason.INVALID_ROUTE_DATA),
    MAX_ROUTE_POINTS_REACHED(FailureReason.INVALID_ROUTE_DATA),
    ROUTE_TOO_SHORT(FailureReason.INVALID_ROUTE_DATA),
    UNKNOWN_ROUTE(FailureReason.INVALID_ROUTE_DATA);

    private final FailureReason failureReason;

    RecordingFailure(FailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public FailureReason failureReason() {
        return failureReason;
    }
}

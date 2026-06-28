package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public final class DockingRuntime {
    private DockingRuntime() {
    }

    public static DockReservationResult requestReservation(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            DockRequestId requestId
    ) {
        DockEndpointResolver.TargetResult target =
                AutomatedLogisticsServices.DOCK_ENDPOINTS.resolveTarget(level, station);
        if (!target.ready()) {
            return DockReservationResult.failed(failureFor(target.status()));
        }
        DockReservationService.ReservationResult result = AutomatedLogisticsServices.DOCK_RESERVATIONS.request(
                target.target().orElseThrow(),
                requestId,
                DockingRuntime::isActiveRequest
        );
        if (result.reason() == DockReservationService.ReservationReason.INCOMPLETE_TARGET) {
            return DockReservationResult.failed(PlaybackFailure.MISSING_DOCK);
        }
        return new DockReservationResult(
                result.granted(),
                result.queuePosition(),
                Optional.empty(),
                result.changed(),
                result.reason()
        );
    }

    public static DockReservationResult requestApproachReservation(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route,
            DockRequestId requestId
    ) {
        return requestReservation(level, station, route, requestId);
    }

    public static void releaseReservation(DockRequestId requestId) {
        AutomatedLogisticsServices.DOCK_HANDSHAKE.releaseRequest(requestId);
        AutomatedLogisticsServices.DOCK_RESERVATIONS.release(requestId);
    }

    public static void resetRuntimeState(String reason) {
        AutomatedLogisticsServices.DOCK_RESERVATIONS.reset(reason);
        AutomatedLogisticsServices.DOCK_HANDSHAKE.resetRuntime(reason);
    }

    public static CompoundTag saveRuntime() {
        return AutomatedLogisticsServices.DOCK_RESERVATIONS.save();
    }

    public static void loadRuntime(CompoundTag tag) {
        AutomatedLogisticsServices.DOCK_RESERVATIONS.load(tag);
    }

    public static DockReservationStatus reservationStatus(DockRequestId requestId) {
        return fromStatus(AutomatedLogisticsServices.DOCK_RESERVATIONS.status(requestId));
    }

    static boolean isActiveRequest(DockRequestId requestId) {
        if (AutomatedLogisticsServices.SCHEDULES.isDockRequestActive(requestId)) {
            return true;
        }
        RouteId routeId = requestId.legacyRouteId();
        return AutomatedLogisticsServices.PLAYBACK.isRunning(routeId)
                || AutomatedLogisticsServices.PLAYBACK.isPending(routeId)
                || AutomatedLogisticsServices.PLAYBACK.isHeld(routeId);
    }

    private static PlaybackFailure failureFor(DockEndpointResolver.Status status) {
        return switch (status) {
            case STATION_NOT_LOADED, STATION_MISSING, STATION_ID_MISMATCH -> PlaybackFailure.STATION_MISSING;
            case STATION_DOCK_AMBIGUOUS, SHIP_DOCK_AMBIGUOUS -> PlaybackFailure.AMBIGUOUS_DOCK;
            case SHIP_BODY_NOT_LOADED -> PlaybackFailure.VEHICLE_UNLOADED;
            case SHIP_TRANSPONDER_MISSING -> PlaybackFailure.MISSING_CONTROLLER;
            case READY -> throw new IllegalArgumentException("ready endpoint has no failure");
            default -> PlaybackFailure.MISSING_DOCK;
        };
    }

    private static DockReservationStatus fromStatus(DockReservationService.ReservationStatus status) {
        return new DockReservationStatus(status.tracked(), status.granted(), status.queuePosition());
    }

    public record DockReservationResult(
            boolean granted,
            int queuePosition,
            Optional<PlaybackFailure> failure,
            boolean changed,
            DockReservationService.ReservationReason reason
    ) {
        private static DockReservationResult failed(PlaybackFailure failure) {
            return new DockReservationResult(
                    false,
                    -1,
                    Optional.of(failure),
                    false,
                    DockReservationService.ReservationReason.INCOMPLETE_TARGET
            );
        }
    }

    public record DockReservationStatus(boolean tracked, boolean granted, int queuePosition) {
    }
}

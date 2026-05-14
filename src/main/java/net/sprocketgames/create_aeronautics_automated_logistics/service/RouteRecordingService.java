package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import java.util.Optional;
import java.util.UUID;

public interface RouteRecordingService {
    RouteOperationResult<RecordingSession> startRecording(ServerPlayer player, BlockPos stationPos, VehicleController controller);

    RouteOperationResult<Route> stopRecording(ServerPlayer player, RouteId routeId);

    RouteOperationResult<RouteSegment> finishSegmentRecording(ServerPlayer player, BlockPos endStationPos);

    default RouteOperationResult<RouteSegment> finishSegmentRecording(ServerPlayer player, BlockPos endStationPos, boolean continueRecording) {
        return finishSegmentRecording(player, endStationPos);
    }

    RouteOperationResult<Boolean> cancelRecording(ServerPlayer player);

    RouteOperationResult<RouteStop> markStop(ServerPlayer player, RouteId routeId, WaitCondition waitCondition);

    RouteOperationResult<RouteStop> markStop(ServerPlayer player, WaitCondition waitCondition);

    RouteOperationResult<RouteStop> updateLastStopWait(ServerPlayer player, RouteId routeId, WaitCondition waitCondition);

    RouteOperationResult<RouteStop> updateLastStopWait(ServerPlayer player, WaitCondition waitCondition);

    RouteOperationResult<RouteStop> cycleLastStopWait(ServerPlayer player);

    RouteOperationResult<RouteStop> lastStop(ServerPlayer player);

    boolean hasActiveRecording(ServerPlayer player);

    Optional<RecordingSession> activeRecordingForPlayer(UUID playerId);

    void tickRecording(ServerPlayer player, RouteId routeId);
}

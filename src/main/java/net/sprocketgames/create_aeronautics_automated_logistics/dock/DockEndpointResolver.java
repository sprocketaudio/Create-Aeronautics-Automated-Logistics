package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockTargetId;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public final class DockEndpointResolver {
    public StationResult resolveStation(
            ServerLevel level,
            RouteStop stop,
            Optional<AirshipStationBlockEntity> legacyFallbackStation,
            Optional<BlockPos> legacyTargetStationPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(legacyFallbackStation, "legacyFallbackStation");
        Objects.requireNonNull(legacyTargetStationPos, "legacyTargetStationPos");

        if (stop.dockPos().isPresent()) {
            BlockPos explicitStationPos = stop.dockPos().get();
            if (!level.isLoaded(explicitStationPos)) {
                return StationResult.failed(Status.STATION_NOT_LOADED, explicitStationPos);
            }
            Optional<AirshipStationBlockEntity> explicitStation = stationAt(level, explicitStationPos);
            if (explicitStation.isEmpty()) {
                return StationResult.failed(Status.STATION_MISSING, explicitStationPos);
            }
            if (stop.stationId().filter(expected -> !expected.equals(explicitStation.get().stationId())).isPresent()) {
                return StationResult.failed(Status.STATION_ID_MISMATCH, explicitStationPos);
            }
            return StationResult.ready(explicitStation.get());
        }

        Optional<AirshipStationBlockEntity> legacyStation = legacyFallbackStation
                .or(() -> legacyTargetStationPos.flatMap(pos -> stationAt(level, pos)));
        return legacyStation
                .map(StationResult::ready)
                .orElseGet(() -> StationResult.failed(Status.STATION_MISSING, legacyTargetStationPos.orElse(null)));
    }

    public TargetResult resolveTarget(ServerLevel level, AirshipStationBlockEntity station) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(station, "station");

        Optional<BlockPos> stationDockPos = station.groundDockPos();
        if (stationDockPos.isEmpty()) {
            return TargetResult.failed(Status.STATION_DOCK_MISSING);
        }
        if (station.groundDockStatus() == DockLinkStatus.AMBIGUOUS) {
            return TargetResult.failed(Status.STATION_DOCK_AMBIGUOUS);
        }
        BlockPos dockPos = stationDockPos.get();
        if (!level.isLoaded(dockPos)) {
            return TargetResult.failed(Status.CHUNK_NOT_READY);
        }
        if (!DockingConnectorDiscovery.isDock(level, dockPos)) {
            return TargetResult.failed(Status.STATION_DOCK_MISSING);
        }
        return TargetResult.ready(new DockTargetId(
                level.dimension(),
                Optional.of(station.stationId()),
                station.getBlockPos(),
                Optional.of(dockPos)
        ));
    }

    public PhysicalResult resolvePhysical(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(route, "route");

        TargetResult target = resolveTarget(level, station);
        if (!target.ready()) {
            return PhysicalResult.failed(target.status());
        }
        Optional<ShipTransponderBlockEntity> transponder = resolveTransponder(level, route);
        if (transponder.isEmpty()) {
            return PhysicalResult.failed(Status.SHIP_TRANSPONDER_MISSING);
        }
        Optional<BlockPos> shipDockPos = transponder.get().shipDockPos();
        if (shipDockPos.isEmpty()) {
            return PhysicalResult.failed(Status.SHIP_DOCK_MISSING);
        }
        if (transponder.get().shipDockStatus() == DockLinkStatus.AMBIGUOUS) {
            return PhysicalResult.failed(Status.SHIP_DOCK_AMBIGUOUS);
        }
        BlockPos shipDock = shipDockPos.get();
        if (!level.isLoaded(shipDock)) {
            return PhysicalResult.failed(Status.SHIP_BODY_NOT_LOADED);
        }
        if (!DockingConnectorDiscovery.isDock(level, shipDock)) {
            return PhysicalResult.failed(Status.SHIP_DOCK_MISSING);
        }
        return PhysicalResult.ready(
                target.target().orElseThrow(),
                station,
                transponder.get(),
                target.target().orElseThrow().stationDockPos().orElseThrow(),
                shipDock
        );
    }

    public Optional<ShipTransponderBlockEntity> resolveTransponder(ServerLevel level, Route route) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(route, "route");
        Optional<BlockPos> routeControllerPos = route.linkedController().controllerPos();
        if (routeControllerPos.isPresent()
                && level.getBlockEntity(routeControllerPos.get()) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }
        for (ShipTransponderSnapshot snapshot : ShipTransponderRegistry.knownShips(level.dimension())) {
            if (snapshot.controllerRef().filter(route.linkedController()::matches).isEmpty()) {
                continue;
            }
            if (level.getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
                return Optional.of(transponder);
            }
        }
        return Optional.empty();
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    public enum Status {
        READY,
        STATION_NOT_LOADED,
        STATION_MISSING,
        STATION_ID_MISMATCH,
        STATION_DOCK_MISSING,
        STATION_DOCK_AMBIGUOUS,
        SHIP_BODY_NOT_LOADED,
        SHIP_TRANSPONDER_MISSING,
        SHIP_DOCK_MISSING,
        SHIP_DOCK_AMBIGUOUS,
        WRONG_CONNECTOR_LOCKED,
        CHUNK_NOT_READY
    }

    public record StationResult(
            Status status,
            Optional<AirshipStationBlockEntity> station,
            Optional<BlockPos> requestedPosition
    ) {
        private static StationResult ready(AirshipStationBlockEntity station) {
            return new StationResult(
                    Status.READY,
                    Optional.of(station),
                    Optional.of(station.getBlockPos().immutable())
            );
        }

        private static StationResult failed(Status status, BlockPos requestedPosition) {
            return new StationResult(
                    status,
                    Optional.empty(),
                    Optional.ofNullable(requestedPosition).map(BlockPos::immutable)
            );
        }

        public boolean ready() {
            return status == Status.READY && station.isPresent();
        }
    }

    public record TargetResult(Status status, Optional<DockTargetId> target) {
        private static TargetResult ready(DockTargetId target) {
            return new TargetResult(Status.READY, Optional.of(target));
        }

        private static TargetResult failed(Status status) {
            return new TargetResult(status, Optional.empty());
        }

        public boolean ready() {
            return status == Status.READY && target.isPresent();
        }
    }

    public record PhysicalResult(
            Status status,
            Optional<DockTargetId> target,
            Optional<AirshipStationBlockEntity> station,
            Optional<ShipTransponderBlockEntity> transponder,
            Optional<BlockPos> stationDockPos,
            Optional<BlockPos> shipDockPos
    ) {
        private static PhysicalResult ready(
                DockTargetId target,
                AirshipStationBlockEntity station,
                ShipTransponderBlockEntity transponder,
                BlockPos stationDockPos,
                BlockPos shipDockPos
        ) {
            return new PhysicalResult(
                    Status.READY,
                    Optional.of(target),
                    Optional.of(station),
                    Optional.of(transponder),
                    Optional.of(stationDockPos.immutable()),
                    Optional.of(shipDockPos.immutable())
            );
        }

        private static PhysicalResult failed(Status status) {
            return new PhysicalResult(
                    status,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        public boolean ready() {
            return status == Status.READY;
        }

        public Optional<PlaybackFailure> failure() {
            if (ready()) {
                return Optional.empty();
            }
            return Optional.of(switch (status) {
                case STATION_NOT_LOADED, STATION_MISSING, STATION_ID_MISMATCH -> PlaybackFailure.STATION_MISSING;
                case STATION_DOCK_AMBIGUOUS, SHIP_DOCK_AMBIGUOUS -> PlaybackFailure.AMBIGUOUS_DOCK;
                case SHIP_BODY_NOT_LOADED -> PlaybackFailure.VEHICLE_UNLOADED;
                case SHIP_TRANSPONDER_MISSING -> PlaybackFailure.MISSING_CONTROLLER;
                case READY -> throw new IllegalStateException("ready endpoint has no failure");
                default -> PlaybackFailure.MISSING_DOCK;
            });
        }
    }
}

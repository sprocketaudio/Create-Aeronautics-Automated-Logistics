package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public final class DockingRuntime {
    private DockingRuntime() {
    }

    public static Optional<PlaybackFailure> beginDockingWait(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return context.failure();
        }
        context.station().setDockOutputActive(true);
        context.transponder().setDockOutputActive(true);
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Docking wait enabled outputs: stationDock={} shipDock={} route={}",
                context.stationDockPos().map(BlockPos::toShortString).orElse("-"),
                context.shipDockPos().map(BlockPos::toShortString).orElse("-"),
                route.id().value()
        );
        return Optional.empty();
    }

    public static DockingWaitResult tickDockingWait(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return DockingWaitResult.failed(context.failure().get());
        }

        context.station().setDockOutputActive(true);
        context.transponder().setDockOutputActive(true);

        BlockPos stationDock = context.stationDockPos().get();
        BlockPos shipDock = context.shipDockPos().get();
        if (DockingConnectorDiscovery.isLockedPair(level, stationDock, shipDock)) {
            return DockingWaitResult.docked();
        }
        if (isLockedToDifferentConnector(level, stationDock, shipDock)
                || isLockedToDifferentConnector(level, shipDock, stationDock)) {
            return DockingWaitResult.failed(PlaybackFailure.DOCK_LOCK_FAILED);
        }
        return DockingWaitResult.waiting();
    }

    public static Optional<DockTransferSnapshot> transferSnapshot(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockingContext context = context(level, station, route);
        if (context.failure().isPresent() || context.stationDockPos().isEmpty() || context.shipDockPos().isEmpty()) {
            return Optional.empty();
        }
        return DockingConnectorDiscovery.dockingConnector(level, context.stationDockPos().get())
                .flatMap(stationDock -> DockingConnectorDiscovery.dockingConnector(level, context.shipDockPos().get())
                        .map(shipDock -> DockTransferSnapshot.capture(stationDock, shipDock)));
    }

    public static void clearDockOutputs(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        station.setDockOutputActive(false);
        transponder(level, station, route).ifPresent(transponder -> transponder.setDockOutputActive(false));
    }

    private static DockingContext context(ServerLevel level, AirshipStationBlockEntity station, Route route) {
        DockDiscoveryResult stationDock = station.refreshGroundDockLink(level);
        Optional<ShipTransponderBlockEntity> transponder = transponder(level, station, route);
        if (transponder.isEmpty()) {
            return DockingContext.failed(PlaybackFailure.MISSING_CONTROLLER);
        }

        DockDiscoveryResult shipDock = transponder.get().refreshShipDockLink(level);
        Optional<PlaybackFailure> stationFailure = failureFor(stationDock.status());
        if (stationFailure.isPresent()) {
            return DockingContext.failed(stationFailure.get());
        }
        Optional<PlaybackFailure> shipFailure = failureFor(shipDock.status());
        if (shipFailure.isPresent()) {
            return DockingContext.failed(shipFailure.get());
        }
        if (stationDock.dockPos().isEmpty() || shipDock.dockPos().isEmpty()) {
            return DockingContext.failed(PlaybackFailure.MISSING_DOCK);
        }
        return new DockingContext(
                station,
                transponder.get(),
                stationDock.dockPos(),
                shipDock.dockPos(),
                Optional.empty()
        );
    }

    private static Optional<ShipTransponderBlockEntity> transponder(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        Optional<BlockPos> routeControllerPos = route.linkedController().controllerPos();
        if (routeControllerPos.isPresent()
                && level.getBlockEntity(routeControllerPos.get()) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }

        return station.selectedTransponderId()
                .flatMap(ShipTransponderRegistry::snapshot)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast);
    }

    private static Optional<PlaybackFailure> failureFor(DockLinkStatus status) {
        return switch (status) {
            case LINKED -> Optional.empty();
            case AMBIGUOUS -> Optional.of(PlaybackFailure.AMBIGUOUS_DOCK);
            case UNKNOWN, MISSING, INVALID -> Optional.of(PlaybackFailure.MISSING_DOCK);
        };
    }

    private static boolean isLockedToDifferentConnector(ServerLevel level, BlockPos dockPos, BlockPos expectedOther) {
        return DockingConnectorDiscovery.dockingConnector(level, dockPos)
                .filter(connector -> connector.isLocked())
                .map(connector -> connector.otherConnectorPosition)
                .filter(otherPos -> otherPos != null && !otherPos.equals(expectedOther))
                .isPresent();
    }

    public record DockingWaitResult(boolean locked, Optional<PlaybackFailure> failure) {
        public static DockingWaitResult waiting() {
            return new DockingWaitResult(false, Optional.empty());
        }

        public static DockingWaitResult docked() {
            return new DockingWaitResult(true, Optional.empty());
        }

        public static DockingWaitResult failed(PlaybackFailure failure) {
            return new DockingWaitResult(false, Optional.of(failure));
        }
    }

    private record DockingContext(
            AirshipStationBlockEntity station,
            ShipTransponderBlockEntity transponder,
            Optional<BlockPos> stationDockPos,
            Optional<BlockPos> shipDockPos,
            Optional<PlaybackFailure> failure
    ) {
        private static DockingContext failed(PlaybackFailure failure) {
            return new DockingContext(null, null, Optional.empty(), Optional.empty(), Optional.of(failure));
        }
    }
}

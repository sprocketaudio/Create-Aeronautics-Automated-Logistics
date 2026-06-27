package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public final class DockingRuntime {
    private static final String RESERVATIONS_TAG = "reservations";
    private static final String DIMENSION_TAG = "dimension";
    private static final String STATION_DOCK_TAG = "stationDock";
    private static final String HOLDER_TAG = "holder";
    private static final String WAITING_TAG = "waiting";
    private static final String ROUTE_ID_TAG = "routeId";
    private static final long LOCK_PENDING_DIAGNOSTIC_INTERVAL_TICKS = 40L;
    private static final Map<DockKey, DockReservation> RESERVATIONS = new HashMap<>();
    private static final Map<RouteId, Long> LOCK_PENDING_DIAGNOSTICS = new HashMap<>();

    private DockingRuntime() {
    }

    public static DockReservationResult requestReservation(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return DockReservationResult.failed(context.failure().get());
        }

        DockKey key = new DockKey(level.dimension(), context.stationDockPos().get().immutable());
        RouteId routeId = route.id();
        releaseReservationFromOtherDocks(routeId, key);
        DockReservation reservation = RESERVATIONS.computeIfAbsent(key, ignored -> new DockReservation());
        scrubInactiveRoutes(level.getServer(), key, reservation);
        DockReservationResult result = reservation.request(routeId);
        if (result.granted()) {
            if (result.changed()) {
                CreateAeronauticsAutomatedLogistics.debugDocking(
                        "Dock reservation granted: stationDock={} route={} queueSize={}",
                        key.stationDockPos().toShortString(),
                        routeId.value(),
                        reservation.waitingCount()
                );
            }
            return result;
        }

        if (result.changed()) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation queued: stationDock={} route={} holder={} queuePosition={}",
                    key.stationDockPos().toShortString(),
                    routeId.value(),
                    reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                    result.queuePosition()
            );
        }
        return result;
    }

    public static DockReservationResult requestApproachReservation(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockReservationContext context = reservationContext(level, station);
        if (context.failure().isPresent()) {
            return DockReservationResult.failed(context.failure().get());
        }

        DockKey key = new DockKey(level.dimension(), context.stationDockPos().immutable());
        RouteId routeId = route.id();
        releaseReservationFromOtherDocks(routeId, key);
        DockReservation reservation = RESERVATIONS.computeIfAbsent(key, ignored -> new DockReservation());
        scrubInactiveRoutes(level.getServer(), key, reservation);
        DockReservationResult result = reservation.request(routeId);
        if (result.granted()) {
            if (result.changed()) {
                CreateAeronauticsAutomatedLogistics.debugDocking(
                        "Dock approach reservation granted: stationDock={} route={} queueSize={}",
                        key.stationDockPos().toShortString(),
                        routeId.value(),
                        reservation.waitingCount()
                );
            }
            return result;
        }

        if (result.changed()) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock approach reservation queued: stationDock={} route={} holder={} queuePosition={}",
                    key.stationDockPos().toShortString(),
                    routeId.value(),
                    reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                    result.queuePosition()
            );
        }
        return result;
    }

    public static void releaseReservation(RouteId routeId) {
        Iterator<Map.Entry<DockKey, DockReservation>> iterator = RESERVATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DockKey, DockReservation> entry = iterator.next();
            DockReservation reservation = entry.getValue();
            boolean changed = reservation.release(routeId);
            if (!changed) {
                continue;
            }
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation released: stationDock={} route={} nextHolder={} queueSize={}",
                    entry.getKey().stationDockPos().toShortString(),
                    routeId.value(),
                    reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                    reservation.waitingCount()
            );
            if (reservation.empty()) {
                iterator.remove();
            }
        }
    }

    public static boolean releaseReservation(
            ResourceKey<Level> dimension,
            BlockPos stationDockPos,
            RouteId routeId
    ) {
        DockKey key = new DockKey(dimension, stationDockPos.immutable());
        DockReservation reservation = RESERVATIONS.get(key);
        if (reservation == null || !reservation.release(routeId)) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation targeted release refused: stationDock={} route={} reason=reservation_not_held_or_queued",
                    stationDockPos.toShortString(),
                    routeId.value()
            );
            return false;
        }
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation targeted release applied: stationDock={} route={} nextHolder={} queueSize={}",
                stationDockPos.toShortString(),
                routeId.value(),
                reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                reservation.waitingCount()
        );
        if (reservation.empty()) {
            RESERVATIONS.remove(key);
        }
        return true;
    }

    public static void resetRuntimeState(String reason) {
        int dockCount = RESERVATIONS.size();
        int heldCount = 0;
        int queuedCount = 0;
        for (DockReservation reservation : RESERVATIONS.values()) {
            if (reservation.holder().isPresent()) {
                heldCount++;
            }
            queuedCount += reservation.waitingCount();
        }
        RESERVATIONS.clear();
        if (dockCount > 0 || heldCount > 0 || queuedCount > 0) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation runtime reset: reason={} docks={} held={} queued={}",
                    reason,
                    dockCount,
                    heldCount,
                    queuedCount
            );
        }
    }

    public static CompoundTag saveRuntime() {
        CompoundTag tag = new CompoundTag();
        ListTag reservations = new ListTag();
        RESERVATIONS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((first, second) -> {
                    int dimension = first.dimension().location().toString()
                            .compareTo(second.dimension().location().toString());
                    if (dimension != 0) {
                        return dimension;
                    }
                    return Long.compare(first.stationDockPos().asLong(), second.stationDockPos().asLong());
                }))
                .forEach(entry -> {
                    CompoundTag reservationTag = new CompoundTag();
                    reservationTag.putString(DIMENSION_TAG, entry.getKey().dimension().location().toString());
                    reservationTag.put(STATION_DOCK_TAG, NbtUtils.writeBlockPos(entry.getKey().stationDockPos()));
                    entry.getValue().holder().ifPresent(holder -> reservationTag.putUUID(HOLDER_TAG, holder.value()));
                    ListTag waiting = new ListTag();
                    for (RouteId routeId : entry.getValue().waiting()) {
                        CompoundTag routeTag = new CompoundTag();
                        routeTag.putUUID(ROUTE_ID_TAG, routeId.value());
                        waiting.add(routeTag);
                    }
                    reservationTag.put(WAITING_TAG, waiting);
                    reservations.add(reservationTag);
                });
        tag.put(RESERVATIONS_TAG, reservations);
        return tag;
    }

    public static void loadRuntime(CompoundTag tag) {
        RESERVATIONS.clear();
        if (tag == null || !tag.contains(RESERVATIONS_TAG, Tag.TAG_LIST)) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation runtime restore found no saved reservation snapshot"
            );
            return;
        }

        int holderCount = 0;
        int queuedCount = 0;
        ListTag reservations = tag.getList(RESERVATIONS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < reservations.size(); i++) {
            CompoundTag reservationTag = reservations.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(reservationTag.getString(DIMENSION_TAG));
            Optional<BlockPos> stationDock = NbtUtils.readBlockPos(reservationTag, STATION_DOCK_TAG);
            if (dimensionId == null || stationDock.isEmpty()) {
                continue;
            }

            RouteId holder = reservationTag.hasUUID(HOLDER_TAG)
                    ? new RouteId(reservationTag.getUUID(HOLDER_TAG))
                    : null;
            ArrayDeque<RouteId> waiting = new ArrayDeque<>();
            if (reservationTag.contains(WAITING_TAG, Tag.TAG_LIST)) {
                ListTag waitingTags = reservationTag.getList(WAITING_TAG, Tag.TAG_COMPOUND);
                for (int waitingIndex = 0; waitingIndex < waitingTags.size(); waitingIndex++) {
                    CompoundTag routeTag = waitingTags.getCompound(waitingIndex);
                    if (!routeTag.hasUUID(ROUTE_ID_TAG)) {
                        continue;
                    }
                    RouteId routeId = new RouteId(routeTag.getUUID(ROUTE_ID_TAG));
                    if (!routeId.equals(holder) && !waiting.contains(routeId)) {
                        waiting.addLast(routeId);
                    }
                }
            }
            if (holder == null && waiting.isEmpty()) {
                continue;
            }

            DockKey key = new DockKey(
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    stationDock.get().immutable()
            );
            RESERVATIONS.put(key, new DockReservation(holder, waiting));
            if (holder != null) {
                holderCount++;
            }
            queuedCount += waiting.size();
        }
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation runtime restored: docks={} held={} queued={}",
                RESERVATIONS.size(),
                holderCount,
                queuedCount
        );
    }

    public static DockReservationStatus reservationStatus(RouteId routeId) {
        for (DockReservation reservation : RESERVATIONS.values()) {
            if (reservation.holder().filter(routeId::equals).isPresent()) {
                return DockReservationStatus.held();
            }
            int queuePosition = reservation.queuePositionIfPresent(routeId);
            if (queuePosition > 0) {
                return DockReservationStatus.queued(queuePosition);
            }
        }
        return DockReservationStatus.untracked();
    }

    public static DockReservationTransferResult transferReservation(RouteId fromRouteId, RouteId toRouteId) {
        if (fromRouteId.equals(toRouteId)) {
            return DockReservationTransferResult.transferred(Optional.empty());
        }
        Iterator<Map.Entry<DockKey, DockReservation>> iterator = RESERVATIONS.entrySet().iterator();
        Optional<BlockPos> transferredDock = Optional.empty();
        while (iterator.hasNext()) {
            Map.Entry<DockKey, DockReservation> entry = iterator.next();
            DockReservation reservation = entry.getValue();
            if (reservation.holder().filter(fromRouteId::equals).isPresent()) {
                reservation.transferHeldIdentity(toRouteId);
                transferredDock = Optional.of(entry.getKey().stationDockPos().immutable());
                CreateAeronauticsAutomatedLogistics.debugDocking(
                        "Dock reservation transferred: stationDock={} fromRoute={} toRoute={} holder={} queueSize={} reason=held_route_handoff",
                        entry.getKey().stationDockPos().toShortString(),
                        fromRouteId.value(),
                        toRouteId.value(),
                        reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                        reservation.waitingCount()
                );
            } else if (reservation.release(fromRouteId)) {
                CreateAeronauticsAutomatedLogistics.debugDocking(
                        "Dock reservation handoff refused queued/non-holder route: stationDock={} fromRoute={} toRoute={} holder={} queueSize={} action=removed_stale_queue_entry",
                        entry.getKey().stationDockPos().toShortString(),
                        fromRouteId.value(),
                        toRouteId.value(),
                        reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                        reservation.waitingCount()
                );
            } else {
                continue;
            }
            if (reservation.empty()) {
                iterator.remove();
            }
        }
        return transferredDock.isPresent()
                ? DockReservationTransferResult.transferred(transferredDock)
                : DockReservationTransferResult.notTransferred();
    }

    public static Optional<PlaybackFailure> beginDockingWait(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockReservationResult reservation = requestReservation(level, station, route);
        if (reservation.failure().isPresent()) {
            return reservation.failure();
        }
        if (!reservation.granted()) {
            return Optional.empty();
        }

        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return context.failure();
        }
        ensureDockOutputsActive(level, context.station(), route);
        CreateAeronauticsAutomatedLogistics.debugDocking(
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
        DockReservationResult reservation = requestReservation(level, station, route);
        if (reservation.failure().isPresent()) {
            return DockingWaitResult.failed(reservation.failure().get());
        }
        if (!reservation.granted()) {
            return DockingWaitResult.waiting();
        }

        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return DockingWaitResult.failed(context.failure().get());
        }

        ensureDockOutputsActive(level, context.station(), route);

        BlockPos stationDock = context.stationDockPos().get();
        BlockPos shipDock = context.shipDockPos().get();
        if (DockingConnectorDiscovery.isLockedPair(level, stationDock, shipDock)) {
            return DockingWaitResult.docked();
        }
        if (isLockedToDifferentConnector(level, stationDock, shipDock)
                || isLockedToDifferentConnector(level, shipDock, stationDock)) {
            return DockingWaitResult.failed(PlaybackFailure.DOCK_LOCK_FAILED);
        }
        logDockLockPending(level, context, route, stationDock, shipDock);
        return DockingWaitResult.waiting();
    }

    public static String lockDiagnostic(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return "contextFailure=" + context.failure().get();
        }
        return DockingConnectorDiscovery.lockDiagnostic(
                level,
                context.stationDockPos().get(),
                context.shipDockPos().get()
        );
    }

    public static Optional<PlaybackFailure> resetDockingPair(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        DockingContext context = context(level, station, route);
        if (context.failure().isPresent()) {
            return context.failure();
        }
        BlockPos stationDock = context.stationDockPos().get();
        BlockPos shipDock = context.shipDockPos().get();
        if (DockingConnectorDiscovery.dockingConnector(level, stationDock).isEmpty()
                || DockingConnectorDiscovery.dockingConnector(level, shipDock).isEmpty()) {
            return Optional.of(PlaybackFailure.MISSING_DOCK);
        }

        DockingConnectorDiscovery.dockingConnector(level, stationDock).ifPresent(connector -> connector.unDock());
        DockingConnectorDiscovery.dockingConnector(level, shipDock).ifPresent(connector -> connector.unDock());
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Docking wait reset connector pair: stationDock={} shipDock={} route={}",
                stationDock.toShortString(),
                shipDock.toShortString(),
                route.id().value()
        );
        return Optional.empty();
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
        boolean stationCleared = station.releaseDockOutput(route.id());
        Optional<ShipTransponderBlockEntity> routeTransponder = transponder(level, station, route);
        boolean transponderCleared = routeTransponder
                .map(transponder -> transponder.releaseDockOutput(route.id()))
                .orElse(true);
        if (!stationCleared || routeTransponder.isPresent() && !transponderCleared) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock output clear skipped stale owner: route={} stationId={} stationCleared={} transponderCleared={}",
                    route.id().value(),
                    station.stationId(),
                    stationCleared,
                    transponderCleared
            );
        }
    }

    public static void ensureDockOutputsActive(
            ServerLevel level,
            AirshipStationBlockEntity station,
            Route route
    ) {
        boolean stationWasActive = station.dockOutputActive();
        Optional<RouteId> previousStationOwner = station.dockOutputOwner();
        station.claimDockOutput(route.id());
        Optional<ShipTransponderBlockEntity> transponder = transponder(level, station, route);
        boolean shipWasActive = transponder.map(ShipTransponderBlockEntity::dockOutputActive).orElse(false);
        Optional<RouteId> previousShipOwner = transponder.flatMap(ShipTransponderBlockEntity::dockOutputOwner);
        transponder.ifPresent(value -> value.claimDockOutput(route.id()));
        if (!stationWasActive
                || previousStationOwner.filter(route.id()::equals).isEmpty()
                || transponder.isPresent() && (!shipWasActive || previousShipOwner.filter(route.id()::equals).isEmpty())) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Docking wait reasserted outputs: stationDock={} shipDock={} route={} stationWasActive={} shipWasActive={} previousStationOwner={} previousShipOwner={}",
                    station.groundDockPos().map(BlockPos::toShortString).orElse("-"),
                    transponder.flatMap(ShipTransponderBlockEntity::shipDockPos).map(BlockPos::toShortString).orElse("-"),
                    route.id().value(),
                    stationWasActive,
                    shipWasActive,
                    previousStationOwner.map(RouteId::value).map(Object::toString).orElse("none"),
                    previousShipOwner.map(RouteId::value).map(Object::toString).orElse("none")
            );
        }
    }

    private static void releaseReservationFromOtherDocks(RouteId routeId, DockKey currentKey) {
        Iterator<Map.Entry<DockKey, DockReservation>> iterator = RESERVATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DockKey, DockReservation> entry = iterator.next();
            if (entry.getKey().equals(currentKey)) {
                continue;
            }
            DockReservation reservation = entry.getValue();
            if (!reservation.release(routeId)) {
                continue;
            }
            if (reservation.empty()) {
                iterator.remove();
            }
        }
    }

    private static void scrubInactiveRoutes(
            MinecraftServer server,
            DockKey key,
            DockReservation reservation
    ) {
        Predicate<RouteId> inactiveRoute = routeId -> !AutomatedLogisticsServices.PLAYBACK.isRunning(routeId)
                && !AutomatedLogisticsServices.PLAYBACK.isPending(routeId)
                && !AutomatedLogisticsServices.PLAYBACK.isHeld(routeId);
        if (!reservation.scrubInactive(inactiveRoute)) {
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation scrubbed inactive holder/queue entries: stationDock={} holder={} queueSize={}",
                key.stationDockPos().toShortString(),
                reservation.holder().map(RouteId::value).map(Object::toString).orElse("none"),
                reservation.waitingCount()
        );
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

    private static DockReservationContext reservationContext(ServerLevel level, AirshipStationBlockEntity station) {
        DockDiscoveryResult stationDock = station.refreshGroundDockLink(level);
        Optional<PlaybackFailure> stationFailure = failureFor(stationDock.status());
        if (stationFailure.isPresent()) {
            return DockReservationContext.failed(stationFailure.get());
        }
        if (stationDock.dockPos().isEmpty()) {
            return DockReservationContext.failed(PlaybackFailure.MISSING_DOCK);
        }
        return DockReservationContext.ready(stationDock.dockPos().get());
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

        for (ShipTransponderSnapshot snapshot : ShipTransponderRegistry.knownShips(level.dimension())) {
            if (snapshot.controllerRef().filter(route.linkedController()::matches).isEmpty()) {
                continue;
            }
            if (level.getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
                return Optional.of(transponder);
            }
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

    private static void logDockLockPending(
            ServerLevel level,
            DockingContext context,
            Route route,
            BlockPos stationDock,
            BlockPos shipDock
    ) {
        long gameTime = level.getGameTime();
        Long previous = LOCK_PENDING_DIAGNOSTICS.get(route.id());
        if (previous != null && gameTime - previous < LOCK_PENDING_DIAGNOSTIC_INTERVAL_TICKS) {
            return;
        }
        LOCK_PENDING_DIAGNOSTICS.put(route.id(), gameTime);
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock lock pending: route={} station={} transponder={} stationOutput={} stationOwner={} shipOutput={} shipOwner={} stationDock={} shipDock={} stationLoaded={} shipLoaded={} diagnostic={}",
                route.id().value(),
                context.station().stationId(),
                context.transponder().transponderId(),
                context.station().dockOutputActive(),
                context.station().dockOutputOwner().map(RouteId::value).map(Object::toString).orElse("none"),
                context.transponder().dockOutputActive(),
                context.transponder().dockOutputOwner().map(RouteId::value).map(Object::toString).orElse("none"),
                stationDock.toShortString(),
                shipDock.toShortString(),
                level.isLoaded(stationDock),
                level.isLoaded(shipDock),
                DockingConnectorDiscovery.lockDiagnostic(level, stationDock, shipDock)
        );
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

    private record DockReservationContext(BlockPos stationDockPos, Optional<PlaybackFailure> failure) {
        private static DockReservationContext ready(BlockPos stationDockPos) {
            return new DockReservationContext(stationDockPos, Optional.empty());
        }

        private static DockReservationContext failed(PlaybackFailure failure) {
            return new DockReservationContext(null, Optional.of(failure));
        }
    }

    public record DockReservationResult(
            boolean granted,
            int queuePosition,
            Optional<PlaybackFailure> failure,
            boolean changed
    ) {
        private static DockReservationResult granted(boolean changed) {
            return new DockReservationResult(true, 0, Optional.empty(), changed);
        }

        private static DockReservationResult queued(int queuePosition, boolean changed) {
            return new DockReservationResult(false, queuePosition, Optional.empty(), changed);
        }

        private static DockReservationResult failed(PlaybackFailure failure) {
            return new DockReservationResult(false, -1, Optional.of(failure), false);
        }
    }

    public record DockReservationTransferResult(boolean transferred, Optional<BlockPos> stationDockPos) {
        private static DockReservationTransferResult transferred(Optional<BlockPos> stationDockPos) {
            return new DockReservationTransferResult(true, stationDockPos);
        }

        private static DockReservationTransferResult notTransferred() {
            return new DockReservationTransferResult(false, Optional.empty());
        }
    }

    public record DockReservationStatus(boolean tracked, boolean granted, int queuePosition) {
        private static DockReservationStatus untracked() {
            return new DockReservationStatus(false, false, -1);
        }

        private static DockReservationStatus held() {
            return new DockReservationStatus(true, true, 0);
        }

        private static DockReservationStatus queued(int queuePosition) {
            return new DockReservationStatus(true, false, queuePosition);
        }
    }

    private record DockKey(ResourceKey<Level> dimension, BlockPos stationDockPos) {
    }

    private static final class DockReservation {
        private RouteId holder;
        private final ArrayDeque<RouteId> waiting = new ArrayDeque<>();

        private DockReservation() {
        }

        private DockReservation(RouteId holder, ArrayDeque<RouteId> waiting) {
            this.holder = holder;
            this.waiting.addAll(waiting);
        }

        private DockReservationResult request(RouteId routeId) {
            if (routeId.equals(holder)) {
                return DockReservationResult.granted(false);
            }
            if (holder == null && (waiting.isEmpty() || routeId.equals(waiting.peekFirst()))) {
                waiting.remove(routeId);
                holder = routeId;
                return DockReservationResult.granted(true);
            }
            boolean added = false;
            if (!waiting.contains(routeId)) {
                waiting.addLast(routeId);
                added = true;
            }
            return DockReservationResult.queued(queuePosition(routeId), added);
        }

        private boolean release(RouteId routeId) {
            boolean changed = false;
            if (routeId.equals(holder)) {
                holder = waiting.pollFirst();
                changed = true;
            }
            if (waiting.remove(routeId)) {
                changed = true;
            }
            return changed;
        }

        private void transferHeldIdentity(RouteId toRouteId) {
            holder = toRouteId;
            waiting.remove(toRouteId);
        }

        private boolean scrubInactive(Predicate<RouteId> inactiveRoute) {
            boolean changed = false;
            if (holder != null && inactiveRoute.test(holder)) {
                holder = null;
                changed = true;
            }
            if (waiting.removeIf(inactiveRoute)) {
                changed = true;
            }
            if (holder == null && !waiting.isEmpty()) {
                holder = waiting.pollFirst();
                changed = true;
            }
            return changed;
        }

        private Optional<RouteId> holder() {
            return Optional.ofNullable(holder);
        }

        private int waitingCount() {
            return waiting.size();
        }

        private List<RouteId> waiting() {
            return List.copyOf(waiting);
        }

        private boolean empty() {
            return holder == null && waiting.isEmpty();
        }

        private int queuePosition(RouteId routeId) {
            int position = 1;
            for (RouteId queued : waiting) {
                if (queued.equals(routeId)) {
                    return position;
                }
                position++;
            }
            return position;
        }

        private int queuePositionIfPresent(RouteId routeId) {
            return waiting.contains(routeId) ? queuePosition(routeId) : -1;
        }
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AirshipStationBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.StationCargoSavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockDiscoveryResult;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingConnectorDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingSession;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ScheduleRouteCleanup;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationChunkLoadingService;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class AirshipStationBlockEntity extends BlockEntity implements MenuProvider {
    private static final int REFRESH_INTERVAL_TICKS = 40;
    private static final String DATA_VERSION = "dataVersion";
    private static final String STATUS = "status";
    private static final String STATION_ID = "stationId";
    private static final String STATION_NAME = "stationName";
    private static final String OWNER_ID = "ownerId";
    private static final String OWNER_NAME = "ownerName";
    private static final String SELECTED_TRANSPONDER_ID = "selectedTransponderId";
    private static final String SELECTED_SHIP_NAME = "selectedShipName";
    private static final String FAILURE_REASON = "failureReason";
    private static final String RECORDED_ROUTE = "recordedRoute";
    private static final String ROUTE_SEGMENTS = "routeSegments";
    private static final String RECORDING_STOPS = "recordingStops";
    private static final String LINKED_CONTROLLERS = "linkedControllers";
    private static final String GROUND_DOCK_POS = "groundDockPos";
    private static final String GROUND_DOCK_STATUS = "groundDockStatus";
    private static final String DOCK_OUTPUT_ACTIVE = "dockOutputActive";
    private static final String LINKED_CARGO = "linkedCargo";
    private static final String LINKED_CARGO_TOTAL = "linkedCargoTotal";
    private static final String LINKED_CARGO_VALID = "linkedCargoValid";
    private static final String LINKED_CARGO_STALE = "linkedCargoStale";
    private static final String LINKED_CARGO_ITEM_COUNT = "linkedCargoItemCount";
    private static final String LINKED_CARGO_FLUID_COUNT = "linkedCargoFluidCount";
    private static final String LINKED_CARGO_REVISION = "linkedCargoRevision";
    private static final String LIVE_SYNC = "liveSync";
    private static final String DIMENSION = "dimension";
    private static final String CONTROLLER_TYPE = "controllerType";
    private static final String CONTROLLER_X = "controllerX";
    private static final String CONTROLLER_Y = "controllerY";
    private static final String CONTROLLER_Z = "controllerZ";
    private static final String VEHICLE_ID = "vehicleId";
    private static final int CURRENT_DATA_VERSION = 1;

    private RouteStatus status = RouteStatus.IDLE;
    private UUID stationId = UUID.randomUUID();
    private String stationName = "";
    private Optional<UUID> ownerId = Optional.empty();
    private String ownerName = "";
    private Optional<UUID> selectedTransponderId = Optional.empty();
    private String selectedShipName = "";
    private Optional<FailureReason> failureReason = Optional.empty();
    private Optional<RecordingSession> activeRecording = Optional.empty();
    private Optional<Route> recordedRoute = Optional.empty();
    private Optional<BlockPos> groundDockPos = Optional.empty();
    private DockLinkStatus groundDockStatus = DockLinkStatus.UNKNOWN;
    private boolean dockOutputActive;
    private final List<RouteSegment> routeSegments = new ArrayList<>();
    private final List<VehicleControllerRef> linkedControllers = new ArrayList<>();
    private final List<RoutePoint> recordingPoints = new ArrayList<>();
    private final List<RouteStop> recordingStops = new ArrayList<>();
    private final List<LinkedCargoEntry> linkedCargo = new ArrayList<>();
    private @Nullable LinkedCargoSummary syncedLinkedCargoSummary;
    private int linkedCargoRevision;
    private boolean linkedCargoRestoreMissLogged;

    public AirshipStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.AIRSHIP_STATION.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirshipStationBlockEntity station) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % REFRESH_INTERVAL_TICKS == 0L) {
            station.refreshGroundDockLink(serverLevel);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station onLoad id={} pos={} linkedCargoCount={} levelClient={}",
                stationId,
                worldPosition,
                linkedCargo.size(),
                level != null && level.isClientSide
        );
        if (level != null && !level.isClientSide && dockOutputActive) {
            dockOutputActive = false;
            setChanged();
        }
        syncPoweredBlockState();
        registerLoadedSnapshot();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(stationName());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        registerStationSnapshot();
        if (level instanceof ServerLevel serverLevel) {
            refreshGroundDockLink(serverLevel);
            selectedTransponderId.ifPresent(transponderId ->
                    AutomatedLogisticsServices.SCHEDULES.reconcileRuntimeStatus(serverLevel, transponderId));
            if (player instanceof ServerPlayer serverPlayer) {
                AirshipStationMenu menu = new AirshipStationMenu(
                        containerId,
                        playerInventory,
                        worldPosition,
                        selectedTransponderId(),
                        selectedShipName(),
                        linkedCargoRevision(),
                        linkedCargoSummary(),
                        linkedCargo(),
                        selectedTransponderId().flatMap(AutomatedLogisticsServices.SCHEDULES::lastCargoFailureContext),
                        AirshipStationMenu.buildRouteChoiceSummaries(serverPlayer, this)
                );
                menu.setClientState(AirshipStationMenu.buildClientState(serverPlayer, this));
                return menu;
            }
        }
        return new AirshipStationMenu(
                containerId,
                playerInventory,
                worldPosition,
                selectedTransponderId(),
                selectedShipName(),
                linkedCargoRevision(),
                linkedCargoSummary(),
                linkedCargo(),
                Optional.empty(),
                List.of()
        );
    }

    public RouteStatus status() {
        return status;
    }

    public UUID stationId() {
        return stationId;
    }

    public String stationName() {
        if (stationName == null || stationName.isBlank()) {
            return IdentityNames.defaultStationName(stationId);
        }
        return stationName;
    }

    public void setStationName(String stationName) {
        String sanitized = IdentityNames.sanitize(stationName);
        this.stationName = sanitized.isBlank() ? IdentityNames.defaultStationName(stationId) : sanitized;
        setChanged();
        syncClientState();
    }

    public Optional<UUID> ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(net.minecraft.server.level.ServerPlayer player) {
        setOwner(player.getUUID(), player.getGameProfile().getName());
    }

    public void setOwner(UUID ownerId, String ownerName) {
        Optional<UUID> normalizedId = Optional.of(ownerId);
        String normalizedName = IdentityNames.sanitize(ownerName);
        if (this.ownerId.equals(normalizedId) && this.ownerName.equals(normalizedName)) {
            return;
        }
        this.ownerId = normalizedId;
        this.ownerName = normalizedName;
        setChanged();
    }

    public Optional<FailureReason> failureReason() {
        return failureReason;
    }

    public Optional<RecordingSession> activeRecording() {
        return activeRecording;
    }

    public Optional<Route> recordedRoute() {
        return recordedRoute;
    }

    public Optional<UUID> selectedTransponderId() {
        return selectedTransponderId;
    }

    public String selectedShipName() {
        if (selectedShipName == null || selectedShipName.isBlank()) {
            return selectedTransponderId.map(IdentityNames::defaultShipName).orElse("");
        }
        return selectedShipName;
    }

    public void selectShip(ShipTransponderSnapshot snapshot) {
        selectedTransponderId = Optional.of(snapshot.transponderId());
        selectedShipName = snapshot.shipName();
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void clearSelectedShip() {
        if (selectedTransponderId.isEmpty() && (selectedShipName == null || selectedShipName.isBlank())) {
            return;
        }
        selectedTransponderId = Optional.empty();
        selectedShipName = "";
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public Optional<BlockPos> groundDockPos() {
        return groundDockPos;
    }

    public DockLinkStatus groundDockStatus() {
        if (groundDockStatus == DockLinkStatus.LINKED && groundDockPos.isEmpty()) {
            return DockLinkStatus.UNKNOWN;
        }
        return groundDockStatus;
    }

    public boolean dockOutputActive() {
        return dockOutputActive;
    }

    public List<LinkedCargoEntry> linkedCargo() {
        return List.copyOf(resolvedLinkedCargo());
    }

    public LinkedCargoSummary linkedCargoSummary() {
        if (level != null && level.isClientSide && syncedLinkedCargoSummary != null) {
            return syncedLinkedCargoSummary;
        }
        return resolveLinkedCargoSummary(true);
    }

    public boolean hasSyncedLinkedCargoSummary() {
        return syncedLinkedCargoSummary != null;
    }

    public int linkedCargoRevision() {
        return linkedCargoRevision;
    }

    public LinkedCargoSummary relinkCargo(ServerLevel level) {
        linkedCargo.clear();
        linkedCargo.addAll(CargoLinkDiscovery.discover(level, worldPosition));
        linkedCargoRevision++;
        setChanged();
        persistLinkedCargo(level);
        syncClientState();
        return linkedCargoSummary();
    }

    public int addLinkedCargoEntries(List<LinkedCargoEntry> entries) {
        ensureLinkedCargoLoaded();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station addLinkedCargoEntries start id={} pos={} existingCount={} incomingCount={}",
                stationId,
                worldPosition,
                linkedCargo.size(),
                entries.size()
        );
        int added = 0;
        for (LinkedCargoEntry entry : entries) {
            boolean exists = linkedCargo.stream().anyMatch(existing -> existing.pos().equals(entry.pos()));
            if (exists) {
                continue;
            }
            linkedCargo.add(entry);
            added++;
        }
        if (added > 0) {
            linkedCargoRevision++;
            setChanged();
            persistLinkedCargo();
            syncClientState();
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station addLinkedCargoEntries saved id={} pos={} added={} newCount={}",
                    stationId,
                    worldPosition,
                    added,
                    linkedCargo.size()
            );
        }
        return added;
    }

    public boolean clearLinkedCargo() {
        ensureLinkedCargoLoaded();
        if (linkedCargo.isEmpty()) {
            persistLinkedCargo();
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station clearLinkedCargo no-op id={} pos={} count=0",
                    stationId,
                    worldPosition
            );
            return false;
        }
        linkedCargo.clear();
        linkedCargoRevision++;
        setChanged();
        persistLinkedCargo();
        syncClientState();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station clearLinkedCargo cleared id={} pos={}",
                stationId,
                worldPosition
        );
        return true;
    }

    public void setDockOutputActive(boolean active) {
        if (dockOutputActive == active) {
            return;
        }
        dockOutputActive = active;
        setChanged();
        syncPoweredBlockState();
        notifyRedstoneNeighbors();
        syncClientState();
    }

    public DockDiscoveryResult refreshGroundDockLink(ServerLevel level) {
        DockDiscoveryResult result = validateGroundDockLink(level);
        groundDockStatus = result.status() == DockLinkStatus.LINKED && result.dockPos().isEmpty()
                ? DockLinkStatus.UNKNOWN
                : result.status();
        if (result.status() == DockLinkStatus.LINKED) {
            groundDockPos = result.dockPos();
        } else {
            groundDockPos = Optional.empty();
        }
        setChanged();
        syncClientState();
        return result;
    }

    public DockDiscoveryResult setGroundDockLink(ServerLevel level, BlockPos dockPos) {
        if (!DockingConnectorDiscovery.isDock(level, dockPos)) {
            groundDockPos = Optional.empty();
            groundDockStatus = DockLinkStatus.INVALID;
            setChanged();
            syncClientState();
            return DockDiscoveryResult.invalid();
        }
        double maxDistance = AutomatedLogisticsConfig.STATION_DOCK_SEARCH_RADIUS.get();
        double maxDistanceSqr = maxDistance * maxDistance;
        if (worldPosition.distSqr(dockPos) > maxDistanceSqr) {
            groundDockPos = Optional.empty();
            groundDockStatus = DockLinkStatus.INVALID;
            setChanged();
            syncClientState();
            return DockDiscoveryResult.invalid();
        }
        groundDockPos = Optional.of(dockPos.immutable());
        groundDockStatus = DockLinkStatus.LINKED;
        setChanged();
        syncClientState();
        return DockDiscoveryResult.linked(dockPos.immutable());
    }

    public void clearGroundDockLink() {
        groundDockPos = Optional.empty();
        groundDockStatus = DockLinkStatus.MISSING;
        setChanged();
        syncClientState();
    }

    private DockDiscoveryResult validateGroundDockLink(ServerLevel level) {
        if (groundDockPos.isEmpty()) {
            return DockDiscoveryResult.missing();
        }
        BlockPos dockPos = groundDockPos.get();
        if (!DockingConnectorDiscovery.isDock(level, dockPos)) {
            return DockDiscoveryResult.missing();
        }
        double maxDistance = AutomatedLogisticsConfig.STATION_DOCK_SEARCH_RADIUS.get();
        double maxDistanceSqr = maxDistance * maxDistance;
        if (worldPosition.distSqr(dockPos) > maxDistanceSqr) {
            return DockDiscoveryResult.invalid();
        }
        return DockDiscoveryResult.linked(dockPos);
    }

    public List<RouteSegment> routeSegments() {
        return List.copyOf(routeSegments);
    }

    public void addRouteSegment(RouteSegment segment) {
        routeSegments.add(segment);
        pruneSegmentHistory(segment, 5);
        syncRoutePersistence();
        setChanged();
        syncClientState();
    }

    public boolean removeRouteSegment(UUID segmentId) {
        boolean removed = routeSegments.removeIf(segment -> segment.id().value().equals(segmentId));
        if (removed) {
            syncRoutePersistence();
            setChanged();
            syncClientState();
        }
        return removed;
    }

    public List<VehicleControllerRef> linkedControllers() {
        return List.copyOf(linkedControllers);
    }

    public Optional<VehicleControllerRef> primaryLinkedController() {
        return linkedControllers.stream().findFirst();
    }

    public List<RoutePoint> recordingPoints() {
        return List.copyOf(recordingPoints);
    }

    public List<RouteStop> recordingStops() {
        return List.copyOf(recordingStops);
    }

    public boolean isRecording() {
        return activeRecording.isPresent();
    }

    public Optional<RouteId> activeRouteId() {
        return activeRecording.map(RecordingSession::routeId);
    }

    public boolean isPlaybackRunning() {
        return status == RouteStatus.RUNNING
                || status == RouteStatus.WAITING
                || status == RouteStatus.HELD
                || status == RouteStatus.HELD_FAULTED;
    }

    public void linkController(VehicleControllerRef controllerRef) {
        linkedControllers.clear();
        linkedControllers.add(controllerRef);
        failureReason = Optional.empty();
        if (status == RouteStatus.MISSING_VEHICLE || status == RouteStatus.INVALID_ROUTE) {
            status = recordedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
        }
        setChanged();
        syncClientState();
    }

    public void startRecording(RecordingSession session) {
        activeRecording = Optional.of(session);
        recordedRoute = Optional.empty();
        recordingPoints.clear();
        recordingStops.clear();
        status = RouteStatus.RECORDING;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void addRecordedPoint(RoutePoint point) {
        recordingPoints.add(point);
        setChanged();
    }

    public void addRecordingStop(RouteStop stop) {
        recordingStops.add(stop);
        setChanged();
    }

    public void replaceLastRecordingStop(RouteStop stop) {
        if (recordingStops.isEmpty()) {
            return;
        }
        recordingStops.set(recordingStops.size() - 1, stop);
        setChanged();
    }

    public void replaceLastRouteStop(RouteStop stop) {
        recordedRoute = recordedRoute.map(route -> {
            if (route.stops().isEmpty()) {
                return route;
            }
            List<RouteStop> stops = new ArrayList<>(route.stops());
            stops.set(stops.size() - 1, stop);
            return route.withStops(stops);
        });
        setChanged();
        syncClientState();
    }

    public void finishRecording(Route route) {
        recordedRoute = Optional.of(route);
        activeRecording = Optional.empty();
        recordingPoints.clear();
        recordingStops.clear();
        status = RouteStatus.RECORDED;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void cancelRecording() {
        activeRecording = Optional.empty();
        recordingPoints.clear();
        recordingStops.clear();
        status = recordedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void startPlayback(Route route) {
        recordedRoute = Optional.of(route.withStatus(RouteStatus.RUNNING));
        status = RouteStatus.RUNNING;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void waitPlayback(Route route) {
        recordedRoute = Optional.of(route.withStatus(RouteStatus.WAITING));
        status = RouteStatus.WAITING;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void holdPlayback(Route route, boolean faulted, Optional<FailureReason> reason) {
        recordedRoute = Optional.of(route.withStatus(faulted ? RouteStatus.HELD_FAULTED : RouteStatus.HELD));
        status = faulted ? RouteStatus.HELD_FAULTED : RouteStatus.HELD;
        failureReason = reason;
        setChanged();
        syncClientState();
    }

    public void resumePlayback() {
        recordedRoute = recordedRoute.map(route -> route.withStatus(RouteStatus.RUNNING));
        status = RouteStatus.RUNNING;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void stopPlayback() {
        recordedRoute = recordedRoute.map(route -> route.withStatus(RouteStatus.RECORDED));
        status = recordedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
        failureReason = Optional.empty();
        setChanged();
        syncClientState();
    }

    public void failPlayback(FailureReason reason) {
        recordedRoute = recordedRoute.map(route -> route.withStatus(RouteStatus.FAILED));
        status = statusForFailure(reason);
        failureReason = Optional.of(reason);
        setChanged();
        syncClientState();
    }

    public void setFailure(FailureReason reason) {
        status = statusForFailure(reason);
        failureReason = Optional.of(reason);
        setChanged();
        syncClientState();
    }

    public void failRecording(FailureReason reason) {
        recordedRoute = Optional.empty();
        activeRecording = Optional.empty();
        recordingPoints.clear();
        recordingStops.clear();
        status = statusForFailure(reason);
        failureReason = Optional.of(reason);
        setChanged();
        syncClientState();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            registerStationSnapshot();
            syncRoutePersistence();
        }
    }

    public void syncClientState() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void registerStationSnapshot() {
        if (level == null || level.isClientSide) {
            return;
        }
        AirshipStationSnapshot snapshot = new AirshipStationSnapshot(
                stationId,
                stationName(),
                level.dimension(),
                worldPosition,
                ownerId,
                ownerName
        );
        AirshipStationRegistry.register(snapshot);
        IdentityDirectorySavedData.upsertStation(((ServerLevel) level).getServer(), snapshot);
        StationChunkLoadingService.track((ServerLevel) level, stationId, worldPosition);
    }

    private RouteStatus statusForFailure(FailureReason reason) {
        return switch (reason) {
            case COLLISION_OR_OBSTRUCTION -> RouteStatus.BLOCKED;
            case VEHICLE_DESTROYED_OR_MISSING, VEHICLE_UNLOADED -> RouteStatus.MISSING_VEHICLE;
            case START_TOO_FAR_FROM_ROUTE -> RouteStatus.FAILED;
            case MISSING_AUTOPILOT_CONTROLLER, MISSING_STATION, INVALID_ROUTE_DATA, DIMENSION_MISMATCH,
                    MISSING_DOCK, AMBIGUOUS_DOCK -> RouteStatus.INVALID_ROUTE;
            case NONE -> recordedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
            case DOCK_LOCK_FAILED, REDSTONE_LINK_UNCONFIGURED, CARGO_STORAGE_MISSING, CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE -> RouteStatus.FAILED;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeStationData(tag, durableStatus(), true, false);
    }

    private void writeStationData(CompoundTag tag, RouteStatus savedStatus, boolean includeFailure, boolean liveCargoSummary) {
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putUUID(STATION_ID, stationId);
        tag.putString(STATION_NAME, stationName());
        ownerId.ifPresent(id -> tag.putUUID(OWNER_ID, id));
        tag.putString(OWNER_NAME, ownerName);
        selectedTransponderId.ifPresent(id -> tag.putUUID(SELECTED_TRANSPONDER_ID, id));
        groundDockPos.ifPresent(pos -> tag.put(GROUND_DOCK_POS, NbtUtils.writeBlockPos(pos)));
        DockLinkStatus savedDockStatus = groundDockStatus == DockLinkStatus.LINKED && groundDockPos.isEmpty()
                ? DockLinkStatus.UNKNOWN
                : groundDockStatus;
        tag.putString(GROUND_DOCK_STATUS, savedDockStatus.name());
        tag.putBoolean(DOCK_OUTPUT_ACTIVE, dockOutputActive);
        if (!linkedCargo.isEmpty()) {
            ListTag linkedCargoTag = new ListTag();
            for (LinkedCargoEntry entry : linkedCargo) {
                linkedCargoTag.add(entry.write());
            }
            tag.put(LINKED_CARGO, linkedCargoTag);
        }
        LinkedCargoSummary summary = resolveLinkedCargoSummary(liveCargoSummary);
        tag.putInt(LINKED_CARGO_TOTAL, summary.totalLinks());
        tag.putInt(LINKED_CARGO_VALID, summary.validLinks());
        tag.putInt(LINKED_CARGO_STALE, summary.staleLinks());
        tag.putInt(LINKED_CARGO_ITEM_COUNT, summary.itemLinks());
        tag.putInt(LINKED_CARGO_FLUID_COUNT, summary.fluidLinks());
        tag.putInt(LINKED_CARGO_REVISION, linkedCargoRevision);
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station writeStationData id={} pos={} linkedCargoCount={} routeSegmentCount={} includeFailure={} liveSync={}",
                stationId,
                worldPosition,
                linkedCargo.size(),
                routeSegments.size(),
                includeFailure,
                tag.getBoolean(LIVE_SYNC)
        );
        if (!selectedShipName().isBlank()) {
            tag.putString(SELECTED_SHIP_NAME, selectedShipName());
        }
        tag.putString(STATUS, savedStatus.name());
        if (includeFailure) {
            failureReason.ifPresent(reason -> tag.putString(FAILURE_REASON, reason.name()));
        }
        tag.put(LINKED_CONTROLLERS, writeLinkedControllers());
        if (!tag.getBoolean(LIVE_SYNC) && !routeSegments.isEmpty()) {
            tag.put(ROUTE_SEGMENTS, RouteSegmentNbtSerializer.writeSegments(routeSegments));
        }
        recordedRoute.ifPresent(route -> tag.put(RECORDED_ROUTE, RouteNbtSerializer.write(route.withStatus(savedStatus))));
        if (!recordingStops.isEmpty()) {
            tag.put(RECORDING_STOPS, RouteNbtSerializer.writeStops(recordingStops));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(LIVE_SYNC, true);
        writeStationData(tag, status, status != RouteStatus.RECORDING, true);
        return tag;
    }

    private LinkedCargoSummary resolveLinkedCargoSummary(boolean liveAllowed) {
        if (level != null && level.isClientSide && syncedLinkedCargoSummary != null) {
            return syncedLinkedCargoSummary;
        }
        if (!liveAllowed) {
            return syncedLinkedCargoSummary != null ? syncedLinkedCargoSummary : structuralLinkedCargoSummary();
        }
        LinkedCargoSummary summary = CargoLinkDiscovery.summarize(level, resolvedLinkedCargo());
        syncedLinkedCargoSummary = summary;
        return summary;
    }

    private LinkedCargoSummary structuralLinkedCargoSummary() {
        int total = linkedCargo.size();
        int item = 0;
        int fluid = 0;
        for (LinkedCargoEntry entry : resolvedLinkedCargo()) {
            if (entry.itemStorage()) {
                item++;
            }
            if (entry.fluidStorage()) {
                fluid++;
            }
        }
        return new LinkedCargoSummary(total, total, 0, item, fluid);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        activeRecording = Optional.empty();
        recordingPoints.clear();
        recordingStops.clear();
        if (tag.getBoolean(LIVE_SYNC) && tag.contains(RECORDING_STOPS, Tag.TAG_LIST)) {
            recordingStops.addAll(RouteNbtSerializer.readStops(tag.getList(RECORDING_STOPS, Tag.TAG_COMPOUND), Integer.MAX_VALUE));
        }
        boolean liveSync = tag.getBoolean(LIVE_SYNC);
        linkedControllers.clear();
        linkedControllers.addAll(readLinkedControllers(tag));
        if (!liveSync || tag.contains(ROUTE_SEGMENTS, Tag.TAG_LIST)) {
            routeSegments.clear();
        }
        if (tag.contains(ROUTE_SEGMENTS, Tag.TAG_LIST)) {
            routeSegments.addAll(RouteSegmentNbtSerializer.readSegments(tag.getList(ROUTE_SEGMENTS, Tag.TAG_COMPOUND)));
        }
        if (tag.hasUUID(STATION_ID)) {
            stationId = tag.getUUID(STATION_ID);
        }
        stationName = tag.contains(STATION_NAME, Tag.TAG_STRING)
                ? IdentityNames.sanitize(tag.getString(STATION_NAME))
                : "";
        ownerId = tag.hasUUID(OWNER_ID)
                ? Optional.of(tag.getUUID(OWNER_ID))
                : Optional.empty();
        ownerName = tag.contains(OWNER_NAME, Tag.TAG_STRING)
                ? IdentityNames.sanitize(tag.getString(OWNER_NAME))
                : "";
        selectedTransponderId = tag.hasUUID(SELECTED_TRANSPONDER_ID)
                ? Optional.of(tag.getUUID(SELECTED_TRANSPONDER_ID))
                : Optional.empty();
        groundDockPos = tag.contains(GROUND_DOCK_POS)
                ? NbtUtils.readBlockPos(tag, GROUND_DOCK_POS)
                : Optional.empty();
        groundDockStatus = readDockStatus(tag);
        dockOutputActive = tag.getBoolean(DOCK_OUTPUT_ACTIVE);
        linkedCargo.clear();
        if (tag.contains(LINKED_CARGO, Tag.TAG_LIST)) {
            ListTag linkedCargoTag = tag.getList(LINKED_CARGO, Tag.TAG_COMPOUND);
            for (int i = 0; i < linkedCargoTag.size(); i++) {
                LinkedCargoEntry.read(linkedCargoTag.getCompound(i)).ifPresent(linkedCargo::add);
            }
        }
        syncedLinkedCargoSummary = readLinkedCargoSummary(tag);
        linkedCargoRevision = tag.getInt(LINKED_CARGO_REVISION);
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station loadAdditional id={} pos={} linkedCargoCount={} routeSegmentCount={} hadLinkedCargoTag={} liveSync={}",
                stationId,
                worldPosition,
                linkedCargo.size(),
                routeSegments.size(),
                tag.contains(LINKED_CARGO, Tag.TAG_LIST),
                liveSync
        );
        selectedShipName = tag.contains(SELECTED_SHIP_NAME, Tag.TAG_STRING)
                ? IdentityNames.sanitize(tag.getString(SELECTED_SHIP_NAME))
                : "";
        failureReason = readFailureReason(tag);
        recordedRoute = readRecordedRoute(tag);
        status = readStatus(tag, recordedRoute, failureReason);
        registerLoadedSnapshot();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    private void registerLoadedSnapshot() {
        if (level == null) {
            return;
        }
        AirshipStationSnapshot snapshot = new AirshipStationSnapshot(
                stationId,
                stationName(),
                level.dimension(),
                worldPosition,
                ownerId,
                ownerName
        );
        AirshipStationRegistry.register(snapshot);
        if (level instanceof ServerLevel serverLevel) {
            IdentityDirectorySavedData.upsertStation(serverLevel.getServer(), snapshot);
            StationChunkLoadingService.track(serverLevel, stationId, worldPosition);
            int deferredDeletionsApplied = AutomatedLogisticsServices.ROUTES.applyPendingDeletions(serverLevel, this);
            List<RouteSegment> ownedSegments = routeSegmentsOwnedByThisStation();
            AutomatedLogisticsServices.ROUTES.refreshIndexForStartStation(stationId, ownedSegments);
            AutomatedLogisticsServices.ROUTES.syncStoredSegments(serverLevel.getServer(), stationId, routeSegments());
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Route index rebuild station={} pos={} dimension={} ownedSegments={} storedSegments={} deferredDeletionsApplied={}",
                    stationId,
                    worldPosition.toShortString(),
                    serverLevel.dimension().location(),
                    ownedSegments.size(),
                    routeSegments.size(),
                    deferredDeletionsApplied
            );
        }
    }

    private void syncRoutePersistence() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AutomatedLogisticsServices.ROUTES.refreshIndexForStartStation(stationId, routeSegmentsOwnedByThisStation());
        AutomatedLogisticsServices.ROUTES.syncStoredSegments(serverLevel.getServer(), stationId, routeSegments());
    }

    private List<RouteSegment> routeSegmentsOwnedByThisStation() {
        return routeSegments.stream()
                .filter(segment -> segment.startStationId().equals(stationId))
                .toList();
    }

    private @Nullable LinkedCargoSummary readLinkedCargoSummary(CompoundTag tag) {
        if (!tag.contains(LINKED_CARGO_TOTAL, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(LINKED_CARGO_VALID, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(LINKED_CARGO_STALE, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(LINKED_CARGO_ITEM_COUNT, Tag.TAG_ANY_NUMERIC)
                || !tag.contains(LINKED_CARGO_FLUID_COUNT, Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        return new LinkedCargoSummary(
                tag.getInt(LINKED_CARGO_TOTAL),
                tag.getInt(LINKED_CARGO_VALID),
                tag.getInt(LINKED_CARGO_STALE),
                tag.getInt(LINKED_CARGO_ITEM_COUNT),
                tag.getInt(LINKED_CARGO_FLUID_COUNT)
        );
    }

    private void pruneSegmentHistory(RouteSegment latest, int maxPerStationPair) {
        List<RouteSegment> matching = routeSegments.stream()
                .filter(segment -> segment.connects(latest.startStationId(), latest.endStationId(), latest.transponderId()))
                .sorted((first, second) -> Long.compare(second.createdEpochMillis(), first.createdEpochMillis()))
                .toList();
        if (matching.size() <= maxPerStationPair) {
            return;
        }

        for (int i = maxPerStationPair; i < matching.size(); i++) {
            routeSegments.remove(matching.get(i));
        }
    }

    private RouteStatus durableStatus() {
        if (status == RouteStatus.RECORDING) {
            return RouteStatus.INVALID_ROUTE;
        }
        if (status == RouteStatus.RUNNING || status == RouteStatus.WAITING) {
            return recordedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
        }
        return status;
    }

    private Optional<Route> readRecordedRoute(CompoundTag tag) {
        if (!tag.contains(RECORDED_ROUTE, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return RouteNbtSerializer.read(tag.getCompound(RECORDED_ROUTE));
    }

    private List<LinkedCargoEntry> resolvedLinkedCargo() {
        ensureLinkedCargoLoaded();
        return linkedCargo;
    }

    private void ensureLinkedCargoLoaded() {
        if (!linkedCargo.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        loadLinkedCargoFromSavedData(serverLevel);
    }

    private boolean loadLinkedCargoFromSavedData(ServerLevel level) {
        if (!linkedCargo.isEmpty()) {
            return false;
        }
        List<LinkedCargoEntry> restored = StationCargoSavedData.entries(level.getServer(), stationId, worldPosition);
        if (restored.isEmpty()) {
            if (!linkedCargoRestoreMissLogged) {
                CreateAeronauticsAutomatedLogistics.debugUi(
                        "Station loadLinkedCargoFromSavedData miss id={} pos={}",
                        stationId,
                        worldPosition
                );
                linkedCargoRestoreMissLogged = true;
            }
            return false;
        }
        linkedCargo.clear();
        linkedCargo.addAll(restored);
        linkedCargoRestoreMissLogged = false;
        StationCargoSavedData.put(level.getServer(), stationId, worldPosition, linkedCargo);
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station loadLinkedCargoFromSavedData restored id={} pos={} restoredCount={}",
                stationId,
                worldPosition,
                linkedCargo.size()
        );
        return true;
    }

    private void persistLinkedCargo() {
        if (level instanceof ServerLevel serverLevel) {
            persistLinkedCargo(serverLevel);
        }
    }

    private void persistLinkedCargo(ServerLevel level) {
        linkedCargoRestoreMissLogged = false;
        if (linkedCargo.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station persistLinkedCargo remove id={} pos={}",
                    stationId,
                    worldPosition
            );
            StationCargoSavedData.remove(level.getServer(), stationId);
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station persistLinkedCargo put id={} pos={} count={}",
                stationId,
                worldPosition,
                linkedCargo.size()
        );
        StationCargoSavedData.put(level.getServer(), stationId, worldPosition, linkedCargo);
    }

    private ListTag writeLinkedControllers() {
        ListTag list = new ListTag();
        for (VehicleControllerRef controller : linkedControllers) {
            if (controller.vehicleId().isEmpty() && controller.controllerPos().isEmpty()) {
                continue;
            }

            CompoundTag controllerTag = new CompoundTag();
            controllerTag.putString(CONTROLLER_TYPE, controller.controllerType().toString());
            controllerTag.putString(DIMENSION, controller.dimension().location().toString());
            controller.vehicleId().ifPresent(vehicleId -> controllerTag.putUUID(VEHICLE_ID, vehicleId));
            controller.controllerPos().ifPresent(pos -> {
                controllerTag.putInt(CONTROLLER_X, pos.getX());
                controllerTag.putInt(CONTROLLER_Y, pos.getY());
                controllerTag.putInt(CONTROLLER_Z, pos.getZ());
            });
            list.add(controllerTag);
        }
        return list;
    }

    private List<VehicleControllerRef> readLinkedControllers(CompoundTag tag) {
        if (!tag.contains(LINKED_CONTROLLERS, Tag.TAG_LIST)) {
            return List.of();
        }

        List<VehicleControllerRef> controllers = new ArrayList<>();
        ListTag list = tag.getList(LINKED_CONTROLLERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag controllerTag = list.getCompound(i);
            try {
                ResourceLocation controllerType = ResourceLocation.parse(controllerTag.getString(CONTROLLER_TYPE));
                ResourceKey<Level> dimension = ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.parse(controllerTag.getString(DIMENSION))
                );
                Optional<UUID> vehicleId = controllerTag.hasUUID(VEHICLE_ID)
                        ? Optional.of(controllerTag.getUUID(VEHICLE_ID))
                        : Optional.empty();
                Optional<BlockPos> controllerPos = controllerTag.contains(CONTROLLER_X, Tag.TAG_ANY_NUMERIC)
                        && controllerTag.contains(CONTROLLER_Y, Tag.TAG_ANY_NUMERIC)
                        && controllerTag.contains(CONTROLLER_Z, Tag.TAG_ANY_NUMERIC)
                        ? Optional.of(new BlockPos(
                                controllerTag.getInt(CONTROLLER_X),
                                controllerTag.getInt(CONTROLLER_Y),
                                controllerTag.getInt(CONTROLLER_Z)
                        ))
                        : Optional.empty();
                controllers.add(new VehicleControllerRef(controllerType, dimension, vehicleId, controllerPos));
            } catch (IllegalArgumentException ignored) {
                failureReason = Optional.of(FailureReason.INVALID_ROUTE_DATA);
            }
        }
        return controllers;
    }

    private Optional<FailureReason> readFailureReason(CompoundTag tag) {
        if (!tag.contains(FAILURE_REASON, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(FailureReason.valueOf(tag.getString(FAILURE_REASON)));
        } catch (IllegalArgumentException ignored) {
            return Optional.of(FailureReason.INVALID_ROUTE_DATA);
        }
    }

    private RouteStatus readStatus(
            CompoundTag tag,
            Optional<Route> loadedRoute,
            Optional<FailureReason> loadedFailureReason
    ) {
        RouteStatus loadedStatus = loadedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
        if (tag.contains(STATUS, Tag.TAG_STRING)) {
            try {
                loadedStatus = RouteStatus.valueOf(tag.getString(STATUS));
            } catch (IllegalArgumentException ignored) {
                return RouteStatus.INVALID_ROUTE;
            }
        }

        if (loadedStatus == RouteStatus.RECORDING && tag.getBoolean(LIVE_SYNC)) {
            failureReason = Optional.empty();
            return RouteStatus.RECORDING;
        }
        if (loadedStatus == RouteStatus.RECORDING) {
            failureReason = Optional.of(FailureReason.INVALID_ROUTE_DATA);
            return RouteStatus.INVALID_ROUTE;
        }
        if ((loadedStatus == RouteStatus.RUNNING || loadedStatus == RouteStatus.WAITING) && tag.getBoolean(LIVE_SYNC)) {
            return loadedStatus;
        }
        if (loadedStatus == RouteStatus.RUNNING || loadedStatus == RouteStatus.WAITING) {
            return loadedRoute.isPresent() ? RouteStatus.RECORDED : RouteStatus.IDLE;
        }
        if (loadedRoute.isEmpty() && loadedStatus == RouteStatus.RECORDED) {
            failureReason = Optional.of(FailureReason.INVALID_ROUTE_DATA);
            return RouteStatus.INVALID_ROUTE;
        }
        if (loadedFailureReason.isPresent() && loadedStatus == RouteStatus.IDLE) {
            return RouteStatus.INVALID_ROUTE;
        }

        return loadedStatus;
    }

    private DockLinkStatus readDockStatus(CompoundTag tag) {
        if (!tag.contains(GROUND_DOCK_STATUS, Tag.TAG_STRING)) {
            return groundDockPos.isPresent() ? DockLinkStatus.LINKED : DockLinkStatus.UNKNOWN;
        }
        try {
            DockLinkStatus loaded = DockLinkStatus.valueOf(tag.getString(GROUND_DOCK_STATUS));
            if (loaded == DockLinkStatus.LINKED && groundDockPos.isEmpty()) {
                return DockLinkStatus.UNKNOWN;
            }
            return loaded;
        } catch (IllegalArgumentException ignored) {
            return DockLinkStatus.INVALID;
        }
    }

    private void notifyRedstoneNeighbors() {
        if (level == null || level.isClientSide) {
            return;
        }
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    private void syncPoweredBlockState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState current = getBlockState();
        if (!(current.getBlock() instanceof AirshipStationBlock)) {
            return;
        }
        boolean currentPowered = current.getValue(AirshipStationBlock.POWERED);
        if (currentPowered == dockOutputActive) {
            return;
        }
        level.setBlock(
                worldPosition,
                current.setValue(AirshipStationBlock.POWERED, dockOutputActive),
                3
        );
    }
}

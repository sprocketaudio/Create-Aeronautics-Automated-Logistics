package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockDiscoveryResult;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingConnectorDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.TransponderCargoSavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.ShipBodyDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoFailureContext;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class ShipTransponderBlockEntity extends BlockEntity implements MenuProvider {
    private static final String DATA_VERSION = "dataVersion";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SHIP_NAME = "shipName";
    private static final String OWNER_ID = "ownerId";
    private static final String OWNER_NAME = "ownerName";
    private static final String RUNTIME_SHIP_ID = "runtimeShipId";
    private static final String LAST_X = "lastKnownX";
    private static final String LAST_Y = "lastKnownY";
    private static final String LAST_Z = "lastKnownZ";
    private static final String LAST_SEEN = "lastSeenGameTime";
    private static final String SHIP_DOCK_POS = "shipDockPos";
    private static final String SHIP_DOCK_STATUS = "shipDockStatus";
    private static final String DOCK_OUTPUT_ACTIVE = "dockOutputActive";
    private static final String DOCK_OUTPUT_OWNER = "dockOutputOwner";
    private static final String APPEND_TO_SCHEDULE = "appendToSchedule";
    private static final String OWNED_SCHEDULE = "ownedSchedule";
    private static final String RECORDING_DESTINATION_STATION_ID = "recordingDestinationStationId";
    private static final String RUNTIME_STATUS = "runtimeStatus";
    private static final String SCHEDULE_SLOT = "scheduleSlot";
    private static final String LEGACY_ITEM_ID = CreateAeronauticsAutomatedLogistics.MOD_ID + ":airship_schedule";
    private static final String LEGACY_COMPONENTS = "components";
    private static final String LEGACY_CUSTOM_DATA_COMPONENT = "minecraft:custom_data";
    private static final String LEGACY_ITEM_TAG = "tag";
    private static final String LEGACY_SCHEDULE_TAG = "airshipSchedule";
    private static final String LINKED_CARGO = "linkedCargo";
    private static final String LINKED_CARGO_X = "x";
    private static final String LINKED_CARGO_Y = "y";
    private static final String LINKED_CARGO_Z = "z";
    private static final String LINKED_CARGO_ITEM = "item";
    private static final String LINKED_CARGO_FLUID = "fluid";
    private static final String LINKED_CARGO_TOTAL = "linkedCargoTotal";
    private static final String LINKED_CARGO_VALID = "linkedCargoValid";
    private static final String LINKED_CARGO_STALE = "linkedCargoStale";
    private static final String LINKED_CARGO_ITEM_COUNT = "linkedCargoItemCount";
    private static final String LINKED_CARGO_FLUID_COUNT = "linkedCargoFluidCount";
    private static final String LINKED_CARGO_REVISION = "linkedCargoRevision";
    private static final String CARGO_FAILURE_TARGET = "cargoFailureTarget";
    private static final String CARGO_FAILURE_WAIT_TYPE = "cargoFailureWaitType";
    private static final int CURRENT_DATA_VERSION = 1;
    private static final int REFRESH_INTERVAL_TICKS = 40;

    private UUID transponderId = UUID.randomUUID();
    private String shipName = "";
    private Optional<UUID> ownerId = Optional.empty();
    private String ownerName = "";
    private Optional<UUID> runtimeShipId = Optional.empty();
    private Optional<Vec3> lastKnownPosition = Optional.empty();
    private Optional<BlockPos> shipDockPos = Optional.empty();
    private DockLinkStatus shipDockStatus = DockLinkStatus.UNKNOWN;
    private boolean dockOutputActive;
    private Optional<RouteId> dockOutputOwner = Optional.empty();
    private boolean appendToSchedule;
    private AirshipSchedule ownedSchedule = AirshipSchedule.empty();
    private Optional<UUID> recordingDestinationStationId = Optional.empty();
    private RouteStatus runtimeStatus = RouteStatus.IDLE;
    private long lastSeenGameTime = -1L;
    private final List<LinkedCargoEntry> linkedCargo = new ArrayList<>();
    private @Nullable LinkedCargoSummary syncedLinkedCargoSummary;
    private int linkedCargoRevision;
    private Optional<CargoFailureContext> syncedCargoFailureContext = Optional.empty();
    private boolean linkedCargoRestoreMissLogged;
    private boolean legacyScheduleSlotNeedsRewrite;

    public ShipTransponderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SHIP_TRANSPONDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShipTransponderBlockEntity transponder) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % REFRESH_INTERVAL_TICKS == 0L) {
            transponder.refreshRuntimeShip(serverLevel);
            transponder.refreshShipDockLink(serverLevel);
        }
    }

    public UUID transponderId() {
        return transponderId;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            RouteStatus projectedRuntimeStatus = AutomatedLogisticsServices.SCHEDULES.projectRuntimeStatus(serverLevel, this);
            ShipTransponderMenu.InitialRecordingState recordingState =
                    player instanceof ServerPlayer serverPlayer
                            ? ShipTransponderMenu.resolveInitialRecordingState(serverPlayer, this, false)
                            : new ShipTransponderMenu.InitialRecordingState(false, false, appendToSchedule());
            ShipTransponderMenu menu = new ShipTransponderMenu(
                    containerId,
                    playerInventory,
                    worldPosition,
                    recordingState.recordingMode(),
                    recordingState.recordingSessionActive(),
                    recordingState.appendToSchedule(),
                    recordingDestinationStationId(),
                    projectedRuntimeStatus,
                    dockOutputActive(),
                    hasOwnedStops(),
                    ownedSchedule(),
                    linkedCargoRevision(),
                    linkedCargoSummary(),
                    linkedCargo(),
                    AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponderId()),
                    AutomatedLogisticsServices.SCHEDULES.lastFailure(transponderId()),
                    ShipTransponderMenu.StatusSnapshot.idle()
            );
            if (player instanceof ServerPlayer serverPlayer) {
                return new ShipTransponderMenu(
                        containerId,
                        playerInventory,
                        worldPosition,
                        recordingState.recordingMode(),
                        recordingState.recordingSessionActive(),
                        recordingState.appendToSchedule(),
                        recordingDestinationStationId(),
                        projectedRuntimeStatus,
                        dockOutputActive(),
                        hasOwnedStops(),
                        ownedSchedule(),
                        linkedCargoRevision(),
                        linkedCargoSummary(),
                        linkedCargo(),
                        AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponderId()),
                        AutomatedLogisticsServices.SCHEDULES.lastFailure(transponderId()),
                        menu.buildStatusSnapshot(serverPlayer)
                );
            }
            return menu;
        }
        return new ShipTransponderMenu(
                containerId,
                playerInventory,
                worldPosition,
                false,
                false,
                appendToSchedule(),
                recordingDestinationStationId(),
                runtimeStatus(),
                dockOutputActive(),
                hasOwnedStops(),
                ownedSchedule(),
                linkedCargoRevision(),
                linkedCargoSummary(),
                linkedCargo(),
                syncedCargoFailureContext(),
                Optional.empty(),
                ShipTransponderMenu.StatusSnapshot.idle()
        );
    }

    public String shipName() {
        if (shipName == null || shipName.isBlank()) {
            return IdentityNames.defaultShipName(transponderId);
        }
        return shipName;
    }

    public Optional<UUID> runtimeShipId() {
        return runtimeShipId;
    }

    public Optional<UUID> ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(ServerPlayer player) {
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

    public Optional<Vec3> lastKnownPosition() {
        return lastKnownPosition;
    }

    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    public Optional<BlockPos> shipDockPos() {
        return shipDockPos;
    }

    public DockLinkStatus shipDockStatus() {
        if (shipDockStatus == DockLinkStatus.LINKED && shipDockPos.isEmpty()) {
            return DockLinkStatus.UNKNOWN;
        }
        return shipDockStatus;
    }

    public boolean dockOutputActive() {
        return dockOutputActive;
    }

    public Optional<RouteId> dockOutputOwner() {
        return dockOutputOwner;
    }

    public boolean appendToSchedule() {
        return appendToSchedule;
    }

    public Optional<UUID> recordingDestinationStationId() {
        return recordingDestinationStationId;
    }

    public RouteStatus runtimeStatus() {
        return runtimeStatus;
    }

    public boolean scheduleActive() {
        return runtimeStatus == RouteStatus.RUNNING
                || runtimeStatus == RouteStatus.WAITING
                || runtimeStatus == RouteStatus.DOCK_QUEUED
                || runtimeStatus == RouteStatus.HELD
                || runtimeStatus == RouteStatus.HELD_FAULTED;
    }

    public boolean scheduleHeld() {
        return runtimeStatus == RouteStatus.HELD || runtimeStatus == RouteStatus.HELD_FAULTED;
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

    public Optional<CargoFailureContext> syncedCargoFailureContext() {
        return syncedCargoFailureContext;
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
        syncClientState();
        return linkedCargoSummary();
    }

    public int addLinkedCargoEntries(List<LinkedCargoEntry> entries) {
        ensureLinkedCargoLoaded();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder addLinkedCargoEntries start id={} pos={} existingCount={} incomingCount={}",
                transponderId,
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
                    "Transponder addLinkedCargoEntries saved id={} pos={} added={} newCount={}",
                    transponderId,
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
                    "Transponder clearLinkedCargo no-op id={} pos={} count=0",
                    transponderId,
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
                "Transponder clearLinkedCargo cleared id={} pos={}",
                transponderId,
                worldPosition
        );
        return true;
    }

    public void claimDockOutput(RouteId routeId) {
        Objects.requireNonNull(routeId, "routeId");
        updateDockOutputState(true, Optional.of(routeId), "claim");
    }

    public boolean releaseDockOutput(RouteId routeId) {
        Objects.requireNonNull(routeId, "routeId");
        if (dockOutputOwner.filter(routeId::equals).isEmpty()) {
            if (dockOutputOwner.isEmpty() && !dockOutputActive) {
                return true;
            }
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Transponder dock output clear refused: transponderId={} pos={} requestedRoute={} active={} owner={} runtimeStatus={} reason=owner_mismatch",
                    transponderId,
                    worldPosition,
                    routeId.value(),
                    dockOutputActive,
                    dockOutputOwner.map(RouteId::value).map(Object::toString).orElse("none"),
                    runtimeStatus
            );
            return false;
        }
        updateDockOutputState(false, Optional.empty(), "release");
        return true;
    }

    public void forceClearDockOutput(String reason) {
        updateDockOutputState(false, Optional.empty(), "force_clear:" + reason);
    }

    public void setDockOutputActive(boolean active) {
        updateDockOutputState(active, active ? dockOutputOwner : Optional.empty(), "legacy_set");
    }

    private void updateDockOutputState(boolean active, Optional<RouteId> owner, String action) {
        Optional<RouteId> previousOwner = dockOutputOwner;
        boolean previousActive = dockOutputActive;
        if (previousActive == active && previousOwner.equals(owner)) {
            return;
        }
        dockOutputActive = active;
        dockOutputOwner = active ? owner : Optional.empty();
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Transponder dock output changed: transponderId={} pos={} {}->{} owner={} prevOwner={} runtimeStatus={} shipDock={} action={} caller={}",
                transponderId,
                worldPosition,
                previousActive,
                dockOutputActive,
                dockOutputOwner.map(RouteId::value).map(Object::toString).orElse("none"),
                previousOwner.map(RouteId::value).map(Object::toString).orElse("none"),
                runtimeStatus,
                shipDockPos.map(BlockPos::toShortString).orElse("-"),
                action,
                dockOutputMutationSource()
        );
        if (previousActive != dockOutputActive) {
            setChanged();
            syncPoweredBlockState();
            notifyRedstoneNeighbors();
            syncClientState();
        }
    }

    private static String dockOutputMutationSource() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            if (className.equals(Thread.class.getName())
                    || className.equals(ShipTransponderBlockEntity.class.getName()) && methodName.equals("dockOutputMutationSource")
                    || className.equals(ShipTransponderBlockEntity.class.getName()) && methodName.equals("setDockOutputActive")) {
                continue;
            }
            return className + "#" + methodName + ":" + element.getLineNumber();
        }
        return "unknown";
    }

    public void setAppendToSchedule(boolean appendToSchedule) {
        if (this.appendToSchedule == appendToSchedule) {
            return;
        }
        this.appendToSchedule = appendToSchedule;
        setChanged();
        syncClientState();
    }

    public void setRecordingDestinationStationId(Optional<UUID> recordingDestinationStationId) {
        Optional<UUID> normalized = recordingDestinationStationId == null ? Optional.empty() : recordingDestinationStationId;
        if (this.recordingDestinationStationId.equals(normalized)) {
            return;
        }
        this.recordingDestinationStationId = normalized;
        setChanged();
        syncClientState();
    }

    public void setRuntimeStatus(RouteStatus runtimeStatus) {
        RouteStatus normalized = runtimeStatus == null ? RouteStatus.IDLE : runtimeStatus;
        if (this.runtimeStatus == normalized) {
            return;
        }
        this.runtimeStatus = normalized;
        setChanged();
        syncClientState();
    }

    public void setShipName(String shipName) {
        String sanitized = IdentityNames.sanitize(shipName);
        this.shipName = sanitized.isBlank() ? IdentityNames.defaultShipName(transponderId) : sanitized;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            ShipTransponderSnapshot snapshot = snapshot(serverLevel);
            ShipTransponderRegistry.register(snapshot);
            IdentityDirectorySavedData.upsertShip(serverLevel.getServer(), snapshot);
        }
        syncClientState();
    }

    public Optional<VehicleControllerRef> controllerRef(ServerLevel level) {
        refreshRuntimeShip(level);
        return runtimeShipId.flatMap(shipId -> SableSubLevelVehicleController.resolveControllerBlock(
                level,
                worldPosition,
                ModBlocks.SHIP_TRANSPONDER.get()
        ).map(SableSubLevelVehicleController::ref));
    }

    public void refreshRuntimeShip(ServerLevel level) {
        Optional<SableSubLevelVehicleController> controller = SableSubLevelVehicleController.resolveControllerBlock(
                level,
                worldPosition,
                ModBlocks.SHIP_TRANSPONDER.get()
        );
        Optional<UUID> previousId = runtimeShipId;
        Optional<Vec3> previousPosition = lastKnownPosition;
        long previousSeen = lastSeenGameTime;

        if (controller.isPresent()) {
            SableSubLevelVehicleController liveController = controller.get();
            runtimeShipId = liveController.ref().vehicleId().or(() -> runtimeShipId);
            lastKnownPosition = Optional.of(liveController.position());
            lastSeenGameTime = level.getGameTime();
            liveController.ref().vehicleId().ifPresent(shipId -> {
                Optional<UUID> existingTrackingPointId = ShipBodyDirectorySavedData.get(level.getServer())
                        .byTransponder(transponderId)
                        .filter(identity -> identity.sableShipId().equals(shipId))
                        .flatMap(ShipBodyDirectorySavedData.BodyIdentity::trackingPointId);
                UUID trackingPointId = liveController.ensureMaterializationTrackingPoint(existingTrackingPointId);
                ShipBodyDirectorySavedData.observeLiveBody(
                        level.getServer(),
                        transponderId,
                        shipId,
                        level.dimension(),
                        liveController.ref().controllerPos().orElse(worldPosition),
                        Optional.of(trackingPointId),
                        liveController.lastSerializationPointer(),
                        level.getGameTime()
                );
            });
        } else if (runtimeShipId.isPresent() && level.getGameTime() % 100 == 0) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Preserving transponder runtime ship identity after live lookup miss: transponder={} ship={} pos={} reason=controller_not_visible",
                    transponderId,
                    runtimeShipId.get(),
                    worldPosition.toShortString()
            );
        }

        if (!previousId.equals(runtimeShipId)
                || !previousPosition.equals(lastKnownPosition)
                || previousSeen != lastSeenGameTime) {
            setChanged();
        }
        ShipTransponderSnapshot snapshot = snapshot(level);
        ShipTransponderRegistry.register(snapshot);
        IdentityDirectorySavedData.upsertShip(level.getServer(), snapshot);
    }

    public DockDiscoveryResult refreshShipDockLink(ServerLevel level) {
        refreshRuntimeShip(level);
        DockDiscoveryResult result = validateShipDockLink(level);
        DockLinkStatus previousStatus = shipDockStatus;
        Optional<BlockPos> previousDockPos = shipDockPos;
        shipDockStatus = result.status() == DockLinkStatus.LINKED && result.dockPos().isEmpty()
                ? DockLinkStatus.UNKNOWN
                : result.status();
        if (result.status() == DockLinkStatus.LINKED) {
            shipDockPos = result.dockPos();
        } else if (shipDockPos.isPresent() && level.getGameTime() % 100 == 0) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Preserving transponder dock link after passive refresh miss: transponder={} dock={} status={} reason=not_explicit_clear",
                    transponderId,
                    shipDockPos.get().toShortString(),
                    result.status()
            );
        }
        if (previousStatus.equals(shipDockStatus) && previousDockPos.equals(shipDockPos)) {
            return result;
        }
        setChanged();
        syncClientState();
        return result;
    }

    public DockDiscoveryResult setShipDockLink(ServerLevel level, BlockPos dockPos) {
        refreshRuntimeShip(level);
        if (!DockingConnectorDiscovery.isDock(level, dockPos)) {
            shipDockPos = Optional.empty();
            shipDockStatus = DockLinkStatus.INVALID;
            setChanged();
            syncClientState();
            return DockDiscoveryResult.invalid();
        }
        if (!dockBelongsToResolvedShip(level, dockPos)) {
            shipDockPos = Optional.empty();
            shipDockStatus = DockLinkStatus.INVALID;
            setChanged();
            syncClientState();
            return DockDiscoveryResult.invalid();
        }
        shipDockPos = Optional.of(dockPos.immutable());
        shipDockStatus = DockLinkStatus.LINKED;
        setChanged();
        syncClientState();
        return DockDiscoveryResult.linked(dockPos.immutable());
    }

    public void clearShipDockLink() {
        shipDockPos = Optional.empty();
        shipDockStatus = DockLinkStatus.MISSING;
        setChanged();
        syncClientState();
    }

    private DockDiscoveryResult validateShipDockLink(ServerLevel level) {
        if (shipDockPos.isEmpty()) {
            return DockDiscoveryResult.missing();
        }
        BlockPos dockPos = shipDockPos.get();
        if (!DockingConnectorDiscovery.isDock(level, dockPos)) {
            return DockDiscoveryResult.missing();
        }
        if (!dockBelongsToResolvedShip(level, dockPos)) {
            return DockDiscoveryResult.invalid();
        }
        return DockDiscoveryResult.linked(dockPos);
    }

    private boolean dockBelongsToResolvedShip(ServerLevel level, BlockPos dockPos) {
        if (runtimeShipId.isEmpty()) {
            return false;
        }
        return SableSubLevelVehicleController.subLevelIdAt(level, dockPos)
                .map(runtimeShipId.get()::equals)
                .orElse(false);
    }

    private ShipTransponderSnapshot snapshot(ServerLevel level) {
        Optional<VehicleControllerRef> controllerRef = runtimeShipId.flatMap(shipId -> SableSubLevelVehicleController.resolveControllerBlock(
                level,
                worldPosition,
                ModBlocks.SHIP_TRANSPONDER.get()
        ).map(SableSubLevelVehicleController::ref));
        return new ShipTransponderSnapshot(
                transponderId,
                shipName(),
                level.dimension(),
                worldPosition,
                runtimeShipId,
                controllerRef,
                lastKnownPosition,
                lastSeenGameTime
        );
    }

    public Component statusMessage() {
        Component runtime = runtimeShipId
                .map(id -> Component.literal(id.toString()))
                .orElseGet(() -> Component.translatable("message.create_aeronautics_automated_logistics.ship_transponder.unavailable"));
        return Component.translatable(
                "message.create_aeronautics_automated_logistics.ship_transponder.status",
                shipName(),
                IdentityNames.shortId(transponderId),
                runtime
        );
    }

    public AirshipSchedule ownedSchedule() {
        return bindScheduleToThisTransponder(ownedSchedule);
    }

    public void setOwnedSchedule(AirshipSchedule schedule) {
        AirshipSchedule normalized = bindScheduleToThisTransponder(schedule);
        if (ownedSchedule.equals(normalized)) {
            return;
        }
        ownedSchedule = normalized;
        setChanged();
        syncClientState();
    }

    public boolean hasOwnedStops() {
        return !ownedSchedule().entries().isEmpty();
    }

    private AirshipSchedule bindScheduleToThisTransponder(AirshipSchedule schedule) {
        AirshipSchedule source = schedule == null ? AirshipSchedule.empty() : schedule;
        return source.withAssignedShip(Optional.of(transponderId), shipName());
    }

    private void migrateLegacyScheduleSlot(CompoundTag rootTag) {
        if (!rootTag.contains(SCHEDULE_SLOT, Tag.TAG_COMPOUND)) {
            return;
        }
        legacyScheduleSlotNeedsRewrite = true;
        CompoundTag slotTag = rootTag.getCompound(SCHEDULE_SLOT);
        Optional<AirshipSchedule> legacySchedule = readLegacyScheduleSlot(slotTag);
        if (legacySchedule.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Legacy schedule slot migration ignored: transponderId={} pos={} reason=no_schedule_payload itemId={}",
                    transponderId,
                    worldPosition,
                    legacySlotItemId(slotTag)
            );
            return;
        }
        if (ownedSchedule.entries().isEmpty()) {
            ownedSchedule = bindScheduleToThisTransponder(legacySchedule.get());
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Legacy schedule slot migration applied: transponderId={} pos={} legacyEntries={} title='{}'",
                    transponderId,
                    worldPosition,
                    ownedSchedule.entries().size(),
                    ownedSchedule.title()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Legacy schedule slot migration skipped: transponderId={} pos={} reason=owned_schedule_already_present existingEntries={} legacyEntries={}",
                transponderId,
                worldPosition,
                ownedSchedule.entries().size(),
                legacySchedule.get().entries().size()
        );
    }

    private Optional<AirshipSchedule> readLegacyScheduleSlot(CompoundTag slotTag) {
        Optional<CompoundTag> scheduleTag = readLegacyScheduleTag(slotTag);
        if (scheduleTag.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AirshipScheduleNbtSerializer.read(scheduleTag.get()));
        } catch (RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Legacy schedule slot migration failed: transponderId={} pos={} reason=deserialize_error itemId={} error={}",
                    transponderId,
                    worldPosition,
                    legacySlotItemId(slotTag),
                    exception.toString()
            );
            return Optional.empty();
        }
    }

    private Optional<CompoundTag> readLegacyScheduleTag(CompoundTag slotTag) {
        if (slotTag.contains(LEGACY_COMPONENTS, Tag.TAG_COMPOUND)) {
            CompoundTag components = slotTag.getCompound(LEGACY_COMPONENTS);
            if (components.contains(LEGACY_CUSTOM_DATA_COMPONENT, Tag.TAG_COMPOUND)) {
                CompoundTag customData = components.getCompound(LEGACY_CUSTOM_DATA_COMPONENT);
                if (customData.contains(LEGACY_SCHEDULE_TAG, Tag.TAG_COMPOUND)) {
                    return Optional.of(customData.getCompound(LEGACY_SCHEDULE_TAG));
                }
            }
        }
        if (slotTag.contains(LEGACY_ITEM_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag legacyItemTag = slotTag.getCompound(LEGACY_ITEM_TAG);
            if (legacyItemTag.contains(LEGACY_SCHEDULE_TAG, Tag.TAG_COMPOUND)) {
                return Optional.of(legacyItemTag.getCompound(LEGACY_SCHEDULE_TAG));
            }
        }
        if (slotTag.contains(LEGACY_SCHEDULE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.of(slotTag.getCompound(LEGACY_SCHEDULE_TAG));
        }
        return Optional.empty();
    }

    private String legacySlotItemId(CompoundTag slotTag) {
        if (slotTag.contains("id", Tag.TAG_STRING)) {
            return slotTag.getString("id");
        }
        return LEGACY_ITEM_ID;
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    public void syncClientState() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(tag, false, true, true);
    }

    private void writeData(
            CompoundTag tag,
            boolean liveCargoSummary,
            boolean includeOwnedSchedule,
            boolean includeLinkedCargoEntries
    ) {
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putUUID(TRANSPONDER_ID, transponderId);
        tag.putString(SHIP_NAME, shipName());
        ownerId.ifPresent(id -> tag.putUUID(OWNER_ID, id));
        tag.putString(OWNER_NAME, ownerName);
        runtimeShipId.ifPresent(id -> tag.putUUID(RUNTIME_SHIP_ID, id));
        shipDockPos.ifPresent(pos -> tag.put(SHIP_DOCK_POS, NbtUtils.writeBlockPos(pos)));
        DockLinkStatus savedDockStatus = shipDockStatus == DockLinkStatus.LINKED && shipDockPos.isEmpty()
                ? DockLinkStatus.UNKNOWN
                : shipDockStatus;
        tag.putString(SHIP_DOCK_STATUS, savedDockStatus.name());
        tag.putBoolean(DOCK_OUTPUT_ACTIVE, dockOutputActive);
        dockOutputOwner.ifPresent(routeId -> tag.putUUID(DOCK_OUTPUT_OWNER, routeId.value()));
        tag.putBoolean(APPEND_TO_SCHEDULE, appendToSchedule);
        if (includeOwnedSchedule) {
            tag.put(OWNED_SCHEDULE, AirshipScheduleNbtSerializer.write(ownedSchedule()));
        }
        recordingDestinationStationId.ifPresent(id -> tag.putUUID(RECORDING_DESTINATION_STATION_ID, id));
        tag.putString(RUNTIME_STATUS, runtimeStatus.name());
        if (includeLinkedCargoEntries && !linkedCargo.isEmpty()) {
            ListTag linkedCargoTag = new ListTag();
            for (LinkedCargoEntry entry : linkedCargo) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putInt(LINKED_CARGO_X, entry.pos().getX() - worldPosition.getX());
                entryTag.putInt(LINKED_CARGO_Y, entry.pos().getY() - worldPosition.getY());
                entryTag.putInt(LINKED_CARGO_Z, entry.pos().getZ() - worldPosition.getZ());
                entryTag.putBoolean(LINKED_CARGO_ITEM, entry.itemStorage());
                entryTag.putBoolean(LINKED_CARGO_FLUID, entry.fluidStorage());
                linkedCargoTag.add(entryTag);
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
        Optional<CargoFailureContext> cargoFailureContext = level instanceof ServerLevel serverLevel
                ? AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponderId)
                : syncedCargoFailureContext;
        if (cargoFailureContext.isPresent()) {
            tag.putString(CARGO_FAILURE_TARGET, cargoFailureContext.get().target().name());
            tag.putString(CARGO_FAILURE_WAIT_TYPE, cargoFailureContext.get().waitType().name());
        }
        lastKnownPosition.ifPresent(pos -> {
            tag.putDouble(LAST_X, pos.x);
            tag.putDouble(LAST_Y, pos.y);
            tag.putDouble(LAST_Z, pos.z);
        });
        tag.putLong(LAST_SEEN, lastSeenGameTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TRANSPONDER_ID)) {
            transponderId = tag.getUUID(TRANSPONDER_ID);
        }
        shipName = tag.contains(SHIP_NAME, Tag.TAG_STRING)
                ? IdentityNames.sanitize(tag.getString(SHIP_NAME))
                : "";
        ownerId = tag.hasUUID(OWNER_ID)
                ? Optional.of(tag.getUUID(OWNER_ID))
                : Optional.empty();
        ownerName = tag.contains(OWNER_NAME, Tag.TAG_STRING)
                ? IdentityNames.sanitize(tag.getString(OWNER_NAME))
                : "";
        runtimeShipId = tag.hasUUID(RUNTIME_SHIP_ID)
                ? Optional.of(tag.getUUID(RUNTIME_SHIP_ID))
                : Optional.empty();
        shipDockPos = tag.contains(SHIP_DOCK_POS)
                ? NbtUtils.readBlockPos(tag, SHIP_DOCK_POS)
                : Optional.empty();
        shipDockStatus = readDockStatus(tag);
        dockOutputActive = tag.getBoolean(DOCK_OUTPUT_ACTIVE);
        dockOutputOwner = tag.hasUUID(DOCK_OUTPUT_OWNER)
                ? Optional.of(new RouteId(tag.getUUID(DOCK_OUTPUT_OWNER)))
                : Optional.empty();
        if (dockOutputActive && dockOutputOwner.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugDockingWarn(
                    "Transponder load cleared incomplete dock output state: transponderId={} pos={} active={} reason=missing_persisted_owner",
                    transponderId,
                    worldPosition,
                    dockOutputActive
            );
            dockOutputActive = false;
        }
        appendToSchedule = tag.getBoolean(APPEND_TO_SCHEDULE);
        ownedSchedule = tag.contains(OWNED_SCHEDULE, Tag.TAG_COMPOUND)
                ? bindScheduleToThisTransponder(AirshipScheduleNbtSerializer.read(tag.getCompound(OWNED_SCHEDULE)))
                : AirshipSchedule.empty();
        recordingDestinationStationId = tag.hasUUID(RECORDING_DESTINATION_STATION_ID)
                ? Optional.of(tag.getUUID(RECORDING_DESTINATION_STATION_ID))
                : Optional.empty();
        runtimeStatus = readRuntimeStatus(tag);
        migrateLegacyScheduleSlot(tag);
        linkedCargo.clear();
        if (tag.contains(LINKED_CARGO, Tag.TAG_LIST)) {
            ListTag linkedCargoTag = tag.getList(LINKED_CARGO, Tag.TAG_COMPOUND);
            for (int i = 0; i < linkedCargoTag.size(); i++) {
                readStoredLinkedCargo(linkedCargoTag.getCompound(i)).ifPresent(linkedCargo::add);
            }
        }
        syncedLinkedCargoSummary = readLinkedCargoSummary(tag);
        linkedCargoRevision = tag.getInt(LINKED_CARGO_REVISION);
        syncedCargoFailureContext = readCargoFailureContext(tag);
        lastKnownPosition = tag.contains(LAST_X, Tag.TAG_ANY_NUMERIC)
                && tag.contains(LAST_Y, Tag.TAG_ANY_NUMERIC)
                && tag.contains(LAST_Z, Tag.TAG_ANY_NUMERIC)
                ? Optional.of(new Vec3(tag.getDouble(LAST_X), tag.getDouble(LAST_Y), tag.getDouble(LAST_Z)))
                : Optional.empty();
        lastSeenGameTime = tag.contains(LAST_SEEN, Tag.TAG_ANY_NUMERIC) ? tag.getLong(LAST_SEEN) : -1L;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder onLoad id={} pos={} linkedCargoCount={} levelClient={}",
                transponderId,
                worldPosition,
                linkedCargo.size(),
                level != null && level.isClientSide
        );
        if (level instanceof ServerLevel serverLevel) {
            if (legacyScheduleSlotNeedsRewrite) {
                legacyScheduleSlotNeedsRewrite = false;
                setChanged();
            }
            restoreOrPersistLinkedCargo(serverLevel);
            ShipTransponderSnapshot snapshot = snapshot(serverLevel);
            ShipTransponderRegistry.register(snapshot);
            IdentityDirectorySavedData.upsertShip(serverLevel.getServer(), snapshot);
        }
        syncPoweredBlockState();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // Normal block-entity sync should stay lightweight. Menus already send
        // full owned-schedule and linked-cargo snapshots explicitly on open/reopen.
        writeData(tag, true, false, false);
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

    private DockLinkStatus readDockStatus(CompoundTag tag) {
        if (!tag.contains(SHIP_DOCK_STATUS, Tag.TAG_STRING)) {
            return shipDockPos.isPresent() ? DockLinkStatus.LINKED : DockLinkStatus.UNKNOWN;
        }
        try {
            DockLinkStatus loaded = DockLinkStatus.valueOf(tag.getString(SHIP_DOCK_STATUS));
            if (loaded == DockLinkStatus.LINKED && shipDockPos.isEmpty()) {
                return DockLinkStatus.UNKNOWN;
            }
            return loaded;
        } catch (IllegalArgumentException ignored) {
            return DockLinkStatus.INVALID;
        }
    }

    private RouteStatus readRuntimeStatus(CompoundTag tag) {
        if (!tag.contains(RUNTIME_STATUS, Tag.TAG_STRING)) {
            return RouteStatus.IDLE;
        }
        try {
            return RouteStatus.valueOf(tag.getString(RUNTIME_STATUS));
        } catch (IllegalArgumentException ignored) {
            return RouteStatus.IDLE;
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
        if (!current.is(ModBlocks.SHIP_TRANSPONDER.get())) {
            return;
        }
        boolean currentPowered = current.getValue(net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock.POWERED);
        if (currentPowered == dockOutputActive) {
            return;
        }
        level.setBlock(
                worldPosition,
                current.setValue(net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock.POWERED, dockOutputActive),
                3
        );
    }

    private void restoreOrPersistLinkedCargo(ServerLevel level) {
        if (loadLinkedCargoFromSavedData(level)) {
            setChanged();
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder restoreOrPersistLinkedCargo restored id={} pos={} count={}",
                    transponderId,
                    worldPosition,
                    linkedCargo.size()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder restoreOrPersistLinkedCargo persisting current id={} pos={} count={}",
                transponderId,
                worldPosition,
                linkedCargo.size()
        );
        persistLinkedCargo(level);
    }

    private List<LinkedCargoEntry> resolvedLinkedCargo() {
        ensureLinkedCargoLoaded();
        return linkedCargo;
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

    private Optional<CargoFailureContext> readCargoFailureContext(CompoundTag tag) {
        if (!tag.contains(CARGO_FAILURE_TARGET, Tag.TAG_STRING) || !tag.contains(CARGO_FAILURE_WAIT_TYPE, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CargoFailureContext(
                    CargoWaitTarget.valueOf(tag.getString(CARGO_FAILURE_TARGET)),
                    WaitConditionType.valueOf(tag.getString(CARGO_FAILURE_WAIT_TYPE))
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
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
        List<LinkedCargoEntry> restored = TransponderCargoSavedData.entries(level.getServer(), transponderId, cargoSaveAnchor(level));
        if (restored.isEmpty()) {
            if (!linkedCargoRestoreMissLogged) {
                CreateAeronauticsAutomatedLogistics.debugUi(
                        "Transponder loadLinkedCargoFromSavedData miss id={} pos={} anchor={}",
                        transponderId,
                        worldPosition,
                        cargoSaveAnchor(level)
                );
                linkedCargoRestoreMissLogged = true;
            }
            return false;
        }
        linkedCargo.addAll(restored);
        linkedCargoRestoreMissLogged = false;
        TransponderCargoSavedData.put(level.getServer(), transponderId, cargoSaveAnchor(level), linkedCargo);
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder loadLinkedCargoFromSavedData restored id={} pos={} anchor={} restoredCount={}",
                transponderId,
                worldPosition,
                cargoSaveAnchor(level),
                linkedCargo.size()
        );
        return true;
    }

    private Optional<LinkedCargoEntry> readStoredLinkedCargo(CompoundTag tag) {
        if (tag.contains(LINKED_CARGO_X, Tag.TAG_ANY_NUMERIC)
                && tag.contains(LINKED_CARGO_Y, Tag.TAG_ANY_NUMERIC)
                && tag.contains(LINKED_CARGO_Z, Tag.TAG_ANY_NUMERIC)) {
            LinkedCargoEntry entry = new LinkedCargoEntry(
                    worldPosition.offset(
                            tag.getInt(LINKED_CARGO_X),
                            tag.getInt(LINKED_CARGO_Y),
                            tag.getInt(LINKED_CARGO_Z)
                    ),
                    tag.getBoolean(LINKED_CARGO_ITEM),
                    tag.getBoolean(LINKED_CARGO_FLUID)
            );
            return entry.hasAnyStorage() ? Optional.of(entry) : Optional.empty();
        }
        return LinkedCargoEntry.read(tag);
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
                    "Transponder persistLinkedCargo remove id={} pos={}",
                    transponderId,
                    worldPosition
            );
            TransponderCargoSavedData.remove(level.getServer(), transponderId);
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder persistLinkedCargo put id={} pos={} anchor={} count={}",
                transponderId,
                worldPosition,
                cargoSaveAnchor(level),
                linkedCargo.size()
        );
        TransponderCargoSavedData.put(level.getServer(), transponderId, cargoSaveAnchor(level), linkedCargo);
    }

    private BlockPos cargoSaveAnchor(ServerLevel level) {
        return controllerRef(level)
                .flatMap(VehicleControllerRef::controllerPos)
                .map(BlockPos::immutable)
                .orElse(worldPosition);
    }

    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }
}

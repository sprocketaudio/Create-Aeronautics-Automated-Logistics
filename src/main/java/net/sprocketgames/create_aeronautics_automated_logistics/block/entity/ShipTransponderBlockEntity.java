package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.Optional;
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
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
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
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ScheduleRouteCleanup;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoFailureContext;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class ShipTransponderBlockEntity extends BlockEntity implements MenuProvider, Container, Clearable {
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
    private static final String APPEND_TO_SCHEDULE = "appendToSchedule";
    private static final String OWNED_SCHEDULE = "ownedSchedule";
    private static final String RECORDING_DESTINATION_STATION_ID = "recordingDestinationStationId";
    private static final String RUNTIME_STATUS = "runtimeStatus";
    private static final String SCHEDULE_SLOT = "scheduleSlot";
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
    public static final int INTERNAL_SCHEDULE_SLOT = 0;

    private UUID transponderId = UUID.randomUUID();
    private String shipName = "";
    private Optional<UUID> ownerId = Optional.empty();
    private String ownerName = "";
    private Optional<UUID> runtimeShipId = Optional.empty();
    private Optional<Vec3> lastKnownPosition = Optional.empty();
    private Optional<BlockPos> shipDockPos = Optional.empty();
    private DockLinkStatus shipDockStatus = DockLinkStatus.UNKNOWN;
    private boolean dockOutputActive;
    private boolean appendToSchedule;
    private AirshipSchedule ownedSchedule = AirshipSchedule.empty();
    private Optional<UUID> recordingDestinationStationId = Optional.empty();
    private RouteStatus runtimeStatus = RouteStatus.IDLE;
    private long lastSeenGameTime = -1L;
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<LinkedCargoEntry> linkedCargo = new ArrayList<>();
    private @Nullable LinkedCargoSummary syncedLinkedCargoSummary;
    private int linkedCargoRevision;
    private Optional<CargoFailureContext> syncedCargoFailureContext = Optional.empty();
    private boolean linkedCargoRestoreMissLogged;

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
            refreshRuntimeShip(serverLevel);
            refreshShipDockLink(serverLevel);
            migrateLegacyInstalledSchedule();
            AutomatedLogisticsServices.SCHEDULES.reconcileRuntimeStatus(serverLevel, this);
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
                    runtimeStatus(),
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
                        runtimeStatus(),
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
            runtimeShipId = controller.get().ref().vehicleId();
            lastKnownPosition = Optional.of(controller.get().position());
            lastSeenGameTime = level.getGameTime();
        } else {
            runtimeShipId = Optional.empty();
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
        shipDockStatus = result.status() == DockLinkStatus.LINKED && result.dockPos().isEmpty()
                ? DockLinkStatus.UNKNOWN
                : result.status();
        if (result.status() == DockLinkStatus.LINKED) {
            shipDockPos = result.dockPos();
        } else {
            shipDockPos = Optional.empty();
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

    public ItemStack installedScheduleStack() {
        return getItem(INTERNAL_SCHEDULE_SLOT);
    }

    public boolean hasInstalledSchedule() {
        return true;
    }

    public AirshipSchedule installedSchedule() {
        return ownedSchedule();
    }

    public Component installedScheduleTitle() {
        return Component.literal(ownedSchedule().title());
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

    public int pruneInvalidOwnedSchedule(ServerLevel level) {
        return ScheduleRouteCleanup.pruneOwnedSchedule(level, this);
    }

    public boolean hasOwnedStops() {
        return !ownedSchedule().entries().isEmpty();
    }

    private AirshipSchedule bindScheduleToThisTransponder(AirshipSchedule schedule) {
        AirshipSchedule source = schedule == null ? AirshipSchedule.empty() : schedule;
        return source.withAssignedShip(Optional.of(transponderId), shipName());
    }

    private void migrateLegacyInstalledSchedule() {
        ItemStack stack = installedScheduleStack();
        if (stack.isEmpty() || !stack.is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return;
        }
        if (ownedSchedule.entries().isEmpty()) {
            ownedSchedule = bindScheduleToThisTransponder(AirshipScheduleItem.readSchedule(stack));
        }
        if (AirshipScheduleItem.isLegacyUnboundSchedule(stack)) {
            AirshipScheduleItem.bindScheduleToTransponder(stack, transponderId, shipName());
        }
        setChanged();
    }

    public boolean canInstallSchedule(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return false;
        }
        Optional<UUID> assignedTransponder = AirshipScheduleItem.readSchedule(stack).assignedTransponderId();
        if (assignedTransponder.isPresent()) {
            return assignedTransponder
                .map(transponderId::equals)
                .orElse(false);
        }
        return true;
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
        writeData(tag, registries, false, true, true);
    }

    private void writeData(
            CompoundTag tag,
            HolderLookup.Provider registries,
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
        tag.putBoolean(APPEND_TO_SCHEDULE, appendToSchedule);
        if (includeOwnedSchedule) {
            tag.put(OWNED_SCHEDULE, AirshipScheduleNbtSerializer.write(ownedSchedule()));
        }
        recordingDestinationStationId.ifPresent(id -> tag.putUUID(RECORDING_DESTINATION_STATION_ID, id));
        tag.putString(RUNTIME_STATUS, runtimeStatus.name());
        if (hasInstalledSchedule()) {
            tag.put(SCHEDULE_SLOT, installedScheduleStack().saveOptional(registries));
        }
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
        appendToSchedule = tag.getBoolean(APPEND_TO_SCHEDULE);
        ownedSchedule = tag.contains(OWNED_SCHEDULE, Tag.TAG_COMPOUND)
                ? bindScheduleToThisTransponder(AirshipScheduleNbtSerializer.read(tag.getCompound(OWNED_SCHEDULE)))
                : AirshipSchedule.empty();
        recordingDestinationStationId = tag.hasUUID(RECORDING_DESTINATION_STATION_ID)
                ? Optional.of(tag.getUUID(RECORDING_DESTINATION_STATION_ID))
                : Optional.empty();
        runtimeStatus = readRuntimeStatus(tag);
        setItem(INTERNAL_SCHEDULE_SLOT, tag.contains(SCHEDULE_SLOT, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(SCHEDULE_SLOT))
                : ItemStack.EMPTY);
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
        migrateLegacyInstalledSchedule();
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
        if (level != null && !level.isClientSide && dockOutputActive) {
            dockOutputActive = false;
            setChanged();
        }
        if (level instanceof ServerLevel serverLevel) {
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
        writeData(tag, registries, true, false, false);
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

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return installedScheduleStack().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == INTERNAL_SCHEDULE_SLOT ? items.get(INTERNAL_SCHEDULE_SLOT) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != INTERNAL_SCHEDULE_SLOT) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != INTERNAL_SCHEDULE_SLOT) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != INTERNAL_SCHEDULE_SLOT) {
            return;
        }
        if (!stack.isEmpty() && !stack.is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return;
        }
        ItemStack stored = stack.copyWithCount(Math.min(stack.getCount(), getMaxStackSize()));
        if (!stored.isEmpty() && AirshipScheduleItem.readSchedule(stored).assignedTransponderId().isEmpty()) {
            AirshipScheduleItem.bindScheduleToTransponder(stored, transponderId, shipName());
        }
        items.set(slot, stored);
        setChanged();
    }

    @Override
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

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INTERNAL_SCHEDULE_SLOT && canInstallSchedule(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void clearContent() {
        items.set(INTERNAL_SCHEDULE_SLOT, ItemStack.EMPTY);
        setChanged();
    }
}

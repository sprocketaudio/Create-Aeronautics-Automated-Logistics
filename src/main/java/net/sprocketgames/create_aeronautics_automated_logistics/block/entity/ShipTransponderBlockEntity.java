package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockDiscoveryResult;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingConnectorDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class ShipTransponderBlockEntity extends BlockEntity implements MenuProvider, Container, Clearable {
    private static final String DATA_VERSION = "dataVersion";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SHIP_NAME = "shipName";
    private static final String RUNTIME_SHIP_ID = "runtimeShipId";
    private static final String LAST_X = "lastKnownX";
    private static final String LAST_Y = "lastKnownY";
    private static final String LAST_Z = "lastKnownZ";
    private static final String LAST_SEEN = "lastSeenGameTime";
    private static final String SHIP_DOCK_POS = "shipDockPos";
    private static final String SHIP_DOCK_STATUS = "shipDockStatus";
    private static final String DOCK_OUTPUT_ACTIVE = "dockOutputActive";
    private static final String SCHEDULE_SLOT = "scheduleSlot";
    private static final int CURRENT_DATA_VERSION = 1;
    private static final int REFRESH_INTERVAL_TICKS = 40;
    public static final int INTERNAL_SCHEDULE_SLOT = 0;

    private UUID transponderId = UUID.randomUUID();
    private String shipName = "";
    private Optional<UUID> runtimeShipId = Optional.empty();
    private Optional<Vec3> lastKnownPosition = Optional.empty();
    private Optional<BlockPos> shipDockPos = Optional.empty();
    private DockLinkStatus shipDockStatus = DockLinkStatus.UNKNOWN;
    private boolean dockOutputActive;
    private long lastSeenGameTime = -1L;
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

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
        }
        return new ShipTransponderMenu(containerId, playerInventory, worldPosition);
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

    public void setDockOutputActive(boolean active) {
        if (dockOutputActive == active) {
            return;
        }
        dockOutputActive = active;
        setChanged();
        syncPoweredBlockState();
        notifyRedstoneNeighbors();
    }

    public void setShipName(String shipName) {
        String sanitized = IdentityNames.sanitize(shipName);
        this.shipName = sanitized.isBlank() ? IdentityNames.defaultShipName(transponderId) : sanitized;
        setChanged();
    }

    public Optional<VehicleControllerRef> controllerRef(ServerLevel level) {
        refreshRuntimeShip(level);
        return runtimeShipId.map(shipId -> new VehicleControllerRef(
                SableSubLevelVehicleController.TYPE,
                level.dimension(),
                Optional.of(shipId),
                Optional.of(worldPosition)
        ));
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
        ShipTransponderRegistry.register(snapshot(level));
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
        return result;
    }

    public DockDiscoveryResult setShipDockLink(ServerLevel level, BlockPos dockPos) {
        refreshRuntimeShip(level);
        if (!DockingConnectorDiscovery.isDock(level, dockPos)) {
            shipDockPos = Optional.empty();
            shipDockStatus = DockLinkStatus.INVALID;
            setChanged();
            return DockDiscoveryResult.invalid();
        }
        if (!dockBelongsToResolvedShip(level, dockPos)) {
            shipDockPos = Optional.empty();
            shipDockStatus = DockLinkStatus.INVALID;
            setChanged();
            return DockDiscoveryResult.invalid();
        }
        shipDockPos = Optional.of(dockPos.immutable());
        shipDockStatus = DockLinkStatus.LINKED;
        setChanged();
        return DockDiscoveryResult.linked(dockPos.immutable());
    }

    public void clearShipDockLink() {
        shipDockPos = Optional.empty();
        shipDockStatus = DockLinkStatus.MISSING;
        setChanged();
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
        Optional<VehicleControllerRef> controllerRef = runtimeShipId.map(shipId -> new VehicleControllerRef(
                SableSubLevelVehicleController.TYPE,
                level.dimension(),
                Optional.of(shipId),
                Optional.of(worldPosition)
        ));
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
        return installedScheduleStack().is(ModItems.AIRSHIP_SCHEDULE.get()) && !installedScheduleStack().isEmpty();
    }

    public AirshipSchedule installedSchedule() {
        return hasInstalledSchedule() ? AirshipScheduleItem.readSchedule(installedScheduleStack()) : AirshipSchedule.empty();
    }

    public Component installedScheduleTitle() {
        if (!hasInstalledSchedule()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_schedule");
        }
        return Component.literal(installedSchedule().title());
    }

    private void migrateLegacyInstalledSchedule() {
        ItemStack stack = installedScheduleStack();
        if (stack.isEmpty() || !AirshipScheduleItem.isLegacyUnboundSchedule(stack)) {
            return;
        }
        AirshipScheduleItem.bindScheduleToTransponder(stack, transponderId, shipName());
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
        return AirshipScheduleItem.isLegacyUnboundSchedule(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(tag, registries);
    }

    private void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.putUUID(TRANSPONDER_ID, transponderId);
        tag.putString(SHIP_NAME, shipName());
        runtimeShipId.ifPresent(id -> tag.putUUID(RUNTIME_SHIP_ID, id));
        shipDockPos.ifPresent(pos -> tag.put(SHIP_DOCK_POS, NbtUtils.writeBlockPos(pos)));
        DockLinkStatus savedDockStatus = shipDockStatus == DockLinkStatus.LINKED && shipDockPos.isEmpty()
                ? DockLinkStatus.UNKNOWN
                : shipDockStatus;
        tag.putString(SHIP_DOCK_STATUS, savedDockStatus.name());
        tag.putBoolean(DOCK_OUTPUT_ACTIVE, dockOutputActive);
        if (hasInstalledSchedule()) {
            tag.put(SCHEDULE_SLOT, installedScheduleStack().saveOptional(registries));
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
        runtimeShipId = tag.hasUUID(RUNTIME_SHIP_ID)
                ? Optional.of(tag.getUUID(RUNTIME_SHIP_ID))
                : Optional.empty();
        shipDockPos = tag.contains(SHIP_DOCK_POS)
                ? NbtUtils.readBlockPos(tag, SHIP_DOCK_POS)
                : Optional.empty();
        shipDockStatus = readDockStatus(tag);
        dockOutputActive = tag.getBoolean(DOCK_OUTPUT_ACTIVE);
        setItem(INTERNAL_SCHEDULE_SLOT, tag.contains(SCHEDULE_SLOT, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(SCHEDULE_SLOT))
                : ItemStack.EMPTY);
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
        if (level != null && !level.isClientSide && dockOutputActive) {
            dockOutputActive = false;
            setChanged();
        }
        syncPoweredBlockState();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeData(tag, registries);
        return tag;
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
        if (!stored.isEmpty() && AirshipScheduleItem.isLegacyUnboundSchedule(stored)) {
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

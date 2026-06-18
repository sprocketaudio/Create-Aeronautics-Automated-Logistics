package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import com.simibubi.create.AllSoundEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncStationMenuStatePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncStationRouteChoicesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoFailureContext;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackOperationResult;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingFailure;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingSession;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteOperationResult;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public class AirshipStationMenu extends AbstractContainerMenu {
    public static final int ACTION_SELECT_SHIP = 0;
    public static final int ACTION_SELECT_PREVIOUS_SHIP = 17;
    public static final int ACTION_START_SEGMENT_RECORDING = 1;
    public static final int ACTION_FINISH_SEGMENT_RECORDING = 2;
    public static final int ACTION_RUN_SCHEDULE = 3;
    public static final int ACTION_STOP_SCHEDULE = 4;
    public static final int ACTION_MARK_STOP = 5;
    public static final int ACTION_CYCLE_LAST_STOP_WAIT = 6;
    public static final int ACTION_DECREASE_LAST_STOP_WAIT = 7;
    public static final int ACTION_INCREASE_LAST_STOP_WAIT = 8;
    public static final int ACTION_RECORD_OR_FINISH_SEGMENT = 9;
    public static final int ACTION_FINISH_RECORDING = 10;
    public static final int ACTION_AUTO_SELECT_CLOSEST_SHIP = 11;
    public static final int ACTION_BEGIN_LINK_DOCK = 12;
    public static final int ACTION_CLEAR_DOCK_LINK = 13;
    public static final int ACTION_LINK_CARGO = 14;
    public static final int ACTION_CLEAR_CARGO = 15;
    public static final int ACTION_SHOW_CARGO = 16;
    public static final int ACTION_SELECT_SHIP_BASE = 1000;
    public static final int ACTION_PREVIEW_ROUTE_BASE = 2000;
    private static final int WAIT_ADJUST_TICKS = 20 * 5;

    private final BlockPos stationPos;
    private final Optional<UUID> initialSelectedTransponderId;
    private final String initialSelectedShipName;
    private final int initialCargoRevision;
    private final LinkedCargoSummary initialCargoSummary;
    private final List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries;
    private final Optional<CargoFailureContext> initialCargoFailureContext;
    private final Player menuPlayer;
    private List<RouteChoiceSummary> clientRouteChoices;
    private ClientState clientState;
    private int lastSyncedClientStateHash;
    private BlockPos clientResolvedStationPos;

    public AirshipStationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readOpenData(buffer));
    }

    private AirshipStationMenu(int containerId, Inventory playerInventory, OpenData data) {
        this(
                containerId,
                playerInventory,
                data.stationPos(),
                data.initialSelectedTransponderId(),
                data.initialSelectedShipName(),
                data.initialCargoRevision(),
                data.initialCargoSummary(),
                data.initialLinkedCargoEntries(),
                data.initialCargoFailureContext(),
                data.clientState().routeChoices()
        );
        setClientState(data.clientState());
    }

    public AirshipStationMenu(int containerId, Inventory playerInventory, BlockPos stationPos) {
        this(containerId, playerInventory, stationPos, Optional.empty(), "", 0, new LinkedCargoSummary(0, 0, 0, 0, 0), List.of(), Optional.empty(), List.of());
    }

    private static OpenData readOpenData(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < Long.BYTES) {
            return new OpenData(
                    BlockPos.ZERO,
                    Optional.empty(),
                    "",
                    0,
                    new LinkedCargoSummary(0, 0, 0, 0, 0),
                    List.of(),
                    Optional.empty(),
                    ClientState.empty(Optional.empty(), "", Optional.empty(), "", List.of())
            );
        }
        BlockPos stationPos = buffer.readBlockPos();
        Optional<UUID> selectedTransponderId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        String selectedShipName = buffer.readUtf(64);
        int cargoRevision = buffer.readableBytes() >= Integer.BYTES ? buffer.readInt() : 0;
        LinkedCargoSummary cargoSummary = buffer.readableBytes() >= Integer.BYTES * 5
                ? new LinkedCargoSummary(
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readInt()
                )
                : new LinkedCargoSummary(0, 0, 0, 0, 0);
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> linkedCargoEntries =
                buffer.readableBytes() >= Integer.BYTES
                        ? IntStream.range(0, buffer.readInt())
                                .mapToObj(ignored -> new net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry(
                                        buffer.readBlockPos(),
                                        buffer.readBoolean(),
                                        buffer.readBoolean()
                                ))
                                .toList()
                        : List.of();
        Optional<CargoFailureContext> cargoFailureContext = ShipTransponderMenu.readCargoFailureContext(buffer);
        List<RouteChoiceSummary> routeChoices = readRouteChoiceSummaries(buffer);
        ClientState clientState = buffer.readableBytes() > 0
                ? readClientState(buffer)
                : ClientState.empty(Optional.empty(), "", selectedTransponderId, selectedShipName, routeChoices);
        return new OpenData(
                stationPos,
                selectedTransponderId,
                selectedShipName,
                cargoRevision,
                cargoSummary,
                linkedCargoEntries,
                cargoFailureContext,
                clientState
        );
    }

    public AirshipStationMenu(
            int containerId,
            Inventory playerInventory,
            BlockPos stationPos,
            Optional<UUID> initialSelectedTransponderId,
            String initialSelectedShipName,
            int initialCargoRevision,
            LinkedCargoSummary initialCargoSummary,
            List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries,
            Optional<CargoFailureContext> initialCargoFailureContext,
            List<RouteChoiceSummary> initialRouteChoices
    ) {
        super(ModMenus.AIRSHIP_STATION.get(), containerId);
        this.stationPos = stationPos;
        this.initialSelectedTransponderId = initialSelectedTransponderId == null ? Optional.empty() : initialSelectedTransponderId;
        this.initialSelectedShipName = initialSelectedShipName == null ? "" : initialSelectedShipName;
        this.initialCargoRevision = initialCargoRevision;
        this.initialCargoSummary = initialCargoSummary == null ? new LinkedCargoSummary(0, 0, 0, 0, 0) : initialCargoSummary;
        this.initialLinkedCargoEntries = initialLinkedCargoEntries == null ? List.of() : List.copyOf(initialLinkedCargoEntries);
        this.initialCargoFailureContext = initialCargoFailureContext == null ? Optional.empty() : initialCargoFailureContext;
        this.menuPlayer = playerInventory.player;
        this.clientRouteChoices = initialRouteChoices == null ? List.of() : List.copyOf(initialRouteChoices);
        this.clientState = ClientState.empty(Optional.empty(), "", this.initialSelectedTransponderId, this.initialSelectedShipName, this.clientRouteChoices);
        this.clientResolvedStationPos = stationPos;
    }

    public void setClientRouteChoices(List<RouteChoiceSummary> routeChoices) {
        this.clientRouteChoices = routeChoices == null ? List.of() : List.copyOf(routeChoices);
        this.clientState = this.clientState.withRouteChoices(this.clientRouteChoices);
    }

    public void setClientState(ClientState state) {
        this.clientState = state == null ? ClientState.empty(Optional.empty(), "", Optional.empty(), "", List.of()) : state;
        this.clientRouteChoices = this.clientState.routeChoices();
    }

    public void applyClientStateSync(BlockPos syncedStationPos, ClientState state) {
        updateClientResolvedStationPos(syncedStationPos);
        setClientState(state);
    }

    public void applyClientRouteChoicesSync(BlockPos syncedStationPos, List<RouteChoiceSummary> routeChoices) {
        updateClientResolvedStationPos(syncedStationPos);
        setClientRouteChoices(routeChoices);
    }

    public static void writeRouteChoiceSummaries(FriendlyByteBuf buffer, List<RouteChoiceSummary> routeChoices) {
        buffer.writeInt(routeChoices.size());
        for (RouteChoiceSummary route : routeChoices) {
            buffer.writeUUID(route.id());
            buffer.writeUUID(route.startStationId());
            buffer.writeUtf(route.startStationName(), 128);
            buffer.writeUUID(route.endStationId());
            buffer.writeUtf(route.endStationName(), 128);
            buffer.writeUUID(route.transponderId());
            buffer.writeUtf(route.shipName(), 128);
            buffer.writeResourceLocation(route.dimension().location());
            buffer.writeInt(route.pointCount());
            buffer.writeLong(route.createdEpochMillis());
        }
    }

    public static List<RouteChoiceSummary> readRouteChoiceSummaries(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() < Integer.BYTES) {
            return List.of();
        }
        int count = buffer.readInt();
        List<RouteChoiceSummary> routes = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            routes.add(new RouteChoiceSummary(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(128),
                    buffer.readUUID(),
                    buffer.readUtf(128),
                    buffer.readUUID(),
                    buffer.readUtf(128),
                    ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, buffer.readResourceLocation()),
                    buffer.readInt(),
                    buffer.readLong()
            ));
        }
        return List.copyOf(routes);
    }

    public static void writeClientState(FriendlyByteBuf buffer, ClientState state) {
        buffer.writeBoolean(state.stationId().isPresent());
        state.stationId().ifPresent(buffer::writeUUID);
        buffer.writeUtf(state.stationName(), 128);
        buffer.writeBoolean(state.canControlStation());
        buffer.writeBoolean(state.selectedTransponderId().isPresent());
        state.selectedTransponderId().ifPresent(buffer::writeUUID);
        buffer.writeUtf(state.selectedShipName(), 128);
        buffer.writeInt(state.shipChoices().size());
        for (ShipChoiceSnapshot choice : state.shipChoices()) {
            buffer.writeUUID(choice.transponderId());
            buffer.writeUtf(choice.shipName(), 128);
            buffer.writeUtf(choice.statusText(), 128);
            buffer.writeInt(choice.statusColor());
            buffer.writeBoolean(choice.selected());
        }
        writeRouteChoiceSummaries(buffer, state.routeChoices());
        writeSelectedShipState(buffer, state.selectedShipState());
        ShipTransponderMenu.writeViewSnapshot(buffer, state.dockView());
        ShipTransponderMenu.writeViewSnapshot(buffer, state.cargoView());
    }

    public static ClientState readClientState(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() <= 0) {
            return ClientState.empty(Optional.empty(), "", Optional.empty(), "", List.of());
        }
        Optional<UUID> stationId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        String stationName = buffer.readUtf(128);
        boolean canControlStation = buffer.readableBytes() > 0 && buffer.readBoolean();
        Optional<UUID> selectedTransponderId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        String selectedShipName = buffer.readUtf(128);
        int shipChoiceCount = buffer.readInt();
        List<ShipChoiceSnapshot> shipChoices = new ArrayList<>(Math.max(0, shipChoiceCount));
        for (int i = 0; i < shipChoiceCount; i++) {
            shipChoices.add(new ShipChoiceSnapshot(
                    buffer.readUUID(),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readInt(),
                    buffer.readBoolean()
            ));
        }
        List<RouteChoiceSummary> routeChoices = readRouteChoiceSummaries(buffer);
        SelectedShipState selectedShipState = readSelectedShipState(buffer);
        ShipTransponderMenu.ViewSnapshot dockView = buffer.readableBytes() > 0
                ? ShipTransponderMenu.readViewSnapshot(buffer, ShipTransponderMenu.ViewSnapshot.dockNone())
                : ShipTransponderMenu.ViewSnapshot.dockNone();
        ShipTransponderMenu.ViewSnapshot cargoView = buffer.readableBytes() > 0
                ? ShipTransponderMenu.readViewSnapshot(buffer, ShipTransponderMenu.ViewSnapshot.cargoNone())
                : ShipTransponderMenu.ViewSnapshot.cargoNone();
        return new ClientState(
                stationId,
                stationName,
                canControlStation,
                selectedTransponderId,
                selectedShipName,
                List.copyOf(shipChoices),
                routeChoices,
                selectedShipState,
                dockView,
                cargoView
        );
    }

    private static void writeSelectedShipState(FriendlyByteBuf buffer, SelectedShipState state) {
        buffer.writeBoolean(state.present());
        buffer.writeBoolean(state.recording());
        buffer.writeBoolean(state.scheduleActive());
        buffer.writeEnum(state.runtimeStatus());
        buffer.writeBoolean(state.dockOutputActive());
        buffer.writeBoolean(state.failure().isPresent());
        state.failure().ifPresent(buffer::writeEnum);
        buffer.writeBoolean(state.hasOwnedStops());
        buffer.writeBoolean(state.readyRoute());
        buffer.writeBoolean(state.canRun());
        buffer.writeBoolean(state.canStop());
    }

    private static SelectedShipState readSelectedShipState(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() <= 0) {
            return SelectedShipState.empty();
        }
        boolean present = buffer.readBoolean();
        boolean recording = buffer.readBoolean();
        boolean scheduleActive = buffer.readBoolean();
        RouteStatus runtimeStatus = buffer.readEnum(RouteStatus.class);
        boolean dockOutputActive = buffer.readBoolean();
        Optional<PlaybackFailure> failure = buffer.readBoolean() ? Optional.of(buffer.readEnum(PlaybackFailure.class)) : Optional.empty();
        boolean hasOwnedStops = buffer.readBoolean();
        boolean readyRoute = buffer.readBoolean();
        boolean canRun = buffer.readBoolean();
        boolean canStop = buffer.readBoolean();
        return new SelectedShipState(
                present,
                recording,
                scheduleActive,
                runtimeStatus,
                dockOutputActive,
                failure,
                hasOwnedStops,
                readyRoute,
                canRun,
                canStop
        );
    }

    public BlockPos stationPos() {
        return resolvedStationPos();
    }

    private BlockPos resolvedStationPos() {
        if (menuPlayer.level().isClientSide && stationPos.equals(BlockPos.ZERO) && clientResolvedStationPos != null) {
            return clientResolvedStationPos;
        }
        return stationPos;
    }

    private void updateClientResolvedStationPos(BlockPos syncedStationPos) {
        if (!menuPlayer.level().isClientSide || syncedStationPos == null || syncedStationPos.equals(BlockPos.ZERO)) {
            return;
        }
        clientResolvedStationPos = syncedStationPos.immutable();
    }

    private BlockPos lookupStationPos(Player player) {
        return player instanceof ServerPlayer ? stationPos : resolvedStationPos();
    }

    public String stationName(Player player) {
        if (!(player instanceof ServerPlayer) && !clientState.stationName().isBlank()) {
            return clientState.stationName();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return "";
        }
        return station.stationName();
    }

    public Component stationIdText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.literal("-");
        }
        return Component.literal(IdentityNames.shortId(station.stationId()));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(serverPlayer.serverLevel().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }

        if (id >= ACTION_SELECT_SHIP_BASE) {
            if (id >= ACTION_PREVIEW_ROUTE_BASE) {
                if (!StationPermissionService.ensureCanControl(serverPlayer, station)) {
                    return false;
                }
                return previewRouteByIndex(serverPlayer, station, id - ACTION_PREVIEW_ROUTE_BASE);
            }
            return selectShipByIndex(serverPlayer, station, id - ACTION_SELECT_SHIP_BASE);
        }
        if (requiresStationControl(id) && !StationPermissionService.ensureCanControl(serverPlayer, station)) {
            return false;
        }
        if (requiresSelectedShipControl(id) && !canControlSelectedShip(serverPlayer, station)) {
            return false;
        }

        return switch (id) {
            case ACTION_SELECT_SHIP -> selectNextShip(serverPlayer, station);
            case ACTION_SELECT_PREVIOUS_SHIP -> selectPreviousShip(serverPlayer, station);
            case ACTION_START_SEGMENT_RECORDING -> startSegmentRecording(serverPlayer, station);
            case ACTION_FINISH_SEGMENT_RECORDING -> finishSegmentRecording(serverPlayer, station);
            case ACTION_RUN_SCHEDULE -> runSchedule(serverPlayer, station);
            case ACTION_STOP_SCHEDULE -> stopSchedule(serverPlayer, station);
            case ACTION_MARK_STOP -> markStop(serverPlayer, station);
            case ACTION_CYCLE_LAST_STOP_WAIT -> cycleLastStopWait(serverPlayer, station);
            case ACTION_DECREASE_LAST_STOP_WAIT -> adjustLastStopWait(serverPlayer, station, -WAIT_ADJUST_TICKS);
            case ACTION_INCREASE_LAST_STOP_WAIT -> adjustLastStopWait(serverPlayer, station, WAIT_ADJUST_TICKS);
            case ACTION_RECORD_OR_FINISH_SEGMENT -> recordOrFinishSegment(serverPlayer, station);
            case ACTION_FINISH_RECORDING -> finishRecordingSession(serverPlayer, station);
            case ACTION_AUTO_SELECT_CLOSEST_SHIP -> autoSelectClosestShip(serverPlayer, station);
            case ACTION_BEGIN_LINK_DOCK -> beginLinkDock(serverPlayer, station);
            case ACTION_CLEAR_DOCK_LINK -> clearDockLink(serverPlayer, station);
            case ACTION_LINK_CARGO -> linkCargo(serverPlayer, station);
            case ACTION_CLEAR_CARGO -> clearCargo(serverPlayer, station);
            case ACTION_SHOW_CARGO -> true;
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
                stationPos.getX() + 0.5D,
                stationPos.getY() + 0.5D,
                stationPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    public Component statusText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return selectedShipRuntimeStateText(clientState.selectedShipState());
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_missing_station");
        }
        RouteStatus status = station.isRecording() ? RouteStatus.RECORDING : station.status();
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status." + status.name().toLowerCase(Locale.ROOT));
    }

    public Component failureText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        if (station.isRecording() || station.status() == RouteStatus.RECORDING) {
            return Component.empty();
        }
        Optional<Component> specific = station.failureReason()
                .map(reason -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.failure",
                        failureReasonText(reason, station.selectedTransponderId())
                ));
        if (specific.isPresent()) {
            return specific.get();
        }
        if (station.status() == RouteStatus.INVALID_ROUTE) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure",
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason.invalid_route_data")
            );
        }
        return Component.empty();
    }

    public List<Component> failureTooltipLines(Player player) {
        return statusTooltipLines(player);
    }

    public boolean canControlStationLocally(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.canControlStation();
        }
        if (!AutomatedLogisticsConfig.RESTRICT_TRANSPONDER_CONTROL_TO_OWNER.get()) {
            return true;
        }
        if (!(player.level().getBlockEntity(lookupStationPos(player)) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return StationPermissionService.canControl((ServerPlayer) player, station);
    }

    public List<Component> statusTooltipLines(Player player) {
        if (!(player instanceof ServerPlayer)) {
            SelectedShipState state = clientState.selectedShipState();
            if (!state.present()) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status")
                                .withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.no_route.2")
                                .withStyle(ChatFormatting.GRAY)
                );
            }
            return selectedShipRuntimeStatusTooltip(state);
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_missing_station")
                            .withStyle(ChatFormatting.RED),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.missing_station.detail")
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        Optional<ShipTransponderBlockEntity> selectedTransponder = selectedTransponder(player, station);
        if (selectedTransponder.isEmpty()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status")
                            .withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.no_route.2")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        return selectedShipRuntimeStatusTooltip(player, selectedTransponder.get());
    }

    public boolean isRecording(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedShipState().recording();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        Optional<ShipTransponderBlockEntity> selectedTransponder = selectedTransponder(player, station);
        return selectedTransponder.map(transponder -> transponder.recordingDestinationStationId().isPresent()).orElse(false);
    }

    public List<Vec3> previewPoints(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Collections.emptyList();
        }
        return station.recordedRoute()
                .map(route -> route.points().stream().map(point -> point.position()).toList())
                .orElse(Collections.emptyList());
    }

    public Component stopSummary(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        if (station.isRecording()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.stops_recording",
                    station.recordingStops().size()
            );
        }
        return station.recordedRoute()
                .map(route -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.stops_recorded",
                        route.stops().size()
                ))
                .orElse(Component.empty());
    }

    public Component selectedShipText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedTransponderId().isPresent()
                    ? Component.literal(clientState.selectedShipName())
                    : Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none");
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return initialSelectedTransponderId.isPresent()
                    ? Component.literal(initialSelectedShipName)
                    : Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none");
        }
        return selectedTransponderId(station)
                .map(id -> Component.literal(selectedShipName(station)))
                .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none"));
    }

    public List<ShipChoice> shipChoices(Player player) {
        if (!(player instanceof ServerPlayer) && !clientState.shipChoices().isEmpty()) {
            return clientState.shipChoices().stream()
                    .map(choice -> new ShipChoice(
                            choice.transponderId(),
                            Component.literal(choice.shipName()),
                            Component.literal(choice.statusText()),
                            choice.statusColor(),
                            choice.selected()
                    ))
                    .toList();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        List<ShipTransponderSnapshot> ships = player instanceof ServerPlayer serverPlayer
                ? sortedShips(serverPlayer, station)
                : liveKnownShips(player.level()).stream()
                        .sorted(Comparator
                                .comparingDouble((ShipTransponderSnapshot snapshot) -> distanceToStationSqr(station, snapshot))
                                .thenComparing(snapshot -> snapshot.transponderId().toString()))
                        .toList();
        Optional<UUID> selected = selectedTransponderId(station);
        double landingRadius = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        double landingRadiusSqr = landingRadius * landingRadius;
        return ships.stream().map(snapshot -> {
            boolean selectedShip = selected.map(snapshot.transponderId()::equals).orElse(false);
            boolean available = snapshot.controllerRef().isPresent();
            Vec3 shipPos = snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos()));
            double distance = Math.sqrt(station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z));
            boolean inRange = station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z) <= landingRadiusSqr;
            Component statusText = available ? Component.literal((int) distance + "m") : Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.not_found"
            );
            int statusColor = available ? (inRange ? 0xFF8BE77A : 0xFFFFC66E) : 0xFFFF8C8C;
            return new ShipChoice(
                    snapshot.transponderId(),
                    Component.literal(snapshot.shipName()),
                    statusText,
                    statusColor,
                    selectedShip
            );
        }).toList();
    }

    public Optional<ShipChoice> selectedShipChoice(Player player) {
        return shipChoices(player).stream().filter(ShipChoice::selected).findFirst();
    }

    public boolean hasSelectedShip(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedTransponderId().isPresent();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return initialSelectedTransponderId.isPresent();
        }
        return selectedTransponderId(station).isPresent();
    }

    public Component segmentSummary(Player player) {
        if (!(player instanceof ServerPlayer)) {
            long outgoing = clientState.stationId()
                    .map(stationId -> clientRouteChoices.stream()
                            .filter(route -> route.startStationId().equals(stationId))
                            .count())
                    .orElse(0L);
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.segments",
                    outgoing,
                    clientRouteChoices.size()
            );
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.segments",
                outgoing,
                station.routeSegments().size()
        );
    }

    public Component outgoingSegmentsText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            long outgoing = clientState.stationId()
                    .map(stationId -> clientRouteChoices.stream()
                            .filter(route -> route.startStationId().equals(stationId))
                            .count())
                    .orElse(0L);
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here",
                    outgoing
            );
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here",
                outgoing
        );
    }

    public Component panelStatusText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return selectedShipRuntimeStateText(clientState.selectedShipState());
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_missing_station");
        }
        Optional<ShipTransponderBlockEntity> selectedTransponder = selectedTransponder(player, station);
        if (selectedTransponder.isEmpty()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_stops_status");
        }
        return selectedShipRuntimeStateText(player, selectedTransponder.get());
    }

    public int panelStatusColor(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return selectedShipRuntimeStateColor(clientState.selectedShipState());
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0xAFC7DE;
        }
        Optional<ShipTransponderBlockEntity> selectedTransponder = selectedTransponder(player, station);
        if (selectedTransponder.isEmpty()) {
            return 0xFFFFD98A;
        }
        ShipTransponderBlockEntity transponder = selectedTransponder.get();
        if (transponder.scheduleActive()) {
            RouteStatus runtimeStatus = transponder.runtimeStatus();
            if (runtimeStatus == RouteStatus.HELD) {
                return 0xFFE7C46E;
            }
            if (runtimeStatus == RouteStatus.HELD_FAULTED) {
                return 0xFFFFB4B4;
            }
            return 0xFFE7C46E;
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return 0xFFFFB4B4;
        }
        if (!transponder.hasOwnedStops()) {
            return 0xFFFFD98A;
        }
        if (!hasReadySelectedRoutePlan(player, transponder)) {
            return 0xFFFFD98A;
        }
        return 0xAFC7DE;
    }

    public boolean selectedShipScheduleRunning(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedShipState().scheduleActive();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return selectedTransponder(player, station)
                .map(ShipTransponderBlockEntity::scheduleActive)
                .orElse(false);
    }

    public boolean selectedShipScheduleHeld(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedShipState().scheduleActive()
                    && clientState.selectedShipState().runtimeStatus() == RouteStatus.HELD;
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return selectedTransponder(player, station)
                .map(ShipTransponderBlockEntity::scheduleHeld)
                .orElse(false);
    }

    public boolean canRunSelectedShip(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedShipState().canRun();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        if (!canControlStationLocally(player) || station.isRecording()) {
            return false;
        }
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponder(player, station);
        if (transponder.isEmpty() || !canControlSelectedShipLocally(player, transponder.get())) {
            return false;
        }
        if (transponder.get().scheduleActive() && !transponder.get().scheduleHeld()) {
            return false;
        }
        if (!transponder.get().hasOwnedStops()) {
            return false;
        }
        return hasReadySelectedRoutePlan(player, transponder.get());
    }

    public boolean canStopSelectedShip(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.selectedShipState().canStop();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        if (!canControlStationLocally(player)) {
            return false;
        }
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponder(player, station);
        return transponder.isPresent()
                && canControlSelectedShipLocally(player, transponder.get())
                && transponder.get().scheduleActive();
    }

    public Component routesFromHereText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            int outgoing = routesFromHereCount(player);
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here_compact",
                    outgoing
            );
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = (int) localRoutes(station, player).stream()
                .filter(segment -> segment.startStationId().equals(station.stationId()))
                .count();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here_compact",
                outgoing
        );
    }

    public int routesFromHereCount(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.stationId()
                    .map(stationId -> (int) clientRouteChoices.stream()
                            .filter(segment -> segment.startStationId().equals(stationId))
                            .count())
                    .orElse(0);
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0;
        }
        return (int) localRoutes(station, player).stream()
                .filter(segment -> segment.startStationId().equals(station.stationId()))
                .count();
    }

    public Component routesToHereText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            int incoming = routesToHereCount(player);
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.routes_to_here_compact",
                    incoming
            );
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int incoming = localRoutes(station, player).stream()
                .filter(segment -> segment.endStationId().equals(station.stationId()))
                .toList()
                .size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_to_here_compact",
                incoming
        );
    }

    public int routesToHereCount(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.stationId()
                    .map(stationId -> (int) clientRouteChoices.stream()
                            .filter(segment -> segment.endStationId().equals(stationId))
                            .count())
                    .orElse(0);
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0;
        }
        return (int) localRoutes(station, player).stream()
                .filter(segment -> segment.endStationId().equals(station.stationId()))
                .count();
    }

    public Component dockText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return dockCompactText(player);
        }
        return dockCompactText(player);
    }

    public Component dockCompactText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return Component.literal(clientState.dockView().text());
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.dock.not_connected");
        }
        DockLinkStatus status = station.groundDockStatus();
        if (status == DockLinkStatus.LINKED && station.groundDockPos().isEmpty()) {
            status = DockLinkStatus.UNKNOWN;
        } else if (status == DockLinkStatus.AMBIGUOUS) {
            status = DockLinkStatus.INVALID;
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact."
                        + status.name().toLowerCase(Locale.ROOT)
        );
    }

    public List<Component> dockTooltip(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return componentsFromSnapshot(clientState.dockView().tooltip());
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        DockLinkStatus status = station.groundDockStatus();
        if (status == DockLinkStatus.LINKED && station.groundDockPos().isEmpty()) {
            status = DockLinkStatus.UNKNOWN;
        } else if (status == DockLinkStatus.AMBIGUOUS) {
            status = DockLinkStatus.INVALID;
        }
        Component statusLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.status",
                dockCompactText(player)
        );
        Component outputLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.redstone_output",
                Component.translatable(
                        station.dockOutputActive()
                                ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                                : "gui.create_aeronautics_automated_logistics.dock.output.off"
                )
        );
        Component positionLine = station.groundDockPos()
                .map(pos -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.position",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                ))
                .orElseGet(() -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.position_unknown"
                ));
        Component hintLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock.hover."
                        + status.name().toLowerCase(Locale.ROOT)
        );
        return List.of(
                statusLine.copy().withStyle(status == DockLinkStatus.LINKED ? ChatFormatting.GRAY : ChatFormatting.YELLOW),
                outputLine.copy().withStyle(station.dockOutputActive() ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                positionLine.copy().withStyle(ChatFormatting.DARK_GRAY),
                hintLine.copy().withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    public int dockStatusColor(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.dockView().color();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0xFFAFC7DE;
        }
        DockLinkStatus status = station.groundDockStatus();
        if (status == DockLinkStatus.LINKED && station.groundDockPos().isEmpty()) {
            status = DockLinkStatus.UNKNOWN;
        }
        return switch (status) {
            case LINKED -> 0xFFAFC7DE;
            case UNKNOWN, MISSING -> 0xFFFFD98A;
            case AMBIGUOUS, INVALID -> 0xFFFFB4B4;
        };
    }

    public Component cargoCompactText(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return Component.literal(clientState.cargoView().text());
        }
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.cargo.none");
        }
        if (summary.staleLinks() > 0) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.cargo.compact.partial",
                    summary.validLinks(),
                    summary.totalLinks()
            );
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.cargo.compact.linked",
                summary.totalLinks()
        );
    }

    public List<Component> cargoTooltip(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return componentsFromSnapshot(clientState.cargoView().tooltip());
        }
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo.none").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.none").withStyle(ChatFormatting.GRAY)
            );
        }
        String hintKey = summary.staleLinks() > 0
                ? "gui.create_aeronautics_automated_logistics.cargo.hover.partial"
                : "gui.create_aeronautics_automated_logistics.cargo.hover.linked";
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.status", cargoCompactText(player))
                        .withStyle(summary.staleLinks() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                Component.translatable(
                        "gui.create_aeronautics_automated_logistics.cargo.hover.contents",
                        summary.itemLinks(),
                        summary.fluidLinks()
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "gui.create_aeronautics_automated_logistics.cargo.hover.validity",
                        summary.validLinks(),
                        summary.staleLinks()
                ).withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    public int cargoStatusColor(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientState.cargoView().color();
        }
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return 0xFFFFD98A;
        }
        return summary.staleLinks() > 0 ? 0xFFFFD98A : 0xFFAFC7DE;
    }

    public boolean hasLinkedCargo(Player player) {
        if (!(player instanceof ServerPlayer)) {
            if (clientState.cargoView().active()) {
                return true;
            }
        }
        return resolveCargoSummary(player).hasLinks();
    }

    public boolean hasLinkedDock(Player player) {
        if (!(player instanceof ServerPlayer)) {
            if (clientState.dockView().active()) {
                return true;
            }
        }
        if (!(player.level().getBlockEntity(lookupStationPos(player)) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return station.groundDockPos().isPresent();
    }

    public boolean isCargoLinkPending(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && CargoLinkInteractionService.hasPendingStationLink(serverPlayer, stationPos);
    }

    private List<Component> failureStatusTooltip(FailureReason reason, Optional<UUID> transponderId) {
        String key = reason.name().toLowerCase(Locale.ROOT);
        RouteStatus status = switch (reason) {
            case COLLISION_OR_OBSTRUCTION -> RouteStatus.BLOCKED;
            case VEHICLE_DESTROYED_OR_MISSING, VEHICLE_UNLOADED -> RouteStatus.MISSING_VEHICLE;
            case MISSING_AUTOPILOT_CONTROLLER, MISSING_STATION, INVALID_ROUTE_DATA, DIMENSION_MISMATCH,
                    MISSING_DOCK, AMBIGUOUS_DOCK -> RouteStatus.INVALID_ROUTE;
            case NONE -> RouteStatus.IDLE;
            case START_TOO_FAR_FROM_ROUTE, DOCK_LOCK_FAILED, REDSTONE_LINK_UNCONFIGURED, CARGO_STORAGE_MISSING, CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE -> RouteStatus.FAILED;
        };
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status." + status.name().toLowerCase(Locale.ROOT))
                        .withStyle(ChatFormatting.RED),
                failureReasonText(reason, transponderId),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_hint." + key)
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    private Component failureReasonText(FailureReason reason, Optional<UUID> transponderId) {
        Optional<CargoFailureContext> context = transponderId.flatMap(AutomatedLogisticsServices.SCHEDULES::lastCargoFailureContext)
                .or(() -> initialCargoFailureContext);
        if (reason == FailureReason.CARGO_STORAGE_MISSING && context.isPresent()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure_reason.cargo_storage_missing.detail",
                    cargoFailureResourceText(context.get()),
                    cargoFailureSideText(context.get())
            ).withStyle(ChatFormatting.GRAY);
        }
        if (reason == FailureReason.CARGO_CONDITION_TIMEOUT && context.isPresent()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure_reason.cargo_condition_timeout.detail",
                    cargoFailureResourceText(context.get()),
                    cargoFailureSideText(context.get())
            ).withStyle(ChatFormatting.GRAY);
        }
        if (reason == FailureReason.MISSING_DOCK) {
            return inferDockFailureReasonText(transponderId).orElseGet(() -> Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure_reason.missing_dock"
            ).withStyle(ChatFormatting.GRAY));
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason." + reason.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY);
    }

    private Optional<Component> inferDockFailureReasonText(Optional<UUID> transponderId) {
        if (!(menuPlayer.level().getBlockEntity(lookupStationPos(menuPlayer)) instanceof AirshipStationBlockEntity station)) {
            return Optional.empty();
        }
        if (station.groundDockStatus() != DockLinkStatus.LINKED || station.groundDockPos().isEmpty()) {
            return Optional.of(Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure_reason.missing_dock.station"
            ).withStyle(ChatFormatting.GRAY));
        }
        if (transponderId.isEmpty()) {
            return Optional.empty();
        }
        if (!(menuPlayer.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponder(serverLevel, transponderId.get());
        if (transponder.isPresent()
                && (transponder.get().shipDockStatus() != DockLinkStatus.LINKED || transponder.get().shipDockPos().isEmpty())) {
            return Optional.of(Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure_reason.missing_dock.ship"
            ).withStyle(ChatFormatting.GRAY));
        }
        return Optional.empty();
    }

    public Optional<BlockPos> dockPreviewPos(Player player) {
        if (!canControlStationLocally(player)) {
            return Optional.empty();
        }
        if (!(player.level().getBlockEntity(lookupStationPos(player)) instanceof AirshipStationBlockEntity station)) {
            return Optional.empty();
        }
        return station.groundDockPos();
    }

    public List<BlockPos> cargoPreviewPositions(Player player) {
        return cargoPreviewPositionGroups(player).stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    public List<List<BlockPos>> cargoPreviewPositionGroups(Player player) {
        if (!canControlStationLocally(player)) {
            return List.of();
        }
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> linkedEntries = resolveLinkedCargoEntries(player);
        if (linkedEntries.isEmpty()) {
            return List.of();
        }
        List<List<BlockPos>> expanded = CargoLinkSupport.expandPreviewPositionGroups(player.level(), lookupStationPos(player), 6, linkedEntries);
        if (!expanded.isEmpty()) {
            return expanded;
        }
        return linkedEntries.stream()
                .map(entry -> List.of(entry.pos()))
                .toList();
    }

    private LinkedCargoSummary resolveCargoSummary(Player player) {
        if (player.level().getBlockEntity(lookupStationPos(player)) instanceof AirshipStationBlockEntity station) {
            LinkedCargoSummary liveSummary = station.linkedCargoSummary();
            if (station.linkedCargoRevision() >= initialCargoRevision
                    || liveSummary.hasLinks()
                    || !initialCargoSummary.hasLinks()) {
                return liveSummary;
            }
        }
        return initialCargoSummary;
    }

    private List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> resolveLinkedCargoEntries(Player player) {
        if (player.level().getBlockEntity(lookupStationPos(player)) instanceof AirshipStationBlockEntity station) {
            List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> liveEntries = station.linkedCargo();
            if (station.linkedCargoRevision() >= initialCargoRevision
                    || !liveEntries.isEmpty()
                    || initialLinkedCargoEntries.isEmpty()) {
                return liveEntries;
            }
        }
        return initialLinkedCargoEntries;
    }

    public List<Component> segmentLines(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        return localRoutes(station, player).stream()
                .limit(4)
                .<Component>map(segment -> Component.translatable(
                        segment.startStationId().equals(station.stationId())
                                ? "gui.create_aeronautics_automated_logistics.airship_station.segment_line.outgoing"
                                : "gui.create_aeronautics_automated_logistics.airship_station.segment_line.incoming",
                        stationName(segment.startStationId(), segment.startStationName()),
                        stationName(segment.endStationId(), segment.endStationName()),
                        shipName(segment),
                        segment.points().size()
                ))
                .toList();
    }

    public List<RouteSegment> routeChoices(Player player) {
        return List.of();
    }

    public List<RouteChoiceSummary> routeChoiceSummaries(Player player) {
        if (!(player instanceof ServerPlayer)) {
            return clientRouteChoices;
        }
        if (!canControlStationLocally(player)) {
            return List.of();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return clientRouteChoices;
        }
        return summarizeRoutes(localRoutes(station, player));
    }

    private Optional<UUID> selectedTransponderId(AirshipStationBlockEntity station) {
        Optional<UUID> live = station.selectedTransponderId().filter(id -> !id.equals(new UUID(0L, 0L)));
        if (live.isPresent()) {
            return live;
        }
        return initialSelectedTransponderId.filter(id -> !id.equals(new UUID(0L, 0L)));
    }

    private String selectedShipName(AirshipStationBlockEntity station) {
        String live = station.selectedShipName();
        if (live != null && !live.isBlank()) {
            return live;
        }
        return initialSelectedShipName;
    }

    private Optional<ShipTransponderBlockEntity> selectedTransponder(Player player, AirshipStationBlockEntity station) {
        Optional<UUID> selectedId = selectedTransponderId(station);
        if (selectedId.isEmpty()) {
            return Optional.empty();
        }
        Optional<ShipTransponderSnapshot> snapshot = ShipTransponderRegistry.snapshot(selectedId.get())
                .filter(value -> value.dimension().equals(player.level().dimension()));
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        if (!(player.level().getBlockEntity(snapshot.get().transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
            return Optional.empty();
        }
        return Optional.of(transponder);
    }

    private boolean selectedShipAutomationRunning(ServerLevel level, AirshipStationBlockEntity station) {
        return selectedTransponderId(station)
                .map(AutomatedLogisticsServices.SCHEDULES::isRunning)
                .orElse(false);
    }

    private Component selectedShipRuntimeStateText(Player player, ShipTransponderBlockEntity transponder) {
        if (transponder.recordingDestinationStationId().isPresent()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_recording");
        }
        RouteStatus runtimeStatus = transponder.runtimeStatus();
        if (transponder.scheduleActive()) {
            if (runtimeStatus == RouteStatus.HELD) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused");
            }
            if (runtimeStatus == RouteStatus.HELD_FAULTED) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused_fault");
            }
            if (transponder.dockOutputActive()) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.docked");
            }
            if (runtimeStatus == RouteStatus.WAITING) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.waiting");
            }
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.running");
        }
        if (!transponder.hasOwnedStops()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
        }
        if (!hasReadySelectedRoutePlan(player, transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.partial_route_status");
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return selectedShipFailureStatusText(failure.get());
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ready");
    }

    private Component selectedShipRuntimeStateText(SelectedShipState state) {
        if (!state.present()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
        }
        if (state.recording()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_recording");
        }
        if (state.scheduleActive()) {
            if (state.runtimeStatus() == RouteStatus.HELD) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused");
            }
            if (state.runtimeStatus() == RouteStatus.HELD_FAULTED) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused_fault");
            }
            if (state.dockOutputActive()) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.docked");
            }
            if (state.runtimeStatus() == RouteStatus.WAITING) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.waiting");
            }
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.running");
        }
        if (!state.hasOwnedStops()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
        }
        if (!state.readyRoute()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.partial_route_status");
        }
        if (state.failure().isPresent()) {
            return selectedShipFailureStatusText(state.failure().get());
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ready");
    }

    private int selectedShipRuntimeStateColor(SelectedShipState state) {
        if (!state.present()) {
            return 0xFFFFD98A;
        }
        if (state.scheduleActive()) {
            if (state.runtimeStatus() == RouteStatus.HELD_FAULTED) {
                return 0xFFFFB4B4;
            }
            return 0xFFE7C46E;
        }
        if (!state.hasOwnedStops() || !state.readyRoute()) {
            return 0xFFFFD98A;
        }
        if (state.failure().isPresent()) {
            return 0xFFFFB4B4;
        }
        return 0xAFC7DE;
    }

    private List<Component> selectedShipRuntimeStatusTooltip(Player player, ShipTransponderBlockEntity transponder) {
        if (transponder.recordingDestinationStationId().isPresent()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.recording.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.recording.2").withStyle(ChatFormatting.GRAY)
            );
        }
        RouteStatus runtimeStatus = transponder.runtimeStatus();
        if (transponder.scheduleActive()) {
            if (runtimeStatus == RouteStatus.HELD) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (runtimeStatus == RouteStatus.HELD_FAULTED) {
                String suffix = AutomatedLogisticsServices.SCHEDULES.heldFailure(transponder.transponderId())
                        .orElse(PlaybackFailure.MOVEMENT_FAILURE)
                        .name()
                        .toLowerCase(Locale.ROOT);
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused_fault").withStyle(ChatFormatting.RED),
                        selectedShipFailureReasonText(
                                transponder,
                                AutomatedLogisticsServices.SCHEDULES.heldFailure(transponder.transponderId())
                                        .orElse(PlaybackFailure.MOVEMENT_FAILURE)
                        ),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused_fault.2")
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            }
            if (transponder.dockOutputActive()) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (runtimeStatus == RouteStatus.WAITING) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.waiting.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.waiting.2").withStyle(ChatFormatting.GRAY)
                );
            }
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.running.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.running.2").withStyle(ChatFormatting.GRAY)
            );
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (!transponder.hasOwnedStops()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.no_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.no_route.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (!hasReadySelectedRoutePlan(player, transponder)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.partial_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_hover.partial_route.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (failure.isEmpty()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.ready.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.ready.2").withStyle(ChatFormatting.DARK_GRAY)
            );
        }
        PlaybackFailure value = failure.get();
        String suffix = value.name().toLowerCase(Locale.ROOT);
        return List.of(
                selectedShipFailureStatusText(value).copy().withStyle(ChatFormatting.RED),
                selectedShipFailureReasonText(transponder, value),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint." + suffix)
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    private Component selectedShipFailureReasonText(ShipTransponderBlockEntity transponder, PlaybackFailure failure) {
        Optional<CargoFailureContext> context = AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId())
                .or(() -> transponder.syncedCargoFailureContext())
                .or(() -> initialCargoFailureContext)
                .or(() -> inferCargoFailureContext(transponder, failure));
        if (failure == PlaybackFailure.CARGO_STORAGE_MISSING && context.isPresent()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.cargo_storage_missing.detail",
                    cargoFailureResourceText(context.get()),
                    cargoFailureSideText(context.get())
            ).withStyle(ChatFormatting.GRAY);
        }
        if (failure == PlaybackFailure.CARGO_CONDITION_TIMEOUT && context.isPresent()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.cargo_condition_timeout.detail",
                    cargoFailureResourceText(context.get()),
                    cargoFailureSideText(context.get())
            ).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason." + failure.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY);
    }

    private Optional<CargoFailureContext> inferCargoFailureContext(ShipTransponderBlockEntity transponder, PlaybackFailure failure) {
        if (failure != PlaybackFailure.CARGO_STORAGE_MISSING && failure != PlaybackFailure.CARGO_CONDITION_TIMEOUT) {
            return Optional.empty();
        }
        LinkedCargoSummary shipSummary = transponder.linkedCargoSummary();
        for (AirshipScheduleEntry entry : transponder.ownedSchedule().entries()) {
            for (List<AirshipScheduleCondition> group : entry.effectiveConditionGroups()) {
                for (AirshipScheduleCondition condition : group) {
                    WaitCondition waitCondition = condition.waitCondition();
                    if (!isCargoWaitType(waitCondition.type())) {
                        continue;
                    }
                    LinkedCargoSummary summary = waitCondition.cargoTarget() == CargoWaitTarget.SHIP_CARGO
                            ? shipSummary
                            : stationCargoSummary(transponder, entry);
                    if (summary == null || summary.staleLinks() > 0 || !summarySupportsWait(summary, waitCondition.type())) {
                        return Optional.of(new CargoFailureContext(waitCondition.cargoTarget(), waitCondition.type()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private LinkedCargoSummary stationCargoSummary(ShipTransponderBlockEntity transponder, AirshipScheduleEntry entry) {
        if (transponder.getLevel() == null || entry.targetStationId().isEmpty()) {
            return null;
        }
        return entry.targetStationId()
                .flatMap(AirshipStationRegistry::snapshot)
                .flatMap(snapshot -> transponder.getLevel().getBlockEntity(snapshot.stationPos()) instanceof AirshipStationBlockEntity station
                        ? Optional.of(station.linkedCargoSummary())
                        : Optional.empty())
                .orElse(null);
    }

    private boolean summarySupportsWait(LinkedCargoSummary summary, WaitConditionType type) {
        return switch (type) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL -> summary.itemLinks() > 0;
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> summary.fluidLinks() > 0;
            default -> true;
        };
    }

    private boolean isCargoWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || type == WaitConditionType.UNTIL_FLUID_THRESHOLD
                || type == WaitConditionType.UNTIL_ITEM_EMPTY
                || type == WaitConditionType.UNTIL_ITEM_FULL
                || type == WaitConditionType.UNTIL_FLUID_EMPTY
                || type == WaitConditionType.UNTIL_FLUID_FULL
                || type == WaitConditionType.UNTIL_EMPTY
                || type == WaitConditionType.UNTIL_FULL;
    }

    private Component cargoFailureSideText(CargoFailureContext context) {
        return Component.translatable(
                context.target() == CargoWaitTarget.STATION_CARGO
                        ? "gui.create_aeronautics_automated_logistics.cargo_failure.side.station"
                        : "gui.create_aeronautics_automated_logistics.cargo_failure.side.ship"
        );
    }

    private Component cargoFailureResourceText(CargoFailureContext context) {
        return switch (context.waitType()) {
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL ->
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo_failure.resource.fluid");
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL ->
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo_failure.resource.item");
            default -> Component.translatable("gui.create_aeronautics_automated_logistics.cargo_failure.resource.cargo");
        };
    }

    private Component selectedShipFailureStatusText(PlaybackFailure failure) {
        return switch (failure) {
            case INVALID_ROUTE, DIMENSION_MISMATCH -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
            case STATION_MISSING -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.station_missing_status");
            case VEHICLE_MISSING, VEHICLE_UNLOADED, MISSING_CONTROLLER -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ship_missing_status");
            case START_TOO_FAR_FROM_ROUTE -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.start_blocked_status");
            case WRONG_START_STATION -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.wrong_station_status");
            case COLLISION_OR_OBSTRUCTION, STUCK -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.blocked_status");
            case MISSING_DOCK, AMBIGUOUS_DOCK, DOCK_LOCK_FAILED -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock_problem_status");
            case REDSTONE_LINK_UNCONFIGURED, CARGO_STORAGE_MISSING, CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE, ALREADY_RUNNING, MAX_ACTIVE_VEHICLES_REACHED -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.needs_attention_status");
        };
    }

    private boolean hasResolvableSelectedRouteChain(Player player, ShipTransponderBlockEntity transponder) {
        if (!transponder.hasOwnedStops()) {
            return false;
        }
        AirshipSchedule schedule = transponder.ownedSchedule();
        if (schedule.entries().isEmpty()) {
            return false;
        }
        AirshipScheduleEntry firstEntry = schedule.entries().getFirst();
        if (firstEntry.targetStationId().isEmpty()) {
            return false;
        }
        Optional<RouteSegment> firstSegment = firstEntry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(candidate -> candidate.endStationId().equals(firstEntry.targetStationId().get()))
                .filter(candidate -> candidate.dimension().equals(player.level().dimension()))
                .filter(candidate -> candidate.transponderId().equals(transponder.transponderId()));
        if (firstSegment.isEmpty()) {
            return false;
        }
        UUID currentStationId = firstEntry.targetStationId().get();
        for (int i = 1; i < schedule.entries().size(); i++) {
            AirshipScheduleEntry entry = schedule.entries().get(i);
            if (entry.targetStationId().isEmpty()) {
                return false;
            }
            UUID fromStationId = currentStationId;
            UUID nextStationId = entry.targetStationId().get();
            Optional<RouteSegment> segment = entry.pinnedSegmentId()
                    .flatMap(RouteSegmentRegistry::byId)
                    .filter(candidate -> candidate.startStationId().equals(fromStationId))
                    .filter(candidate -> candidate.endStationId().equals(nextStationId))
                    .filter(candidate -> candidate.dimension().equals(player.level().dimension()))
                    .filter(candidate -> candidate.transponderId().equals(transponder.transponderId()))
                    .or(() -> RouteSegmentResolver.newestFor(
                            fromStationId,
                            nextStationId,
                            player.level().dimension(),
                            Optional.of(transponder.transponderId())
                    ));
            if (segment.isEmpty()) {
                return false;
            }
            currentStationId = nextStationId;
        }
        return true;
    }

    private boolean hasReadySelectedRoutePlan(Player player, ShipTransponderBlockEntity transponder) {
        int minimumStops = AutomatedLogisticsConfig.allowOneWayRoutePlans() ? 1 : 2;
        return transponder.ownedSchedule().entries().size() >= minimumStops
                && hasResolvableSelectedRouteChain(player, transponder);
    }

    private boolean selectNextShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (ships.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.ship_selection.none_found"));
            return false;
        }

        int currentIndex = -1;
        Optional<UUID> current = station.selectedTransponderId();
        if (current.isPresent()) {
            for (int i = 0; i < ships.size(); i++) {
                if (ships.get(i).transponderId().equals(current.get())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        ShipTransponderSnapshot selected = ships.get((currentIndex + 1) % ships.size());
        station.selectShip(selected);
        syncClientState(player, station);
        return true;
    }

    private boolean selectPreviousShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (ships.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.ship_selection.none_found"));
            return false;
        }

        int currentIndex = -1;
        Optional<UUID> current = station.selectedTransponderId();
        if (current.isPresent()) {
            for (int i = 0; i < ships.size(); i++) {
                if (ships.get(i).transponderId().equals(current.get())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int selectedIndex = currentIndex < 0 ? ships.size() - 1 : Math.floorMod(currentIndex - 1, ships.size());
        station.selectShip(ships.get(selectedIndex));
        syncClientState(player, station);
        return true;
    }

    private boolean selectShipByIndex(ServerPlayer player, AirshipStationBlockEntity station, int shipIndex) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (shipIndex < 0 || shipIndex >= ships.size()) {
            return false;
        }
        ShipTransponderSnapshot selected = ships.get(shipIndex);
        station.selectShip(selected);
        syncClientState(player, station);
        return true;
    }

    private boolean autoSelectClosestShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (ships.isEmpty()) {
            return false;
        }
        station.selectShip(ships.getFirst());
        syncClientState(player, station);
        return true;
    }

    private boolean beginLinkDock(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!StationPermissionService.ensureCanControl(player, station)) {
            return false;
        }
        DockLinkInteractionService.beginStationLink(player, station.getBlockPos());
        return true;
    }

    private boolean clearDockLink(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!StationPermissionService.ensureCanControl(player, station)) {
            return false;
        }
        if (DockLinkInteractionService.hasPendingStationLink(player, station.getBlockPos())) {
            return DockLinkInteractionService.cancelPending(player);
        }
        if (selectedShipAutomationRunning(player.serverLevel(), station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.locked_while_running"));
            return false;
        }
        station.clearGroundDockLink();
        DockLinkInteractionService.clearPending(player);
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.cleared"));
        return true;
    }

    private boolean recordOrFinishSegment(ServerPlayer player, AirshipStationBlockEntity station) {
        if (AutomatedLogisticsServices.RECORDING.hasActiveRecording(player)) {
            return finishSegmentRecording(player, station);
        }
        return startSegmentRecording(player, station);
    }

    private boolean startSegmentRecording(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.busy"));
            return false;
        }
        if (selectedShipAutomationRunning(player.serverLevel(), station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.playback.running"));
            return false;
        }

        Optional<UUID> selectedTransponderId = station.selectedTransponderId();
        if (selectedTransponderId.isEmpty()) {
            station.setFailure(FailureReason.MISSING_AUTOPILOT_CONTROLLER);
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.no_selected_ship"));
            return false;
        }

        Optional<ShipTransponderSnapshot> selectedShip = ShipTransponderRegistry.snapshot(selectedTransponderId.get())
                .filter(ship -> ship.dimension().equals(player.serverLevel().dimension()));
        if (selectedShip.isEmpty() || selectedShip.get().controllerRef().isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return false;
        }

        Optional<VehicleController> controller = selectedShip.get().controllerRef()
                .flatMap(controllerRef -> VehicleControllerResolver.resolve(player.serverLevel(), controllerRef));
        if (controller.isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return false;
        }

        RouteOperationResult<RecordingSession> result = AutomatedLogisticsServices.RECORDING.startRecording(
                player,
                stationPos,
                controller.get()
        );
        result.value().ifPresentOrElse(
                session -> actionBar(player, Component.translatable(
                        "message.create_aeronautics_automated_logistics.segment_recording.started",
                        station.stationName(),
                        selectedShip.get().shipName()
                )),
                () -> result.failure().ifPresent(failure -> {
                    station.setFailure(failure.failureReason());
                    actionBar(player, recordingFailureMessage(failure));
                })
        );
        return result.value().isPresent();
    }

    private boolean finishSegmentRecording(ServerPlayer player, AirshipStationBlockEntity station) {
        RouteOperationResult<RouteSegment> result = AutomatedLogisticsServices.RECORDING.finishSegmentRecording(player, stationPos);
        result.value().ifPresentOrElse(
                segment -> {
                    syncClientState(player, station);
                    actionBar(player, Component.translatable(
                            "message.create_aeronautics_automated_logistics.segment_recording.saved",
                            stationName(segment.startStationId(), segment.startStationName()),
                            stationName(segment.endStationId(), segment.endStationName()),
                            shipName(segment),
                            segment.points().size()
                    ));
                },
                () -> result.failure().ifPresent(failure -> {
                    station.setFailure(failure.failureReason());
                    actionBar(player, recordingFailureMessage(failure));
                })
        );
        return result.value().isPresent();
    }

    private boolean finishRecordingSession(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<RecordingSession> session = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (session.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.not_recording"));
            return false;
        }
        RouteOperationResult<Route> result = AutomatedLogisticsServices.RECORDING.stopRecording(player, session.get().routeId());
        result.value().ifPresentOrElse(
                route -> actionBar(player, Component.translatable(
                        "message.create_aeronautics_automated_logistics.recording.saved",
                        route.points().size()
                )),
                () -> result.failure().ifPresent(failure -> actionBar(player, recordingFailureMessage(failure)))
        );
        return result.value().isPresent();
    }

    private boolean runSchedule(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.busy"));
            return false;
        }
        if (selectedShipAutomationRunning(player.serverLevel(), station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.playback.running"));
            return false;
        }

        Optional<UUID> selectedTransponderId = station.selectedTransponderId();
        if (selectedTransponderId.isEmpty()) {
            station.setFailure(FailureReason.MISSING_AUTOPILOT_CONTROLLER);
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.no_selected_ship"));
            return false;
        }
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponder(player.serverLevel(), selectedTransponderId.get());
        if (transponder.isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return false;
        }
        if (!transponder.get().hasOwnedStops()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_no_stops"));
            return false;
        }
        if (!hasReadySelectedRoutePlan(player, transponder.get())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_partial_route"));
            return false;
        }

        if (AutomatedLogisticsServices.SCHEDULES.isHeld(transponder.get().transponderId())) {
            boolean resumed = AutomatedLogisticsServices.SCHEDULES.resumeHeldPlayback(
                    player.serverLevel(),
                    transponder.get().transponderId()
            );
            if (resumed) {
                AllSoundEvents.CONFIRM.playOnServer(player.level(), station.getBlockPos(), 0.6f, 1.0f);
                return true;
            }
            PlaybackFailure failure = AutomatedLogisticsServices.SCHEDULES
                    .heldFailure(transponder.get().transponderId())
                    .orElse(PlaybackFailure.INVALID_ROUTE);
            actionBar(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.playback.failed",
                    Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
            ));
            AllSoundEvents.DENY.playOnServer(player.level(), station.getBlockPos(), 0.5f, 1.0f);
            return false;
        }

        AirshipSchedule schedule = transponder.get().ownedSchedule();
        PlaybackOperationResult<?> result = AutomatedLogisticsServices.SCHEDULES.startFromTransponder(
                player,
                transponder.get(),
                schedule
        );
        result.value().ifPresent(routeId -> {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.started"));
            AllSoundEvents.CONFIRM.playOnServer(player.level(), station.getBlockPos(), 0.6f, 1.0f);
        });
        result.failure().ifPresent(failure -> {
            station.setFailure(failure.failureReason());
            Component message = switch (failure) {
                case START_TOO_FAR_FROM_ROUTE -> Component.translatable(
                        "message.create_aeronautics_automated_logistics.playback.move_to_valid_start_station"
                );
                case WRONG_START_STATION -> AutomatedLogisticsServices.SCHEDULES
                        .currentStartStationName(player.serverLevel(), transponder.get())
                        .map(stationName -> Component.translatable(
                                "message.create_aeronautics_automated_logistics.playback.wrong_start_station",
                                stationName
                        ))
                        .orElseGet(() -> Component.translatable(
                                "message.create_aeronautics_automated_logistics.playback.move_to_valid_start_station"
                        ));
                case INVALID_ROUTE -> {
                    Optional<String> currentStationName = AutomatedLogisticsServices.SCHEDULES
                            .currentStartStationName(player.serverLevel(), transponder.get());
                    Optional<String> nextStopName = AutomatedLogisticsServices.SCHEDULES
                            .nextStopNameForCurrentStation(player.serverLevel(), transponder.get(), schedule);
                    yield currentStationName.isPresent() && nextStopName.isPresent()
                            ? Component.translatable(
                                    "message.create_aeronautics_automated_logistics.playback.invalid_route_from_station",
                                    currentStationName.get(),
                                    nextStopName.get()
                            )
                            : Component.translatable(
                                    "message.create_aeronautics_automated_logistics.playback.failed",
                                    Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
                            );
                }
                default -> Component.translatable(
                        "message.create_aeronautics_automated_logistics.playback.failed",
                        Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
                );
            };
            actionBar(player, message);
            AllSoundEvents.DENY.playOnServer(player.level(), station.getBlockPos(), 0.5f, 1.0f);
        });
        return result.value().isPresent();
    }

    private boolean stopSchedule(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<UUID> transponderId = station.selectedTransponderId();
        if (transponderId.isEmpty()
                || !AutomatedLogisticsServices.SCHEDULES.hasActiveRuntime(player.serverLevel(), transponderId.get())) {
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), transponderId.get());
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.stopped"));
        AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(player.level(), station.getBlockPos(), 0.45f, 1.2f);
        return true;
    }

    private boolean linkCargo(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording() || selectedShipAutomationRunning(player.serverLevel(), station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), station.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        CargoLinkInteractionService.beginStationLink(player, station.getBlockPos());
        return true;
    }

    private boolean clearCargo(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording() || selectedShipAutomationRunning(player.serverLevel(), station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), station.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        if (CargoLinkInteractionService.hasPendingStationLink(player, station.getBlockPos())) {
            return CargoLinkInteractionService.cancelPending(player);
        }
        if (!station.clearLinkedCargo()) {
            return false;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.cleared"));
        AllSoundEvents.CONFIRM.playOnServer(player.level(), station.getBlockPos(), 0.6f, 1.0f);
        return true;
    }

    private boolean previewRouteByIndex(ServerPlayer player, AirshipStationBlockEntity station, int routeIndex) {
        List<RouteSegment> routes = localRoutes(station, player);
        if (routeIndex < 0 || routeIndex >= routes.size()) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station preview route rejected player={} spectator={} stationPos={} routeIndex={} availableRoutes={}",
                    player.getName().getString(),
                    player.isSpectator(),
                    station.getBlockPos(),
                    routeIndex,
                    routes.size()
            );
            return false;
        }
        RouteSegment segment = routes.get(routeIndex);
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station preview route sending player={} spectator={} stationPos={} routeIndex={} routeId={} points={}",
                player.getName().getString(),
                player.isSpectator(),
                station.getBlockPos(),
                routeIndex,
                segment.id().value(),
                segment.points().size()
        );
        PacketDistributor.sendToPlayer(
                player,
                new SetFlightPathPreviewPayload(
                        true,
                        segment.points().stream().map(point -> point.position()).toList(),
                        List.of(Math.max(0, segment.points().size() - 1)),
                        Optional.empty()
                )
        );
        return true;
    }

    public static List<RouteChoiceSummary> buildRouteChoiceSummaries(ServerPlayer player, AirshipStationBlockEntity station) {
        return summarizeRoutes(localRoutesForSummary(station, player));
    }

    private static List<RouteChoiceSummary> summarizeRoutes(List<RouteSegment> routes) {
        return routes.stream()
                .map(route -> new RouteChoiceSummary(
                        route.id().value(),
                        route.startStationId(),
                        stationNameForSummary(route.startStationId(), route.startStationName()),
                        route.endStationId(),
                        stationNameForSummary(route.endStationId(), route.endStationName()),
                        route.transponderId(),
                        shipNameForSummary(route.transponderId(), route.shipName()),
                        route.dimension(),
                        route.points().size(),
                        route.createdEpochMillis()
                ))
                .toList();
    }

    private List<RouteSegment> localRoutes(AirshipStationBlockEntity station, Player player) {
        Optional<ShipTransponderBlockEntity> selectedTransponder = selectedTransponder(player, station);
        if (selectedTransponder.isPresent()) {
            return scheduledLocalRoutes(station, player, selectedTransponder.get());
        }
        return RouteSegmentResolver.validLocalSegments(station, player.level().dimension(), Optional.empty()).stream()
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    private static List<RouteSegment> localRoutesForSummary(AirshipStationBlockEntity station, ServerPlayer player) {
        Optional<ShipTransponderBlockEntity> selectedTransponder = station.selectedTransponderId()
                .flatMap(transponderId -> selectedTransponderForSummary(player.serverLevel(), transponderId));
        if (selectedTransponder.isPresent()) {
            return scheduledLocalRoutesForSummary(station, player, selectedTransponder.get());
        }
        return RouteSegmentResolver.validLocalSegments(station, player.level().dimension(), Optional.empty()).stream()
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    private static List<RouteSegment> scheduledLocalRoutesForSummary(
            AirshipStationBlockEntity station,
            ServerPlayer player,
            ShipTransponderBlockEntity transponder
    ) {
        List<AirshipScheduleEntry> entries = transponder.ownedSchedule().entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        List<RouteSegment> routes = new ArrayList<>();
        Optional<UUID> currentStationId = Optional.empty();
        for (int i = 0; i < entries.size(); i++) {
            AirshipScheduleEntry entry = entries.get(i);
            if (entry.targetStationId().isEmpty()) {
                break;
            }
            UUID targetStationId = entry.targetStationId().get();
            Optional<RouteSegment> segment = i == 0
                    ? resolveInitialScheduledSegmentForSummary(entry, targetStationId, transponder.transponderId(), player)
                    : currentStationId.flatMap(startStationId -> resolveScheduledSegmentForSummary(
                            entry,
                            startStationId,
                            targetStationId,
                            transponder.transponderId(),
                            player
                    ));
            segment.ifPresent(route -> {
                if (route.startStationId().equals(station.stationId())
                        || route.endStationId().equals(station.stationId())) {
                    addUniqueRouteForSummary(routes, route);
                }
            });
            currentStationId = Optional.of(targetStationId);
        }
        return List.copyOf(routes);
    }

    private static Optional<RouteSegment> resolveInitialScheduledSegmentForSummary(
            AirshipScheduleEntry entry,
            UUID targetStationId,
            UUID transponderId,
            ServerPlayer player
    ) {
        return entry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(segment -> segment.endStationId().equals(targetStationId))
                .filter(segment -> segment.dimension().equals(player.level().dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .or(() -> RouteSegmentRegistry.endingAt(
                        targetStationId,
                        player.level().dimension(),
                        Optional.of(transponderId)
                ).stream().findFirst());
    }

    private static Optional<RouteSegment> resolveScheduledSegmentForSummary(
            AirshipScheduleEntry entry,
            UUID startStationId,
            UUID targetStationId,
            UUID transponderId,
            ServerPlayer player
    ) {
        return entry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> segment.endStationId().equals(targetStationId))
                .filter(segment -> segment.dimension().equals(player.level().dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .or(() -> RouteSegmentResolver.newestFor(
                        startStationId,
                        targetStationId,
                        player.level().dimension(),
                        Optional.of(transponderId)
                ));
    }

    private static void addUniqueRouteForSummary(List<RouteSegment> routes, RouteSegment route) {
        for (RouteSegment existing : routes) {
            if (existing.id().equals(route.id())) {
                return;
            }
        }
        routes.add(route);
    }

    private List<RouteSegment> scheduledLocalRoutes(
            AirshipStationBlockEntity station,
            Player player,
            ShipTransponderBlockEntity transponder
    ) {
        List<AirshipScheduleEntry> entries = transponder.ownedSchedule().entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        List<RouteSegment> routes = new java.util.ArrayList<>();
        Optional<UUID> currentStationId = Optional.empty();
        for (int i = 0; i < entries.size(); i++) {
            AirshipScheduleEntry entry = entries.get(i);
            if (entry.targetStationId().isEmpty()) {
                break;
            }
            UUID targetStationId = entry.targetStationId().get();
            Optional<RouteSegment> segment = i == 0
                    ? resolveInitialScheduledSegment(entry, targetStationId, transponder.transponderId(), player)
                    : currentStationId.flatMap(startStationId -> resolveScheduledSegment(
                            entry,
                            startStationId,
                            targetStationId,
                            transponder.transponderId(),
                            player
                    ));
            segment.ifPresent(route -> {
                if (route.startStationId().equals(station.stationId())
                        || route.endStationId().equals(station.stationId())) {
                    addUniqueRoute(routes, route);
                }
            });
            currentStationId = Optional.of(targetStationId);
        }
        return List.copyOf(routes);
    }

    private Optional<RouteSegment> resolveInitialScheduledSegment(
            AirshipScheduleEntry entry,
            UUID targetStationId,
            UUID transponderId,
            Player player
    ) {
        return entry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(segment -> segment.endStationId().equals(targetStationId))
                .filter(segment -> segment.dimension().equals(player.level().dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .or(() -> RouteSegmentRegistry.endingAt(
                        targetStationId,
                        player.level().dimension(),
                        Optional.of(transponderId)
                ).stream().findFirst());
    }

    private Optional<RouteSegment> resolveScheduledSegment(
            AirshipScheduleEntry entry,
            UUID startStationId,
            UUID targetStationId,
            UUID transponderId,
            Player player
    ) {
        return entry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(segment -> segment.startStationId().equals(startStationId))
                .filter(segment -> segment.endStationId().equals(targetStationId))
                .filter(segment -> segment.dimension().equals(player.level().dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .or(() -> RouteSegmentResolver.newestFor(
                        startStationId,
                        targetStationId,
                        player.level().dimension(),
                        Optional.of(transponderId)
                ));
    }

    private void addUniqueRoute(List<RouteSegment> routes, RouteSegment route) {
        for (RouteSegment existing : routes) {
            if (existing.id().equals(route.id())) {
                return;
            }
        }
        routes.add(route);
    }

    private String stationName(UUID stationId, String fallbackName) {
        return AirshipStationRegistry.snapshot(stationId)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
    }

    private String shipName(RouteSegment segment) {
        return ShipTransponderRegistry.snapshot(segment.transponderId())
                .map(ShipTransponderSnapshot::shipName)
                .filter(name -> !name.isBlank())
                .orElse(segment.shipName());
    }

    private Optional<ShipTransponderBlockEntity> selectedTransponder(ServerLevel level, UUID transponderId) {
        return ShipTransponderRegistry.snapshot(transponderId)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast);
    }

    private static Optional<ShipTransponderBlockEntity> selectedTransponderForSummary(ServerLevel level, UUID transponderId) {
        return ShipTransponderRegistry.snapshot(transponderId)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast);
    }

    private static String stationNameForSummary(UUID stationId, String fallbackName) {
        return AirshipStationRegistry.snapshot(stationId)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
    }

    private static String shipNameForSummary(UUID transponderId, String fallbackName) {
        return ShipTransponderRegistry.snapshot(transponderId)
                .map(ShipTransponderSnapshot::shipName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
    }

    private boolean requiresSelectedShipControl(int id) {
        return id == ACTION_START_SEGMENT_RECORDING
                || id == ACTION_FINISH_SEGMENT_RECORDING
                || id == ACTION_RUN_SCHEDULE
                || id == ACTION_STOP_SCHEDULE
                || id == ACTION_MARK_STOP
                || id == ACTION_CYCLE_LAST_STOP_WAIT
                || id == ACTION_DECREASE_LAST_STOP_WAIT
                || id == ACTION_INCREASE_LAST_STOP_WAIT
                || id == ACTION_RECORD_OR_FINISH_SEGMENT
                || id == ACTION_FINISH_RECORDING;
    }

    private boolean requiresStationControl(int id) {
        return id == ACTION_RUN_SCHEDULE
                || id == ACTION_STOP_SCHEDULE
                || id == ACTION_BEGIN_LINK_DOCK
                || id == ACTION_CLEAR_DOCK_LINK
                || id == ACTION_LINK_CARGO
                || id == ACTION_CLEAR_CARGO
                || id == ACTION_SHOW_CARGO;
    }

    private boolean canControlSelectedShip(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<UUID> selectedTransponderId = station.selectedTransponderId();
        if (selectedTransponderId.isEmpty()) {
            return true;
        }
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponder(player.serverLevel(), selectedTransponderId.get());
        return transponder.isEmpty() || TransponderPermissionService.ensureCanControl(player, transponder.get());
    }

    private boolean canControlSelectedShipLocally(Player player, ShipTransponderBlockEntity transponder) {
        if (!AutomatedLogisticsConfig.RESTRICT_TRANSPONDER_CONTROL_TO_OWNER.get()) {
            return true;
        }
        Optional<UUID> ownerId = transponder.ownerId();
        return ownerId.isEmpty() || ownerId.get().equals(player.getUUID());
    }

    public record ShipChoice(
            UUID transponderId,
            Component shipName,
            Component statusText,
            int statusColor,
            boolean selected
    ) {
    }

    private boolean markStop(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!station.isRecording()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.not_recording"));
            return false;
        }
        return station.activeRecording()
                .filter(session -> session.playerId().equals(player.getUUID()))
                .map(session -> {
                    RouteOperationResult<RouteStop> result = AutomatedLogisticsServices.RECORDING.markStop(
                            player,
                            session.routeId(),
                            WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                    );
                    result.value().ifPresentOrElse(
                            stop -> actionBar(player, Component.translatable(
                                    "message.create_aeronautics_automated_logistics.stop_mark.added",
                                    stop.name(),
                                    stop.pointIndex(),
                                    waitText(stop.waitCondition())
                            )),
                            () -> result.failure().ifPresent(failure -> actionBar(player, recordingFailureMessage(failure)))
                    );
                    return result.value().isPresent();
                })
                .orElse(false);
    }

    private boolean cycleLastStopWait(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<RouteStop> lastStop = lastEditableStop(station);
        if (lastStop.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.no_stop"));
            return false;
        }

        WaitCondition next = lastStop.get().waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                : WaitCondition.none();
        return updateLastStopWait(player, station, next);
    }

    private boolean adjustLastStopWait(ServerPlayer player, AirshipStationBlockEntity station, int deltaTicks) {
        Optional<RouteStop> lastStop = lastEditableStop(station);
        if (lastStop.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.no_stop"));
            return false;
        }

        int currentTicks = lastStop.get().waitCondition().type() == WaitConditionType.TIMED
                ? lastStop.get().waitCondition().durationTicks()
                : 0;
        int nextTicks = Math.max(0, currentTicks + deltaTicks);
        WaitCondition next = nextTicks <= 0 ? WaitCondition.none() : WaitCondition.timed(nextTicks);
        return updateLastStopWait(player, station, next);
    }

    private Optional<RouteStop> lastEditableStop(AirshipStationBlockEntity station) {
        if (station.isRecording()) {
            List<RouteStop> stops = station.recordingStops();
            return stops.isEmpty() ? Optional.empty() : Optional.of(stops.getLast());
        }
        return station.recordedRoute().flatMap(route -> route.stops().isEmpty()
                ? Optional.empty()
                : Optional.of(route.stops().getLast()));
    }

    private boolean updateLastStopWait(ServerPlayer player, AirshipStationBlockEntity station, WaitCondition waitCondition) {
        if (station.isRecording()) {
            return station.activeRecording()
                    .filter(session -> session.playerId().equals(player.getUUID()))
                    .map(session -> {
                        RouteOperationResult<RouteStop> result = AutomatedLogisticsServices.RECORDING.updateLastStopWait(
                                player,
                                session.routeId(),
                                waitCondition
                        );
                        result.value().ifPresentOrElse(
                                stop -> actionBar(player, Component.translatable(
                                        "message.create_aeronautics_automated_logistics.stop_mark.wait_updated",
                                        stop.name(),
                                        waitText(stop.waitCondition())
                                )),
                                () -> result.failure().ifPresent(failure -> actionBar(player, recordingFailureMessage(failure)))
                        );
                        return result.value().isPresent();
                    })
                    .orElse(false);
        }

        return station.recordedRoute().map(route -> {
            if (!canControlRoute(player, route)) {
                actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.station.permission_denied"));
                return false;
            }
            if (route.stops().isEmpty()) {
                actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.no_stop"));
                return false;
            }

            RouteStop updated = route.stops().getLast().withWaitCondition(waitCondition);
            station.replaceLastRouteStop(updated);
            actionBar(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.stop_mark.wait_updated",
                    updated.name(),
                    waitText(updated.waitCondition())
            ));
            return true;
        }).orElse(false);
    }

    private Component waitText(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case NONE -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.wait.none");
            case TIMED -> Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.wait.timed",
                    waitCondition.durationTicks() / 20
            );
            default -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.wait.unsupported");
        };
    }

    private Component recordingFailureMessage(RecordingFailure failure) {
        return Component.translatable(
                "message.create_aeronautics_automated_logistics.recording.failed",
                Component.translatable("failure.create_aeronautics_automated_logistics." + failure.name().toLowerCase(Locale.ROOT))
        );
    }

    private void actionBar(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            SetMenuActionBarMessagePayload.send(serverPlayer, message);
        }
    }

    private void syncRouteChoices(ServerPlayer player, AirshipStationBlockEntity station) {
        PacketDistributor.sendToPlayer(
                player,
                new SyncStationRouteChoicesPayload(
                        station.getBlockPos(),
                        buildRouteChoiceSummaries(player, station)
                )
        );
    }

    private List<Component> selectedShipRuntimeStatusTooltip(SelectedShipState state) {
        if (state.recording()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.recording.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.recording.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (state.scheduleActive()) {
            if (state.runtimeStatus() == RouteStatus.HELD) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (state.runtimeStatus() == RouteStatus.HELD_FAULTED) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused_fault").withStyle(ChatFormatting.RED),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused_fault.2")
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            }
            if (state.dockOutputActive()) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (state.runtimeStatus() == RouteStatus.WAITING) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.waiting.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.waiting.2").withStyle(ChatFormatting.GRAY)
                );
            }
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.running.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.running.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (state.failure().isEmpty() && !state.hasOwnedStops()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_route.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (state.failure().isEmpty() && !state.readyRoute()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.partial_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.partial_route.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (state.failure().isEmpty()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.ready.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.ready.2").withStyle(ChatFormatting.DARK_GRAY)
            );
        }
        PlaybackFailure value = state.failure().get();
        return List.of(
                selectedShipFailureStatusText(value).copy().withStyle(ChatFormatting.RED),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason."
                        + value.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint."
                        + value.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    private void syncClientState(ServerPlayer player, AirshipStationBlockEntity station) {
        ClientState state = buildClientState(player, station);
        lastSyncedClientStateHash = state.hashCode();
        PacketDistributor.sendToPlayer(player, new SyncStationMenuStatePayload(station.getBlockPos(), state));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(menuPlayer instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverPlayer.serverLevel().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return;
        }
        ClientState state = buildClientState(serverPlayer, station);
        int stateHash = state.hashCode();
        if (stateHash == lastSyncedClientStateHash) {
            return;
        }
        lastSyncedClientStateHash = stateHash;
        PacketDistributor.sendToPlayer(serverPlayer, new SyncStationMenuStatePayload(station.getBlockPos(), state));
    }

    public static ClientState buildClientState(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<UUID> selectedTransponderId = station.selectedTransponderId()
                .filter(id -> !id.equals(new UUID(0L, 0L)));
        List<ShipChoiceSnapshot> shipChoices = buildShipChoiceSnapshots(player, station, selectedTransponderId);
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponderForSummary(player.serverLevel(), selectedTransponderId);
        SelectedShipState selectedShipState = transponder
                .map(value -> buildSelectedShipState(player, station, value))
                .orElseGet(SelectedShipState::empty);
        return new ClientState(
                Optional.of(station.stationId()),
                station.stationName(),
                StationPermissionService.canControl(player, station),
                selectedTransponderId,
                station.selectedShipName(),
                shipChoices,
                buildRouteChoiceSummaries(player, station),
                selectedShipState,
                buildDockViewSnapshot(player, station),
                buildCargoViewSnapshot(station)
        );
    }

    private static ShipTransponderMenu.ViewSnapshot buildDockViewSnapshot(ServerPlayer player, AirshipStationBlockEntity station) {
        DockLinkStatus status = station.groundDockStatus();
        if (status == DockLinkStatus.LINKED && station.groundDockPos().isEmpty()) {
            status = DockLinkStatus.UNKNOWN;
        } else if (status == DockLinkStatus.AMBIGUOUS) {
            status = DockLinkStatus.INVALID;
        }
        Component compact = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact."
                        + status.name().toLowerCase(Locale.ROOT)
        );
        Component statusLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.status",
                compact
        );
        Component outputLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.redstone_output",
                Component.translatable(
                        station.dockOutputActive()
                                ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                                : "gui.create_aeronautics_automated_logistics.dock.output.off"
                )
        );
        Component positionLine = station.groundDockPos()
                .map(pos -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.position",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                ))
                .orElseGet(() -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.dock.hover.position_unknown"
                ));
        Component hintLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock.hover."
                        + status.name().toLowerCase(Locale.ROOT)
        );
        int color = switch (status) {
            case LINKED -> 0xFFAFC7DE;
            case UNKNOWN, MISSING -> 0xFFFFD98A;
            case AMBIGUOUS, INVALID -> 0xFFFFB4B4;
        };
        return new ShipTransponderMenu.ViewSnapshot(
                compact.getString(),
                color,
                status == DockLinkStatus.LINKED,
                List.of(
                        tooltipLine(statusLine.copy().withStyle(status == DockLinkStatus.LINKED ? ChatFormatting.GRAY : ChatFormatting.YELLOW)),
                        tooltipLine(outputLine.copy().withStyle(station.dockOutputActive() ? ChatFormatting.YELLOW : ChatFormatting.GRAY)),
                        tooltipLine(positionLine.copy().withStyle(ChatFormatting.DARK_GRAY)),
                        tooltipLine(hintLine.copy().withStyle(ChatFormatting.DARK_GRAY))
                )
        );
    }

    private static ShipTransponderMenu.ViewSnapshot buildCargoViewSnapshot(AirshipStationBlockEntity station) {
        return cargoViewFromSummary(station.linkedCargoSummary());
    }

    private static ShipTransponderMenu.ViewSnapshot cargoViewFromSummary(LinkedCargoSummary summary) {
        Component compact;
        if (!summary.hasLinks()) {
            compact = Component.translatable("gui.create_aeronautics_automated_logistics.cargo.none");
        } else if (summary.staleLinks() > 0) {
            compact = Component.translatable(
                    "gui.create_aeronautics_automated_logistics.cargo.compact.partial",
                    summary.validLinks(),
                    summary.totalLinks()
            );
        } else {
            compact = Component.translatable(
                    "gui.create_aeronautics_automated_logistics.cargo.compact.linked",
                    summary.totalLinks()
            );
        }
        if (!summary.hasLinks()) {
            return new ShipTransponderMenu.ViewSnapshot(
                    compact.getString(),
                    0xFFFFD98A,
                    false,
                    List.of(
                            tooltipLine(compact.copy().withStyle(ChatFormatting.YELLOW)),
                            tooltipLine(Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.none").withStyle(ChatFormatting.GRAY))
                    )
            );
        }
        String hintKey = summary.staleLinks() > 0
                ? "gui.create_aeronautics_automated_logistics.cargo.hover.partial"
                : "gui.create_aeronautics_automated_logistics.cargo.hover.linked";
        return new ShipTransponderMenu.ViewSnapshot(
                compact.getString(),
                summary.staleLinks() > 0 ? 0xFFFFD98A : 0xFFAFC7DE,
                true,
                List.of(
                        tooltipLine(Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.status", compact)
                                .withStyle(summary.staleLinks() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY)),
                        tooltipLine(Component.translatable(
                                "gui.create_aeronautics_automated_logistics.cargo.hover.contents",
                                summary.itemLinks(),
                                summary.fluidLinks()
                        ).withStyle(ChatFormatting.GRAY)),
                        tooltipLine(Component.translatable(
                                "gui.create_aeronautics_automated_logistics.cargo.hover.validity",
                                summary.validLinks(),
                                summary.staleLinks()
                        ).withStyle(ChatFormatting.DARK_GRAY)),
                        tooltipLine(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY))
                )
        );
    }

    private static ShipTransponderMenu.StatusTooltipLine tooltipLine(Component component) {
        net.minecraft.network.chat.TextColor color = component.getStyle().getColor();
        return new ShipTransponderMenu.StatusTooltipLine(
                component.getString(),
                color == null ? 0xFFFFFF : color.getValue()
        );
    }

    private static Component componentFromLine(ShipTransponderMenu.StatusTooltipLine line) {
        return Component.literal(line.text())
                .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(line.color())));
    }

    private static List<Component> componentsFromSnapshot(List<ShipTransponderMenu.StatusTooltipLine> lines) {
        return lines.stream().map(AirshipStationMenu::componentFromLine).toList();
    }

    private static Optional<ShipTransponderBlockEntity> selectedTransponderForSummary(ServerLevel level, Optional<UUID> transponderId) {
        return transponderId.flatMap(id -> selectedTransponderForSummary(level, id));
    }

    private static List<ShipChoiceSnapshot> buildShipChoiceSnapshots(
            ServerPlayer player,
            AirshipStationBlockEntity station,
            Optional<UUID> selectedTransponderId
    ) {
        double landingRadius = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        double landingRadiusSqr = landingRadius * landingRadius;
        return sortedShipsForSummary(player, station).stream().map(snapshot -> {
            boolean selected = selectedTransponderId.map(snapshot.transponderId()::equals).orElse(false);
            boolean available = snapshot.controllerRef().isPresent();
            Vec3 shipPos = snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos()));
            double distance = Math.sqrt(station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z));
            boolean inRange = station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z) <= landingRadiusSqr;
            return new ShipChoiceSnapshot(
                    snapshot.transponderId(),
                    snapshot.shipName(),
                    available ? (int) distance + "m" : "Not Found",
                    available ? (inRange ? 0xFF8BE77A : 0xFFFFC66E) : 0xFFFF8C8C,
                    selected
            );
        }).toList();
    }

    private static List<ShipTransponderSnapshot> sortedShipsForSummary(ServerPlayer player, AirshipStationBlockEntity station) {
        return ShipTransponderRegistry.knownShips(player.serverLevel().dimension()).stream()
                .filter(snapshot -> player.serverLevel().getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder
                        && transponder.transponderId().equals(snapshot.transponderId()))
                .sorted(Comparator
                        .comparingDouble((ShipTransponderSnapshot snapshot) -> distanceToStationSqrForSummary(station, snapshot))
                        .thenComparingInt(snapshot -> activityPriorityForSummary(player, snapshot))
                        .thenComparing(snapshot -> snapshot.transponderId().toString()))
                .toList();
    }

    private static double distanceToStationSqrForSummary(AirshipStationBlockEntity station, ShipTransponderSnapshot snapshot) {
        Vec3 shipPos = snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos()));
        return station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z);
    }

    private static int activityPriorityForSummary(ServerPlayer player, ShipTransponderSnapshot snapshot) {
        if (isSelectedShipRecordingForSummary(player, snapshot.transponderId())) {
            return 0;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(snapshot.transponderId())) {
            return 1;
        }
        return 2;
    }

    private static boolean isSelectedShipRecordingForSummary(Player player, UUID transponderId) {
        Optional<ShipTransponderSnapshot> snapshot = ShipTransponderRegistry.snapshot(transponderId);
        if (snapshot.isEmpty()) {
            return false;
        }
        Optional<RecordingSession> activeRecording = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (activeRecording.isEmpty()) {
            return false;
        }
        if (snapshot.get().controllerRef().isPresent() && snapshot.get().controllerRef().get().equals(activeRecording.get().controllerRef())) {
            return true;
        }
        return snapshot.get().runtimeShipId()
                .flatMap(runtimeId -> activeRecording.get().controllerRef().vehicleId().map(runtimeId::equals))
                .orElse(false);
    }

    private static SelectedShipState buildSelectedShipState(
            ServerPlayer player,
            AirshipStationBlockEntity station,
            ShipTransponderBlockEntity transponder
    ) {
        boolean recording = transponder.recordingDestinationStationId().isPresent();
        boolean ready = hasReadyRoutePlanForSummary(player, transponder);
        boolean canControl = StationPermissionService.canControl(player, station)
                && TransponderPermissionService.canControl(player, transponder);
        boolean canRun = canControl
                && !station.isRecording()
                && (!transponder.scheduleActive() || transponder.scheduleHeld())
                && transponder.hasOwnedStops()
                && ready;
        boolean canStop = canControl && transponder.scheduleActive();
        return new SelectedShipState(
                true,
                recording,
                transponder.scheduleActive(),
                transponder.runtimeStatus(),
                transponder.dockOutputActive(),
                AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId()),
                transponder.hasOwnedStops(),
                ready,
                canRun,
                canStop
        );
    }

    private static boolean hasReadyRoutePlanForSummary(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        int minimumStops = AutomatedLogisticsConfig.allowOneWayRoutePlans() ? 1 : 2;
        return transponder.ownedSchedule().entries().size() >= minimumStops
                && hasResolvableRouteChainForSummary(player, transponder);
    }

    private static boolean hasResolvableRouteChainForSummary(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        AirshipSchedule schedule = transponder.ownedSchedule();
        if (schedule.entries().isEmpty()) {
            return false;
        }
        AirshipScheduleEntry firstEntry = schedule.entries().getFirst();
        if (firstEntry.targetStationId().isEmpty()) {
            return false;
        }
        Optional<RouteSegment> firstSegment = resolveInitialScheduledSegmentForSummary(
                firstEntry,
                firstEntry.targetStationId().get(),
                transponder.transponderId(),
                player
        );
        if (firstSegment.isEmpty()) {
            return false;
        }
        UUID currentStationId = firstEntry.targetStationId().get();
        for (int i = 1; i < schedule.entries().size(); i++) {
            AirshipScheduleEntry entry = schedule.entries().get(i);
            if (entry.targetStationId().isEmpty()) {
                return false;
            }
            UUID targetStationId = entry.targetStationId().get();
            Optional<RouteSegment> segment = resolveScheduledSegmentForSummary(
                    entry,
                    currentStationId,
                    targetStationId,
                    transponder.transponderId(),
                    player
            );
            if (segment.isEmpty()) {
                return false;
            }
            currentStationId = targetStationId;
        }
        return true;
    }

    public record RouteChoiceSummary(
            UUID id,
            UUID startStationId,
            String startStationName,
            UUID endStationId,
            String endStationName,
            UUID transponderId,
            String shipName,
            ResourceKey<net.minecraft.world.level.Level> dimension,
            int pointCount,
            long createdEpochMillis
    ) {
    }

    private record OpenData(
            BlockPos stationPos,
            Optional<UUID> initialSelectedTransponderId,
            String initialSelectedShipName,
            int initialCargoRevision,
            LinkedCargoSummary initialCargoSummary,
            List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries,
            Optional<CargoFailureContext> initialCargoFailureContext,
            ClientState clientState
    ) {
    }

    public record ShipChoiceSnapshot(UUID transponderId, String shipName, String statusText, int statusColor, boolean selected) {
    }

    public record SelectedShipState(
            boolean present,
            boolean recording,
            boolean scheduleActive,
            RouteStatus runtimeStatus,
            boolean dockOutputActive,
            Optional<PlaybackFailure> failure,
            boolean hasOwnedStops,
            boolean readyRoute,
            boolean canRun,
            boolean canStop
    ) {
        public static SelectedShipState empty() {
            return new SelectedShipState(false, false, false, RouteStatus.IDLE, false, Optional.empty(), false, false, false, false);
        }
    }

    public record ClientState(
            Optional<UUID> stationId,
            String stationName,
            boolean canControlStation,
            Optional<UUID> selectedTransponderId,
            String selectedShipName,
            List<ShipChoiceSnapshot> shipChoices,
            List<RouteChoiceSummary> routeChoices,
            SelectedShipState selectedShipState,
            ShipTransponderMenu.ViewSnapshot dockView,
            ShipTransponderMenu.ViewSnapshot cargoView
    ) {
        public ClientState {
            stationId = stationId == null ? Optional.empty() : stationId;
            stationName = stationName == null ? "" : stationName;
            selectedTransponderId = selectedTransponderId == null ? Optional.empty() : selectedTransponderId;
            selectedShipName = selectedShipName == null ? "" : selectedShipName;
            shipChoices = shipChoices == null ? List.of() : List.copyOf(shipChoices);
            routeChoices = routeChoices == null ? List.of() : List.copyOf(routeChoices);
            selectedShipState = selectedShipState == null ? SelectedShipState.empty() : selectedShipState;
            dockView = dockView == null ? ShipTransponderMenu.ViewSnapshot.dockNone() : dockView;
            cargoView = cargoView == null ? ShipTransponderMenu.ViewSnapshot.cargoNone() : cargoView;
        }

        public static ClientState empty(Optional<UUID> stationId, String stationName, Optional<UUID> selectedTransponderId, String selectedShipName, List<RouteChoiceSummary> routeChoices) {
            return new ClientState(
                    stationId,
                    stationName,
                    false,
                    selectedTransponderId,
                    selectedShipName,
                    List.of(),
                    routeChoices,
                    SelectedShipState.empty(),
                    ShipTransponderMenu.ViewSnapshot.dockNone(),
                    ShipTransponderMenu.ViewSnapshot.cargoNone()
            );
        }

        public ClientState withRouteChoices(List<RouteChoiceSummary> routeChoices) {
            return new ClientState(stationId, stationName, canControlStation, selectedTransponderId, selectedShipName, shipChoices, routeChoices, selectedShipState, dockView, cargoView);
        }
    }

    private List<ShipTransponderSnapshot> sortedShips(ServerPlayer player, AirshipStationBlockEntity station) {
        return liveKnownShips(player.serverLevel()).stream()
                .sorted(Comparator
                        .comparingDouble((ShipTransponderSnapshot snapshot) -> distanceToStationSqr(station, snapshot))
                        .thenComparingInt(snapshot -> activityPriority(player, snapshot))
                        .thenComparing(snapshot -> snapshot.transponderId().toString()))
                .toList();
    }

    private List<ShipTransponderSnapshot> liveKnownShips(net.minecraft.world.level.Level level) {
        return ShipTransponderRegistry.knownShips(level.dimension()).stream()
                .filter(snapshot -> level.getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder
                        && transponder.transponderId().equals(snapshot.transponderId()))
                .toList();
    }

    private double distanceToStationSqr(AirshipStationBlockEntity station, ShipTransponderSnapshot snapshot) {
        Vec3 shipPos = snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos()));
        return station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z);
    }

    private int activityPriority(ServerPlayer player, ShipTransponderSnapshot snapshot) {
        if (isSelectedShipRecording(player, snapshot.transponderId())) {
            return 0;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(snapshot.transponderId())) {
            return 1;
        }
        return 2;
    }

    private boolean isSelectedShipRecording(Player player, UUID transponderId) {
        Optional<ShipTransponderSnapshot> snapshot = ShipTransponderRegistry.snapshot(transponderId);
        if (snapshot.isEmpty()) {
            return false;
        }
        Optional<RecordingSession> activeRecording = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (activeRecording.isEmpty()) {
            return false;
        }

        if (snapshot.get().controllerRef().isPresent() && snapshot.get().controllerRef().get().equals(activeRecording.get().controllerRef())) {
            return true;
        }
        return snapshot.get().runtimeShipId()
                .flatMap(runtimeId -> activeRecording.get().controllerRef().vehicleId().map(runtimeId::equals))
                .orElse(false);
    }

    private boolean canControlRoute(ServerPlayer player, Route route) {
        return route.ownerId()
                .map(ownerId -> ownerId.equals(player.getUUID()))
                .orElse(false)
                || player.server.getProfilePermissions(player.getGameProfile()) >= 2;
    }

    private boolean canControlSegment(ServerPlayer player, RouteSegment segment) {
        return segment.ownerId()
                .map(ownerId -> ownerId.equals(player.getUUID()))
                .orElse(false)
                || player.server.getProfilePermissions(player.getGameProfile()) >= 2;
    }

}

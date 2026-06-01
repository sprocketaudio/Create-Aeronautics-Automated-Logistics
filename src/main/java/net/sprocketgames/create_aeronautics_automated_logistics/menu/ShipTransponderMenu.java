package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import com.simibubi.create.AllSoundEvents;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AirshipScheduleExecutionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingSession;
import java.util.Locale;
import java.util.ArrayList;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public class ShipTransponderMenu extends AbstractContainerMenu {
    public static final int ACTION_START_INSTALLED_SCHEDULE = 0;
    public static final int ACTION_STOP_SCHEDULE = 1;
    public static final int ACTION_TOGGLE_PREVIEW = 2;
    public static final int ACTION_BEGIN_LINK_DOCK = 3;
    public static final int ACTION_CLEAR_DOCK_LINK = 4;
    public static final int ACTION_EDIT_INSTALLED_SCHEDULE = 5;
    public static final int ACTION_LINK_CARGO = 6;
    public static final int ACTION_CLEAR_CARGO = 7;
    public static final int ACTION_SHOW_CARGO = 8;
    private static final int PLAYER_INVENTORY_START = 0;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final BlockPos transponderPos;
    private final boolean initialRecordingMode;
    private final boolean initialRecordingSessionActive;
    private final boolean initialAppendToSchedule;
    private final Optional<UUID> initialRecordingDestinationStationId;
    private final LinkedCargoSummary initialCargoSummary;
    private final List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries;

    public ShipTransponderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                playerInventory,
                buffer.readBlockPos(),
                buffer.readableBytes() > 0 && buffer.readBoolean(),
                buffer.readableBytes() > 0 && buffer.readBoolean(),
                buffer.readableBytes() > 0 && buffer.readBoolean(),
                buffer.readableBytes() > 0 && buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty(),
                buffer.readableBytes() >= Integer.BYTES * 5
                        ? new LinkedCargoSummary(
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readInt()
                        )
                        : new LinkedCargoSummary(0, 0, 0, 0, 0),
                buffer.readableBytes() >= Integer.BYTES
                        ? IntStream.range(0, buffer.readInt())
                                .mapToObj(ignored -> new net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry(
                                        buffer.readBlockPos(),
                                        buffer.readBoolean(),
                                        buffer.readBoolean()
                                ))
                                .toList()
                        : List.of()
        );
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos) {
        this(containerId, playerInventory, transponderPos, false, false, false, Optional.empty(), new LinkedCargoSummary(0, 0, 0, 0, 0), List.of());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos, boolean initialRecordingMode) {
        this(containerId, playerInventory, transponderPos, initialRecordingMode, false, false, Optional.empty(), new LinkedCargoSummary(0, 0, 0, 0, 0), List.of());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos, boolean initialRecordingMode, boolean initialRecordingSessionActive, boolean initialAppendToSchedule, Optional<UUID> initialRecordingDestinationStationId, LinkedCargoSummary initialCargoSummary, List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries) {
        super(ModMenus.SHIP_TRANSPONDER.get(), containerId);
        this.transponderPos = transponderPos;
        this.initialRecordingMode = initialRecordingMode;
        this.initialRecordingSessionActive = initialRecordingSessionActive;
        this.initialAppendToSchedule = initialAppendToSchedule;
        this.initialRecordingDestinationStationId = initialRecordingDestinationStationId == null ? Optional.empty() : initialRecordingDestinationStationId;
        this.initialCargoSummary = initialCargoSummary == null ? new LinkedCargoSummary(0, 0, 0, 0, 0) : initialCargoSummary;
        this.initialLinkedCargoEntries = initialLinkedCargoEntries == null ? List.of() : List.copyOf(initialLinkedCargoEntries);
        addPlayerInventorySlots(playerInventory, 17, 198);
    }

    public BlockPos transponderPos() {
        return transponderPos;
    }

    public boolean initialRecordingMode() {
        return initialRecordingMode;
    }

    public boolean initialRecordingSessionActive() {
        return initialRecordingSessionActive;
    }

    public boolean initialAppendToSchedule() {
        return initialAppendToSchedule;
    }

    public Optional<UUID> initialRecordingDestinationStationId() {
        return initialRecordingDestinationStationId;
    }

    public static void writeCargoSummary(FriendlyByteBuf buffer, LinkedCargoSummary summary) {
        buffer.writeInt(summary.totalLinks());
        buffer.writeInt(summary.validLinks());
        buffer.writeInt(summary.staleLinks());
        buffer.writeInt(summary.itemLinks());
        buffer.writeInt(summary.fluidLinks());
    }

    public static void writeLinkedCargoEntries(FriendlyByteBuf buffer, List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> entries) {
        buffer.writeInt(entries.size());
        for (net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry entry : entries) {
            buffer.writeBlockPos(entry.pos());
            buffer.writeBoolean(entry.itemStorage());
            buffer.writeBoolean(entry.fluidStorage());
        }
    }

    public static InitialRecordingState resolveInitialRecordingState(ServerPlayer player, ShipTransponderBlockEntity transponder, boolean preferredMode) {
        boolean sessionActive = isActiveRecordingForTransponder(player, transponder);
        return new InitialRecordingState(preferredMode || sessionActive, sessionActive, transponder.appendToSchedule());
    }

    private static boolean isActiveRecordingForTransponder(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        Optional<RecordingSession> session = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (session.isEmpty()) {
            return false;
        }
        RecordingSession active = session.get();
        Optional<UUID> activeVehicleId = active.controllerRef().vehicleId();
        Optional<UUID> runtimeVehicleId = transponder.runtimeShipId();
        if (activeVehicleId.isPresent() && runtimeVehicleId.isPresent() && activeVehicleId.get().equals(runtimeVehicleId.get())) {
            return true;
        }
        if (player.serverLevel().getBlockEntity(active.stationPos()) instanceof AirshipStationBlockEntity station) {
            return station.selectedTransponderId().filter(transponder.transponderId()::equals).isPresent();
        }
        return false;
    }

    public record InitialRecordingState(boolean recordingMode, boolean recordingSessionActive, boolean appendToSchedule) {
    }

    public String shipName(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.shipName();
        }
        return "";
    }

    public Component shipIdText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return Component.literal(IdentityNames.shortId(transponder.transponderId()));
        }
        return Component.literal("-");
    }

    public Component runtimeShipText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.runtimeShipId()
                    .map(id -> Component.literal(IdentityNames.shortId(id)))
                    .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.unavailable"));
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.missing");
    }

    public Component lastKnownPositionText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            Optional<Vec3> position = transponder.lastKnownPosition();
            if (position.isPresent()) {
                Vec3 pos = position.get();
                return Component.literal((int) pos.x + ", " + (int) pos.y + ", " + (int) pos.z);
            }
        }
        return Component.literal("-");
    }

    public Component dockText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.empty();
        }
        return dockTooltip(player).isEmpty() ? Component.empty() : dockTooltip(player).getFirst();
    }

    public List<Component> dockTooltip(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return List.of();
        }
        Component statusLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.status",
                dockCompactText(player)
        );
        Component outputLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.redstone_output",
                Component.translatable(
                        transponder.dockOutputActive()
                                ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                                : "gui.create_aeronautics_automated_logistics.dock.output.off"
                )
        );
        Component distanceLine = transponder.shipDockPos()
                .map(dockPos -> {
                    double distance = Math.sqrt(transponderPos.distSqr(dockPos));
                    int meters = (int) Math.round(distance);
                    return Component.translatable(
                            "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.distance",
                            meters
                    );
                })
                .orElseGet(() -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.distance_unknown"
                ));
        Component hintLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover." + transponder.shipDockStatus().name().toLowerCase(Locale.ROOT)
        );
        return List.of(
                statusLine.copy().withStyle(transponder.shipDockStatus() == DockLinkStatus.LINKED ? ChatFormatting.GRAY : ChatFormatting.YELLOW),
                outputLine.copy().withStyle(transponder.dockOutputActive() ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                distanceLine.copy().withStyle(ChatFormatting.DARK_GRAY),
                hintLine.copy().withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    public Component dockCompactText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.dock.not_connected");
        }
        DockLinkStatus status = transponder.shipDockStatus();
        if (status == DockLinkStatus.AMBIGUOUS) {
            status = DockLinkStatus.INVALID;
        }
        return status == DockLinkStatus.LINKED && transponder.shipDockPos().isPresent()
                ? Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact.linked")
                : Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact." + status.name().toLowerCase(Locale.ROOT));
    }

    public int dockStatusColor(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return 0xFFAFC7DE;
        }
        return switch (transponder.shipDockStatus()) {
            case LINKED -> 0xFFAFC7DE;
            case UNKNOWN, MISSING -> 0xFFFFD98A;
            case AMBIGUOUS, INVALID -> 0xFFFFB4B4;
        };
    }

    public Component cargoCompactText(Player player) {
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
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return 0xFFFFD98A;
        }
        return summary.staleLinks() > 0 ? 0xFFFFD98A : 0xFFAFC7DE;
    }

    public boolean hasLinkedCargo(Player player) {
        return resolveCargoSummary(player).hasLinks();
    }

    public boolean isCargoLinkPending(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && CargoLinkInteractionService.hasPendingTransponderLink(serverPlayer, transponderPos);
    }

    private LinkedCargoSummary resolveCargoSummary(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return initialCargoSummary;
        }
        return transponder.linkedCargoSummary();
    }

    public Component installedScheduleText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.empty();
        }
        return transponder.installedScheduleTitle();
    }

    public boolean hasInstalledSchedule(Player player) {
        return player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity;
    }

    public boolean hasOwnedStops(Player player) {
        return player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder
                && transponder.hasOwnedStops();
    }

    public boolean canPreviewOwnedRoute(Player player) {
        return player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder
                && transponder.hasOwnedStops()
                && hasResolvableInstalledRouteChain(player);
    }

    public boolean isScheduleRunning(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        return transponder.scheduleActive();
    }

    public boolean isScheduleHeld(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        return transponder.scheduleHeld();
    }

    public boolean isScheduleSlotLocked(Player player) {
        return isScheduleRunning(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        if (!TransponderPermissionService.ensureCanControl(serverPlayer, transponder)) {
            return false;
        }

        return switch (id) {
            case ACTION_START_INSTALLED_SCHEDULE -> startInstalledSchedule(serverPlayer, transponder);
            case ACTION_STOP_SCHEDULE -> stopSchedule(serverPlayer, transponder);
            case ACTION_TOGGLE_PREVIEW -> togglePreview(serverPlayer, transponder);
            case ACTION_BEGIN_LINK_DOCK -> beginLinkDock(serverPlayer, transponder);
            case ACTION_CLEAR_DOCK_LINK -> clearDockLink(serverPlayer, transponder);
            case ACTION_EDIT_INSTALLED_SCHEDULE -> editInstalledSchedule(serverPlayer, transponder);
            case ACTION_LINK_CARGO -> linkCargo(serverPlayer, transponder);
            case ACTION_CLEAR_CARGO -> clearCargo(serverPlayer, transponder);
            case ACTION_SHOW_CARGO -> true;
            default -> false;
        };
    }

    public boolean canControlTransponderLocally(Player player) {
        if (!AutomatedLogisticsConfig.RESTRICT_TRANSPONDER_CONTROL_TO_OWNER.get()) {
            return true;
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return true;
        }
        return transponder.ownerId().isEmpty() || transponder.ownerId().get().equals(player.getUUID());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack stackCopy = sourceStack.copy();

        if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(sourceStack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= HOTBAR_START && index < HOTBAR_END) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return stackCopy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
                transponderPos.getX() + 0.5D,
                transponderPos.getY() + 0.5D,
                transponderPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    private boolean startInstalledSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (AutomatedLogisticsServices.SCHEDULES.isHeld(transponder.transponderId())) {
            boolean resumed = AutomatedLogisticsServices.SCHEDULES.resumeHeldPlayback(player.serverLevel(), transponder.transponderId());
            if (resumed) {
                AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f);
                return true;
            }
            PlaybackFailure failure = AutomatedLogisticsServices.SCHEDULES
                    .heldFailure(transponder.transponderId())
                    .orElse(PlaybackFailure.INVALID_ROUTE);
            showMenuWarning(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.playback.failed",
                    Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
            ));
            return false;
        }
        if (!transponder.hasOwnedStops()) {
            showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_no_stops"));
            return false;
        }
        var result = AutomatedLogisticsServices.SCHEDULES
                .startFromTransponder(player, transponder, transponder.ownedSchedule());
        result.value().ifPresent(routeId ->
                AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f));
        result.failure().ifPresent(failure -> {
            Component message = switch (failure) {
                case START_TOO_FAR_FROM_ROUTE -> Component.translatable(
                        "message.create_aeronautics_automated_logistics.playback.move_to_valid_start_station"
                );
                case WRONG_START_STATION -> AutomatedLogisticsServices.SCHEDULES
                        .currentStartStationName(player.serverLevel(), transponder)
                        .map(stationName -> Component.translatable(
                                "message.create_aeronautics_automated_logistics.playback.wrong_start_station",
                                stationName
                        ))
                        .orElseGet(() -> Component.translatable(
                                "message.create_aeronautics_automated_logistics.playback.move_to_valid_start_station"
                        ));
                case INVALID_ROUTE -> {
                    Optional<String> currentStationName = AutomatedLogisticsServices.SCHEDULES
                            .currentStartStationName(player.serverLevel(), transponder);
                    Optional<String> nextStopName = AutomatedLogisticsServices.SCHEDULES
                            .nextStopNameForCurrentStation(player.serverLevel(), transponder, transponder.ownedSchedule());
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
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
        });
        return result.value().isPresent();
    }

    private boolean stopSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        UUID transponderId = transponder.transponderId();
        if (!AutomatedLogisticsServices.SCHEDULES.hasActiveRuntime(player.serverLevel(), transponderId)) {
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), transponderId);
        AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(player.level(), transponder.getBlockPos(), 0.45f, 1.2f);
        return true;
    }

    private boolean togglePreview(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!transponder.hasOwnedStops()) {
            PacketDistributor.sendToPlayer(player, new SetFlightPathPreviewPayload(false, List.of(), List.of()));
            return false;
        }
        PreviewPath preview = previewPath(player.serverLevel(), transponder, transponder.ownedSchedule());
        boolean enabled = !preview.points().isEmpty();
        PacketDistributor.sendToPlayer(player, new SetFlightPathPreviewPayload(enabled, preview.points(), preview.legEndIndices()));
        return enabled;
    }

    private PreviewPath previewPath(ServerLevel level, ShipTransponderBlockEntity transponder, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return PreviewPath.empty();
        }
        Optional<AirshipScheduleExecutionService.ResolvedStartContext> startContext =
                AutomatedLogisticsServices.SCHEDULES.resolveStartContext(level, transponder, schedule);
        if (startContext.isEmpty()) {
            return PreviewPath.empty();
        }
        List<Vec3> points = new ArrayList<>();
        List<Integer> legEndIndices = new ArrayList<>();
        UUID stationId = startContext.get().stationId();
        AirshipSchedule runtimeSchedule = startContext.get().runtimeSchedule();
        for (AirshipScheduleEntry entry : runtimeSchedule.entries()) {
            if (entry.targetStationId().isEmpty()) {
                break;
            }
            UUID fromStationId = stationId;
            Optional<RouteSegment> segment = entry.pinnedSegmentId()
                    .flatMap(net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry::byId)
                    .filter(candidate -> candidate.startStationId().equals(fromStationId))
                    .filter(candidate -> candidate.endStationId().equals(entry.targetStationId().get()))
                    .filter(candidate -> candidate.dimension().equals(level.dimension()))
                    .filter(candidate -> candidate.transponderId().equals(transponder.transponderId()))
                    .or(() -> RouteSegmentResolver.newestFor(
                            fromStationId,
                            entry.targetStationId().get(),
                            level.dimension(),
                            Optional.of(transponder.transponderId())
                    ));
            if (segment.isEmpty()) {
                break;
            }
            segment.get().points().forEach(routePoint -> points.add(routePoint.position()));
            if (points.size() >= 2) {
                legEndIndices.add(points.size() - 1);
            }
            stationId = entry.targetStationId().get();
        }
        return new PreviewPath(points, legEndIndices);
    }

    private Optional<UUID> resolveStartStationId(ServerLevel level, ShipTransponderBlockEntity transponder, AirshipSchedule schedule) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            Optional<UUID> runningStation = AutomatedLogisticsServices.SCHEDULES.currentStationId(transponder.transponderId());
            if (runningStation.isPresent()) {
                return runningStation;
            }
        }
        return AutomatedLogisticsServices.SCHEDULES.resolveStartContext(level, transponder, schedule)
                .map(AirshipScheduleExecutionService.ResolvedStartContext::stationId);
    }

    private record PreviewPath(List<Vec3> points, List<Integer> legEndIndices) {
        private static PreviewPath empty() {
            return new PreviewPath(List.of(), List.of());
        }
    }

    public Component runtimeStateText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.idle");
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
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return failureStatusText(failure.get());
        }
        if (!transponder.hasOwnedStops()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_stops_status");
        }
        if (!hasResolvableInstalledRouteChain(player)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ready");
    }

    public int runtimeStateColor(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return 0xAFC7DE;
        }
        RouteStatus runtimeStatus = transponder.runtimeStatus();
        if (transponder.scheduleActive()) {
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
        if (!hasResolvableInstalledRouteChain(player)) {
            return 0xFFFFD98A;
        }
        return 0xAFC7DE;
    }

    public Optional<BlockPos> dockPreviewPos(Player player) {
        if (!canControlTransponderLocally(player)) {
            return Optional.empty();
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Optional.empty();
        }
        return transponder.shipDockPos();
    }

    public List<BlockPos> cargoPreviewPositions(Player player) {
        return cargoPreviewPositionGroups(player).stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    public List<List<BlockPos>> cargoPreviewPositionGroups(Player player) {
        if (!canControlTransponderLocally(player)) {
            return List.of();
        }
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> linkedEntries = List.of();
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            linkedEntries = transponder.linkedCargo();
        } else if (!initialLinkedCargoEntries.isEmpty()) {
            linkedEntries = initialLinkedCargoEntries;
        }
        if (linkedEntries.isEmpty()) {
            return List.of();
        }
        List<List<BlockPos>> expanded = CargoLinkSupport.expandPreviewPositionGroups(player.level(), transponderPos, 6, linkedEntries);
        if (!expanded.isEmpty()) {
            return expanded;
        }
        return linkedEntries.stream()
                .map(entry -> List.of(entry.pos()))
                .toList();
    }

    public List<Component> runtimeFailureTooltip(Player player) {
        return runtimeStatusTooltip(player);
    }

    public List<Component> runtimeStatusTooltip(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return List.of();
        }
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
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason." + suffix)
                                .withStyle(ChatFormatting.GRAY),
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
        if (failure.isEmpty() && !transponder.hasOwnedStops()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_stops.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_stops.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (failure.isEmpty() && !hasResolvableInstalledRouteChain(player)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_route.2").withStyle(ChatFormatting.GRAY)
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
                failureStatusText(value).copy().withStyle(ChatFormatting.RED),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason." + suffix)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint." + suffix)
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    private Component failureStatusText(PlaybackFailure failure) {
        return switch (failure) {
            case INVALID_ROUTE, DIMENSION_MISMATCH -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
            case STATION_MISSING -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.station_missing_status");
            case VEHICLE_MISSING, VEHICLE_UNLOADED, MISSING_CONTROLLER -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ship_missing_status");
            case START_TOO_FAR_FROM_ROUTE -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.start_blocked_status");
            case WRONG_START_STATION -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.wrong_station_status");
            case COLLISION_OR_OBSTRUCTION, STUCK -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.blocked_status");
            case MISSING_DOCK, AMBIGUOUS_DOCK, DOCK_LOCK_FAILED -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock_problem_status");
            case REDSTONE_LINK_UNCONFIGURED, CARGO_STORAGE_MISSING, CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE, ALREADY_RUNNING -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.needs_attention_status");
        };
    }

    public boolean shouldSuggestRecordingMode(Player player) {
        return !hasResolvableInstalledRouteChain(player);
    }

    private boolean hasResolvableInstalledRouteChain(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        if (!transponder.hasOwnedStops()) {
            return false;
        }
        return hasRecordedRouteChain(player, transponder.ownedSchedule(), transponder.transponderId());
    }

    private boolean hasRecordedRouteChain(Player player, AirshipSchedule schedule, UUID transponderId) {
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
                .filter(candidate -> candidate.transponderId().equals(transponderId));
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
                    .filter(candidate -> candidate.transponderId().equals(transponderId))
                    .or(() -> RouteSegmentResolver.newestFor(
                            fromStationId,
                            nextStationId,
                            player.level().dimension(),
                            Optional.of(transponderId)
                    ));
            if (segment.isEmpty()) {
                return false;
            }
            currentStationId = nextStationId;
        }
        return true;
    }

    private void showMenuWarning(Player player, Component message) {
        actionBar(player, message);
    }

    private void actionBar(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            SetMenuActionBarMessagePayload.send(serverPlayer, message);
        }
    }

    private boolean beginLinkDock(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        DockLinkInteractionService.beginTransponderLink(player, transponder.getBlockPos());
        return true;
    }

    private boolean clearDockLink(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (DockLinkInteractionService.hasPendingTransponderLink(player, transponder.getBlockPos())) {
            return DockLinkInteractionService.cancelPending(player);
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.locked_while_running"));
            return false;
        }
        transponder.clearShipDockLink();
        DockLinkInteractionService.clearPending(player);
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.cleared"));
        return true;
    }

    private boolean editInstalledSchedule(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        AirshipSchedule schedule = transponder.ownedSchedule();
        player.openMenu(
                new net.minecraft.world.SimpleMenuProvider(
                        (containerId, inventory, ignoredPlayer) -> new AirshipScheduleMenu(containerId, inventory, transponder.getBlockPos(), false),
                        Component.literal(schedule.title())
                ),
                buffer -> {
                    buffer.writeBoolean(true);
                    buffer.writeBlockPos(transponder.getBlockPos());
                    buffer.writeBoolean(false);
                }
        );
        return true;
    }

    private boolean linkCargo(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        CargoLinkInteractionService.beginTransponderLink(player, transponder.getBlockPos());
        return true;
    }

    private boolean clearCargo(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        if (CargoLinkInteractionService.hasPendingTransponderLink(player, transponder.getBlockPos())) {
            return CargoLinkInteractionService.cancelPending(player);
        }
        if (!transponder.clearLinkedCargo()) {
            return false;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.cleared"));
        AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f);
        return true;
    }

    private void addPlayerInventorySlots(Inventory inventory, int leftX, int topY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, leftX + col * 18, topY + row * 18));
            }
        }
        int hotbarY = topY + 58;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, leftX + col * 18, hotbarY));
        }
    }
}

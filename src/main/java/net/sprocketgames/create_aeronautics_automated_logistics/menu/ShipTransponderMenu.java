package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import com.simibubi.create.AllSoundEvents;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import java.util.Locale;
import java.util.ArrayList;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public class ShipTransponderMenu extends AbstractContainerMenu {
    public static final int ACTION_START_INSTALLED_SCHEDULE = 0;
    public static final int ACTION_STOP_SCHEDULE = 1;
    public static final int ACTION_TOGGLE_PREVIEW = 2;
    public static final int ACTION_BEGIN_LINK_DOCK = 3;
    public static final int ACTION_CLEAR_DOCK_LINK = 4;
    private static final int SCHEDULE_SLOT_INDEX = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final BlockPos transponderPos;

    public ShipTransponderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos) {
        super(ModMenus.SHIP_TRANSPONDER.get(), containerId);
        this.transponderPos = transponderPos;
        if (playerInventory.player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            addSlot(new Slot(transponder, ShipTransponderBlockEntity.INTERNAL_SCHEDULE_SLOT, 44, 64) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return transponder.canInstallSchedule(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId());
                }
            });
        }
        addPlayerInventorySlots(playerInventory, 17, 198);
    }

    public BlockPos transponderPos() {
        return transponderPos;
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
        return transponder.shipDockStatus() == DockLinkStatus.LINKED && transponder.shipDockPos().isPresent()
                ? Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact.linked")
                : Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact." + transponder.shipDockStatus().name().toLowerCase(Locale.ROOT));
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

    public Component installedScheduleText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_schedule");
        }
        return transponder.installedScheduleTitle();
    }

    public boolean hasInstalledSchedule(Player player) {
        return player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder
                && transponder.hasInstalledSchedule();
    }

    public boolean isScheduleRunning(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        return AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId());
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

        return switch (id) {
            case ACTION_START_INSTALLED_SCHEDULE -> startInstalledSchedule(serverPlayer, transponder);
            case ACTION_STOP_SCHEDULE -> stopSchedule(serverPlayer, transponder);
            case ACTION_TOGGLE_PREVIEW -> togglePreview(serverPlayer, transponder);
            case ACTION_BEGIN_LINK_DOCK -> beginLinkDock(serverPlayer, transponder);
            case ACTION_CLEAR_DOCK_LINK -> clearDockLink(serverPlayer, transponder);
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack stackCopy = sourceStack.copy();

        if (index == SCHEDULE_SLOT_INDEX) {
            if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
                showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_schedule_locked"));
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!sourceStack.is(ModItems.AIRSHIP_SCHEDULE.get())) {
                return ItemStack.EMPTY;
            }
            if (!transponder.canInstallSchedule(sourceStack)) {
                showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.wrong_ship_for_transponder"));
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(sourceStack, SCHEDULE_SLOT_INDEX, SCHEDULE_SLOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
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
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }
        if (slotId == SCHEDULE_SLOT_INDEX) {
            if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
                showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_schedule_locked"));
                return;
            }
            ItemStack attemptedInsert = attemptedScheduleInsert(player, dragType, clickType);
            if (!attemptedInsert.isEmpty() && !transponder.canInstallSchedule(attemptedInsert)) {
                showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.wrong_ship_for_transponder"));
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    private ItemStack attemptedScheduleInsert(Player player, int dragType, ClickType clickType) {
        if (clickType == ClickType.PICKUP || clickType == ClickType.PICKUP_ALL) {
            return getCarried();
        }
        if (clickType == ClickType.SWAP && dragType >= 0 && dragType < 9) {
            return player.getInventory().getItem(dragType);
        }
        return ItemStack.EMPTY;
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
        if (!transponder.hasInstalledSchedule()) {
            showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_no_schedule"));
            return false;
        }
        var result = AutomatedLogisticsServices.SCHEDULES
                .startFromTransponder(player, transponder, AirshipScheduleItem.readSchedule(transponder.installedScheduleStack()));
        result.value().ifPresent(routeId ->
                AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f));
        result.failure().ifPresent(failure -> {
            actionBar(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.playback.failed",
                    Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
            ));
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
        });
        return result.value().isPresent();
    }

    private boolean stopSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        UUID transponderId = transponder.transponderId();
        if (!AutomatedLogisticsServices.SCHEDULES.isRunning(transponderId)) {
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), transponderId);
        AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(player.level(), transponder.getBlockPos(), 0.45f, 1.2f);
        return true;
    }

    private boolean togglePreview(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!transponder.hasInstalledSchedule()) {
            PacketDistributor.sendToPlayer(player, new SetFlightPathPreviewPayload(false, List.of(), List.of()));
            return false;
        }
        PreviewPath preview = previewPath(player.serverLevel(), transponder, transponder.installedSchedule());
        boolean enabled = !preview.points().isEmpty();
        PacketDistributor.sendToPlayer(player, new SetFlightPathPreviewPayload(enabled, preview.points(), preview.legEndIndices()));
        return enabled;
    }

    private PreviewPath previewPath(ServerLevel level, ShipTransponderBlockEntity transponder, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return PreviewPath.empty();
        }
        Optional<UUID> currentStationId = resolveStartStationId(level, transponder, schedule);
        if (currentStationId.isEmpty()) {
            return PreviewPath.empty();
        }
        List<Vec3> points = new ArrayList<>();
        List<Integer> legEndIndices = new ArrayList<>();
        UUID stationId = currentStationId.get();
        for (AirshipScheduleEntry entry : schedule.entries()) {
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
        if (schedule.entries().isEmpty()) {
            return Optional.empty();
        }
        return schedule.entries().getFirst().pinnedSegmentId()
                .flatMap(net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry::byId)
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(transponder.transponderId()))
                .map(RouteSegment::startStationId);
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
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            if (transponder.dockOutputActive()) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.docked");
            }
            if (AutomatedLogisticsServices.SCHEDULES.currentStationStatus(player.level(), transponder.transponderId())
                    .filter(status -> status == RouteStatus.WAITING).isPresent()) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.waiting");
            }
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.running");
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return failureStatusText(failure.get());
        }
        if (!transponder.hasInstalledSchedule()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_schedule_status");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ready");
    }

    public int runtimeStateColor(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return 0xAFC7DE;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            return 0xFFE7C46E;
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return 0xFFFFB4B4;
        }
        if (!transponder.hasInstalledSchedule()) {
            return 0xFFFFD98A;
        }
        return 0xAFC7DE;
    }

    public Optional<BlockPos> dockPreviewPos(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Optional.empty();
        }
        return transponder.shipDockPos();
    }

    public List<Component> runtimeFailureTooltip(Player player) {
        return runtimeStatusTooltip(player);
    }

    public List<Component> runtimeStatusTooltip(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return List.of();
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            if (transponder.dockOutputActive()) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (AutomatedLogisticsServices.SCHEDULES.currentStationStatus(player.level(), transponder.transponderId())
                    .filter(status -> status == RouteStatus.WAITING).isPresent()) {
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
        if (failure.isEmpty() && !transponder.hasInstalledSchedule()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_schedule.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_schedule.2").withStyle(ChatFormatting.GRAY)
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
            case COLLISION_OR_OBSTRUCTION -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.blocked_status");
            case MISSING_DOCK, AMBIGUOUS_DOCK, DOCK_LOCK_FAILED -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock_problem_status");
            case CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE, ALREADY_RUNNING -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.needs_attention_status");
        };
    }

    private void showMenuWarning(Player player, Component message) {
        actionBar(player, message);
    }

    private void actionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
        if (player instanceof ServerPlayer serverPlayer) {
            SetMenuActionBarMessagePayload.send(serverPlayer, message);
        }
    }

    private boolean beginLinkDock(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        DockLinkInteractionService.beginTransponderLink(player, transponder.getBlockPos());
        return true;
    }

    private boolean clearDockLink(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        transponder.clearShipDockLink();
        DockLinkInteractionService.clearPending(player);
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.cleared"));
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

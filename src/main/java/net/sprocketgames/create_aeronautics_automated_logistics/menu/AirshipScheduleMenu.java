package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncTransponderOwnedSchedulePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ScheduleRouteCleanup;

public class AirshipScheduleMenu extends AbstractContainerMenu {
    public static final int ACTION_ADD_TRAVEL = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_DUPLICATE = 2;
    public static final int ACTION_MOVE_UP = 3;
    public static final int ACTION_MOVE_DOWN = 4;
    public static final int ACTION_WAIT_DOWN = 5;
    public static final int ACTION_WAIT_UP = 6;
    public static final int ACTION_TOGGLE_LOOP = 7;
    public static final int ACTION_SELECT_PREVIOUS = 8;
    public static final int ACTION_SELECT_NEXT = 9;
    public static final int ACTION_CYCLE_TARGET_STATION = 10;
    public static final int ACTION_TOGGLE_WAIT = 11;
    public static final int ACTION_CYCLE_WAIT_UNIT = 12;
    public static final int ACTION_ADD_CONDITION = 13;
    public static final int ACTION_ADD_ALTERNATIVE_CONDITION = 14;
    public static final int ACTION_PIN_NEWEST_SEGMENT = 15;
    public static final int ACTION_SKIP_CURRENT_STOP = 16;
    public static final int ACTION_SELECT_ENTRY_BASE = 100;
    private static final int WAIT_ADJUST_TICKS = 20 * 5;

    private int selectedIndex;
    private final BlockPos originTransponderPos;
    private final boolean returnToRecordingMode;
    private final AirshipSchedule initialSchedule;

    public AirshipScheduleMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readContext(buffer));
    }

    public AirshipScheduleMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, false);
    }

    private AirshipScheduleMenu(int containerId, Inventory playerInventory, OpenContext context) {
        this(containerId, playerInventory, context.originTransponderPos(), context.returnToRecordingMode(), context.initialSchedule());
    }

    public AirshipScheduleMenu(int containerId, Inventory playerInventory, BlockPos originTransponderPos, boolean returnToRecordingMode) {
        this(containerId, playerInventory, originTransponderPos, returnToRecordingMode, AirshipSchedule.empty());
    }

    public AirshipScheduleMenu(int containerId, Inventory playerInventory, BlockPos originTransponderPos, boolean returnToRecordingMode, AirshipSchedule initialSchedule) {
        super(ModMenus.AIRSHIP_SCHEDULE.get(), containerId);
        this.originTransponderPos = originTransponderPos;
        this.returnToRecordingMode = returnToRecordingMode;
        this.initialSchedule = initialSchedule == null ? AirshipSchedule.empty() : initialSchedule;
        addPlayerInventory(playerInventory);
    }

    private static OpenContext readContext(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return new OpenContext(null, false, AirshipSchedule.empty());
        }
        boolean fromTransponder = buffer.readBoolean();
        if (!fromTransponder || buffer.readableBytes() < 9) {
            return new OpenContext(null, false, AirshipSchedule.empty());
        }
        BlockPos originPos = buffer.readBlockPos();
        boolean returnToRecordingMode = buffer.readBoolean();
        AirshipSchedule initialSchedule = AirshipSchedule.empty();
        if (buffer.readableBytes() > 0) {
            var tag = buffer.readNbt();
            if (tag != null) {
                initialSchedule = AirshipScheduleNbtSerializer.read(tag);
            }
        }
        return new OpenContext(originPos, returnToRecordingMode, initialSchedule);
    }

    private record OpenContext(BlockPos originTransponderPos, boolean returnToRecordingMode, AirshipSchedule initialSchedule) {
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide) {
            return id == ACTION_SKIP_CURRENT_STOP;
        }
        return applyAction(player, id);
    }

    public void handleClientAction(int id, Player player) {
        if (id == ACTION_SELECT_PREVIOUS || id == ACTION_SELECT_NEXT || id >= ACTION_SELECT_ENTRY_BASE) {
            applyAction(player, id);
        }
    }

    public AirshipSchedule schedule(Player player) {
        if (player.level().isClientSide && openedFromTransponder()) {
            return initialSchedule;
        }
        return editableTransponder(player)
                .map(ShipTransponderBlockEntity::ownedSchedule)
                .orElseGet(AirshipSchedule::empty);
    }

    public int selectedIndex(Player player) {
        AirshipSchedule schedule = schedule(player);
        Optional<ShipTransponderBlockEntity> transponder = editableTransponder(player);
        if (transponder.isPresent()) {
            Optional<Integer> currentEntryIndex = net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices.SCHEDULES
                    .currentEntryIndex(transponder.get().transponderId(), schedule);
            if (currentEntryIndex.isPresent() && !schedule.entries().isEmpty()) {
                return Math.max(0, Math.min(currentEntryIndex.get(), schedule.entries().size() - 1));
            }
        }
        clampSelectedIndex(schedule);
        return selectedIndex;
    }

    public Component selectedEntryText(Player player) {
        AirshipSchedule schedule = schedule(player);
        if (schedule.entries().isEmpty()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.no_entries");
        }
        AirshipScheduleEntry entry = schedule.entries().get(selectedIndex(player));
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.selected_entry",
                selectedIndex + 1,
                displayStationName(entry)
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return originTransponderPos != null
                && player.level().getBlockEntity(originTransponderPos) instanceof ShipTransponderBlockEntity;
    }

    public boolean openedFromTransponder() {
        return originTransponderPos != null;
    }

    public boolean isReadOnly(Player player) {
        return editableTransponder(player)
                .map(transponder -> !transponder.isRemoved()
                        && net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId()))
                .orElse(false);
    }

    public Optional<BlockPos> originTransponderPos() {
        return Optional.ofNullable(originTransponderPos);
    }

    public boolean returnToRecordingMode() {
        return returnToRecordingMode;
    }

    public boolean skipStopUiActive(Player player) {
        return editableTransponder(player)
                .map(ShipTransponderBlockEntity::runtimeStatus)
                .map(status -> status == RouteStatus.WAITING || status == RouteStatus.HELD_FAULTED)
                .orElse(false);
    }

    private void addPlayerInventory(Inventory inventory) {
        int startX = 46;
        int startY = 140;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9 + 9;
                addSlot(new Slot(inventory, slotIndex, startX + col * 18, startY + row * 18));
            }
        }
        int hotbarY = 198;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, startX + col * 18, hotbarY));
        }
    }

    private boolean applyAction(Player player, int id) {
        if (id >= ACTION_SELECT_ENTRY_BASE) {
            AirshipSchedule schedule = schedule(player);
            selectedIndex = Math.max(0, Math.min(id - ACTION_SELECT_ENTRY_BASE, Math.max(0, schedule.entries().size() - 1)));
            return true;
        }
        if (id == ACTION_SKIP_CURRENT_STOP) {
            return skipCurrentStop(player);
        }
        if (!canModify(player)) {
            return false;
        }
        if (isReadOnly(player)) {
            return false;
        }
        AirshipSchedule schedule = schedule(player);
        clampSelectedIndex(schedule);
        if (openedFromTransponder()
                && (id == ACTION_ADD_TRAVEL || id == ACTION_DUPLICATE || id == ACTION_CYCLE_TARGET_STATION)) {
            return true;
        }
        AirshipSchedule updated = switch (id) {
                case ACTION_ADD_TRAVEL -> addTravel(schedule);
                case ACTION_REMOVE -> removeSelected(player, schedule);
                case ACTION_DUPLICATE -> duplicateSelected(schedule);
                case ACTION_MOVE_UP -> moveSelected(schedule, -1);
                case ACTION_MOVE_DOWN -> moveSelected(schedule, 1);
                case ACTION_WAIT_DOWN -> adjustSelectedWait(schedule, -WAIT_ADJUST_TICKS);
                case ACTION_WAIT_UP -> adjustSelectedWait(schedule, WAIT_ADJUST_TICKS);
                case ACTION_TOGGLE_LOOP -> schedule.withLoop(!schedule.loop());
                case ACTION_SELECT_PREVIOUS -> select(schedule, -1);
                case ACTION_SELECT_NEXT -> select(schedule, 1);
                case ACTION_CYCLE_TARGET_STATION -> cycleTargetStation(player, schedule);
                case ACTION_TOGGLE_WAIT -> toggleSelectedWait(schedule);
                case ACTION_CYCLE_WAIT_UNIT -> cycleSelectedWaitUnit(schedule);
                case ACTION_ADD_CONDITION -> addCondition(schedule);
                case ACTION_ADD_ALTERNATIVE_CONDITION -> addAlternativeCondition(schedule);
                case ACTION_PIN_NEWEST_SEGMENT -> pinNewestSegment(player, schedule);
                default -> schedule;
            };
        if (updated != schedule) {
            writeSchedule(player, updated);
        }
        return id >= ACTION_ADD_TRAVEL && id <= ACTION_PIN_NEWEST_SEGMENT;
    }

    private AirshipSchedule addTravel(AirshipSchedule schedule) {
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        int insertIndex = entries.isEmpty() ? 0 : Math.min(selectedIndex + 1, entries.size());
        entries.add(insertIndex, AirshipScheduleEntry.blankTravel());
        selectedIndex = insertIndex;
        return schedule.withEntries(entries);
    }

    private AirshipSchedule removeSelected(Player player, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.remove(selectedIndex);
        selectedIndex = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
        return schedule.withEntries(entries);
    }

    private AirshipSchedule duplicateSelected(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.add(selectedIndex + 1, entries.get(selectedIndex));
        selectedIndex++;
        return schedule.withEntries(entries);
    }

    private AirshipSchedule moveSelected(AirshipSchedule schedule, int direction) {
        if (schedule.entries().size() < 2) {
            return schedule;
        }
        int targetIndex = selectedIndex + direction;
        if (targetIndex < 0 || targetIndex >= schedule.entries().size()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry selected = entries.remove(selectedIndex);
        entries.add(targetIndex, selected);
        selectedIndex = targetIndex;
        return schedule.withEntries(entries);
    }

    private AirshipSchedule adjustSelectedWait(AirshipSchedule schedule, int deltaTicks) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(selectedIndex);
        int currentTicks = entry.waitCondition().type() == WaitConditionType.TIMED
                ? entry.waitCondition().durationTicks()
                : 0;
        int unitScaledDelta = Integer.signum(deltaTicks) * entry.waitUnit().ticksPerStep() * 5;
        int nextTicks = Math.max(0, currentTicks + unitScaledDelta);
        entries.set(selectedIndex, entry.withWaitCondition(nextTicks == 0 ? WaitCondition.none() : WaitCondition.timed(nextTicks)));
        return schedule.withEntries(entries);
    }

    private AirshipSchedule toggleSelectedWait(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(selectedIndex);
        WaitCondition next = entry.waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(Math.max(entry.waitUnit().ticksPerStep() * 5, WaitCondition.DEFAULT_TIMED_WAIT_TICKS))
                : WaitCondition.none();
        entries.set(selectedIndex, entry.withWaitCondition(next));
        return schedule.withEntries(entries);
    }

    private AirshipSchedule cycleSelectedWaitUnit(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(selectedIndex);
        entries.set(selectedIndex, entry.withWaitUnit(entry.waitUnit().next()));
        return schedule.withEntries(entries);
    }

    private AirshipSchedule cycleTargetStation(Player player, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipStationSnapshot> stations = stationsForScheduleSelection(player);
        if (stations.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.no_stations"));
            return schedule;
        }

        AirshipScheduleEntry entry = schedule.entries().get(selectedIndex);
        Optional<UUID> current = entry.targetStationId();
        int currentIndex = -1;
        if (current.isPresent()) {
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).stationId().equals(current.get())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        AirshipStationSnapshot selectedStation = stations.get((currentIndex + 1) % stations.size());
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(selectedIndex, entry.withTargetStation(selectedStation.stationId(), selectedStation.stationName()));
        return schedule.withEntries(entries);
    }

    private AirshipSchedule addCondition(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(selectedIndex, entries.get(selectedIndex).withAddedCondition());
        return schedule.withEntries(entries);
    }

    private List<AirshipStationSnapshot> stationsForScheduleSelection(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            List<AirshipStationSnapshot> persisted = IdentityDirectorySavedData.get(serverPlayer.server)
                    .stationSnapshots(serverPlayer.level().dimension());
            if (!persisted.isEmpty()) {
                return persisted;
            }
        }
        return AirshipStationRegistry.knownStations(player.level().dimension());
    }

    private AirshipSchedule addAlternativeCondition(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return schedule;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(selectedIndex, entries.get(selectedIndex).withAddedAlternativeConditionGroup());
        return schedule.withEntries(entries);
    }

    private AirshipSchedule pinNewestSegment(Player player, AirshipSchedule schedule) {
        if (selectedIndex <= 0 || selectedIndex >= schedule.entries().size()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.no_segment_to_pin"));
            return schedule;
        }
        AirshipScheduleEntry previous = schedule.entries().get(selectedIndex - 1);
        AirshipScheduleEntry current = schedule.entries().get(selectedIndex);
        if (previous.targetStationId().isEmpty() || current.targetStationId().isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.no_segment_to_pin"));
            return schedule;
        }

        Optional<RouteSegment> segment = player instanceof ServerPlayer serverPlayer
                ? RouteSegmentResolver.newestFor(
                        serverPlayer.serverLevel(),
                        previous.targetStationId().get(),
                        current.targetStationId().get(),
                        Optional.empty()
                )
                : RouteSegmentResolver.newestFor(
                        previous.targetStationId().get(),
                        current.targetStationId().get(),
                        player.level().dimension(),
                        Optional.empty()
                );
        if (segment.isEmpty()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.no_segment_to_pin"));
            return schedule;
        }

        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(selectedIndex, current.withPinnedSegment(Optional.of(segment.get().id())));
        actionBar(player, Component.translatable(
                "message.create_aeronautics_automated_logistics.airship_schedule.segment_pinned",
                stationName(segment.get().startStationId(), segment.get().startStationName()),
                stationName(segment.get().endStationId(), segment.get().endStationName())
        ));
        return schedule.withEntries(entries);
    }

    private String displayStationName(AirshipScheduleEntry entry) {
        return entry.targetStationId()
                .flatMap(AirshipStationRegistry::snapshot)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElseGet(entry::displayStationName);
    }

    private String stationName(UUID stationId, String fallbackName) {
        return AirshipStationRegistry.snapshot(stationId)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
    }

    private AirshipSchedule select(AirshipSchedule schedule, int direction) {
        if (schedule.entries().isEmpty()) {
            selectedIndex = 0;
            return schedule;
        }
        selectedIndex = Math.floorMod(selectedIndex + direction, schedule.entries().size());
        return schedule;
    }

    private void clampSelectedIndex(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            selectedIndex = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, schedule.entries().size() - 1));
    }

    public void writeSchedule(Player player, AirshipSchedule schedule) {
        if (isReadOnly(player)) {
            return;
        }
        Optional<ShipTransponderBlockEntity> transponder = editableTransponder(player);
        if (transponder.isPresent()) {
            if (player instanceof ServerPlayer serverPlayer
                    && !TransponderPermissionService.ensureCanControl(serverPlayer, transponder.get())) {
                return;
            }
            AirshipSchedule current = transponder.get().ownedSchedule();
            if (!containsOnlyExistingStops(current, schedule)) {
                return;
            }
            List<AirshipScheduleEntry> removedEntries = removedEntries(current, schedule);
            int removedRoutes = 0;
            if (player instanceof ServerPlayer serverPlayer && !removedEntries.isEmpty()) {
                removedRoutes = ScheduleRouteCleanup.removeRoutesForDeletedScheduleEntries(
                        serverPlayer.serverLevel(),
                        transponder.get().transponderId(),
                        removedEntries
                );
            }
            transponder.get().setOwnedSchedule(schedule);
            if (player instanceof ServerPlayer serverPlayer) {
                if (!removedEntries.isEmpty()) {
                    actionBar(player, Component.translatable(
                            "message.create_aeronautics_automated_logistics.airship_schedule.stop_deleted_with_routes",
                            displayStationName(removedEntries.getFirst()),
                            removedRoutes
                    ));
                    PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new SetFlightPathPreviewPayload(false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), Optional.empty())
                    );
                }
                PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new SyncTransponderOwnedSchedulePayload(
                                transponder.get().getBlockPos(),
                                AirshipScheduleNbtSerializer.write(schedule)
                        )
                );
            }
            return;
        }
    }

    private List<AirshipScheduleEntry> removedEntries(AirshipSchedule current, AirshipSchedule updated) {
        List<AirshipScheduleEntry> unmatchedUpdated = new ArrayList<>(updated.entries());
        List<AirshipScheduleEntry> removed = new ArrayList<>();
        for (AirshipScheduleEntry currentEntry : current.entries()) {
            int matchingIndex = matchingStopIndex(unmatchedUpdated, currentEntry);
            if (matchingIndex >= 0) {
                unmatchedUpdated.remove(matchingIndex);
            } else {
                removed.add(currentEntry);
            }
        }
        return List.copyOf(removed);
    }

    private int matchingStopIndex(List<AirshipScheduleEntry> entries, AirshipScheduleEntry expected) {
        for (int i = 0; i < entries.size(); i++) {
            AirshipScheduleEntry candidate = entries.get(i);
            if (candidate.targetStationId().equals(expected.targetStationId())
                    && candidate.pinnedSegmentId().equals(expected.pinnedSegmentId())) {
                return i;
            }
        }
        return -1;
    }

    private Optional<ShipTransponderBlockEntity> editableTransponder(Player player) {
        if (originTransponderPos != null
                && player.level().getBlockEntity(originTransponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }
        return Optional.empty();
    }

    private boolean containsOnlyExistingStops(AirshipSchedule current, AirshipSchedule updated) {
        List<AirshipScheduleEntry> remaining = new ArrayList<>(current.entries());
        for (AirshipScheduleEntry updatedEntry : updated.entries()) {
            int matchingIndex = -1;
            for (int i = 0; i < remaining.size(); i++) {
                AirshipScheduleEntry candidate = remaining.get(i);
                if (candidate.targetStationId().equals(updatedEntry.targetStationId())
                        && candidate.pinnedSegmentId().equals(updatedEntry.pinnedSegmentId())) {
                    matchingIndex = i;
                    break;
                }
            }
            if (matchingIndex < 0) {
                return false;
            }
            remaining.remove(matchingIndex);
        }
        return true;
    }

    private void actionBar(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            SetMenuActionBarMessagePayload.send(serverPlayer, message);
        }
    }

    private boolean skipCurrentStop(Player player) {
        Optional<ShipTransponderBlockEntity> transponder = editableTransponder(player);
        if (transponder.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Skip-stop request refused: transponder unavailable or player was not server-side"
            );
            return false;
        }
        if (!TransponderPermissionService.ensureCanControl(serverPlayer, transponder.get())) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Skip-stop request refused: player={} transponder={} reason=permission_denied",
                    serverPlayer.getGameProfile().getName(),
                    transponder.get().transponderId()
            );
            return false;
        }
        if (!net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices.SCHEDULES
                .skipCurrentStop(serverPlayer.serverLevel(), transponder.get().transponderId())) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Skip-stop request refused: player={} transponder={} reason=runtime_not_waiting",
                    serverPlayer.getGameProfile().getName(),
                    transponder.get().transponderId()
            );
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.skip_stop_unavailable"));
            return false;
        }
        CreateAeronauticsAutomatedLogistics.debugPlayback(
                "Skip-stop request applied: player={} transponder={}",
                serverPlayer.getGameProfile().getName(),
                transponder.get().transponderId()
        );
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.skip_stop_success"));
        return true;
    }

    private boolean canModify(Player player) {
        Optional<ShipTransponderBlockEntity> transponder = editableTransponder(player);
        if (transponder.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return TransponderPermissionService.ensureCanControl(serverPlayer, transponder.get());
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;

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
    public static final int ACTION_SELECT_ENTRY_BASE = 100;
    private static final int WAIT_ADJUST_TICKS = 20 * 5;

    private int selectedIndex;

    public AirshipScheduleMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory);
    }

    public AirshipScheduleMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.AIRSHIP_SCHEDULE.get(), containerId);
        addPlayerInventory(playerInventory);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer)) {
            return false;
        }
        return applyAction(player, id);
    }

    public void handleClientAction(int id, Player player) {
        if (id == ACTION_SELECT_PREVIOUS || id == ACTION_SELECT_NEXT || id >= ACTION_SELECT_ENTRY_BASE) {
            applyAction(player, id);
        }
    }

    public AirshipSchedule schedule(Player player) {
        return scheduleStack(player).map(AirshipScheduleItem::readSchedule).orElseGet(AirshipSchedule::empty);
    }

    public int selectedIndex(Player player) {
        AirshipSchedule schedule = schedule(player);
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
                entry.displayStationName()
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return scheduleStack(player).isPresent();
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
        return scheduleStack(player).map(stack -> {
            AirshipSchedule schedule = AirshipScheduleItem.readSchedule(stack);
            clampSelectedIndex(schedule);
            AirshipSchedule updated = switch (id) {
                case ACTION_ADD_TRAVEL -> addTravel(schedule);
                case ACTION_REMOVE -> removeSelected(schedule);
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
                AirshipScheduleItem.writeSchedule(stack, updated);
            }
            return id >= ACTION_ADD_TRAVEL && id <= ACTION_PIN_NEWEST_SEGMENT;
        }).orElse(false);
    }

    private AirshipSchedule addTravel(AirshipSchedule schedule) {
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        int insertIndex = entries.isEmpty() ? 0 : Math.min(selectedIndex + 1, entries.size());
        entries.add(insertIndex, AirshipScheduleEntry.blankTravel());
        selectedIndex = insertIndex;
        return schedule.withEntries(entries);
    }

    private AirshipSchedule removeSelected(AirshipSchedule schedule) {
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
        List<AirshipStationSnapshot> stations = AirshipStationRegistry.knownStations(player.level().dimension());
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

        Optional<RouteSegment> segment = RouteSegmentResolver.newestFor(
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
                segment.get().startStationName(),
                segment.get().endStationName()
        ));
        return schedule.withEntries(entries);
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

    private java.util.Optional<ItemStack> scheduleStack(Player player) {
        if (player.getMainHandItem().is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return java.util.Optional.of(player.getMainHandItem());
        }
        if (player.getOffhandItem().is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return java.util.Optional.of(player.getOffhandItem());
        }
        return java.util.Optional.empty();
    }

    private void actionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
        if (player instanceof ServerPlayer serverPlayer) {
            SetMenuActionBarMessagePayload.send(serverPlayer, message);
        }
    }
}

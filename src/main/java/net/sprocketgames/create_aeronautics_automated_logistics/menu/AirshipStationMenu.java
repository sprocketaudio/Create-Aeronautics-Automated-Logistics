package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import com.simibubi.create.AllSoundEvents;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
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
    private final LinkedCargoSummary initialCargoSummary;
    private final List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries;

    public AirshipStationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                playerInventory,
                buffer.readBlockPos(),
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

    public AirshipStationMenu(int containerId, Inventory playerInventory, BlockPos stationPos) {
        this(containerId, playerInventory, stationPos, new LinkedCargoSummary(0, 0, 0, 0, 0), List.of());
    }

    public AirshipStationMenu(
            int containerId,
            Inventory playerInventory,
            BlockPos stationPos,
            LinkedCargoSummary initialCargoSummary,
            List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries
    ) {
        super(ModMenus.AIRSHIP_STATION.get(), containerId);
        this.stationPos = stationPos;
        this.initialCargoSummary = initialCargoSummary == null ? new LinkedCargoSummary(0, 0, 0, 0, 0) : initialCargoSummary;
        this.initialLinkedCargoEntries = initialLinkedCargoEntries == null ? List.of() : List.copyOf(initialLinkedCargoEntries);
    }

    public BlockPos stationPos() {
        return stationPos;
    }

    public String stationName(Player player) {
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
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason." + reason.name().toLowerCase(Locale.ROOT))
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
        if (!AutomatedLogisticsConfig.RESTRICT_TRANSPONDER_CONTROL_TO_OWNER.get()) {
            return true;
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return station.ownerId().isEmpty() || station.ownerId().get().equals(player.getUUID());
    }

    public List<Component> statusTooltipLines(Player player) {
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
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_stops_status")
                            .withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_stops.2")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        return selectedShipRuntimeStatusTooltip(player, selectedTransponder.get());
    }

    public boolean isRecording(Player player) {
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
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none");
        }
        return station.selectedTransponderId()
                .map(id -> Component.literal(station.selectedShipName()))
                .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none"));
    }

    public List<ShipChoice> shipChoices(Player player) {
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
        Optional<UUID> selected = station.selectedTransponderId();
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

    public Component segmentSummary(Player player) {
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
        if (!hasResolvableSelectedRouteChain(player, transponder)) {
            return 0xFFFFD98A;
        }
        return 0xAFC7DE;
    }

    public boolean selectedShipScheduleRunning(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return selectedTransponder(player, station)
                .map(ShipTransponderBlockEntity::scheduleActive)
                .orElse(false);
    }

    public boolean selectedShipScheduleHeld(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return selectedTransponder(player, station)
                .map(ShipTransponderBlockEntity::scheduleHeld)
                .orElse(false);
    }

    public Component routesFromHereText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here_compact",
                outgoing
        );
    }

    public int routesFromHereCount(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0;
        }
        return RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
    }

    public Component routesToHereText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int incoming = RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .filter(segment -> segment.endStationId().equals(station.stationId()))
                .toList()
                .size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_to_here_compact",
                incoming
        );
    }

    public int routesToHereCount(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0;
        }
        return (int) RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .filter(segment -> segment.endStationId().equals(station.stationId()))
                .count();
    }

    public Component dockText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        return dockCompactText(player);
    }

    public Component dockCompactText(Player player) {
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
                && CargoLinkInteractionService.hasPendingStationLink(serverPlayer, stationPos);
    }

    private List<Component> failureStatusTooltip(FailureReason reason) {
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
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason." + key)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_hint." + key)
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    public Optional<BlockPos> dockPreviewPos(Player player) {
        if (!canControlStationLocally(player)) {
            return Optional.empty();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
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
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> linkedEntries = List.of();
        if (player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            linkedEntries = station.linkedCargo();
            if (linkedEntries.isEmpty() && !initialLinkedCargoEntries.isEmpty()) {
                linkedEntries = initialLinkedCargoEntries;
            }
        } else if (!initialLinkedCargoEntries.isEmpty()) {
            linkedEntries = initialLinkedCargoEntries;
        }
        if (linkedEntries.isEmpty()) {
            return List.of();
        }
        List<List<BlockPos>> expanded = CargoLinkSupport.expandPreviewPositionGroups(player.level(), stationPos, 6, linkedEntries);
        if (!expanded.isEmpty()) {
            return expanded;
        }
        return linkedEntries.stream()
                .map(entry -> List.of(entry.pos()))
                .toList();
    }

    private LinkedCargoSummary resolveCargoSummary(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return initialCargoSummary;
        }
        LinkedCargoSummary summary = station.linkedCargoSummary();
        if (!summary.hasLinks() && initialCargoSummary.hasLinks()) {
            return initialCargoSummary;
        }
        return summary;
    }

    public List<Component> segmentLines(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        return RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .limit(4)
                .<Component>map(segment -> Component.translatable(
                        segment.startStationId().equals(station.stationId())
                                ? "gui.create_aeronautics_automated_logistics.airship_station.segment_line.outgoing"
                                : "gui.create_aeronautics_automated_logistics.airship_station.segment_line.incoming",
                        segment.startStationName(),
                        segment.endStationName(),
                        segment.shipName(),
                        segment.points().size()
                ))
                .toList();
    }

    public List<RouteSegment> routeChoices(Player player) {
        if (!canControlStationLocally(player)) {
            return List.of();
        }
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        return localRoutes(station, player);
    }

    private Optional<UUID> selectedTransponderId(AirshipStationBlockEntity station) {
        return station.selectedTransponderId().filter(id -> !id.equals(new UUID(0L, 0L)));
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
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return selectedShipFailureStatusText(failure.get());
        }
        if (!transponder.hasOwnedStops()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_stops_status");
        }
        if (!hasResolvableSelectedRouteChain(player, transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ready");
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
        if (failure.isEmpty() && !hasResolvableSelectedRouteChain(player, transponder)) {
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
                selectedShipFailureStatusText(value).copy().withStyle(ChatFormatting.RED),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason." + suffix)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint." + suffix)
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
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
            case REDSTONE_LINK_UNCONFIGURED, CARGO_STORAGE_MISSING, CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE, ALREADY_RUNNING -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.needs_attention_status");
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
        return true;
    }

    private boolean selectShipByIndex(ServerPlayer player, AirshipStationBlockEntity station, int shipIndex) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (shipIndex < 0 || shipIndex >= ships.size()) {
            return false;
        }
        ShipTransponderSnapshot selected = ships.get(shipIndex);
        station.selectShip(selected);
        return true;
    }

    private boolean autoSelectClosestShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (ships.isEmpty()) {
            return false;
        }
        station.selectShip(ships.getFirst());
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
        if (AutomatedLogisticsServices.SCHEDULES.isRunningAtStation(station.getBlockPos())) {
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
        if (station.isPlaybackRunning()) {
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
                segment -> actionBar(player, Component.translatable(
                        "message.create_aeronautics_automated_logistics.segment_recording.saved",
                        segment.startStationName(),
                        segment.endStationName(),
                        segment.shipName(),
                        segment.points().size()
                )),
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
        if (station.isPlaybackRunning()) {
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
        if (station.isRecording() || station.isPlaybackRunning()) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), station.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        CargoLinkInteractionService.beginStationLink(player, station.getBlockPos());
        return true;
    }

    private boolean clearCargo(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording() || station.isPlaybackRunning()) {
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
            return false;
        }
        RouteSegment segment = routes.get(routeIndex);
        PacketDistributor.sendToPlayer(
                player,
                new SetFlightPathPreviewPayload(
                        true,
                        segment.points().stream().map(point -> point.position()).toList(),
                        List.of(Math.max(0, segment.points().size() - 1))
                )
        );
        return true;
    }

    private List<RouteSegment> localRoutes(AirshipStationBlockEntity station, Player player) {
        return RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    private Optional<ShipTransponderBlockEntity> selectedTransponder(ServerLevel level, UUID transponderId) {
        return ShipTransponderRegistry.snapshot(transponderId)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast);
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

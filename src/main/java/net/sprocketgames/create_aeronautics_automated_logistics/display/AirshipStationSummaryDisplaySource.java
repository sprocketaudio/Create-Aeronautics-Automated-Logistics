package net.sprocketgames.create_aeronautics_automated_logistics.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingPhase;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.VehicleRoutePlaybackService.RuntimePlaybackSummary;

/** Station-wide vehicle summary, optionally filtered to one routed vehicle. */
public final class AirshipStationSummaryDisplaySource extends DisplaySource {
    private static final String VEHICLE_SELECTION = "VehicleSelection";
    private static final Map<UUID, String> LAST_TRAFFIC_DIAGNOSTICS = new ConcurrentHashMap<>();
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof AirshipStationBlockEntity station)) {
            return EMPTY;
        }
        if (!(context.level() instanceof ServerLevel level)) {
            return List.of(Component.literal(station.stationName()));
        }

        List<VehicleEntry> vehicles = knownStationVehicles(level, station);
        int selection = context.sourceConfig().getInt(VEHICLE_SELECTION);

        List<MutableComponent> lines = new ArrayList<>();
        if (selection <= 0) {
            lines.add(Component.literal(station.stationName()));
            addStationTraffic(lines, station, vehicles);
        } else if (selection > vehicles.size()) {
            lines.add(Component.literal(station.stationName()));
            lines.add(Component.literal("Selected vehicle unavailable"));
            lines.add(Component.literal(dockText(station)));
        } else {
            VehicleEntry vehicle = vehicles.get(selection - 1);
            lines.add(Component.literal("Ship: " + vehicle.displayName()));
            if (vehicle.isAtStation(station)) {
                lines.add(Component.literal("Location: " + station.stationName()));
                lines.add(Component.literal("Next: " + vehicle.nextFor(station)));
                lines.add(Component.literal("Status: " + vehicle.status()));
            } else {
                lines.add(Component.literal("From: " + vehicle.originStationName()));
                lines.add(Component.literal("To: " + vehicle.destinationStationName()));
                lines.add(Component.literal("Status: " + vehicle.status()));
            }
        }
        return List.copyOf(lines.subList(0, Math.min(lines.size(), Math.max(1, stats.maxRows()))));
    }

    @Override
    public void populateData(DisplayLinkContext context) {
        CompoundTag config = context.sourceConfig();
        if (!config.contains(VEHICLE_SELECTION)) {
            config.putInt(VEHICLE_SELECTION, 0);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        if (!isFirstLine) {
            return;
        }
        List<Component> options = clientVehicleOptions(context);
        builder.addSelectionScrollInput(0, 137, (input, label) -> {
            input.forOptions(options);
            input.setState(Math.max(0, Math.min(context.sourceConfig().getInt(VEHICLE_SELECTION), options.size() - 1)));
            input.titled(Component.literal("Vehicle filter"));
        }, VEHICLE_SELECTION);
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 10;
    }

    @OnlyIn(Dist.CLIENT)
    private static List<Component> clientVehicleOptions(DisplayLinkContext context) {
        if (!(context.getSourceBlockEntity() instanceof AirshipStationBlockEntity station)
                || Minecraft.getInstance().player == null) {
            return List.of(Component.literal("All vehicles"));
        }
        UUID playerId = Minecraft.getInstance().player.getUUID();
        boolean isOp = Minecraft.getInstance().player.hasPermissions(2);
        List<Component> options = new ArrayList<>();
        options.add(Component.literal("All vehicles"));
        station.displayLinkVehicleChoices().stream()
                .filter(choice -> clientMayUseVehicle(playerId, isOp, choice.ownerId()))
                .map(AirshipStationBlockEntity.DisplayLinkVehicleChoice::name)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(Component::literal)
                .forEach(options::add);
        return List.copyOf(options);
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean clientMayUseVehicle(UUID playerId, boolean isOp, Optional<UUID> vehicleOwner) {
        if (!AutomatedLogisticsConfig.ownershipPermissionsEnabled() || isOp || vehicleOwner.isEmpty()
                || vehicleOwner.get().equals(playerId)) {
            return true;
        }
        return FtbTeamsCompat.canControlClientSide(
                playerId,
                vehicleOwner.get(),
                AutomatedLogisticsConfig.ALLOW_TEAM_TRANSPONDER_CONTROL.get(),
                AutomatedLogisticsConfig.ALLOW_ALLIED_TRANSPONDER_CONTROL.get()
        ).orElse(false);
    }

    private static List<VehicleEntry> knownStationVehicles(ServerLevel level, AirshipStationBlockEntity station) {
        Map<UUID, List<RouteSegment>> routesByVehicle = new LinkedHashMap<>();
        RouteSegmentDirectorySavedData.connectedToStation(level.getServer(), station.stationId()).stream()
                .filter(segment -> permittedForStation(station, segment.ownerId()))
                .sorted(Comparator.comparingLong(RouteSegment::createdEpochMillis).reversed())
                .forEach(segment -> routesByVehicle
                        .computeIfAbsent(segment.transponderId(), ignored -> new ArrayList<>())
                        .add(segment));

        Map<UUID, RuntimePlaybackSummary> runtimeByVehicle = new LinkedHashMap<>();
        for (RuntimePlaybackSummary runtime : AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(level.getServer())) {
            runtime.transponderId().ifPresent(id -> runtimeByVehicle.put(id, runtime));
        }

        return routesByVehicle.entrySet().stream()
                .map(entry -> {
                    UUID transponderId = entry.getKey();
                    RuntimePlaybackSummary runtime = runtimeByVehicle.get(transponderId);
                    RouteSegment segment = selectCurrentSegment(transponderId, entry.getValue(), runtime);
                    return new VehicleEntry(segment, runtime);
                })
                .sorted(Comparator.comparing(VehicleEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.transponderId().toString()))
                .toList();
    }

    private static RouteSegment selectCurrentSegment(
            UUID transponderId,
            List<RouteSegment> segments,
            RuntimePlaybackSummary runtime
    ) {
        Optional<UUID> scheduledOrigin = AutomatedLogisticsServices.SCHEDULES.currentStationId(transponderId);
        Optional<UUID> scheduledDestination = AutomatedLogisticsServices.SCHEDULES.targetStationId(transponderId);
        if (scheduledOrigin.isPresent() && scheduledDestination.isPresent()) {
            Optional<RouteSegment> scheduledSegment = segments.stream()
                    .filter(segment -> segment.startStationId().equals(scheduledOrigin.get()))
                    .filter(segment -> segment.endStationId().equals(scheduledDestination.get()))
                    .findFirst();
            if (scheduledSegment.isPresent()) {
                return scheduledSegment.get();
            }
        }
        if (runtime != null) {
            Optional<RouteSegment> targetedSegment = runtime.dockTargetStationId()
                    .flatMap(target -> segments.stream()
                            .filter(segment -> segment.endStationId().equals(target))
                            .findFirst());
            if (targetedSegment.isPresent()) {
                return targetedSegment.get();
            }
            Optional<RouteSegment> namedSegment = segments.stream()
                    .filter(segment -> runtime.routeName().equals(
                            segment.startStationName() + " -> " + segment.endStationName()
                    ))
                    .findFirst();
            if (namedSegment.isPresent()) {
                return namedSegment.get();
            }
        }
        return segments.getFirst();
    }

    private static void addStationTraffic(
            List<MutableComponent> lines,
            AirshipStationBlockEntity station,
            List<VehicleEntry> vehicles
    ) {
        long arriving = vehicles.stream()
                .filter(vehicle -> vehicle.isQueuedFor(station)
                        || (vehicle.isTravelling()
                        && vehicle.destinationStationId().equals(station.stationId())))
                .count();
        long departing = vehicles.stream()
                .filter(VehicleEntry::isTravelling)
                .filter(vehicle -> vehicle.originStationId().equals(station.stationId()))
                .count();

        lines.add(Component.literal("Traffic: In " + arriving + " \u00b7 Out " + departing));
        Optional<DockRequestId> reservationHolder = AutomatedLogisticsServices.DOCK_RESERVATIONS.snapshots().stream()
                .filter(snapshot -> snapshot.target().stationId().filter(station.stationId()::equals).isPresent())
                .flatMap(snapshot -> snapshot.holder().stream())
                .findFirst();
        Optional<DockRequestId> retainedHolder = reservationHolder.stream()
                .filter(AutomatedLogisticsServices.PLAYBACK::isRetainingDock)
                .findFirst();
        boolean approaching = reservationHolder.flatMap(DockRequestId::transponderId)
                .filter(id -> vehicles.stream().anyMatch(vehicle -> vehicle.transponderId().equals(id)
                        && vehicle.isTravelling()
                        && vehicle.destinationStationId().equals(station.stationId())))
                .isPresent();
        boolean dockDeparting = reservationHolder.flatMap(DockRequestId::transponderId)
                .filter(id -> vehicles.stream().anyMatch(vehicle -> vehicle.transponderId().equals(id)
                        && vehicle.isTravelling()
                        && vehicle.originStationId().equals(station.stationId())))
                .isPresent();
        long atStation = vehicles.stream()
                .filter(vehicle -> vehicle.isAtStation(station))
                .count();
        if (retainedHolder.flatMap(DockRequestId::transponderId)
                .filter(id -> vehicles.stream()
                        .noneMatch(vehicle -> vehicle.transponderId().equals(id) && vehicle.isAtStation(station)))
                .isPresent()) {
            atStation++;
        }
        lines.add(Component.literal("At station: " + atStation));
        String dockedVehicle = station.dockOutputOwner()
                .flatMap(routeId -> vehicles.stream()
                        .filter(vehicle -> vehicle.runtimeMatches(routeId))
                        .map(VehicleEntry::displayName)
                        .findFirst())
                .or(() -> retainedHolder.flatMap(DockRequestId::transponderId)
                        .flatMap(id -> vehicles.stream()
                                .filter(vehicle -> vehicle.transponderId().equals(id))
                                .map(VehicleEntry::displayName)
                                .findFirst()))
                .orElse(null);
        lines.add(Component.literal(dockedVehicle == null
                ? dockOccupancyText(station, retainedHolder.isPresent(), approaching, dockDeparting)
                : "Docked: " + dockedVehicle));
        long queued = vehicles.stream()
                .filter(vehicle -> vehicle.isQueuedFor(station))
                .count();
        lines.add(Component.literal("Queued: " + queued));
        logTrafficClassification(station, vehicles, arriving, departing, atStation, queued);
    }

    private static void logTrafficClassification(
            AirshipStationBlockEntity station,
            List<VehicleEntry> vehicles,
            long arriving,
            long departing,
            long atStation,
            long queued
    ) {
        String vehicleDetails = vehicles.stream()
                .map(vehicle -> vehicle.displayName()
                        + "[" + vehicle.transponderId() + "]"
                        + " state=" + vehicle.runtimeState()
                        + " origin=" + vehicle.originStationId()
                        + " destination=" + vehicle.destinationStationId()
                        + " in=" + (vehicle.isQueuedFor(station)
                                || vehicle.isTravelling()
                                && vehicle.destinationStationId().equals(station.stationId()))
                        + " out=" + (vehicle.isTravelling()
                                && vehicle.originStationId().equals(station.stationId()))
                        + " at=" + vehicle.isAtStation(station)
                        + " queued=" + vehicle.isQueuedFor(station))
                .toList()
                .toString();
        String diagnostic = "in=" + arriving
                + " out=" + departing
                + " at=" + atStation
                + " queued=" + queued
                + " vehicles=" + vehicleDetails;
        if (!diagnostic.equals(LAST_TRAFFIC_DIAGNOSTICS.put(station.stationId(), diagnostic))) {
            CreateAeronauticsAutomatedLogistics.debugPlayback(
                    "Display traffic classification: station={} id={} {}",
                    station.stationName(),
                    station.stationId(),
                    diagnostic
            );
        }
    }

    /** A public board must not expose routes owned by an unrelated protected player/team. */
    private static boolean permittedForStation(AirshipStationBlockEntity station, Optional<UUID> vehicleOwner) {
        if (!AutomatedLogisticsConfig.ownershipPermissionsEnabled() || station.ownerId().isEmpty() || vehicleOwner.isEmpty()) {
            return true;
        }
        UUID stationOwner = station.ownerId().get();
        UUID shipOwner = vehicleOwner.get();
        if (stationOwner.equals(shipOwner)) {
            return true;
        }
        if (AutomatedLogisticsConfig.ALLOW_TEAM_TRANSPONDER_CONTROL.get()
                && FtbTeamsCompat.areSameTeam(stationOwner, shipOwner)) {
            return true;
        }
        return AutomatedLogisticsConfig.ALLOW_ALLIED_TRANSPONDER_CONTROL.get()
                && FtbTeamsCompat.areAllied(shipOwner, stationOwner);
    }

    private static String dockText(AirshipStationBlockEntity station) {
        if (station.dockOutputActive()) {
            return "Dock: Active";
        }
        return switch (station.groundDockStatus()) {
            case LINKED -> "Dock: Ready";
            case AMBIGUOUS -> "Dock: Ambiguous";
            case INVALID -> "Dock: Invalid";
            case MISSING, UNKNOWN -> "Dock: Not linked";
        };
    }

    private static String dockOccupancyText(
            AirshipStationBlockEntity station,
            boolean physicallyRetained,
            boolean approaching,
            boolean departing
    ) {
        if (station.dockOutputActive() || physicallyRetained) {
            return "Dock: Occupied";
        }
        if (approaching) {
            return "Dock: Approaching";
        }
        if (departing) {
            return "Dock: Departing";
        }
        return switch (station.groundDockStatus()) {
            case LINKED -> "Dock: Free";
            case AMBIGUOUS -> "Dock: Ambiguous";
            case INVALID -> "Dock: Invalid";
            case MISSING, UNKNOWN -> "Dock: Unlinked";
        };
    }

    private record VehicleEntry(RouteSegment route, RuntimePlaybackSummary runtime) {
        UUID transponderId() {
            return route.transponderId();
        }

        String displayName() {
            return route.shipName();
        }

        boolean isTravelling() {
            return runtime != null && ("ACTIVE".equals(runtime.state()) || "UNLOADED_TRANSIT".equals(runtime.state()));
        }

        String runtimeState() {
            return runtime == null ? "NONE" : runtime.state();
        }

        boolean runtimeMatches(net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId routeId) {
            return runtime != null && runtime.routeId().equals(routeId);
        }

        boolean isAtStation(AirshipStationBlockEntity station) {
            if (runtime == null) {
                return false;
            }
            if (runtimeMatches(station.dockOutputOwner().orElse(null))) {
                return true;
            }
            return runtime.confirmedStationId().filter(station.stationId()::equals).isPresent();
        }

        boolean isQueuedFor(AirshipStationBlockEntity station) {
            return runtime != null
                    && "DOCK_QUEUED".equals(runtime.state())
                    && runtime.dockTargetStationId()
                            .map(station.stationId()::equals)
                            .orElseGet(() -> destinationStationId().equals(station.stationId()));
        }

        UUID originStationId() {
            return runtime != null && runtime.travelDirection() < 0
                    ? route.endStationId()
                    : route.startStationId();
        }

        UUID destinationStationId() {
            return runtime != null && runtime.travelDirection() < 0
                    ? route.startStationId()
                    : route.endStationId();
        }

        String originStationName() {
            return runtime != null && runtime.travelDirection() < 0
                    ? route.endStationName()
                    : route.startStationName();
        }

        String destinationStationName() {
            return runtime != null && runtime.travelDirection() < 0
                    ? route.startStationName()
                    : route.endStationName();
        }

        String nextFor(AirshipStationBlockEntity station) {
            return route.startStationId().equals(station.stationId())
                    ? route.endStationName()
                    : route.startStationName();
        }

        String status() {
            if (runtime == null) {
                return "Ready";
            }
            DockingPhase dockingPhase = AutomatedLogisticsServices.PLAYBACK
                    .dockingSessionSnapshot(runtime.routeId())
                    .map(session -> session.phase())
                    .orElse(DockingPhase.NONE);
            return VehicleDisplayStatus.label(runtime.state(), dockingPhase);
        }

    }
}

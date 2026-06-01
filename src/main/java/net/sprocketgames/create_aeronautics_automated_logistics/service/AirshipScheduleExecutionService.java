package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

public class AirshipScheduleExecutionService {
    public enum CompletionAdvanceResult {
        NOT_SCHEDULED,
        FINISHED,
        STARTED_NEXT,
        HELD_FAULT,
        FAILED
    }

    private static final String ACTIVE_SCHEDULES = "activeSchedules";
    private static final String LAST_FAILURES = "lastStartFailures";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String TRANSPONDER_POS = "transponderPos";
    private static final String DIMENSION = "dimension";
    private static final String SCHEDULE = "schedule";
    private static final String START_STATION_ID = "startStationId";
    private static final String START_STATION_NAME = "startStationName";
    private static final String START_STATION_POS = "startStationPos";
    private static final String CURRENT_STATION_ID = "currentStationId";
    private static final String CURRENT_STATION_NAME = "currentStationName";
    private static final String CURRENT_STATION_POS = "currentStationPos";
    private static final String ENTRY_INDEX = "entryIndex";
    private static final String ACTIVE_ROUTE_ID = "activeRouteId";
    private static final String FAILURE = "failure";
    private final Map<UUID, ActiveAirshipSchedule> activeSchedules = new HashMap<>();
    private final Map<UUID, PlaybackFailure> lastStartFailures = new HashMap<>();

    public void resetRuntime() {
        activeSchedules.clear();
        lastStartFailures.clear();
    }

    public CompoundTag saveRuntime() {
        CompoundTag tag = new CompoundTag();
        ListTag schedules = new ListTag();
        for (ActiveAirshipSchedule active : activeSchedules.values()) {
            schedules.add(writeActiveSchedule(active));
        }
        tag.put(ACTIVE_SCHEDULES, schedules);

        ListTag failures = new ListTag();
        for (Map.Entry<UUID, PlaybackFailure> entry : lastStartFailures.entrySet()) {
            CompoundTag failureTag = new CompoundTag();
            failureTag.putUUID(TRANSPONDER_ID, entry.getKey());
            failureTag.putString(FAILURE, entry.getValue().name());
            failures.add(failureTag);
        }
        tag.put(LAST_FAILURES, failures);
        return tag;
    }

    public void loadRuntime(MinecraftServer server, CompoundTag tag) {
        resetRuntime();
        if (tag == null) {
            CreateAeronauticsAutomatedLogistics.debugLog("Loaded schedule runtime: no saved active schedules");
            return;
        }
        if (tag.contains(ACTIVE_SCHEDULES, Tag.TAG_LIST)) {
            ListTag schedules = tag.getList(ACTIVE_SCHEDULES, Tag.TAG_COMPOUND);
            for (int i = 0; i < schedules.size(); i++) {
                readActiveSchedule(schedules.getCompound(i)).ifPresent(active -> activeSchedules.put(active.transponderId(), active));
            }
        }
        if (tag.contains(LAST_FAILURES, Tag.TAG_LIST)) {
            ListTag failures = tag.getList(LAST_FAILURES, Tag.TAG_COMPOUND);
            for (int i = 0; i < failures.size(); i++) {
                CompoundTag failureTag = failures.getCompound(i);
                if (!failureTag.hasUUID(TRANSPONDER_ID) || !failureTag.contains(FAILURE, Tag.TAG_STRING)) {
                    continue;
                }
                try {
                    lastStartFailures.put(failureTag.getUUID(TRANSPONDER_ID), PlaybackFailure.valueOf(failureTag.getString(FAILURE)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Loaded schedule runtime: {} active schedule(s), {} stored failure(s)",
                activeSchedules.size(),
                lastStartFailures.size()
        );
    }

    public PlaybackOperationResult<RouteId> start(
            ServerPlayer player,
            AirshipStationBlockEntity station,
            BlockPos stationPos,
            ShipTransponderBlockEntity transponder,
            AirshipSchedule schedule
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(stationPos, "stationPos");
        Objects.requireNonNull(transponder, "transponder");
        Objects.requireNonNull(schedule, "schedule");

        transponder.pruneInvalidOwnedSchedule(player.serverLevel());
        schedule = transponder.ownedSchedule();

        if (schedule.entries().isEmpty()) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponder.transponderId(), PlaybackFailure.INVALID_ROUTE);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        UUID transponderId = transponder.transponderId();
        clearStaleActiveSchedule(transponderId);
        if (activeSchedules.containsKey(transponderId)) {
            transponder.setRuntimeStatus(resolveActiveRuntimeStatus(player.serverLevel(), activeSchedules.get(transponderId)));
            lastStartFailures.put(transponderId, PlaybackFailure.ALREADY_RUNNING);
            return PlaybackOperationResult.failure(PlaybackFailure.ALREADY_RUNNING);
        }
        Optional<ShipTransponderSnapshot> ship = ShipTransponderRegistry.snapshot(transponderId)
                .filter(snapshot -> snapshot.dimension().equals(player.serverLevel().dimension()));
        if (ship.isEmpty() || ship.get().controllerRef().isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponderId, PlaybackFailure.VEHICLE_MISSING);
            return PlaybackOperationResult.failure(PlaybackFailure.VEHICLE_MISSING);
        }
        if (scheduleRequiresDocking(schedule)
                && !dockLinksReadyForSchedule(player.serverLevel(), transponder, schedule)) {
            station.setFailure(FailureReason.MISSING_DOCK);
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponderId, PlaybackFailure.MISSING_DOCK);
            return PlaybackOperationResult.failure(PlaybackFailure.MISSING_DOCK);
        }
        if (!cargoLinksReadyForSchedule(player.serverLevel(), transponder, schedule)) {
            station.setFailure(FailureReason.CARGO_STORAGE_MISSING);
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponderId, PlaybackFailure.CARGO_STORAGE_MISSING);
            return PlaybackOperationResult.failure(PlaybackFailure.CARGO_STORAGE_MISSING);
        }
        if (!scheduleChainIsValid(player.serverLevel(), schedule, station.stationId(), transponderId)) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponderId, PlaybackFailure.INVALID_ROUTE);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }

        ActiveAirshipSchedule active = new ActiveAirshipSchedule(
                transponderId,
                transponder.getBlockPos().immutable(),
                player.serverLevel().dimension(),
                schedule,
                station.stationId(),
                station.stationName(),
                stationPos.immutable(),
                station.stationId(),
                station.stationName(),
                stationPos.immutable(),
                0,
                Optional.empty()
        );
        PlaybackOperationResult<RouteId> result = startEntry(player.serverLevel(), station, active, ship.get());
        result.value().ifPresent(routeId -> {
            if (playbackStillActive(routeId)) {
                activeSchedules.put(transponderId, active.withActiveRoute(routeId));
                transponder.setRuntimeStatus(resolveRuntimeStatusFromStation(station));
            } else if (active.advance().isFinished() && !active.schedule().loop()) {
                station.stopPlayback();
                transponder.setRuntimeStatus(RouteStatus.IDLE);
            } else {
                activeSchedules.put(transponderId, active.withActiveRoute(routeId));
                transponder.setRuntimeStatus(resolveRuntimeStatusFromStation(station));
            }
            lastStartFailures.remove(transponderId);
        });
        result.failure().ifPresent(failure -> {
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponderId, failure);
        });
        return result;
    }

    public PlaybackOperationResult<RouteId> startFromTransponder(
            ServerPlayer player,
            ShipTransponderBlockEntity transponder,
            AirshipSchedule schedule
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(transponder, "transponder");
        Objects.requireNonNull(schedule, "schedule");

        transponder.pruneInvalidOwnedSchedule(player.serverLevel());
        schedule = transponder.ownedSchedule();

        if (schedule.entries().isEmpty()) {
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponder.transponderId(), PlaybackFailure.INVALID_ROUTE);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        clearStaleActiveSchedule(transponder.transponderId());
        if (activeSchedules.containsKey(transponder.transponderId())) {
            transponder.setRuntimeStatus(resolveActiveRuntimeStatus(player.serverLevel(), activeSchedules.get(transponder.transponderId())));
            lastStartFailures.put(transponder.transponderId(), PlaybackFailure.ALREADY_RUNNING);
            return PlaybackOperationResult.failure(PlaybackFailure.ALREADY_RUNNING);
        }

        Optional<ResolvedStartContext> startContext = resolveStartContext(player.serverLevel(), transponder, schedule);
        if (startContext.isEmpty()) {
            PlaybackFailure failure = nearestStartStation(player.serverLevel(), transponder).isPresent()
                    ? PlaybackFailure.WRONG_START_STATION
                    : PlaybackFailure.START_TOO_FAR_FROM_ROUTE;
            transponder.setRuntimeStatus(RouteStatus.IDLE);
            lastStartFailures.put(transponder.transponderId(), failure);
            return PlaybackOperationResult.failure(failure);
        }

        ResolvedStartContext resolved = startContext.get();
        AirshipStationBlockEntity station = resolved.station();
        station.selectShip(new ShipTransponderSnapshot(
                transponder.transponderId(),
                transponder.shipName(),
                player.serverLevel().dimension(),
                transponder.getBlockPos(),
                transponder.runtimeShipId(),
                transponder.controllerRef(player.serverLevel()),
                transponder.lastKnownPosition(),
                transponder.lastSeenGameTime()
        ));
        return start(player, station, station.getBlockPos(), transponder, resolved.runtimeSchedule());
    }

    public Optional<ResolvedStartContext> resolveStartContext(
            ServerLevel level,
            ShipTransponderBlockEntity transponder,
            AirshipSchedule schedule
    ) {
        List<AirshipStationBlockEntity> nearbyStations = nearbyStartStations(level, transponder);
        if (nearbyStations.isEmpty()) {
            return Optional.empty();
        }

        if (schedule.entries().isEmpty()) {
            return Optional.empty();
        }

        UUID transponderId = transponder.transponderId();
        Optional<UUID> canonicalStartStationId = canonicalStartStationId(level, schedule, transponderId);
        List<AirshipScheduleEntry> entries = schedule.entries();

        for (AirshipStationBlockEntity station : nearbyStations) {
            UUID currentStationId = station.stationId();
            String currentStationName = station.stationName();
            BlockPos currentStationPos = station.getBlockPos().immutable();

            if (canonicalStartStationId.isPresent() && canonicalStartStationId.get().equals(currentStationId)) {
                if (scheduleChainIsValid(level, schedule, currentStationId, transponderId)) {
                    return Optional.of(new ResolvedStartContext(
                            station,
                            currentStationId,
                            currentStationName,
                            currentStationPos,
                            schedule
                    ));
                }
                continue;
            }

            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).targetStationId().filter(currentStationId::equals).isEmpty()) {
                    continue;
                }
                List<AirshipScheduleEntry> runtimeEntries = runtimeEntriesFromStopIndex(schedule, i);
                if (runtimeEntries.isEmpty()) {
                    continue;
                }
                AirshipSchedule runtimeSchedule = schedule.withEntries(runtimeEntries);
                if (scheduleChainIsValid(level, runtimeSchedule, currentStationId, transponderId)) {
                    return Optional.of(new ResolvedStartContext(
                            station,
                            currentStationId,
                            currentStationName,
                            currentStationPos,
                            runtimeSchedule
                    ));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<String> currentStartStationName(ServerLevel level, ShipTransponderBlockEntity transponder) {
        return nearestStartStation(level, transponder).map(AirshipStationBlockEntity::stationName);
    }

    public Optional<String> nextStopNameForCurrentStation(
            ServerLevel level,
            ShipTransponderBlockEntity transponder,
            AirshipSchedule schedule
    ) {
        Optional<AirshipStationBlockEntity> currentStation = nearestStartStation(level, transponder);
        if (currentStation.isEmpty()) {
            return Optional.empty();
        }
        UUID currentStationId = currentStation.get().stationId();
        Optional<UUID> canonicalStartStationId = canonicalStartStationId(level, schedule, transponder.transponderId());
        if (canonicalStartStationId.isPresent() && canonicalStartStationId.get().equals(currentStationId)) {
            return schedule.entries().isEmpty() ? Optional.empty() : Optional.of(schedule.entries().getFirst().displayStationName());
        }
        for (int i = 0; i < schedule.entries().size(); i++) {
            if (schedule.entries().get(i).targetStationId().filter(currentStationId::equals).isEmpty()) {
                continue;
            }
            List<AirshipScheduleEntry> runtimeEntries = runtimeEntriesFromStopIndex(schedule, i);
            if (!runtimeEntries.isEmpty()) {
                return Optional.of(runtimeEntries.getFirst().displayStationName());
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> canonicalStartStationId(ServerLevel level, AirshipSchedule schedule, UUID transponderId) {
        if (schedule.entries().isEmpty()) {
            return Optional.empty();
        }
        AirshipScheduleEntry firstEntry = schedule.entries().getFirst();
        if (firstEntry.targetStationId().isEmpty()) {
            return Optional.empty();
        }
        return firstEntry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(segment -> segment.dimension().equals(level.dimension()))
                .filter(segment -> segment.transponderId().equals(transponderId))
                .filter(segment -> segment.endStationId().equals(firstEntry.targetStationId().get()))
                .map(RouteSegment::startStationId);
    }

    private List<AirshipScheduleEntry> runtimeEntriesFromStopIndex(AirshipSchedule schedule, int stopIndex) {
        List<AirshipScheduleEntry> entries = schedule.entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        if (stopIndex < 0 || stopIndex >= entries.size()) {
            return List.of();
        }
        if (schedule.loop()) {
            List<AirshipScheduleEntry> rotated = new java.util.ArrayList<>(entries.size());
            rotated.addAll(entries.subList(stopIndex + 1, entries.size()));
            rotated.addAll(entries.subList(0, stopIndex + 1));
            return rotated;
        }
        if (stopIndex >= entries.size() - 1) {
            return List.of();
        }
        return List.copyOf(entries.subList(stopIndex + 1, entries.size()));
    }

    private boolean scheduleChainIsValid(
            ServerLevel level,
            AirshipSchedule schedule,
            UUID startStationId,
            UUID transponderId
    ) {
        UUID currentStationId = startStationId;
        for (AirshipScheduleEntry entry : schedule.entries()) {
            if (entry.targetStationId().isEmpty()) {
                return false;
            }
            UUID fromStationId = currentStationId;
            UUID toStationId = entry.targetStationId().get();
            Optional<RouteSegment> segment = entry.pinnedSegmentId()
                    .flatMap(RouteSegmentRegistry::byId)
                    .filter(candidate -> candidate.startStationId().equals(fromStationId))
                    .filter(candidate -> candidate.endStationId().equals(toStationId))
                    .filter(candidate -> candidate.dimension().equals(level.dimension()))
                    .filter(candidate -> candidate.transponderId().equals(transponderId))
                    .or(() -> RouteSegmentResolver.newestFor(
                            fromStationId,
                            toStationId,
                            level.dimension(),
                            Optional.of(transponderId)
                    ));
            if (segment.isEmpty()) {
                return false;
            }
            currentStationId = toStationId;
        }
        return true;
    }

    private boolean cargoLinksReadyForSchedule(
            ServerLevel level,
            ShipTransponderBlockEntity transponder,
            AirshipSchedule schedule
    ) {
        LinkedCargoSummary shipSummary = transponder.linkedCargoSummary();
        for (AirshipScheduleEntry entry : schedule.entries()) {
            for (List<AirshipScheduleCondition> group : entry.effectiveConditionGroups()) {
                for (AirshipScheduleCondition condition : group) {
                    WaitCondition waitCondition = condition.waitCondition();
                    if (!isCargoWaitType(waitCondition.type())) {
                        continue;
                    }
                    if (!cargoStorageReadyForCondition(level, shipSummary, entry, waitCondition)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean cargoStorageReadyForCondition(
            ServerLevel level,
            LinkedCargoSummary shipSummary,
            AirshipScheduleEntry entry,
            WaitCondition waitCondition
    ) {
        LinkedCargoSummary summary = waitCondition.cargoTarget() == CargoWaitTarget.SHIP_CARGO
                ? shipSummary
                : entry.targetStationId()
                        .flatMap(AirshipStationRegistry::snapshot)
                        .flatMap(snapshot -> stationAt(level, snapshot.stationPos()))
                        .map(AirshipStationBlockEntity::linkedCargoSummary)
                        .orElse(null);
        if (summary == null) {
            return false;
        }
        if (summary.staleLinks() > 0) {
            return false;
        }
        return switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL -> summary.itemLinks() > 0;
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> summary.fluidLinks() > 0;
            default -> true;
        };
    }

    private static boolean isCargoWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || type == WaitConditionType.UNTIL_FLUID_THRESHOLD
                || type == WaitConditionType.UNTIL_ITEM_EMPTY
                || type == WaitConditionType.UNTIL_ITEM_FULL
                || type == WaitConditionType.UNTIL_FLUID_EMPTY
                || type == WaitConditionType.UNTIL_FLUID_FULL
                || type == WaitConditionType.UNTIL_EMPTY
                || type == WaitConditionType.UNTIL_FULL;
    }

    public void stop(ServerLevel level, UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.remove(transponderId);
        lastStartFailures.remove(transponderId);
        transponderAt(level, transponderPosition(active, transponderId)).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
        if (active == null) {
            stopLinkedOrphanPlaybacks(level, transponderId);
            return;
        }
        active.activeRouteId().ifPresent(routeId -> AutomatedLogisticsServices.PLAYBACK.stopPlayback(level, routeId, FailureReason.NONE));
        stationAt(level, active.currentStationPos()).ifPresent(station -> {
            station.setDockOutputActive(false);
            station.stopPlayback();
        });
        transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setDockOutputActive(false));
    }

    public boolean isRunning(UUID transponderId) {
        return activeSchedules.containsKey(transponderId);
    }

    public boolean hasActiveRuntime(ServerLevel level, UUID transponderId) {
        if (activeSchedules.containsKey(transponderId)) {
            return true;
        }
        return transponderAt(level, transponderPosition(null, transponderId))
                .map(ShipTransponderBlockEntity::scheduleActive)
                .orElse(false);
    }

    public boolean isHeld(UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null || active.activeRouteId().isEmpty()) {
            return false;
        }
        return AutomatedLogisticsServices.PLAYBACK.isHeld(active.activeRouteId().get());
    }

    public Optional<PlaybackFailure> heldFailure(UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null) {
            return Optional.empty();
        }
        if (active.activeRouteId().isEmpty()) {
            return Optional.empty();
        }
        return AutomatedLogisticsServices.PLAYBACK.heldFailure(active.activeRouteId().get());
    }

    public boolean resumeHeldPlayback(ServerLevel level, UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null) {
            return false;
        }
        if (active.activeRouteId().isEmpty()) {
            return false;
        }
        if (playbackBlocker(level, active.activeRouteId().get()).isPresent()) {
            return false;
        }
        boolean resumed = AutomatedLogisticsServices.PLAYBACK.resumeHeldPlayback(level, active.activeRouteId().get());
        if (resumed) {
            lastStartFailures.remove(transponderId);
            transponderAt(level, active.transponderPos())
                    .ifPresent(transponder -> transponder.setRuntimeStatus(resolveActiveRuntimeStatus(level, active)));
        }
        return resumed;
    }

    public Optional<UUID> currentStationId(UUID transponderId) {
        return Optional.ofNullable(activeSchedules.get(transponderId)).map(ActiveAirshipSchedule::currentStationId);
    }

    public Optional<Integer> currentEntryIndex(UUID transponderId) {
        return Optional.ofNullable(activeSchedules.get(transponderId)).map(ActiveAirshipSchedule::entryIndex);
    }

    public Optional<PlaybackFailure> playbackBlocker(ServerLevel level, RouteId routeId) {
        Optional<ActiveAirshipSchedule> active = activeSchedules.values().stream()
                .filter(candidate -> candidate.activeRouteId().filter(routeId::equals).isPresent())
                .findFirst();
        if (active.isEmpty()) {
            return Optional.of(PlaybackFailure.INVALID_ROUTE);
        }

        ActiveAirshipSchedule schedule = active.get();
        if (schedule.isFinished()) {
            return Optional.of(PlaybackFailure.INVALID_ROUTE);
        }

        AirshipScheduleEntry entry = schedule.currentEntry();
        if (entry.targetStationId().isEmpty()) {
            return Optional.of(PlaybackFailure.INVALID_ROUTE);
        }
        if (IdentityDirectorySavedData.get(level.getServer()).station(entry.targetStationId().get()).isEmpty()) {
            return Optional.of(PlaybackFailure.STATION_MISSING);
        }

        Optional<RouteSegment> segment = resolveScheduledSegment(
                level,
                schedule.currentStationId(),
                entry.targetStationId().get(),
                entry.pinnedSegmentId(),
                schedule.transponderId()
        );
        if (segment.isEmpty()) {
            return Optional.of(PlaybackFailure.INVALID_ROUTE);
        }
        return Optional.empty();
    }

    private Optional<RouteSegment> resolveScheduledSegment(
            ServerLevel level,
            UUID startStationId,
            UUID targetStationId,
            Optional<RouteSegmentId> pinnedSegmentId,
            UUID transponderId
    ) {
        return pinnedSegmentId
                .flatMap(RouteSegmentRegistry::byId)
                .filter(candidate -> candidate.startStationId().equals(startStationId))
                .filter(candidate -> candidate.endStationId().equals(targetStationId))
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(transponderId))
                .or(() -> RouteSegmentResolver.newestFor(
                        startStationId,
                        targetStationId,
                        level.dimension(),
                        Optional.of(transponderId)
                ));
    }

    public Optional<RouteStatus> currentStationStatus(Level level, UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null) {
            return Optional.empty();
        }
        if (level.getBlockEntity(active.currentStationPos()) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station.status());
        }
        return Optional.empty();
    }

    public boolean canStationStartFor(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ShipTransponderBlockEntity transponder
    ) {
        return isShipWithinLandingArea(level, station.getBlockPos(), transponder);
    }

    public boolean canStationStopFor(
            ServerLevel level,
            AirshipStationBlockEntity station,
            UUID transponderId
    ) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null) {
            return false;
        }
        if (isShipWithinLandingArea(level, station.getBlockPos(), transponderId)) {
            return true;
        }
        if (active.currentStationId().equals(station.stationId())) {
            return true;
        }
        if (active.isFinished()) {
            return false;
        }
        return active.currentEntry().targetStationId()
                .map(targetStationId -> targetStationId.equals(station.stationId()))
                .orElse(false);
    }

    public boolean isRunningAtStation(BlockPos stationPos) {
        return activeSchedules.values().stream()
                .anyMatch(active -> active.currentStationPos().equals(stationPos));
    }

    public Optional<UUID> runningTransponderAtStation(BlockPos stationPos) {
        return activeSchedules.values().stream()
                .filter(active -> active.currentStationPos().equals(stationPos))
                .map(ActiveAirshipSchedule::transponderId)
                .findFirst();
    }

    public boolean resetProgress(UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null) {
            return false;
        }
        activeSchedules.put(transponderId, active.resetProgress());
        return true;
    }

    public boolean skipCurrentStop(ServerLevel level, UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null || active.activeRouteId().isEmpty()) {
            return false;
        }
        AutomatedLogisticsServices.PLAYBACK.stopPlayback(level, active.activeRouteId().get(), FailureReason.NONE);
        ActiveAirshipSchedule advanced = active.advance();
        if (advanced.isFinished()) {
            if (!advanced.schedule().loop()) {
                activeSchedules.remove(transponderId);
                return true;
            }
            advanced = advanced.restart();
        }
        activeSchedules.put(transponderId, advanced);
        return true;
    }

    public void tickAll(MinecraftServer server) {
        reconcileOrphanPlaybacks(server);
        var iterator = activeSchedules.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveAirshipSchedule> entry = iterator.next();
            ActiveAirshipSchedule active = entry.getValue();
            ServerLevel level = server.getLevel(active.dimension());
            if (level == null) {
                if (active.activeRouteId().map(AutomatedLogisticsServices.PLAYBACK::isPending).orElse(false)) {
                    continue;
                }
                iterator.remove();
                continue;
            }

            Optional<ShipTransponderBlockEntity> activeTransponder = transponderAt(level, active.transponderPos());
            if (activeTransponder.isEmpty() || activeScheduleEntryMissing(activeTransponder.get(), active)) {
                active.activeRouteId().ifPresent(routeId ->
                        AutomatedLogisticsServices.PLAYBACK.holdPlayback(level, routeId, PlaybackFailure.INVALID_ROUTE));
                activeTransponder.ifPresent(value -> value.setRuntimeStatus(RouteStatus.HELD_FAULTED));
                lastStartFailures.put(active.transponderId(), PlaybackFailure.INVALID_ROUTE);
                iterator.remove();
                continue;
            }

            activeTransponder.ifPresent(value -> value.setRuntimeStatus(resolveActiveRuntimeStatus(level, active)));

            if (active.activeRouteId().isPresent()) {
                RouteId activeRouteId = active.activeRouteId().get();
                Optional<PlaybackFailure> blocker = playbackBlocker(level, activeRouteId);
                if (blocker.isPresent()) {
                    AutomatedLogisticsServices.PLAYBACK.holdPlayback(level, activeRouteId, blocker.get());
                    activeTransponder.ifPresent(value -> value.setRuntimeStatus(RouteStatus.HELD_FAULTED));
                    lastStartFailures.put(active.transponderId(), blocker.get());
                    iterator.remove();
                    continue;
                }
                if (AutomatedLogisticsServices.PLAYBACK.isRunning(activeRouteId)
                        || AutomatedLogisticsServices.PLAYBACK.isPending(activeRouteId)
                        || AutomatedLogisticsServices.PLAYBACK.isHeld(activeRouteId)) {
                    continue;
                }
            }

            Optional<AirshipStationBlockEntity> station = stationAt(level, active.currentStationPos());
            if (station.isEmpty()) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Removing active schedule for transponder {} because station {} is missing and route {} is not running/pending",
                        active.transponderId(),
                        active.currentStationPos(),
                        active.activeRouteId().map(routeId -> routeId.value().toString()).orElse("none")
                );
                transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
                lastStartFailures.put(active.transponderId(), PlaybackFailure.STATION_MISSING);
                iterator.remove();
                continue;
            }
            if (station.get().status() == RouteStatus.FAILED
                    || station.get().status() == RouteStatus.BLOCKED
                    || station.get().status() == RouteStatus.MISSING_VEHICLE
                    || station.get().status() == RouteStatus.INVALID_ROUTE) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Removing active schedule for transponder {} because station {} status is {}",
                        active.transponderId(),
                        active.currentStationPos(),
                        station.get().status()
                );
                transponderAt(level, active.transponderPos())
                        .ifPresent(transponder -> transponder.setRuntimeStatus(resolveRuntimeStatusFromStation(station.get())));
                iterator.remove();
                continue;
            }

            ActiveAirshipSchedule advanced = active.advance();
            UUID activeTransponderId = advanced.transponderId();
            if (advanced.isFinished()) {
                if (!advanced.schedule().loop()) {
                    station.get().stopPlayback();
                    transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
                    lastStartFailures.remove(active.transponderId());
                    iterator.remove();
                    continue;
                }
                advanced = advanced.restart();
            }

            Optional<ShipTransponderSnapshot> ship = ShipTransponderRegistry.snapshot(advanced.transponderId())
                    .filter(snapshot -> snapshot.dimension().equals(level.dimension()));
            if (ship.isEmpty() || ship.get().controllerRef().isEmpty()) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Removing active schedule for transponder {} because the ship controller is missing",
                        active.transponderId()
                );
                station.get().failPlayback(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
                transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
                lastStartFailures.put(active.transponderId(), PlaybackFailure.VEHICLE_MISSING);
                iterator.remove();
                continue;
            }

            PlaybackOperationResult<RouteId> result = startEntry(level, station.get(), advanced, ship.get());
            if (result.value().isPresent()) {
                entry.setValue(advanced.withActiveRoute(result.value().get()));
                BlockPos advancedTransponderPos = advanced.transponderPos();
                transponderAt(level, advancedTransponderPos)
                        .ifPresent(transponder -> transponder.setRuntimeStatus(resolveRuntimeStatusFromStation(station.get())));
                lastStartFailures.remove(activeTransponderId);
            } else {
                BlockPos advancedTransponderPos = advanced.transponderPos();
                result.failure().ifPresent(failure -> {
                    CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                            "Removing active schedule for transponder {} because starting next route failed with {}",
                            activeTransponderId,
                            failure
                    );
                    station.get().failPlayback(failure.failureReason());
                    transponderAt(level, advancedTransponderPos).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
                    lastStartFailures.put(activeTransponderId, failure);
                });
                iterator.remove();
            }
        }
    }

    private boolean activeScheduleEntryMissing(ShipTransponderBlockEntity transponder, ActiveAirshipSchedule active) {
        if (active.isFinished() || active.schedule().entries().isEmpty()) {
            return true;
        }
        if (active.entryIndex() < 0 || active.entryIndex() >= active.schedule().entries().size()) {
            return true;
        }
        AirshipScheduleEntry activeEntry = active.currentEntry();
        return transponder.ownedSchedule().entries().stream().noneMatch(entry -> sameScheduledLeg(entry, activeEntry));
    }

    private boolean sameScheduledLeg(AirshipScheduleEntry a, AirshipScheduleEntry b) {
        return a.targetStationId().equals(b.targetStationId())
                && a.pinnedSegmentId().equals(b.pinnedSegmentId());
    }

    private void reconcileOrphanPlaybacks(MinecraftServer server) {
        java.util.Set<RouteId> scheduledRoutes = activeSchedules.values().stream()
                .flatMap(active -> active.activeRouteId().stream())
                .collect(java.util.stream.Collectors.toSet());
        AutomatedLogisticsServices.PLAYBACK.holdUnscheduledPlaybacks(server, scheduledRoutes, PlaybackFailure.INVALID_ROUTE);
    }

    private void stopLinkedOrphanPlaybacks(ServerLevel level, UUID transponderId) {
        ShipTransponderRegistry.snapshot(transponderId)
                .flatMap(ShipTransponderSnapshot::controllerRef)
                .ifPresent(controllerRef ->
                        AutomatedLogisticsServices.PLAYBACK.stopLinkedPlaybacks(level, controllerRef, FailureReason.NONE));
    }

    public CompletionAdvanceResult advanceCompletedRoute(
            ServerLevel level,
            Route completedRoute,
            BlockPos completedStationPos,
            Optional<BlockPos> activeDockStationPos
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(completedRoute, "completedRoute");
        Objects.requireNonNull(completedStationPos, "completedStationPos");
        Objects.requireNonNull(activeDockStationPos, "activeDockStationPos");

        Map.Entry<UUID, ActiveAirshipSchedule> scheduleEntry = activeSchedules.entrySet().stream()
                .filter(entry -> entry.getValue().activeRouteId().filter(completedRoute.id()::equals).isPresent())
                .findFirst()
                .orElse(null);
        if (scheduleEntry == null) {
            return CompletionAdvanceResult.NOT_SCHEDULED;
        }

        ActiveAirshipSchedule active = scheduleEntry.getValue();
        Optional<AirshipStationBlockEntity> station = stationAt(level, active.currentStationPos());
        if (station.isEmpty()) {
            activeSchedules.remove(scheduleEntry.getKey());
            transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
            lastStartFailures.put(active.transponderId(), PlaybackFailure.STATION_MISSING);
            return CompletionAdvanceResult.FAILED;
        }

        ActiveAirshipSchedule advanced = active.advance();
        UUID activeTransponderId = advanced.transponderId();
        if (advanced.isFinished()) {
            if (!advanced.schedule().loop()) {
                activeSchedules.remove(scheduleEntry.getKey());
                transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
                lastStartFailures.remove(active.transponderId());
                return CompletionAdvanceResult.FINISHED;
            }
            advanced = advanced.restart();
        }

        Optional<ShipTransponderSnapshot> ship = ShipTransponderRegistry.snapshot(advanced.transponderId())
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()));
        if (ship.isEmpty() || ship.get().controllerRef().isEmpty()) {
            station.get().failPlayback(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            transponderAt(level, active.transponderPos()).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
            lastStartFailures.put(active.transponderId(), PlaybackFailure.VEHICLE_MISSING);
            activeSchedules.remove(scheduleEntry.getKey());
            return CompletionAdvanceResult.FAILED;
        }

        PlaybackOperationResult<RouteId> result = startEntry(level, station.get(), advanced, ship.get());
        if (result.value().isPresent()) {
            RouteId nextRouteId = result.value().get();
            activeSchedules.put(scheduleEntry.getKey(), advanced.withActiveRoute(nextRouteId));
            AutomatedLogisticsServices.PLAYBACK.deferDockOutputClear(
                    nextRouteId,
                    completedRoute,
                    completedStationPos,
                    activeDockStationPos
            );
            BlockPos advancedTransponderPos = advanced.transponderPos();
            transponderAt(level, advancedTransponderPos)
                    .ifPresent(transponder -> transponder.setRuntimeStatus(resolveRuntimeStatusFromStation(station.get())));
            lastStartFailures.remove(activeTransponderId);
            return CompletionAdvanceResult.STARTED_NEXT;
        }

        ActiveAirshipSchedule advancedForFailure = advanced;
        BlockPos advancedTransponderPos = advanced.transponderPos();
        boolean[] heldFaulted = {false};
        result.failure().ifPresent(failure -> {
            PlaybackOperationResult<RouteId> heldResult = AutomatedLogisticsServices.PLAYBACK.startHeldFaultPlayback(
                    level,
                    completedStationPos,
                    completedRoute,
                    failure
            );
            if (heldResult.value().isPresent()) {
                activeSchedules.put(scheduleEntry.getKey(), advancedForFailure.withActiveRoute(completedRoute.id()));
                transponderAt(level, advancedTransponderPos)
                        .ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.HELD_FAULTED));
                lastStartFailures.put(activeTransponderId, failure);
                heldFaulted[0] = true;
                return;
            }
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Removing active schedule for transponder {} because starting next route failed with {} and fault hold restore also failed",
                    activeTransponderId,
                    failure
            );
            station.get().failPlayback(failure.failureReason());
            transponderAt(level, advancedTransponderPos).ifPresent(transponder -> transponder.setRuntimeStatus(RouteStatus.IDLE));
            lastStartFailures.put(activeTransponderId, failure);
        });
        if (heldFaulted[0]) {
            return CompletionAdvanceResult.HELD_FAULT;
        }
        activeSchedules.remove(scheduleEntry.getKey());
        return CompletionAdvanceResult.FAILED;
    }

    public Optional<PlaybackFailure> lastFailure(UUID transponderId) {
        return Optional.ofNullable(lastStartFailures.get(transponderId));
    }

    public void clearLastFailure(UUID transponderId) {
        lastStartFailures.remove(transponderId);
    }

    private void clearStaleActiveSchedule(UUID transponderId) {
        ActiveAirshipSchedule active = activeSchedules.get(transponderId);
        if (active == null) {
            return;
        }
        if (active.activeRouteId().isEmpty()) {
            activeSchedules.remove(transponderId);
            return;
        }
        RouteId routeId = active.activeRouteId().get();
        if (!AutomatedLogisticsServices.PLAYBACK.isRunning(routeId)
                && !AutomatedLogisticsServices.PLAYBACK.isPending(routeId)
                && !AutomatedLogisticsServices.PLAYBACK.isHeld(routeId)) {
            activeSchedules.remove(transponderId);
        }
    }

    private BlockPos transponderPosition(ActiveAirshipSchedule active, UUID transponderId) {
        return active != null ? active.transponderPos() : ShipTransponderRegistry.snapshot(transponderId)
                .map(ShipTransponderSnapshot::transponderPos)
                .orElse(BlockPos.ZERO);
    }

    private RouteStatus resolveActiveRuntimeStatus(ServerLevel level, ActiveAirshipSchedule active) {
        if (active == null) {
            return RouteStatus.IDLE;
        }
        if (active.activeRouteId().isPresent() && AutomatedLogisticsServices.PLAYBACK.isHeld(active.activeRouteId().get())) {
            return AutomatedLogisticsServices.PLAYBACK.heldFailure(active.activeRouteId().get()).isPresent()
                    ? RouteStatus.HELD_FAULTED
                    : RouteStatus.HELD;
        }
        return stationAt(level, active.currentStationPos())
                .map(this::resolveRuntimeStatusFromStation)
                .orElse(RouteStatus.RUNNING);
    }

    private RouteStatus resolveRuntimeStatusFromStation(AirshipStationBlockEntity station) {
        RouteStatus status = station.status();
        if (status == RouteStatus.RECORDING || status == RouteStatus.RECORDED || status == RouteStatus.IDLE) {
            return RouteStatus.RUNNING;
        }
        return status;
    }

    private boolean playbackStillActive(RouteId routeId) {
        return AutomatedLogisticsServices.PLAYBACK.isRunning(routeId)
                || AutomatedLogisticsServices.PLAYBACK.isPending(routeId)
                || AutomatedLogisticsServices.PLAYBACK.isHeld(routeId);
    }

    private PlaybackOperationResult<RouteId> startEntry(
            ServerLevel level,
            AirshipStationBlockEntity station,
            ActiveAirshipSchedule active,
            ShipTransponderSnapshot ship
    ) {
        AirshipScheduleEntry entry = active.currentEntry();
        if (entry.targetStationId().isEmpty()) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }
        boolean targetStationMissing = entry.targetStationId().flatMap(AirshipStationRegistry::snapshot).isEmpty();

        Optional<RouteSegment> segment = entry.pinnedSegmentId()
                .flatMap(RouteSegmentRegistry::byId)
                .filter(candidate -> candidate.startStationId().equals(active.currentStationId()))
                .filter(candidate -> candidate.endStationId().equals(entry.targetStationId().get()))
                .filter(candidate -> candidate.dimension().equals(level.dimension()))
                .filter(candidate -> candidate.transponderId().equals(active.transponderId()))
                .or(() -> RouteSegmentResolver.newestFor(
                        active.currentStationId(),
                        entry.targetStationId().get(),
                        level.dimension(),
                        Optional.of(active.transponderId())
                ));

        if (segment.isEmpty()) {
            station.setFailure(FailureReason.INVALID_ROUTE_DATA);
            return PlaybackOperationResult.failure(PlaybackFailure.INVALID_ROUTE);
        }

        VehicleControllerRef controllerRef = ship.controllerRef().orElse(segment.get().controllerRef());
        Route route = routeFor(active, entry, segment.get(), controllerRef);
        if (targetStationMissing) {
            return AutomatedLogisticsServices.PLAYBACK.startHeldFaultPlayback(
                    level,
                    active.currentStationPos(),
                    route,
                    PlaybackFailure.STATION_MISSING
            );
        }
        PlaybackOperationResult<RouteId> result = AutomatedLogisticsServices.PLAYBACK.startPlayback(level, active.currentStationPos(), route);
        result.failure().ifPresent(failure -> station.failPlayback(failure.failureReason()));
        return result;
    }

    private Route routeFor(
            ActiveAirshipSchedule active,
            AirshipScheduleEntry entry,
            RouteSegment segment,
            VehicleControllerRef controllerRef
    ) {
        List<List<AirshipScheduleCondition>> conditionGroups = entry.effectiveConditionGroups();
        Optional<BlockPos> targetStationPos = entry.targetStationId()
                .flatMap(AirshipStationRegistry::snapshot)
                .map(snapshot -> snapshot.stationPos().immutable());
        List<RouteStop> stops = List.of(RouteStop.create(
                entry.displayStationName(),
                segment.points().size() - 1,
                entry.primaryEffectiveWaitCondition(),
                targetStationPos,
                conditionGroups
        ));
        return new Route(
                RouteId.create(),
                active.currentStationName() + " -> " + entry.displayStationName(),
                segment.dimension(),
                segment.points(),
                controllerRef,
                PlaybackMode.ONE_WAY,
                RouteStatus.RECORDED,
                stops,
                segment.ownerId()
        );
    }

    private Optional<AirshipStationBlockEntity> stationAt(ServerLevel level, BlockPos stationPos) {
        if (level.getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station) {
            return Optional.of(station);
        }
        return Optional.empty();
    }

    private boolean dockLinksReadyForSchedule(
            ServerLevel level,
            ShipTransponderBlockEntity transponder,
            AirshipSchedule schedule
    ) {
        if (!hasValidShipDock(transponder)) {
            return false;
        }
        for (AirshipScheduleEntry entry : schedule.entries()) {
            if (!entryRequiresDockLock(entry) || entry.targetStationId().isEmpty()) {
                continue;
            }
            Optional<AirshipStationBlockEntity> targetStation = entry.targetStationId()
                    .flatMap(AirshipStationRegistry::snapshot)
                    .flatMap(snapshot -> stationAt(level, snapshot.stationPos()));
            if (targetStation.isEmpty()) {
                return false;
            }
            DockLinkStatus dockStatus = targetStation.get().groundDockStatus();
            if (dockStatus != DockLinkStatus.LINKED || targetStation.get().groundDockPos().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasValidShipDock(ShipTransponderBlockEntity transponder) {
        return transponder.shipDockStatus() == DockLinkStatus.LINKED && transponder.shipDockPos().isPresent();
    }

    private boolean scheduleRequiresDocking(AirshipSchedule schedule) {
        return schedule.entries().stream().anyMatch(this::entryRequiresDockLock);
    }

    private boolean entryRequiresStationContext(AirshipScheduleEntry entry) {
        return entry.hasEffectiveWaitCondition(this::requiresStationContext);
    }

    private boolean entryRequiresDockLock(AirshipScheduleEntry entry) {
        return entry.hasEffectiveWaitCondition(this::requiresDockLock);
    }

    private boolean requiresStationContext(WaitCondition wait) {
        return requiresDockLock(wait);
    }

    private boolean requiresDockLock(WaitCondition wait) {
        return wait.type() == WaitConditionType.UNTIL_DOCKED
                || wait.type() == WaitConditionType.UNTIL_IDLE
                || wait.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD
                || wait.type() == WaitConditionType.UNTIL_ITEM_EMPTY
                || wait.type() == WaitConditionType.UNTIL_ITEM_FULL
                || wait.type() == WaitConditionType.UNTIL_FLUID_EMPTY
                || wait.type() == WaitConditionType.UNTIL_FLUID_FULL
                || wait.type() == WaitConditionType.UNTIL_EMPTY
                || wait.type() == WaitConditionType.UNTIL_FULL;
    }

    private Optional<ShipTransponderBlockEntity> transponderAt(ServerLevel level, BlockPos transponderPos) {
        if (level.getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }
        return Optional.empty();
    }

    private Optional<AirshipStationBlockEntity> nearestStartStation(ServerLevel level, ShipTransponderBlockEntity transponder) {
        return nearbyStartStations(level, transponder).stream().findFirst();
    }

    private List<AirshipStationBlockEntity> nearbyStartStations(ServerLevel level, ShipTransponderBlockEntity transponder) {
        List<net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot> stations =
                AirshipStationRegistry.knownStations(level.dimension());
        if (stations.isEmpty()) {
            return List.of();
        }
        double maxDistanceSqr = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get()
                * AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        Vec3 anchor = transponder.lastKnownPosition().orElse(Vec3.atCenterOf(transponder.getBlockPos()));

        return stations.stream()
                .filter(snapshot -> snapshot.stationPos().distToCenterSqr(anchor.x, anchor.y, anchor.z) <= maxDistanceSqr)
                .sorted((a, b) -> Double.compare(
                        a.stationPos().distToCenterSqr(anchor.x, anchor.y, anchor.z),
                        b.stationPos().distToCenterSqr(anchor.x, anchor.y, anchor.z)
                ))
                .map(snapshot -> stationAt(level, snapshot.stationPos()))
                .flatMap(Optional::stream)
                .toList();
    }

    private boolean isShipWithinLandingArea(ServerLevel level, BlockPos stationPos, UUID transponderId) {
        return ShipTransponderRegistry.snapshot(transponderId)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos())))
                .map(shipPos -> isWithinLandingArea(stationPos, shipPos))
                .orElse(false);
    }

    private boolean isShipWithinLandingArea(ServerLevel level, BlockPos stationPos, ShipTransponderBlockEntity transponder) {
        Vec3 shipPos = transponder.lastKnownPosition().orElse(Vec3.atCenterOf(transponder.getBlockPos()));
        return isWithinLandingArea(stationPos, shipPos);
    }

    private boolean isWithinLandingArea(BlockPos stationPos, Vec3 shipPos) {
        double radius = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        double radiusSqr = radius * radius;
        return stationPos.distToCenterSqr(shipPos.x, shipPos.y, shipPos.z) <= radiusSqr;
    }

    private CompoundTag writeActiveSchedule(ActiveAirshipSchedule active) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TRANSPONDER_ID, active.transponderId());
        tag.put(TRANSPONDER_POS, NbtUtils.writeBlockPos(active.transponderPos()));
        tag.putString(DIMENSION, active.dimension().location().toString());
        tag.put(SCHEDULE, AirshipScheduleNbtSerializer.write(active.schedule()));
        tag.putUUID(START_STATION_ID, active.startStationId());
        tag.putString(START_STATION_NAME, active.startStationName());
        tag.put(START_STATION_POS, NbtUtils.writeBlockPos(active.startStationPos()));
        tag.putUUID(CURRENT_STATION_ID, active.currentStationId());
        tag.putString(CURRENT_STATION_NAME, active.currentStationName());
        tag.put(CURRENT_STATION_POS, NbtUtils.writeBlockPos(active.currentStationPos()));
        tag.putInt(ENTRY_INDEX, active.entryIndex());
        active.activeRouteId().ifPresent(routeId -> tag.putUUID(ACTIVE_ROUTE_ID, routeId.value()));
        return tag;
    }

    private Optional<ActiveAirshipSchedule> readActiveSchedule(CompoundTag tag) {
        if (!tag.hasUUID(TRANSPONDER_ID)
                || !tag.contains(TRANSPONDER_POS)
                || !tag.contains(DIMENSION, Tag.TAG_STRING)
                || !tag.contains(SCHEDULE, Tag.TAG_COMPOUND)
                || !tag.hasUUID(START_STATION_ID)
                || !tag.contains(START_STATION_NAME, Tag.TAG_STRING)
                || !tag.contains(START_STATION_POS)
                || !tag.hasUUID(CURRENT_STATION_ID)
                || !tag.contains(CURRENT_STATION_NAME, Tag.TAG_STRING)
                || !tag.contains(CURRENT_STATION_POS)) {
            return Optional.empty();
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        if (dimensionId == null) {
            return Optional.empty();
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        AirshipSchedule schedule = AirshipScheduleNbtSerializer.read(tag.getCompound(SCHEDULE));
        int entryIndex = tag.contains(ENTRY_INDEX, Tag.TAG_ANY_NUMERIC) ? tag.getInt(ENTRY_INDEX) : 0;
        Optional<RouteId> activeRouteId = tag.hasUUID(ACTIVE_ROUTE_ID)
                ? Optional.of(new RouteId(tag.getUUID(ACTIVE_ROUTE_ID)))
                : Optional.empty();
        return NbtUtils.readBlockPos(tag, TRANSPONDER_POS).flatMap(transponderPos ->
                NbtUtils.readBlockPos(tag, START_STATION_POS).flatMap(startStationPos ->
                        NbtUtils.readBlockPos(tag, CURRENT_STATION_POS).map(currentStationPos ->
                                new ActiveAirshipSchedule(
                                        tag.getUUID(TRANSPONDER_ID),
                                        transponderPos.immutable(),
                                        dimension,
                                        schedule,
                                        tag.getUUID(START_STATION_ID),
                                        tag.getString(START_STATION_NAME),
                                        startStationPos.immutable(),
                                        tag.getUUID(CURRENT_STATION_ID),
                                        tag.getString(CURRENT_STATION_NAME),
                                        currentStationPos.immutable(),
                                        Math.max(0, entryIndex),
                                        activeRouteId
                                ))));
    }

    private Optional<PlaybackFailure> readPlaybackFailure(String value) {
        try {
            return Optional.of(PlaybackFailure.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public record ResolvedStartContext(
            AirshipStationBlockEntity station,
            UUID stationId,
            String stationName,
            BlockPos stationPos,
            AirshipSchedule runtimeSchedule
    ) {
    }
}

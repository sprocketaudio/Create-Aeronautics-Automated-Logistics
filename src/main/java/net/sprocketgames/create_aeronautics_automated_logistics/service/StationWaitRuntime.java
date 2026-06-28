package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockTransferSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;

public final class StationWaitRuntime {
    public ConditionTickResult tickConditionGroups(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> dockingStation,
            WaitEvaluation evaluation,
            Context context
    ) {
        List<List<AirshipScheduleCondition>> groups = evaluation.conditionGroups();
        boolean anyPendingGroup = false;
        Optional<PlaybackFailure> firstFailure = Optional.empty();

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            List<AirshipScheduleCondition> group = groups.get(groupIndex);
            boolean groupPending = false;
            boolean groupFailed = false;

            for (int conditionIndex = 0; conditionIndex < group.size(); conditionIndex++) {
                AirshipScheduleCondition condition = group.get(conditionIndex);
                ConditionRuntimeState state =
                        evaluation.conditionState(groupIndex, conditionIndex, condition.waitCondition());
                ConditionTickResult result = tickCondition(level, dockingStation, evaluation, context, condition.waitCondition(), state);
                if (result.failure().isPresent()) {
                    groupFailed = true;
                    if (firstFailure.isEmpty()) {
                        firstFailure = result.failure();
                    }
                    break;
                }
                if (!result.satisfied()) {
                    groupPending = true;
                }
            }

            if (!groupFailed && !groupPending) {
                return ConditionTickResult.completedResult();
            }
            if (!groupFailed) {
                anyPendingGroup = true;
            }
        }

        if (anyPendingGroup) {
            return ConditionTickResult.pendingResult();
        }
        return ConditionTickResult.failedResult(firstFailure.orElse(PlaybackFailure.INVALID_ROUTE));
    }

    public static boolean isCargoWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || type == WaitConditionType.UNTIL_FLUID_THRESHOLD
                || type == WaitConditionType.UNTIL_ITEM_EMPTY
                || type == WaitConditionType.UNTIL_ITEM_FULL
                || type == WaitConditionType.UNTIL_FLUID_EMPTY
                || type == WaitConditionType.UNTIL_FLUID_FULL
                || type == WaitConditionType.UNTIL_EMPTY
                || type == WaitConditionType.UNTIL_FULL;
    }

    private ConditionTickResult tickCondition(
            ServerLevel level,
            Optional<AirshipStationBlockEntity> dockingStation,
            WaitEvaluation evaluation,
            Context context,
            WaitCondition waitCondition,
            ConditionRuntimeState state
    ) {
        if (state.satisfied) {
            return ConditionTickResult.completedResult();
        }
        if (state.failure.isPresent()) {
            return ConditionTickResult.failedResult(state.failure.get());
        }

        switch (waitCondition.type()) {
            case NONE -> {
                state.markSatisfied();
                return ConditionTickResult.completedResult();
            }
            case TIMED -> {
                if (state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case UNTIL_DOCKED -> {
                if (evaluation.dockWaitFailure().isPresent()) {
                    state.markFailure(evaluation.dockWaitFailure().get());
                    return ConditionTickResult.failedResult(evaluation.dockWaitFailure().get());
                }
                if (!evaluation.dockLocked()) {
                    return ConditionTickResult.pendingResult();
                }
                if (state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case UNTIL_IDLE -> {
                if (evaluation.dockWaitFailure().isPresent()) {
                    state.markFailure(evaluation.dockWaitFailure().get());
                    return ConditionTickResult.failedResult(evaluation.dockWaitFailure().get());
                }
                if (!evaluation.dockLocked()) {
                    return ConditionTickResult.pendingResult();
                }
                if (dockingStation.isEmpty()) {
                    state.markFailure(PlaybackFailure.STATION_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.STATION_MISSING);
                }
                Optional<DockTransferSnapshot> snapshot =
                        context.dockTransferSnapshot(level, dockingStation.get(), evaluation.route());
                if (snapshot.isEmpty()) {
                    if (state.tickWait()) {
                        state.markSatisfied();
                        return ConditionTickResult.completedResult();
                    }
                    return ConditionTickResult.pendingResult();
                }
                if (state.dockTransferSnapshot.isEmpty()) {
                    state.dockTransferSnapshot = snapshot;
                } else if (!snapshot.get().equals(state.dockTransferSnapshot.get())) {
                    state.dockTransferSnapshot = snapshot;
                    state.resetIdleWait();
                    return ConditionTickResult.pendingResult();
                }
                if (state.tickIdleTimeout() || state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case REDSTONE_LINK, REDSTONE -> {
                if (waitCondition.redstoneFrequencyFirst().isEmpty() || waitCondition.redstoneFrequencySecond().isEmpty()) {
                    state.markFailure(PlaybackFailure.REDSTONE_LINK_UNCONFIGURED);
                    return ConditionTickResult.failedResult(PlaybackFailure.REDSTONE_LINK_UNCONFIGURED);
                }
                if (context.redstoneLinkSatisfied(waitCondition)) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case TIME_OF_DAY -> {
                if (context.timeOfDaySatisfied(level, waitCondition)) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL, UNTIL_EMPTY, UNTIL_FULL -> {
                if (evaluation.dockWaitFailure().isPresent()) {
                    state.markFailure(evaluation.dockWaitFailure().get());
                    return ConditionTickResult.failedResult(evaluation.dockWaitFailure().get());
                }
                if (!evaluation.dockLocked()) {
                    return ConditionTickResult.pendingResult();
                }
                if (waitCondition.cargoTarget() == CargoWaitTarget.STATION_CARGO && dockingStation.isEmpty()) {
                    state.markFailure(PlaybackFailure.STATION_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.STATION_MISSING);
                }
                Optional<List<LinkedCargoEntry>> targetEntries =
                        context.cargoEntries(level, dockingStation, evaluation.route(), waitCondition.cargoTarget());
                if (targetEntries.isEmpty()) {
                    CreateAeronauticsAutomatedLogistics.debugCargo(
                            "Cargo wait {} on playback {} failed: no target entries for {}",
                            waitCondition.type(),
                            evaluation.route().id().value(),
                            waitCondition.cargoTarget()
                    );
                    state.markFailure(PlaybackFailure.CARGO_STORAGE_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_STORAGE_MISSING);
                }
                if (context.cargoStorageMissing(level, targetEntries.get(), waitCondition, evaluation.route().id().value().toString())) {
                    state.markFailure(PlaybackFailure.CARGO_STORAGE_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_STORAGE_MISSING);
                }
                Optional<LinkedCargoSnapshot> snapshot = targetEntries
                        .map(entries -> LinkedCargoSnapshot.capture(level, entries))
                        .filter(captured -> context.relevantStoragePresent(captured, waitCondition));
                if (snapshot.isEmpty()) {
                    CreateAeronauticsAutomatedLogistics.debugCargo(
                            "Cargo wait {} on playback {} failed: snapshot had no relevant {} storage for entries {}",
                            waitCondition.type(),
                            evaluation.route().id().value(),
                            waitCondition.type(),
                            context.summarizeCargoEntries(targetEntries.get())
                    );
                    state.markFailure(PlaybackFailure.CARGO_STORAGE_MISSING);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_STORAGE_MISSING);
                }
                if (context.cargoConditionSatisfied(level, snapshot.get(), waitCondition)) {
                    if (state.tickWait()) {
                        state.markSatisfied();
                        return ConditionTickResult.completedResult();
                    }
                    return ConditionTickResult.pendingResult();
                }
                context.logCargoConditionPending(evaluation, waitCondition, snapshot.get(), state, targetEntries.get());
                state.resetIdleWait();
                if (state.tickCargoTimeout()) {
                    state.markFailure(PlaybackFailure.CARGO_CONDITION_TIMEOUT);
                    return ConditionTickResult.failedResult(PlaybackFailure.CARGO_CONDITION_TIMEOUT);
                }
                return ConditionTickResult.pendingResult();
            }
            default -> {
                if (state.tickWait()) {
                    state.markSatisfied();
                    return ConditionTickResult.completedResult();
                }
                return ConditionTickResult.pendingResult();
            }
        }
    }

    public record WaitEvaluation(
            Route route,
            Optional<RouteStop> waitingStop,
            List<List<AirshipScheduleCondition>> conditionGroups,
            ConditionStateLookup conditionStateLookup,
            boolean dockLocked,
            Optional<PlaybackFailure> dockWaitFailure,
            boolean shouldLogProgress
    ) {
        ConditionRuntimeState conditionState(int groupIndex, int conditionIndex, WaitCondition waitCondition) {
            return conditionStateLookup.get(groupIndex, conditionIndex, waitCondition);
        }
    }

    @FunctionalInterface
    public interface ConditionStateLookup {
        ConditionRuntimeState get(int groupIndex, int conditionIndex, WaitCondition waitCondition);
    }

    public interface Context {
        Optional<DockTransferSnapshot> dockTransferSnapshot(
                ServerLevel level,
                AirshipStationBlockEntity station,
                Route route
        );

        Optional<List<LinkedCargoEntry>> cargoEntries(
                ServerLevel level,
                Optional<AirshipStationBlockEntity> station,
                Route route,
                CargoWaitTarget target
        );

        boolean cargoStorageMissing(
                ServerLevel level,
                List<LinkedCargoEntry> entries,
                WaitCondition waitCondition,
                String playbackId
        );

        boolean relevantStoragePresent(LinkedCargoSnapshot snapshot, WaitCondition waitCondition);

        boolean cargoConditionSatisfied(ServerLevel level, LinkedCargoSnapshot snapshot, WaitCondition waitCondition);

        void logCargoConditionPending(
                WaitEvaluation evaluation,
                WaitCondition waitCondition,
                LinkedCargoSnapshot snapshot,
                ConditionRuntimeState state,
                List<LinkedCargoEntry> entries
        );

        String summarizeCargoEntries(List<LinkedCargoEntry> entries);

        boolean redstoneLinkSatisfied(WaitCondition waitCondition);

        boolean timeOfDaySatisfied(ServerLevel level, WaitCondition waitCondition);
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Objects;
import java.util.Optional;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockTransferSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;

final class ConditionRuntimeState {
    int waitTicksRemaining;
    int idleWindowTicks;
    int idleTimeoutTicksRemaining;
    int cargoTimeoutTicksRemaining;
    Optional<DockTransferSnapshot> dockTransferSnapshot = Optional.empty();
    boolean satisfied;
    Optional<PlaybackFailure> failure;

    ConditionRuntimeState(
            int waitTicksRemaining,
            int idleWindowTicks,
            int idleTimeoutTicksRemaining,
            int cargoTimeoutTicksRemaining,
            boolean satisfied,
            Optional<PlaybackFailure> failure
    ) {
        this.waitTicksRemaining = Math.max(0, waitTicksRemaining);
        this.idleWindowTicks = Math.max(0, idleWindowTicks);
        this.idleTimeoutTicksRemaining = Math.max(0, idleTimeoutTicksRemaining);
        this.cargoTimeoutTicksRemaining = Math.max(0, cargoTimeoutTicksRemaining);
        this.satisfied = satisfied;
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    static ConditionRuntimeState initialize(WaitCondition waitCondition) {
        int waitTicks = StationWaitRuntime.isCargoWaitType(waitCondition.type())
                ? Math.max(0, waitCondition.cargoStabilityTicks())
                : Math.max(0, waitCondition.runtimeTicks());
        if (waitCondition.type() == WaitConditionType.UNTIL_IDLE && waitTicks <= 0) {
            waitTicks = WaitCondition.DEFAULT_TIMED_WAIT_TICKS;
        }
        int idleTimeout = waitCondition.type() == WaitConditionType.UNTIL_IDLE
                ? (waitCondition.maxTicks() > 0
                ? waitCondition.maxTicks()
                : AutomatedLogisticsConfig.DOCK_IDLE_TIMEOUT_TICKS.get())
                : 0;
        int cargoTimeout = StationWaitRuntime.isCargoWaitType(waitCondition.type())
                ? Math.max(0, waitCondition.maxTicks())
                : 0;
        return new ConditionRuntimeState(waitTicks, waitTicks, idleTimeout, cargoTimeout, false, Optional.empty());
    }

    void markSatisfied() {
        this.satisfied = true;
        this.failure = Optional.empty();
    }

    void markFailure(PlaybackFailure failure) {
        this.satisfied = false;
        this.failure = Optional.of(failure);
    }

    void clearFailure() {
        this.failure = Optional.empty();
    }

    boolean tickWait() {
        if (waitTicksRemaining > 0) {
            waitTicksRemaining--;
        }
        return waitTicksRemaining <= 0;
    }

    void resetIdleWait() {
        waitTicksRemaining = Math.max(1, idleWindowTicks);
    }

    void resetWaitWindow() {
        waitTicksRemaining = Math.max(0, idleWindowTicks);
    }

    String summary() {
        return "wait="
                + waitTicksRemaining
                + "/"
                + idleWindowTicks
                + ",idleTimeout="
                + idleTimeoutTicksRemaining
                + ",cargoTimeout="
                + cargoTimeoutTicksRemaining
                + ",satisfied="
                + satisfied
                + ",failure="
                + failure.map(Enum::name).orElse("none");
    }

    boolean tickIdleTimeout() {
        if (idleTimeoutTicksRemaining > 0) {
            idleTimeoutTicksRemaining--;
        }
        return idleTimeoutTicksRemaining <= 0;
    }

    boolean tickCargoTimeout() {
        if (cargoTimeoutTicksRemaining <= 0) {
            return false;
        }
        if (cargoTimeoutTicksRemaining > 0) {
            cargoTimeoutTicksRemaining--;
        }
        return cargoTimeoutTicksRemaining <= 0;
    }
}

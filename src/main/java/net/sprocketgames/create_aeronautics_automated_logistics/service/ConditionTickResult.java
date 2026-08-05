package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Optional;

record ConditionTickResult(boolean satisfied, Optional<PlaybackFailure> failure) {
    static ConditionTickResult completedResult() {
        return new ConditionTickResult(true, Optional.empty());
    }

    static ConditionTickResult pendingResult() {
        return new ConditionTickResult(false, Optional.empty());
    }

    static ConditionTickResult failedResult(PlaybackFailure failure) {
        return new ConditionTickResult(false, Optional.of(failure));
    }
}

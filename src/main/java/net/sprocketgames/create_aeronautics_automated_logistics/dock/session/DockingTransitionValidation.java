package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.Objects;

public record DockingTransitionValidation(boolean allowed, String reason) {
    public DockingTransitionValidation {
        Objects.requireNonNull(reason, "reason");
    }

    public static DockingTransitionValidation accepted() {
        return new DockingTransitionValidation(true, "allowed");
    }

    public static DockingTransitionValidation refused(String reason) {
        return new DockingTransitionValidation(false, reason);
    }
}

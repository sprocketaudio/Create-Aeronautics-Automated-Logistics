package net.sprocketgames.create_aeronautics_automated_logistics.display;

import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingPhase;

final class VehicleDisplayStatus {
    private VehicleDisplayStatus() {
    }

    static String label(String runtimeState, DockingPhase dockingPhase) {
        return switch (runtimeState) {
            case "RESTORING", "PENDING_RESTORE" -> "Restoring";
            case "DOCK_QUEUED" -> "Queued";
            case "PAUSED", "PENDING_HELD_MANUAL", "PENDING_HELD_TRANSIENT" -> "Paused";
            case "PENDING_HELD_FAULTED" -> "Faulted";
            case "DOCKED" -> "Docked";
            case "WAITING" -> dockingPhase == DockingPhase.DOCKED || dockingPhase == DockingPhase.WAITING
                    ? "Docked"
                    : "Waiting";
            default -> switch (dockingPhase) {
                case QUEUE_REQUESTED, QUEUED -> "Queued";
                case APPROACHING, ALIGNING, LOCKING -> "Arriving";
                case DOCKED, WAITING -> "Docked";
                case RELEASING, DEPARTING_CLEARANCE -> "Departing";
                case BLOCKED -> "Held";
                case RECOVERING -> "Recovering";
                case FAULTED -> "Faulted";
                default -> "ACTIVE".equals(runtimeState) || "UNLOADED_TRANSIT".equals(runtimeState)
                        ? "Travelling"
                        : "Ready";
            };
        };
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockingPhase;
import org.junit.jupiter.api.Test;

class AirshipStationSummaryDisplaySourceTest {
    @Test
    void activeVehicleUsesDockingPhaseForPlayerFacingStatus() {
        assertEquals("Travelling", status("ACTIVE", DockingPhase.NONE));
        assertEquals("Arriving", status("ACTIVE", DockingPhase.APPROACHING));
        assertEquals("Arriving", status("ACTIVE", DockingPhase.LOCKING));
        assertEquals("Departing", status("ACTIVE", DockingPhase.DEPARTING_CLEARANCE));
    }

    @Test
    void dockAndExceptionalStatesRemainExplicit() {
        assertEquals("Docked", status("DOCKED", DockingPhase.WAITING));
        assertEquals("Queued", status("DOCK_QUEUED", DockingPhase.QUEUED));
        assertEquals("Restoring", status("RESTORING", DockingPhase.RECOVERING));
        assertEquals("Paused", status("PAUSED", DockingPhase.APPROACHING));
        assertEquals("Faulted", status("PENDING_HELD_FAULTED", DockingPhase.FAULTED));
    }

    private static String status(String runtimeState, DockingPhase dockingPhase) {
        return VehicleDisplayStatus.label(runtimeState, dockingPhase);
    }
}

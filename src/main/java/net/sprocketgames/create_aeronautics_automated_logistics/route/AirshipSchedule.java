package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AirshipSchedule(
        String title,
        boolean loop,
        Optional<UUID> assignedTransponderId,
        String assignedShipName,
        List<AirshipScheduleEntry> entries
) {
    public AirshipSchedule {
        title = title == null || title.isBlank() ? "Airship Schedule" : title;
        assignedTransponderId = Objects.requireNonNull(assignedTransponderId, "assignedTransponderId");
        assignedShipName = assignedShipName == null ? "" : assignedShipName;
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static AirshipSchedule empty() {
        return new AirshipSchedule("Airship Schedule", true, Optional.empty(), "", List.of());
    }

    public AirshipSchedule withEntries(List<AirshipScheduleEntry> entries) {
        return new AirshipSchedule(title, loop, assignedTransponderId, assignedShipName, entries);
    }

    public AirshipSchedule withLoop(boolean loop) {
        return new AirshipSchedule(title, loop, assignedTransponderId, assignedShipName, entries);
    }

    public AirshipSchedule withTitle(String title) {
        return new AirshipSchedule(title, loop, assignedTransponderId, assignedShipName, entries);
    }

    public AirshipSchedule withAssignedShip(Optional<UUID> assignedTransponderId, String assignedShipName) {
        return new AirshipSchedule(title, loop, assignedTransponderId, assignedShipName, entries);
    }
}

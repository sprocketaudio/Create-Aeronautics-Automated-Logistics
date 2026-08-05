package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DockingSessionReadResult(
        Status status,
        Optional<DockingSession> session,
        List<String> diagnostics
) {
    public DockingSessionReadResult {
        Objects.requireNonNull(status, "status");
        session = Objects.requireNonNull(session, "session");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public enum Status {
        RESTORED,
        LEGACY_MISSING,
        INVALID
    }
}

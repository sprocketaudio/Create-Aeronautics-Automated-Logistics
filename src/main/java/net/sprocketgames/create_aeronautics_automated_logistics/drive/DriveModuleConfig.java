package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import java.util.Objects;

/**
 * Module-local wiring configuration. The controller may inspect this later,
 * but changing it belongs to the Drive Module UI.
 */
public record DriveModuleConfig(DriveIntentType intent, DriveEncodingType encoding) {
    public DriveModuleConfig {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(encoding, "encoding");
        if (!intent.supports(encoding)) {
            throw new IllegalArgumentException("Encoding " + encoding + " is not supported by " + intent);
        }
    }
}

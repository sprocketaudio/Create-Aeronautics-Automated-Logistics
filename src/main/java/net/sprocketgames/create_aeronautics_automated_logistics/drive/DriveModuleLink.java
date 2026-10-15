package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable ownership link between one Drive Module and one Advanced Transponder.
 * Same-ship validation is intentionally deferred until live linking exists.
 */
public record DriveModuleLink(UUID moduleId, UUID controllerTransponderId) {
    public DriveModuleLink {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(controllerTransponderId, "controllerTransponderId");
        if (moduleId.equals(controllerTransponderId)) {
            throw new IllegalArgumentException("A Drive Module cannot link to itself");
        }
    }
}

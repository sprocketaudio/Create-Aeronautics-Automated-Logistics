package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Complete persisted-shape model for a future Drive Module. It deliberately
 * carries no redstone signal or solver result during the scaffolding phase.
 */
public record DriveModuleState(
        UUID moduleId,
        Optional<DriveModuleLink> link,
        Optional<DriveModuleConfig> config,
        DriveModuleHealth health
) {
    public DriveModuleState {
        Objects.requireNonNull(moduleId, "moduleId");
        link = Objects.requireNonNull(link, "link");
        config = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(health, "health");
        if (link.isEmpty() != config.isEmpty()) {
            throw new IllegalArgumentException("Drive Module link and config must be present together");
        }
        if (link.isPresent() && !link.get().moduleId().equals(moduleId)) {
            throw new IllegalArgumentException("Drive Module link does not belong to this module");
        }
    }

    public static DriveModuleState unlinked(UUID moduleId) {
        return new DriveModuleState(moduleId, Optional.empty(), Optional.empty(), DriveModuleHealth.UNLINKED);
    }

    public boolean linked() {
        return link.isPresent();
    }
}

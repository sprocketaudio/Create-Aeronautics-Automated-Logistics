package net.sprocketgames.create_aeronautics_automated_logistics.map;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

public record ShipMapMarker(
        UUID transponderId,
        String shipName,
        TransportMode transportMode,
        ResourceKey<Level> dimension,
        Vec3 position,
        String state
) {
    public ShipMapMarker {
        Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(shipName, "shipName");
        transportMode = transportMode == null ? TransportMode.DEFAULT : transportMode;
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
    }

    public String displayState() {
        if (state.isBlank()) {
            return state;
        }
        String normalized = state.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (character == ' ') {
                capitalize = true;
                builder.append(character);
                continue;
            }
            builder.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = false;
        }
        return builder.toString();
    }
}

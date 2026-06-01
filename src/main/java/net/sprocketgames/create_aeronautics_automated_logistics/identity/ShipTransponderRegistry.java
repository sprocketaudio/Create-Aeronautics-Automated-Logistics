package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ShipTransponderRegistry {
    private static final Map<UUID, ShipTransponderSnapshot> SHIPS = new ConcurrentHashMap<>();

    private ShipTransponderRegistry() {
    }

    public static void register(ShipTransponderSnapshot snapshot) {
        SHIPS.put(snapshot.transponderId(), snapshot);
    }

    public static void unregister(UUID transponderId) {
        SHIPS.remove(transponderId);
    }

    public static Optional<ShipTransponderSnapshot> snapshot(UUID transponderId) {
        return Optional.ofNullable(SHIPS.get(transponderId));
    }

    public static List<ShipTransponderSnapshot> allShips() {
        return SHIPS.values().stream()
                .sorted(Comparator
                        .comparing(ShipTransponderSnapshot::shipName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(snapshot -> snapshot.transponderId().toString()))
                .toList();
    }

    public static List<ShipTransponderSnapshot> knownShips(ResourceKey<Level> dimension) {
        return SHIPS.values().stream()
                .filter(snapshot -> snapshot.dimension().equals(dimension))
                .sorted(Comparator
                        .comparing(ShipTransponderSnapshot::shipName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(snapshot -> snapshot.transponderId().toString()))
                .toList();
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class AirshipStationRegistry {
    private static final Map<UUID, AirshipStationSnapshot> STATIONS = new ConcurrentHashMap<>();

    private AirshipStationRegistry() {
    }

    public static void register(AirshipStationSnapshot snapshot) {
        STATIONS.put(snapshot.stationId(), snapshot);
    }

    public static void unregister(UUID stationId) {
        STATIONS.remove(stationId);
    }

    public static Optional<AirshipStationSnapshot> snapshot(UUID stationId) {
        return Optional.ofNullable(STATIONS.get(stationId));
    }

    public static List<AirshipStationSnapshot> allStations() {
        return STATIONS.values().stream()
                .sorted(Comparator
                        .comparing(AirshipStationSnapshot::stationName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(snapshot -> snapshot.stationId().toString()))
                .toList();
    }

    public static List<AirshipStationSnapshot> knownStations(ResourceKey<Level> dimension) {
        return STATIONS.values().stream()
                .filter(snapshot -> snapshot.dimension().equals(dimension))
                .sorted(Comparator
                        .comparing(AirshipStationSnapshot::stationName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(snapshot -> snapshot.stationId().toString()))
                .toList();
    }
}

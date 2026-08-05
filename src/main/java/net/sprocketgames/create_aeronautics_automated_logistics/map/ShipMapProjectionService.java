package net.sprocketgames.create_aeronautics_automated_logistics.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.LogisticsTerminalPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public final class ShipMapProjectionService {
    private ShipMapProjectionService() {
    }

    public static java.util.List<ShipMapMarker> snapshotsFor(ServerPlayer player) {
        return snapshotsFor(player, false);
    }

    public static java.util.List<ShipMapMarker> previewSnapshotsFor(ServerPlayer player) {
        return snapshotsFor(player, true);
    }

    private static java.util.List<ShipMapMarker> snapshotsFor(ServerPlayer player, boolean previewAudience) {
        Map<UUID, ShipMapMarker> markers = new LinkedHashMap<>();
        IdentityDirectorySavedData.get(player.server).allShips().stream()
                .filter(ship -> ship.ownerId().isPresent())
                .filter(ship -> previewAudience
                        ? LogisticsTerminalPermissionService.canReceivePreviewData(player, ship.ownerId().get())
                        : TransponderPermissionService.canControl(player, ship.ownerId()))
                .forEach(ship -> markers.put(ship.transponderId(), new ShipMapMarker(
                        ship.transponderId(),
                        ship.shipName(),
                        ship.transportMode(),
                        ship.dimension(),
                        ship.lastKnownPosition().orElse(Vec3.atCenterOf(ship.transponderPos())),
                        "IDLE"
                )));

        AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(player.server).stream()
                .filter(summary -> summary.transponderId().isPresent() && summary.position().isPresent())
                .filter(summary -> summary.ownerId().isPresent())
                .filter(summary -> previewAudience
                        ? LogisticsTerminalPermissionService.canReceivePreviewData(player, summary.ownerId().get())
                        : TransponderPermissionService.canControl(player, summary.ownerId()))
                .forEach(summary -> markers.put(summary.transponderId().get(), new ShipMapMarker(
                        summary.transponderId().get(),
                        summary.shipName(),
                        summary.transportMode(),
                        summary.dimension(),
                        summary.position().get(),
                        summary.state()
                )));
        return markers.values().stream()
                .sorted(java.util.Comparator.comparing(ShipMapMarker::shipName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}

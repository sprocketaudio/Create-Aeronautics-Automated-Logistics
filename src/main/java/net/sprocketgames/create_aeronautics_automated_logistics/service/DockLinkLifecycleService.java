package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingConnectorDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;

/** Clears affected dock state and runtime ownership before a linked connector is removed. */
public final class DockLinkLifecycleService {
    private DockLinkLifecycleService() {
    }

    public static void onBlockBreak(BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !DockingConnectorDiscovery.isDock(level, event.getPos())) {
            return;
        }

        AirshipStationRegistry.knownStations(level.dimension()).forEach(snapshot -> {
            if (!(level.getBlockEntity(snapshot.stationPos()) instanceof AirshipStationBlockEntity station)
                    || station.groundDockPos().filter(event.getPos()::equals).isEmpty()) {
                return;
            }
            station.clearGroundDockLink();
            AutomatedLogisticsServices.SCHEDULES.runningTransponderAtStation(station.getBlockPos())
                    .ifPresent(transponderId -> stopDockedSchedule(level, transponderId, "station_connector_removed"));
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Station dock link cleared because its connector was removed: station={} stationPos={} dock={} player={}",
                    station.stationId(),
                    station.getBlockPos().toShortString(),
                    event.getPos().toShortString(),
                    event.getPlayer() == null ? "unknown" : event.getPlayer().getGameProfile().getName()
            );
        });

        // A ship-side connector lives in a Sable sub-level, so it has no station block
        // entity to discover from the overworld position. Resolve its owning ship directly.
        SableSubLevelVehicleController.subLevelIdAt(level, event.getPos()).ifPresent(shipId ->
                ShipTransponderRegistry.knownShips(level.dimension()).stream()
                        .filter(snapshot -> snapshot.runtimeShipId().filter(shipId::equals).isPresent())
                        .forEach(snapshot -> stopDockedSchedule(level, snapshot.transponderId(), "ship_connector_removed"))
        );
    }

    private static void stopDockedSchedule(ServerLevel level, UUID transponderId, String reason) {
        if (!AutomatedLogisticsServices.SCHEDULES.isRunning(transponderId)) {
            return;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(level, transponderId);
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Stopped active docking schedule because a linked connector was removed: transponder={} reason={}",
                transponderId,
                reason
        );
    }
}

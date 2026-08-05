package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncAutomatedShipVisualsPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapProjectionService;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalPreviewMarkersPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalPreviewRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncVisibleLogisticsTerminalPreviewsPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncShipMapMarkersPayload;

public final class AutomationVisualServerEvents {
    private AutomationVisualServerEvents() {
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new SyncAutomatedShipVisualsPayload(AutomatedLogisticsServices.PLAYBACK.activeVisualShipIds().stream().toList())
        );
        PacketDistributor.sendToPlayer(player, new SyncShipMapMarkersPayload(ShipMapProjectionService.snapshotsFor(player)));
        SyncLogisticsTerminalRoutesPayload.sendTo(player);
        SyncLogisticsTerminalPreviewMarkersPayload.sendTo(player);
        SyncLogisticsTerminalPreviewRoutesPayload.sendTo(player);
        SyncVisibleLogisticsTerminalPreviewsPayload.sendTo(player);
    }
}

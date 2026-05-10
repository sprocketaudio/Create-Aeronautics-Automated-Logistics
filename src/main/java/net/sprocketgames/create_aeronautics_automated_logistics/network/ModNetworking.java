package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                UpdateIdentityNamePayload.TYPE,
                UpdateIdentityNamePayload.STREAM_CODEC,
                UpdateIdentityNamePayload::handle
        );
        registrar.playToServer(
                UpdateAirshipScheduleTitlePayload.TYPE,
                UpdateAirshipScheduleTitlePayload.STREAM_CODEC,
                UpdateAirshipScheduleTitlePayload::handle
        );
        registrar.playToServer(
                UpdateAirshipSchedulePayload.TYPE,
                UpdateAirshipSchedulePayload.STREAM_CODEC,
                UpdateAirshipSchedulePayload::handle
        );
        registrar.playToServer(
                SelectAirshipScheduleStationPayload.TYPE,
                SelectAirshipScheduleStationPayload.STREAM_CODEC,
                SelectAirshipScheduleStationPayload::handle
        );
        registrar.playToClient(
                SetFlightPathPreviewPayload.TYPE,
                SetFlightPathPreviewPayload.STREAM_CODEC,
                SetFlightPathPreviewPayload::handle
        );
        registrar.playToClient(
                SetAutomatedShipVisualStatePayload.TYPE,
                SetAutomatedShipVisualStatePayload.STREAM_CODEC,
                SetAutomatedShipVisualStatePayload::handle
        );
        registrar.playToClient(
                SyncAutomatedShipVisualsPayload.TYPE,
                SyncAutomatedShipVisualsPayload.STREAM_CODEC,
                SyncAutomatedShipVisualsPayload::handle
        );
        registrar.playToClient(
                SetDockLinkPromptPayload.TYPE,
                SetDockLinkPromptPayload.STREAM_CODEC,
                SetDockLinkPromptPayload::handle
        );
        registrar.playToClient(
                SetMenuActionBarMessagePayload.TYPE,
                SetMenuActionBarMessagePayload.STREAM_CODEC,
                SetMenuActionBarMessagePayload::handle
        );
    }
}

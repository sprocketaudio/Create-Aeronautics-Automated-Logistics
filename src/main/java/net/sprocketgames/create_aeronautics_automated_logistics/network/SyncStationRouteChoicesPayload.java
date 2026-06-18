package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.AirshipStationScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;

public record SyncStationRouteChoicesPayload(
        BlockPos stationPos,
        List<AirshipStationMenu.RouteChoiceSummary> routeChoices
) implements CustomPacketPayload {
    public static final Type<SyncStationRouteChoicesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "sync_station_route_choices"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStationRouteChoicesPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncStationRouteChoicesPayload::write, SyncStationRouteChoicesPayload::read);

    private static SyncStationRouteChoicesPayload read(RegistryFriendlyByteBuf buffer) {
        return new SyncStationRouteChoicesPayload(
                buffer.readBlockPos(),
                AirshipStationMenu.readRouteChoiceSummaries(buffer)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(stationPos);
        AirshipStationMenu.writeRouteChoiceSummaries(buffer, routeChoices);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncStationRouteChoicesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BlockPos zero = BlockPos.ZERO;
            if (Minecraft.getInstance().screen instanceof AirshipStationScreen screen
                    && (screen.stationMenu().stationPos().equals(payload.stationPos())
                    || screen.stationMenu().stationPos().equals(zero))) {
                screen.stationMenu().applyClientRouteChoicesSync(payload.stationPos(), payload.routeChoices());
                var previewedRouteId = LogisticsClientOverlays.previewedRouteId();
                if (previewedRouteId.isPresent()
                        && payload.routeChoices().stream().noneMatch(route -> route.id().equals(previewedRouteId.get()))) {
                    LogisticsClientOverlays.clearFlightPath();
                    screen.clearPreviewedRouteSelection();
                }
            }
        });
    }
}

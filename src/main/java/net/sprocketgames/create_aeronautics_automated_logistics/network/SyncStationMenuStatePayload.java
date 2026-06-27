package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.AirshipStationScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;

public record SyncStationMenuStatePayload(
        BlockPos stationPos,
        AirshipStationMenu.ClientState state
) implements CustomPacketPayload {
    public static final Type<SyncStationMenuStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "sync_station_menu_state"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStationMenuStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncStationMenuStatePayload::write, SyncStationMenuStatePayload::read);

    private static SyncStationMenuStatePayload read(RegistryFriendlyByteBuf buffer) {
        return new SyncStationMenuStatePayload(
                buffer.readBlockPos(),
                AirshipStationMenu.readClientState(buffer)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(stationPos);
        AirshipStationMenu.writeClientState(buffer, state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncStationMenuStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station sync payload received pos={} selectedId={} selectedShip='{}' shipChoices={} routeChoices={} selectedPresent={} selectedRuntime={} selectedCanRun={} selectedCanStop={}",
                    payload.stationPos(),
                    payload.state().selectedTransponderId().map(java.util.UUID::toString).orElse("<none>"),
                    payload.state().selectedShipName(),
                    payload.state().shipChoices().size(),
                    payload.state().routeChoices().size(),
                    payload.state().selectedShipState().present(),
                    payload.state().selectedShipState().runtimeStatus(),
                    payload.state().selectedShipState().canRun(),
                    payload.state().selectedShipState().canStop()
            );
            BlockPos zero = BlockPos.ZERO;
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof AirshipStationMenu menu
                    && (menu.stationPos().equals(payload.stationPos()) || menu.stationPos().equals(zero))) {
                menu.applyClientStateSync(payload.stationPos(), payload.state());
            }
            if (Minecraft.getInstance().screen instanceof AirshipStationScreen screen
                    && (screen.stationMenu().stationPos().equals(payload.stationPos())
                    || screen.stationMenu().stationPos().equals(zero))) {
                screen.stationMenu().applyClientStateSync(payload.stationPos(), payload.state());
            }
        });
    }
}

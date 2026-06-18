package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;

public record AirshipStationMenuActionPayload(int containerId, BlockPos stationPos, int actionId) implements CustomPacketPayload {
    public static final Type<AirshipStationMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "airship_station_menu_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AirshipStationMenuActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(AirshipStationMenuActionPayload::write, AirshipStationMenuActionPayload::read);

    private static AirshipStationMenuActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new AirshipStationMenuActionPayload(buffer.readVarInt(), buffer.readBlockPos(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeBlockPos(stationPos);
        buffer.writeVarInt(actionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AirshipStationMenuActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AirshipStationMenu menu)) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station menu action rejected currentMenu={} player={} spectator={} action={} pos={}",
                    player.containerMenu == null ? "<null>" : player.containerMenu.getClass().getSimpleName(),
                    player.getName().getString(),
                    player.isSpectator(),
                    payload.actionId(),
                    payload.stationPos()
            );
            return;
        }
        if (menu.containerId != payload.containerId() || !menu.stationPos().equals(payload.stationPos())) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Station menu action rejected mismatch menuId={} payloadId={} menuPos={} payloadPos={} player={} spectator={} action={}",
                    menu.containerId,
                    payload.containerId(),
                    menu.stationPos(),
                    payload.stationPos(),
                    player.getName().getString(),
                    player.isSpectator(),
                    payload.actionId()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Station menu action forwarding player={} spectator={} menuId={} pos={} action={}",
                player.getName().getString(),
                player.isSpectator(),
                payload.containerId(),
                payload.stationPos(),
                payload.actionId()
        );
        menu.clickMenuButton(player, payload.actionId());
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;

public record ShipTransponderMenuActionPayload(int containerId, BlockPos transponderPos, int actionId) implements CustomPacketPayload {
    public static final Type<ShipTransponderMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "ship_transponder_menu_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipTransponderMenuActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(ShipTransponderMenuActionPayload::write, ShipTransponderMenuActionPayload::read);

    private static ShipTransponderMenuActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new ShipTransponderMenuActionPayload(buffer.readVarInt(), buffer.readBlockPos(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeBlockPos(transponderPos);
        buffer.writeVarInt(actionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShipTransponderMenuActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof ShipTransponderMenu menu)) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder menu action rejected currentMenu={} player={} spectator={} action={} pos={}",
                    player.containerMenu == null ? "<null>" : player.containerMenu.getClass().getSimpleName(),
                    player.getName().getString(),
                    player.isSpectator(),
                    payload.actionId(),
                    payload.transponderPos()
            );
            return;
        }
        if (menu.containerId != payload.containerId() || !menu.transponderPos().equals(payload.transponderPos())) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder menu action rejected mismatch menuId={} payloadId={} menuPos={} payloadPos={} player={} spectator={} action={}",
                    menu.containerId,
                    payload.containerId(),
                    menu.transponderPos(),
                    payload.transponderPos(),
                    player.getName().getString(),
                    player.isSpectator(),
                    payload.actionId()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder menu action forwarding player={} spectator={} menuId={} pos={} action={}",
                player.getName().getString(),
                player.isSpectator(),
                payload.containerId(),
                payload.transponderPos(),
                payload.actionId()
        );
        menu.clickMenuButton(player, payload.actionId());
    }
}

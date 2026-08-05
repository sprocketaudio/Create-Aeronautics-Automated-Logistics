package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;

public record AirshipScheduleMenuActionPayload(
        int containerId,
        BlockPos transponderPos,
        int actionId
) implements CustomPacketPayload {
    public static final Type<AirshipScheduleMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "airship_schedule_menu_action"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AirshipScheduleMenuActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(AirshipScheduleMenuActionPayload::write, AirshipScheduleMenuActionPayload::read);

    private static AirshipScheduleMenuActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new AirshipScheduleMenuActionPayload(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readVarInt()
        );
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

    public static void handle(AirshipScheduleMenuActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AirshipScheduleMenu menu)) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Schedule menu action rejected currentMenu={} player={} action={}",
                    player.containerMenu == null ? "<null>" : player.containerMenu.getClass().getSimpleName(),
                    player.getName().getString(),
                    payload.actionId()
            );
            return;
        }
        if (menu.containerId != payload.containerId()
                || menu.originTransponderPos().filter(payload.transponderPos()::equals).isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Schedule menu action rejected mismatch menuId={} payloadId={} menuPos={} payloadPos={} player={} action={}",
                    menu.containerId,
                    payload.containerId(),
                    menu.originTransponderPos().map(BlockPos::toShortString).orElse("none"),
                    payload.transponderPos().toShortString(),
                    player.getName().getString(),
                    payload.actionId()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Schedule menu action forwarding player={} menuId={} pos={} action={}",
                player.getName().getString(),
                payload.containerId(),
                payload.transponderPos().toShortString(),
                payload.actionId()
        );
        menu.clickMenuButton(player, payload.actionId());
    }
}

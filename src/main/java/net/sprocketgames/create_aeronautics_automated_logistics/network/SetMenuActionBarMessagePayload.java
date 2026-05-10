package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.MenuActionBarClientState;

public record SetMenuActionBarMessagePayload(Component message, int durationTicks) implements CustomPacketPayload {
    public static final int DEFAULT_DURATION_TICKS = 60;
    public static final Type<SetMenuActionBarMessagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_menu_action_bar_message")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetMenuActionBarMessagePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetMenuActionBarMessagePayload::write, SetMenuActionBarMessagePayload::read);

    private static SetMenuActionBarMessagePayload read(RegistryFriendlyByteBuf buffer) {
        return new SetMenuActionBarMessagePayload(
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, message);
        buffer.writeVarInt(durationTicks);
    }

    public static void send(ServerPlayer player, Component message) {
        PacketDistributor.sendToPlayer(player, new SetMenuActionBarMessagePayload(message, DEFAULT_DURATION_TICKS));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetMenuActionBarMessagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MenuActionBarClientState.show(payload.message(), payload.durationTicks()));
    }
}

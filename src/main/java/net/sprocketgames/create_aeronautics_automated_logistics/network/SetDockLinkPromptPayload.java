package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.DockLinkPromptClientState;

public record SetDockLinkPromptPayload(boolean active, boolean shipPrompt) implements CustomPacketPayload {
    public static final Type<SetDockLinkPromptPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_dock_link_prompt")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetDockLinkPromptPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetDockLinkPromptPayload::write, SetDockLinkPromptPayload::read);

    private static SetDockLinkPromptPayload read(RegistryFriendlyByteBuf buffer) {
        return new SetDockLinkPromptPayload(buffer.readBoolean(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBoolean(shipPrompt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetDockLinkPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active()) {
                DockLinkPromptClientState.show(payload.shipPrompt());
            } else {
                DockLinkPromptClientState.clear();
            }
        });
    }
}

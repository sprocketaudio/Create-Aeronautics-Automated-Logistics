package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.CargoLinkPromptClientState;

public record SetCargoLinkPromptPayload(boolean active, boolean shipPrompt, BlockPos sourcePos) implements CustomPacketPayload {
    public static final Type<SetCargoLinkPromptPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_cargo_link_prompt")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetCargoLinkPromptPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetCargoLinkPromptPayload::write, SetCargoLinkPromptPayload::read);

    private static SetCargoLinkPromptPayload read(RegistryFriendlyByteBuf buffer) {
        return new SetCargoLinkPromptPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBoolean(shipPrompt);
        buffer.writeBlockPos(sourcePos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetCargoLinkPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) {
                return;
            }
            if (payload.active()) {
                CargoLinkPromptClientState.show(payload.shipPrompt(), payload.sourcePos());
            } else {
                CargoLinkPromptClientState.clear();
            }
        });
    }
}

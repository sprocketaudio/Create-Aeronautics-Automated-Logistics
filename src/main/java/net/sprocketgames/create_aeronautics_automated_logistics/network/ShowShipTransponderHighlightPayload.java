package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.ShipTransponderHighlightClientState;

public record ShowShipTransponderHighlightPayload(BlockPos transponderPos, int durationTicks) implements CustomPacketPayload {
    public static final Type<ShowShipTransponderHighlightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "show_ship_transponder_highlight")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowShipTransponderHighlightPayload> STREAM_CODEC =
            StreamCodec.ofMember(ShowShipTransponderHighlightPayload::write, ShowShipTransponderHighlightPayload::read);

    private static ShowShipTransponderHighlightPayload read(RegistryFriendlyByteBuf buffer) {
        return new ShowShipTransponderHighlightPayload(buffer.readBlockPos().immutable(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeVarInt(durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowShipTransponderHighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) {
                return;
            }
            ShipTransponderHighlightClientState.show(payload.transponderPos(), payload.durationTicks());
        });
    }
}

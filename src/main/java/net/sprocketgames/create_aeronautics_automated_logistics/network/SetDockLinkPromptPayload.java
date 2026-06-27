package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.DockLinkPromptClientState;

import java.util.ArrayList;
import java.util.List;

public record SetDockLinkPromptPayload(boolean active, boolean shipPrompt, BlockPos sourcePos, List<BlockPos> candidatePositions) implements CustomPacketPayload {
    public static final Type<SetDockLinkPromptPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_dock_link_prompt")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetDockLinkPromptPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetDockLinkPromptPayload::write, SetDockLinkPromptPayload::read);

    private static SetDockLinkPromptPayload read(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        boolean shipPrompt = buffer.readBoolean();
        BlockPos sourcePos = buffer.readBlockPos();
        int count = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_LINK_PROMPT_POSITIONS, "dock link prompt positions");
        List<BlockPos> candidatePositions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            candidatePositions.add(buffer.readBlockPos().immutable());
        }
        return new SetDockLinkPromptPayload(active, shipPrompt, sourcePos, List.copyOf(candidatePositions));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBoolean(shipPrompt);
        buffer.writeBlockPos(sourcePos);
        buffer.writeVarInt(candidatePositions.size());
        for (BlockPos pos : candidatePositions) {
            buffer.writeBlockPos(pos);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetDockLinkPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active()) {
                DockLinkPromptClientState.show(payload.shipPrompt(), payload.sourcePos(), payload.candidatePositions());
            } else {
                DockLinkPromptClientState.clear();
            }
        });
    }
}

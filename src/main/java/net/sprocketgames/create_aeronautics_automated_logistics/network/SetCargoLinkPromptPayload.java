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

import java.util.ArrayList;
import java.util.List;

public record SetCargoLinkPromptPayload(boolean active, boolean shipPrompt, BlockPos sourcePos, List<List<BlockPos>> candidateGroups) implements CustomPacketPayload {
    public static final Type<SetCargoLinkPromptPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_cargo_link_prompt")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetCargoLinkPromptPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetCargoLinkPromptPayload::write, SetCargoLinkPromptPayload::read);

    private static SetCargoLinkPromptPayload read(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        boolean shipPrompt = buffer.readBoolean();
        BlockPos sourcePos = buffer.readBlockPos();
        int groupCount = buffer.readVarInt();
        List<List<BlockPos>> candidateGroups = new ArrayList<>(groupCount);
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int groupSize = buffer.readVarInt();
            List<BlockPos> group = new ArrayList<>(groupSize);
            for (int i = 0; i < groupSize; i++) {
                group.add(buffer.readBlockPos().immutable());
            }
            candidateGroups.add(List.copyOf(group));
        }
        return new SetCargoLinkPromptPayload(active, shipPrompt, sourcePos, List.copyOf(candidateGroups));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBoolean(shipPrompt);
        buffer.writeBlockPos(sourcePos);
        buffer.writeVarInt(candidateGroups.size());
        for (List<BlockPos> group : candidateGroups) {
            buffer.writeVarInt(group.size());
            for (BlockPos pos : group) {
                buffer.writeBlockPos(pos);
            }
        }
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
                CargoLinkPromptClientState.show(payload.shipPrompt(), payload.sourcePos(), payload.candidateGroups());
            } else {
                CargoLinkPromptClientState.clear();
            }
        });
    }
}

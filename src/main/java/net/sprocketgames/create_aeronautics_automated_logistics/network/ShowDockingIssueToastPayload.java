package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.DockingIssueToastClientState;

public record ShowDockingIssueToastPayload() implements CustomPacketPayload {
    public static final ShowDockingIssueToastPayload INSTANCE = new ShowDockingIssueToastPayload();
    public static final Type<ShowDockingIssueToastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "show_docking_issue_toast")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowDockingIssueToastPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowDockingIssueToastPayload payload, IPayloadContext context) {
        context.enqueueWork(DockingIssueToastClientState::show);
    }
}

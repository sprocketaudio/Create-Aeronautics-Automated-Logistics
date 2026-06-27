package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public record ShowDockingIssueToastPayload() implements CustomPacketPayload {
    public static final ShowDockingIssueToastPayload INSTANCE = new ShowDockingIssueToastPayload();
    public static final Type<ShowDockingIssueToastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "show_docking_issue_toast")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowDockingIssueToastPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    private static final SystemToast.SystemToastId DOCKING_ISSUE = new SystemToast.SystemToastId();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowDockingIssueToastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SystemToast.addOrUpdate(
                Minecraft.getInstance().getToasts(),
                DOCKING_ISSUE,
                Component.translatable("toast.create_aeronautics_automated_logistics.docking_issue.title")
                        .withStyle(ChatFormatting.RED),
                Component.translatable("toast.create_aeronautics_automated_logistics.docking_issue.message")
        ));
    }
}

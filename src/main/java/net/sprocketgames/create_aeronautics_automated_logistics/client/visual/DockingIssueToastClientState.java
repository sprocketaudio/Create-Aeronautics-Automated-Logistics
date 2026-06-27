package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class DockingIssueToastClientState {
    private static final SystemToast.SystemToastId DOCKING_ISSUE = new SystemToast.SystemToastId();

    private DockingIssueToastClientState() {
    }

    public static void show() {
        SystemToast.addOrUpdate(
                Minecraft.getInstance().getToasts(),
                DOCKING_ISSUE,
                Component.translatable("toast.create_aeronautics_automated_logistics.docking_issue.title")
                        .withStyle(ChatFormatting.RED),
                Component.translatable("toast.create_aeronautics_automated_logistics.docking_issue.message")
        );
    }
}

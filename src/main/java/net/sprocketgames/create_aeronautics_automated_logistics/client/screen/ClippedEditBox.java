package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ClippedEditBox extends EditBox {
    public ClippedEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.enableScissor(getX() - 2, getY(), getX() + width + 2, getY() + height);
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.disableScissor();
    }
}

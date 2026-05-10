package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.createmod.catnip.gui.element.ScreenElement;

public class MiniIconButton extends IconButton {
    private static final float SCALE = 2.0F / 3.0F;

    public MiniIconButton(int x, int y, ScreenElement icon) {
        super(x, y, 12, 12, icon);
    }

    @Override
    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;

        AllGuiTextures button = !active ? AllGuiTextures.BUTTON_DISABLED
                : isHovered && AllKeys.isMouseButtonDown(0) ? AllGuiTextures.BUTTON_DOWN
                : isHovered ? AllGuiTextures.BUTTON_HOVER
                : green ? AllGuiTextures.BUTTON_GREEN : AllGuiTextures.BUTTON;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(getX(), getY(), 0);
        graphics.pose().scale(SCALE, SCALE, 1.0F);
        graphics.blit(button.location, 0, 0, button.getStartX(), button.getStartY(), button.getWidth(), button.getHeight());
        icon.render(graphics, 1, 1);
        graphics.pose().popPose();
    }
}

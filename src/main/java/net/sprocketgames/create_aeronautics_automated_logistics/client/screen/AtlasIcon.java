package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class AtlasIcon implements ScreenElement {
    private final ResourceLocation atlas;
    private final int u;
    private final int v;
    private final int width;
    private final int height;
    private final int atlasSize;
    private final float renderScale;
    private final int alignCell;

    public AtlasIcon(ResourceLocation atlas, int u, int v, int width, int height, int atlasSize) {
        this(atlas, u, v, width, height, atlasSize, 1.0F, width);
    }

    public AtlasIcon(ResourceLocation atlas, int u, int v, int width, int height, int atlasSize, float renderScale, int alignCell) {
        this.atlas = atlas;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.atlasSize = atlasSize;
        this.renderScale = renderScale;
        this.alignCell = alignCell;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        if (renderScale == 1.0F) {
            graphics.blit(atlas, x, y, 0, u, v, width, height, atlasSize, atlasSize);
            return;
        }
        int scaledW = Math.max(1, Math.round(width * renderScale));
        int scaledH = Math.max(1, Math.round(height * renderScale));
        int drawX = x + Math.max(0, (alignCell - scaledW) / 2);
        int drawY = y + Math.max(0, (alignCell - scaledH) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(renderScale, renderScale, 1.0F);
        graphics.blit(atlas, 0, 0, 0, u, v, width, height, atlasSize, atlasSize);
        graphics.pose().popPose();
    }
}

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
    private final float renderOffsetX;
    private final float renderOffsetY;

    public AtlasIcon(ResourceLocation atlas, int u, int v, int width, int height, int atlasSize) {
        this(atlas, u, v, width, height, atlasSize, 1.0F, width, 0.0F, 0.0F);
    }

    public AtlasIcon(ResourceLocation atlas, int u, int v, int width, int height, int atlasSize, float renderScale, int alignCell) {
        this(atlas, u, v, width, height, atlasSize, renderScale, alignCell, 0.0F, 0.0F);
    }

    public AtlasIcon(
            ResourceLocation atlas,
            int u,
            int v,
            int width,
            int height,
            int atlasSize,
            float renderScale,
            int alignCell,
            float renderOffsetX,
            float renderOffsetY
    ) {
        this.atlas = atlas;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.atlasSize = atlasSize;
        this.renderScale = renderScale;
        this.alignCell = alignCell;
        this.renderOffsetX = renderOffsetX;
        this.renderOffsetY = renderOffsetY;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        int drawX = x + Math.max(0, (alignCell - width) / 2);
        int drawY = y + Math.max(0, (alignCell - height) / 2);
        if (renderScale == 1.0F) {
            graphics.pose().pushPose();
            graphics.pose().translate(renderOffsetX, renderOffsetY, 0.0F);
            graphics.blit(atlas, drawX, drawY, 0, u, v, width, height, atlasSize, atlasSize);
            graphics.pose().popPose();
            return;
        }
        int scaledW = Math.max(1, Math.round(width * renderScale));
        int scaledH = Math.max(1, Math.round(height * renderScale));
        drawX = x + Math.max(0, (alignCell - scaledW) / 2);
        drawY = y + Math.max(0, (alignCell - scaledH) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(drawX + renderOffsetX, drawY + renderOffsetY, 0.0F);
        graphics.pose().scale(renderScale, renderScale, 1.0F);
        graphics.blit(atlas, 0, 0, 0, u, v, width, height, atlasSize, atlasSize);
        graphics.pose().popPose();
    }
}

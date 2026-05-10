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

    public AtlasIcon(ResourceLocation atlas, int u, int v, int width, int height, int atlasSize) {
        this.atlas = atlas;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.atlasSize = atlasSize;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(atlas, x, y, 0, u, v, width, height, atlasSize, atlasSize);
    }
}

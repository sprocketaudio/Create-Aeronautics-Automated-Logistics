package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class ListDropdown {
    private static final int DEFAULT_ROW_HEIGHT = 12;
    private static final int DEFAULT_FADE_HEIGHT = 5;
    private static final int DEFAULT_SCROLLBAR_WIDTH = 6;

    private final Font font;
    private final int width;
    private final int rowHeight;
    private final int maxVisibleRows;
    private final int scrollbarWidth;
    private final int fadeHeight;

    public ListDropdown(Font font, int width, int maxVisibleRows) {
        this(font, width, DEFAULT_ROW_HEIGHT, maxVisibleRows, DEFAULT_SCROLLBAR_WIDTH, DEFAULT_FADE_HEIGHT);
    }

    public ListDropdown(Font font, int width, int rowHeight, int maxVisibleRows, int scrollbarWidth, int fadeHeight) {
        this.font = Objects.requireNonNull(font, "font");
        this.width = width;
        this.rowHeight = rowHeight;
        this.maxVisibleRows = maxVisibleRows;
        this.scrollbarWidth = scrollbarWidth;
        this.fadeHeight = fadeHeight;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y, List<String> options, int selectedIndex, int scrollOffset) {
        int visible = Math.min(maxVisibleRows, options.size());
        int height = Math.max(1, visible) * rowHeight + 4;
        boolean scrollable = options.size() > visible;
        int contentRight = x + width - (scrollable ? scrollbarWidth + 4 : 4);
        guiGraphics.fill(x, y, x + width, y + height, 0xF018101C);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xF02B2130);

        int maxScroll = Math.max(0, options.size() - visible);
        int clampedScroll = Mth.clamp(scrollOffset, 0, maxScroll);
        for (int i = 0; i < visible; i++) {
            int optionIndex = clampedScroll + i;
            if (optionIndex >= options.size()) {
                break;
            }
            int rowY = y + 4 + i * rowHeight;
            boolean hovered = contains(mouseX, mouseY, x + 2, rowY - 1, contentRight - x - 2, rowHeight);
            int highlightTop = rowY - 1;
            int highlightBottom = rowY + font.lineHeight + 1;
            if (hovered) {
                guiGraphics.fill(x + 4, highlightTop, contentRight, highlightBottom, 0x7A525963);
            }
            if (optionIndex == selectedIndex) {
                int outlineColor = hovered ? 0xFFE8EEF6 : 0xB8B9C4D0;
                int outlineLeft = x + 3;
                int outlineRight = contentRight;
                int outlineTop = highlightTop - 1;
                int outlineBottom = highlightBottom - 1;
                guiGraphics.hLine(outlineLeft, outlineRight, outlineTop, outlineColor);
                guiGraphics.hLine(outlineLeft, outlineRight, outlineBottom, outlineColor);
                guiGraphics.vLine(outlineLeft, outlineTop, outlineBottom, outlineColor);
                guiGraphics.vLine(outlineRight, outlineTop, outlineBottom, outlineColor);
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(options.get(optionIndex), contentRight - (x + 5) - 2), x + 5, rowY, 0xFFD8DDE6, false);
        }

        if (clampedScroll > 0) {
            guiGraphics.fillGradient(x + 1, y + 1, x + width - 1, y + 1 + fadeHeight, 0xF0333840, 0x00333943);
        }
        if (clampedScroll < maxScroll) {
            guiGraphics.fillGradient(x + 1, y + height - 1 - fadeHeight, x + width - 1, y + height - 1, 0x00333943, 0xF0333840);
        }
        if (scrollable) {
            int trackLeft = x + width - scrollbarWidth - 2;
            int trackRight = x + width - 2;
            int trackTop = y + 3;
            int trackBottom = y + height - 3;
            int trackHeight = trackBottom - trackTop;
            guiGraphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0xAA20242A);

            int knobHeight = Math.max(12, Math.round((visible / (float) options.size()) * trackHeight));
            int knobTravel = Math.max(0, trackHeight - knobHeight);
            int knobTop = trackTop + (maxScroll <= 0 ? 0 : Math.round((clampedScroll / (float) maxScroll) * knobTravel));
            int knobBottom = knobTop + knobHeight;
            guiGraphics.fill(trackLeft, knobTop + 1, trackRight, knobBottom - 1, 0xFF7A818C);
            guiGraphics.hLine(trackLeft + 1, trackRight - 1, knobTop, 0xFFB9C4D0);
            guiGraphics.hLine(trackLeft + 1, trackRight - 1, knobBottom - 1, 0xFF535A64);
            guiGraphics.vLine(trackLeft, knobTop + 1, knobBottom - 2, 0xFF929AA6);
            guiGraphics.vLine(trackRight - 1, knobTop + 1, knobBottom - 2, 0xFF535A64);
        }
    }

    public int scrolled(int current, int optionCount, double scrollY) {
        int maxScroll = Math.max(0, optionCount - Math.min(maxVisibleRows, optionCount));
        return Mth.clamp(current + (scrollY < 0 ? 1 : -1), 0, maxScroll);
    }

    public int clickedIndex(double mouseX, double mouseY, int x, int y, List<String> options, int scrollOffset) {
        int visible = Math.min(maxVisibleRows, options.size());
        int height = Math.max(1, visible) * rowHeight + 4;
        if (!contains(mouseX, mouseY, x, y, width, height)) {
            return -1;
        }
        int row = ((int) mouseY - (y + 4)) / rowHeight;
        if (row < 0 || row >= visible) {
            return -1;
        }
        int index = Mth.clamp(scrollOffset, 0, Math.max(0, options.size() - visible)) + row;
        return index >= 0 && index < options.size() ? index : -1;
    }

    public boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import org.lwjgl.glfw.GLFW;

public class ShipTransponderScreen extends AbstractContainerScreen<ShipTransponderMenu> {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 178;
    private static final int INVENTORY_X = 9;
    private static final int INVENTORY_Y = 180;
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/transponder.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");
    private static final ScreenElement SHOW_ROUTE_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 176, 16, 16, 16, 256);
    private static final ScreenElement SHOW_DOCK_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 64, 192, 16, 16, 256);

    private final List<ButtonTooltip> buttonTooltips = new ArrayList<>();
    private EditBox nameBox;
    private IconButton previewButton;
    private IconButton dockPreviewButton;
    private MiniIconButton dockLinkButton;
    private MiniIconButton dockClearButton;
    private int statusValueX;
    private int statusValueY;
    private int statusValueWidth;
    private int statusValueHeight;
    private int dockValueX;
    private int dockValueY;
    private int dockValueWidth;
    private int dockValueHeight;

    public ShipTransponderScreen(ShipTransponderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT + 108;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        buttonTooltips.clear();

        nameBox = new EditBox(new NoShadowFontWrapper(this.font), this.leftPos + 24, this.topPos + 4, PANEL_WIDTH - 48, 10, Component.empty());
        nameBox.setBordered(false);
        nameBox.setMaxLength(64);
        nameBox.setTextColor(0xE8EDF6);
        if (this.minecraft != null && this.minecraft.player != null) {
            nameBox.setValue(this.menu.shipName(this.minecraft.player));
        }
        nameBox.setResponder(ignored -> centerNameBox());
        nameBox.setFocused(false);
        centerNameBox();
        addRenderableWidget(nameBox);

        int buttonY = this.topPos + 63;
        addIconButton(
                this.leftPos + 83,
                buttonY,
                AllIcons.I_PLAY,
                () -> pressAction(ShipTransponderMenu.ACTION_START_INSTALLED_SCHEDULE),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.start_schedule")
        );
        addIconButton(
                this.leftPos + 109,
                buttonY,
                AllIcons.I_STOP,
                () -> pressAction(ShipTransponderMenu.ACTION_STOP_SCHEDULE),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.stop_schedule"),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_1"),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_2")
        );
        previewButton = addIconButton(
                this.leftPos + 135,
                buttonY,
                SHOW_ROUTE_ICON,
                this::togglePreview,
                routePreviewButtonText()
        );
        dockPreviewButton = addIconButton(
                this.leftPos + 17,
                this.topPos + 154,
                SHOW_DOCK_ICON,
                this::toggleDockPreview,
                dockPreviewTooltip()
        );
        addIconButton(
                this.leftPos + 167,
                this.topPos + 154,
                AllIcons.I_CONFIRM,
                this::onClose,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.close.tooltip")
        );
        dockLinkButton = addMiniIconButton(
                this.leftPos + 140,
                this.topPos + 119,
                AllIcons.I_ADD,
                () -> pressAction(ShipTransponderMenu.ACTION_BEGIN_LINK_DOCK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.link")
        );
        dockClearButton = addMiniIconButton(
                this.leftPos + 154,
                this.topPos + 119,
                AllIcons.I_MTD_CLOSE,
                () -> pressAction(ShipTransponderMenu.ACTION_CLEAR_DOCK_LINK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.clear")
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setCursorPosition(nameBox.getValue().length());
            nameBox.setHighlightPos(nameBox.getCursorPosition());
        }
        centerNameBox();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
        guiGraphics.blit(PLAYER_INVENTORY, this.leftPos + INVENTORY_X, this.topPos + INVENTORY_Y, 0, 0, 176, 108);
        if (this.minecraft != null && this.minecraft.player != null && this.menu.isScheduleSlotLocked(this.minecraft.player)) {
            int slotX = this.leftPos + 43;
            int slotY = this.topPos + 63;
            guiGraphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0x7A2B2323);
            guiGraphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0x55331212);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        if (previewButton != null) {
            previewButton.setToolTip(routePreviewButtonText());
            previewButton.green = LogisticsClientOverlays.hasFlightPath();
        }
        if (dockPreviewButton != null && this.minecraft != null && this.minecraft.player != null) {
            dockPreviewButton.setToolTip(dockPreviewTooltip());
            dockPreviewButton.active = menu.dockPreviewPos(this.minecraft.player).isPresent();
            dockPreviewButton.green = menu.dockPreviewPos(this.minecraft.player)
                    .map(LogisticsClientOverlays::isDockVisible)
                    .orElse(false);
        }
        if (dockClearButton != null && this.minecraft != null && this.minecraft.player != null) {
            dockClearButton.active = menu.dockPreviewPos(this.minecraft.player).isPresent();
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHeaderEditIcon(guiGraphics);
        renderSubtitle(guiGraphics);
        renderMainPanel(guiGraphics);
        renderHoveredTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle, INVENTORY_X + 8, INVENTORY_Y + 6, 0x505050, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && nameBox != null && !nameBox.isFocused()
                && mouseY > this.topPos && mouseY < this.topPos + 18
                && mouseX > this.leftPos && mouseX < this.leftPos + PANEL_WIDTH) {
            nameBox.setFocused(true);
            nameBox.setHighlightPos(0);
            setFocused(nameBox);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (nameBox != null && nameBox.isFocused()) {
                nameBox.setFocused(false);
                saveName();
                return true;
            }
        }
        if (this.nameBox != null && this.nameBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return this.nameBox.keyPressed(keyCode, scanCode, modifiers) || this.nameBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.nameBox != null && this.nameBox.isFocused() && this.nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        saveName();
        super.removed();
    }

    private IconButton addIconButton(int x, int y, ScreenElement icon, Runnable callback, Component... tooltip) {
        IconButton button = new IconButton(x, y, icon);
        button.withCallback(callback);
        addRenderableWidget(button);
        buttonTooltips.add(new ButtonTooltip(button, List.of(tooltip)));
        return button;
    }

    private MiniIconButton addMiniIconButton(int x, int y, AllIcons icon, Runnable callback, Component... tooltip) {
        MiniIconButton button = new MiniIconButton(x, y, icon);
        button.withCallback(callback);
        addRenderableWidget(button);
        buttonTooltips.add(new ButtonTooltip(button, List.of(tooltip)));
        return button;
    }

    private void renderHeaderEditIcon(GuiGraphics guiGraphics) {
        if (nameBox == null || nameBox.isFocused()) {
            return;
        }
        int iconX = Math.min(nameBox.getX() + this.font.width(nameBox.getValue()) + 5, this.leftPos + PANEL_WIDTH - 24);
        guiGraphics.blit(BACKGROUND, iconX, this.topPos + 1, 0, 239, 15, 14, 256, 256);
    }

    private void renderSubtitle(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.subtitle"),
                this.leftPos + PANEL_WIDTH / 2,
                this.topPos + 21,
                0xFFE7D7B3
        );
    }

    private void renderMainPanel(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        int x = this.leftPos + 44;
        guiGraphics.drawString(this.font, "Schedule:", x, this.topPos + 49, 0xFF9EA5AA, false);

        Component status = menu.runtimeStateText(this.minecraft.player);
        int statusY = this.topPos + 95;
        guiGraphics.drawString(this.font, "Status:", x, statusY, 0xFF9EA5AA, false);
        String statusText = shortText(status, 154 - this.font.width("Status: ") - 2);
        statusValueX = x + this.font.width("Status: ") + 2;
        statusValueY = statusY;
        statusValueWidth = this.font.width(statusText);
        statusValueHeight = this.font.lineHeight;
        guiGraphics.drawString(this.font, statusText, statusValueX, statusY, menu.runtimeStateColor(this.minecraft.player), false);

        Component dock = menu.dockCompactText(this.minecraft.player);
        int dockY = this.topPos + 120;
        guiGraphics.drawString(this.font, "Dock:", x, dockY, 0xFF9EA5AA, false);
        String dockText = shortText(dock, 154 - this.font.width("Dock: ") - 2);
        dockValueX = x + this.font.width("Dock: ") + 2;
        dockValueY = dockY;
        dockValueWidth = this.font.width(dockText);
        dockValueHeight = this.font.lineHeight;
        guiGraphics.drawString(this.font, dockText, dockValueX, dockY, menu.dockStatusColor(this.minecraft.player), false);
    }

    private void renderHoveredTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTooltip(guiGraphics, mouseX, mouseY);
        if (this.minecraft != null && this.minecraft.player != null) {
            var statusTooltip = menu.runtimeStatusTooltip(this.minecraft.player);
            if (!statusTooltip.isEmpty()
                    && isInside(mouseX, mouseY, statusValueX, statusValueY, Math.max(1, statusValueWidth), Math.max(1, statusValueHeight))) {
                guiGraphics.renderTooltip(this.font, statusTooltip, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
            if (isInside(mouseX, mouseY, dockValueX, dockValueY, Math.max(1, dockValueWidth), Math.max(1, dockValueHeight))) {
                guiGraphics.renderTooltip(this.font, menu.dockTooltip(this.minecraft.player), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        for (ButtonTooltip tooltip : buttonTooltips) {
            if (tooltip.button().isHovered() && !tooltip.lines().isEmpty()) {
                guiGraphics.renderTooltip(this.font, tooltip.lines(), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    private void saveName() {
        if (this.minecraft != null && this.nameBox != null) {
            PacketDistributor.sendToServer(new UpdateIdentityNamePayload(this.menu.transponderPos(), this.nameBox.getValue()));
        }
    }

    private void pressAction(int actionId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, actionId);
        }
    }

    private void togglePreview() {
        if (LogisticsClientOverlays.hasFlightPath()) {
            LogisticsClientOverlays.clearFlightPath();
            if (previewButton != null) {
                previewButton.setToolTip(routePreviewButtonText());
            }
            return;
        }
        pressAction(ShipTransponderMenu.ACTION_TOGGLE_PREVIEW);
        if (previewButton != null) {
            previewButton.setToolTip(routePreviewButtonText());
        }
    }

    private void toggleDockPreview() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        menu.dockPreviewPos(this.minecraft.player).ifPresentOrElse(
                LogisticsClientOverlays::toggleDock,
                LogisticsClientOverlays::clearDock
        );
        if (dockPreviewButton != null) {
            dockPreviewButton.setToolTip(dockPreviewTooltip());
        }
    }

    private Component routePreviewButtonText() {
        return Component.translatable(
                LogisticsClientOverlays.hasFlightPath()
                        ? "gui.create_aeronautics_automated_logistics.ship_transponder.hide_flight_path"
                        : "gui.create_aeronautics_automated_logistics.ship_transponder.show_flight_path"
        );
    }

    private Component dockPreviewTooltip() {
        boolean visible = this.minecraft != null
                && this.minecraft.player != null
                && menu.dockPreviewPos(this.minecraft.player).map(LogisticsClientOverlays::isDockVisible).orElse(false);
        return Component.translatable(
                visible
                        ? "gui.create_aeronautics_automated_logistics.dock.hide"
                        : "gui.create_aeronautics_automated_logistics.dock.show"
        );
    }

    private void centerNameBox() {
        if (nameBox == null) {
            return;
        }
        String value = nameBox.getValue();
        int width = Math.min(this.font.width(value), nameBox.getWidth());
        nameBox.setX(this.leftPos + PANEL_WIDTH / 2 - (width + 10) / 2);
        nameBox.setY(this.topPos + 4);
    }

    private String shortText(Component component, int width) {
        return this.font.plainSubstrByWidth(component.getString(), width);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record ButtonTooltip(IconButton button, List<Component> lines) {
    }
}

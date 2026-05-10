package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import org.lwjgl.glfw.GLFW;

public class AirshipStationScreen extends AbstractContainerScreen<AirshipStationMenu> {
    private static final int PANEL_U = 0;
    private static final int PANEL_V = 3;
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 235;
    private static final int INNER_X = 13;
    private static final int INNER_WIDTH = 174;
    private static final int SHIP_BOX_X = 37;
    private static final int SHIP_BOX_Y = 65;
    private static final int SHIP_BOX_WIDTH = 120;
    private static final int SHIP_BOX_HEIGHT = 20;
    private static final int ROUTES_VISIBLE_ROWS = 3;
    private static final int ROUTES_POPUP_X = 8;
    private static final int ROUTES_POPUP_Y = 42;
    private static final int ROUTES_POPUP_W = 176;
    private static final int ROUTES_POPUP_H = 159;
    private static final int ROUTES_ROW_Y = ROUTES_POPUP_Y + 25;
    private static final int ROUTES_ROW_H = 35;
    private static final int ROUTES_ROW_FILL_H = 33;
    private static final int ROUTES_ROW_TEXT_1_Y = 2;
    private static final int ROUTES_ROW_TEXT_2_Y = 12;
    private static final int ROUTES_ROW_TEXT_3_Y = 22;
    private static final int ROUTES_FOOTER_Y = ROUTES_POPUP_H - 10;
    private static final int ROUTES_FADE_HEIGHT = 16;
    private static final DateTimeFormatter ROUTE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/airship_station.png");
    private static final ScreenElement SHOW_DOCK_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 64, 192, 16, 16, 256);

    private final List<ButtonTooltip> buttonTooltips = new ArrayList<>();
    private EditBox nameBox;
    private IconButton recordButton;
    private IconButton finishRecordingButton;
    private IconButton landingAreaButton;
    private IconButton routesButton;
    private IconButton dockPreviewButton;
    private MiniIconButton dockLinkButton;
    private MiniIconButton dockClearButton;
    private boolean shipDropdownOpen;
    private boolean routesOpen;
    private int routeScroll;
    private UUID previewedRouteId;
    private Integer pendingDeleteRouteIndex;
    private Integer hoveredRouteIndex;
    private int statusValueX;
    private int statusValueY;
    private int statusValueWidth;
    private int statusValueHeight;
    private int dockValueX;
    private int dockValueY;
    private int dockValueWidth;
    private int dockValueHeight;
    private List<Component> statusTooltipLines = List.of();

    public AirshipStationScreen(AirshipStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        buttonTooltips.clear();
        int x = this.leftPos;
        int y = this.topPos;

        nameBox = new EditBox(new NoShadowFontWrapper(this.font), x + 20, y + 4, PANEL_WIDTH - 40, 10, Component.empty());
        nameBox.setBordered(false);
        nameBox.setMaxLength(64);
        nameBox.setTextColor(0x592424);
        nameBox.setValue(stationName());
        nameBox.setResponder(ignored -> centerNameBox());
        nameBox.setFocused(false);
        centerNameBox();
        addRenderableWidget(nameBox);

        int controlsY = y + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 8;
        int controlsStartX = x + (PANEL_WIDTH - (18 * 4 + 8 * 3)) / 2;
        recordButton = addIconButton(
                controlsStartX,
                controlsY,
                AllIcons.I_ADD,
                () -> pressAction(AirshipStationMenu.ACTION_RECORD_OR_FINISH_SEGMENT),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.start_or_save_segment.tooltip")
        );
        finishRecordingButton = addIconButton(
                controlsStartX + 26,
                controlsY,
                AllIcons.I_CONFIRM,
                () -> pressAction(AirshipStationMenu.ACTION_FINISH_RECORDING),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.finish_recording.tooltip")
        );
        addIconButton(
                controlsStartX + 52,
                controlsY,
                AllIcons.I_PLAY,
                () -> pressAction(AirshipStationMenu.ACTION_RUN_SCHEDULE),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.run_schedule.tooltip")
        );
        addIconButton(
                controlsStartX + 78,
                controlsY,
                AllIcons.I_STOP,
                () -> pressAction(AirshipStationMenu.ACTION_STOP_SCHEDULE),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_1"),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_2")
        );

        int bottomButtonsY = y + PANEL_HEIGHT - 24;
        routesButton = addIconButton(
                x + 16,
                bottomButtonsY,
                AllIcons.I_VIEW_SCHEDULE,
                () -> {
                    routesOpen = !routesOpen;
                    shipDropdownOpen = false;
                    if (!routesOpen) {
                        pendingDeleteRouteIndex = null;
                    }
                },
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.routes.tooltip")
        );
        landingAreaButton = addIconButton(
                x + 36,
                bottomButtonsY,
                AllIcons.I_TARGET,
                this::toggleLandingArea,
                landingAreaTooltip()
        );
        dockPreviewButton = addIconButton(
                x + 56,
                bottomButtonsY,
                SHOW_DOCK_ICON,
                this::toggleDockPreview,
                dockPreviewTooltip()
        );
        addIconButton(
                x + PANEL_WIDTH - 33,
                y + PANEL_HEIGHT - 24,
                AllIcons.I_CONFIRM,
                this::onClose,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.close.tooltip")
        );
        int dockButtonsY = y + 182;
        dockLinkButton = addMiniIconButton(
                x + 141,
                dockButtonsY,
                AllIcons.I_ADD,
                () -> pressAction(AirshipStationMenu.ACTION_BEGIN_LINK_DOCK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.link")
        );
        dockClearButton = addMiniIconButton(
                x + 155,
                dockButtonsY,
                AllIcons.I_MTD_CLOSE,
                () -> pressAction(AirshipStationMenu.ACTION_CLEAR_DOCK_LINK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.clear")
        );

        if (this.minecraft != null && this.minecraft.player != null) {
            pressAction(AirshipStationMenu.ACTION_AUTO_SELECT_CLOSEST_SHIP);
        }
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
        guiGraphics.blit(
                BACKGROUND,
                this.leftPos,
                this.topPos,
                PANEL_U,
                PANEL_V,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                256,
                256
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        if (landingAreaButton != null) {
            landingAreaButton.setToolTip(landingAreaTooltip());
            landingAreaButton.green = LogisticsClientOverlays.isLandingAreaVisible(this.menu.stationPos());
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
        boolean recording = false;
        if (this.minecraft != null && this.minecraft.player != null) {
            recording = menu.isRecording(this.minecraft.player);
        }
        if (recordButton != null) {
            recordButton.setIcon(recording ? AllIcons.I_CONFIG_SAVE : AllIcons.I_ADD);
            recordButton.green = recording;
            if (finishRecordingButton != null) {
                finishRecordingButton.active = recording;
                finishRecordingButton.green = false;
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHeaderEditIcon(guiGraphics);
        int headerColor = 0xFFE7D7B3;
        String headerText = "Route recording & playback";
        if (recording) {
            headerText = null;
            int ticks = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.tickCount : 0;
            float pulse = (float) ((Math.sin((ticks + partialTick) * 0.25F) + 1.0F) * 0.5F);
            int bright = 0xD0 + (int) (0x2F * pulse);
            int mid = 0xA8 + (int) (0x35 * pulse);
            headerColor = 0xFF000000 | (bright << 16) | (mid << 8) | 0x4E;
        }
        int headerY = this.topPos + 21;
        if (headerText != null) {
            guiGraphics.drawCenteredString(this.font, headerText, this.leftPos + PANEL_WIDTH / 2, headerY, headerColor);
        } else {
            String left = "Save segment with ";
            String middle = ", finish with ";
            String right = "";
            float iconScale = 2.0F / 3.0F;
            int iconWidth = Math.round(16 * iconScale);
            int totalWidth = this.font.width(left) + iconWidth + this.font.width(middle) + iconWidth + this.font.width(right);
            int startX = this.leftPos + (PANEL_WIDTH - totalWidth) / 2 - 3;
            int iconY = headerY - 2;
            guiGraphics.drawString(this.font, left, startX, headerY, headerColor, false);
            int x = startX + this.font.width(left);
            renderScaledIcon(guiGraphics, AllIcons.I_CONFIG_SAVE, x, iconY, iconScale);
            x += iconWidth;
            guiGraphics.drawString(this.font, middle, x, headerY, headerColor, false);
            x += this.font.width(middle);
            renderScaledIcon(guiGraphics, AllIcons.I_CONFIRM, x, iconY, iconScale);
            x += iconWidth;
            guiGraphics.drawString(this.font, right, x, headerY, headerColor, false);
        }
        if (recording && finishRecordingButton != null) {
            int bx = finishRecordingButton.getX();
            int by = finishRecordingButton.getY();
            int bw = finishRecordingButton.getWidth();
            int bh = finishRecordingButton.getHeight();
            int outline = 0xFF66CC66;
            guiGraphics.fill(bx - 1, by - 1, bx + bw + 1, by, outline);
            guiGraphics.fill(bx - 1, by + bh, bx + bw + 1, by + bh + 1, outline);
            guiGraphics.fill(bx - 1, by, bx, by + bh, outline);
            guiGraphics.fill(bx + bw, by, bx + bw + 1, by + bh, outline);
        }
        renderShipSelector(guiGraphics, mouseX, mouseY);
        renderMainStatus(guiGraphics);
        if (shipDropdownOpen) {
            renderShipDropdown(guiGraphics);
        }
        if (routesOpen) {
            renderRoutesPopup(guiGraphics, mouseX, mouseY);
        }
        renderHoveredTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
        AllGuiTextures.STATION_EDIT_NAME.render(guiGraphics, iconX, this.topPos + 1);
    }

    private void renderShipSelector(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos + SHIP_BOX_X;
        int y = this.topPos + SHIP_BOX_Y;
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.ship_label"),
                x,
                y - 12,
                0xFFB9C4D0,
                false
        );
        boolean hovered = isInside(mouseX, mouseY, x, y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT);
        guiGraphics.fill(x, y, x + SHIP_BOX_WIDTH, y + SHIP_BOX_HEIGHT, hovered ? 0xDD9C9C9C : 0xCC747474);
        guiGraphics.fill(x + 1, y + 1, x + SHIP_BOX_WIDTH - 1, y + SHIP_BOX_HEIGHT - 1, 0xEE242424);
        String ship = "-";
        String status = "";
        int statusColor = 0xFFD8DDE6;
        if (this.minecraft != null && this.minecraft.player != null) {
            var selected = menu.selectedShipChoice(this.minecraft.player);
            if (selected.isPresent()) {
                ship = selected.get().shipName().getString();
                status = selected.get().statusText().getString();
                statusColor = selected.get().statusColor();
            } else {
                ship = menu.selectedShipText(this.minecraft.player).getString();
            }
        }
        int statusWidth = status.isBlank() ? 0 : this.font.width(status) + 8;
        String clipped = this.font.plainSubstrByWidth(ship, SHIP_BOX_WIDTH - 12 - statusWidth);
        guiGraphics.drawString(this.font, clipped, x + 6, y + 6, 0xE5E8EE, false);
        if (!status.isBlank()) {
            guiGraphics.drawString(this.font, status, x + SHIP_BOX_WIDTH - this.font.width(status) - 6, y + 6, statusColor, false);
        }
    }

    private void renderShipDropdown(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        var options = this.menu.shipChoices(this.minecraft.player);
        int x = this.leftPos + SHIP_BOX_X;
        int y = this.topPos + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 2;
        int rowHeight = 13;
        int visible = Math.min(6, options.size());
        int height = Math.max(1, visible) * rowHeight + 4;
        guiGraphics.fill(x, y, x + SHIP_BOX_WIDTH, y + height, 0xF018101C);
        guiGraphics.fill(x + 1, y + 1, x + SHIP_BOX_WIDTH - 1, y + height - 1, 0xF02B2130);
        if (options.isEmpty()) {
            guiGraphics.drawString(this.font, "No ships found", x + 5, y + 5, 0xD8DDE6, false);
            return;
        }
        for (int i = 0; i < visible; i++) {
            AirshipStationMenu.ShipChoice option = options.get(i);
            int labelColor = option.selected() ? 0xFFFFE27A : 0xFFD8DDE6;
            String status = option.statusText().getString();
            int statusWidth = this.font.width(status) + 8;
            String clipped = this.font.plainSubstrByWidth(option.shipName().getString(), SHIP_BOX_WIDTH - 10 - statusWidth);
            int rowY = y + 4 + i * rowHeight;
            guiGraphics.drawString(this.font, clipped, x + 5, rowY, labelColor, false);
            guiGraphics.drawString(
                    this.font,
                    status,
                    x + SHIP_BOX_WIDTH - this.font.width(status) - 6,
                    rowY,
                    option.statusColor(),
                    false
            );
        }
    }

    private void renderMainStatus(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        statusTooltipLines = List.of();
        statusValueX = 0;
        statusValueY = 0;
        statusValueWidth = 0;
        statusValueHeight = 0;
        int x = this.leftPos + INNER_X + 24;
        int dividerY = this.topPos + 121;
        guiGraphics.hLine(this.leftPos + INNER_X + 11, this.leftPos + INNER_X + INNER_WIDTH - 21, dividerY, 0xFF515151);

        Component status = menu.panelStatusText(this.minecraft.player);
        String statusText = status.getString();
        Component statusLabel = Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_inline", status);
        int statusY = dividerY + 9;
        guiGraphics.drawString(this.font, "Status:", x, statusY, 0xFF9EA5AA, false);
        guiGraphics.drawString(
                this.font,
                shortText(status, 126 - this.font.width("Status: ") - 2),
                x + this.font.width("Status: ") + 2,
                statusY,
                statusColor(statusText),
                false
        );
        String statusValue = shortText(status, 126 - this.font.width("Status: ") - 2);
        statusValueX = x + this.font.width("Status: ") + 2;
        statusValueY = statusY;
        statusValueWidth = this.font.width(statusValue);
        statusValueHeight = this.font.lineHeight;

        int routesY = dividerY + 30;
        guiGraphics.drawString(this.font, "Routes:", x, routesY, 0xFF9EA5AA, false);
        int outCount = menu.routesFromHereCount(this.minecraft.player);
        int inCount = menu.routesToHereCount(this.minecraft.player);
        int valuesY = routesY + 11;
        guiGraphics.drawString(this.font, "Out:", x, valuesY, 0xFF9EA5AA, false);
        int outValueX = x + this.font.width("Out: ") + 1;
        guiGraphics.drawString(this.font, Integer.toString(outCount), outValueX, valuesY, 0xFFB9C4D0, false);
        int inLabelX = outValueX + this.font.width(Integer.toString(outCount)) + 14;
        guiGraphics.drawString(this.font, "In:", inLabelX, valuesY, 0xFF9EA5AA, false);
        int inValueX = inLabelX + this.font.width("In: ") + 1;
        guiGraphics.drawString(this.font, Integer.toString(inCount), inValueX, valuesY, 0xFFB9C4D0, false);

        int dockRowY = this.topPos + 181;
        guiGraphics.drawString(this.font, "Dock:", x, dockRowY + 3, 0xFF9EA5AA, false);
        String dockValue = shortText(menu.dockCompactText(this.minecraft.player), 92);
        dockValueX = x + this.font.width("Dock: ") + 2;
        dockValueY = dockRowY + 3;
        dockValueWidth = this.font.width(dockValue);
        dockValueHeight = this.font.lineHeight;
        guiGraphics.drawString(this.font, dockValue, dockValueX, dockValueY, 0xFFB9C4D0, false);

        statusTooltipLines = menu.statusTooltipLines(this.minecraft.player);
    }

    private void renderRoutesPopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        var routes = menu.routeChoices(this.minecraft.player);
        if (previewedRouteId == null) {
            previewedRouteId = LogisticsClientOverlays.previewedRouteId().orElse(null);
        }
        int x = this.leftPos + ROUTES_POPUP_X;
        int y = this.topPos + ROUTES_POPUP_Y;
        int width = ROUTES_POPUP_W;
        int height = ROUTES_POPUP_H;
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xF0101010);
        guiGraphics.fill(x, y, x + width, y + height, 0xF0333840);
        guiGraphics.drawCenteredString(this.font, "Routes", x + width / 2, y + 7, 0xFFE7C46E);
        guiGraphics.hLine(x + 12, x + width - 12, y + 21, 0xFF555A60);
        hoveredRouteIndex = null;

        if (routes.isEmpty()) {
            guiGraphics.drawWordWrap(
                    this.font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.no_routes_for_selected_ship"),
                    x + 21,
                    y + 56,
                    width - 42,
                    0xFFB9C4D0
            );
            return;
        }

        routeScroll = Math.max(0, Math.min(routeScroll, Math.max(0, routes.size() - ROUTES_VISIBLE_ROWS)));
        int rowY = y + (ROUTES_ROW_Y - ROUTES_POPUP_Y);
        int rowHeight = ROUTES_ROW_H;
        int maxScroll = Math.max(0, routes.size() - ROUTES_VISIBLE_ROWS);
        boolean hasAbove = routeScroll > 0;
        boolean hasBelow = routeScroll < maxScroll;
        int clipLeft = x + 5;
        int clipTop = rowY;
        int clipRight = x + width - 5;
        int clipBottom = y + ROUTES_FOOTER_Y - 2;
        int renderedRows = Math.min(routes.size() - routeScroll, ROUTES_VISIBLE_ROWS + (hasBelow ? 1 : 0));

        guiGraphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        for (int visibleIndex = 0; visibleIndex < renderedRows; visibleIndex++) {
            int routeIndex = routeScroll + visibleIndex;
            if (routeIndex >= routes.size()) {
                break;
            }
            RouteSegment route = routes.get(routeIndex);
            int ry = rowY + visibleIndex * rowHeight;
            int visibleBottom = Math.min(ry + ROUTES_ROW_FILL_H, clipBottom);
            boolean hovered = visibleBottom > ry && isInside(mouseX, mouseY, x + 5, ry, width - 10, visibleBottom - ry);
            if (hovered) {
                hoveredRouteIndex = routeIndex;
            }
            boolean previewed = previewedRouteId != null && previewedRouteId.equals(route.id().value());
            boolean invalid = routeInvalidReason(route, this.minecraft.player.level()).isPresent();
            int rowColor = invalid ? (hovered ? 0xAA704545 : 0x885A3A3A) : (hovered ? 0xAA555B65 : 0x88414850);
            guiGraphics.fill(x + 5, ry, x + width - 5, ry + ROUTES_ROW_FILL_H, rowColor);
            if (previewed) {
                guiGraphics.fill(x + 4, ry - 1, x + width - 4, ry, 0xFFFFE27A);
                guiGraphics.fill(x + 4, ry + ROUTES_ROW_FILL_H, x + width - 4, ry + ROUTES_ROW_FILL_H + 1, 0xFFFFE27A);
                guiGraphics.fill(x + 4, ry, x + 5, ry + ROUTES_ROW_FILL_H, 0xFFFFE27A);
                guiGraphics.fill(x + width - 5, ry, x + width - 4, ry + ROUTES_ROW_FILL_H, 0xFFFFE27A);
            }
            guiGraphics.drawString(this.font, shortText(Component.literal("From: " + route.startStationName()), 158), x + 9, ry + ROUTES_ROW_TEXT_1_Y, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, shortText(Component.literal("To: " + route.endStationName()), 158), x + 9, ry + ROUTES_ROW_TEXT_2_Y, 0xFFFFFFFF, false);
            String meta = ROUTE_TIME_FORMAT.format(Instant.ofEpochMilli(route.createdEpochMillis()))
                    + " | " + route.points().size() + " pts";
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(meta, 158), x + 9, ry + ROUTES_ROW_TEXT_3_Y, 0xFFB9C4D0, false);
        }
        guiGraphics.disableScissor();

        if (hasAbove) {
            guiGraphics.fillGradient(clipLeft, clipTop, clipRight, clipTop + ROUTES_FADE_HEIGHT, 0xF0333840, 0x00333943);
        }
        if (hasBelow) {
            guiGraphics.fillGradient(clipLeft, clipBottom - ROUTES_FADE_HEIGHT, clipRight, clipBottom, 0x00333943, 0xF0333840);
            guiGraphics.fill(clipLeft, clipBottom - 1, clipRight, clipBottom, 0xAA707780);
        }

        guiGraphics.drawString(this.font, "LMB Preview Route  RMB Delete", x + 8, y + ROUTES_FOOTER_Y, 0xFF9EA5AA, false);

        if (pendingDeleteRouteIndex != null) {
            renderDeleteConfirm(guiGraphics, x, y, width, height, routes, mouseX, mouseY);
        }
    }

    private void renderDeleteConfirm(GuiGraphics guiGraphics, int popupX, int popupY, int popupW, int popupH, List<RouteSegment> routes, int mouseX, int mouseY) {
        guiGraphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xAA111316);
        int boxW = 146;
        int boxH = 52;
        int x = popupX + (popupW - boxW) / 2;
        int y = popupY + (popupH - boxH) / 2;
        guiGraphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0xFF111111);
        guiGraphics.fill(x, y, x + boxW, y + boxH, 0xF0292A2E);
        guiGraphics.drawCenteredString(this.font, "Delete route?", x + boxW / 2, y + 6, 0xFFFFC66E);
        if (pendingDeleteRouteIndex >= 0 && pendingDeleteRouteIndex < routes.size()) {
            String route = routes.get(pendingDeleteRouteIndex).startStationName() + " -> " + routes.get(pendingDeleteRouteIndex).endStationName();
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(route, boxW - 10), x + 5, y + 18, 0xFFD8DDE6, false);
        }
        boolean yesHovered = isInside(mouseX, mouseY, x + 20, y + 33, 44, 14);
        boolean noHovered = isInside(mouseX, mouseY, x + boxW - 64, y + 33, 44, 14);
        guiGraphics.fill(x + 20, y + 33, x + 64, y + 47, yesHovered ? 0xFF9E5A5A : 0xFF7A4444);
        guiGraphics.fill(x + boxW - 64, y + 33, x + boxW - 20, y + 47, noHovered ? 0xFF6A6A6A : 0xFF525252);
        guiGraphics.drawCenteredString(this.font, "Delete", x + 42, y + 37, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Cancel", x + boxW - 42, y + 37, 0xFFFFFFFF);
    }

    private void renderHoveredTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY, this.leftPos, this.topPos, PANEL_WIDTH, PANEL_HEIGHT)) {
            return;
        }
        if (routesOpen) {
            Component routeTooltip = hoveredRouteTooltip(mouseX, mouseY);
            if (routeTooltip != null) {
                guiGraphics.renderTooltip(this.font, routeTooltip, mouseX, mouseY);
            }
            return;
        }
        if (shipDropdownOpen) {
            if (isInside(mouseX, mouseY, this.leftPos + SHIP_BOX_X, this.topPos + SHIP_BOX_Y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT)) {
                guiGraphics.renderTooltip(
                        this.font,
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.ship_selector.tooltip"),
                        mouseX,
                        mouseY
                );
            }
            return;
        }
        if (isInside(mouseX, mouseY, this.leftPos + SHIP_BOX_X, this.topPos + SHIP_BOX_Y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT)) {
            guiGraphics.renderTooltip(
                    this.font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.ship_selector.tooltip"),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (!statusTooltipLines.isEmpty() && isInside(mouseX, mouseY, statusValueX, statusValueY, Math.max(1, statusValueWidth), Math.max(1, statusValueHeight))) {
            guiGraphics.renderTooltip(this.font, statusTooltipLines, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (this.minecraft != null && this.minecraft.player != null
                && isInside(mouseX, mouseY, dockValueX, dockValueY, Math.max(1, dockValueWidth), Math.max(1, dockValueHeight))) {
            guiGraphics.renderTooltip(this.font, menu.dockTooltip(this.minecraft.player), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        for (ButtonTooltip tooltip : buttonTooltips) {
            if (tooltip.button().isHovered() && !tooltip.lines().isEmpty()) {
                guiGraphics.renderTooltip(this.font, tooltip.lines(), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    private Component hoveredRouteTooltip(int mouseX, int mouseY) {
        int x = this.leftPos + ROUTES_POPUP_X;
        int y = this.topPos + ROUTES_POPUP_Y;
        int width = ROUTES_POPUP_W;
        int height = ROUTES_POPUP_H;
        int rowY = y + (ROUTES_ROW_Y - ROUTES_POPUP_Y);
        if (hoveredRouteIndex != null && hoveredRouteIndex >= 0 && this.minecraft != null && this.minecraft.player != null) {
            var routes = menu.routeChoices(this.minecraft.player);
            if (hoveredRouteIndex < routes.size()) {
                RouteSegment route = routes.get(hoveredRouteIndex);
                String fullName = route.startStationName() + " -> " + route.endStationName();
                Component invalid = routeInvalidReason(route, this.minecraft.player.level())
                        .map(reason -> Component.literal("Invalid: " + reason))
                        .orElse(Component.empty());
                if (!invalid.getString().isBlank()) {
                    return Component.literal(fullName + " | " + invalid.getString());
                }
                return Component.literal(fullName);
            }
        }
        if (isInside(mouseX, mouseY, x + 6, y + height - 14, width - 12, 12)) {
            return Component.literal("Mouse wheel to scroll");
        }
        return null;
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
        if (routesOpen && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            if (handleRouteClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isInside(mouseX, mouseY, this.leftPos + SHIP_BOX_X, this.topPos + SHIP_BOX_Y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT)) {
            shipDropdownOpen = !shipDropdownOpen;
            routesOpen = false;
            return true;
        }
        if (shipDropdownOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleShipDropdownClick(mouseX, mouseY)) {
                return true;
            }
            shipDropdownOpen = false;
            return true;
        }
        if (shipDropdownOpen) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleRouteClick(double mouseX, double mouseY, int button) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        int x = this.leftPos + ROUTES_POPUP_X;
        int y = this.topPos + ROUTES_POPUP_Y;
        int width = ROUTES_POPUP_W;
        int height = ROUTES_POPUP_H;
        int rowY = y + (ROUTES_ROW_Y - ROUTES_POPUP_Y);
        int clipBottom = y + ROUTES_FOOTER_Y - 2;
        var routes = menu.routeChoices(this.minecraft.player);
        int routeCount = routes.size();
        int maxScroll = Math.max(0, routeCount - ROUTES_VISIBLE_ROWS);

        if (pendingDeleteRouteIndex != null) {
            int boxW = 146;
            int boxH = 52;
            int cx = x + (width - boxW) / 2;
            int cy = y + (height - boxH) / 2;
            if (isInside(mouseX, mouseY, cx + 20, cy + 33, 44, 14)) {
                int idx = pendingDeleteRouteIndex;
                if (idx >= 0 && idx < routeCount) {
                    pressAction(AirshipStationMenu.ACTION_DELETE_ROUTE_BASE + idx);
                }
                pendingDeleteRouteIndex = null;
                return true;
            }
            if (isInside(mouseX, mouseY, cx + boxW - 64, cy + 33, 44, 14) || !isInside(mouseX, mouseY, cx, cy, boxW, boxH)) {
                pendingDeleteRouteIndex = null;
                return true;
            }
            return true;
        }

        int renderedRows = Math.min(routeCount - routeScroll, ROUTES_VISIBLE_ROWS + (routeScroll < maxScroll ? 1 : 0));
        for (int visibleIndex = 0; visibleIndex < renderedRows; visibleIndex++) {
            int routeIndex = routeScroll + visibleIndex;
            if (routeIndex >= routeCount) {
                break;
            }
            int ry = rowY + visibleIndex * ROUTES_ROW_H;
            int visibleBottom = Math.min(ry + ROUTES_ROW_FILL_H, clipBottom);
            if (visibleBottom > ry && isInside(mouseX, mouseY, x + 5, ry, width - 10, visibleBottom - ry)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    pendingDeleteRouteIndex = routeIndex;
                } else {
                    UUID routeId = routes.get(routeIndex).id().value();
                    if (previewedRouteId != null && previewedRouteId.equals(routeId)) {
                        LogisticsClientOverlays.clearFlightPath();
                        previewedRouteId = null;
                    } else {
                        pressAction(AirshipStationMenu.ACTION_PREVIEW_ROUTE_BASE + routeIndex);
                        previewedRouteId = routeId;
                        LogisticsClientOverlays.setPreviewedRouteId(routeId);
                    }
                }
                return true;
            }
        }
        if (isInside(mouseX, mouseY, x - 4, y - 4, width + 8, height + 8)) {
            return true;
        }
        if (!isInside(mouseX, mouseY, x - 4, y - 4, width + 8, height + 8)) {
            routesOpen = false;
            pendingDeleteRouteIndex = null;
            return true;
        }
        return false;
    }

    private boolean handleShipDropdownClick(double mouseX, double mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        var options = this.menu.shipChoices(this.minecraft.player);
        int x = this.leftPos + SHIP_BOX_X;
        int y = this.topPos + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 2;
        int rowHeight = 13;
        int visible = Math.min(6, options.size());
        int height = Math.max(1, visible) * rowHeight + 4;
        if (!isInside(mouseX, mouseY, x, y, SHIP_BOX_WIDTH, height)) {
            return false;
        }
        int row = (int) ((mouseY - y - 3) / rowHeight);
        if (row >= 0 && row < visible) {
            pressAction(AirshipStationMenu.ACTION_SELECT_SHIP_BASE + row);
            shipDropdownOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (routesOpen && this.minecraft != null && this.minecraft.player != null) {
            int routeCount = menu.routeChoices(this.minecraft.player).size();
            int maxScroll = Math.max(0, routeCount - ROUTES_VISIBLE_ROWS);
            int x = this.leftPos + ROUTES_POPUP_X;
            int y = this.topPos + ROUTES_POPUP_Y;
            if (isInside(mouseX, mouseY, x, y, ROUTES_POPUP_W, ROUTES_POPUP_H)) {
                if (maxScroll > 0) {
                    routeScroll = Math.max(0, Math.min(maxScroll, routeScroll + (scrollY < 0 ? 1 : -1)));
                }
                return true;
            }
        }
        if (isInside(mouseX, mouseY, this.leftPos + SHIP_BOX_X, this.topPos + SHIP_BOX_Y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT)) {
            pressAction(AirshipStationMenu.ACTION_SELECT_SHIP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (pendingDeleteRouteIndex != null) {
                pendingDeleteRouteIndex = null;
                return true;
            }
            if (routesOpen) {
                routesOpen = false;
                return true;
            }
            if (shipDropdownOpen) {
                shipDropdownOpen = false;
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

    private void pressAction(int actionId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, actionId);
        }
    }

    private void saveName() {
        if (this.minecraft != null && this.nameBox != null) {
            PacketDistributor.sendToServer(new UpdateIdentityNamePayload(this.menu.stationPos(), this.nameBox.getValue()));
        }
    }

    private void toggleLandingArea() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        LogisticsClientOverlays.toggleLandingArea(
                this.menu.stationPos(),
                AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get()
        );
    }

    private void toggleDockPreview() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.menu.dockPreviewPos(this.minecraft.player).ifPresentOrElse(
                LogisticsClientOverlays::toggleDock,
                LogisticsClientOverlays::clearDock
        );
        if (dockPreviewButton != null) {
            dockPreviewButton.setToolTip(dockPreviewTooltip());
        }
    }

    private Component landingAreaTooltip() {
        return Component.translatable(
                LogisticsClientOverlays.isLandingAreaVisible(this.menu.stationPos())
                        ? "gui.create_aeronautics_automated_logistics.airship_station.hide_landing_area"
                        : "gui.create_aeronautics_automated_logistics.airship_station.show_landing_area"
        );
    }

    private Component dockPreviewTooltip() {
        boolean visible = this.minecraft != null
                && this.minecraft.player != null
                && this.menu.dockPreviewPos(this.minecraft.player).map(LogisticsClientOverlays::isDockVisible).orElse(false);
        return Component.translatable(
                visible
                        ? "gui.create_aeronautics_automated_logistics.dock.hide"
                        : "gui.create_aeronautics_automated_logistics.dock.show"
        );
    }

    private String stationName() {
        return this.minecraft != null && this.minecraft.player != null
                ? menu.stationName(this.minecraft.player)
                : this.title.getString();
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

    private int statusColor(String status) {
        if (status.equalsIgnoreCase("Needs Attention") || status.equalsIgnoreCase("Blocked") || status.equalsIgnoreCase("Route Problem")
                || status.equalsIgnoreCase("No Route") || status.equalsIgnoreCase("Ship Missing")) {
            return 0xFFFFB4B4;
        }
        if (status.equalsIgnoreCase("Running") || status.equalsIgnoreCase("Waiting") || status.equalsIgnoreCase("Recording")) {
            return 0xFFFFE27A;
        }
        if (status.equalsIgnoreCase("Idle")) {
            return 0xFFB9C4D0;
        }
        return 0xFF9EA5AA;
    }

    private String shortText(Component component, int width) {
        return this.font.plainSubstrByWidth(component.getString(), width);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void renderScaledIcon(GuiGraphics guiGraphics, AllIcons icon, int x, int y, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        icon.render(guiGraphics, 0, 0);
        guiGraphics.pose().popPose();
    }

    private java.util.Optional<String> routeInvalidReason(RouteSegment route, Level level) {
        if (AirshipStationRegistry.snapshot(route.startStationId()).isEmpty()) {
            return java.util.Optional.of("missing start station");
        }
        if (AirshipStationRegistry.snapshot(route.endStationId()).isEmpty()) {
            return java.util.Optional.of("missing end station");
        }
        if (!route.dimension().equals(level.dimension())) {
            return java.util.Optional.of("wrong dimension");
        }
        return java.util.Optional.empty();
    }

    private record ButtonTooltip(IconButton button, List<Component> lines) {
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.CargoLinkPromptClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.DockLinkPromptClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.AirshipStationMenuActionPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.PreviewStationRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import org.lwjgl.glfw.GLFW;

public class AirshipStationScreen extends AbstractContainerScreen<AirshipStationMenu> {
    private static final Map<BlockPos, RouteFilterMemory> ROUTE_FILTER_MEMORY = new HashMap<>();
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
    private static final int SHIP_DROPDOWN_MAX_VISIBLE_ROWS = 8;
    private static final int SHIP_DROPDOWN_ROW_HEIGHT = 13;
    private static final int SHIP_DROPDOWN_SCROLLBAR_WIDTH = 3;
    private static final int SHIP_DROPDOWN_FADE_HEIGHT = 14;
    private static final long SHIP_TYPEAHEAD_TIMEOUT_MS = 1000L;
    private static final int STATUS_FOOTER_X = 82;
    private static final int STATUS_FOOTER_Y = 216;
    private static final int STATUS_FOOTER_WIDTH = 74;
    private static final int SECTION_HEADER_COLOR = 0xFFEEE8DD;
    private static final int DIVIDER_Y = 116;
    private static final int STATION_LABEL_Y = 125;
    private static final int DOCK_ROW_Y = 139;
    private static final int CARGO_ROW_Y = 153;
    private static final int ROUTES_PANEL_ROW_Y = 172;
    private static final int ROUTES_VISIBLE_ROWS = 3;
    private static final int ROUTES_POPUP_X = 8;
    private static final int ROUTES_POPUP_Y = 42;
    private static final int ROUTES_POPUP_W = 176;
    private static final int ROUTES_POPUP_H = 159;
    private static final int ROUTES_FILTER_BOX_WIDTH = 88;
    private static final int ROUTES_FILTER_BOX_HEIGHT = 20;
    private static final int ROUTES_FILTER_DROPDOWN_MAX_VISIBLE_ROWS = 6;
    private static final int ROUTES_FILTER_ROW_HEIGHT = 13;
    private static final int ROUTES_FILTER_SCROLLBAR_WIDTH = 3;
    private static final int ROUTES_HEADER_LINE_Y = 27;
    private static final int ROUTES_ROW_Y = ROUTES_POPUP_Y + 33;
    private static final int ROUTES_ROW_H = 35;
    private static final int ROUTES_ROW_FILL_H = 33;
    private static final int ROUTES_ROW_TEXT_1_Y = 2;
    private static final int ROUTES_ROW_TEXT_2_Y = 12;
    private static final int ROUTES_ROW_TEXT_3_Y = 22;
    private static final int ROUTES_FOOTER_Y = ROUTES_POPUP_H - 10;
    private static final int ROUTES_FADE_HEIGHT = 16;
    private static final int ROUTES_SHOW_ALL_TEXT_PADDING_X = 4;
    private static final int ROUTES_SHOW_ALL_TEXT_PADDING_Y = 2;
    private static final DateTimeFormatter ROUTE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/airship_station.png");
    private static final ScreenElement SHOW_DOCK_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 64, 192, 16, 16, 256);
    private static final ScreenElement SHOW_CARGO_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), -1, 176, 15, 15, 256, 1.0F, 16);
    private static final ScreenElement SHOW_CHUNKS_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 32, 0, 16, 16, 256);

    private final List<ButtonTooltip> buttonTooltips = new ArrayList<>();
    private EditBox nameBox;
    private String pendingSubmittedName;
    private IconButton runButton;
    private IconButton stopButton;
    private IconButton landingAreaButton;
    private IconButton routesButton;
    private IconButton stationChunksButton;
    private IconButton dockPreviewButton;
    private IconButton cargoPreviewButton;
    private MiniIconButton dockLinkButton;
    private MiniIconButton dockClearButton;
    private MiniIconButton cargoLinkButton;
    private MiniIconButton cargoClearButton;
    private boolean shipDropdownOpen;
    private int shipDropdownScroll;
    private String shipTypeaheadBuffer = "";
    private long shipTypeaheadExpiryMs;
    private boolean routesOpen;
    private int routeScroll;
    private boolean routeFilterDropdownOpen;
    private int routeFilterDropdownScroll;
    private int routeFilterIndex;
    private UUID routeFilterTransponderId;
    private boolean routeFilterStateRestored;
    private boolean previewAllFilteredRoutes;
    private final LinkedHashSet<UUID> previewedRouteIds = new LinkedHashSet<>();
    private Integer hoveredRouteIndex;
    private int statusValueX;
    private int statusValueY;
    private int statusValueWidth;
    private int statusValueHeight;
    private int dockValueX;
    private int dockValueY;
    private int dockValueWidth;
    private int dockValueHeight;
    private int cargoValueX;
    private int cargoValueY;
    private int cargoValueWidth;
    private int cargoValueHeight;
    private int lastMouseX;
    private int lastMouseY;
    private List<Component> statusTooltipLines = List.of();

    public AirshipStationScreen(AirshipStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = 10000;
    }

    public AirshipStationMenu stationMenu() {
        return this.menu;
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
        routeFilterStateRestored = false;

        int controlsY = y + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 8;
        int shipSelectorRight = x + SHIP_BOX_X + SHIP_BOX_WIDTH;
        int controlsStopX = shipSelectorRight - 18;
        int controlsStartX = controlsStopX - 26;
        runButton = addIconButton(
                controlsStartX,
                controlsY,
                AllIcons.I_PLAY,
                () -> pressAction(AirshipStationMenu.ACTION_RUN_SCHEDULE),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.run_schedule.tooltip")
        );
        stopButton = addIconButton(
                controlsStartX + 26,
                controlsY,
                AllIcons.I_STOP,
                () -> pressAction(AirshipStationMenu.ACTION_STOP_SCHEDULE),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_1"),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_2")
        );
        routesButton = addIconButton(
                controlsStopX,
                y + ROUTES_PANEL_ROW_Y + 1,
                AllIcons.I_VIEW_SCHEDULE,
                () -> {
                    routesOpen = !routesOpen;
                    shipDropdownOpen = false;
                    routeFilterDropdownOpen = false;
                },
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.routes.tooltip")
        );

        int bottomButtonsY = y + PANEL_HEIGHT - 24;
        landingAreaButton = addIconButton(
                x + 6,
                bottomButtonsY,
                AllIcons.I_TARGET,
                this::toggleLandingArea,
                landingAreaTooltipTitle()
        );
        stationChunksButton = addIconButton(
                x + 25,
                bottomButtonsY,
                SHOW_CHUNKS_ICON,
                this::toggleStationChunks,
                stationChunksTooltipTitle()
        );
        dockPreviewButton = addIconButton(
                x + 44,
                bottomButtonsY,
                SHOW_DOCK_ICON,
                this::toggleDockPreview,
                dockPreviewTooltip()
        );
        cargoPreviewButton = addIconButton(
                x + 63,
                bottomButtonsY,
                SHOW_CARGO_ICON,
                this::toggleCargoPreview,
                cargoPreviewTooltip()
        );
        addIconButton(
                x + PANEL_WIDTH - 33,
                y + PANEL_HEIGHT - 24,
                AllIcons.I_CONFIRM,
                this::onClose,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.close.tooltip")
        );
        int dockButtonsY = y + DOCK_ROW_Y + 1;
        int clearButtonX = shipSelectorRight - 12;
        int linkButtonX = clearButtonX - 14;
        dockLinkButton = addMiniIconButton(
                linkButtonX,
                dockButtonsY,
                AllIcons.I_ADD,
                () -> pressAction(AirshipStationMenu.ACTION_BEGIN_LINK_DOCK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.link")
        );
        dockClearButton = addMiniIconButton(
                clearButtonX,
                dockButtonsY,
                AllIcons.I_MTD_CLOSE,
                () -> pressAction(AirshipStationMenu.ACTION_CLEAR_DOCK_LINK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.clear")
        );
        cargoLinkButton = addMiniIconButton(
                linkButtonX,
                dockButtonsY + 14,
                AllIcons.I_ADD,
                () -> pressAction(AirshipStationMenu.ACTION_LINK_CARGO),
                Component.translatable("gui.create_aeronautics_automated_logistics.cargo.link")
        );
        cargoClearButton = addMiniIconButton(
                clearButtonX,
                dockButtonsY + 14,
                AllIcons.I_MTD_CLOSE,
                () -> pressAction(AirshipStationMenu.ACTION_CLEAR_CARGO),
                Component.translatable("gui.create_aeronautics_automated_logistics.cargo.clear")
        );

        if (this.minecraft != null
                && this.minecraft.player != null
                && !menu.hasSelectedShip(this.minecraft.player)) {
            pressAction(AirshipStationMenu.ACTION_AUTO_SELECT_CLOSEST_SHIP);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (nameBox != null && !nameBox.isFocused()) {
            String authoritativeName = stationName();
            if (pendingSubmittedName != null) {
                if (pendingSubmittedName.equals(authoritativeName)) {
                    pendingSubmittedName = null;
                } else {
                    centerNameBox();
                    return;
                }
            }
            if (!nameBox.getValue().equals(authoritativeName)) {
                nameBox.setValue(authoritativeName);
            }
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
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        if (landingAreaButton != null) {
            landingAreaButton.green = LogisticsClientOverlays.isLandingAreaVisible(this.menu.stationPos());
        }
        if (stationChunksButton != null) {
            stationChunksButton.active = AutomatedLogisticsConfig.forceLoadStationChunks();
            stationChunksButton.green = LogisticsClientOverlays.isStationChunksVisible(
                    this.menu.stationPos(),
                    AutomatedLogisticsConfig.stationInteractionChunkRadius()
            );
            if (!stationChunksButton.active) {
                LogisticsClientOverlays.clearStationChunksIfMatches(this.menu.stationPos());
            }
        }
        if (dockPreviewButton != null && this.minecraft != null && this.minecraft.player != null) {
            boolean hasLinkedDock = menu.hasLinkedDock(this.minecraft.player);
            if (!hasLinkedDock) {
                LogisticsClientOverlays.clearDock();
            }
            dockPreviewButton.active = hasLinkedDock;
            dockPreviewButton.green = menu.dockPreviewPos(this.minecraft.player)
                    .map(LogisticsClientOverlays::isDockVisible)
                    .orElse(false);
        }
        if (dockClearButton != null && this.minecraft != null && this.minecraft.player != null) {
            boolean pendingDockLink = DockLinkPromptClientState.isPendingForStation(this.menu.stationPos());
            dockClearButton.active = pendingDockLink || menu.hasLinkedDock(this.minecraft.player);
            dockClearButton.green = pendingDockLink;
        }
        if (cargoPreviewButton != null && this.minecraft != null && this.minecraft.player != null) {
            List<List<BlockPos>> cargoPreview = menu.cargoPreviewPositionGroups(this.minecraft.player);
            if (cargoPreview.isEmpty()) {
                LogisticsClientOverlays.clearCargo();
            }
            cargoPreviewButton.active = menu.hasLinkedCargo(this.minecraft.player);
            cargoPreviewButton.green = LogisticsClientOverlays.isCargoVisibleGroups(cargoPreview);
        }
        if (cargoClearButton != null && this.minecraft != null && this.minecraft.player != null) {
            boolean pendingCargoLink = CargoLinkPromptClientState.isPendingForStation(this.menu.stationPos());
            cargoClearButton.active = pendingCargoLink || menu.hasLinkedCargo(this.minecraft.player);
            cargoClearButton.green = pendingCargoLink;
        }
        previewedRouteIds.clear();
        previewedRouteIds.addAll(LogisticsClientOverlays.previewedRouteIds());
        if (this.minecraft != null && this.minecraft.player != null) {
            boolean running = menu.selectedShipScheduleRunning(this.minecraft.player);
            boolean held = menu.selectedShipScheduleHeld(this.minecraft.player);
            if (runButton != null) {
                runButton.active = menu.canRunSelectedShip(this.minecraft.player);
                runButton.green = running;
            }
            if (stopButton != null) {
                stopButton.active = menu.canStopSelectedShip(this.minecraft.player);
            }
            if (routesButton != null) {
                routesButton.active = menu.canControlStationLocally(this.minecraft.player);
                routesButton.green = !previewedRouteIds.isEmpty();
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHeaderEditIcon(guiGraphics);
        int headerY = this.topPos + 21;
        guiGraphics.drawCenteredString(this.font, "Station Control", this.leftPos + PANEL_WIDTH / 2, headerY, 0xFFE7D7B3);
        renderShipSelector(guiGraphics, mouseX, mouseY);
        renderMainStatus(guiGraphics);
        if (shipDropdownOpen) {
            renderShipDropdown(guiGraphics, mouseX, mouseY);
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
                Component.literal("SHIP"),
                x,
                y - 12,
                SECTION_HEADER_COLOR,
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

    private void renderShipDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        var options = this.menu.shipChoices(this.minecraft.player);
        int x = this.leftPos + SHIP_BOX_X;
        int y = this.topPos + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 2;
        int visible = Math.min(SHIP_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
        int height = Math.max(1, visible) * SHIP_DROPDOWN_ROW_HEIGHT + 4;
        boolean scrollable = options.size() > visible;
        int contentRight = x + SHIP_BOX_WIDTH - (scrollable ? SHIP_DROPDOWN_SCROLLBAR_WIDTH + 4 : 4);
        guiGraphics.fill(x, y, x + SHIP_BOX_WIDTH, y + height, 0xF018101C);
        guiGraphics.fill(x + 1, y + 1, x + SHIP_BOX_WIDTH - 1, y + height - 1, 0xF02B2130);
        if (options.isEmpty()) {
            guiGraphics.drawString(this.font, "No ships found", x + 5, y + 5, 0xD8DDE6, false);
            return;
        }
        int maxScroll = Math.max(0, options.size() - visible);
        shipDropdownScroll = Math.max(0, Math.min(maxScroll, shipDropdownScroll));
        for (int i = 0; i < visible; i++) {
            int optionIndex = shipDropdownScroll + i;
            if (optionIndex >= options.size()) {
                break;
            }
            AirshipStationMenu.ShipChoice option = options.get(optionIndex);
            int rowY = y + 4 + i * SHIP_DROPDOWN_ROW_HEIGHT;
            boolean hovered = isInside(mouseX, mouseY, x + 2, rowY - 1, contentRight - x - 2, SHIP_DROPDOWN_ROW_HEIGHT);
            int highlightTop = rowY - 1;
            int highlightBottom = rowY + this.font.lineHeight + 1;
            if (hovered) {
                guiGraphics.fill(x + 4, highlightTop, contentRight, highlightBottom, 0x7A525963);
            }
            if (option.selected()) {
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
            int labelColor = 0xFFD8DDE6;
            String status = option.statusText().getString();
            int statusWidth = this.font.width(status) + 8;
            String clipped = this.font.plainSubstrByWidth(option.shipName().getString(), contentRight - (x + 5) - statusWidth - 2);
            guiGraphics.drawString(this.font, clipped, x + 5, rowY, labelColor, false);
            guiGraphics.drawString(
                    this.font,
                    status,
                    contentRight - this.font.width(status) - 2,
                    rowY,
                    option.statusColor(),
                    false
            );
        }
        if (shipDropdownScroll > 0) {
            guiGraphics.fillGradient(
                    x + 1,
                    y + 1,
                    x + SHIP_BOX_WIDTH - 1,
                    y + 1 + SHIP_DROPDOWN_FADE_HEIGHT,
                    0xF0333840,
                    0x00333943
            );
        }
        if (shipDropdownScroll < maxScroll) {
            guiGraphics.fillGradient(
                    x + 1,
                    y + height - 1 - SHIP_DROPDOWN_FADE_HEIGHT,
                    x + SHIP_BOX_WIDTH - 1,
                    y + height - 1,
                    0x00333943,
                    0xF0333840
            );
        }
        if (scrollable) {
            int trackLeft = x + SHIP_BOX_WIDTH - SHIP_DROPDOWN_SCROLLBAR_WIDTH - 2;
            int trackRight = x + SHIP_BOX_WIDTH - 2;
            int trackTop = y + 3;
            int trackBottom = y + height - 3;
            int trackHeight = trackBottom - trackTop;
            guiGraphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0xAA20242A);

            int knobHeight = Math.max(12, Math.round((visible / (float) options.size()) * trackHeight));
            int knobTravel = Math.max(0, trackHeight - knobHeight);
            int knobTop = trackTop + (maxScroll <= 0 ? 0 : Math.round((shipDropdownScroll / (float) maxScroll) * knobTravel));
            int knobBottom = knobTop + knobHeight;
            guiGraphics.fill(trackLeft, knobTop + 1, trackRight, knobBottom - 1, 0xFF7A818C);
            guiGraphics.hLine(trackLeft + 1, trackRight - 1, knobTop, 0xFFB9C4D0);
            guiGraphics.hLine(trackLeft + 1, trackRight - 1, knobBottom - 1, 0xFF535A64);
            guiGraphics.vLine(trackLeft, knobTop + 1, knobBottom - 2, 0xFF929AA6);
            guiGraphics.vLine(trackRight - 1, knobTop + 1, knobBottom - 2, 0xFF535A64);
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
        int x = this.leftPos + SHIP_BOX_X;
        int dividerY = this.topPos + DIVIDER_Y;
        guiGraphics.hLine(this.leftPos + INNER_X + 11, this.leftPos + INNER_X + INNER_WIDTH - 21, dividerY, 0xFF515151);

        Component status = menu.panelStatusText(this.minecraft.player);
        int runY = this.topPos + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 17;
        guiGraphics.drawString(this.font, "Run:", x, runY, 0xFFB9C4D0, false);

        int statusY = this.topPos + STATUS_FOOTER_Y;
        int statusValueAreaLeft = this.leftPos + STATUS_FOOTER_X;
        int statusValueAreaWidth = STATUS_FOOTER_WIDTH;
        String statusValue = shortText(status, statusValueAreaWidth);
        int statusValueDrawX = statusValueAreaLeft + Math.max(0, (statusValueAreaWidth - this.font.width(statusValue)) / 2) + 3;
        guiGraphics.drawString(
                this.font,
                statusValue,
                statusValueDrawX,
                statusY,
                menu.panelStatusColor(this.minecraft.player),
                false
        );
        statusValueX = statusValueAreaLeft;
        statusValueY = statusY;
        statusValueWidth = statusValueAreaWidth;
        statusValueHeight = this.font.lineHeight;

        int stationLabelY = this.topPos + STATION_LABEL_Y;
        guiGraphics.drawString(this.font, "STATION", x, stationLabelY, SECTION_HEADER_COLOR, false);

        int dockRowY = this.topPos + DOCK_ROW_Y;
        guiGraphics.drawString(this.font, "Dock:", x, dockRowY + 3, 0xFFB9C4D0, false);
        int linkValueAreaLeft = x + Math.max(this.font.width("Dock: "), this.font.width("Cargo: ")) + 2;
        int dockButtonsLeft = this.leftPos + INNER_X + INNER_WIDTH - 65;
        int linkValueAreaWidth = Math.max(1, (dockButtonsLeft - 4) - linkValueAreaLeft);
        String dockValue = shortText(menu.dockCompactText(this.minecraft.player), linkValueAreaWidth);
        int dockValueDrawX = linkValueAreaLeft + Math.max(0, (linkValueAreaWidth - this.font.width(dockValue)) / 2);
        dockValueX = Math.max(linkValueAreaLeft, dockValueDrawX);
        dockValueY = dockRowY + 3;
        dockValueWidth = this.font.width(dockValue);
        dockValueHeight = this.font.lineHeight;
        guiGraphics.drawString(this.font, dockValue, dockValueX, dockValueY, menu.dockStatusColor(this.minecraft.player), false);

        int cargoRowY = this.topPos + CARGO_ROW_Y;
        guiGraphics.drawString(this.font, "Cargo:", x, cargoRowY + 3, 0xFFB9C4D0, false);
        int cargoButtonsLeft = this.leftPos + INNER_X + INNER_WIDTH - 65;
        String cargoValue = shortText(menu.cargoCompactText(this.minecraft.player), linkValueAreaWidth);
        int cargoValueDrawX = linkValueAreaLeft + Math.max(0, (linkValueAreaWidth - this.font.width(cargoValue)) / 2);
        cargoValueX = Math.max(linkValueAreaLeft, cargoValueDrawX);
        cargoValueY = cargoRowY + 3;
        cargoValueWidth = this.font.width(cargoValue);
        cargoValueHeight = this.font.lineHeight;
        guiGraphics.drawString(this.font, cargoValue, cargoValueX, cargoValueY, menu.cargoStatusColor(this.minecraft.player), false);

        int routesY = this.topPos + ROUTES_PANEL_ROW_Y;
        guiGraphics.drawString(this.font, "Routes:", x, routesY + 11, 0xFFB9C4D0, false);

        statusTooltipLines = menu.statusTooltipLines(this.minecraft.player);
    }

    private void renderRoutesPopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        List<RouteFilterOption> filterOptions = routeFilterOptions();
        clampRouteFilterState(filterOptions);
        var allRoutes = menu.routeChoiceSummaries(this.minecraft.player);
        var routes = filteredRouteSummaries(allRoutes, filterOptions);
        previewAllFilteredRoutes = matchesCurrentFilteredSelection(routes);
        if (!previewedRouteIds.isEmpty() && allRoutes.stream().map(AirshipStationMenu.RouteChoiceSummary::id).noneMatch(previewedRouteIds::contains)) {
            LogisticsClientOverlays.clearFlightPath();
            previewedRouteIds.clear();
        }
        if (!previewedRouteIds.isEmpty() && !currentRouteIds(allRoutes).containsAll(previewedRouteIds)) {
            LogisticsClientOverlays.clearFlightPath();
            previewedRouteIds.clear();
        }
        if (previewAllFilteredRoutes && routes.isEmpty()) {
            LogisticsClientOverlays.clearFlightPath();
            previewedRouteIds.clear();
            previewAllFilteredRoutes = false;
        }
        int x = this.leftPos + ROUTES_POPUP_X;
        int y = this.topPos + ROUTES_POPUP_Y;
        int width = ROUTES_POPUP_W;
        int height = ROUTES_POPUP_H;
        int filterBoxX = x + width - ROUTES_FILTER_BOX_WIDTH - 8;
        int filterBoxY = y + 4;
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xF0101010);
        guiGraphics.fill(x, y, x + width, y + height, 0xF0333840);
        guiGraphics.drawString(this.font, "ROUTES", x + 8, y + 10, SECTION_HEADER_COLOR, false);
        renderRouteFilterSelector(guiGraphics, mouseX, mouseY, filterOptions, filterBoxX, filterBoxY);
        guiGraphics.hLine(x + 12, x + width - 12, y + ROUTES_HEADER_LINE_Y, 0xFF555A60);
        hoveredRouteIndex = null;

        if (routes.isEmpty()) {
            guiGraphics.drawWordWrap(
                    this.font,
                    Component.literal(routeFilterIndex == 0 ? "No routes at this station" : "No routes for this ship at this station"),
                    x + 21,
                    y + 56,
                    width - 42,
                    0xFFB9C4D0
            );
            renderShowAllToggle(guiGraphics, x, y, false, false);
            if (routeFilterDropdownOpen) {
                renderRouteFilterDropdown(guiGraphics, mouseX, mouseY, filterOptions, filterBoxX, filterBoxY);
            }
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
            AirshipStationMenu.RouteChoiceSummary route = routes.get(routeIndex);
            int ry = rowY + visibleIndex * rowHeight;
            int visibleBottom = Math.min(ry + ROUTES_ROW_FILL_H, clipBottom);
            boolean hovered = visibleBottom > ry && isInside(mouseX, mouseY, x + 5, ry, width - 10, visibleBottom - ry);
            if (hovered) {
                for (int i = 0; i < allRoutes.size(); i++) {
                    if (allRoutes.get(i).id().equals(route.id())) {
                        hoveredRouteIndex = i;
                        break;
                    }
                }
            }
            boolean previewed = previewedRouteIds.contains(route.id());
            boolean invalid = routeInvalidReason(route, this.minecraft.player.level()).isPresent();
            int rowColor = invalid ? (hovered ? 0xAA704545 : 0x885A3A3A) : (hovered ? 0xAA555B65 : 0x88414850);
            if (previewed && !invalid) {
                rowColor = hovered ? 0xCCE7C46E : 0x99D9B968;
            } else if (previewed) {
                rowColor = hovered ? 0xAA8A6A45 : 0x887A5B3D;
            }
            guiGraphics.fill(x + 5, ry, x + width - 5, ry + ROUTES_ROW_FILL_H, rowColor);
            guiGraphics.drawString(
                    this.font,
                    shortText(Component.literal("Ship: " + route.shipName()), 158),
                    x + 9,
                    ry + ROUTES_ROW_TEXT_1_Y,
                    0xFFFFFFFF,
                    false
            );
            String routeSummary = "From: "
                    + stationName(route.startStationId(), route.startStationName())
                    + " | To: "
                    + stationName(route.endStationId(), route.endStationName());
            guiGraphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(routeSummary, 158),
                    x + 9,
                    ry + ROUTES_ROW_TEXT_2_Y,
                    0xFFFFFFFF,
                    false
            );
            String meta = ROUTE_TIME_FORMAT.format(Instant.ofEpochMilli(route.createdEpochMillis()))
                    + " | " + route.pointCount() + " pts";
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

        guiGraphics.drawString(this.font, "LMB Preview Route", x + 8, y + ROUTES_FOOTER_Y, 0xFF9EA5AA, false);
        renderShowAllToggle(guiGraphics, x, y, previewAllFilteredRoutes, true);
        if (routeFilterDropdownOpen) {
            renderRouteFilterDropdown(guiGraphics, mouseX, mouseY, filterOptions, filterBoxX, filterBoxY);
        }
    }

    private void renderRouteFilterSelector(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            List<RouteFilterOption> filterOptions,
            int x,
            int y
    ) {
        boolean hovered = isInside(mouseX, mouseY, x, y, ROUTES_FILTER_BOX_WIDTH, ROUTES_FILTER_BOX_HEIGHT);
        guiGraphics.fill(x, y, x + ROUTES_FILTER_BOX_WIDTH, y + ROUTES_FILTER_BOX_HEIGHT, hovered ? 0xDD9C9C9C : 0xCC747474);
        guiGraphics.fill(x + 1, y + 1, x + ROUTES_FILTER_BOX_WIDTH - 1, y + ROUTES_FILTER_BOX_HEIGHT - 1, 0xEE242424);
        String label = filterOptions.get(routeFilterIndex).label();
        guiGraphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(label, ROUTES_FILTER_BOX_WIDTH - 12),
                x + 6,
                y + 6,
                0xE5E8EE,
                false
        );
    }

    private void renderRouteFilterDropdown(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            List<RouteFilterOption> filterOptions,
            int x,
            int y
    ) {
        int dropdownY = y + ROUTES_FILTER_BOX_HEIGHT + 2;
        int visible = Math.min(ROUTES_FILTER_DROPDOWN_MAX_VISIBLE_ROWS, filterOptions.size());
        int height = Math.max(1, visible) * ROUTES_FILTER_ROW_HEIGHT + 4;
        boolean scrollable = filterOptions.size() > visible;
        int contentRight = x + ROUTES_FILTER_BOX_WIDTH - (scrollable ? ROUTES_FILTER_SCROLLBAR_WIDTH + 4 : 4);
        guiGraphics.fill(x, dropdownY, x + ROUTES_FILTER_BOX_WIDTH, dropdownY + height, 0xF018101C);
        guiGraphics.fill(x + 1, dropdownY + 1, x + ROUTES_FILTER_BOX_WIDTH - 1, dropdownY + height - 1, 0xF02B2130);
        int maxScroll = Math.max(0, filterOptions.size() - visible);
        routeFilterDropdownScroll = Math.max(0, Math.min(maxScroll, routeFilterDropdownScroll));
        for (int i = 0; i < visible; i++) {
            int optionIndex = routeFilterDropdownScroll + i;
            if (optionIndex >= filterOptions.size()) {
                break;
            }
            RouteFilterOption option = filterOptions.get(optionIndex);
            int rowY = dropdownY + 4 + i * ROUTES_FILTER_ROW_HEIGHT;
            boolean hovered = isInside(mouseX, mouseY, x + 2, rowY - 1, contentRight - x - 2, ROUTES_FILTER_ROW_HEIGHT);
            int highlightTop = rowY - 1;
            int highlightBottom = rowY + this.font.lineHeight + 1;
            if (hovered) {
                guiGraphics.fill(x + 4, highlightTop, contentRight, highlightBottom, 0x7A525963);
            }
            if (optionIndex == routeFilterIndex) {
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
            guiGraphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(option.label(), contentRight - (x + 5) - 2),
                    x + 5,
                    rowY,
                    0xFFD8DDE6,
                    false
            );
        }
        if (routeFilterDropdownScroll > 0) {
            guiGraphics.fillGradient(
                    x + 1,
                    dropdownY + 1,
                    x + ROUTES_FILTER_BOX_WIDTH - 1,
                    dropdownY + 1 + SHIP_DROPDOWN_FADE_HEIGHT,
                    0xF0333840,
                    0x00333943
            );
        }
        if (routeFilterDropdownScroll < maxScroll) {
            guiGraphics.fillGradient(
                    x + 1,
                    dropdownY + height - 1 - SHIP_DROPDOWN_FADE_HEIGHT,
                    x + ROUTES_FILTER_BOX_WIDTH - 1,
                    dropdownY + height - 1,
                    0x00333943,
                    0xF0333840
            );
        }
        if (scrollable) {
            int trackLeft = x + ROUTES_FILTER_BOX_WIDTH - ROUTES_FILTER_SCROLLBAR_WIDTH - 2;
            int trackRight = x + ROUTES_FILTER_BOX_WIDTH - 2;
            int trackTop = dropdownY + 3;
            int trackBottom = dropdownY + height - 3;
            int trackHeight = trackBottom - trackTop;
            guiGraphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0xAA20242A);

            int knobHeight = Math.max(12, Math.round((visible / (float) filterOptions.size()) * trackHeight));
            int knobTravel = Math.max(0, trackHeight - knobHeight);
            int knobTop = trackTop + (maxScroll <= 0 ? 0 : Math.round((routeFilterDropdownScroll / (float) maxScroll) * knobTravel));
            int knobBottom = knobTop + knobHeight;
            guiGraphics.fill(trackLeft, knobTop + 1, trackRight, knobBottom - 1, 0xFF7A818C);
            guiGraphics.hLine(trackLeft + 1, trackRight - 1, knobTop, 0xFFB9C4D0);
            guiGraphics.hLine(trackLeft + 1, trackRight - 1, knobBottom - 1, 0xFF535A64);
            guiGraphics.vLine(trackLeft, knobTop + 1, knobBottom - 2, 0xFF929AA6);
            guiGraphics.vLine(trackRight - 1, knobTop + 1, knobBottom - 2, 0xFF535A64);
        }
    }

    private void renderShowAllToggle(GuiGraphics guiGraphics, int popupX, int popupY, boolean active, boolean enabled) {
        int width = routeShowAllWidth();
        int height = routeShowAllHeight();
        int x = popupX + ROUTES_POPUP_W - width - 8;
        int y = popupY + ROUTES_FOOTER_Y - 1;
        int visibleHeight = Math.max(1, height - 2);
        int border = active ? 0xFF627C58 : 0xAA747474;
        int fill = active ? 0x99526449 : 0x66333943;
        int textColor = enabled ? 0xFFD8DDE6 : 0xFF7A818C;
        guiGraphics.fill(x, y, x + width, y + visibleHeight, border);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + visibleHeight - 1, fill);
        String label = active ? "Hide All" : "Show All";
        int drawX = x + Math.max(0, (width - this.font.width(label)) / 2);
        guiGraphics.drawString(this.font, label, drawX, y + ROUTES_SHOW_ALL_TEXT_PADDING_Y, textColor, false);
    }

    private int routeShowAllWidth() {
        int labelWidth = Math.max(this.font.width("Show All"), this.font.width("Hide All"));
        return labelWidth + (ROUTES_SHOW_ALL_TEXT_PADDING_X * 2);
    }

    private int routeShowAllHeight() {
        return this.font.lineHeight + (ROUTES_SHOW_ALL_TEXT_PADDING_Y * 2);
    }

    private List<RouteFilterOption> routeFilterOptions() {
        List<RouteFilterOption> options = new ArrayList<>();
        options.add(new RouteFilterOption(null, "All Ships"));
        if (this.minecraft == null || this.minecraft.player == null) {
            return options;
        }
        LinkedHashMap<UUID, String> labels = new LinkedHashMap<>();
        for (AirshipStationMenu.ShipChoice shipChoice : menu.shipChoices(this.minecraft.player)) {
            labels.put(shipChoice.transponderId(), shipChoice.shipName().getString());
        }
        for (AirshipStationMenu.RouteChoiceSummary route : menu.routeChoiceSummaries(this.minecraft.player)) {
            labels.putIfAbsent(
                    route.transponderId(),
                    route.shipName().isBlank() ? route.transponderId().toString() : route.shipName()
            );
        }
        labels.forEach((transponderId, label) -> options.add(new RouteFilterOption(transponderId, label)));
        return options;
    }

    private void clampRouteFilterState(List<RouteFilterOption> options) {
        restoreRouteFilterStateIfNeeded();
        int maxIndex = Math.max(0, options.size() - 1);
        int resolvedIndex = 0;
        if (routeFilterTransponderId != null) {
            for (int i = 1; i < options.size(); i++) {
                if (routeFilterTransponderId.equals(options.get(i).transponderId())) {
                    resolvedIndex = i;
                    break;
                }
            }
        } else {
            resolvedIndex = routeFilterIndex;
        }
        routeFilterIndex = Math.max(0, Math.min(maxIndex, resolvedIndex));
        int visible = Math.min(ROUTES_FILTER_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
        int maxScroll = Math.max(0, options.size() - visible);
        routeFilterDropdownScroll = Math.max(0, Math.min(maxScroll, routeFilterDropdownScroll));
        routeFilterTransponderId = options.get(routeFilterIndex).transponderId();
    }

    private List<AirshipStationMenu.RouteChoiceSummary> filteredRouteSummaries(
            List<AirshipStationMenu.RouteChoiceSummary> routes,
            List<RouteFilterOption> filterOptions
    ) {
        UUID transponderId = filterOptions.get(routeFilterIndex).transponderId();
        if (transponderId == null) {
            return routes;
        }
        return routes.stream().filter(route -> transponderId.equals(route.transponderId())).toList();
    }

    private LinkedHashSet<UUID> currentRouteIds(List<AirshipStationMenu.RouteChoiceSummary> routes) {
        LinkedHashSet<UUID> routeIds = new LinkedHashSet<>();
        routes.stream().map(AirshipStationMenu.RouteChoiceSummary::id).forEach(routeIds::add);
        return routeIds;
    }

    private boolean matchesCurrentFilteredSelection(List<AirshipStationMenu.RouteChoiceSummary> filteredRoutes) {
        if (filteredRoutes.isEmpty() || previewedRouteIds.isEmpty()) {
            return false;
        }
        LinkedHashSet<UUID> filteredIds = currentRouteIds(filteredRoutes);
        return previewedRouteIds.containsAll(filteredIds);
    }

    private void sendRoutePreviewSelection() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (previewedRouteIds.isEmpty()) {
            LogisticsClientOverlays.clearFlightPath();
            return;
        }
        LogisticsClientOverlays.setPreviewedRouteIds(previewedRouteIds);
        PacketDistributor.sendToServer(new PreviewStationRoutesPayload(
                this.menu.containerId,
                this.menu.stationPos(),
                List.copyOf(previewedRouteIds)
        ));
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
        if (this.minecraft != null && this.minecraft.player != null
                && isInside(mouseX, mouseY, cargoValueX, cargoValueY, Math.max(1, cargoValueWidth), Math.max(1, cargoValueHeight))) {
            guiGraphics.renderTooltip(this.font, menu.cargoTooltip(this.minecraft.player), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (landingAreaButton != null && landingAreaButton.isHovered()) {
            guiGraphics.renderTooltip(this.font, landingAreaTooltip(), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (stationChunksButton != null && stationChunksButton.isHovered()) {
            guiGraphics.renderTooltip(this.font, stationChunksTooltip(), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (dockPreviewButton != null && dockPreviewButton.isHovered()) {
            guiGraphics.renderTooltip(this.font, List.of(dockPreviewTooltip()), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (cargoPreviewButton != null && cargoPreviewButton.isHovered()) {
            guiGraphics.renderTooltip(this.font, List.of(cargoPreviewTooltip()), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (dockClearButton != null && dockClearButton.isHovered()) {
            guiGraphics.renderTooltip(this.font, List.of(dockClearTooltip()), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (cargoClearButton != null && cargoClearButton.isHovered()) {
            guiGraphics.renderTooltip(
                    this.font,
                    List.of(cargoClearTooltip()),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY
            );
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
        if (hoveredRouteIndex != null && hoveredRouteIndex >= 0 && this.minecraft != null && this.minecraft.player != null) {
            var routes = menu.routeChoiceSummaries(this.minecraft.player);
            int fullIndex = hoveredRouteIndex;
            if (fullIndex < routes.size()) {
                AirshipStationMenu.RouteChoiceSummary route = routes.get(fullIndex);
                String fullName = stationName(route.startStationId(), route.startStationName())
                        + " -> "
                        + stationName(route.endStationId(), route.endStationName());
                String shipName = route.shipName().isBlank() ? "" : " | " + route.shipName();
                Component invalid = routeInvalidReason(route, this.minecraft.player.level())
                        .map(reason -> Component.literal("Invalid: " + reason))
                        .orElse(Component.empty());
                if (!invalid.getString().isBlank()) {
                    return Component.literal(fullName + shipName + " | " + invalid.getString());
                }
                return Component.literal(fullName + shipName);
            }
        }
        if (isInside(mouseX, mouseY, x + 6, y + height - 14, width - 12, 12)) {
            return Component.literal("Mouse wheel to scroll");
        }
        return null;
    }

    private String stationName(UUID stationId, String fallbackName) {
        return AirshipStationRegistry.snapshot(stationId)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
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
        if (routesOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleRouteClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isInside(mouseX, mouseY, this.leftPos + SHIP_BOX_X, this.topPos + SHIP_BOX_Y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT)) {
            if (shipDropdownOpen) {
                closeShipDropdown();
            } else {
                openShipDropdown();
            }
            return true;
        }
        if (shipDropdownOpen && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleShipDropdownClick(mouseX, mouseY)) {
                return true;
            }
            closeShipDropdown();
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
        int filterBoxX = x + width - ROUTES_FILTER_BOX_WIDTH - 8;
        int filterBoxY = y + 4;
        int rowY = y + (ROUTES_ROW_Y - ROUTES_POPUP_Y);
        int clipBottom = y + ROUTES_FOOTER_Y - 2;
        var allRoutes = menu.routeChoiceSummaries(this.minecraft.player);
        List<RouteFilterOption> filterOptions = routeFilterOptions();
        clampRouteFilterState(filterOptions);
        var routes = filteredRouteSummaries(allRoutes, filterOptions);
        int routeCount = routes.size();
        int maxScroll = Math.max(0, routeCount - ROUTES_VISIBLE_ROWS);

        if (isInside(mouseX, mouseY, filterBoxX, filterBoxY, ROUTES_FILTER_BOX_WIDTH, ROUTES_FILTER_BOX_HEIGHT)) {
            routeFilterDropdownOpen = !routeFilterDropdownOpen;
            return true;
        }
        if (routeFilterDropdownOpen) {
            if (handleRouteFilterDropdownClick(mouseX, mouseY, filterOptions, filterBoxX, filterBoxY)) {
                return true;
            }
            routeFilterDropdownOpen = false;
        }

        int showAllWidth = routeShowAllWidth();
        int showAllHeight = Math.max(1, routeShowAllHeight() - 2);
        int showAllX = x + width - showAllWidth - 8;
        int showAllY = y + ROUTES_FOOTER_Y - 1;
        if (isInside(mouseX, mouseY, showAllX, showAllY, showAllWidth, showAllHeight)) {
            if (routeCount <= 0) {
                return true;
            }
            if (matchesCurrentFilteredSelection(routes)) {
                routes.stream().map(AirshipStationMenu.RouteChoiceSummary::id).forEach(previewedRouteIds::remove);
            } else {
                routes.stream().map(AirshipStationMenu.RouteChoiceSummary::id).forEach(previewedRouteIds::add);
            }
            previewAllFilteredRoutes = matchesCurrentFilteredSelection(routes);
            sendRoutePreviewSelection();
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
                AirshipStationMenu.RouteChoiceSummary route = routes.get(routeIndex);
                UUID routeId = route.id();
                int fullRouteIndex = -1;
                for (int i = 0; i < allRoutes.size(); i++) {
                    if (allRoutes.get(i).id().equals(routeId)) {
                        fullRouteIndex = i;
                        break;
                    }
                }
                if (fullRouteIndex < 0) {
                    return true;
                }
                if (previewedRouteIds.contains(routeId)) {
                    previewedRouteIds.remove(routeId);
                } else {
                    previewedRouteIds.add(routeId);
                }
                previewAllFilteredRoutes = matchesCurrentFilteredSelection(routes);
                sendRoutePreviewSelection();
                return true;
            }
        }
        if (isInside(mouseX, mouseY, x - 4, y - 4, width + 8, height + 8)) {
            return true;
        }
        if (!isInside(mouseX, mouseY, x - 4, y - 4, width + 8, height + 8)) {
            routesOpen = false;
            routeFilterDropdownOpen = false;
            return true;
        }
        return false;
    }

    private boolean handleRouteFilterDropdownClick(
            double mouseX,
            double mouseY,
            List<RouteFilterOption> filterOptions,
            int x,
            int y
    ) {
        int dropdownY = y + ROUTES_FILTER_BOX_HEIGHT + 2;
        int visible = Math.min(ROUTES_FILTER_DROPDOWN_MAX_VISIBLE_ROWS, filterOptions.size());
        int height = Math.max(1, visible) * ROUTES_FILTER_ROW_HEIGHT + 4;
        if (!isInside(mouseX, mouseY, x, dropdownY, ROUTES_FILTER_BOX_WIDTH, height)) {
            return false;
        }
        int row = (int) ((mouseY - dropdownY - 3) / ROUTES_FILTER_ROW_HEIGHT);
        if (row >= 0 && row < visible) {
            int optionIndex = routeFilterDropdownScroll + row;
            if (optionIndex >= filterOptions.size()) {
                return false;
            }
            routeFilterIndex = optionIndex;
            routeFilterTransponderId = filterOptions.get(routeFilterIndex).transponderId();
            routeFilterDropdownOpen = false;
            routeScroll = 0;
            persistRouteFilterState();
            if (previewAllFilteredRoutes) {
                List<AirshipStationMenu.RouteChoiceSummary> allRoutes = this.minecraft == null || this.minecraft.player == null
                        ? List.of()
                        : menu.routeChoiceSummaries(this.minecraft.player);
                previewedRouteIds.clear();
                filteredRouteSummaries(allRoutes, filterOptions).stream()
                        .map(AirshipStationMenu.RouteChoiceSummary::id)
                        .forEach(previewedRouteIds::add);
                sendRoutePreviewSelection();
            }
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
        int visible = Math.min(SHIP_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
        int height = Math.max(1, visible) * SHIP_DROPDOWN_ROW_HEIGHT + 4;
        if (!isInside(mouseX, mouseY, x, y, SHIP_BOX_WIDTH, height)) {
            return false;
        }
        int row = (int) ((mouseY - y - 3) / SHIP_DROPDOWN_ROW_HEIGHT);
        if (row >= 0 && row < visible) {
            int optionIndex = shipDropdownScroll + row;
            if (optionIndex >= options.size()) {
                return false;
            }
            pressAction(AirshipStationMenu.ACTION_SELECT_SHIP_BASE + optionIndex);
            closeShipDropdown();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (routesOpen && this.minecraft != null && this.minecraft.player != null) {
            List<RouteFilterOption> filterOptions = routeFilterOptions();
            clampRouteFilterState(filterOptions);
            int popupX = this.leftPos + ROUTES_POPUP_X;
            int popupY = this.topPos + ROUTES_POPUP_Y;
            int filterBoxX = popupX + ROUTES_POPUP_W - ROUTES_FILTER_BOX_WIDTH - 8;
            int filterDropdownY = popupY + 4 + ROUTES_FILTER_BOX_HEIGHT + 2;
            int visibleFilters = Math.min(ROUTES_FILTER_DROPDOWN_MAX_VISIBLE_ROWS, filterOptions.size());
            int filterDropdownHeight = Math.max(1, visibleFilters) * ROUTES_FILTER_ROW_HEIGHT + 4;
            if (routeFilterDropdownOpen && isInside(mouseX, mouseY, filterBoxX, filterDropdownY, ROUTES_FILTER_BOX_WIDTH, filterDropdownHeight)) {
                int maxFilterScroll = Math.max(0, filterOptions.size() - visibleFilters);
                if (maxFilterScroll > 0) {
                    routeFilterDropdownScroll = Math.max(0, Math.min(maxFilterScroll, routeFilterDropdownScroll + (scrollY < 0 ? 1 : -1)));
                }
                return true;
            }
            int routeCount = filteredRouteSummaries(menu.routeChoiceSummaries(this.minecraft.player), filterOptions).size();
            int maxScroll = Math.max(0, routeCount - ROUTES_VISIBLE_ROWS);
            if (isInside(mouseX, mouseY, popupX, popupY, ROUTES_POPUP_W, ROUTES_POPUP_H)) {
                if (maxScroll > 0) {
                    routeScroll = Math.max(0, Math.min(maxScroll, routeScroll + (scrollY < 0 ? 1 : -1)));
                }
                return true;
            }
        }
        if (shipDropdownOpen && this.minecraft != null && this.minecraft.player != null) {
            var options = menu.shipChoices(this.minecraft.player);
            int visible = Math.min(SHIP_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
            int maxScroll = Math.max(0, options.size() - visible);
            int x = this.leftPos + SHIP_BOX_X;
            int y = this.topPos + SHIP_BOX_Y + SHIP_BOX_HEIGHT + 2;
            int height = Math.max(1, visible) * SHIP_DROPDOWN_ROW_HEIGHT + 4;
            if (isInside(mouseX, mouseY, x, y, SHIP_BOX_WIDTH, height)) {
                if (maxScroll > 0) {
                    shipDropdownScroll = Math.max(0, Math.min(maxScroll, shipDropdownScroll + (scrollY < 0 ? 1 : -1)));
                }
                return true;
            }
        }
        if (isShipSelectorHovered(mouseX, mouseY)) {
            pressAction(scrollY < 0
                    ? AirshipStationMenu.ACTION_SELECT_SHIP
                    : AirshipStationMenu.ACTION_SELECT_PREVIOUS_SHIP);
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
            if (routeFilterDropdownOpen) {
                routeFilterDropdownOpen = false;
                return true;
            }
            if (routesOpen) {
                routesOpen = false;
                return true;
            }
            if (shipDropdownOpen) {
                closeShipDropdown();
                return true;
            }
        }
        if (shipDropdownOpen && this.minecraft != null && this.minecraft.player != null) {
            var options = menu.shipChoices(this.minecraft.player);
            int visible = Math.min(SHIP_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
            int maxScroll = Math.max(0, options.size() - visible);
            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (shipDropdownScroll > 0) {
                    shipDropdownScroll--;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (shipDropdownScroll < maxScroll) {
                    shipDropdownScroll++;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
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
        boolean selectorReady = this.nameBox == null || !this.nameBox.isFocused();
        boolean selectorHovered = isShipSelectorHovered(lastMouseX, lastMouseY);
        if (selectorReady
                && Character.isLetterOrDigit(codePoint)
                && ((shipDropdownOpen && this.minecraft != null && this.minecraft.player != null)
                || (!shipDropdownOpen && selectorHovered))
                && handleShipTypeahead(codePoint)) {
            return true;
        }
        if (this.nameBox != null && this.nameBox.isFocused() && this.nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        persistRouteFilterState();
        saveName();
        super.removed();
    }

    private void restoreRouteFilterStateIfNeeded() {
        if (routeFilterStateRestored) {
            return;
        }
        routeFilterStateRestored = true;
        RouteFilterMemory memory = ROUTE_FILTER_MEMORY.get(this.menu.stationPos());
        if (memory == null) {
            return;
        }
        routeFilterTransponderId = memory.transponderId().orElse(null);
        routeFilterDropdownScroll = memory.dropdownScroll();
    }

    private void persistRouteFilterState() {
        ROUTE_FILTER_MEMORY.put(
                this.menu.stationPos().immutable(),
                new RouteFilterMemory(Optional.ofNullable(routeFilterTransponderId), routeFilterDropdownScroll)
        );
    }

    private void pressAction(int actionId) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (this.minecraft.player.isSpectator()) {
            PacketDistributor.sendToServer(new AirshipStationMenuActionPayload(
                    this.menu.containerId,
                    this.menu.stationPos(),
                    actionId
            ));
            return;
        }
        if (this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, actionId);
        }
    }

    public void clearPreviewedRouteSelection() {
        previewedRouteIds.clear();
        previewAllFilteredRoutes = false;
    }

    private void saveName() {
        if (this.minecraft != null && this.nameBox != null) {
            pendingSubmittedName = this.nameBox.getValue();
            PacketDistributor.sendToServer(new UpdateIdentityNamePayload(this.menu.stationPos(), pendingSubmittedName));
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

    private void toggleStationChunks() {
        if (this.minecraft == null || this.minecraft.player == null || !AutomatedLogisticsConfig.forceLoadStationChunks()) {
            return;
        }
        LogisticsClientOverlays.toggleStationChunks(
                this.menu.stationPos(),
                AutomatedLogisticsConfig.stationInteractionChunkRadius()
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
    }

    private void toggleCargoPreview() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        List<List<BlockPos>> cargoPreview = this.menu.cargoPreviewPositionGroups(this.minecraft.player);
        if (cargoPreview.isEmpty()) {
            LogisticsClientOverlays.clearCargo();
            return;
        }
        LogisticsClientOverlays.toggleCargoGroups(cargoPreview);
    }

    private List<Component> landingAreaTooltip() {
        return List.of(
                landingAreaTooltipTitle(),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.landing_area.tooltip")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
    }

    private Component landingAreaTooltipTitle() {
        return Component.translatable(
                LogisticsClientOverlays.isLandingAreaVisible(this.menu.stationPos())
                        ? "gui.create_aeronautics_automated_logistics.airship_station.hide_landing_area"
                        : "gui.create_aeronautics_automated_logistics.airship_station.show_landing_area"
        );
    }

    private List<Component> stationChunksTooltip() {
        if (!AutomatedLogisticsConfig.forceLoadStationChunks()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.loaded_chunks_disabled"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.loaded_chunks.tooltip")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
            );
        }
        return List.of(
                stationChunksTooltipTitle(),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.loaded_chunks.tooltip")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
    }

    private Component stationChunksTooltipTitle() {
        if (!AutomatedLogisticsConfig.forceLoadStationChunks()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.loaded_chunks_disabled");
        }
        return Component.translatable(
                LogisticsClientOverlays.isStationChunksVisible(
                        this.menu.stationPos(),
                        AutomatedLogisticsConfig.stationInteractionChunkRadius()
                )
                        ? "gui.create_aeronautics_automated_logistics.airship_station.hide_loaded_chunks"
                        : "gui.create_aeronautics_automated_logistics.airship_station.show_loaded_chunks"
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

    private Component dockClearTooltip() {
        return Component.translatable(
                DockLinkPromptClientState.isPendingForStation(this.menu.stationPos())
                        ? "gui.create_aeronautics_automated_logistics.dock.cancel"
                        : "gui.create_aeronautics_automated_logistics.dock.clear"
        );
    }

    private Component cargoPreviewTooltip() {
        boolean visible = this.minecraft != null
                && this.minecraft.player != null
                && LogisticsClientOverlays.isCargoVisibleGroups(this.menu.cargoPreviewPositionGroups(this.minecraft.player));
        return Component.translatable(
                visible
                        ? "gui.create_aeronautics_automated_logistics.cargo.hide"
                        : "gui.create_aeronautics_automated_logistics.cargo.show"
        );
    }

    private Component cargoClearTooltip() {
        return Component.translatable(
                CargoLinkPromptClientState.isPendingForStation(this.menu.stationPos())
                        ? "gui.create_aeronautics_automated_logistics.cargo.cancel"
                        : "gui.create_aeronautics_automated_logistics.cargo.clear"
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
        if (status.equalsIgnoreCase("Cargo Check") || status.equalsIgnoreCase("Blocked") || status.equalsIgnoreCase("Route Problem")
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

    private boolean isShipSelectorHovered(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, this.leftPos + SHIP_BOX_X, this.topPos + SHIP_BOX_Y, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT);
    }

    private void openShipDropdown() {
        shipDropdownOpen = true;
        routesOpen = false;
        routeFilterDropdownOpen = false;
        shipTypeaheadBuffer = "";
        shipTypeaheadExpiryMs = 0L;
        ensureSelectedShipVisible();
    }

    private void closeShipDropdown() {
        shipDropdownOpen = false;
        shipTypeaheadBuffer = "";
        shipTypeaheadExpiryMs = 0L;
    }

    private void ensureSelectedShipVisible() {
        if (this.minecraft == null || this.minecraft.player == null) {
            shipDropdownScroll = 0;
            return;
        }
        var options = menu.shipChoices(this.minecraft.player);
        int selectedIndex = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).selected()) {
                selectedIndex = i;
                break;
            }
        }
        int visible = Math.min(SHIP_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
        int maxScroll = Math.max(0, options.size() - visible);
        if (selectedIndex < 0) {
            shipDropdownScroll = Math.max(0, Math.min(maxScroll, shipDropdownScroll));
            return;
        }
        if (selectedIndex < shipDropdownScroll) {
            shipDropdownScroll = selectedIndex;
        } else if (selectedIndex >= shipDropdownScroll + visible) {
            shipDropdownScroll = selectedIndex - visible + 1;
        }
        shipDropdownScroll = Math.max(0, Math.min(maxScroll, shipDropdownScroll));
    }

    private boolean handleShipTypeahead(char codePoint) {
        if (!Character.isLetterOrDigit(codePoint) || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now > shipTypeaheadExpiryMs) {
            shipTypeaheadBuffer = "";
        }
        shipTypeaheadBuffer += Character.toLowerCase(codePoint);
        shipTypeaheadExpiryMs = now + SHIP_TYPEAHEAD_TIMEOUT_MS;

        var options = menu.shipChoices(this.minecraft.player);
        int matchIndex = -1;
        for (int i = 0; i < options.size(); i++) {
            String shipName = options.get(i).shipName().getString().toLowerCase();
            if (shipName.startsWith(shipTypeaheadBuffer)) {
                matchIndex = i;
                break;
            }
        }
        if (matchIndex < 0 && shipTypeaheadBuffer.length() > 1) {
            shipTypeaheadBuffer = Character.toString(Character.toLowerCase(codePoint));
            shipTypeaheadExpiryMs = now + SHIP_TYPEAHEAD_TIMEOUT_MS;
            for (int i = 0; i < options.size(); i++) {
                String shipName = options.get(i).shipName().getString().toLowerCase();
                if (shipName.startsWith(shipTypeaheadBuffer)) {
                    matchIndex = i;
                    break;
                }
            }
        }
        if (matchIndex < 0) {
            return true;
        }
        pressAction(AirshipStationMenu.ACTION_SELECT_SHIP_BASE + matchIndex);
        if (!shipDropdownOpen) {
            return true;
        }
        int visible = Math.min(SHIP_DROPDOWN_MAX_VISIBLE_ROWS, options.size());
        int maxScroll = Math.max(0, options.size() - visible);
        if (matchIndex < shipDropdownScroll) {
            shipDropdownScroll = matchIndex;
        } else if (matchIndex >= shipDropdownScroll + visible) {
            shipDropdownScroll = matchIndex - visible + 1;
        }
        shipDropdownScroll = Math.max(0, Math.min(maxScroll, shipDropdownScroll));
        return true;
    }

    private void renderScaledIcon(GuiGraphics guiGraphics, AllIcons icon, int x, int y, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        icon.render(guiGraphics, 0, 0);
        guiGraphics.pose().popPose();
    }

    private java.util.Optional<String> routeInvalidReason(AirshipStationMenu.RouteChoiceSummary route, Level level) {
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

    private record RouteFilterOption(UUID transponderId, String label) {
    }

    private record RouteFilterMemory(Optional<UUID> transponderId, int dropdownScroll) {
    }

    private record ButtonTooltip(IconButton button, List<Component> lines) {
    }
}

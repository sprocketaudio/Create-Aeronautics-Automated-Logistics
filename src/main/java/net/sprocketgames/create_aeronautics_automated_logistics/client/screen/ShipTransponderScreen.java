package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.data.IntAttached;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.CargoLinkPromptClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.DockLinkPromptClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.MenuActionBarClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.CancelTransponderRouteRecordingPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.FinishTransponderRouteRecordingPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.OpenInstalledScheduleEditorPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.StartTransponderRouteRecordingPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationPermissionService;
import org.lwjgl.glfw.GLFW;

public class ShipTransponderScreen extends AbstractContainerScreen<ShipTransponderMenu> {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 178;
    private static final int INVENTORY_X = 9;
    private static final int INVENTORY_Y = 180;
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/transponder.png");
    private static final ResourceLocation RECORDING_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/transponder_record.png");
    private static final ResourceLocation ROUTE_SELECTION_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/route_select.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");
    private static final ResourceLocation CREATE_WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final int SCHEDULE_LABEL_X = 36;
    private static final int SCHEDULE_LABEL_Y = 91;
    private static final int CONTROL_LABEL_X = 36;
    private static final int CONTROL_LABEL_Y = 66;
    private static final int CONTROL_BUTTON_Y = 56;
    private static final int DOCK_LABEL_X = 36;
    private static final int DOCK_LABEL_Y = 110;
    private static final int CARGO_LABEL_X = 36;
    private static final int CARGO_LABEL_Y = 124;
    private static final int DOCK_BUTTON_RIGHT_X = 143;
    private static final int DOCK_BUTTON_LEFT_X = 129;
    private static final int CARGO_BUTTON_RIGHT_X = 143;
    private static final int CARGO_BUTTON_LEFT_X = 129;
    private static final int STATUS_FOOTER_X = 60;
    private static final int STATUS_FOOTER_Y = 159;
    private static final int STATUS_FOOTER_WIDTH = 118;
    private static final ScreenElement SHOW_ROUTE_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 176, 16, 16, 16, 256);
    private static final ScreenElement SHOW_DOCK_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), 64, 192, 16, 16, 256);
    private static final ScreenElement SHOW_CARGO_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png"), -1, 176, 15, 15, 256, 1.0F, 16);
    private static final ScreenElement EDIT_SCHEDULE_ICON =
            new AtlasIcon(ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/transponder.png"), 16, 240, 16, 16, 256, 1.0F, 16);
    private static final int MODE_TOGGLE_U = 219;
    private static final int MODE_TOGGLE_LEFT_V = 19;
    private static final int MODE_TOGGLE_RIGHT_V = 27;
    private static final int MODE_TOGGLE_SOURCE_WIDTH = 12;
    private static final int MODE_TOGGLE_SOURCE_HEIGHT = 7;
    private static final int MODE_TOGGLE_DRAW_WIDTH = 14;
    private static final int MODE_TOGGLE_DRAW_HEIGHT = 8;
    private static final int MODE_TOGGLE_X = 170;
    private static final int MODE_TOGGLE_Y = 21;
    private static final float MINI_BUTTON_SCALE = 2.0F / 3.0F;
    private static final int RECORD_LABEL_X = 36;
    private static final int RECORD_LABEL_Y = 66;
    private static final int RECORD_BUTTON_Y = 56;
    private static final int FOOTER_ROUTE_BUTTON_X = 17;
    private static final int FOOTER_DOCK_BUTTON_X = 38;
    private static final int FOOTER_CARGO_BUTTON_X = 59;
    private static final int ROUTE_SELECTION_WIDTH = 206;
    private static final int ROUTE_SELECTION_HEIGHT = 101;
    private static final int ROUTE_SELECTION_Y = 43;
    private static final int ROUTE_SELECTION_TITLE_Y = 3;
    private static final int ROUTE_SELECTION_LABEL_X = 15;
    private static final int ROUTE_SELECTION_ORIGIN_Y = 33;
    private static final int ROUTE_SELECTION_DESTINATION_Y = 55;
    private static final int ROUTE_SELECTION_BOX_X = 86;
    private static final int ROUTE_SELECTION_BOX_WIDTH = 88;
    private static final int ROUTE_SELECTION_BOX_HEIGHT = 12;
    private static final int ROUTE_SELECTION_CONFIRM_OFFSET_RIGHT = 33;
    private static final int ROUTE_SELECTION_CONFIRM_OFFSET_BOTTOM = 24;
    private static final int ORIGIN_SELECTOR_X_OFFSET = 2;
    private static final int ORIGIN_SELECTOR_Y_OFFSET = -3;
    private static final int DEST_SELECTOR_X_OFFSET = 2;
    private static final int DEST_SELECTOR_Y_OFFSET = -4;
    private static final int ORIGIN_DROPDOWN_X_OFFSET = 1;
    private static final int ORIGIN_DROPDOWN_Y_OFFSET = -2;
    private static final int DEST_DROPDOWN_X_OFFSET = 2;
    private static final int DEST_DROPDOWN_Y_OFFSET = -1;

    private final List<ButtonTooltip> buttonTooltips = new ArrayList<>();
    private EditBox nameBox;
    private IconButton playButton;
    private IconButton stopButton;
    private IconButton recordButton;
    private IconButton recordCancelButton;
    private IconButton previewButton;
    private IconButton dockPreviewButton;
    private IconButton cargoPreviewButton;
    private IconButton scheduleEditButton;
    private MiniIconButton dockLinkButton;
    private MiniIconButton dockClearButton;
    private MiniIconButton cargoLinkButton;
    private MiniIconButton cargoClearButton;
    private boolean recordingMode;
    private boolean recordingSessionActive;
    private boolean routeSelectionOpen;
    private boolean pulseRecordingModeHint;
    private EditBox originStationBox;
    private EditBox destinationStationBox;
    private StationSuggestions originStationSuggestions;
    private StationSuggestions destinationStationSuggestions;
    private Optional<UUID> recordingDestinationStationId = Optional.empty();
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
        recordingMode = this.menu.initialRecordingMode();
        recordingSessionActive = this.menu.initialRecordingSessionActive();
        recordingDestinationStationId = this.menu.initialRecordingDestinationStationId();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.menu.primeClientRuntimeState(this.minecraft.player);
        }
        if (recordingSessionActive) {
            recordingMode = true;
        }

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

        int buttonY = this.topPos + CONTROL_BUTTON_Y;
        playButton = addIconButton(
                this.leftPos + 111,
                buttonY,
                AllIcons.I_PLAY,
                this::startInstalledSchedule,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.start_schedule")
        );
        stopButton = addIconButton(
                this.leftPos + 137,
                buttonY,
                AllIcons.I_STOP,
                () -> pressAction(ShipTransponderMenu.ACTION_STOP_SCHEDULE),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.stop_schedule"),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_1"),
                Component.translatable("tooltip.create_aeronautics_automated_logistics.schedule_stop.warning_2")
        );
        previewButton = addIconButton(
                this.leftPos + FOOTER_ROUTE_BUTTON_X,
                this.topPos + 154,
                SHOW_ROUTE_ICON,
                this::togglePreview,
                routePreviewButtonText()
        );
        scheduleEditButton = addIconButton(
                this.leftPos + 137,
                this.topPos + 83,
                EDIT_SCHEDULE_ICON,
                this::openInstalledScheduleEditor,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.edit_schedule.tooltip"),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.edit_schedule.tooltip_skip")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        );
        dockPreviewButton = addIconButton(
                this.leftPos + FOOTER_DOCK_BUTTON_X,
                this.topPos + 154,
                SHOW_DOCK_ICON,
                this::toggleDockPreview,
                dockPreviewTooltip()
        );
        cargoPreviewButton = addIconButton(
                this.leftPos + FOOTER_CARGO_BUTTON_X,
                this.topPos + 154,
                SHOW_CARGO_ICON,
                this::toggleCargoPreview,
                cargoPreviewTooltip()
        );
        addIconButton(
                this.leftPos + 167,
                this.topPos + 154,
                AllIcons.I_CONFIRM,
                this::onClose,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.close.tooltip")
        );
        dockLinkButton = addMiniIconButton(
                this.leftPos + DOCK_BUTTON_LEFT_X,
                this.topPos + 108,
                AllIcons.I_ADD,
                () -> pressAction(ShipTransponderMenu.ACTION_BEGIN_LINK_DOCK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.link")
        );
        dockClearButton = addMiniIconButton(
                this.leftPos + DOCK_BUTTON_RIGHT_X,
                this.topPos + 108,
                AllIcons.I_MTD_CLOSE,
                () -> pressAction(ShipTransponderMenu.ACTION_CLEAR_DOCK_LINK),
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.clear")
        );
        cargoLinkButton = addMiniIconButton(
                this.leftPos + CARGO_BUTTON_LEFT_X,
                this.topPos + 122,
                AllIcons.I_ADD,
                () -> pressAction(ShipTransponderMenu.ACTION_LINK_CARGO),
                Component.translatable("gui.create_aeronautics_automated_logistics.cargo.link")
        );
        cargoClearButton = addMiniIconButton(
                this.leftPos + CARGO_BUTTON_RIGHT_X,
                this.topPos + 122,
                AllIcons.I_MTD_CLOSE,
                () -> pressAction(ShipTransponderMenu.ACTION_CLEAR_CARGO),
                Component.translatable("gui.create_aeronautics_automated_logistics.cargo.clear")
        );
        recordButton = addIconButton(
                this.leftPos + 111,
                this.topPos + RECORD_BUTTON_Y,
                AllIcons.I_ADD,
                this::toggleRecordingSession,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.record_start")
        );
        recordCancelButton = addIconButton(
                this.leftPos + 137,
                this.topPos + RECORD_BUTTON_Y,
                AllIcons.I_STOP,
                this::cancelRecordingSession,
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.record_cancel")
        );

        originStationBox = createRouteStationBox(ROUTE_SELECTION_ORIGIN_Y, ORIGIN_SELECTOR_X_OFFSET, ORIGIN_SELECTOR_Y_OFFSET);
        destinationStationBox = createRouteStationBox(ROUTE_SELECTION_DESTINATION_Y, DEST_SELECTOR_X_OFFSET, DEST_SELECTOR_Y_OFFSET);
        originStationSuggestions = createStationSuggestions(originStationBox, ORIGIN_DROPDOWN_X_OFFSET, ORIGIN_DROPDOWN_Y_OFFSET);
        destinationStationSuggestions = createStationSuggestions(destinationStationBox, DEST_DROPDOWN_X_OFFSET, DEST_DROPDOWN_Y_OFFSET);
        originStationBox.visible = false;
        originStationBox.active = false;
        destinationStationBox.visible = false;
        destinationStationBox.active = false;
        addRenderableWidget(originStationBox);
        addRenderableWidget(destinationStationBox);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (nameBox != null && !nameBox.isFocused()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                String authoritativeName = this.menu.shipName(this.minecraft.player);
                if (!nameBox.getValue().equals(authoritativeName)) {
                    nameBox.setValue(authoritativeName);
                }
            }
            nameBox.setCursorPosition(nameBox.getValue().length());
            nameBox.setHighlightPos(nameBox.getCursorPosition());
        }
        centerNameBox();
        if (routeSelectionOpen) {
            if (originStationSuggestions != null) {
                originStationSuggestions.tick();
                originStationSuggestions.updateCommandInfo();
            }
            if (destinationStationSuggestions != null) {
                destinationStationSuggestions.tick();
                destinationStationSuggestions.updateCommandInfo();
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation panelTexture = recordingMode ? RECORDING_BACKGROUND : BACKGROUND;
        guiGraphics.blit(panelTexture, this.leftPos, this.topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
        renderModeToggle(guiGraphics);
        if (!routeSelectionOpen) {
            guiGraphics.blit(PLAYER_INVENTORY, this.leftPos + INVENTORY_X, this.topPos + INVENTORY_Y, 0, 0, 176, 108);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        boolean showScheduleButtons = !recordingMode;
        boolean scheduleRunning = this.minecraft != null
                && this.minecraft.player != null
                && menu.isScheduleRunning(this.minecraft.player);
        boolean scheduleHeld = this.minecraft != null
                && this.minecraft.player != null
                && menu.isScheduleHeld(this.minecraft.player);
        boolean runControlsLockedByRecording = recordingSessionActive;
        if (playButton != null) {
            playButton.visible = showScheduleButtons;
            playButton.active = showScheduleButtons && !runControlsLockedByRecording && (!scheduleRunning || scheduleHeld);
            playButton.green = showScheduleButtons && scheduleRunning;
        }
        if (stopButton != null) {
            stopButton.visible = showScheduleButtons;
            stopButton.active = showScheduleButtons && !runControlsLockedByRecording && scheduleRunning;
        }
        if (previewButton != null) {
            previewButton.visible = showScheduleButtons;
            previewButton.active = showScheduleButtons
                    && this.minecraft != null
                    && this.minecraft.player != null
                    && menu.canControlTransponderLocally(this.minecraft.player)
                    && menu.canPreviewOwnedRoute(this.minecraft.player);
        }
        if (scheduleEditButton != null) {
            scheduleEditButton.visible = true;
            scheduleEditButton.active = this.minecraft != null
                    && this.minecraft.player != null
                    && menu.hasOwnedStops(this.minecraft.player);
        }
        if (recordButton != null) {
            recordButton.visible = recordingMode;
            recordButton.active = recordingMode;
            recordButton.setIcon(recordingSessionActive ? AllIcons.I_CONFIG_SAVE : AllIcons.I_ADD);
            recordButton.green = recordingSessionActive;
        }
        if (recordCancelButton != null) {
            recordCancelButton.visible = recordingMode;
            recordCancelButton.active = recordingMode && recordingSessionActive;
        }
        if (originStationBox != null) {
            originStationBox.visible = routeSelectionOpen;
            originStationBox.active = routeSelectionOpen;
        }
        if (destinationStationBox != null) {
            destinationStationBox.visible = routeSelectionOpen;
            destinationStationBox.active = routeSelectionOpen;
        }
        if (dockPreviewButton != null) {
            dockPreviewButton.visible = !recordingMode;
            dockPreviewButton.active = !recordingMode
                    && this.minecraft != null
                    && this.minecraft.player != null
                    && menu.dockPreviewPos(this.minecraft.player).isPresent();
        }
        if (cargoPreviewButton != null) {
            cargoPreviewButton.visible = !recordingMode;
            cargoPreviewButton.active = !recordingMode
                    && this.minecraft != null
                    && this.minecraft.player != null
                    && !menu.cargoPreviewPositionGroups(this.minecraft.player).isEmpty();
        }
        if (dockLinkButton != null) {
            dockLinkButton.visible = !recordingMode;
            dockLinkButton.active = !recordingMode;
        }
        if (dockClearButton != null) {
            dockClearButton.visible = !recordingMode;
            dockClearButton.active = !recordingMode
                    && this.minecraft != null
                    && this.minecraft.player != null
                    && menu.dockPreviewPos(this.minecraft.player).isPresent();
        }
        if (cargoLinkButton != null) {
            cargoLinkButton.visible = !recordingMode;
            cargoLinkButton.active = !recordingMode;
        }
        if (cargoClearButton != null) {
            cargoClearButton.visible = !recordingMode;
            cargoClearButton.active = !recordingMode
                    && this.minecraft != null
                    && this.minecraft.player != null
                    && menu.hasLinkedCargo(this.minecraft.player);
        }
        if (previewButton != null) {
            if (!previewButton.active) {
                LogisticsClientOverlays.clearFlightPath();
            }
            previewButton.green = LogisticsClientOverlays.hasFlightPath();
        }
        if (!recordingMode && dockPreviewButton != null && this.minecraft != null && this.minecraft.player != null) {
            boolean hasLinkedDock = menu.dockPreviewPos(this.minecraft.player).isPresent();
            if (!hasLinkedDock) {
                LogisticsClientOverlays.clearDock();
            }
            dockPreviewButton.active = hasLinkedDock;
            dockPreviewButton.green = menu.dockPreviewPos(this.minecraft.player)
                    .map(LogisticsClientOverlays::isDockVisible)
                    .orElse(false);
        }
        if (!recordingMode && dockClearButton != null && this.minecraft != null && this.minecraft.player != null) {
            boolean pendingDockLink = DockLinkPromptClientState.isPendingForTransponder(this.menu.transponderPos());
            dockClearButton.active = pendingDockLink || menu.dockPreviewPos(this.minecraft.player).isPresent();
            dockClearButton.green = pendingDockLink;
        }
        if (!recordingMode && cargoPreviewButton != null && this.minecraft != null && this.minecraft.player != null) {
            List<List<BlockPos>> cargoPreview = menu.cargoPreviewPositionGroups(this.minecraft.player);
            if (cargoPreview.isEmpty()) {
                LogisticsClientOverlays.clearCargo();
            }
            cargoPreviewButton.active = !cargoPreview.isEmpty();
            cargoPreviewButton.green = LogisticsClientOverlays.isCargoVisibleGroups(cargoPreview);
        }
        if (!recordingMode && cargoClearButton != null && this.minecraft != null && this.minecraft.player != null) {
            boolean pendingCargoLink = CargoLinkPromptClientState.isPendingForTransponder(this.menu.transponderPos());
            cargoClearButton.active = pendingCargoLink || menu.hasLinkedCargo(this.minecraft.player);
            cargoClearButton.green = pendingCargoLink;
        }
        if (routeSelectionOpen) {
            if (nameBox != null) {
                nameBox.setFocused(false);
                nameBox.setVisible(false);
                nameBox.setEditable(false);
            }
            if (playButton != null) {
                playButton.active = false;
            }
            if (stopButton != null) {
                stopButton.active = false;
            }
            if (previewButton != null) {
                previewButton.active = false;
            }
            if (scheduleEditButton != null) {
                scheduleEditButton.active = false;
            }
            if (dockPreviewButton != null) {
                dockPreviewButton.active = false;
            }
            if (cargoPreviewButton != null) {
                cargoPreviewButton.active = false;
            }
            if (dockLinkButton != null) {
                dockLinkButton.active = false;
            }
            if (dockClearButton != null) {
                dockClearButton.active = false;
            }
            if (cargoLinkButton != null) {
                cargoLinkButton.active = false;
            }
            if (cargoClearButton != null) {
                cargoClearButton.active = false;
            }
            if (recordButton != null) {
                recordButton.active = false;
            }
            if (recordCancelButton != null) {
                recordCancelButton.active = false;
            }
            this.hoveredSlot = null;
        } else if (nameBox != null) {
            nameBox.setVisible(true);
            nameBox.setEditable(true);
        }
        int effectiveMouseX = routeSelectionOpen ? -10000 : mouseX;
        int effectiveMouseY = routeSelectionOpen ? -10000 : mouseY;
        super.render(guiGraphics, effectiveMouseX, effectiveMouseY, partialTick);
        renderHeaderEditIcon(guiGraphics);
        renderSubtitle(guiGraphics);
        renderMainPanel(guiGraphics);
        if (routeSelectionOpen) {
            guiGraphics.fill(
                    0,
                    0,
                    this.width,
                    this.height,
                    0x96000000
            );
            renderRouteSelection(guiGraphics, mouseX, mouseY);
        } else {
            renderHoveredTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (routeSelectionOpen) {
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (routeSelectionOpen) {
            return;
        }
        guiGraphics.drawString(this.font, this.playerInventoryTitle, INVENTORY_X + 8, INVENTORY_Y + 6, 0x505050, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (routeSelectionOpen) {
            return mouseClickedRouteSelection(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isInside(mouseX, mouseY, this.leftPos + MODE_TOGGLE_X, this.topPos + MODE_TOGGLE_Y, MODE_TOGGLE_DRAW_WIDTH, MODE_TOGGLE_DRAW_HEIGHT)) {
            toggleRecordingMode();
            return true;
        }
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
        if (routeSelectionOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRouteSelection();
                return true;
            }
            if (originStationSuggestions != null && originStationSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (destinationStationSuggestions != null && destinationStationSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (originStationBox != null && originStationBox.isFocused()) {
                return originStationBox.keyPressed(keyCode, scanCode, modifiers) || originStationBox.canConsumeInput();
            }
            if (destinationStationBox != null && destinationStationBox.isFocused()) {
                return destinationStationBox.keyPressed(keyCode, scanCode, modifiers) || destinationStationBox.canConsumeInput();
            }
            return true;
        }
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
        if (routeSelectionOpen) {
            if (originStationBox != null && originStationBox.isFocused() && originStationBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            if (destinationStationBox != null && destinationStationBox.isFocused() && destinationStationBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            return true;
        }
        if (this.nameBox != null && this.nameBox.isFocused() && this.nameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (routeSelectionOpen) {
            if (originStationSuggestions != null && originStationSuggestions.mouseScrolled(scrollY)) {
                return true;
            }
            if (destinationStationSuggestions != null && destinationStationSuggestions.mouseScrolled(scrollY)) {
                return true;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        guiGraphics.blit(recordingMode ? RECORDING_BACKGROUND : BACKGROUND, iconX, this.topPos + 1, 0, 239, 15, 14, 256, 256);
    }

    private void renderSubtitle(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable(
                        recordingMode
                                ? "gui.create_aeronautics_automated_logistics.ship_transponder.subtitle.record_mode"
                                : "gui.create_aeronautics_automated_logistics.ship_transponder.subtitle.control_mode"
                ),
                this.leftPos + PANEL_WIDTH / 2,
                this.topPos + 21,
                0xFFE7D7B3
        );
    }

    private void renderMainPanel(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        int x = this.leftPos + SCHEDULE_LABEL_X;
        guiGraphics.drawString(this.font, "Stops:", x, this.topPos + SCHEDULE_LABEL_Y, 0xFF9EA5AA, false);
        if (recordingMode) {
            guiGraphics.drawString(this.font, "Record:", this.leftPos + RECORD_LABEL_X, this.topPos + RECORD_LABEL_Y, 0xFF9EA5AA, false);
        } else {
            guiGraphics.drawString(this.font, "Run:", this.leftPos + CONTROL_LABEL_X, this.topPos + CONTROL_LABEL_Y, 0xFF9EA5AA, false);
        }

        Component status = recordingSessionActive
                ? Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_recording")
                : menu.runtimeStateText(this.minecraft.player);
        int statusY = this.topPos + STATUS_FOOTER_Y;
        int statusFooterX = this.leftPos + STATUS_FOOTER_X;
        String statusText = shortText(status, STATUS_FOOTER_WIDTH);
        statusValueX = statusFooterX;
        statusValueY = statusY;
        statusValueWidth = STATUS_FOOTER_WIDTH;
        statusValueHeight = this.font.lineHeight;
        int statusTextWidth = this.font.width(statusText);
        int centeredStatusX = statusFooterX + Math.max(0, (STATUS_FOOTER_WIDTH - statusTextWidth) / 2);
        int statusColor = menu.runtimeStateColor(this.minecraft.player);
        if (recordingSessionActive && this.minecraft.level != null) {
            float phase = (this.minecraft.level.getGameTime() % 20L) / 20.0F;
            float pulse = 0.5F + 0.5F * Mth.sin(phase * (float) (Math.PI * 2.0));
            int red = 200 + (int) (55.0F * pulse);
            int greenBlue = 60 + (int) (25.0F * pulse);
            statusColor = (red << 16) | (greenBlue << 8) | greenBlue;
        }
        guiGraphics.drawString(this.font, statusText, centeredStatusX, statusY, statusColor, false);

        if (!recordingMode) {
            Component dock = menu.dockCompactText(this.minecraft.player);
            int dockY = this.topPos + DOCK_LABEL_Y;
            guiGraphics.drawString(this.font, "Dock:", this.leftPos + DOCK_LABEL_X, dockY, 0xFF9EA5AA, false);
            int linkValueAreaLeft = this.leftPos + DOCK_LABEL_X + Math.max(this.font.width("Dock: "), this.font.width("Cargo: ")) + 2;
            int linkValueAreaWidth = (this.leftPos + DOCK_BUTTON_LEFT_X - 4) - linkValueAreaLeft;
            String dockText = shortText(dock, Math.max(1, linkValueAreaWidth));
            int dockValueDrawX = linkValueAreaLeft + Math.max(0, (linkValueAreaWidth - this.font.width(dockText)) / 2);
            dockValueX = Math.max(linkValueAreaLeft, dockValueDrawX);
            dockValueY = dockY;
            dockValueWidth = this.font.width(dockText);
            dockValueHeight = this.font.lineHeight;
            guiGraphics.drawString(this.font, dockText, dockValueX, dockY, menu.dockStatusColor(this.minecraft.player), false);

            Component cargo = menu.cargoCompactText(this.minecraft.player);
            int cargoY = this.topPos + CARGO_LABEL_Y;
            guiGraphics.drawString(this.font, "Cargo:", this.leftPos + CARGO_LABEL_X, cargoY, 0xFF9EA5AA, false);
            String cargoText = shortText(cargo, Math.max(1, linkValueAreaWidth));
            int cargoValueDrawX = linkValueAreaLeft + Math.max(0, (linkValueAreaWidth - this.font.width(cargoText)) / 2);
            cargoValueX = Math.max(linkValueAreaLeft, cargoValueDrawX);
            cargoValueY = cargoY;
            cargoValueWidth = this.font.width(cargoText);
            cargoValueHeight = this.font.lineHeight;
            guiGraphics.drawString(this.font, cargoText, cargoValueX, cargoY, menu.cargoStatusColor(this.minecraft.player), false);
        } else {
            dockValueX = dockValueY = dockValueWidth = dockValueHeight = 0;
            cargoValueX = cargoValueY = cargoValueWidth = cargoValueHeight = 0;
        }
    }

    private void renderHoveredTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTooltip(guiGraphics, mouseX, mouseY);
        if (recordButton != null && recordButton.isHovered()) {
            guiGraphics.renderTooltip(
                    this.font,
                    List.of(recordingSessionActive ? recordSaveTooltip() : recordStartTooltip()),
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (previewButton != null && previewButton.isHovered()) {
            guiGraphics.renderTooltip(this.font, List.of(routePreviewButtonText()), java.util.Optional.empty(), mouseX, mouseY);
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
            if (tooltip.button() == recordButton
                    || tooltip.button() == previewButton
                    || tooltip.button() == dockPreviewButton
                    || tooltip.button() == cargoPreviewButton
                    || tooltip.button() == dockClearButton
                    || tooltip.button() == cargoClearButton) {
                continue;
            }
            if (tooltip.button().isHovered() && !tooltip.lines().isEmpty()) {
                guiGraphics.renderTooltip(this.font, tooltip.lines(), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            if (isInside(mouseX, mouseY, this.leftPos + MODE_TOGGLE_X, this.topPos + MODE_TOGGLE_Y, MODE_TOGGLE_DRAW_WIDTH, MODE_TOGGLE_DRAW_HEIGHT)) {
                guiGraphics.renderTooltip(this.font, List.of(recordingModeTooltip()), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
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
            if (isInside(mouseX, mouseY, cargoValueX, cargoValueY, Math.max(1, cargoValueWidth), Math.max(1, cargoValueHeight))) {
                guiGraphics.renderTooltip(this.font, menu.cargoTooltip(this.minecraft.player), java.util.Optional.empty(), mouseX, mouseY);
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
            return;
        }
        pressAction(ShipTransponderMenu.ACTION_TOGGLE_PREVIEW);
    }

    private void toggleDockPreview() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        menu.dockPreviewPos(this.minecraft.player).ifPresentOrElse(
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

    public boolean isRecordingMode() {
        return recordingMode;
    }

    private Component dockClearTooltip() {
        return Component.translatable(
                DockLinkPromptClientState.isPendingForTransponder(this.menu.transponderPos())
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
                CargoLinkPromptClientState.isPendingForTransponder(this.menu.transponderPos())
                        ? "gui.create_aeronautics_automated_logistics.cargo.cancel"
                        : "gui.create_aeronautics_automated_logistics.cargo.clear"
        );
    }

    private void openInstalledScheduleEditor() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        PacketDistributor.sendToServer(new OpenInstalledScheduleEditorPayload(this.menu.transponderPos(), recordingMode));
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
                && menu.dockPreviewPos(this.minecraft.player)
                .map(LogisticsClientOverlays::isDockVisible)
                .orElse(false);
        return Component.translatable(
                visible
                        ? "gui.create_aeronautics_automated_logistics.dock.hide"
                        : "gui.create_aeronautics_automated_logistics.dock.show"
        );
    }

    private void toggleRecordingMode() {
        recordingMode = !recordingMode;
        if (recordingMode) {
            pulseRecordingModeHint = false;
        }
    }

    private Component recordingModeTooltip() {
        return Component.translatable(
                recordingMode
                        ? "gui.create_aeronautics_automated_logistics.ship_transponder.switch_to_run_mode"
                        : "gui.create_aeronautics_automated_logistics.ship_transponder.switch_to_record_mode"
        );
    }

    private Component recordStartTooltip() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.record_start");
    }

    private Component recordSaveTooltip() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.record_save");
    }

    private void toggleRecordingSession() {
        if (recordingSessionActive) {
            saveTransponderRecording();
        } else {
            openRouteSelection();
        }
    }

    private void cancelRecordingSession() {
        if (this.minecraft != null) {
            PacketDistributor.sendToServer(new CancelTransponderRouteRecordingPayload(this.menu.transponderPos()));
        }
        recordingSessionActive = false;
        recordingDestinationStationId = Optional.empty();
    }

    public void setRecordingSessionActive(boolean active) {
        setRecordingSessionActive(active, Optional.empty());
    }

    public void setRecordingSessionActive(boolean active, Optional<UUID> destinationStationId) {
        this.recordingSessionActive = active;
        if (active) {
            this.recordingMode = true;
            this.pulseRecordingModeHint = false;
            this.recordingDestinationStationId = destinationStationId == null ? Optional.empty() : destinationStationId;
        }
        if (!active) {
            this.recordingDestinationStationId = Optional.empty();
        }
    }

    @Override
    public void onClose() {
        pulseRecordingModeHint = false;
        super.onClose();
    }

    private EditBox createRouteStationBox(int rowY, int selectorXOffset, int selectorYOffset) {
        int popupLeft = routeSelectionLeft();
        EditBox box = new ClippedEditBox(
                new NoShadowFontWrapper(this.font),
                popupLeft + ROUTE_SELECTION_BOX_X + selectorXOffset,
                this.topPos + ROUTE_SELECTION_Y + rowY - 2 + selectorYOffset,
                ROUTE_SELECTION_BOX_WIDTH,
                ROUTE_SELECTION_BOX_HEIGHT,
                Component.empty()
        );
        box.setBordered(false);
        box.setMaxLength(64);
        box.setTextColor(0xE8EDF6);
        return box;
    }

    private StationSuggestions createStationSuggestions(EditBox targetBox, int dropdownXOffset, int dropdownYOffset) {
        if (targetBox == null) {
            return null;
        }
        StationSuggestions suggestions = new StationSuggestions(targetBox, viableStations(), dropdownXOffset, dropdownYOffset);
        suggestions.updateCommandInfo();
        return suggestions;
    }

    private List<IntAttached<String>> viableStations() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return List.of();
        }
        Vec3 playerPosition = this.minecraft.player.position();
        Set<String> seen = new HashSet<>();
        return accessibleStations().stream()
                .filter(station -> seen.add(station.stationName()))
                .map(station -> IntAttached.with(
                        (int) Vec3.atCenterOf(station.stationPos()).distanceTo(playerPosition),
                        station.stationName()))
                .toList();
    }

    private void openRouteSelection() {
        if (!recordingMode) {
            return;
        }
        routeSelectionOpen = true;
        originStationSuggestions = createStationSuggestions(originStationBox, ORIGIN_DROPDOWN_X_OFFSET, ORIGIN_DROPDOWN_Y_OFFSET);
        destinationStationSuggestions = createStationSuggestions(destinationStationBox, DEST_DROPDOWN_X_OFFSET, DEST_DROPDOWN_Y_OFFSET);
        if (originStationBox != null) {
            originStationBox.setFocused(true);
            originStationBox.visible = true;
            originStationBox.active = true;
            setFocused(originStationBox);
            if (originStationSuggestions != null) {
                originStationSuggestions.forceShow();
            }
        }
        if (destinationStationBox != null) {
            destinationStationBox.visible = true;
            destinationStationBox.active = true;
        }
    }

    private void closeRouteSelection() {
        routeSelectionOpen = false;
        if (originStationBox != null) {
            originStationBox.setFocused(false);
            originStationBox.visible = false;
            originStationBox.active = false;
        }
        if (destinationStationBox != null) {
            destinationStationBox.setFocused(false);
            destinationStationBox.visible = false;
            destinationStationBox.active = false;
        }
        if (originStationSuggestions != null) {
            originStationSuggestions.dismiss();
        }
        if (destinationStationSuggestions != null) {
            destinationStationSuggestions.dismiss();
        }
        setFocused(null);
    }

    private void confirmRouteSelection() {
        Optional<AirshipStationSnapshot> origin = selectedStation(originStationBox);
        Optional<AirshipStationSnapshot> destination = selectedStation(destinationStationBox);
        if (origin.isEmpty() || destination.isEmpty()) {
            showLocalMessage(Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.choose_stations"));
            return;
        }
        this.recordingDestinationStationId = Optional.of(destination.get().stationId());
        PacketDistributor.sendToServer(new StartTransponderRouteRecordingPayload(
                this.menu.transponderPos(),
                origin.get().stationId(),
                destination.get().stationId()
        ));
        closeRouteSelection();
    }

    private Optional<AirshipStationSnapshot> selectedStation(EditBox box) {
        if (this.minecraft == null || this.minecraft.level == null || box == null) {
            return Optional.empty();
        }
        String value = box.getValue().trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        return accessibleStations().stream()
                .filter(station -> station.stationName().equals(value))
                .findFirst();
    }

    private List<AirshipStationSnapshot> accessibleStations() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return List.of();
        }
        boolean isOp = this.minecraft.player.hasPermissions(2);
        return AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .filter(station -> this.minecraft.level.getBlockEntity(station.stationPos()) instanceof AirshipStationBlockEntity blockEntity
                        && blockEntity.stationId().equals(station.stationId()))
                .filter(station -> StationPermissionService.canControl(this.minecraft.player.getUUID(), isOp, station))
                .toList();
    }

    private void saveTransponderRecording() {
        if (this.minecraft == null) {
            return;
        }
        if (recordingDestinationStationId.isEmpty()) {
            showLocalMessage(Component.translatable("message.create_aeronautics_automated_logistics.transponder_recording.destination_missing"));
            return;
        }
        PacketDistributor.sendToServer(new FinishTransponderRouteRecordingPayload(
                this.menu.transponderPos(),
                recordingDestinationStationId.get(),
                true
        ));
    }

    private void showLocalMessage(Component message) {
        if (this.minecraft != null) {
            MenuActionBarClientState.show(message, 60);
        }
    }

    private void renderRouteSelection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = routeSelectionLeft();
        int y = this.topPos + ROUTE_SELECTION_Y;
        guiGraphics.blit(
                ROUTE_SELECTION_BACKGROUND,
                x,
                y,
                0,
                0,
                ROUTE_SELECTION_WIDTH,
                ROUTE_SELECTION_HEIGHT,
                ROUTE_SELECTION_WIDTH,
                ROUTE_SELECTION_HEIGHT
        );
        Component title = Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.route_selection");
        int titleWidth = this.font.width(title);
        guiGraphics.drawString(
                this.font,
                title,
                x + (ROUTE_SELECTION_WIDTH - titleWidth) / 2,
                y + ROUTE_SELECTION_TITLE_Y,
                0xFF3D281C,
                false
        );
        guiGraphics.drawString(this.font, "Origin", x + ROUTE_SELECTION_LABEL_X, y + ROUTE_SELECTION_ORIGIN_Y, 0xFFB8C0C8, false);
        guiGraphics.drawString(this.font, "Destination", x + ROUTE_SELECTION_LABEL_X, y + ROUTE_SELECTION_DESTINATION_Y, 0xFFB8C0C8, false);
        if (originStationBox != null) {
            originStationBox.render(guiGraphics, mouseX, mouseY, 0);
        }
        if (destinationStationBox != null) {
            destinationStationBox.render(guiGraphics, mouseX, mouseY, 0);
        }
        int confirmX = routeSelectionConfirmX(x);
        int confirmY = routeSelectionConfirmY(y);
        boolean confirmHovered = isInside(mouseX, mouseY, confirmX, confirmY, 18, 18);
        AllGuiTextures confirmButtonTexture = confirmHovered && AllKeys.isMouseButtonDown(0)
                ? AllGuiTextures.BUTTON_DOWN
                : confirmHovered
                ? AllGuiTextures.BUTTON_HOVER
                : AllGuiTextures.BUTTON;
        guiGraphics.blit(
                confirmButtonTexture.location,
                confirmX,
                confirmY,
                confirmButtonTexture.getStartX(),
                confirmButtonTexture.getStartY(),
                confirmButtonTexture.getWidth(),
                confirmButtonTexture.getHeight()
        );
        AllIcons.I_CONFIG_SAVE.render(guiGraphics, confirmX + 1, confirmY + 1);
        if (originStationSuggestions != null) {
            originStationSuggestions.render(guiGraphics, mouseX, mouseY);
        }
        if (destinationStationSuggestions != null) {
            destinationStationSuggestions.render(guiGraphics, mouseX, mouseY);
        }
        if (confirmHovered) {
            guiGraphics.renderTooltip(
                    this.font,
                    List.of(Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.route_selection.save_and_start")),
                    Optional.empty(),
                    mouseX,
                    mouseY
            );
        }
    }

    private boolean mouseClickedRouteSelection(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int x = routeSelectionLeft();
        int y = this.topPos + ROUTE_SELECTION_Y;
        if (originStationSuggestions != null && originStationSuggestions.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        if (destinationStationSuggestions != null && destinationStationSuggestions.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        int confirmX = routeSelectionConfirmX(x);
        int confirmY = routeSelectionConfirmY(y);
        if (isInside(mouseX, mouseY, confirmX, confirmY, 18, 18)) {
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            confirmRouteSelection();
            return true;
        }
        if (originStationBox != null && isInside(mouseX, mouseY, originStationBox.getX() - 6, originStationBox.getY() - 3, originStationBox.getWidth() + 12, originStationBox.getHeight() + 6)) {
            originStationBox.mouseClicked(mouseX, mouseY, button);
            setFocused(originStationBox);
            if (originStationSuggestions != null) {
                originStationSuggestions.forceShow();
            }
            return true;
        }
        if (destinationStationBox != null && isInside(mouseX, mouseY, destinationStationBox.getX() - 6, destinationStationBox.getY() - 3, destinationStationBox.getWidth() + 12, destinationStationBox.getHeight() + 6)) {
            destinationStationBox.mouseClicked(mouseX, mouseY, button);
            setFocused(destinationStationBox);
            if (destinationStationSuggestions != null) {
                destinationStationSuggestions.forceShow();
            }
            return true;
        }
        if (!isInside(mouseX, mouseY, x, y, ROUTE_SELECTION_WIDTH, ROUTE_SELECTION_HEIGHT)) {
            return true;
        }
        return true;
    }

    private void renderModeToggle(GuiGraphics guiGraphics) {
        int x = this.leftPos + MODE_TOGGLE_X;
        int y = this.topPos + MODE_TOGGLE_Y;
        if (!recordingMode
                && this.minecraft.level != null
                && pulseRecordingModeHint) {
            float phase = (this.minecraft.level.getGameTime() % 20L) / 20.0F;
            float pulse = 0.5F + 0.5F * Mth.sin(phase * (float) (Math.PI * 2.0));
            int alpha = (int) (255.0F * pulse);
            if (alpha > 0) {
                int glow = net.minecraft.util.FastColor.ARGB32.color(alpha, 255, 225, 96);
                int border = net.minecraft.util.FastColor.ARGB32.color(alpha, 255, 208, 64);
                guiGraphics.fill(x - 2, y - 2, x + MODE_TOGGLE_DRAW_WIDTH + 2, y + MODE_TOGGLE_DRAW_HEIGHT + 2, glow);
                guiGraphics.fill(x - 3, y - 3, x + MODE_TOGGLE_DRAW_WIDTH + 3, y - 2, border);
                guiGraphics.fill(x - 3, y + MODE_TOGGLE_DRAW_HEIGHT + 2, x + MODE_TOGGLE_DRAW_WIDTH + 3, y + MODE_TOGGLE_DRAW_HEIGHT + 3, border);
                guiGraphics.fill(x - 3, y - 2, x - 2, y + MODE_TOGGLE_DRAW_HEIGHT + 2, border);
                guiGraphics.fill(x + MODE_TOGGLE_DRAW_WIDTH + 2, y - 2, x + MODE_TOGGLE_DRAW_WIDTH + 3, y + MODE_TOGGLE_DRAW_HEIGHT + 2, border);
            }
        }
        int v = recordingMode ? MODE_TOGGLE_RIGHT_V : MODE_TOGGLE_LEFT_V;
        guiGraphics.blit(
                CREATE_WIDGETS,
                x,
                y,
                MODE_TOGGLE_DRAW_WIDTH,
                MODE_TOGGLE_DRAW_HEIGHT,
                (float) (MODE_TOGGLE_U + MODE_TOGGLE_SOURCE_WIDTH),
                (float) v,
                -MODE_TOGGLE_SOURCE_WIDTH,
                MODE_TOGGLE_SOURCE_HEIGHT,
                256,
                256
        );
    }

    private void startInstalledSchedule() {
        if (this.minecraft != null && this.minecraft.player != null) {
            pulseRecordingModeHint = menu.shouldSuggestRecordingMode(this.minecraft.player);
        }
        pressAction(ShipTransponderMenu.ACTION_START_INSTALLED_SCHEDULE);
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

    private int routeSelectionLeft() {
        return this.leftPos + (PANEL_WIDTH - ROUTE_SELECTION_WIDTH) / 2;
    }

    private int routeSelectionConfirmX(int popupLeft) {
        return popupLeft + ROUTE_SELECTION_WIDTH - ROUTE_SELECTION_CONFIRM_OFFSET_RIGHT;
    }

    private int routeSelectionConfirmY(int popupTop) {
        return popupTop + ROUTE_SELECTION_HEIGHT - ROUTE_SELECTION_CONFIRM_OFFSET_BOTTOM;
    }

    private final class StationSuggestions {
        private final PopupSuggestions delegate;

        private StationSuggestions(EditBox textBox, List<IntAttached<String>> viableStations, int dropdownXOffset, int dropdownYOffset) {
            this.delegate = new PopupSuggestions(textBox, viableStations, dropdownXOffset, dropdownYOffset);
        }

        private void tick() {
            this.delegate.tick();
        }

        private void updateCommandInfo() {
            this.delegate.updateCommandInfo();
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return this.delegate.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean mouseClicked(int mouseX, int mouseY, int button) {
            return this.delegate.mouseClicked(mouseX, mouseY, button);
        }

        private boolean mouseScrolled(double scrollY) {
            return this.delegate.mouseScrolled(scrollY);
        }

        private void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            this.delegate.render(guiGraphics, mouseX, mouseY);
        }

        private void forceShow() {
            this.delegate.forceShow();
        }

        private void dismiss() {
            this.delegate.dismiss();
        }
    }

    private final class PopupSuggestions {
        private static final int ROW_HEIGHT = 12;
        private static final int MAX_ROWS = 4;
        private final EditBox textBox;
        private final List<IntAttached<String>> options;
        private final int dropdownXOffset;
        private final int dropdownYOffset;
        private List<String> visibleSuggestions = List.of();
        private int selectedIndex;
        private int scrollOffset;
        private boolean active;
        private String previous = "<>";

        private PopupSuggestions(EditBox textBox, List<IntAttached<String>> options, int dropdownXOffset, int dropdownYOffset) {
            this.textBox = textBox;
            this.options = options;
            this.dropdownXOffset = dropdownXOffset;
            this.dropdownYOffset = dropdownYOffset;
        }

        private void tick() {
            if (!this.active) {
                this.textBox.setSuggestion("");
            }
            if (this.active == this.textBox.isFocused()) {
                return;
            }
            this.active = this.textBox.isFocused();
            updateCommandInfo();
        }

        private void updateCommandInfo() {
            String value = currentValue();
            if (value.equals(this.previous)) {
                return;
            }
            this.previous = value;
            if (!this.active) {
                this.visibleSuggestions = List.of();
                this.textBox.setSuggestion("");
                return;
            }
            String trimmed = value.trim();
            boolean exactMatch = this.options.stream()
                    .map(IntAttached::getValue)
                    .anyMatch(option -> option.equalsIgnoreCase(trimmed));
            if (exactMatch) {
                this.visibleSuggestions = List.of();
                this.scrollOffset = 0;
                this.textBox.setSuggestion("");
                return;
            }
            this.visibleSuggestions = this.options.stream()
                    .filter(option -> !option.getValue().equals(value)
                            && option.getValue().toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT)))
                    .sorted((left, right) -> Integer.compare(left.getFirst(), right.getFirst()))
                    .map(IntAttached::getValue)
                    .toList();
            this.selectedIndex = Mth.clamp(this.selectedIndex, 0, Math.max(0, this.visibleSuggestions.size() - 1));
            clampScrollOffset();
            keepSelectionVisible();
            syncGhostText();
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.visibleSuggestions.isEmpty()) {
                return false;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                this.selectedIndex = Mth.clamp(this.selectedIndex + 1, 0, this.visibleSuggestions.size() - 1);
                keepSelectionVisible();
                syncGhostText();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                this.selectedIndex = Mth.clamp(this.selectedIndex - 1, 0, this.visibleSuggestions.size() - 1);
                keepSelectionVisible();
                syncGhostText();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applySelected();
                return true;
            }
            return false;
        }

        private boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            int row = rowAt(mouseX, mouseY);
            if (row < 0) {
                return true;
            }
            this.selectedIndex = row;
            keepSelectionVisible();
            applySelected();
            return true;
        }

        private boolean mouseScrolled(double scrollY) {
            if (this.visibleSuggestions.isEmpty()) {
                return false;
            }
            this.selectedIndex = Mth.clamp(this.selectedIndex + (scrollY > 0 ? -1 : 1), 0, this.visibleSuggestions.size() - 1);
            keepSelectionVisible();
            syncGhostText();
            return true;
        }

        private void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            if (!this.active || this.visibleSuggestions.isEmpty()) {
                return;
            }
            int x = this.textBox.getX() - 6 + this.dropdownXOffset;
            int y = this.textBox.getY() + this.textBox.getHeight() + 2 + this.dropdownYOffset;
            int width = Math.max(1, this.textBox.getWidth() + 12);
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int height = rows * ROW_HEIGHT + 4;

            guiGraphics.fill(x, y, x + width, y + height, 0xee303030);
            guiGraphics.fill(x, y, x + width, y + 1, 0xff505050);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xff505050);
            guiGraphics.fill(x, y, x + 1, y + height, 0xff505050);
            guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xff505050);

            guiGraphics.enableScissor(x, y, x + width, y + height);
            for (int i = 0; i < rows; i++) {
                int suggestionIndex = this.scrollOffset + i;
                if (suggestionIndex < 0 || suggestionIndex >= this.visibleSuggestions.size()) {
                    continue;
                }
                int rowY = y + 2 + i * ROW_HEIGHT;
                if (suggestionIndex == this.selectedIndex) {
                    guiGraphics.fill(x + 1, rowY - 1, x + width - 1, rowY + ROW_HEIGHT - 1, 0xff3a3a3a);
                }
                guiGraphics.drawString(
                        ShipTransponderScreen.this.font,
                        ShipTransponderScreen.this.font.plainSubstrByWidth(this.visibleSuggestions.get(suggestionIndex), width - 6),
                        x + 4,
                        rowY,
                        suggestionIndex == this.selectedIndex ? 0xFFFFFF55 : 0xFFE8EDF6,
                        false
                );
            }
            guiGraphics.disableScissor();
            if (this.scrollOffset > 0) {
                guiGraphics.fillGradient(x, y, x + width, y + 5, 0xCC303030, 0x00303030);
            }
            if (this.scrollOffset + rows < this.visibleSuggestions.size()) {
                guiGraphics.fillGradient(x, y + height - 5, x + width, y + height, 0x00303030, 0xCC303030);
            }
        }

        private void forceShow() {
            this.textBox.setFocused(true);
            ShipTransponderScreen.this.setFocused(this.textBox);
            this.previous = "<force>";
            this.active = true;
            clampScrollOffset();
            keepSelectionVisible();
            updateCommandInfo();
        }

        private void dismiss() {
            this.active = false;
            this.visibleSuggestions = List.of();
            this.scrollOffset = 0;
            this.textBox.setSuggestion("");
        }

        private String currentValue() {
            String value = this.textBox.getValue();
            int cursor = Math.min(this.textBox.getCursorPosition(), value.length());
            return value.substring(0, cursor);
        }

        private void syncGhostText() {
            if (this.visibleSuggestions.isEmpty()) {
                this.textBox.setSuggestion("");
                return;
            }
            String value = this.textBox.getValue();
            String selected = this.visibleSuggestions.get(this.selectedIndex);
            if (selected.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT)) && selected.length() > value.length()) {
                this.textBox.setSuggestion(selected.substring(value.length()));
            } else {
                this.textBox.setSuggestion("");
            }
        }

        private void applySelected() {
            if (this.visibleSuggestions.isEmpty()) {
                return;
            }
            this.textBox.setValue(this.visibleSuggestions.get(this.selectedIndex));
            this.textBox.setCursorPosition(this.textBox.getValue().length());
            this.textBox.setHighlightPos(this.textBox.getCursorPosition());
            this.visibleSuggestions = List.of();
            this.scrollOffset = 0;
            this.textBox.setSuggestion("");
        }

        private boolean contains(int mouseX, int mouseY) {
            if (!this.active || this.visibleSuggestions.isEmpty()) {
                return false;
            }
            int x = this.textBox.getX() - 6 + this.dropdownXOffset;
            int y = this.textBox.getY() + this.textBox.getHeight() + 2 + this.dropdownYOffset;
            int width = Math.max(1, this.textBox.getWidth() + 12);
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int height = rows * ROW_HEIGHT + 4;
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private int rowAt(int mouseX, int mouseY) {
            if (!contains(mouseX, mouseY)) {
                return -1;
            }
            int y = this.textBox.getY() + this.textBox.getHeight() + 2 + this.dropdownYOffset;
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int row = (mouseY - (y + 2)) / ROW_HEIGHT;
            return row >= 0 && row < rows ? this.scrollOffset + row : -1;
        }

        private void keepSelectionVisible() {
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            if (rows <= 0) {
                this.scrollOffset = 0;
                return;
            }
            if (this.selectedIndex < this.scrollOffset) {
                this.scrollOffset = this.selectedIndex;
            } else if (this.selectedIndex >= this.scrollOffset + rows) {
                this.scrollOffset = this.selectedIndex - rows + 1;
            }
            clampScrollOffset();
        }

        private void clampScrollOffset() {
            int maxOffset = Math.max(0, this.visibleSuggestions.size() - Math.min(MAX_ROWS, this.visibleSuggestions.size()));
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxOffset);
        }
    }

    private static final class ClippedEditBox extends EditBox {
        private ClippedEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.enableScissor(getX() - 2, getY(), getX() + width + 2, getY() + height);
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.disableScissor();
        }
    }

    private record ButtonTooltip(IconButton button, List<Component> lines) {
    }
}

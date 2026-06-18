package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScreenOverlay;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.gui.widget.TooltipArea;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition.Ops;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition.TimeUnit;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.mutable.MutableObject;
import net.createmod.catnip.data.IntAttached;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.ReopenShipTransponderPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateAirshipSchedulePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitDurationUnit;
import org.lwjgl.glfw.GLFW;

public class AirshipScheduleScreen extends AbstractContainerScreen<AirshipScheduleMenu> {
    private static final ResourceLocation SCHEDULE_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("create_aeronautics_automated_logistics", "textures/gui/schedule.png");
    private static final ResourceLocation CREATE_SCHEDULE_SHEET =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/schedule.png");
    private static final ResourceLocation SCHEDULE_EDITOR =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/schedule_2.png");
    private static final ResourceLocation CREATE_ICONS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");
    private static final ResourceLocation CREATE_WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final ResourceLocation PLAYER_INVENTORY =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");
    private static final ResourceLocation DISPLAY_LINK =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/display_link.png");
    private static final ResourceLocation ROUTE_SELECTOR =
            ResourceLocation.fromNamespaceAndPath("create_aeronautics_automated_logistics", "textures/gui/route_selector.png");
    private static final ResourceLocation CARGO_STABILITY_PANEL =
            ResourceLocation.fromNamespaceAndPath("create_aeronautics_automated_logistics", "textures/gui/cargo_stability_panel.png");
    private static final int CARD_HEADER = 22;
    private static final int CARD_WIDTH = 195;
    private static final int LEGACY_SCHEDULE_HEIGHT = 226;
    private static final int SCHEDULE_TOP_EXTENSION = 29;
    private static final int SCHEDULE_BACKGROUND_HEIGHT = LEGACY_SCHEDULE_HEIGHT + SCHEDULE_TOP_EXTENSION;
    private static final int SCHEDULE_BACKGROUND_TEXTURE_HEIGHT = 285;
    private static final int TEXT_COLOR = 0xE8E8E8;
    private static final int DARK_TEXT_COLOR = 0x505050;
    private static final int MUTED_TEXT_COLOR = 0xB8B8B8;
    private static final int ACTION_FIELD_MAX_WIDTH = 150;
    private static final int ASSIGNED_SHIP_LABEL_X = 24;
    private static final int ASSIGNED_SHIP_LABEL_Y = -2;
    private static final int ASSIGNED_SHIP_BOX_X = 103;
    private static final int ASSIGNED_SHIP_BOX_Y = -3;
    private static final int ASSIGNED_SHIP_BOX_WIDTH = 111;
    private static final int ASSIGNED_SHIP_BOX_HEIGHT = 10;
    private static final int ROUTE_SELECTOR_X = -3;
    private static final int ROUTE_SELECTOR_Y = 18;
    private static final int ROUTE_SELECTOR_WIDTH = 256;
    private static final int ROUTE_SELECTOR_HEIGHT = 151;
    private static final int ROUTE_SELECTOR_CONTENT_X = 40;
    private static final int ROUTE_SELECTOR_CONTENT_Y = 20;
    private static final int ROUTE_SELECTOR_CONTENT_WIDTH = 176;
    private static final int ROUTE_SELECTOR_CONTENT_HEIGHT = 112;
    private static final int ROUTE_SELECTOR_LIST_X = 47;
    private static final int ROUTE_SELECTOR_LIST_Y = 30;
    private static final int ROUTE_SELECTOR_LIST_WIDTH = 160;
    private static final int ROUTE_SELECTOR_ROW_HEIGHT = 32;
    private static final int ROUTE_SELECTOR_VISIBLE_ROWS = 3;
    private static final int ROUTE_SELECTOR_CONFIRM_SIZE = 18;
    private static final int ROUTE_SELECTOR_CONFIRM_MARGIN = 3;
    private static final int ROUTE_SELECTOR_CONFIRM_X_OFFSET = -10;
    private static final int REDSTONE_SLOT_X = 77;
    private static final int REDSTONE_SLOT_Y = 88;
    private static final int REDSTONE_SLOT_SPACING = 18;
    private static final int REDSTONE_SLOT_SIZE = 16;
    private static final int CARGO_STABLE_BUTTON_X = 202;
    private static final int CARGO_STABLE_BUTTON_Y = 96;
    private static final int CARGO_STABLE_BUTTON_SIZE = 9;
    private static final int CARGO_STABLE_PANEL_WIDTH = 128;
    private static final int CARGO_STABLE_PANEL_HEIGHT = 92;
    private static final int CARGO_STABLE_PANEL_FIELD_X = 29;
    private static final int CARGO_STABLE_PANEL_FIELD_Y = 30;
    private static final int CARGO_STABLE_PANEL_FIELD_WIDTH = 62;
    private static final int CARGO_STABLE_PANEL_CONFIRM_X = 95;
    private static final int CARGO_STABLE_PANEL_CONFIRM_Y = 68;
    private static final int CARGO_STABLE_PANEL_CONFIRM_WIDTH = 18;
    private static final int CARGO_STABLE_PANEL_CONFIRM_HEIGHT = 18;
    private static final int READ_ONLY_FOOTER_X = 120;
    private static final int READ_ONLY_FOOTER_Y = 201;
    private static final int READ_ONLY_FOOTER_WIDTH = 86;
    private static final int READ_ONLY_FOOTER_HEIGHT = 12;
    private static final DateTimeFormatter ROUTE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
    private EditBox titleBox;
    private EditBox assignedShipBox;
    private EditBox stationFilterBox;
    private EditBox editorStationBox;
    private ScrollInput editorDurationInput;
    private SelectionScrollInput editorUnitInput;
    private ScrollInput cargoStablePopupInput;
    private IconButton cargoStablePopupConfirmButton;
    private IconButton skipStopButton;
    private StationSuggestions stationSuggestions;
    private ShipSuggestions assignedShipSuggestions;
    private final EditorSubWidgets editorSubWidgets = new EditorSubWidgets();
    private final CompoundTag editorData = new CompoundTag();
    private AirshipSchedule localSchedule;
    private EditorMode editorMode = EditorMode.NONE;
    private int editorEntryIndex;
    private int editorConditionGroup;
    private int editorConditionIndex;
    private int selectedIndex;
    private int scrollOffset;
    private final List<RouteSegment> routeChoices = new ArrayList<>();
    private int routeChoiceSelected;
    private int routeChoiceScroll;
    private boolean noRoutePopupOpen;
    private boolean cargoStablePopupOpen;
    private final List<Component> noRoutePopupLines = new ArrayList<>();
    private Integer pendingDeleteStopIndex;
    private boolean leftMouseDown;
    private final Map<Integer, Integer> conditionScrollColumns = new HashMap<>();
    private final List<Slot> playerInventorySlots = new ArrayList<>();
    private boolean showPlayerInventorySlots;
    private boolean reopenSourceScreenOnClose;

    public AirshipScheduleScreen(AirshipScheduleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = LEGACY_SCHEDULE_HEIGHT;
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
        this.reopenSourceScreenOnClose = menu.openedFromTransponder();
    }

    @Override
    protected void init() {
        super.init();
        if (this.localSchedule == null) {
            this.localSchedule = this.minecraft != null && this.minecraft.player != null
                    ? this.menu.schedule(this.minecraft.player)
                    : AirshipSchedule.empty();
            this.selectedIndex = this.minecraft != null && this.minecraft.player != null
                    ? this.menu.selectedIndex(this.minecraft.player)
                    : 0;
            clampSelectedIndex();
        }
        AirshipSchedule schedule = currentSchedule();
        this.titleBox = new EditBox(
                this.font,
                this.leftPos + 74,
                this.topPos + 4,
                108,
                14,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.title")
        );
        this.titleBox.setBordered(false);
        this.titleBox.setTextColor(DARK_TEXT_COLOR);
        this.titleBox.setMaxLength(64);
        this.titleBox.setValue(schedule.title());
        this.titleBox.visible = false;
        this.titleBox.active = false;
        addRenderableWidget(this.titleBox);

        this.assignedShipBox = new ClippedEditBox(
                this.font,
                this.leftPos + ASSIGNED_SHIP_BOX_X,
                this.topPos + ASSIGNED_SHIP_BOX_Y,
                ASSIGNED_SHIP_BOX_WIDTH,
                ASSIGNED_SHIP_BOX_HEIGHT,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.assigned_ship")
        );
        this.assignedShipBox.setBordered(false);
        this.assignedShipBox.setTextColor(TEXT_COLOR);
        this.assignedShipBox.setTextColorUneditable(TEXT_COLOR);
        this.assignedShipBox.setMaxLength(64);
        this.assignedShipBox.setResponder(value -> {
            syncAssignedShipSelection(value);
            if (this.assignedShipSuggestions != null) {
                this.assignedShipSuggestions.updateCommandInfo();
            }
        });
        this.assignedShipBox.setEditable(!isAssignedShipLocked());
        this.assignedShipBox.active = !isAssignedShipLocked();
        populateAssignedShipBox();
        addRenderableWidget(this.assignedShipBox);
        this.assignedShipSuggestions = createAssignedShipSuggestions(this.assignedShipBox);

        this.stationFilterBox = new EditBox(
                this.font,
                this.leftPos + 82,
                this.topPos + 92,
                107,
                8,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_filter")
        );
        this.stationFilterBox.setBordered(false);
        this.stationFilterBox.setTextColor(TEXT_COLOR);
        this.stationFilterBox.setTextColorUneditable(TEXT_COLOR);
        this.stationFilterBox.setMaxLength(64);
        this.stationFilterBox.setResponder(value -> {
            if (this.stationSuggestions != null) {
                this.stationSuggestions.updateCommandInfo();
            }
        });
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        addRenderableWidget(this.stationFilterBox);
        addRenderableWidget(this.editorSubWidgets);

        this.skipStopButton = new IconButton(this.leftPos + 43, this.topPos + 196, AllIcons.I_PRIORITY_LOW);
        this.skipStopButton.withCallback(() -> sendServerAction(AirshipScheduleMenu.ACTION_SKIP_CURRENT_STOP));
        this.skipStopButton.setToolTip(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop"));
        addRenderableWidget(this.skipStopButton);
        updateSkipStopButtonState();

        cachePlayerInventorySlots();
        this.showPlayerInventorySlots = false;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(
                SCHEDULE_BACKGROUND,
                this.leftPos,
                this.topPos - SCHEDULE_TOP_EXTENSION,
                0,
                0,
                0,
                this.imageWidth,
                SCHEDULE_BACKGROUND_HEIGHT,
                256,
                SCHEDULE_BACKGROUND_TEXTURE_HEIGHT
        );
        Component title = Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.title");
        guiGraphics.drawString(this.font, title.getVisualOrderText(), this.leftPos + 124 - this.font.width(title) / 2, this.topPos - 26, DARK_TEXT_COLOR, false);
        Component assignedShip = Component.literal("Editing for:");
        guiGraphics.drawString(this.font, assignedShip, this.leftPos + ASSIGNED_SHIP_LABEL_X, this.topPos + ASSIGNED_SHIP_LABEL_Y, TEXT_COLOR, false);
        renderSchedule(guiGraphics, mouseX, mouseY);
        if (this.editorMode == EditorMode.NONE) {
            renderFooterButtons(guiGraphics, mouseX, mouseY);
        }
        if (this.editorMode != EditorMode.NONE) {
            renderEditor(guiGraphics, mouseX, mouseY);
        }
        renderSkipStopAvailabilityPulse(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.showPlayerInventorySlots = this.editorMode != EditorMode.NONE && !usesRouteEditorBacking();
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int renderMouseX = this.cargoStablePopupOpen ? Integer.MIN_VALUE / 4 : mouseX;
        int renderMouseY = this.cargoStablePopupOpen ? Integer.MIN_VALUE / 4 : mouseY;
        super.render(guiGraphics, renderMouseX, renderMouseY, partialTick);
        if (this.cargoStablePopupOpen) {
            renderCargoStablePopup(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (!this.cargoStablePopupOpen && !usesRouteEditorBacking() && this.stationSuggestions != null) {
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 500);
            this.stationSuggestions.render(guiGraphics, mouseX, mouseY);
            pose.popPose();
        }
        if (!this.cargoStablePopupOpen && !usesRouteEditorBacking() && !isAssignedShipLocked() && this.assignedShipSuggestions != null) {
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 500);
            this.assignedShipSuggestions.render(guiGraphics, mouseX, mouseY);
            pose.popPose();
        }
        renderHover(guiGraphics, mouseX, mouseY);
        if (pendingDeleteStopIndex != null) {
            renderDeleteStopConfirm(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateSkipStopButtonState();
        if (usesRouteEditorBacking()) {
            dismissSuggestionPopups();
            return;
        }
        if (this.stationSuggestions != null) {
            this.stationSuggestions.tick();
        }
        if (!isAssignedShipLocked() && this.assignedShipSuggestions != null) {
            this.assignedShipSuggestions.tick();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderSkipStopAvailabilityPulse(GuiGraphics guiGraphics) {
        if (this.skipStopButton == null
                || !this.skipStopButton.visible
                || !this.skipStopButton.active
                || this.minecraft == null
                || this.minecraft.level == null) {
            return;
        }
        float phase = (this.minecraft.level.getGameTime() % 20L) / 20.0F;
        float pulse = 0.5F + 0.5F * Mth.sin(phase * (float) (Math.PI * 2.0));
        int alpha = (int) (255.0F * pulse);
        if (alpha <= 0) {
            return;
        }
        int x = this.skipStopButton.getX();
        int y = this.skipStopButton.getY();
        int glow = net.minecraft.util.FastColor.ARGB32.color(alpha, 255, 225, 96);
        int border = net.minecraft.util.FastColor.ARGB32.color(alpha, 255, 208, 64);
        guiGraphics.fill(x - 2, y - 2, x + 18 + 2, y + 18 + 2, glow);
        guiGraphics.fill(x - 3, y - 3, x + 18 + 3, y - 2, border);
        guiGraphics.fill(x - 3, y + 18 + 2, x + 18 + 3, y + 18 + 3, border);
        guiGraphics.fill(x - 3, y - 2, x - 2, y + 18 + 2, border);
        guiGraphics.fill(x + 18 + 2, y - 2, x + 18 + 3, y + 18 + 2, border);
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (!this.showPlayerInventorySlots && this.playerInventorySlots.contains(slot)) {
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        if ((this.editorMode == EditorMode.NONE || usesRouteEditorBacking()) && isPlayerInventoryRegion(x, y, width, height)) {
            return false;
        }
        return super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    private boolean usesRouteEditorBacking() {
        return this.editorMode == EditorMode.ROUTE || this.noRoutePopupOpen;
    }

    private void dismissSuggestionPopups() {
        if (this.stationSuggestions != null) {
            this.stationSuggestions.dismiss();
        }
        if (this.assignedShipSuggestions != null) {
            this.assignedShipSuggestions.dismiss();
        }
        if (this.editorStationBox != null) {
            this.editorStationBox.setFocused(false);
        }
        if (this.assignedShipBox != null) {
            this.assignedShipBox.setFocused(false);
        }
        if (this.stationFilterBox != null) {
            this.stationFilterBox.setFocused(false);
        }
        setFocused(null);
    }

    private int routeSelectorPanelX() {
        return this.leftPos + ROUTE_SELECTOR_X;
    }

    private int routeSelectorPanelY() {
        return this.topPos + ROUTE_SELECTOR_Y;
    }

    private int routeSelectorConfirmX() {
        return routeSelectorPanelX() + ROUTE_SELECTOR_WIDTH
                - ROUTE_SELECTOR_CONFIRM_SIZE - ROUTE_SELECTOR_CONFIRM_MARGIN
                + ROUTE_SELECTOR_CONFIRM_X_OFFSET;
    }

    private int routeSelectorConfirmY() {
        return routeSelectorPanelY() + ROUTE_SELECTOR_CONTENT_Y + ROUTE_SELECTOR_CONTENT_HEIGHT
                - ROUTE_SELECTOR_CONFIRM_SIZE - ROUTE_SELECTOR_CONFIRM_MARGIN;
    }

    private boolean insideRouteSelectorConfirm(int mx, int my) {
        return inside(mx, my,
                ROUTE_SELECTOR_X + ROUTE_SELECTOR_WIDTH
                        - ROUTE_SELECTOR_CONFIRM_SIZE - ROUTE_SELECTOR_CONFIRM_MARGIN
                        + ROUTE_SELECTOR_CONFIRM_X_OFFSET,
                ROUTE_SELECTOR_Y + ROUTE_SELECTOR_CONTENT_Y + ROUTE_SELECTOR_CONTENT_HEIGHT
                        - ROUTE_SELECTOR_CONFIRM_SIZE - ROUTE_SELECTOR_CONFIRM_MARGIN,
                ROUTE_SELECTOR_CONFIRM_SIZE,
                ROUTE_SELECTOR_CONFIRM_SIZE);
    }

    private void renderRouteSelectorPanel(GuiGraphics guiGraphics, int panelX, int panelY) {
        guiGraphics.blit(ROUTE_SELECTOR, panelX, panelY, 0, 0, ROUTE_SELECTOR_WIDTH, ROUTE_SELECTOR_HEIGHT, ROUTE_SELECTOR_WIDTH, ROUTE_SELECTOR_HEIGHT);
    }

    private void renderSchedule(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AirshipSchedule schedule = currentSchedule();
        int selectedIndex = selectedIndex();
        int absoluteLeft = this.leftPos;
        int absoluteTop = this.topPos;

        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, absoluteLeft + 33, absoluteTop + 16, 3, 173, 5, 235, 3, 1);
        guiGraphics.enableScissor(absoluteLeft + 16, absoluteTop + 16, absoluteLeft + 236, absoluteTop + 189);

        int y = 25 - this.scrollOffset;
        List<AirshipScheduleEntry> entries = schedule.entries();
        for (int i = 0; i <= entries.size(); i++) {
            if (selectedIndex == i && !entries.isEmpty()) {
                int expectedY = absoluteTop + y + 4;
                int actualY = Mth.clamp(expectedY, absoluteTop + 18, absoluteTop + 170);
                if (expectedY == actualY) {
                    blit(guiGraphics, CREATE_SCHEDULE_SHEET, absoluteLeft, actualY, 185, 239, 21, 16);
                } else {
                    blit(guiGraphics, CREATE_SCHEDULE_SHEET, absoluteLeft, actualY, 171, 239, 13, 16);
                }
            }
            if (i == 0 || entries.isEmpty()) {
                blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, absoluteLeft + 33, absoluteTop + 16, 3, 10, 5, 237, 3, 1);
            }
            if (i == entries.size()) {
                if (i > 0) {
                    y += 9;
                }
                if (!isTransponderManagedPlan()) {
                    blit(guiGraphics, CREATE_SCHEDULE_SHEET, absoluteLeft + 29, absoluteTop + y, 34, 239, 11, 16);
                    renderAddCard(guiGraphics, absoluteLeft + 43, absoluteTop + y);
                }
                break;
            }

            AirshipScheduleEntry entry = entries.get(i);
            int cardHeight = cardHeight(entry);
            renderEntryCard(guiGraphics, entry, i, selectedIndex == i, absoluteLeft + 25, absoluteTop + y, cardHeight);
            y += cardHeight;
            if (i + 1 < entries.size()) {
                renderDottedStrip(guiGraphics, absoluteLeft + 33, absoluteTop + y - 2);
                y += 10;
            }
        }

        guiGraphics.disableScissor();
        if (hasSelectedShip()) {
            guiGraphics.fillGradient(absoluteLeft + 16, absoluteTop + 16, absoluteLeft + 236, absoluteTop + 26, 200, 0x77000000, 0x00000000);
            guiGraphics.fillGradient(absoluteLeft + 16, absoluteTop + 179, absoluteLeft + 236, absoluteTop + 189, 200, 0x00000000, 0x77000000);
        }

        if (!hasSelectedShip()) {
            guiGraphics.fill(absoluteLeft + 13, absoluteTop + 16, absoluteLeft + 237, absoluteTop + 191, 0xBB2C2C2C);
            guiGraphics.fill(absoluteLeft + 13, absoluteTop + 16, absoluteLeft + 237, absoluteTop + 191, 0x66000000);
            Component prompt = Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.select_ship");
            int promptX = absoluteLeft + 102;
            int promptY = absoluteTop + 92;
            guiGraphics.drawWordWrap(
                    this.font,
                    prompt,
                    promptX + 1,
                    promptY + 1,
                    110,
                    0xFF202020
            );
            guiGraphics.drawWordWrap(
                    this.font,
                    prompt,
                    promptX,
                    promptY,
                    110,
                    0xFFF0F0F0
            );
        } else if (entries.isEmpty()) {
            guiGraphics.drawWordWrap(
                    this.font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.empty"),
                    absoluteLeft + 62,
                    absoluteTop + 78,
                    145,
                    0xFFB0B0B0
            );
        }
    }

    private void renderEntryCard(GuiGraphics guiGraphics, AirshipScheduleEntry entry, int index, boolean selected, int x, int y, int height) {
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x, y + 1, CARD_WIDTH, height - 2, 7, 233, 1, 1);
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x + 1, y, CARD_WIDTH - 2, height, 7, 233, 1, 1);
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x + 1, y + 1, CARD_WIDTH - 2, height - 2, 5, 233, 1, 1);
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x + 2, y + 2, CARD_WIDTH - 4, height - 4, 6, 233, 1, 1);
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x + 2, y + 2, CARD_WIDTH - 4, CARD_HEADER, 7, 233, 1, 1);
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x + 8, y, 3, height + 10, 5, 237, 3, 1);
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + 4, y + 6, 12, 239, 11, 16);
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + 4, y + 28, 1, 239, 11, 16);
        Component actionText = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.entry.travel",
                displayStationName(entry)
        );
        int actionFieldWidth = Math.min(ACTION_FIELD_MAX_WIDTH, Math.max(100, this.font.width(actionText) + 36));
        renderScheduleInput(guiGraphics, x + 26, y + 5, actionFieldWidth, false, actionText, ModItems.AIRSHIP_STATION.get().getDefaultInstance());

        blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + CARD_WIDTH - 14, y + 2, 51, 243, 12, 12);
        if (!isTransponderManagedPlan()) {
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + CARD_WIDTH - 14, y + height - 14, 65, 243, 12, 12);
        }
        if (index > 0) {
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + CARD_WIDTH, y + CARD_HEADER - 14, 51, 230, 12, 12);
        }
        if (index < currentSchedule().entries().size() - 1) {
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + CARD_WIDTH, y + CARD_HEADER, 65, 230, 12, 12);
        }

        renderConditions(guiGraphics, entry, index, x, y, height);
    }

    private void renderConditions(GuiGraphics guiGraphics, AirshipScheduleEntry entry, int entryIndex, int x, int y, int cardHeight) {
        int scrollColumns = conditionScrollColumns.getOrDefault(entryIndex, 0);
        int scrollPixels = conditionScrollPixels(entry, scrollColumns);
        int maxRows = entry.conditionGroups().stream().mapToInt(List::size).max().orElse(1);
        int clipTop = y + 24;
        int clipBottom = y + CARD_HEADER + 24 + maxRows * 18;
        int clipLeft = x + 18;
        int clipRight = x + CARD_WIDTH - 16;
        guiGraphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(-scrollPixels, 0, 0);
        int groupX = x + 26;
        for (List<AirshipScheduleCondition> group : entry.conditionGroups()) {
            int groupWidth = conditionColumnWidth(group, entry.waitUnit());
            int row = 0;
            for (; row < group.size(); row++) {
                renderScheduleInput(guiGraphics, groupX, y + 29 + row * 18, groupWidth, row != 0, conditionWaitText(group.get(row), entry.waitUnit()), Items.STRUCTURE_VOID.getDefaultInstance());
            }
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, groupX + (groupWidth - 10) / 2, y + 29 + row * 18, 150, 245, 10, 10);
            groupX += groupWidth + 10;
        }
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, groupX - 3, y + 29, 96, 239, 19, 16);
        pose.popPose();
        guiGraphics.disableScissor();

        if (isConditionAreaScrollable(entry)) {
            int center = y + (cardHeight - 8 + CARD_HEADER) / 2;
            if (scrollColumns > 0) {
                blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + 15, center, 161, 247, 4, 8);
            }
            if (scrollColumns < Math.max(0, entry.conditionGroups().size() - 1)) {
                blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + 178, center, 166, 247, 4, 8);
            }
            var fadePose = guiGraphics.pose();
            fadePose.pushPose();
            fadePose.translate(x, y, 0);
            fadePose.mulPose(Axis.ZP.rotationDegrees(-90));
            guiGraphics.fillGradient(-cardHeight + 2, 18, -2 - CARD_HEADER, 28, 200, 0x44000000, 0x00000000);
            guiGraphics.fillGradient(-cardHeight + 2, CARD_WIDTH - 26, -2 - CARD_HEADER, CARD_WIDTH - 16, 200, 0x00000000, 0x44000000);
            fadePose.popPose();
        }
    }

    private void renderAddCard(GuiGraphics guiGraphics, int x, int y) {
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, x, y, 79, 239, 16, 16);
    }

    private void renderDottedStrip(GuiGraphics guiGraphics, int x, int y) {
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, x - 4, y - 1, 23, 239, 11, 16);
    }

    private void renderField(GuiGraphics guiGraphics, int x, int y, int width, Component text) {
        guiGraphics.fill(x, y, x + width, y + 16, 0xFF8C8C8C);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 15, 0xFF6D6D6D);
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), width - 12), x + 6, y + 4, TEXT_COLOR, false);
    }

    private void renderScheduleInput(GuiGraphics guiGraphics, int x, int y, int width, boolean clean, Component text, net.minecraft.world.item.ItemStack icon) {
        blitStretch(guiGraphics, CREATE_SCHEDULE_SHEET, x, y, width, 16, 123, 239, 1, 16);
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, clean ? x : x - 3, y, clean ? 147 : 116, 239, clean ? 2 : 6, 16);
        blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + width - 2, y, 144, 239, 2, 16);
        boolean hasIcon = hasDisplayedIcon(icon);
        if (hasIcon) {
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, x + 3, y, 125, 239, 18, 16);
            guiGraphics.renderItem(icon, x + 4, y);
        }
        int textLimit = Math.min(120, width - (hasIcon ? 36 : 12));
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), textLimit), x + (hasIcon ? 28 : 8), y + 4, TEXT_COLOR, false);
    }

    private void renderDataAreaBox(GuiGraphics guiGraphics, int x, int y, int width, boolean speechBubble) {
        blitStretch(guiGraphics, DISPLAY_LINK, x, y, width, 18, 3, 163, 1, 18);
        if (speechBubble) {
            blit(guiGraphics, DISPLAY_LINK, x - 3, y, 8, 163, 5, 18);
        } else {
            blit(guiGraphics, DISPLAY_LINK, x, y, 0, 163, 2, 18);
        }
        blit(guiGraphics, DISPLAY_LINK, x + width - 2, y, 5, 163, 2, 18);
    }

    private void renderFooterButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        AirshipSchedule schedule = currentSchedule();
        boolean editable = !isScheduleReadOnly();
        boolean loopHovered = editable && isHoveringButton(mouseX, mouseY, this.leftPos + 21, this.topPos + 196);
        boolean loopPressed = !editable || isPressedButton(mouseX, mouseY, this.leftPos + 21, this.topPos + 196);
        renderIconButton(
                guiGraphics,
                this.leftPos + 21,
                this.topPos + 196,
                48,
                16,
                schedule.loop(),
                loopHovered,
                loopPressed,
                true
        );
        renderIconButton(
                guiGraphics,
                this.leftPos + this.imageWidth - 42,
                this.topPos + LEGACY_SCHEDULE_HEIGHT - 30,
                0,
                16,
                false,
                isHoveringButton(mouseX, mouseY, this.leftPos + this.imageWidth - 42, this.topPos + LEGACY_SCHEDULE_HEIGHT - 30),
                isPressedButton(mouseX, mouseY, this.leftPos + this.imageWidth - 42, this.topPos + LEGACY_SCHEDULE_HEIGHT - 30),
                true
        );
        if (!editable) {
            String text = Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.read_only").getString();
            String clipped = this.font.plainSubstrByWidth(text, READ_ONLY_FOOTER_WIDTH);
            int boxX = this.leftPos + READ_ONLY_FOOTER_X + 7;
            int boxY = this.topPos + READ_ONLY_FOOTER_Y - 5;
            int boxW = READ_ONLY_FOOTER_WIDTH - 14;
            int boxH = READ_ONLY_FOOTER_HEIGHT + 6;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF575757);
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF393939);
            guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF393939);
            guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF393939);
            guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF393939);
            int textX = this.leftPos + READ_ONLY_FOOTER_X + Math.max(0, (READ_ONLY_FOOTER_WIDTH - this.font.width(clipped)) / 2);
            guiGraphics.drawString(this.font, clipped, textX, this.topPos + READ_ONLY_FOOTER_Y, 0xFFE7C46E, false);
        }
    }

    private void renderIconButton(GuiGraphics guiGraphics, int x, int y, int iconU, int iconV, boolean green, boolean hovered, boolean pressed, boolean active) {
        int u;
        int v;
        if (!active) {
            u = 90;
            v = 0;
        } else if (hovered && pressed) {
            u = 36;
            v = 0;
        } else if (hovered) {
            u = 18;
            v = 0;
        } else if (green) {
            u = 72;
            v = 0;
        } else {
            u = 0;
            v = 0;
        }
        guiGraphics.blit(CREATE_WIDGETS, x, y, 0, u, v, 18, 18, 256, 256);
        guiGraphics.blit(CREATE_ICONS, x + 1, y + 1, 0, iconU, iconV, 16, 16, 256, 256);
    }

    private void renderScaledItemButton(GuiGraphics guiGraphics, int x, int y, int size, ItemStack icon, boolean green, boolean hovered, boolean pressed, boolean active) {
        int u;
        int v;
        if (!active) {
            u = 90;
            v = 0;
        } else if (hovered && pressed) {
            u = 36;
            v = 0;
        } else if (hovered) {
            u = 18;
            v = 0;
        } else if (green) {
            u = 72;
            v = 0;
        } else {
            u = 0;
            v = 0;
        }
        float scale = size / 18f;
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1f);
        guiGraphics.blit(CREATE_WIDGETS, Math.round(x / scale), Math.round(y / scale), 0, u, v, 18, 18, 256, 256);
        guiGraphics.renderItem(icon, Math.round(x / scale) + 1, Math.round(y / scale) + 1);
        pose.popPose();
    }

    private void renderCargoStablePopup(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = cargoStablePopupX();
        int y = cargoStablePopupY();
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 540);
        blit(guiGraphics, CARGO_STABILITY_PANEL, x, y, 0, 0, CARGO_STABLE_PANEL_WIDTH, CARGO_STABLE_PANEL_HEIGHT);
        Component title = Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_stable_for");
        guiGraphics.drawString(this.font, title, x + (CARGO_STABLE_PANEL_WIDTH - this.font.width(title)) / 2, y + 4, DARK_TEXT_COLOR, false);

        int seconds = this.cargoStablePopupInput == null
                ? Mth.clamp(this.editorData.getInt("Stable"), 0, 99)
                : this.cargoStablePopupInput.getState();
        Component value = cargoStableSecondsLabel(seconds);
        renderDataAreaBox(guiGraphics, x + CARGO_STABLE_PANEL_FIELD_X, y + CARGO_STABLE_PANEL_FIELD_Y, CARGO_STABLE_PANEL_FIELD_WIDTH);
        guiGraphics.drawString(
                this.font,
                value,
                x + CARGO_STABLE_PANEL_FIELD_X + (CARGO_STABLE_PANEL_FIELD_WIDTH - this.font.width(value)) / 2,
                y + CARGO_STABLE_PANEL_FIELD_Y + 5,
                TEXT_COLOR,
                false
        );
        if (this.cargoStablePopupInput != null) {
            this.cargoStablePopupInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.cargoStablePopupConfirmButton != null) {
            this.cargoStablePopupConfirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        pose.popPose();
    }

    private void renderDataAreaBox(GuiGraphics guiGraphics, int x, int y, int width) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, y, 0);
        UIRenderHelper.drawStretched(guiGraphics, x, 0, width, 18, 0, AllGuiTextures.DATA_AREA);
        AllGuiTextures.DATA_AREA_START.render(guiGraphics, x, 0);
        AllGuiTextures.DATA_AREA_END.render(guiGraphics, x + width - 2, 0);
        pose.popPose();
    }

    private boolean isHoveringButton(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    private boolean isPressedButton(int mouseX, int mouseY, int x, int y) {
        return this.leftMouseDown && isHoveringButton(mouseX, mouseY, x, y);
    }

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(texture, x, y, 0, u, v, width, height, 256, 256);
    }

    private void blitStretch(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        guiGraphics.blit(texture, x, y, width, height, (float) u, (float) v, uWidth, vHeight, 256, 256);
    }

    private int conditionColumnWidth(List<AirshipScheduleCondition> group, WaitDurationUnit waitUnit) {
        int width = 32;
        for (AirshipScheduleCondition condition : group) {
            width = Math.max(width, fieldSize(32, conditionWaitText(condition, waitUnit), Items.STRUCTURE_VOID.getDefaultInstance()));
        }
        return width;
    }

    private int conditionScrollPixels(AirshipScheduleEntry entry, int scrollColumns) {
        int pixels = 0;
        int maxColumns = Math.min(scrollColumns, Math.max(0, entry.conditionGroups().size() - 1));
        for (int i = 0; i < maxColumns; i++) {
            pixels += conditionColumnWidth(entry.conditionGroups().get(i), entry.waitUnit()) + 10;
        }
        return pixels;
    }

    private boolean isConditionAreaScrollable(AirshipScheduleEntry entry) {
        int totalWidth = 26;
        for (List<AirshipScheduleCondition> group : entry.conditionGroups()) {
            totalWidth += conditionColumnWidth(group, entry.waitUnit()) + 10;
        }
        return totalWidth + 16 > CARD_WIDTH - 26;
    }

    private void scrollConditionColumns(int entryIndex, int direction) {
        if (entryIndex < 0 || entryIndex >= currentSchedule().entries().size()) {
            return;
        }
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        if (!isConditionAreaScrollable(entry)) {
            conditionScrollColumns.remove(entryIndex);
            return;
        }
        int max = Math.max(0, entry.conditionGroups().size() - 1);
        int next = Mth.clamp(conditionScrollColumns.getOrDefault(entryIndex, 0) + direction, 0, max);
        conditionScrollColumns.put(entryIndex, next);
    }

    private void renderEditor(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
        boolean editable = !isScheduleReadOnly();
        boolean routeEditorBacking = usesRouteEditorBacking();
        int x = this.leftPos - 2;
        int y = this.topPos + 40;
        if (!routeEditorBacking) {
            blit(guiGraphics, SCHEDULE_EDITOR, x, y, 0, 0, 256, 89);
        }
        if (!routeEditorBacking) {
            blit(guiGraphics, PLAYER_INVENTORY, this.leftPos + 38, this.topPos + 122, 0, 0, 176, 108);
            guiGraphics.drawString(this.font, this.playerInventoryTitle, this.leftPos + 46, this.topPos + 128, DARK_TEXT_COLOR, false);
        }
        Component title = routeEditorBacking
                ? (this.noRoutePopupOpen
                ? Component.literal("No Route")
                : Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.choose_route"))
                : switch (this.editorMode) {
            case STATION -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.instruction_editor");
            case CONDITION -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.condition_editor");
            case ROUTE -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.route_editor");
            case NONE -> Component.empty();
        };

        if (this.noRoutePopupOpen) {
            renderNoRoutePopup(guiGraphics, mouseX, mouseY);
        } else if (this.editorMode == EditorMode.ROUTE) {
            renderRouteChoiceEditor(guiGraphics, mouseX, mouseY);
        } else if (this.editorMode == EditorMode.STATION) {
            renderIconButton(
                    guiGraphics,
                    this.leftPos + 11,
                    this.topPos + 87,
                    16,
                    0,
                    false,
                    isHoveringButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                    isPressedButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                    editable
                );
            renderEditorChoiceText(guiGraphics, this.leftPos + 61, this.topPos + 69, 135, Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.travel_to_station"));
            guiGraphics.renderItem(ModItems.AIRSHIP_STATION.get().getDefaultInstance(), this.leftPos + 54, this.topPos + 88);
        } else {
            if (editable && canRemoveEditedCondition()) {
                renderIconButton(
                        guiGraphics,
                        this.leftPos + 11,
                        this.topPos + 87,
                        16,
                        0,
                        false,
                        isHoveringButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                        isPressedButton(mouseX, mouseY, this.leftPos + 11, this.topPos + 87),
                        editable
                );
            }
            AirshipScheduleCondition condition = currentCondition()
                    .orElse(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)));
            renderEditorChoiceText(guiGraphics, this.leftPos + 61, this.topPos + 69, 135, conditionActionText(condition));
            if (isRedstoneLinkWaitType(condition.waitCondition().type())) {
                guiGraphics.renderItem(conditionIcon(condition), this.leftPos + 54, this.topPos + 88);
                renderRedstoneFrequencySlot(guiGraphics, this.leftPos + REDSTONE_SLOT_X, this.topPos + REDSTONE_SLOT_Y, condition.waitCondition().redstoneFrequencyFirst());
                renderRedstoneFrequencySlot(guiGraphics, this.leftPos + REDSTONE_SLOT_X + REDSTONE_SLOT_SPACING, this.topPos + REDSTONE_SLOT_Y, condition.waitCondition().redstoneFrequencySecond());
            } else if (isTimeOfDayWaitType(condition.waitCondition().type())) {
                renderTimeOfDayIcon(guiGraphics, this.leftPos + 54, this.topPos + 88, condition.waitCondition());
            } else {
                guiGraphics.renderItem(conditionIcon(condition), this.leftPos + 54, this.topPos + 88);
            }
            if (editable && isCargoThresholdWaitType(condition.waitCondition().type())) {
                renderScaledItemButton(
                        guiGraphics,
                        this.leftPos + CARGO_STABLE_BUTTON_X,
                        this.topPos + CARGO_STABLE_BUTTON_Y,
                        CARGO_STABLE_BUTTON_SIZE,
                        Items.CLOCK.getDefaultInstance(),
                        this.editorData.getInt("Stable") > 0,
                        inside(mouseX, mouseY, this.leftPos + CARGO_STABLE_BUTTON_X, this.topPos + CARGO_STABLE_BUTTON_Y, CARGO_STABLE_BUTTON_SIZE, CARGO_STABLE_BUTTON_SIZE),
                        this.leftMouseDown && inside(mouseX, mouseY, this.leftPos + CARGO_STABLE_BUTTON_X, this.topPos + CARGO_STABLE_BUTTON_Y, CARGO_STABLE_BUTTON_SIZE, CARGO_STABLE_BUTTON_SIZE),
                        true
                );
            }
        }

        if (this.editorMode == EditorMode.STATION || this.editorMode == EditorMode.CONDITION) {
            var widgetsPose = guiGraphics.pose();
            widgetsPose.pushPose();
            widgetsPose.translate(0, this.topPos + 87, 0);
            this.editorSubWidgets.renderBg(this.leftPos + 77, guiGraphics);
            widgetsPose.popPose();
        }

        if (this.editorMode != EditorMode.NONE && !usesRouteEditorBacking()) {
            renderIconButton(
                    guiGraphics,
                    this.leftPos + 224,
                    this.topPos + 87,
                    0,
                    16,
                    false,
                    isHoveringButton(mouseX, mouseY, this.leftPos + 224, this.topPos + 87),
                    isPressedButton(mouseX, mouseY, this.leftPos + 224, this.topPos + 87),
                    editable
            );
        }
        if (usesRouteEditorBacking()) {
            var confirmPose = guiGraphics.pose();
            confirmPose.pushPose();
            confirmPose.translate(0, 0, 260);
            renderIconButton(
                    guiGraphics,
                    routeSelectorConfirmX(),
                    routeSelectorConfirmY(),
                    0,
                    16,
                    false,
                    isHoveringButton(mouseX, mouseY, routeSelectorConfirmX(), routeSelectorConfirmY()),
                    isPressedButton(mouseX, mouseY, routeSelectorConfirmX(), routeSelectorConfirmY()),
                    true
            );
            confirmPose.popPose();
        }
        int titleY = routeEditorBacking ? this.topPos + 22 : this.topPos + 44;
        int titleX = routeEditorBacking
                ? routeSelectorPanelX() + ROUTE_SELECTOR_WIDTH / 2 - this.font.width(title) / 2
                : this.leftPos + 124 - this.font.width(title) / 2;
        var titlePose = guiGraphics.pose();
        titlePose.pushPose();
        titlePose.translate(0, 0, 320);
        guiGraphics.drawString(this.font, title.getVisualOrderText(), titleX, titleY, DARK_TEXT_COLOR, false);
        titlePose.popPose();
        pose.popPose();
    }

    private void renderNoRoutePopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 80);
        int panelX = routeSelectorPanelX();
        int panelY = routeSelectorPanelY();
        int contentX = panelX + ROUTE_SELECTOR_CONTENT_X;
        int contentY = panelY + ROUTE_SELECTOR_CONTENT_Y;
        int contentW = ROUTE_SELECTOR_CONTENT_WIDTH;
        int contentH = ROUTE_SELECTOR_CONTENT_HEIGHT;
        int listX = panelX + ROUTE_SELECTOR_LIST_X;
        int listY = panelY + ROUTE_SELECTOR_LIST_Y;
        int rowWidth = ROUTE_SELECTOR_LIST_WIDTH;
        renderRouteSelectorPanel(guiGraphics, panelX, panelY);
        guiGraphics.fill(contentX - 2, contentY - 2, contentX + contentW + 2, contentY + contentH + 2, 0xF0101010);
        guiGraphics.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0xF0333840);

        int lineY = listY;
        for (Component line : this.noRoutePopupLines) {
            if (line.getString().isBlank()) {
                lineY += 12;
                continue;
            }
            for (var wrapped : this.font.split(line, rowWidth - 12)) {
                guiGraphics.drawString(this.font, wrapped, listX + 6, lineY, 0xFFD8DDE6, false);
                lineY += 12;
            }
        }

        pose.popPose();
    }

    private void renderDeleteStopConfirm(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 560);
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA111316);
        int boxW = 176;
        int boxH = 136;
        int x = this.leftPos + (this.imageWidth - boxW) / 2;
        int y = this.topPos + 28;
        guiGraphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0xFF111111);
        guiGraphics.fill(x, y, x + boxW, y + boxH, 0xF0292A2E);
        guiGraphics.drawCenteredString(this.font, "Delete stop?", x + boxW / 2, y + 6, 0xFFFFC66E);
        int lineY = y + 18;
        for (Component line : deleteStopConfirmLines()) {
            for (var wrapped : this.font.split(line, boxW - 12)) {
                guiGraphics.drawString(this.font, wrapped, x + 6, lineY, 0xFFD8DDE6, false);
                lineY += 10;
            }
        }
        boolean yesHovered = inside(mouseX, mouseY, x + 20, y + boxH - 19, 52, 14);
        boolean noHovered = inside(mouseX, mouseY, x + boxW - 72, y + boxH - 19, 52, 14);
        guiGraphics.fill(x + 20, y + boxH - 19, x + 72, y + boxH - 5, yesHovered ? 0xFF9E5A5A : 0xFF7A4444);
        guiGraphics.fill(x + boxW - 72, y + boxH - 19, x + boxW - 20, y + boxH - 5, noHovered ? 0xFF6A6A6A : 0xFF525252);
        guiGraphics.drawCenteredString(this.font, "Delete", x + 46, y + boxH - 15, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Cancel", x + boxW - 46, y + boxH - 15, 0xFFFFFFFF);
        pose.popPose();
    }

    private List<Component> deleteStopConfirmLines() {
        if (pendingDeleteStopIndex == null || pendingDeleteStopIndex < 0 || pendingDeleteStopIndex >= currentSchedule().entries().size()) {
            return List.of();
        }
        AirshipScheduleEntry entry = currentSchedule().entries().get(pendingDeleteStopIndex);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(displayStationName(entry)));
        lines.add(Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.delete_stop.routes",
                deletedRouteCountForStop(currentSchedule(), pendingDeleteStopIndex)
        ));
        lines.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.delete_stop.rerecord"));
        return lines;
    }

    private void renderRouteChoiceEditor(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 20);
        int panelX = routeSelectorPanelX();
        int panelY = routeSelectorPanelY();
        int contentX = panelX + ROUTE_SELECTOR_CONTENT_X;
        int contentY = panelY + ROUTE_SELECTOR_CONTENT_Y;
        int contentW = ROUTE_SELECTOR_CONTENT_WIDTH;
        int contentH = ROUTE_SELECTOR_CONTENT_HEIGHT;
        int listX = panelX + ROUTE_SELECTOR_LIST_X;
        int listY = panelY + ROUTE_SELECTOR_LIST_Y;
        int rowHeight = ROUTE_SELECTOR_ROW_HEIGHT;
        int visibleRows = ROUTE_SELECTOR_VISIBLE_ROWS;
        int rowWidth = ROUTE_SELECTOR_LIST_WIDTH;

        renderRouteSelectorPanel(guiGraphics, panelX, panelY);
        guiGraphics.fill(contentX - 2, contentY - 2, contentX + contentW + 2, contentY + contentH + 2, 0xF0101010);
        guiGraphics.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0xF0333840);

        guiGraphics.enableScissor(listX, listY, listX + rowWidth, listY + rowHeight * visibleRows);
        for (int i = 0; i < visibleRows; i++) {
            int routeIndex = this.routeChoiceScroll + i;
            if (routeIndex < 0 || routeIndex >= this.routeChoices.size()) {
                continue;
            }
            RouteSegment segment = this.routeChoices.get(routeIndex);
            boolean selected = routeIndex == this.routeChoiceSelected;
            int rowY = listY + i * rowHeight;
            boolean hovered = mouseX >= listX && mouseX < listX + rowWidth && mouseY >= rowY && mouseY < rowY + rowHeight;
            int rowColor = selected
                    ? (hovered ? 0xAA6A644E : 0x99615B49)
                    : (hovered ? 0xAA555B65 : 0x88414850);
            guiGraphics.fill(listX, rowY, listX + rowWidth, rowY + rowHeight - 2, rowColor);
            int textColor = TEXT_COLOR;
            guiGraphics.drawString(this.font, routeFromText(segment, 166), listX + 6, rowY + 3, textColor, false);
            guiGraphics.drawString(this.font, routeToText(segment, 166), listX + 6, rowY + 12, textColor, false);
            guiGraphics.drawString(this.font, routeMetaText(segment, 166), listX + 6, rowY + 21, 0xFFB9C4D0, false);
        }
        guiGraphics.disableScissor();
        if (this.routeChoiceScroll > 0) {
            guiGraphics.fillGradient(listX, listY, listX + rowWidth, listY + 5, 0xCC333943, 0x00333943);
        }
        if (this.routeChoiceScroll + visibleRows < this.routeChoices.size()) {
            int fadeBottomY = listY + rowHeight * visibleRows;
            guiGraphics.fillGradient(listX, fadeBottomY - 5, listX + rowWidth, fadeBottomY, 0x00333943, 0xCC333943);
        }

        if (this.routeChoiceScroll > 0) {
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, this.leftPos + 224, this.topPos + 85, 51, 230, 12, 12);
        }
        if (this.routeChoiceScroll + visibleRows < this.routeChoices.size()) {
            blit(guiGraphics, CREATE_SCHEDULE_SHEET, this.leftPos + 224, this.topPos + 118, 65, 230, 12, 12);
        }
        pose.popPose();
    }

    private boolean hasDisplayedIcon(net.minecraft.world.item.ItemStack icon) {
        return !icon.isEmpty() && icon.getItem() != Items.STRUCTURE_VOID;
    }

    private int fieldSize(int minSize, Component text, net.minecraft.world.item.ItemStack icon) {
        return Math.max((text == null ? 0 : this.font.width(text)) + (hasDisplayedIcon(icon) ? 20 : 0) + 16, minSize);
    }

    private void renderEditorChoiceText(GuiGraphics guiGraphics, int x, int y, int width, Component text) {
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), width), x, y, TEXT_COLOR, false);
    }

    private void renderRedstoneFrequencySlot(GuiGraphics guiGraphics, int x, int y, ItemStack stack) {
        AllGuiTextures.SCHEDULE_EDITOR_ADDITIONAL_SLOT.render(guiGraphics, x - 1, y - 1);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x, y);
        }
    }

    private void renderTimeOfDayIcon(GuiGraphics guiGraphics, int x, int y, WaitCondition waitCondition) {
        int displayHour = (waitCondition.timeOfDayHour() + 12) % 24;
        float progress = (displayHour * 60f + waitCondition.timeOfDayMinute()) / (24 * 60);
        ResourceLocation location = ResourceLocation.withDefaultNamespace(
                "textures/item/clock_" + twoDigits(Mth.clamp((int) (progress * 64), 0, 63)) + ".png"
        );
        guiGraphics.blit(location, x, y, 0, 0, 16, 16, 16, 16);
    }

    private void renderHover(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (pendingDeleteStopIndex != null) {
            return;
        }
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            var pose = guiGraphics.pose();
            pose.pushPose();
            if (this.cargoStablePopupOpen) {
                pose.translate(0, 0, 640);
            }
            guiGraphics.renderTooltip(this.font, tooltip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
            pose.popPose();
        } else if (this.editorMode != EditorMode.NONE && !this.noRoutePopupOpen) {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (this.editorMode != EditorMode.NONE) {
            return editorTooltipAt(mouseX, mouseY);
        }
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (inside(mx, my, ASSIGNED_SHIP_BOX_X, ASSIGNED_SHIP_BOX_Y - 2, ASSIGNED_SHIP_BOX_WIDTH, ASSIGNED_SHIP_BOX_HEIGHT + 6)) {
            if (this.assignedShipBox != null && !this.assignedShipBox.getValue().isBlank()) {
                return List.of(Component.literal(this.assignedShipBox.getValue()));
            }
            return List.of();
        }
        if (!hasSelectedShip() && mx >= 11 && mx < 241 && my >= 14 && my < 191) {
            return List.of();
        }
        if (inside(mx, my, 21, 196, 18, 18)) {
            return loopTooltip();
        }
        if (inside(mx, my, 214, 196, 18, 18)) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.confirm"));
        }
        if (skipStopButtonVisible() && inside(mx, my, 43, 196, 18, 18)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop.tooltip").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop.tooltip_availability").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
            );
        }
        if (isScheduleReadOnly() && inside(mx, my, READ_ONLY_FOOTER_X, READ_ONLY_FOOTER_Y - 1, READ_ONLY_FOOTER_WIDTH, this.font.lineHeight + 2)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.read_only"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.read_only.tooltip").withStyle(ChatFormatting.GRAY)
            );
        }
        Hit hit = hitAt(mouseX, mouseY);
        return switch (hit.type) {
            case ADD_ENTRY -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.add_entry"));
            case STATION -> stopTooltip(hit.index);
            case REMOVE -> deleteStopTooltip(hit.index);
            case DUPLICATE -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.duplicate"));
            case MOVE_UP -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.move_up"));
            case MOVE_DOWN -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.move_down"));
            case CONDITION_SCROLL_LEFT -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_left"));
            case CONDITION_SCROLL_RIGHT -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_right"));
            case CONDITION -> conditionTooltip(hit.index, hit.conditionGroup, hit.conditionIndex);
            case ADD_CONDITION -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.add_condition"));
            case ADD_ALTERNATIVE -> List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.alternative_condition"));
            default -> List.of();
        };
    }

    private List<Component> editorTooltipAt(int mouseX, int mouseY) {
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        if (skipStopButtonVisible() && inside(mx, my, 43, 196, 18, 18)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop.tooltip").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.skip_stop.tooltip_availability").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
            );
        }
        if (this.cargoStablePopupOpen) {
            int panelX = cargoStablePopupX() - this.leftPos;
            int panelY = cargoStablePopupY() - this.topPos;
            if (inside(mx, my, panelX + CARGO_STABLE_PANEL_CONFIRM_X, panelY + CARGO_STABLE_PANEL_CONFIRM_Y, CARGO_STABLE_PANEL_CONFIRM_WIDTH, CARGO_STABLE_PANEL_CONFIRM_HEIGHT)) {
                return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.confirm"));
            }
            return List.of();
        }
        if (this.noRoutePopupOpen) {
            return insideRouteSelectorConfirm(mx, my)
                    ? List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.confirm"))
                    : List.of();
        }
        if ((!usesRouteEditorBacking() && inside(mx, my, 224, 87, 18, 18))
                || (usesRouteEditorBacking() && insideRouteSelectorConfirm(mx, my))) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.confirm"));
        }
        if (this.editorMode == EditorMode.ROUTE) {
            int hovered = routeChoiceAt(mx, my);
            if (hovered >= 0 && hovered < this.routeChoices.size()) {
                RouteSegment segment = this.routeChoices.get(hovered);
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.route_choice").withStyle(ChatFormatting.GOLD),
                        Component.literal(routeDisplayName(segment)),
                        Component.literal(resolveShipName(segment)).withStyle(ChatFormatting.GRAY),
                        Component.literal(routeMetaTextRaw(segment)).withStyle(ChatFormatting.DARK_AQUA)
                );
            }
        }
        if (this.editorMode == EditorMode.STATION) {
            if (inside(mx, my, 11, 87, 18, 18)) {
                return deleteStopTooltip(this.editorEntryIndex);
            }
            if (inside(mx, my, 56, 65, 143, 16)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.next_action"),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.travel_to_station")
                );
            }
            if (inside(mx, my, 53, 87, 32, 18) || inside(mx, my, 77, 88, 121, 18)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name"),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_wildcard"),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_nearest")
                );
            }
        }
        if (this.editorMode == EditorMode.CONDITION) {
            if (inside(mx, my, 11, 87, 18, 18) && canRemoveEditedCondition()) {
                return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.remove_entry"));
            }
            if (inside(mx, my, 56, 65, 143, 16)) {
                return conditionSelectorTooltip();
            }
            if (inside(mx, my, REDSTONE_SLOT_X, REDSTONE_SLOT_Y, REDSTONE_SLOT_SIZE, REDSTONE_SLOT_SIZE)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isRedstoneLinkWaitType).orElse(false)) {
                return redstoneFrequencyTooltip(0);
            }
            if (inside(mx, my, REDSTONE_SLOT_X + REDSTONE_SLOT_SPACING, REDSTONE_SLOT_Y, REDSTONE_SLOT_SIZE, REDSTONE_SLOT_SIZE)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isRedstoneLinkWaitType).orElse(false)) {
                return redstoneFrequencyTooltip(1);
            }
            if (inside(mx, my, 53, 87, 18, 18)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isCargoThresholdWaitType).orElse(false)) {
                return List.of(
                        Component.translatable("create.schedule.condition.threshold.place_item"),
                        Component.translatable("create.schedule.condition.threshold.place_item_2").withStyle(ChatFormatting.GRAY),
                        Component.translatable("create.schedule.condition.threshold.place_item_3").withStyle(ChatFormatting.GRAY)
                );
            }
            if (inside(mx, my, CARGO_STABLE_BUTTON_X, CARGO_STABLE_BUTTON_Y, CARGO_STABLE_BUTTON_SIZE, CARGO_STABLE_BUTTON_SIZE)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isCargoThresholdWaitType).orElse(false)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_stable_for"),
                        cargoStableSecondsLabel(Mth.clamp(this.editorData.getInt("Stable"), 0, 99)).copy().withStyle(ChatFormatting.DARK_AQUA)
                );
            }
        }
        if (this.editorMode == EditorMode.NONE
                && inside(mx, my, ASSIGNED_SHIP_BOX_X, ASSIGNED_SHIP_BOX_Y - 2, ASSIGNED_SHIP_BOX_WIDTH, ASSIGNED_SHIP_BOX_HEIGHT + 6)) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.assigned_ship.tooltip"));
        }
        return List.of();
    }

    private List<Component> conditionSelectorTooltip() {
        WaitConditionType current = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .map(WaitCondition::type)
                .orElse(WaitConditionType.TIMED);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.continue_if_after").withStyle(ChatFormatting.BLUE));
        for (WaitConditionType type : conditionSelectorTypes()) {
            Component option = conditionActionText(AirshipScheduleCondition.fromWaitCondition(defaultWaitConditionForType(type)));
            tooltip.add(Component.literal((type == current ? "-> " : "> ")).append(option));
        }
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scroll_select").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return tooltip;
    }

    private List<Component> redstoneFrequencyTooltip(int slot) {
        WaitCondition wait = currentCondition().map(AirshipScheduleCondition::waitCondition).orElse(WaitCondition.redstoneLink(ItemStack.EMPTY, ItemStack.EMPTY, true));
        ItemStack stack = slot == 0 ? wait.redstoneFrequencyFirst() : wait.redstoneFrequencySecond();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(
                slot == 0
                        ? "create.logistics.firstFrequency"
                        : "create.logistics.secondFrequency"
        ));
        tooltip.add((stack.isEmpty() ? Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.empty") : stack.getHoverName())
                .copy().withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.place_frequency").withStyle(ChatFormatting.GRAY));
        return tooltip;
    }

    private List<Component> loopTooltip() {
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.loop_tooltip"),
                Component.translatable(currentSchedule().loop()
                                ? "gui.create_aeronautics_automated_logistics.airship_schedule.currently_enabled"
                                : "gui.create_aeronautics_automated_logistics.airship_schedule.currently_disabled")
                        .withStyle(currentSchedule().loop() ? ChatFormatting.DARK_GREEN : ChatFormatting.RED),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.loop_description").withStyle(ChatFormatting.GRAY)
        );
    }

    private List<Component> stopTooltip(int entryIndex) {
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.next_stop").withStyle(ChatFormatting.GOLD),
                Component.literal("\"" + displayStationName(entry) + "\"")
        );
    }

    private List<Component> deleteStopTooltip(int entryIndex) {
        if (!isTransponderManagedPlan()) {
            return List.of(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.remove_entry"));
        }
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.delete_stop.tooltip"),
                Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_schedule.delete_stop.routes",
                        deletedRouteCountForStop(currentSchedule(), entryIndex)
                ).withStyle(ChatFormatting.GRAY)
        );
    }

    private List<Component> conditionTooltip(int entryIndex, int groupIndex, int conditionIndex) {
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        if (groupIndex < 0 || groupIndex >= entry.conditionGroups().size()) {
            return List.of();
        }
        List<AirshipScheduleCondition> group = entry.conditionGroups().get(groupIndex);
        if (conditionIndex < 0 || conditionIndex >= group.size()) {
            return List.of();
        }
        AirshipScheduleCondition condition = group.get(conditionIndex);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.continue_if_after"));
        tooltip.add(conditionActionText(condition));
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED
                || condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            tooltip.add(conditionWaitText(condition).copy()
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.add(Component.translatable(
                    isCargoWaitType(condition.waitCondition().type())
                            ? "gui.create_aeronautics_automated_logistics.airship_schedule.detail"
                            : "gui.create_aeronautics_automated_logistics.airship_schedule.for_time",
                    longConditionTimeText(condition, entry.waitUnit())
            ).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.left_click_edit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (canRemoveCondition(entry, group)) {
            tooltip.add(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.right_click_remove").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return tooltip;
    }

    private boolean canRemoveCondition(AirshipScheduleEntry entry, List<AirshipScheduleCondition> group) {
        return group.size() > 1 || entry.conditionGroups().size() > 1;
    }

    private boolean canRemoveEditedCondition() {
        AirshipScheduleEntry entry = currentEntry().orElse(null);
        if (entry == null) {
            return false;
        }
        if (this.editorConditionGroup < 0 || this.editorConditionGroup >= entry.conditionGroups().size()) {
            return false;
        }
        List<AirshipScheduleCondition> group = entry.conditionGroups().get(this.editorConditionGroup);
        if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
            return false;
        }
        return canRemoveCondition(entry, group);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean editable = !isScheduleReadOnly();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.leftMouseDown = true;
        }
        if (pendingDeleteStopIndex != null) {
            return handleDeleteStopConfirmClick(mouseX, mouseY);
        }
        if (this.noRoutePopupOpen) {
            int mx = (int) mouseX - this.leftPos;
            int my = (int) mouseY - this.topPos;
            if (insideRouteSelectorConfirm(mx, my)) {
                closeNoRoutePopup();
                return true;
            }
            return true;
        }
        if (this.editorMode != EditorMode.NONE) {
            return mouseClickedEditor(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
        }

        if (!isAssignedShipLocked()
                && this.assignedShipSuggestions != null
                && this.assignedShipSuggestions.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }

        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        if (!isAssignedShipLocked()
                && this.assignedShipBox != null
                && inside(mx, my, ASSIGNED_SHIP_BOX_X, ASSIGNED_SHIP_BOX_Y - 2, ASSIGNED_SHIP_BOX_WIDTH, ASSIGNED_SHIP_BOX_HEIGHT + 6)) {
            this.assignedShipBox.mouseClicked(mouseX, mouseY, button);
            this.assignedShipBox.setFocused(true);
            setFocused(this.assignedShipBox);
            if (this.assignedShipSuggestions != null) {
                this.assignedShipSuggestions.forceShow();
            }
            return true;
        }
        if (!hasSelectedShip() && mx >= 11 && mx < 241 && my >= 14 && my < 191) {
            return true;
        }
        if (inside(mx, my, 21, 196, 18, 18)) {
            if (editable) {
                pressAction(AirshipScheduleMenu.ACTION_TOGGLE_LOOP);
            }
            return true;
        }
        if (inside(mx, my, 214, 196, 18, 18)) {
            playUiButtonClick();
            saveTitle();
            if (this.minecraft != null && this.minecraft.player != null) {
                if (this.menu.originTransponderPos().isPresent()) {
                    PacketDistributor.sendToServer(new ReopenShipTransponderPayload(
                            this.menu.originTransponderPos().get(),
                            this.menu.returnToRecordingMode()
                    ));
                    this.reopenSourceScreenOnClose = false;
                } else {
                    this.minecraft.player.closeContainer();
                }
            }
            return true;
        }
        Hit hit = hitAt((int) mouseX, (int) mouseY);
        if (hit.type == HitType.NONE) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (hit.index >= 0) {
            pressAction(AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE + hit.index);
        }

        switch (hit.type) {
            case ADD_ENTRY -> {
                if (isTransponderManagedPlan() || !editable) {
                    return true;
                }
                pressAction(AirshipScheduleMenu.ACTION_ADD_TRAVEL);
                openStationEditor(this.selectedIndex);
            }
            case STATION -> {
                if (!isTransponderManagedPlan()) {
                    openStationEditor(hit.index);
                }
            }
            case REMOVE -> {
                if (!editable) {
                    return true;
                }
                if (isTransponderManagedPlan()) {
                    openDeleteStopConfirm(hit.index);
                } else {
                    pressAction(AirshipScheduleMenu.ACTION_REMOVE);
                }
            }
            case DUPLICATE -> {
                if (editable && !isTransponderManagedPlan()) {
                    pressAction(AirshipScheduleMenu.ACTION_DUPLICATE);
                }
            }
            case MOVE_UP -> {
                if (editable) {
                    pressAction(AirshipScheduleMenu.ACTION_MOVE_UP);
                }
            }
            case MOVE_DOWN -> {
                if (editable) {
                    pressAction(AirshipScheduleMenu.ACTION_MOVE_DOWN);
                }
            }
            case CONDITION_SCROLL_LEFT -> scrollConditionColumns(hit.index, -1);
            case CONDITION_SCROLL_RIGHT -> scrollConditionColumns(hit.index, 1);
            case CONDITION -> {
                if (!editable) {
                    return true;
                }
                if (button == 1 && editable) {
                    removeConditionLocally(hit.index, hit.conditionGroup, hit.conditionIndex);
                    syncSchedule();
                } else {
                    openConditionEditor(hit.index, hit.conditionGroup, hit.conditionIndex);
                }
            }
            case ADD_CONDITION -> {
                if (!editable) {
                    return true;
                }
                addConditionLocally(hit.index, hit.conditionGroup);
                syncSchedule();
                openConditionEditorForLast(hit.index, hit.conditionGroup);
            }
            case ADD_ALTERNATIVE -> {
                if (!editable) {
                    return true;
                }
                int newGroupIndex = currentSchedule().entries().get(hit.index).conditionGroups().size();
                addAlternativeConditionLocally(hit.index);
                syncSchedule();
                openConditionEditorForLast(hit.index, newGroupIndex);
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.leftMouseDown = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean mouseClickedEditor(double mouseX, double mouseY, int button) {
        boolean editable = !isScheduleReadOnly();
        boolean routeEditorBacking = usesRouteEditorBacking();
        if (this.stationSuggestions != null && this.stationSuggestions.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        if (this.cargoStablePopupOpen) {
            int panelX = cargoStablePopupX() - this.leftPos;
            int panelY = cargoStablePopupY() - this.topPos;
            if (!inside(mx, my, panelX, panelY, CARGO_STABLE_PANEL_WIDTH, CARGO_STABLE_PANEL_HEIGHT))
                return true;
            if (this.cargoStablePopupConfirmButton != null && this.cargoStablePopupConfirmButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.cargoStablePopupInput != null) {
                this.cargoStablePopupInput.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (this.editorMode == EditorMode.ROUTE) {
            int routeIndex = routeChoiceAt(mx, my);
            if (routeIndex >= 0 && routeIndex < this.routeChoices.size()) {
                this.routeChoiceSelected = routeIndex;
                return true;
            }
            if (inside(mx, my, 224, 85, 12, 12) && this.routeChoiceScroll > 0) {
                this.routeChoiceScroll--;
                return true;
            }
            if (inside(mx, my, 224, 118, 12, 12) && this.routeChoiceScroll + ROUTE_SELECTOR_VISIBLE_ROWS < this.routeChoices.size()) {
                this.routeChoiceScroll++;
                return true;
            }
        }
        if (this.editorMode == EditorMode.STATION) {
            if (editable && inside(mx, my, 11, 87, 18, 18)) {
                if (isTransponderManagedPlan()) {
                    openDeleteStopConfirm(this.editorEntryIndex);
                } else {
                    removeSelectedLocally(currentSchedule());
                    syncSchedule();
                    closeEditor();
                }
                return true;
            }
            if (editable && this.editorStationBox != null && inside(mx, my, 77, 88, 121, 18)) {
                this.editorStationBox.mouseClicked(mouseX, mouseY, button);
                this.editorStationBox.setFocused(true);
                setFocused(this.editorStationBox);
                if (this.stationSuggestions != null) {
                    this.stationSuggestions.forceShow();
                }
                return true;
            }
        }
        if (editable && ((!usesRouteEditorBacking() && inside(mx, my, 224, 87, 18, 18))
                || (usesRouteEditorBacking() && insideRouteSelectorConfirm(mx, my)))) {
            playUiButtonClick();
            confirmEditor();
            return true;
        }
        if (this.editorMode == EditorMode.CONDITION) {
            if (editable && inside(mx, my, 56, 65, 143, 16)) {
                cycleEditedConditionType();
                return true;
            }
            if (editable && inside(mx, my, REDSTONE_SLOT_X, REDSTONE_SLOT_Y, REDSTONE_SLOT_SIZE, REDSTONE_SLOT_SIZE)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isRedstoneLinkWaitType).orElse(false)) {
                setEditedRedstoneFrequency(0, this.menu.getCarried());
                return true;
            }
            if (editable && inside(mx, my, REDSTONE_SLOT_X + REDSTONE_SLOT_SPACING, REDSTONE_SLOT_Y, REDSTONE_SLOT_SIZE, REDSTONE_SLOT_SIZE)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isRedstoneLinkWaitType).orElse(false)) {
                setEditedRedstoneFrequency(1, this.menu.getCarried());
                return true;
            }
            if (editable && inside(mx, my, 53, 87, 18, 18)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isCargoThresholdWaitType).orElse(false)) {
                setEditedConditionFilter(this.menu.getCarried());
                return true;
            }
            if (editable && inside(mx, my, CARGO_STABLE_BUTTON_X, CARGO_STABLE_BUTTON_Y, CARGO_STABLE_BUTTON_SIZE, CARGO_STABLE_BUTTON_SIZE)
                    && currentCondition().map(AirshipScheduleCondition::waitCondition).map(WaitCondition::type).map(this::isCargoThresholdWaitType).orElse(false)) {
                openCargoStablePopup();
                return true;
            }
            if (editable && inside(mx, my, 11, 87, 18, 18) && canRemoveEditedCondition()) {
                removeConditionLocally(this.editorEntryIndex, this.editorConditionGroup, this.editorConditionIndex);
                syncSchedule();
                closeEditor();
                return true;
            }
        }
        if (routeEditorBacking) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pendingDeleteStopIndex != null) {
            return true;
        }
        if (this.noRoutePopupOpen) {
            return true;
        }
        if (this.cargoStablePopupOpen) {
            if (this.cargoStablePopupInput != null && this.cargoStablePopupInput.isMouseOver(mouseX, mouseY)) {
                this.cargoStablePopupInput.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (!isAssignedShipLocked()
                && this.assignedShipBox != null
                && this.assignedShipBox.isFocused()
                && this.assignedShipSuggestions != null
                && this.assignedShipSuggestions.mouseScrolled(Mth.clamp(scrollY, -1.0D, 1.0D))) {
            return true;
        }
        if (!hasSelectedShip()) {
            int mx = (int) mouseX - this.leftPos;
            int my = (int) mouseY - this.topPos;
            if (mx >= 11 && mx < 241 && my >= 14 && my < 191) {
                return false;
            }
        }
        if (!hasSelectedShip()) {
            return false;
        }
        if (this.stationSuggestions != null && this.stationSuggestions.mouseScrolled(Mth.clamp(scrollY, -1.0D, 1.0D))) {
            return true;
        }
        if (this.editorMode == EditorMode.ROUTE) {
            int max = Math.max(0, this.routeChoices.size() - ROUTE_SELECTOR_VISIBLE_ROWS);
            this.routeChoiceScroll = Mth.clamp(this.routeChoiceScroll + (scrollY > 0 ? -1 : 1), 0, max);
            this.routeChoiceSelected = Mth.clamp(this.routeChoiceSelected, this.routeChoiceScroll, Math.min(this.routeChoices.size() - 1, this.routeChoiceScroll + ROUTE_SELECTOR_VISIBLE_ROWS - 1));
            return true;
        }
        if (this.editorMode == EditorMode.CONDITION) {
            int mx = (int) mouseX - this.leftPos;
            int my = (int) mouseY - this.topPos;
            if (inside(mx, my, 56, 65, 143, 16)) {
                cycleEditedConditionType(scrollY > 0 ? -1 : 1);
                return true;
            }
        }
        if (this.editorMode != EditorMode.NONE) {
            return true;
        }
        Hit hoverHit = hitAt((int) mouseX, (int) mouseY);
        if (hoverHit.type == HitType.CONDITION
                || hoverHit.type == HitType.ADD_CONDITION
                || hoverHit.type == HitType.ADD_ALTERNATIVE
                || hoverHit.type == HitType.CONDITION_SCROLL_LEFT
                || hoverHit.type == HitType.CONDITION_SCROLL_RIGHT) {
            AirshipScheduleEntry entry = hoverHit.index >= 0 && hoverHit.index < currentSchedule().entries().size()
                    ? currentSchedule().entries().get(hoverHit.index)
                    : null;
            if (entry != null && isConditionAreaScrollable(entry)) {
                scrollConditionColumns(hoverHit.index, scrollY > 0 ? -1 : 1);
                return true;
            }
        }
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Mth.clamp((int) (this.scrollOffset - scrollY * 12), 0, maxScroll);
            return true;
        }
        this.scrollOffset = 0;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pendingDeleteStopIndex != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeDeleteStopConfirm();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmDeleteStop();
                return true;
            }
            return true;
        }
        if (this.noRoutePopupOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeNoRoutePopup();
            }
            return true;
        }
        if (this.cargoStablePopupOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeCargoStablePopup();
            }
            return true;
        }
        if (!isAssignedShipLocked()
                && this.assignedShipBox != null
                && this.assignedShipBox.isFocused()
                && this.assignedShipSuggestions != null
                && this.assignedShipSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.stationSuggestions != null && this.stationSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.editorMode != EditorMode.NONE) {
            closeEditor();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.editorMode != EditorMode.NONE) {
                confirmEditor();
                return true;
            }
            saveTitle();
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (this.titleBox != null && this.titleBox.isFocused()) {
                return this.titleBox.keyPressed(keyCode, scanCode, modifiers) || this.titleBox.canConsumeInput();
            }
            if (!isAssignedShipLocked() && this.assignedShipBox != null && this.assignedShipBox.isFocused()) {
                return this.assignedShipBox.keyPressed(keyCode, scanCode, modifiers) || this.assignedShipBox.canConsumeInput();
            }
            if (this.stationFilterBox != null && this.stationFilterBox.isFocused()) {
                return this.stationFilterBox.keyPressed(keyCode, scanCode, modifiers) || this.stationFilterBox.canConsumeInput();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.noRoutePopupOpen) {
            return true;
        }
        if (pendingDeleteStopIndex != null) {
            return true;
        }
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.titleBox != null && this.titleBox.isFocused() && this.titleBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (!isAssignedShipLocked() && this.assignedShipBox != null && this.assignedShipBox.isFocused() && this.assignedShipBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.stationFilterBox != null && this.stationFilterBox.isFocused() && this.stationFilterBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isAssignedShipLocked() {
        return this.menu.openedFromTransponder();
    }

    private boolean isTransponderManagedPlan() {
        return this.menu.openedFromTransponder();
    }

    private boolean isScheduleReadOnly() {
        return this.minecraft != null
                && this.minecraft.player != null
                && this.menu.isReadOnly(this.minecraft.player);
    }

    private void updateSkipStopButtonState() {
        if (this.skipStopButton == null) {
            return;
        }
        boolean visible = skipStopButtonVisible();
        this.skipStopButton.visible = visible;
        this.skipStopButton.active = visible && this.editorMode == EditorMode.NONE && canSkipStopFromUiState();
    }

    private boolean skipStopButtonVisible() {
        return this.menu.openedFromTransponder()
                && !this.noRoutePopupOpen
                && this.pendingDeleteStopIndex == null;
    }

    private boolean canSkipStopFromUiState() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (this.menu.skipStopUiActive(this.minecraft.player)) {
            return true;
        }
        return openedTransponder().map(ShipTransponderBlockEntity::runtimeStatus)
                .map(status -> status == RouteStatus.WAITING || status == RouteStatus.HELD_FAULTED)
                .orElse(false);
    }

    private Optional<ShipTransponderBlockEntity> openedTransponder() {
        if (this.minecraft == null || this.minecraft.level == null || this.menu.originTransponderPos().isEmpty()) {
            return Optional.empty();
        }
        if (this.minecraft.level.getBlockEntity(this.menu.originTransponderPos().get()) instanceof ShipTransponderBlockEntity transponder) {
            return Optional.of(transponder);
        }
        return Optional.empty();
    }

    @Override
    public void removed() {
        this.leftMouseDown = false;
        this.showPlayerInventorySlots = true;
        saveTitle();
        if (this.reopenSourceScreenOnClose
                && this.minecraft != null
                && this.minecraft.player != null
                && this.menu.originTransponderPos().isPresent()) {
            PacketDistributor.sendToServer(new ReopenShipTransponderPayload(
                    this.menu.originTransponderPos().get(),
                    this.menu.returnToRecordingMode()
            ));
            this.reopenSourceScreenOnClose = false;
        }
        super.removed();
    }

    private void cachePlayerInventorySlots() {
        if (!this.playerInventorySlots.isEmpty()) {
            return;
        }
        int count = this.menu.slots.size();
        int start = Math.max(0, count - 36);
        for (int i = start; i < count; i++) {
            this.playerInventorySlots.add(this.menu.slots.get(i));
        }
    }

    private boolean isPlayerInventoryRegion(int x, int y, int width, int height) {
        if (width != 16 || height != 16) {
            return false;
        }
        for (Slot slot : this.playerInventorySlots) {
            if (slot.x == x && slot.y == y) {
                return true;
            }
        }
        return false;
    }

    private Hit hitAt(int mouseX, int mouseY) {
        int x = mouseX - this.leftPos - 25;
        int y = mouseY - this.topPos - 25 + this.scrollOffset;
        if (x < 0 || x >= 210 || y < 0 || y >= scheduleContentHeight() + 28) {
            return Hit.NONE;
        }

        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = 0; i < entries.size(); i++) {
            AirshipScheduleEntry entry = entries.get(i);
            int cardHeight = cardHeight(entry);
            if (y >= cardHeight + 5) {
                y -= cardHeight + 10;
                if (y < 0) {
                    return Hit.NONE;
                }
                continue;
            }

            if (x > 25 && x <= 155 && y > 4 && y <= 20) {
                return new Hit(HitType.STATION, i);
            }
            if (x > 180 && x <= 193) {
                if (y > 0 && y <= 15) {
                    return new Hit(HitType.REMOVE, i);
                }
                if (!isTransponderManagedPlan() && y > cardHeight - 15) {
                    return new Hit(HitType.DUPLICATE, i);
                }
            }
            if (x > 194) {
                if (y > 7 && y <= 20 && i > 0) {
                    return new Hit(HitType.MOVE_UP, i);
                }
                if (y > 20 && y <= 33 && i < entries.size() - 1) {
                    return new Hit(HitType.MOVE_DOWN, i);
                }
            }
            if (isConditionAreaScrollable(entry)) {
                int center = (cardHeight - 8 + CARD_HEADER) / 2;
                if (x >= 12 && x <= 26 && y >= CARD_HEADER && y <= cardHeight - 4) {
                    return conditionScrollColumns.getOrDefault(i, 0) > 0
                            ? new Hit(HitType.CONDITION_SCROLL_LEFT, i)
                            : Hit.NONE;
                }
                if (x >= 168 && x <= 184 && y >= CARD_HEADER && y <= cardHeight - 4) {
                    return conditionScrollColumns.getOrDefault(i, 0) < Math.max(0, entry.conditionGroups().size() - 1)
                            ? new Hit(HitType.CONDITION_SCROLL_RIGHT, i)
                            : Hit.NONE;
                }
                if (x >= 15 && x < 19 && y >= center && y < center + 8 && conditionScrollColumns.getOrDefault(i, 0) > 0) {
                    return new Hit(HitType.CONDITION_SCROLL_LEFT, i);
                }
                if (x >= 178 && x < 182 && y >= center && y < center + 8
                        && conditionScrollColumns.getOrDefault(i, 0) < Math.max(0, entry.conditionGroups().size() - 1)) {
                    return new Hit(HitType.CONDITION_SCROLL_RIGHT, i);
                }
            }
            int conditionX = x - 26;
            int conditionY = y - 29;
            conditionX += conditionScrollPixels(entry, conditionScrollColumns.getOrDefault(i, 0));
            int groupX = 0;
            for (int groupIndex = 0; groupIndex < entry.conditionGroups().size(); groupIndex++) {
                List<AirshipScheduleCondition> group = entry.conditionGroups().get(groupIndex);
                int groupWidth = conditionColumnWidth(group, entry.waitUnit());
                if (conditionX >= groupX && conditionX < groupX + groupWidth) {
                    int row = conditionY / 18;
                    int rowOffset = conditionY % 18;
                    if (row >= 0 && row < group.size() && rowOffset <= 16) {
                        return new Hit(HitType.CONDITION, i, groupIndex, row);
                    }
                    int addY = group.size() * 18;
                    if (conditionY > addY && conditionY <= addY + 10 && conditionX >= groupX + groupWidth / 2 - 5 && conditionX < groupX + groupWidth / 2 + 5) {
                        return new Hit(HitType.ADD_CONDITION, i, groupIndex, -1);
                    }
                    return Hit.NONE;
                }
                groupX += groupWidth + 10;
            }
            if (conditionX >= groupX - 3 && conditionX <= groupX + 13 && conditionY >= 0 && conditionY <= 20) {
                return new Hit(HitType.ADD_ALTERNATIVE, i, entry.conditionGroups().size(), -1);
            }
            return Hit.NONE;
        }

        if (x >= 18 && x <= 50 && y >= 0 && y <= 20) {
            return new Hit(HitType.ADD_ENTRY, -1);
        }
        return Hit.NONE;
    }

    private void openStationEditor(int index) {
        this.editorMode = EditorMode.STATION;
        this.editorEntryIndex = index;
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setValue("");
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        buildStationEditorWidgets();
        setFocused(null);
    }

    private void openConditionEditor(int index, int conditionGroup, int conditionIndex) {
        if (isScheduleReadOnly()) {
            return;
        }
        this.editorMode = EditorMode.CONDITION;
        this.editorEntryIndex = index;
        this.editorConditionGroup = conditionGroup;
        this.editorConditionIndex = conditionIndex;
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.stationSuggestions = null;
        buildConditionEditorWidgets();
        setFocused(null);
    }

    private void openConditionEditorForLast(int entryIndex, int conditionGroup) {
        if (isScheduleReadOnly()) {
            return;
        }
        if (entryIndex < 0 || entryIndex >= currentSchedule().entries().size()) {
            return;
        }
        AirshipScheduleEntry entry = currentSchedule().entries().get(entryIndex);
        if (conditionGroup < 0 || conditionGroup >= entry.conditionGroups().size()) {
            return;
        }
        List<AirshipScheduleCondition> group = entry.conditionGroups().get(conditionGroup);
        if (group.isEmpty()) {
            return;
        }
        openConditionEditor(entryIndex, conditionGroup, group.size() - 1);
    }

    private void buildStationEditorWidgets() {
        this.editorSubWidgets.reset();
        this.editorData.remove("Text");
        this.editorData.remove("Duration");
        this.editorData.remove("Unit");
        this.editorData.putString("Text", currentEntry().map(AirshipScheduleEntry::targetStationName).orElse(""));

        ModularGuiLineBuilder builder = this.editorSubWidgets.newLineBuilder(this.font, this.leftPos + 77, this.topPos + 92)
                .speechBubble();
        builder.addTextInput(0, 121, (box, tooltip) -> {
            this.editorStationBox = box;
            box.setHint(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_search_hint"));
            box.setMaxLength(64);
            box.setResponder(this::onStationEditorTextChanged);
            tooltip.withTooltip(List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_wildcard"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.station_name_nearest")
            ));
        }, "Text");
        this.editorSubWidgets.load(this.editorData);
        this.stationSuggestions = createStationSuggestions(this.editorStationBox);
        onStationEditorTextChanged(this.editorStationBox == null ? "" : this.editorStationBox.getValue());
    }

    private void buildConditionEditorWidgets() {
        closeCargoStablePopup();
        this.editorSubWidgets.reset();
        this.editorData.remove("Text");
        this.editorData.remove("Duration");
        this.editorData.remove("Unit");
        this.editorData.remove("Operator");
        this.editorData.remove("Measure");
        this.editorData.remove("Target");
        this.editorData.remove("Stable");
        this.editorData.remove("Powered");
        this.editorData.remove("Hour");
        this.editorData.remove("Minute");
        this.editorData.remove("Rotation");
        AirshipScheduleCondition condition = currentCondition().orElse(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)));
        WaitDurationUnit waitUnit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        boolean cargoThreshold = isCargoThresholdWaitType(condition.waitCondition().type());
        boolean cargoWait = isCargoWaitType(condition.waitCondition().type());
        boolean redstoneLink = isRedstoneLinkWaitType(condition.waitCondition().type());
        boolean timeOfDay = isTimeOfDayWaitType(condition.waitCondition().type());
        boolean itemThreshold = condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD;
        boolean fluidThreshold = condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD;
        if (cargoThreshold) {
            this.editorData.putString("Duration", Integer.toString(Mth.clamp(waitAmount(condition.waitCondition(), waitUnit), 0, 99)));
        } else if (!redstoneLink && !timeOfDay) {
            this.editorData.putInt("Duration", waitAmount(condition.waitCondition(), waitUnit));
        }
        this.editorData.putInt("Unit", waitUnit.ordinal());
        this.editorData.putInt("Operator", condition.waitCondition().cargoOperator());
        this.editorData.putInt("Measure", condition.waitCondition().cargoMeasure());
        this.editorData.putInt("Target", condition.waitCondition().cargoTarget().ordinal());
        this.editorData.putInt("Stable", cargoStableSeconds(condition.waitCondition()));
        this.editorData.putInt("Powered", condition.waitCondition().redstonePowered() ? 0 : 1);
        this.editorData.putInt("Hour", condition.waitCondition().timeOfDayHour());
        this.editorData.putInt("Minute", condition.waitCondition().timeOfDayMinute());
        this.editorData.putInt("Rotation", condition.waitCondition().timeOfDayRotation());

        ModularGuiLineBuilder builder = this.editorSubWidgets.newLineBuilder(this.font, this.leftPos + 77, this.topPos + 92)
                .speechBubble();
        MutableObject<Label> timeEditorLabel = new MutableObject<>();
        MutableObject<Label> cargoStableLabel = new MutableObject<>();
        if (cargoThreshold) {
            builder.addSelectionScrollInput(0, 13, (input, label) -> {
                input.forOptions(Ops.translatedOptions())
                        .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_holds"));
                input.format(state -> Component.literal(Ops.values()[Mth.clamp(state, 0, Ops.values().length - 1)].formatted));
                label.setX(label.getX() - 1);
                label.withShadow();
            }, "Operator");
            builder.addIntegerTextInput(14, 22, (box, tooltip) -> {
                box.setMaxLength(2);
            }, "Duration");
            builder.addSelectionScrollInput(37, itemThreshold ? 41 : 43, (input, label) -> {
                List<Component> options = itemThreshold
                        ? List.of(
                                Component.translatable("create.schedule.condition.threshold.items"),
                                Component.translatable("create.schedule.condition.threshold.stacks")
                        )
                        : List.of(Component.translatable("create.schedule.condition.threshold.buckets"));
                this.editorUnitInput = (SelectionScrollInput) input.forOptions(options);
                input.titled(itemThreshold
                        ? Component.translatable("create.schedule.condition.threshold.item_measure")
                        : null);
                if (!itemThreshold) {
                    label.setX(label.getX() - 3);
                }
                label.withShadow();
            }, "Measure");
            builder.addSelectionScrollInput(itemThreshold ? 79 : 81, itemThreshold ? 42 : 41, (input, label) -> {
                input.forOptions(cargoTargetOptions())
                        .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_target"));
                if (!itemThreshold) {
                    label.setX(label.getX() - 2);
                }
                label.withShadow();
            }, "Target");
        } else if (cargoWait) {
            builder.addSelectionScrollInput(0, 94, (input, label) -> {
                input.forOptions(cargoTargetOptions())
                        .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_target"));
                label.withShadow();
            }, "Target");
            builder.addScrollInput(95, 26, (input, label) -> {
                input.withRange(0, 100)
                        .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_stable_for"))
                        .withShiftStep(10)
                        .calling(value -> label.text = cargoStableSecondsLabel(value));
                cargoStableLabel.setValue(label);
                input.lockedTooltipX = -15;
                input.lockedTooltipY = 35;
                label.withShadow();
            }, "Stable");
        } else if (redstoneLink) {
            builder.addSelectionScrollInput(40, 81, (input, label) -> {
                input.forOptions(List.of(
                                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.powered"),
                                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.unpowered")
                        ))
                        .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.state"));
                label.withShadow();
            }, "Powered");
        } else if (timeOfDay) {
            MutableObject<ScrollInput> minuteInput = new MutableObject<>();
            MutableObject<ScrollInput> hourInput = new MutableObject<>();
            MutableObject<Label> timeLabel = new MutableObject<>();
            builder.addScrollInput(0, 16, (input, label) -> {
                input.withRange(0, 24);
                timeLabel.setValue(label);
                timeEditorLabel.setValue(label);
                hourInput.setValue(input);
            }, "Hour");
            builder.addScrollInput(18, 16, (input, label) -> {
                input.withRange(0, 60)
                        .titled(Component.translatable("create.generic.daytime.minute"));
                minuteInput.setValue(input);
                label.visible = false;
            }, "Minute");
            builder.addSelectionScrollInput(52, 68, (input, label) -> {
                input.forOptions(timeOfDayRotationOptions())
                        .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation"));
                label.withShadow();
            }, "Rotation");
            hourInput.getValue()
                    .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.start_time"))
                    .calling(value -> {
                        if (timeLabel.getValue() != null && minuteInput.getValue() != null) {
                            timeLabel.getValue().text = timeOfDayDisplay(value, minuteInput.getValue().getState(), true);
                        }
                    })
                    .writingTo(null)
                    .withShiftStep(6);
            minuteInput.getValue()
                    .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.start_time"))
                    .calling(value -> {
                        if (timeLabel.getValue() != null && hourInput.getValue() != null) {
                            timeLabel.getValue().text = timeOfDayDisplay(hourInput.getValue().getState(), value, true);
                        }
                    })
                    .writingTo(null)
                    .withShiftStep(15);
            minuteInput.getValue().lockedTooltipX = hourInput.getValue().lockedTooltipX = -15;
            minuteInput.getValue().lockedTooltipY = hourInput.getValue().lockedTooltipY = 35;
            if (timeLabel.getValue() != null && hourInput.getValue() != null && minuteInput.getValue() != null) {
                timeLabel.getValue().text = timeOfDayDisplay(hourInput.getValue().getState(), minuteInput.getValue().getState(), true);
            }
            builder.customArea(0, 52);
            builder.customArea(52, 69);
        } else {
            builder.addScrollInput(0, 31, (input, label) -> {
                this.editorDurationInput = input.withRange(0, 10001)
                        .titled(Component.translatable("create.generic.duration"))
                        .withShiftStep(15)
                        .calling(state -> {
                        });
                input.lockedTooltipX = -15;
                input.lockedTooltipY = 35;
                label.withShadow();
            }, "Duration");
            builder.addSelectionScrollInput(36, 85, (input, label) -> {
                this.editorUnitInput = (SelectionScrollInput) input.forOptions(TimeUnit.translatedOptions());
                input.titled(Component.translatable("create.generic.timeUnit"));
                label.withShadow();
            }, "Unit");
        }
        this.editorSubWidgets.load(this.editorData);
        if (timeOfDay && timeEditorLabel.getValue() != null) {
            timeEditorLabel.getValue().text = timeOfDayDisplay(
                    Mth.clamp(this.editorData.getInt("Hour"), 0, 23),
                    Mth.clamp(this.editorData.getInt("Minute"), 0, 59),
                    true
            );
        }
        if (cargoWait && cargoStableLabel.getValue() != null) {
            cargoStableLabel.getValue().text = cargoStableSecondsLabel(
                    Mth.clamp(this.editorData.getInt("Stable"), 0, 99)
            );
        }
    }

    private void onStationEditorTextChanged(String value) {
        if (this.stationSuggestions != null) {
            this.stationSuggestions.updateCommandInfo();
        }
        if (this.editorStationBox == null) {
            return;
        }
        if (!this.editorStationBox.isFocused() || value == null || value.isBlank()) {
            this.editorStationBox.setSuggestion("");
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String suggestion = availableStations().stream()
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .filter(name -> name.length() > value.length())
                .findFirst()
                .map(name -> clipSuggestionSuffix(this.editorStationBox, value, name.substring(value.length()), 0))
                .orElse("");
        this.editorStationBox.setSuggestion(suggestion);
    }

    private String clipSuggestionSuffix(EditBox box, String typedValue, String suffix, int extraWidthPixels) {
        int availableWidth = Math.max(0, box.getWidth() - this.font.width(typedValue) - 18 + extraWidthPixels);
        return this.font.plainSubstrByWidth(suffix, availableWidth);
    }

    private void closeEditor() {
        closeCargoStablePopup();
        this.editorMode = EditorMode.NONE;
        this.routeChoices.clear();
        this.routeChoiceSelected = 0;
        this.routeChoiceScroll = 0;
        closeNoRoutePopup();
        this.editorSubWidgets.reset();
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.titleBox.visible = false;
        this.titleBox.active = false;
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.stationSuggestions = null;
        setFocused(null);
    }

    private void confirmEditor() {
        if (isScheduleReadOnly()) {
            closeEditor();
            return;
        }
        if (this.editorMode == EditorMode.STATION) {
            saveEditorData();
            List<AirshipStationSnapshot> matches = filteredStations(this.editorData.getString("Text"));
            if (!matches.isEmpty()) {
                selectStation(matches.getFirst());
                return;
            }
        }
        if (this.editorMode == EditorMode.CONDITION) {
            applyEditedConditionFromWidgets();
            syncSchedule();
            closeEditor();
            return;
        }
        if (this.editorMode == EditorMode.ROUTE) {
            pinSelectedRouteChoice();
            return;
        }
        closeEditor();
    }

    private void saveEditorData() {
        this.editorSubWidgets.save(this.editorData);
        saveCargoStablePopupValue();
    }

    private void openCargoStablePopup() {
        saveEditorData();
        closeCargoStablePopup();
        this.cargoStablePopupOpen = true;
        int x = cargoStablePopupX() + CARGO_STABLE_PANEL_FIELD_X;
        int y = cargoStablePopupY() + CARGO_STABLE_PANEL_FIELD_Y;
        this.cargoStablePopupInput = new ScrollInput(x, y, CARGO_STABLE_PANEL_FIELD_WIDTH, 18);
        this.cargoStablePopupInput
                .withRange(0, 100)
                .withShiftStep(10)
                .titled(Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_stable_for"))
                .calling(value -> this.editorData.putInt("Stable", value))
                .setState(Mth.clamp(this.editorData.getInt("Stable"), 0, 99));
        this.cargoStablePopupInput.lockedTooltipX = -15;
        this.cargoStablePopupInput.lockedTooltipY = 35;
        this.cargoStablePopupConfirmButton = new IconButton(
                cargoStablePopupX() + CARGO_STABLE_PANEL_CONFIRM_X,
                cargoStablePopupY() + CARGO_STABLE_PANEL_CONFIRM_Y,
                AllIcons.I_CONFIRM
        );
        this.cargoStablePopupConfirmButton.withCallback(this::closeCargoStablePopup);
        addRenderableWidget(this.cargoStablePopupInput);
    }

    private void closeCargoStablePopup() {
        saveCargoStablePopupValue();
        if (this.cargoStablePopupInput != null) {
            removeWidget(this.cargoStablePopupInput);
            this.cargoStablePopupInput = null;
        }
        this.cargoStablePopupConfirmButton = null;
        this.cargoStablePopupOpen = false;
    }

    private void saveCargoStablePopupValue() {
        if (this.cargoStablePopupInput != null) {
            this.editorData.putInt("Stable", this.cargoStablePopupInput.getState());
        }
    }

    private int cargoStablePopupX() {
        return this.leftPos + (this.imageWidth - CARGO_STABLE_PANEL_WIDTH) / 2;
    }

    private int cargoStablePopupY() {
        return this.topPos + 33;
    }

    private void pressAction(int actionId) {
        applyLocalAction(actionId);
        if (!isScheduleReadOnly() && actionId < AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE) {
            syncSchedule();
        }
    }

    private void sendServerAction(int actionId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, actionId);
        }
    }

    private void playUiButtonClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void saveTitle() {
        if (isScheduleReadOnly()) {
            return;
        }
        if (this.titleBox != null) {
            this.localSchedule = currentSchedule().withTitle(this.titleBox.getValue());
            syncSchedule();
        }
    }

    private void syncSchedule() {
        if (isScheduleReadOnly()) {
            return;
        }
        PacketDistributor.sendToServer(new UpdateAirshipSchedulePayload(AirshipScheduleNbtSerializer.write(currentSchedule())));
    }

    private void applyLocalAction(int actionId) {
        if (actionId >= AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE) {
            this.selectedIndex = Math.max(0, Math.min(actionId - AirshipScheduleMenu.ACTION_SELECT_ENTRY_BASE, Math.max(0, currentSchedule().entries().size() - 1)));
            return;
        }
        if (isScheduleReadOnly()) {
            if (actionId == AirshipScheduleMenu.ACTION_SELECT_PREVIOUS) {
                selectLocally(-1);
            } else if (actionId == AirshipScheduleMenu.ACTION_SELECT_NEXT) {
                selectLocally(1);
            }
            return;
        }
        if (isTransponderManagedPlan()
                && (actionId == AirshipScheduleMenu.ACTION_ADD_TRAVEL
                || actionId == AirshipScheduleMenu.ACTION_DUPLICATE
                || actionId == AirshipScheduleMenu.ACTION_CYCLE_TARGET_STATION)) {
            return;
        }

        AirshipSchedule schedule = currentSchedule();
        clampSelectedIndex();
        switch (actionId) {
            case AirshipScheduleMenu.ACTION_ADD_TRAVEL -> addTravelLocally(schedule);
            case AirshipScheduleMenu.ACTION_REMOVE -> removeSelectedLocally(schedule);
            case AirshipScheduleMenu.ACTION_DUPLICATE -> duplicateSelectedLocally(schedule);
            case AirshipScheduleMenu.ACTION_MOVE_UP -> moveSelectedLocally(schedule, -1);
            case AirshipScheduleMenu.ACTION_MOVE_DOWN -> moveSelectedLocally(schedule, 1);
            case AirshipScheduleMenu.ACTION_WAIT_DOWN -> {
                if (this.editorMode == EditorMode.CONDITION) {
                    adjustEditedConditionWaitLocally(schedule, -1);
                } else {
                    adjustSelectedWaitLocally(schedule, -1);
                }
            }
            case AirshipScheduleMenu.ACTION_WAIT_UP -> {
                if (this.editorMode == EditorMode.CONDITION) {
                    adjustEditedConditionWaitLocally(schedule, 1);
                } else {
                    adjustSelectedWaitLocally(schedule, 1);
                }
            }
            case AirshipScheduleMenu.ACTION_TOGGLE_LOOP -> this.localSchedule = schedule.withLoop(!schedule.loop());
            case AirshipScheduleMenu.ACTION_SELECT_PREVIOUS -> selectLocally(-1);
            case AirshipScheduleMenu.ACTION_SELECT_NEXT -> selectLocally(1);
            case AirshipScheduleMenu.ACTION_CYCLE_TARGET_STATION -> cycleTargetStationLocally(schedule);
            case AirshipScheduleMenu.ACTION_TOGGLE_WAIT -> {
                if (this.editorMode == EditorMode.CONDITION) {
                    toggleEditedConditionWaitLocally(schedule);
                } else {
                    toggleSelectedWaitLocally(schedule);
                }
            }
            case AirshipScheduleMenu.ACTION_CYCLE_WAIT_UNIT -> cycleSelectedWaitUnitLocally(schedule);
            case AirshipScheduleMenu.ACTION_ADD_CONDITION -> addConditionLocally(schedule);
            case AirshipScheduleMenu.ACTION_ADD_ALTERNATIVE_CONDITION -> addAlternativeConditionLocally(schedule);
            default -> {
            }
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private void addTravelLocally(AirshipSchedule schedule) {
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        int insertIndex = entries.isEmpty() ? 0 : Math.min(this.selectedIndex + 1, entries.size());
        entries.add(insertIndex, AirshipScheduleEntry.blankTravel());
        this.selectedIndex = insertIndex;
        this.localSchedule = schedule.withEntries(entries);
    }

    private void removeSelectedLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.remove(this.selectedIndex);
        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, entries.size() - 1));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void duplicateSelectedLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.add(this.selectedIndex + 1, entries.get(this.selectedIndex));
        this.selectedIndex++;
        this.localSchedule = schedule.withEntries(entries);
    }

    private void moveSelectedLocally(AirshipSchedule schedule, int direction) {
        if (schedule.entries().size() < 2) {
            return;
        }
        int targetIndex = this.selectedIndex + direction;
        if (targetIndex < 0 || targetIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry selected = entries.remove(this.selectedIndex);
        entries.add(targetIndex, selected);
        this.selectedIndex = targetIndex;
        this.localSchedule = schedule.withEntries(entries);
    }

    private void adjustSelectedWaitLocally(AirshipSchedule schedule, int direction) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.selectedIndex);
        int currentTicks = entry.waitCondition().type() == WaitConditionType.TIMED ? entry.waitCondition().durationTicks() : 0;
        int nextTicks = Math.max(0, currentTicks + direction * entry.waitUnit().ticksPerStep() * 5);
        entries.set(this.selectedIndex, entry.withWaitCondition(nextTicks == 0 ? WaitCondition.none() : WaitCondition.timed(nextTicks)));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void toggleSelectedWaitLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.selectedIndex);
        WaitCondition next = entry.waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(Math.max(entry.waitUnit().ticksPerStep() * 5, WaitCondition.DEFAULT_TIMED_WAIT_TICKS))
                : WaitCondition.none();
        entries.set(this.selectedIndex, entry.withWaitCondition(next));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void cycleSelectedWaitUnitLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.selectedIndex);
        entries.set(this.selectedIndex, entry.withWaitUnit(entry.waitUnit().next()));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void addConditionLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        addConditionLocally(this.selectedIndex, 0);
    }

    private void addAlternativeConditionLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return;
        }
        addAlternativeConditionLocally(this.selectedIndex);
    }

    private void addConditionLocally(int entryIndex, int groupIndex) {
        updateConditionGroups(entryIndex, groups -> {
            if (groups.isEmpty()) {
                groups.add(new ArrayList<>());
            }
            int targetGroup = Math.max(0, Math.min(groupIndex, groups.size() - 1));
            WaitCondition wait = groups.get(targetGroup).isEmpty()
                    ? WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                    : groups.get(targetGroup).getLast().waitCondition();
            groups.get(targetGroup).add(AirshipScheduleCondition.scheduledDelay(wait));
        });
    }

    private void addAlternativeConditionLocally(int entryIndex) {
        updateConditionGroups(entryIndex, groups -> groups.add(new ArrayList<>(
                List.of(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)))
        )));
    }

    private void removeConditionLocally(int entryIndex, int groupIndex, int conditionIndex) {
        updateConditionGroups(entryIndex, groups -> {
            if (groupIndex < 0 || groupIndex >= groups.size()) {
                return;
            }
            List<AirshipScheduleCondition> group = groups.get(groupIndex);
            if (conditionIndex < 0 || conditionIndex >= group.size()) {
                return;
            }
            if (groups.size() == 1 && group.size() == 1) {
                return;
            }
            group.remove(conditionIndex);
            if (group.isEmpty()) {
                groups.remove(groupIndex);
            }
        });
    }

    private void adjustEditedConditionWaitLocally(AirshipSchedule schedule, int direction) {
        AirshipScheduleEntry entry = currentEntry().orElse(null);
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (entry == null || condition == null) {
            return;
        }
        int currentTicks = switch (condition.waitCondition().type()) {
            case TIMED -> condition.waitCondition().durationTicks();
            case UNTIL_DOCKED -> condition.waitCondition().runtimeTicks();
            case UNTIL_IDLE -> condition.waitCondition().idleTicks();
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD -> condition.waitCondition().durationTicks();
            case UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> condition.waitCondition().cargoStabilityTicks();
            default -> 0;
        };
        int step = switch (condition.waitCondition().type()) {
            case UNTIL_ITEM_THRESHOLD -> condition.waitCondition().cargoMeasure() == 1 ? 1 : 16;
            case UNTIL_FLUID_THRESHOLD -> 1;
            case UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> 20;
            default -> entry.waitUnit().ticksPerStep() * 5;
        };
        int nextTicks = Math.max(0, currentTicks + direction * step);
        if (isCargoThresholdWaitType(condition.waitCondition().type())) {
            nextTicks = Math.min(99, nextTicks);
        }
        setEditedConditionWait(waitConditionWithTicks(condition.waitCondition().type(), nextTicks));
    }

    private void toggleEditedConditionWaitLocally(AirshipSchedule schedule) {
        AirshipScheduleEntry entry = currentEntry().orElse(null);
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (entry == null || condition == null) {
            return;
        }
        WaitCondition next = condition.waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(Math.max(entry.waitUnit().ticksPerStep() * 5, WaitCondition.DEFAULT_TIMED_WAIT_TICKS))
                : WaitCondition.none();
        setEditedConditionWait(next);
    }

    private void cycleEditedConditionType() {
        cycleEditedConditionType(1);
    }

    private void cycleEditedConditionType(int direction) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (condition == null) {
            return;
        }
        List<WaitConditionType> types = conditionSelectorTypes();
        int current = Math.max(0, types.indexOf(condition.waitCondition().type()));
        int next = Math.floorMod(current + Integer.signum(direction), types.size());
        WaitConditionType nextType = types.get(next);
        setEditedConditionWait(defaultWaitConditionForType(nextType));
        buildConditionEditorWidgets();
    }

    private List<WaitConditionType> conditionSelectorTypes() {
        return List.of(
                WaitConditionType.TIMED,
                WaitConditionType.UNTIL_DOCKED,
                WaitConditionType.UNTIL_IDLE,
                WaitConditionType.REDSTONE_LINK,
                WaitConditionType.TIME_OF_DAY,
                WaitConditionType.UNTIL_ITEM_EMPTY,
                WaitConditionType.UNTIL_ITEM_FULL,
                WaitConditionType.UNTIL_FLUID_EMPTY,
                WaitConditionType.UNTIL_FLUID_FULL,
                WaitConditionType.UNTIL_ITEM_THRESHOLD,
                WaitConditionType.UNTIL_FLUID_THRESHOLD
        );
    }

    private WaitCondition defaultWaitConditionForType(WaitConditionType type) {
        return switch (type) {
            case UNTIL_DOCKED -> WaitCondition.untilDocked(WaitCondition.DEFAULT_TIMED_WAIT_TICKS);
            case UNTIL_IDLE -> WaitCondition.untilIdle(WaitCondition.DEFAULT_TIMED_WAIT_TICKS, 0);
            case REDSTONE_LINK -> WaitCondition.redstoneLink(ItemStack.EMPTY, ItemStack.EMPTY, true);
            case TIME_OF_DAY -> WaitCondition.timeOfDay(12, 0, 0);
            case UNTIL_ITEM_EMPTY -> WaitCondition.itemEmpty(0, CargoWaitTarget.SHIP_CARGO);
            case UNTIL_ITEM_FULL -> WaitCondition.itemFull(0, CargoWaitTarget.SHIP_CARGO);
            case UNTIL_FLUID_EMPTY -> WaitCondition.fluidEmpty(0, CargoWaitTarget.SHIP_CARGO);
            case UNTIL_FLUID_FULL -> WaitCondition.fluidFull(0, CargoWaitTarget.SHIP_CARGO);
            case UNTIL_FLUID_THRESHOLD -> WaitCondition.fluidThreshold(10, 0, 0, 0, ItemStack.EMPTY, CargoWaitTarget.SHIP_CARGO);
            case UNTIL_ITEM_THRESHOLD -> WaitCondition.itemThreshold(10, 0, 0, 0, ItemStack.EMPTY, CargoWaitTarget.SHIP_CARGO);
            default -> WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS);
        };
    }

    private void setEditedConditionWait(WaitCondition waitCondition) {
        updateConditionGroups(this.editorEntryIndex, groups -> {
            if (this.editorConditionGroup < 0 || this.editorConditionGroup >= groups.size()) {
                return;
            }
            List<AirshipScheduleCondition> group = groups.get(this.editorConditionGroup);
            if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
                return;
            }
            group.set(this.editorConditionIndex, AirshipScheduleCondition.fromWaitCondition(waitCondition));
        });
    }

    private boolean isCargoWait(WaitCondition waitCondition) {
        return isCargoWaitType(waitCondition.type());
    }

    private boolean isRedstoneLinkWaitType(WaitConditionType type) {
        return type == WaitConditionType.REDSTONE_LINK || type == WaitConditionType.REDSTONE;
    }

    private boolean isTimeOfDayWaitType(WaitConditionType type) {
        return type == WaitConditionType.TIME_OF_DAY;
    }

    private List<Component> timeOfDayRotationOptions() {
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_24"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_12"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_6"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_4"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_3"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_2"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_1"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_0_45"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_0_30"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.rotation.every_0_15")
        );
    }

    private void setEditedConditionFilter(ItemStack stack) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (condition == null || !isCargoThresholdWaitType(condition.waitCondition().type())) {
            return;
        }
        ItemStack filter = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        WaitCondition current = condition.waitCondition();
        WaitCondition next = current.type() == WaitConditionType.UNTIL_ITEM_THRESHOLD
                ? WaitCondition.itemThreshold(current.durationTicks(), current.maxTicks(), current.cargoStabilityTicks(), current.cargoOperator(), current.cargoMeasure(), filter, current.cargoTarget())
                : WaitCondition.fluidThreshold(current.durationTicks(), current.maxTicks(), current.cargoStabilityTicks(), current.cargoOperator(), current.cargoMeasure(), filter, current.cargoTarget());
        setEditedConditionWait(next);
    }

    private void setEditedRedstoneFrequency(int slot, ItemStack stack) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        if (condition == null || !isRedstoneLinkWaitType(condition.waitCondition().type())) {
            return;
        }
        ItemStack frequency = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        WaitCondition current = condition.waitCondition();
        WaitCondition next = WaitCondition.redstoneLink(
                slot == 0 ? frequency : current.redstoneFrequencyFirst(),
                slot == 1 ? frequency : current.redstoneFrequencySecond(),
                current.redstonePowered()
        );
        setEditedConditionWait(next);
    }

    private void applyEditedConditionFromWidgets() {
        saveEditorData();
        WaitDurationUnit unit = WaitDurationUnit.values()[Mth.clamp(this.editorData.getInt("Unit"), 0, WaitDurationUnit.values().length - 1)];
        WaitConditionType type = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .map(WaitCondition::type)
                .orElse(WaitConditionType.TIMED);
        WaitCondition current = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .orElse(defaultWaitConditionForType(type));
        if (isRedstoneLinkWaitType(type)) {
            setEditedConditionWait(WaitCondition.redstoneLink(
                    current.redstoneFrequencyFirst(),
                    current.redstoneFrequencySecond(),
                    this.editorData.getInt("Powered") == 0
            ));
            return;
        }
        if (isTimeOfDayWaitType(type)) {
            setEditedConditionWait(WaitCondition.timeOfDay(
                    Mth.clamp(this.editorData.getInt("Hour"), 0, 23),
                    Mth.clamp(this.editorData.getInt("Minute"), 0, 59),
                    Mth.clamp(this.editorData.getInt("Rotation"), 0, 9)
            ));
            return;
        }
        int amount = isCargoThresholdWaitType(type)
                ? Mth.clamp(parsePositiveInt(this.editorData.getString("Duration"), 10), 0, 99)
                : Math.max(0, this.editorData.getInt("Duration"));
        int operator = Mth.clamp(this.editorData.getInt("Operator"), 0, Ops.values().length - 1);
        int measure = Math.max(0, this.editorData.getInt("Measure"));
        CargoWaitTarget target = CargoWaitTarget.values()[Mth.clamp(this.editorData.getInt("Target"), 0, CargoWaitTarget.values().length - 1)];
        int cargoStableTicks = Math.max(0, this.editorData.getInt("Stable")) * 20;
        ItemStack filter = current.cargoFilter();
        int ticks = switch (type) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD -> amount;
            default -> switch (unit) {
                case TICKS -> amount;
                case SECONDS -> amount * 20;
                case MINUTES -> amount * 20 * 60;
            };
        };

        AirshipSchedule schedule = currentSchedule();
        if (this.editorEntryIndex < 0 || this.editorEntryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(this.editorEntryIndex).withWaitUnit(unit);
        entries.set(this.editorEntryIndex, entry);
        this.localSchedule = schedule.withEntries(entries);
        setEditedConditionWait(waitConditionWithTicks(type, ticks, cargoStableTicks, operator, measure, filter, target));
    }

    private boolean isCargoWaitType(WaitConditionType type) {
        return isCargoThresholdWaitType(type)
                || type == WaitConditionType.UNTIL_ITEM_EMPTY
                || type == WaitConditionType.UNTIL_ITEM_FULL
                || type == WaitConditionType.UNTIL_FLUID_EMPTY
                || type == WaitConditionType.UNTIL_FLUID_FULL;
    }

    private boolean isCargoThresholdWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD || type == WaitConditionType.UNTIL_FLUID_THRESHOLD;
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private WaitCondition waitConditionWithTicks(WaitConditionType type, int ticks) {
        AirshipScheduleCondition condition = currentCondition().orElse(null);
        WaitCondition current = condition == null ? WaitCondition.none() : condition.waitCondition();
        return waitConditionWithTicks(type, ticks, current.cargoStabilityTicks(), current.cargoOperator(), current.cargoMeasure(), current.cargoFilter(), current.cargoTarget());
    }

    private WaitCondition waitConditionWithTicks(WaitConditionType type, int ticks, int cargoStableTicks, int operator, int measure, ItemStack filter, CargoWaitTarget target) {
        WaitCondition current = currentCondition()
                .map(AirshipScheduleCondition::waitCondition)
                .orElse(defaultWaitConditionForType(type));
        return switch (type) {
            case UNTIL_DOCKED -> WaitCondition.untilDocked(ticks);
            case UNTIL_IDLE -> WaitCondition.untilIdle(ticks, 0);
            case REDSTONE_LINK, REDSTONE -> WaitCondition.redstoneLink(current.redstoneFrequencyFirst(), current.redstoneFrequencySecond(), current.redstonePowered());
            case TIME_OF_DAY -> WaitCondition.timeOfDay(current.timeOfDayHour(), current.timeOfDayMinute(), current.timeOfDayRotation());
            case UNTIL_ITEM_THRESHOLD -> WaitCondition.itemThreshold(ticks, 0, cargoStableTicks, operator, measure, filter, target);
            case UNTIL_FLUID_THRESHOLD -> WaitCondition.fluidThreshold(ticks, 0, cargoStableTicks, operator, measure, filter, target);
            case UNTIL_ITEM_EMPTY -> WaitCondition.itemEmpty(cargoStableTicks, ticks, target);
            case UNTIL_ITEM_FULL -> WaitCondition.itemFull(cargoStableTicks, ticks, target);
            case UNTIL_FLUID_EMPTY -> WaitCondition.fluidEmpty(cargoStableTicks, ticks, target);
            case UNTIL_FLUID_FULL -> WaitCondition.fluidFull(cargoStableTicks, ticks, target);
            default -> ticks == 0 ? WaitCondition.none() : WaitCondition.timed(ticks);
        };
    }

    private List<Component> cargoTargetOptions() {
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_target.ship"),
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.cargo_target.station")
        );
    }

    private void updateConditionGroups(int entryIndex, java.util.function.Consumer<List<List<AirshipScheduleCondition>>> updater) {
        AirshipSchedule schedule = currentSchedule();
        if (entryIndex < 0 || entryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        AirshipScheduleEntry entry = entries.get(entryIndex);
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups(entry.conditionGroups());
        updater.accept(groups);
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>(List.of(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)))));
        }
        entries.set(entryIndex, entryWithConditionGroups(entry, groups));
        this.localSchedule = schedule.withEntries(entries);
    }

    private List<List<AirshipScheduleCondition>> mutableConditionGroups(List<List<AirshipScheduleCondition>> source) {
        List<List<AirshipScheduleCondition>> groups = new ArrayList<>();
        for (List<AirshipScheduleCondition> group : source) {
            groups.add(new ArrayList<>(group));
        }
        return groups;
    }

    private AirshipScheduleEntry entryWithConditionGroups(AirshipScheduleEntry entry, List<List<AirshipScheduleCondition>> groups) {
        WaitCondition primaryWait = groups.stream()
                .filter(group -> !group.isEmpty())
                .findFirst()
                .map(group -> group.getFirst().waitCondition())
                .orElse(entry.waitCondition());
        return new AirshipScheduleEntry(
                entry.type(),
                entry.targetStationId(),
                displayStationName(entry),
                primaryWait,
                entry.waitUnit(),
                entry.pinnedSegmentId(),
                groups
        );
    }

    private void selectLocally(int direction) {
        if (currentSchedule().entries().isEmpty()) {
            this.selectedIndex = 0;
            return;
        }
        this.selectedIndex = Math.floorMod(this.selectedIndex + direction, currentSchedule().entries().size());
    }

    private void cycleTargetStationLocally(AirshipSchedule schedule) {
        if (schedule.entries().isEmpty() || this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        List<AirshipStationSnapshot> stations = availableStations();
        if (stations.isEmpty()) {
            return;
        }
        AirshipScheduleEntry entry = schedule.entries().get(this.selectedIndex);
        Optional<UUID> current = entry.targetStationId();
        int currentIndex = -1;
        if (current.isPresent()) {
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).stationId().equals(current.get())) {
                    currentIndex = i;
                    break;
                }
            }
        }
        AirshipStationSnapshot selectedStation = stations.get((currentIndex + 1) % stations.size());
        setEntryStationLocally(this.selectedIndex, selectedStation);
    }

    private void selectFilteredStationLocally(int entryIndex, String filter) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<AirshipStationSnapshot> stations = availableStations().stream()
                .filter(station -> normalizedFilter.isBlank() || station.stationName().toLowerCase(Locale.ROOT).contains(normalizedFilter))
                .toList();
        if (!stations.isEmpty()) {
            setEntryStationLocally(entryIndex, stations.getFirst());
        }
    }

    private List<AirshipStationSnapshot> filteredStations() {
        return filteredStations(this.stationFilterBox == null ? "" : this.stationFilterBox.getValue());
    }

    private List<AirshipStationSnapshot> filteredStations(String rawFilter) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
        return availableStations().stream()
                .filter(station -> filter.isBlank() || station.stationName().toLowerCase(Locale.ROOT).startsWith(filter))
                .toList();
    }

    private void selectStation(AirshipStationSnapshot station) {
        Optional<UUID> startStationId = previousStationId(this.editorEntryIndex);
        List<RouteSegment> candidates = routeCandidatesFor(this.editorEntryIndex, station.stationId());
        if (candidates.isEmpty()) {
            openNoRoutePopup(startStationId, station);
            return;
        }
        setEntryStationLocally(this.editorEntryIndex, station);
        openRouteChoiceEditor(candidates);
        syncSchedule();
    }

    private void openNoRoutePopup(Optional<UUID> startStationId, AirshipStationSnapshot targetStation) {
        dismissSuggestionPopups();
        this.noRoutePopupOpen = true;
        this.noRoutePopupLines.clear();
        String target = targetStation.stationName();
        if (startStationId.isPresent()) {
            String start = resolveStationName(startStationId.get(), previousStationNameFallback(this.editorEntryIndex));
            this.noRoutePopupLines.add(Component.literal("No recorded route from"));
            this.noRoutePopupLines.add(Component.literal(start + " -> " + target + " exists."));
            this.noRoutePopupLines.add(Component.empty());
            this.noRoutePopupLines.add(Component.literal("Use the Ship Transponder"));
            this.noRoutePopupLines.add(Component.literal("in Recording Mode to record"));
            this.noRoutePopupLines.add(Component.literal("that route first."));
        } else {
            this.noRoutePopupLines.add(Component.literal("No recorded route ends at"));
            this.noRoutePopupLines.add(Component.literal(target + "."));
            this.noRoutePopupLines.add(Component.empty());
            this.noRoutePopupLines.add(Component.literal("Use the Ship Transponder"));
            this.noRoutePopupLines.add(Component.literal("in Recording Mode to record"));
            this.noRoutePopupLines.add(Component.literal("an inbound route first."));
        }
    }

    private void closeNoRoutePopup() {
        this.noRoutePopupOpen = false;
        this.noRoutePopupLines.clear();
    }

    private void openDeleteStopConfirm(int entryIndex) {
        if (!isTransponderManagedPlan() || entryIndex < 0 || entryIndex >= currentSchedule().entries().size()) {
            return;
        }
        this.pendingDeleteStopIndex = entryIndex;
    }

    private void closeDeleteStopConfirm() {
        this.pendingDeleteStopIndex = null;
    }

    private boolean handleDeleteStopConfirmClick(double mouseX, double mouseY) {
        int boxW = 176;
        int boxH = 136;
        int x = this.leftPos + (this.imageWidth - boxW) / 2;
        int y = this.topPos + 28;
        if (inside((int) mouseX, (int) mouseY, x + 20, y + boxH - 19, 52, 14)) {
            confirmDeleteStop();
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, x + boxW - 72, y + boxH - 19, 52, 14)) {
            closeDeleteStopConfirm();
            return true;
        }
        return true;
    }

    private void confirmDeleteStop() {
        if (pendingDeleteStopIndex == null) {
            return;
        }
        int entryIndex = pendingDeleteStopIndex;
        closeDeleteStopConfirm();
        if (entryIndex < 0 || entryIndex >= currentSchedule().entries().size()) {
            return;
        }
        this.selectedIndex = entryIndex;
        pressAction(AirshipScheduleMenu.ACTION_REMOVE);
        if (this.editorMode == EditorMode.STATION && this.editorEntryIndex == entryIndex) {
            closeEditor();
        }
    }

    private int deletedRouteCountForStop(AirshipSchedule schedule, int removedIndex) {
        if (removedIndex < 0 || removedIndex >= schedule.entries().size()) {
            return 0;
        }
        if (schedule.entries().size() == 1) {
            return 1;
        }
        int count = 0;
        if (removedIndex == schedule.entries().size() - 1) {
            count++;
        }
        if (removedIndex < schedule.entries().size() - 1) {
            count++;
        }
        return count;
    }

    private StationSuggestions createStationSuggestions(EditBox targetBox) {
        if (this.minecraft == null || targetBox == null) {
            return null;
        }
        StationSuggestions suggestions = new StationSuggestions(targetBox, viableStations());
        suggestions.updateCommandInfo();
        return suggestions;
    }

    private ShipSuggestions createAssignedShipSuggestions(EditBox targetBox) {
        if (this.minecraft == null || targetBox == null) {
            return null;
        }
        ShipSuggestions suggestions = new ShipSuggestions(targetBox, viableShips());
        suggestions.updateCommandInfo();
        return suggestions;
    }

    private void populateAssignedShipBox() {
        if (this.assignedShipBox == null || this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return;
        }
        AirshipSchedule schedule = currentSchedule();
        if (schedule.assignedTransponderId().isPresent()) {
            String name = ShipTransponderRegistry.snapshot(schedule.assignedTransponderId().get())
                    .map(ShipTransponderSnapshot::shipName)
                    .filter(found -> !found.isBlank())
                    .orElse(schedule.assignedShipName());
            this.assignedShipBox.setValue(name);
            this.assignedShipBox.setCursorPosition(0);
            this.assignedShipBox.setHighlightPos(0);
            this.assignedShipBox.setSuggestion("");
            return;
        }
        this.assignedShipBox.setValue("");
        this.assignedShipBox.setCursorPosition(0);
        this.assignedShipBox.setHighlightPos(0);
        ShipTransponderRegistry.knownShips(this.minecraft.level.dimension()).stream()
                .min((left, right) -> Double.compare(distanceToPlayer(left), distanceToPlayer(right)))
                .ifPresentOrElse(
                        snapshot -> this.assignedShipBox.setSuggestion(snapshot.shipName()),
                        () -> this.assignedShipBox.setSuggestion("")
                );
    }

    private void syncAssignedShipSelection(String value) {
        if (this.minecraft == null || this.minecraft.level == null || this.localSchedule == null || this.assignedShipBox == null) {
            return;
        }
        String trimmed = value.trim();
        AirshipSchedule schedule = currentSchedule();
        if (trimmed.isBlank()) {
            if (schedule.assignedTransponderId().isPresent() || !schedule.assignedShipName().isBlank()) {
                this.localSchedule = schedule.withAssignedShip(Optional.empty(), "");
                syncSchedule();
            }
            return;
        }
        Optional<ShipTransponderSnapshot> selected = ShipTransponderRegistry.knownShips(this.minecraft.level.dimension()).stream()
                .filter(snapshot -> snapshot.shipName().equals(trimmed))
                .min((left, right) -> Double.compare(distanceToPlayer(left), distanceToPlayer(right)));
        if (selected.isEmpty()) {
            return;
        }
        ShipTransponderSnapshot snapshot = selected.get();
        Optional<UUID> selectedId = Optional.of(snapshot.transponderId());
        if (!schedule.assignedTransponderId().equals(selectedId) || !schedule.assignedShipName().equals(snapshot.shipName())) {
            this.localSchedule = schedule.withAssignedShip(selectedId, snapshot.shipName());
            syncSchedule();
        }
    }

    private List<IntAttached<String>> viableStations() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return List.of();
        }
        Vec3 playerPosition = this.minecraft.player.position();
        Set<String> seen = new HashSet<>();
        return availableStations().stream()
                .filter(station -> seen.add(station.stationName()))
                .map(station -> IntAttached.with(
                        (int) Vec3.atCenterOf(station.stationPos()).distanceTo(playerPosition),
                        station.stationName()))
                .toList();
    }

    private List<IntAttached<String>> viableShips() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        return availableShips().stream()
                .sorted((left, right) -> Double.compare(distanceToPlayer(left), distanceToPlayer(right)))
                .filter(ship -> seen.add(ship.shipName()))
                .map(ship -> IntAttached.with((int) distanceToPlayer(ship), ship.shipName()))
                .toList();
    }

    private List<AirshipStationSnapshot> availableStations() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        return AirshipStationRegistry.knownStations(this.minecraft.level.dimension()).stream()
                .filter(station -> this.minecraft.level.getBlockEntity(station.stationPos()) instanceof AirshipStationBlockEntity blockEntity
                        && blockEntity.stationId().equals(station.stationId()))
                .toList();
    }

    private List<ShipTransponderSnapshot> availableShips() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        return ShipTransponderRegistry.knownShips(this.minecraft.level.dimension()).stream()
                .filter(ship -> this.minecraft.level.getBlockEntity(ship.transponderPos()) instanceof ShipTransponderBlockEntity blockEntity
                        && blockEntity.transponderId().equals(ship.transponderId()))
                .toList();
    }

    private double distanceToPlayer(ShipTransponderSnapshot ship) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return Double.MAX_VALUE;
        }
        Vec3 shipPosition = ship.lastKnownPosition().orElse(Vec3.atCenterOf(ship.transponderPos()));
        return shipPosition.distanceTo(this.minecraft.player.position());
    }

    private void setEntryStationLocally(int entryIndex, AirshipStationSnapshot selectedStation) {
        AirshipSchedule schedule = currentSchedule();
        if (entryIndex < 0 || entryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(entryIndex, entries.get(entryIndex).withTargetStation(selectedStation.stationId(), selectedStation.stationName()));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void setEntryPinnedSegmentLocally(int entryIndex, Optional<RouteSegmentId> segmentId) {
        AirshipSchedule schedule = currentSchedule();
        if (entryIndex < 0 || entryIndex >= schedule.entries().size()) {
            return;
        }
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(entryIndex, entries.get(entryIndex).withPinnedSegment(segmentId));
        this.localSchedule = schedule.withEntries(entries);
    }

    private void openRouteChoiceEditor(List<RouteSegment> candidates) {
        dismissSuggestionPopups();
        this.editorMode = EditorMode.ROUTE;
        this.editorSubWidgets.reset();
        this.stationFilterBox.visible = false;
        this.stationFilterBox.active = false;
        this.stationFilterBox.setFocused(false);
        this.stationFilterBox.setSuggestion("");
        this.editorStationBox = null;
        this.editorDurationInput = null;
        this.editorUnitInput = null;
        this.stationSuggestions = null;
        this.routeChoices.clear();
        this.routeChoices.addAll(candidates);
        this.routeChoiceSelected = 0;
        this.routeChoiceScroll = 0;
        setFocused(null);
    }

    private void pinSelectedRouteChoice() {
        if (this.routeChoices.isEmpty()) {
            closeEditor();
            return;
        }
        int selected = Mth.clamp(this.routeChoiceSelected, 0, this.routeChoices.size() - 1);
        setEntryPinnedSegmentLocally(this.editorEntryIndex, Optional.of(this.routeChoices.get(selected).id()));
        syncSchedule();
        closeEditor();
    }

    private List<RouteSegment> routeCandidatesFor(int entryIndex, UUID endStationId) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        Optional<UUID> selectedTransponderId = selectedShipTransponderId();
        if (selectedTransponderId.isEmpty()) {
            return List.of();
        }
        Optional<UUID> startStationId = previousStationId(entryIndex);
        if (startStationId.isPresent()) {
            return RouteSegmentRegistry.matching(
                    startStationId.get(),
                    endStationId,
                    this.minecraft.level.dimension(),
                    selectedTransponderId
            );
        }
        return RouteSegmentRegistry.endingAt(
                endStationId,
                this.minecraft.level.dimension(),
                selectedTransponderId
        );
    }

    private Optional<UUID> previousStationId(int entryIndex) {
        if (entryIndex <= 0) {
            return Optional.empty();
        }
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = entryIndex - 1; i >= 0; i--) {
            Optional<UUID> stationId = entries.get(i).targetStationId();
            if (stationId.isPresent()) {
                return stationId;
            }
        }
        return Optional.empty();
    }

    private String previousStationNameFallback(int entryIndex) {
        if (entryIndex <= 0) {
            return "Unknown";
        }
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = entryIndex - 1; i >= 0; i--) {
            AirshipScheduleEntry entry = entries.get(i);
            if (entry.targetStationId().isPresent()) {
                return displayStationName(entry);
            }
        }
        return "Unknown";
    }

    private String routeDisplayName(RouteSegment segment) {
        return resolveStationName(segment.startStationId(), segment.startStationName())
                + " -> "
                + resolveStationName(segment.endStationId(), segment.endStationName());
    }

    private String routeFromText(RouteSegment segment, int width) {
        return this.font.plainSubstrByWidth("From: " + resolveStationName(segment.startStationId(), segment.startStationName()), width);
    }

    private String routeToText(RouteSegment segment, int width) {
        return this.font.plainSubstrByWidth("To: " + resolveStationName(segment.endStationId(), segment.endStationName()), width);
    }

    private String routeMetaText(RouteSegment segment, int width) {
        return this.font.plainSubstrByWidth(routeMetaTextRaw(segment), width);
    }

    private String routeMetaTextRaw(RouteSegment segment) {
        return ROUTE_TIME_FORMAT.format(Instant.ofEpochMilli(segment.createdEpochMillis()))
                + " | "
                + segment.points().size()
                + " pts";
    }

    private String resolveStationName(UUID stationId, String fallbackName) {
        return AirshipStationRegistry.snapshot(stationId)
                .map(AirshipStationSnapshot::stationName)
                .filter(name -> !name.isBlank())
                .orElse(fallbackName);
    }

    private String displayStationName(AirshipScheduleEntry entry) {
        return entry.targetStationId()
                .map(stationId -> resolveStationName(stationId, entry.displayStationName()))
                .orElseGet(entry::displayStationName);
    }

    private String resolveShipName(RouteSegment segment) {
        return ShipTransponderRegistry.snapshot(segment.transponderId())
                .map(ShipTransponderSnapshot::shipName)
                .filter(name -> !name.isBlank())
                .orElse(segment.shipName());
    }

    private boolean hasSelectedShip() {
        return selectedShipTransponderId().isPresent();
    }

    private Optional<UUID> selectedShipTransponderId() {
        if (this.minecraft == null || this.minecraft.level == null || this.assignedShipBox == null) {
            return Optional.empty();
        }
        String selectedName = this.assignedShipBox.getValue().trim();
        if (selectedName.isBlank()) {
            return Optional.empty();
        }
        return ShipTransponderRegistry.knownShips(this.minecraft.level.dimension()).stream()
                .filter(snapshot -> snapshot.shipName().equals(selectedName))
                .min((left, right) -> Double.compare(distanceToPlayer(left), distanceToPlayer(right)))
                .map(ShipTransponderSnapshot::transponderId);
    }

    private int routeChoiceAt(int mx, int my) {
        int listLeft = ROUTE_SELECTOR_X + ROUTE_SELECTOR_LIST_X;
        int listTop = ROUTE_SELECTOR_Y + ROUTE_SELECTOR_LIST_Y;
        int x = mx - listLeft;
        int y = my - listTop;
        if (x < 0 || x >= ROUTE_SELECTOR_LIST_WIDTH || y < 0 || y >= ROUTE_SELECTOR_ROW_HEIGHT * ROUTE_SELECTOR_VISIBLE_ROWS) {
            return -1;
        }
        return this.routeChoiceScroll + y / ROUTE_SELECTOR_ROW_HEIGHT;
    }

    private void clampSelectedIndex() {
        if (currentSchedule().entries().isEmpty()) {
            this.selectedIndex = 0;
            return;
        }
        this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, currentSchedule().entries().size() - 1));
    }

    private AirshipSchedule currentSchedule() {
        return this.localSchedule == null ? AirshipSchedule.empty() : this.localSchedule;
    }

    private java.util.Optional<AirshipScheduleEntry> currentEntry() {
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        if (this.editorEntryIndex < 0 || this.editorEntryIndex >= entries.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(entries.get(this.editorEntryIndex));
    }

    private java.util.Optional<AirshipScheduleCondition> currentCondition() {
        return currentEntry().flatMap(entry -> {
            if (this.editorConditionGroup < 0 || this.editorConditionGroup >= entry.conditionGroups().size()) {
                return java.util.Optional.empty();
            }
            List<AirshipScheduleCondition> group = entry.conditionGroups().get(this.editorConditionGroup);
            if (this.editorConditionIndex < 0 || this.editorConditionIndex >= group.size()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(group.get(this.editorConditionIndex));
        });
    }

    private int selectedIndex() {
        clampSelectedIndex();
        return this.selectedIndex;
    }

    private int cardHeight(AirshipScheduleEntry entry) {
        int maxRows = 1;
        for (List<AirshipScheduleCondition> group : entry.conditionGroups()) {
            maxRows = Math.max(maxRows, group.size());
        }
        return CARD_HEADER + 24 + maxRows * 18;
    }

    private int scheduleContentHeight() {
        int height = 25;
        List<AirshipScheduleEntry> entries = currentSchedule().entries();
        for (int i = 0; i < entries.size(); i++) {
            height += cardHeight(entries.get(i));
            if (i + 1 < entries.size()) {
                height += 10;
            }
        }
        if (!entries.isEmpty()) {
            height += 9;
        }
        height += 20;
        return height;
    }

    private int maxScroll() {
        return Math.max(0, scheduleContentHeight() - 173);
    }

    private Component waitText(AirshipScheduleEntry entry) {
        if (entry.waitCondition().type() == WaitConditionType.NONE) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.wait.none");
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.wait.short",
                waitAmount(entry),
                unitShortText(entry.waitUnit())
        );
    }

    private Component conditionWaitText(AirshipScheduleCondition condition) {
        WaitDurationUnit unit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return conditionWaitText(condition, unit);
    }

    private Component conditionWaitText(AirshipScheduleCondition condition, WaitDurationUnit unit) {
        if (condition.waitCondition().type() == WaitConditionType.NONE) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.wait.none");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.docked_time.short",
                    formatCreateTime(condition.waitCondition(), unit, true)
            );
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.wait.until_idle",
                    waitAmount(condition.waitCondition(), unit),
                    unitShortText(unit)
            );
        }
        if (isRedstoneLinkWaitType(condition.waitCondition().type())) {
            return redstoneLinkSummaryText(condition.waitCondition());
        }
        if (isTimeOfDayWaitType(condition.waitCondition().type())) {
            return timeOfDaySummaryText(condition.waitCondition());
        }
        if (isCargoWaitType(condition.waitCondition().type())) {
            return cargoSummaryText(condition);
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.wait.short",
                waitAmount(condition.waitCondition(), unit),
                unitShortText(unit)
        );
    }

    private Component longConditionTimeText(AirshipScheduleCondition condition, WaitDurationUnit unit) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.docked_time.long",
                    formatCreateTime(condition.waitCondition(), unit, false)
            );
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_schedule.dock_inactivity.long",
                    formatCreateTime(condition.waitCondition(), unit, false)
            );
        }
        if (isRedstoneLinkWaitType(condition.waitCondition().type())) {
            return redstoneLinkLongText(condition.waitCondition());
        }
        if (isTimeOfDayWaitType(condition.waitCondition().type())) {
            return timeOfDayLongText(condition.waitCondition());
        }
        if (isCargoWaitType(condition.waitCondition().type())) {
            return cargoLongText(condition);
        }
        return Component.literal(waitAmount(condition.waitCondition(), unit) + " ").append(unitText(unit));
    }

    private Component conditionValueText(AirshipScheduleCondition condition) {
        if (isTimeOfDayWaitType(condition.waitCondition().type())) {
            return timeOfDayDisplay(condition.waitCondition(), false);
        }
        if (isRedstoneLinkWaitType(condition.waitCondition().type())) {
            return condition.waitCondition().redstonePowered()
                    ? Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.powered")
                    : Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.unpowered");
        }
        WaitDurationUnit unit = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return Component.literal(Integer.toString(waitAmount(condition.waitCondition(), unit)));
    }

    private Component conditionActionText(AirshipScheduleCondition condition) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.until_docked");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.until_idle");
        }
        if (isRedstoneLinkWaitType(condition.waitCondition().type())) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link");
        }
        if (isTimeOfDayWaitType(condition.waitCondition().type())) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.until_item_threshold");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.until_fluid_threshold");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_EMPTY) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.item_empty");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_FULL) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.item_full");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_EMPTY) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.fluid_empty");
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_FULL) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.fluid_full");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.scheduled_delay");
    }

    private net.minecraft.world.item.ItemStack conditionIcon(AirshipScheduleCondition condition) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_DOCKED) {
            return ModItems.SHIP_TRANSPONDER.get().getDefaultInstance();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_IDLE) {
            return Items.HOPPER.getDefaultInstance();
        }
        if (isRedstoneLinkWaitType(condition.waitCondition().type())) {
            return redstoneLinkIcon();
        }
        if (isTimeOfDayWaitType(condition.waitCondition().type())) {
            return ItemStack.EMPTY;
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_THRESHOLD) {
            return condition.waitCondition().cargoFilter().isEmpty()
                    ? ItemStack.EMPTY
                    : condition.waitCondition().cargoFilter();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return condition.waitCondition().cargoFilter().isEmpty()
                    ? ItemStack.EMPTY
                    : condition.waitCondition().cargoFilter();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_EMPTY
                || condition.waitCondition().type() == WaitConditionType.UNTIL_ITEM_FULL) {
            return Items.CHEST.getDefaultInstance();
        }
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_EMPTY
                || condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_FULL) {
            return Items.WATER_BUCKET.getDefaultInstance();
        }
        return Items.REPEATER.getDefaultInstance();
    }

    private Component cargoSummaryText(AirshipScheduleCondition condition) {
        WaitCondition wait = condition.waitCondition();
        if (!isCargoThresholdWaitType(wait.type())) {
            return switch (wait.type()) {
                case UNTIL_ITEM_EMPTY -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_state_summary",
                        cargoTargetText(wait),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.item_empty")
                );
                case UNTIL_ITEM_FULL -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_state_summary",
                        cargoTargetText(wait),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.item_full")
                );
                case UNTIL_FLUID_EMPTY -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_state_summary",
                        cargoTargetText(wait),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.fluid_empty")
                );
                case UNTIL_FLUID_FULL -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_state_summary",
                        cargoTargetText(wait),
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.fluid_full")
                );
                default -> cargoTargetText(wait);
            };
        }
        Ops[] ops = Ops.values();
        Ops operator = ops[Mth.clamp(wait.cargoOperator(), 0, ops.length - 1)];
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_threshold_summary",
                Component.literal(operator.formatted),
                Component.literal(Integer.toString(wait.durationTicks())),
                cargoMeasureText(wait)
        );
    }

    private Component redstoneLinkSummaryText(WaitCondition wait) {
        return wait.redstonePowered()
                ? Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.powered")
                : Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.unpowered");
    }

    private Component redstoneLinkLongText(WaitCondition wait) {
        return Component.translatable(
                wait.redstonePowered()
                        ? "gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.long.powered"
                        : "gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.long.unpowered",
                redstoneFrequencyName(wait.redstoneFrequencyFirst()),
                redstoneFrequencyName(wait.redstoneFrequencySecond())
        );
    }

    private Component timeOfDaySummaryText(WaitCondition wait) {
        return timeOfDayDisplay(wait, false)
                .copy()
                .append(Component.literal(" / "))
                .append(timeOfDayRepeatShortText(wait));
    }

    private Component timeOfDayLongText(WaitCondition wait) {
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.long",
                timeOfDayDisplay(wait, false),
                timeOfDayRotationOptions().get(Mth.clamp(wait.timeOfDayRotation(), 0, timeOfDayRotationOptions().size() - 1))
        );
    }

    private Component timeOfDayRepeatShortText(WaitCondition wait) {
        return switch (wait.timeOfDayRotation()) {
            case 9 -> Component.literal("15m");
            case 8 -> Component.literal("30m");
            case 7 -> Component.literal("45m");
            case 6 -> Component.literal("1h");
            case 5 -> Component.literal("2h");
            case 4 -> Component.literal("3h");
            case 3 -> Component.literal("4h");
            case 2 -> Component.literal("6h");
            case 1 -> Component.literal("12h");
            default -> Component.literal("24h");
        };
    }

    private Component cargoLongText(AirshipScheduleCondition condition) {
        WaitCondition wait = condition.waitCondition();
        if (!isCargoThresholdWaitType(wait.type())) {
            Component text = Component.translatable(
                    switch (wait.type()) {
                        case UNTIL_ITEM_EMPTY -> "gui.create_aeronautics_automated_logistics.airship_schedule.item_empty.long";
                        case UNTIL_ITEM_FULL -> "gui.create_aeronautics_automated_logistics.airship_schedule.item_full.long";
                        case UNTIL_FLUID_EMPTY -> "gui.create_aeronautics_automated_logistics.airship_schedule.fluid_empty.long";
                        case UNTIL_FLUID_FULL -> "gui.create_aeronautics_automated_logistics.airship_schedule.fluid_full.long";
                        default -> "gui.create_aeronautics_automated_logistics.airship_schedule.scheduled_delay";
                    },
                    cargoTargetText(wait)
            );
            if (wait.cargoStabilityTicks() > 0) {
                text = text.copy()
                        .append(Component.literal(" "))
                        .append(cargoStableLongText(wait).copy().withStyle(ChatFormatting.DARK_AQUA));
            }
            return text;
        }
        Ops[] ops = Ops.values();
        Ops operator = ops[Mth.clamp(wait.cargoOperator(), 0, ops.length - 1)];
        Component text = Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_threshold_long",
                Component.literal(operator.formatted),
                Component.literal(Integer.toString(wait.durationTicks())),
                cargoMeasureText(wait),
                cargoFilterText(wait),
                cargoTargetText(wait)
        );
        if (wait.cargoStabilityTicks() > 0) {
            text = text.copy()
                    .append(Component.literal(" "))
                    .append(cargoStableLongText(wait).copy().withStyle(ChatFormatting.DARK_AQUA));
        }
        return text;
    }

    private Component cargoUnitText(WaitCondition wait) {
        if (wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.literal("b");
        }
        return Component.literal(wait.cargoMeasure() == 1 ? "\u25A4" : "");
    }

    private Component cargoMeasureText(WaitCondition wait) {
        if (wait.type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.translatable("create.schedule.condition.threshold.buckets");
        }
        return Component.translatable("create.schedule.condition.threshold." + (wait.cargoMeasure() == 1 ? "stacks" : "items"));
    }

    private Component cargoFilterText(WaitCondition wait) {
        if (wait.cargoFilter().isEmpty()) {
            return Component.translatable("create.schedule.condition.threshold.anything");
        }
        return wait.cargoFilter().getHoverName();
    }

    private Component cargoTargetText(WaitCondition wait) {
        return Component.translatable(
                wait.cargoTarget() == CargoWaitTarget.SHIP_CARGO
                        ? "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_target.ship"
                        : "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_target.station"
        );
    }

    private Component redstoneFrequencyName(ItemStack stack) {
        return stack.isEmpty()
                ? Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.redstone_link.empty")
                : stack.getHoverName();
    }

    private ItemStack redstoneLinkIcon() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("create", "redstone_link");
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(Items.REDSTONE.getDefaultInstance());
    }

    private Component timeOfDayDisplay(WaitCondition wait, boolean doubleDigitHours) {
        return timeOfDayDisplay(wait.timeOfDayHour(), wait.timeOfDayMinute(), doubleDigitHours);
    }

    private Component timeOfDayDisplay(int hour, int minute, boolean doubleDigitHours) {
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        String displayHour12 = doubleDigitHours ? twoDigits(hour12) : Integer.toString(hour12);
        String displayHour24 = doubleDigitHours ? twoDigits(hour) : Integer.toString(hour);
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.time_of_day.digital_format",
                displayHour12,
                displayHour24,
                twoDigits(minute),
                Component.translatable(hour > 11 ? "create.generic.daytime.pm" : "create.generic.daytime.am")
        );
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private Component optionLine(WaitDurationUnit unit) {
        WaitDurationUnit current = currentEntry().map(AirshipScheduleEntry::waitUnit).orElse(WaitDurationUnit.SECONDS);
        return Component.literal(current == unit ? "-> " : "> ")
                .append(unitText(unit))
                .withStyle(current == unit ? ChatFormatting.WHITE : ChatFormatting.GRAY);
    }

    private int waitAmount(AirshipScheduleEntry entry) {
        return waitAmount(entry.waitCondition(), entry.waitUnit());
    }

    private int waitAmount(WaitCondition waitCondition, WaitDurationUnit waitUnit) {
        int ticks = switch (waitCondition.type()) {
            case UNTIL_DOCKED -> waitCondition.runtimeTicks();
            case UNTIL_IDLE -> waitCondition.idleTicks();
            case REDSTONE_LINK, REDSTONE, TIME_OF_DAY -> 0;
            case UNTIL_ITEM_THRESHOLD, UNTIL_FLUID_THRESHOLD -> waitCondition.durationTicks();
            case UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> 0;
            default -> waitCondition.durationTicks();
        };
        if (isCargoThresholdWaitType(waitCondition.type())) {
            return ticks;
        }
        return switch (waitUnit) {
            case TICKS -> ticks;
            case SECONDS -> ticks / 20;
            case MINUTES -> ticks / (20 * 60);
        };
    }

    private Component unitText(WaitDurationUnit unit) {
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit." + unit.name().toLowerCase(Locale.ROOT));
    }

    private Component thresholdUnitText(AirshipScheduleCondition condition) {
        if (condition.waitCondition().type() == WaitConditionType.UNTIL_FLUID_THRESHOLD) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit.millibuckets");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit.items");
    }

    private Component unitShortText(WaitDurationUnit unit) {
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.unit_short." + unit.name().toLowerCase(Locale.ROOT));
    }

    private Component formatCreateTime(WaitCondition waitCondition, WaitDurationUnit unit, boolean compact) {
        int amount = waitAmount(waitCondition, unit);
        if (compact) {
            return Component.literal(amount + switch (unit) {
                case TICKS -> "t";
                case SECONDS -> "s";
                case MINUTES -> "min";
            });
        }
        return Component.literal(amount + " ").append(Component.translatable(switch (unit) {
            case TICKS -> "create.generic.unit.ticks";
            case SECONDS -> "create.generic.unit.seconds";
            case MINUTES -> "create.generic.unit.minutes";
        }));
    }

    private Component statusText(AirshipScheduleEntry entry) {
        if (!hasSelectedShip()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.select_ship");
        }
        if (entry.targetStationId().isEmpty()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.status.missing_station");
        }
        if (entry.pinnedSegmentId().isPresent()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.status.pinned");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_schedule.status.unpinned");
    }

    private Component conditionText(AirshipScheduleEntry entry) {
        int conditionCount = entry.conditionGroups().stream().mapToInt(List::size).sum();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.conditions",
                entry.conditionGroups().size(),
                conditionCount
        );
    }

    private static boolean inside(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    private enum EditorMode {
        NONE,
        STATION,
        CONDITION,
        ROUTE
    }

    private enum HitType {
        NONE,
        ADD_ENTRY,
        STATION,
        REMOVE,
        DUPLICATE,
        MOVE_UP,
        MOVE_DOWN,
        CONDITION_SCROLL_LEFT,
        CONDITION_SCROLL_RIGHT,
        CONDITION,
        ADD_CONDITION,
        ADD_ALTERNATIVE
    }

    private record Hit(HitType type, int index, int conditionGroup, int conditionIndex) {
        private static final Hit NONE = new Hit(HitType.NONE, -1);

        private Hit(HitType type, int index) {
            this(type, index, -1, -1);
        }
    }

    private static final class EditorSubWidgets extends ScreenOverlay {
        private final ModularGuiLine line;

        private EditorSubWidgets() {
            super(200);
            this.line = new ModularGuiLine();
        }

        private void save(CompoundTag data) {
            this.line.saveValues(data);
        }

        private void load(CompoundTag data) {
            this.line.loadValues(data, this::add, this::addRenderableOnly);
        }

        private void reset() {
            this.line.forEach(this::remove);
            this.line.clear();
        }

        private ModularGuiLineBuilder newLineBuilder(net.minecraft.client.gui.Font font, int x, int y) {
            return new ModularGuiLineBuilder(font, this.line, x, y);
        }

        private void renderBg(int guiLeft, GuiGraphics graphics) {
            this.line.renderWidgetBG(guiLeft, graphics);
        }
    }

    private int cargoStableSeconds(WaitCondition waitCondition) {
        return waitCondition.cargoStabilityTicks() / 20;
    }

    private Component cargoStableSecondsLabel(int seconds) {
        return Component.literal(seconds + "s");
    }

    private Component cargoStableShortText(WaitCondition waitCondition) {
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_stable_for.short",
                cargoStableSeconds(waitCondition)
        );
    }

    private Component cargoStableLongText(WaitCondition waitCondition) {
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_schedule.cargo_stable_for.long",
                cargoStableSeconds(waitCondition)
        );
    }

    private static final class ClippedEditBox extends EditBox {
        private ClippedEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.enableScissor(getX() - 7, getY(), getX() + width - 5, getY() + height);
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.disableScissor();
        }
    }

    private final class StationSuggestions {
        private final PopupSuggestions delegate;

        private StationSuggestions(EditBox textBox, List<IntAttached<String>> viableStations) {
            this.delegate = new PopupSuggestions(textBox, viableStations, -5, 8);
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
        private static final int MAX_ROWS = 5;
        private final EditBox textBox;
        private final List<IntAttached<String>> options;
        private final int horizontalOffset;
        private final int extraRightWidth;
        private List<String> visibleSuggestions = List.of();
        private int selectedIndex;
        private int scrollOffset;
        private boolean active;
        private String previous = "<>";

        private PopupSuggestions(EditBox textBox, List<IntAttached<String>> options, int horizontalOffset, int extraRightWidth) {
            this.textBox = textBox;
            this.options = options;
            this.horizontalOffset = horizontalOffset;
            this.extraRightWidth = extraRightWidth;
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
            int x = this.textBox.getX() + this.horizontalOffset;
            int y = this.textBox.getY() + this.textBox.getHeight() + 1;
            int width = Math.max(1, this.textBox.getWidth() + this.extraRightWidth);
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
                        AirshipScheduleScreen.this.font,
                        AirshipScheduleScreen.this.font.plainSubstrByWidth(this.visibleSuggestions.get(suggestionIndex), width - 6),
                        x + 4,
                        rowY,
                        suggestionIndex == this.selectedIndex ? 0xFFFFFF55 : TEXT_COLOR,
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
            AirshipScheduleScreen.this.setFocused(this.textBox);
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
                this.textBox.setSuggestion(clipSuggestionSuffix(this.textBox, value, selected.substring(value.length()), 18));
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
            int x = this.textBox.getX() + this.horizontalOffset;
            int y = this.textBox.getY() + this.textBox.getHeight() + 1;
            int width = Math.max(1, this.textBox.getWidth() + this.extraRightWidth);
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int height = rows * ROW_HEIGHT + 4;
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private int rowAt(int mouseX, int mouseY) {
            if (!contains(mouseX, mouseY)) {
                return -1;
            }
            int y = this.textBox.getY() + this.textBox.getHeight() + 1;
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

    private final class ShipSuggestions {
        private static final int ROW_HEIGHT = 12;
        private static final int MAX_ROWS = 3;
        private final EditBox textBox;
        private final List<IntAttached<String>> viableShips;
        private List<String> visibleSuggestions = List.of();
        private int selectedIndex;
        private boolean active;
        private String previous = "<>";

        private ShipSuggestions(EditBox textBox, List<IntAttached<String>> viableShips) {
            this.textBox = textBox;
            this.viableShips = viableShips;
        }

        private void tick() {
            if (!active) {
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

            this.visibleSuggestions = this.viableShips.stream()
                    .filter(ia -> !ia.getValue().equals(value)
                            && ia.getValue().toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT)))
                    .sorted((left, right) -> Integer.compare(left.getFirst(), right.getFirst()))
                    .map(IntAttached::getValue)
                    .toList();
            this.selectedIndex = Mth.clamp(this.selectedIndex, 0, Math.max(0, this.visibleSuggestions.size() - 1));
            syncGhostText();
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.visibleSuggestions.isEmpty()) {
                return false;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                this.selectedIndex = Mth.clamp(this.selectedIndex + 1, 0, this.visibleSuggestions.size() - 1);
                syncGhostText();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                this.selectedIndex = Mth.clamp(this.selectedIndex - 1, 0, this.visibleSuggestions.size() - 1);
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
            applySelected();
            return true;
        }

        private boolean mouseScrolled(double scrollY) {
            if (this.visibleSuggestions.isEmpty()) {
                return false;
            }
            this.selectedIndex = Mth.clamp(this.selectedIndex + (scrollY > 0 ? -1 : 1), 0, this.visibleSuggestions.size() - 1);
            syncGhostText();
            return true;
        }

        private void dismiss() {
            this.active = false;
            this.visibleSuggestions = List.of();
            this.textBox.setSuggestion("");
        }

        private void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            if (!this.active || this.visibleSuggestions.isEmpty()) {
                return;
            }
            int x = this.textBox.getX() - 4;
            int y = this.textBox.getY() + this.textBox.getHeight() + 1;
            int width = Math.max(1, this.textBox.getInnerWidth() + 3);
            int textClipWidth = Math.max(1, this.textBox.getInnerWidth() - 3);
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int height = rows * ROW_HEIGHT + 4;

            guiGraphics.fill(x, y, x + width, y + height, 0xee303030);
            guiGraphics.fill(x, y, x + width, y + 1, 0xff505050);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xff505050);
            guiGraphics.fill(x, y, x + 1, y + height, 0xff505050);
            guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xff505050);

            guiGraphics.enableScissor(x, y, x + width, y + height);
            for (int i = 0; i < rows; i++) {
                int rowY = y + 2 + i * ROW_HEIGHT;
                if (i == this.selectedIndex) {
                    guiGraphics.fill(x + 1, rowY - 1, x + width - 1, rowY + ROW_HEIGHT - 1, 0xff3a3a3a);
                }
                guiGraphics.drawString(
                        AirshipScheduleScreen.this.font,
                        AirshipScheduleScreen.this.font.plainSubstrByWidth(this.visibleSuggestions.get(i), textClipWidth - 8),
                        x + 4,
                        rowY,
                        i == this.selectedIndex ? 0xFFFFFF55 : TEXT_COLOR,
                        false
                );
            }
            guiGraphics.disableScissor();

            int hoveredRow = rowAt(mouseX, mouseY);
            if (hoveredRow >= 0 && hoveredRow < this.visibleSuggestions.size()) {
                guiGraphics.renderTooltip(
                        AirshipScheduleScreen.this.font,
                        Component.literal(this.visibleSuggestions.get(hoveredRow)),
                        mouseX,
                        mouseY
                );
            }
        }

        private void forceShow() {
            this.textBox.setFocused(true);
            AirshipScheduleScreen.this.setFocused(this.textBox);
            this.previous = "<force>";
            this.active = true;
            updateCommandInfo();
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
                this.textBox.setSuggestion(clipSuggestionSuffix(this.textBox, value, selected.substring(value.length()), 0));
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
            this.textBox.setSuggestion("");
        }

        private boolean contains(int mouseX, int mouseY) {
            if (!this.active || this.visibleSuggestions.isEmpty()) {
                return false;
            }
            int x = this.textBox.getX() - 4;
            int y = this.textBox.getY() + this.textBox.getHeight() + 1;
            int width = Math.max(1, this.textBox.getInnerWidth() + 3);
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int height = rows * ROW_HEIGHT + 4;
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private int rowAt(int mouseX, int mouseY) {
            if (!contains(mouseX, mouseY)) {
                return -1;
            }
            int y = this.textBox.getY() + this.textBox.getHeight() + 1;
            int rows = Math.min(MAX_ROWS, this.visibleSuggestions.size());
            int row = (mouseY - (y + 2)) / ROW_HEIGHT;
            return row >= 0 && row < rows ? row : -1;
        }
    }
}



package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleHealth;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveSignalCategory;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.DriveModuleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.network.DriveModuleMenuActionPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.UpdateIdentityNamePayload;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class DriveModuleScreen extends AbstractContainerScreen<DriveModuleMenu> {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 178;
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/drive_module.png");
    private static final ScreenElement EDIT_NAME_ICON =
            new AtlasIcon(BACKGROUND, 0, 239, 13, 13, 256);
    private static final int BOX_FILL = 0xCC4A3F3D;
    private static final int BOX_OUTLINE = 0xFF8C7964;
    private static final int BOX_SELECTED = 0xFF6B5B53;
    private static final int TEXT_COLOR = 0xFFE7D6B4;
    private static final int SUBTEXT_COLOR = 0xFFBEB3A2;
    private static final int DESCRIPTION_COLOR = 0xFFE6E6E6;
    private static final int INVALID_COLOR = 0xFFFF9B7D;
    private static final int LABEL_COLOR = 0xFFE7D6B4;
    private static final int STATUS_FOOTER_X = 60;
    private static final int STATUS_FOOTER_Y = 159;
    private static final int STATUS_FOOTER_WIDTH = 118;
    private static final int DROPDOWN_VISIBLE_ROWS = 6;
    private static final int DROPDOWN_WIDTH = 88;
    private static final int DESCRIPTION_WIDTH = 134;
    private static final int DESCRIPTION_LINE_SPACING = 10;

    private EditBox nameBox;
    private String pendingSubmittedName;
    private IconButton closeButton;
    private boolean categoryDropdownOpen;
    private boolean intentDropdownOpen;
    private boolean controlSelected;
    private DriveSignalCategory selectedCategory = DriveSignalCategory.LIFT;
    private int categoryScroll;
    private int intentScroll;
    private int statusValueX;
    private int statusValueY;
    private int statusValueWidth;
    private int statusValueHeight;
    private ListDropdown listDropdown;
    private final List<TooltipArea> buttonTooltips = new ArrayList<>();
    private final List<TooltipArea> dynamicTooltips = new ArrayList<>();

    public DriveModuleScreen(DriveModuleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        buttonTooltips.clear();
        dynamicTooltips.clear();
        listDropdown = new ListDropdown(this.font, DROPDOWN_WIDTH, DROPDOWN_VISIBLE_ROWS);
        DriveModuleBlockEntity module = moduleEntity();
        if (module != null) {
            module.config().map(DriveModuleConfig::intent).ifPresent(intent -> {
                selectedCategory = intent.category();
                controlSelected = true;
            });
        }
        initNameBox();
        initButtons();
    }

    private void initNameBox() {
        nameBox = new EditBox(new NoShadowFontWrapper(this.font), this.leftPos + 24, this.topPos + 4, PANEL_WIDTH - 48, 10, Component.empty());
        nameBox.setBordered(false);
        nameBox.setMaxLength(64);
        nameBox.setTextColor(0xE8EDF6);
        DriveModuleBlockEntity module = moduleEntity();
        if (module != null) {
            nameBox.setValue(module.moduleName());
        }
        nameBox.setResponder(ignored -> centerNameBox());
        nameBox.setFocused(false);
        centerNameBox();
        addRenderableWidget(nameBox);
    }

    private void initButtons() {
        closeButton = addIconButton(
                this.leftPos + 167,
                this.topPos + 154,
                AllIcons.I_CONFIRM,
                this::onClose,
                Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.close.tooltip")
        );
        statusValueX = this.leftPos + STATUS_FOOTER_X;
        statusValueY = this.topPos + STATUS_FOOTER_Y;
        statusValueWidth = STATUS_FOOTER_WIDTH;
        statusValueHeight = 12;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        DriveModuleBlockEntity module = moduleEntity();
        if (module == null) {
            return;
        }
        if (!nameBox.isFocused()) {
            String authoritativeName = module.moduleName();
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
        guiGraphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
        DriveModuleBlockEntity module = moduleEntity();
        if (module == null) {
            return;
        }

        drawSubtitle(guiGraphics);
        drawSelectionBox(guiGraphics, categoryDropdownRect(), selectedCategoryLabel(), categoryDropdownOpen, true);
        drawSelectionBox(guiGraphics, intentDropdownRect(), selectedIntentLabel(module.config()), intentDropdownOpen, controlSelected);
        drawSettings(guiGraphics, module.config(), mouseX, mouseY);
        drawStatus(guiGraphics, module.health());
        if (categoryDropdownOpen) {
            renderDropdown(guiGraphics, mouseX, mouseY, categoryDropdownRect(), categoryOptionLabels(), categoryScroll,
                    categoryOptions().indexOf(selectedCategory));
        }
        if (intentDropdownOpen && controlSelected) {
            renderDropdown(guiGraphics, mouseX, mouseY, intentDropdownRect(), intentOptionLabels(), intentScroll,
                    module.config().map(this::intentOptionIndex).orElse(-1));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        dynamicTooltips.clear();
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHeaderEditIcon(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderCustomTooltips(guiGraphics, mouseX, mouseY);
    }

    private void drawSubtitle(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(font,
                Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.subtitle"),
                this.leftPos + PANEL_WIDTH / 2,
                this.topPos + 21,
                SUBTEXT_COLOR);
        guiGraphics.drawString(font,
                Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.category"),
                this.leftPos + 29,
                this.topPos + 54,
                LABEL_COLOR,
                false);
        guiGraphics.drawString(font,
                Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent"),
                this.leftPos + 29,
                this.topPos + 70,
                LABEL_COLOR,
                false);
    }

    private void renderHeaderEditIcon(GuiGraphics guiGraphics) {
        if (nameBox == null || nameBox.isFocused()) {
            return;
        }
        int iconX = Math.min(nameBox.getX() + font.width(nameBox.getValue()) + 5, this.leftPos + PANEL_WIDTH - 24);
        EDIT_NAME_ICON.render(guiGraphics, iconX, this.topPos + 1);
    }

    private void drawSelectionBox(GuiGraphics guiGraphics, Rect rect, Component value, boolean open, boolean selectable) {
        guiGraphics.fill(rect.x, rect.y, rect.right(), rect.bottom(), open ? BOX_SELECTED : BOX_FILL);
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, BOX_OUTLINE);
        guiGraphics.drawString(font, value, rect.x + 4, rect.y + 2, TEXT_COLOR, false);
        if (selectable) {
            guiGraphics.drawString(font, Component.literal(open ? "\u25b2" : "\u25bc"), rect.right() - 9, rect.y + 2, SUBTEXT_COLOR, false);
        }
    }

    private void drawSettings(GuiGraphics guiGraphics, Optional<DriveModuleConfig> config, int mouseX, int mouseY) {
        int x = this.leftPos + 29;
        int y = this.topPos + 99;
        if (config.isEmpty()) {
            guiGraphics.drawString(font,
                    Component.translatable(controlSelected
                            ? "gui.create_aeronautics_automated_logistics.drive_module.select_role"
                            : "gui.create_aeronautics_automated_logistics.drive_module.select_control"),
                    x,
                    y,
                    SUBTEXT_COLOR,
                    false);
            return;
        }
        DriveModuleConfig selectedConfig = config.get();
        if (selectedConfig.intent().category() != selectedCategory) {
            guiGraphics.drawString(font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.choose_role"),
                    x,
                    y,
                    SUBTEXT_COLOR,
                    false);
            return;
        }
        int descriptionY = this.topPos + (selectedConfig.supportsInversion() ? 106 : 86);
        List<net.minecraft.util.FormattedCharSequence> descriptionLines = font.split(
                describeIntent(selectedConfig.intent()),
                DESCRIPTION_WIDTH
        );
        for (int lineIndex = 0; lineIndex < descriptionLines.size(); lineIndex++) {
            guiGraphics.drawString(
                    font,
                    descriptionLines.get(lineIndex),
                    x,
                    descriptionY + lineIndex * DESCRIPTION_LINE_SPACING,
                    DESCRIPTION_COLOR,
                    false
            );
        }
        if (selectedConfig.supportsInversion()) {
            guiGraphics.drawString(font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.invert"),
                    x,
                    this.topPos + 86,
                    LABEL_COLOR,
                    false);
            drawInvertCheckbox(guiGraphics, invertSignalRect(), selectedConfig.inverted());
            dynamicTooltips.add(new TooltipArea(
                    invertSignalRect().x,
                    invertSignalRect().y,
                    invertSignalRect().width,
                    invertSignalRect().height,
                    List.of(Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.invert.tooltip"))
            ));
        }
    }

    private void drawStatus(GuiGraphics guiGraphics, DriveModuleHealth health) {
        int color = switch (health) {
            case LINKED -> TEXT_COLOR;
            case UNLINKED, NO_ASSEMBLED_VEHICLE, NO_ADVANCED_CONTROLLER -> SUBTEXT_COLOR;
            default -> INVALID_COLOR;
        };
        guiGraphics.drawCenteredString(font, statusLabel(health), statusValueX + statusValueWidth / 2, statusValueY, color);
        dynamicTooltips.add(new TooltipArea(statusValueX, statusValueY, statusValueWidth, statusValueHeight, statusTooltip(health)));
    }

    private void drawInvertCheckbox(GuiGraphics guiGraphics, Rect rect, boolean checked) {
        guiGraphics.fill(rect.x, rect.y, rect.right(), rect.bottom(), BOX_FILL);
        guiGraphics.renderOutline(rect.x, rect.y, rect.width, rect.height, BOX_OUTLINE);
        if (!checked) {
            return;
        }
        int checkColor = 0xFFDAE7B3;
        guiGraphics.fill(rect.x + 2, rect.y + 6, rect.x + 4, rect.y + 8, checkColor);
        guiGraphics.fill(rect.x + 3, rect.y + 7, rect.x + 5, rect.y + 9, checkColor);
        guiGraphics.fill(rect.x + 4, rect.y + 8, rect.x + 6, rect.y + 10, checkColor);
        guiGraphics.fill(rect.x + 5, rect.y + 7, rect.x + 7, rect.y + 9, checkColor);
        guiGraphics.fill(rect.x + 6, rect.y + 6, rect.x + 8, rect.y + 8, checkColor);
        guiGraphics.fill(rect.x + 7, rect.y + 5, rect.x + 9, rect.y + 7, checkColor);
        guiGraphics.fill(rect.x + 8, rect.y + 4, rect.x + 10, rect.y + 6, checkColor);
    }

    private void renderDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, Rect anchor, List<String> options, int scroll, int selectedIndex) {
        if (listDropdown == null || options.isEmpty()) {
            return;
        }
        listDropdown.render(guiGraphics, mouseX, mouseY, anchor.x, anchor.bottom() + 2, options, selectedIndex, scroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (nameBox != null && !nameBox.isFocused()
                    && mouseY > this.topPos && mouseY < this.topPos + 18
                    && mouseX > this.leftPos && mouseX < this.leftPos + PANEL_WIDTH) {
                nameBox.setFocused(true);
                nameBox.setHighlightPos(0);
                setFocused(nameBox);
                return true;
            }
            Rect categoryRect = categoryDropdownRect();
            Rect intentRect = intentDropdownRect();
            DriveModuleBlockEntity module = moduleEntity();
            if (categoryDropdownOpen) {
                if (handleDropdownClick(mouseX, mouseY, true)) {
                    return true;
                }
                if (!categoryRect.contains(mouseX, mouseY)) {
                    categoryDropdownOpen = false;
                }
            }
            if (intentDropdownOpen) {
                if (handleDropdownClick(mouseX, mouseY, false)) {
                    return true;
                }
                if (!intentRect.contains(mouseX, mouseY)) {
                    intentDropdownOpen = false;
                }
            }
            if (categoryRect.contains(mouseX, mouseY)) {
                categoryDropdownOpen = !categoryDropdownOpen;
                intentDropdownOpen = false;
                return true;
            }
            if (intentRect.contains(mouseX, mouseY)) {
                if (controlSelected) {
                    intentDropdownOpen = !intentDropdownOpen;
                }
                categoryDropdownOpen = false;
                return true;
            }
            if (module != null
                    && module.config().filter(DriveModuleConfig::supportsInversion).isPresent()
                    && invertSignalRect().contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new DriveModuleMenuActionPayload(
                        menu.modulePos(),
                        DriveModuleMenuActionPayload.ACTION_TOGGLE_INVERTED,
                        ""
                ));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (categoryDropdownOpen) {
            categoryScroll = listDropdown.scrolled(categoryScroll, categoryOptions().size(), scrollY);
            return true;
        }
        if (intentDropdownOpen) {
            intentScroll = listDropdown.scrolled(intentScroll, intentOptions().size(), scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitName();
                setFocused(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nameBox.setFocused(false);
                DriveModuleBlockEntity module = moduleEntity();
                if (module != null) {
                    nameBox.setValue(module.moduleName());
                }
                return true;
            }
        }
        if (categoryDropdownOpen || intentDropdownOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                categoryDropdownOpen = false;
                intentDropdownOpen = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void onClose() {
        submitName();
        super.onClose();
    }

    private void renderCustomTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (TooltipArea area : dynamicTooltips) {
            if (area.contains(mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, area.lines, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        for (TooltipArea area : buttonTooltips) {
            if (area.contains(mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, area.lines, java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    private void submitName() {
        if (nameBox == null || minecraft == null || minecraft.player == null) {
            return;
        }
        DriveModuleBlockEntity module = moduleEntity();
        if (module == null) {
            return;
        }
        String normalized = nameBox.getValue().trim();
        if (normalized.equals(module.moduleName())) {
            return;
        }
        pendingSubmittedName = normalized;
        PacketDistributor.sendToServer(new UpdateIdentityNamePayload(menu.modulePos(), normalized));
    }

    private boolean handleDropdownClick(double mouseX, double mouseY, boolean categoryDropdown) {
        DriveModuleBlockEntity module = moduleEntity();
        if (module == null || listDropdown == null) {
            return false;
        }
        Rect anchor = categoryDropdown ? categoryDropdownRect() : intentDropdownRect();
        List<? extends Enum<?>> options = categoryDropdown ? categoryOptions() : intentOptions();
        List<String> labels = categoryDropdown ? categoryOptionLabels() : intentOptionLabels();
        int scroll = categoryDropdown ? categoryScroll : intentScroll;
        int optionIndex = listDropdown.clickedIndex(mouseX, mouseY, anchor.x, anchor.bottom() + 2, labels, scroll);
        if (optionIndex < 0) {
            return false;
        }
        if (optionIndex >= options.size()) {
            return true;
        }
        Enum<?> selected = options.get(optionIndex);
        if (categoryDropdown) {
            selectedCategory = (DriveSignalCategory) selected;
            controlSelected = true;
            categoryDropdownOpen = false;
            intentScroll = 0;
        } else {
            PacketDistributor.sendToServer(new DriveModuleMenuActionPayload(menu.modulePos(), DriveModuleMenuActionPayload.ACTION_SET_INTENT, selected.name()));
            intentDropdownOpen = false;
        }
        return true;
    }

    private List<DriveSignalCategory> categoryOptions() {
        return List.of(DriveSignalCategory.values());
    }

    private List<String> categoryOptionLabels() {
        return categoryOptions().stream().map(this::categoryLabel).toList();
    }

    private List<DriveIntentType> intentOptions() {
        return DriveIntentType.forCategory(selectedCategory).stream()
                .filter(intent -> intent != DriveIntentType.LIFT_INCREASE
                        && intent != DriveIntentType.LIFT_DECREASE)
                .toList();
    }

    private List<String> intentOptionLabels() {
        return intentOptions().stream().map(this::outputRoleLabel).toList();
    }

    private int intentOptionIndex(DriveModuleConfig config) {
        return intentOptions().indexOf(config.intent());
    }

    private void centerNameBox() {
        if (nameBox == null) {
            return;
        }
        int textWidth = font.width(nameBox.getValue());
        int available = PANEL_WIDTH - 48;
        nameBox.setWidth(available);
        nameBox.setX(this.leftPos + 24 + Math.max(0, (available - Math.min(textWidth, available - 8)) / 2 - 4));
    }

    private Rect categoryDropdownRect() {
        return new Rect(this.leftPos + 77, this.topPos + 51, DROPDOWN_WIDTH, 12);
    }

    private Rect intentDropdownRect() {
        return new Rect(this.leftPos + 77, this.topPos + 67, DROPDOWN_WIDTH, 12);
    }

    private Rect invertSignalRect() {
        return new Rect(this.leftPos + 77, this.topPos + 83, 12, 12);
    }

    private Component selectedCategoryLabel() {
        if (!controlSelected) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.select_control");
        }
        return Component.literal(categoryLabel(selectedCategory));
    }

    private Component selectedIntentLabel(Optional<DriveModuleConfig> config) {
        if (!controlSelected || config.isEmpty() || config.get().intent().category() != selectedCategory) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.select_role");
        }
        return Component.literal(outputRoleLabel(config.get().intent()));
    }

    private Component statusLabel(DriveModuleHealth health) {
        return switch (health) {
            case LINKED -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.ready");
            case UNLINKED, NO_ASSEMBLED_VEHICLE, NO_ADVANCED_CONTROLLER -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.unlinked");
            case CONTROLLER_UNAVAILABLE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.waiting");
            case MULTIPLE_CONTROLLERS, WRONG_VEHICLE, CONFLICTING_CONFIGURATION -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.invalid");
        };
    }

    private List<Component> statusTooltip(DriveModuleHealth health) {
        return switch (health) {
            case LINKED -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.ready"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.ready")
            );
            case UNLINKED -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.unlinked"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.unlinked")
            );
            case NO_ASSEMBLED_VEHICLE -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.unlinked"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.no_vehicle")
            );
            case NO_ADVANCED_CONTROLLER -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.unlinked"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.no_advanced")
            );
            case CONTROLLER_UNAVAILABLE -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.waiting"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.unavailable")
            );
            case MULTIPLE_CONTROLLERS -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.invalid"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.multiple")
            );
            case WRONG_VEHICLE -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.invalid"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.wrong_vehicle")
            );
            case CONFLICTING_CONFIGURATION -> List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.status.invalid"),
                    Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.tooltip.status.config")
            );
        };
    }

    private Component describeIntent(DriveIntentType intent) {
        return switch (intent) {
            case LIFT_TARGET_STRENGTH -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.lift_target_strength");
            case LIFT_INCREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.lift_increase");
            case LIFT_DECREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.lift_decrease");
            case FORWARD_BACK_DIRECTION -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.forward_back_direction");
            case FORWARD_BACK_TARGET_STRENGTH -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.forward_back_target_strength");
            case FORWARD_BACK_INCREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.forward_back_increase");
            case FORWARD_BACK_DECREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.forward_back_decrease");
            case FORWARD_BACK_FORWARD -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.forward_back_forward");
            case FORWARD_BACK_BACKWARD -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.forward_back_backward");
            case LEFT_RIGHT_DIRECTION -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.left_right_direction");
            case LEFT_RIGHT_TARGET_STRENGTH -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.left_right_target_strength");
            case LEFT_RIGHT_INCREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.left_right_increase");
            case LEFT_RIGHT_DECREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.left_right_decrease");
            case LEFT_RIGHT_LEFT -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.left_right_left");
            case LEFT_RIGHT_RIGHT -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.left_right_right");
            case TURN_DIRECTION -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.turn_direction");
            case TURN_TARGET_STRENGTH -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.turn_target_strength");
            case TURN_INCREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.turn_increase");
            case TURN_DECREASE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.turn_decrease");
            case TURN_CLOCKWISE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.turn_clockwise");
            case TURN_ANTICLOCKWISE -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.turn_anticlockwise");
            case ROTATIONAL_FORCE_ENABLED -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.rotational_force_enabled");
            case DOCKED -> Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.intent_desc.docked");
        };
    }

    private String categoryLabel(DriveSignalCategory category) {
        return switch (category) {
            case LIFT -> "Lift";
            case FORWARD_BACK -> "Forward/Back";
            case LEFT_RIGHT -> "Left/Right";
            case TURN -> "Turning";
            case ROTATIONAL_FORCE -> "Rot. Force";
            case DOCKING -> "Docking";
        };
    }

    private String outputRoleLabel(DriveIntentType intent) {
        return switch (intent) {
            case LIFT_TARGET_STRENGTH -> "Burn Strength";
            case LIFT_INCREASE -> "Rotor Faster";
            case LIFT_DECREASE -> "Rotor Slower";
            case FORWARD_BACK_TARGET_STRENGTH, LEFT_RIGHT_TARGET_STRENGTH, TURN_TARGET_STRENGTH -> "Strength";
            case FORWARD_BACK_INCREASE, LEFT_RIGHT_INCREASE, TURN_INCREASE -> "Increase";
            case FORWARD_BACK_DECREASE, LEFT_RIGHT_DECREASE, TURN_DECREASE -> "Decrease";
            case FORWARD_BACK_FORWARD -> "Forward";
            case FORWARD_BACK_BACKWARD -> "Backward";
            case LEFT_RIGHT_LEFT -> "Left";
            case LEFT_RIGHT_RIGHT -> "Right";
            case TURN_CLOCKWISE -> "Clockwise";
            case TURN_ANTICLOCKWISE -> "Anticlockwise";
            case FORWARD_BACK_DIRECTION, LEFT_RIGHT_DIRECTION, TURN_DIRECTION -> "Direction";
            case ROTATIONAL_FORCE_ENABLED -> "Enabled";
            case DOCKED -> "Docked";
        };
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private IconButton addIconButton(int x, int y, ScreenElement icon, Runnable action, Component... tooltip) {
        IconButton button = new IconButton(x, y, icon);
        button.withCallback(action);
        addRenderableWidget(button);
        if (tooltip.length > 0) {
            buttonTooltips.add(new TooltipArea(x, y, 18, 18, java.util.Arrays.asList(tooltip)));
        }
        return button;
    }

    private @Nullable DriveModuleBlockEntity moduleEntity() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getBlockEntity(menu.modulePos()) instanceof DriveModuleBlockEntity module ? module : null;
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < right() && mouseY < bottom();
        }
    }

    private record TooltipArea(int x, int y, int width, int height, List<Component> lines) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }
}

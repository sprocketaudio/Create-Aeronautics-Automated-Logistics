package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import java.util.List;
import net.minecraft.client.Minecraft;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.network.chat.Component;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AdvancedTransponderOverlayClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.minecraft.ChatFormatting;

/**
 * Advanced-only extension seam for the shared Ship Transponder screen. It
 * currently owns only the legacy prototype overlay behavior.
 */
public final class AdvancedTransponderUiSupport {
    private final ShipTransponderMenu menu;

    public AdvancedTransponderUiSupport(ShipTransponderMenu menu) {
        this.menu = menu;
    }

    public void toggleOverlay() {
        AdvancedTransponderOverlayClientState.toggle(menu.transponderPos());
    }

    public boolean overlayEnabled() {
        return AdvancedTransponderOverlayClientState.isEnabled(menu.transponderPos());
    }

    public boolean appliesTo(Minecraft minecraft) {
        return minecraft != null
                && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.transponderPos()) instanceof AdvancedTransponderBlockEntity;
    }

    public List<Component> overlayTooltip() {
        return List.of(
                Component.translatable(
                        overlayEnabled()
                                ? "gui.create_aeronautics_automated_logistics.advanced_transponder.hide_overlay"
                                : "gui.create_aeronautics_automated_logistics.advanced_transponder.show_overlay"
                ),
                Component.translatable("gui.create_aeronautics_automated_logistics.advanced_transponder.overlay.tooltip")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        );
    }

    public void syncOverlayButtonState(
            Minecraft minecraft,
            IconButton overlayToggleButton
    ) {
        if (overlayToggleButton == null) {
            return;
        }
        boolean advancedTransponder = appliesTo(minecraft);
        overlayToggleButton.visible = advancedTransponder;
        overlayToggleButton.active = advancedTransponder;
        overlayToggleButton.green = advancedTransponder && overlayEnabled();
    }

    public boolean shouldRenderOverlayTooltip(
            Minecraft minecraft,
            IconButton overlayToggleButton
    ) {
        return overlayToggleButton != null
                && overlayToggleButton.isHovered()
                && appliesTo(minecraft);
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class IdentityNames {
    private IdentityNames() {
    }

    public static String defaultStationName(UUID id) {
        return "Station " + shortId(id);
    }

    public static String defaultShipName(UUID id) {
        return "Ship " + shortId(id);
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public static String itemName(ItemStack stack) {
        Component hoverName = stack.getHoverName();
        return sanitize(hoverName.getString());
    }

    public static String sanitize(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned;
    }
}

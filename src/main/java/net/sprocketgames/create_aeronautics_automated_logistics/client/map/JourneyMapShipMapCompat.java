package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

final class JourneyMapShipMapCompat {
    private static final String FULLSCREEN_CLASS_NAME = "journeymap.client.ui.fullscreen.Fullscreen";

    private JourneyMapShipMapCompat() {
    }

    static void refresh() {
        if (ModList.get().isLoaded("journeymap")) {
            JourneyMapShipPlugin.refreshIfAvailable();
            refreshFullscreenIfOpen();
        }
    }

    private static void refreshFullscreenIfOpen() {
        Object screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getName().equals(FULLSCREEN_CLASS_NAME)) {
            return;
        }

        try {
            Method requestRefresh = screen.getClass().getMethod("requestRefresh");
            requestRefresh.invoke(screen);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}

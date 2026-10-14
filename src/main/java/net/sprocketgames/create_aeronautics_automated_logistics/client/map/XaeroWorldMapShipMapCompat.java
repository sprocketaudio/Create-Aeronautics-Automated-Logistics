package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsClientConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;

/** Optional Xaero World Map overlay. Kept isolated so Xaero remains an optional client dependency. */
public final class XaeroWorldMapShipMapCompat {
    private static final String MAP_SCREEN_CLASS = "xaero.map.gui.GuiMap";
    private static boolean reflectionUnavailable;

    private XaeroWorldMapShipMapCompat() {
    }

    public static void renderLargeMapOverlay(ScreenEvent.Render.Post event) {
        if (!AutomatedLogisticsClientConfig.xaeroMapsIntegrationEnabled()
                || reflectionUnavailable
                || !ModList.get().isLoaded("xaeroworldmap")
                || !event.getScreen().getClass().getName().equals(MAP_SCREEN_CLASS)) {
            return;
        }

        try {
            Screen map = event.getScreen();
            double cameraX = privateDouble(map, "cameraX");
            double cameraZ = privateDouble(map, "cameraZ");
            double scale = privateDouble(map, "scale");
            double screenScale = privateDouble(map, "screenScale");
            double coordinateScale = privateDouble(map, "prevPlayerDimDiv");
            ResourceKey<Level> viewedDimension = viewedDimension(map);
            if (viewedDimension == null || scale <= 0.0D || screenScale <= 0.0D) {
                return;
            }
            if (coordinateScale == 0.0D) {
                coordinateScale = 1.0D;
            }

            double pixelsPerBlock = scale / screenScale;
            for (ShipMapMarker marker : ShipMapClientState.markers()) {
                if (!marker.dimension().equals(viewedDimension)) {
                    continue;
                }
                int drawX = (int) Math.round(map.width / 2.0D
                        + (marker.position().x / coordinateScale - cameraX) * pixelsPerBlock);
                int drawY = (int) Math.round(map.height / 2.0D
                        + (marker.position().z / coordinateScale - cameraZ) * pixelsPerBlock);
                if (drawX < -24 || drawX > map.width + 24 || drawY < -24 || drawY > map.height + 24) {
                    continue;
                }
                MapMarkerRenderUtil.drawMarker(event.getGuiGraphics(), marker, drawX, drawY);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reflectionUnavailable = true;
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Xaero World Map marker overlay could not read the active map view; disabling it for this session",
                    exception
            );
        }
    }

    private static double privateDouble(Screen map, String fieldName) throws ReflectiveOperationException {
        return findField(map.getClass(), fieldName).getDouble(map);
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> viewedDimension(Screen map) throws ReflectiveOperationException {
        return (ResourceKey<Level>) findField(map.getClass(), "lastViewedDimensionId").get(map);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}

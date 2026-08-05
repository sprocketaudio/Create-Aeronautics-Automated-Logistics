package net.sprocketgames.create_aeronautics_automated_logistics.client.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapShipPlugin implements IClientPlugin {
    private static final int MARKER_DISPLAY_ORDER = 10_000;
    private static final ResourceLocation AIRSHIP_ICON = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsAutomatedLogistics.MOD_ID,
            "textures/gui/blimp.png"
    );
    private static final ResourceLocation TRAIN_ICON = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsAutomatedLogistics.MOD_ID,
            "textures/gui/train.png"
    );
    private static JourneyMapShipPlugin instance;

    private final Map<UUID, MarkerOverlay> overlays = new HashMap<>();
    private final Map<UUID, ShipMapMarker> shownMarkers = new HashMap<>();
    private IClientAPI api;

    public JourneyMapShipPlugin() {
        instance = this;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(
                CreateAeronauticsAutomatedLogistics.MOD_ID,
                JourneyMapFullscreenOverlayRenderer::render
        );
        refresh();
    }

    @Override
    public String getModId() {
        return CreateAeronauticsAutomatedLogistics.MOD_ID;
    }

    static void refreshIfAvailable() {
        if (instance != null && instance.api != null) {
            instance.refresh();
        }
    }

    private void refresh() {
        Set<UUID> currentIds = new HashSet<>();
        for (ShipMapMarker marker : ShipMapClientState.markers()) {
            currentIds.add(marker.transponderId());
            if (marker.equals(shownMarkers.get(marker.transponderId()))) {
                continue;
            }
            MarkerOverlay overlay = overlays.computeIfAbsent(marker.transponderId(), ignored -> createOverlay(marker));
            overlay.setPoint(BlockPos.containing(marker.position()))
                    .setIcon(createIcon(marker.transportMode()))
                    .setDimension(marker.dimension())
                    .setLabel(marker.shipName())
                    .setTitle(marker.shipName() + " - " + marker.displayState())
                    .setOverlayGroupName("Automated Logistics");
            try {
                api.show(overlay);
                shownMarkers.put(marker.transponderId(), marker);
            } catch (Exception exception) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Failed to update JourneyMap marker for ship {} ({})",
                        marker.shipName(),
                        marker.transponderId(),
                        exception
                );
            }
        }

        Set<UUID> removed = new HashSet<>(overlays.keySet());
        removed.removeAll(currentIds);
        for (UUID id : removed) {
            api.remove(overlays.remove(id));
            shownMarkers.remove(id);
        }
    }

    private static MarkerOverlay createOverlay(ShipMapMarker marker) {
        MarkerOverlay overlay = new MarkerOverlay(
                CreateAeronauticsAutomatedLogistics.MOD_ID,
                BlockPos.containing(marker.position()),
                createIcon(marker.transportMode())
        );
        overlay.setDisplayOrder(MARKER_DISPLAY_ORDER);
        overlay.setActiveUIs(Context.UI.Minimap);
        overlay.setTextProperties(new TextProperties()
                .setScale(1.0F)
                .setColor(0xFFF4D78A)
                .setBackgroundColor(0xFF000000)
                .setBackgroundOpacity(0.5F)
                .setOpacity(1.0F)
                .setFontShadow(false)
                .setOffsetY(-18)
                .setActiveUIs(Context.UI.Minimap));
        return overlay;
    }

    private static ResourceLocation iconFor(TransportMode transportMode) {
        return transportMode == TransportMode.TRAIN ? TRAIN_ICON : AIRSHIP_ICON;
    }

    private static MapImage createIcon(TransportMode transportMode) {
        return new MapImage(iconFor(transportMode), 56, 56)
                .setDisplayWidth(22)
                .setDisplayHeight(22)
                .centerAnchors();
    }
}

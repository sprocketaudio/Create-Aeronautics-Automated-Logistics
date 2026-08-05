package net.sprocketgames.create_aeronautics_automated_logistics.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.map.ShipMapClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.LogisticsTerminalMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationPermissionService;
import org.lwjgl.glfw.GLFW;

public class LogisticsTerminalScreen extends AbstractContainerScreen<LogisticsTerminalMenu> {
    private static final ResourceLocation FRAME =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/logistics_terminal.png");
    private static final ResourceLocation AIRSHIP_ICON =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/blimp.png");
    private static final ResourceLocation TRAIN_ICON =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/train.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int FRAME_U = 8;
    private static final int FRAME_V = 38;
    private static final int FRAME_WIDTH = 240;
    private static final int FRAME_HEIGHT = 180;
    private static final int BORDER_LEFT = 10;
    private static final int BORDER_RIGHT = 10;
    private static final int BORDER_TOP = 12;
    private static final int BORDER_BOTTOM = 13;
    private static final int INNER_SOURCE_WIDTH = 220;
    private static final int INNER_SOURCE_HEIGHT = 155;
    private static final int MIN_WIDTH = 240;
    private static final int MAX_WIDTH = 448;
    private static final int MIN_HEIGHT = 180;
    private static final int MAX_HEIGHT = 336;
    private static final int HEADER_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 12;
    private static final int CONTENT_PADDING = 6;
    private static final int ICON_TEXTURE_SIZE = 56;
    private static final int ICON_SIZE = 18;
    private static final int NODE_RADIUS = 3;
    private static final int HOVER_RADIUS = 12;
    private static final int[] ROUTE_COLORS = {
            0xFF7CC9FF, 0xFFF7BE6A, 0xFF89E0A8, 0xFFFF8F8F,
            0xFFC6A0FF, 0xFFFFD36E, 0xFF7DE1D8, 0xFFFFA5D8
    };
    private static final int MAP_BACKGROUND = 0x11000000;
    private static final int GRID_COLOR = 0x1AFFFFFF;
    private static final int STATION_FILL = 0xFFE7D6B4;
    private static final int STATION_OUTLINE = 0xFF4F4036;
    private static final int LABEL_COLOR = 0xFFF4D78A;
    private static final int LABEL_BACKGROUND = 0x70000000;
    private static final int IMPORTANT_STATE_COLOR = 0xFFFF9B7D;
    private static final int ROUTE_LINE_ALPHA = 0xD0000000;
    private static final int ROUTE_HOVER_COLOR = 0xFFF6F0E4;
    private static final double ROUTE_ARROW_SPACING_PIXELS = 96.0D;
    private static final double ROUTE_ARROW_MIN_ROUTE_PIXELS = 6.0D;
    private static final int ROUTE_ARROW_SIZE = 4;
    private static final int ROUTE_ARROW_SHADOW = 0xB0000000;
    private static final int ROUTE_ARROW_COLOR = 0xFFF7EFE0;
    private static final double ROUTE_ARROW_SPEED_PIXELS_PER_SECOND = 28.0D;
    private static final double ROUTE_HOVER_DISTANCE = 6.0D;

    private double zoom = 1.0D;
    private double panWorldX;
    private double panWorldZ;
    private boolean draggingMap;
    private int lastDragX;
    private int lastDragY;
    private Optional<UUID> pinnedStationId = Optional.empty();
    private Optional<UUID> pinnedTransponderId = Optional.empty();
    private HoveredEntry hoveredEntry;
    private List<Component> hoveredTooltip = List.of();

    public LogisticsTerminalScreen(LogisticsTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        int targetWidth = Mth.clamp((int) (this.width * 0.8F), MIN_WIDTH, MAX_WIDTH);
        int targetHeight = Mth.clamp((int) (targetWidth * 0.75F), MIN_HEIGHT, MAX_HEIGHT);
        int maxHeightFromScreen = (int) (this.height * 0.82F);
        if (targetHeight > maxHeightFromScreen) {
            targetHeight = Mth.clamp(maxHeightFromScreen, MIN_HEIGHT, MAX_HEIGHT);
            targetWidth = Mth.clamp((int) (targetHeight / 0.75F), MIN_WIDTH, MAX_WIDTH);
        }
        this.imageWidth = targetWidth;
        this.imageHeight = targetHeight;
        super.init();
        this.titleLabelX = 12;
        this.titleLabelY = 14;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        hoveredEntry = null;
        hoveredTooltip = List.of();
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (!hoveredTooltip.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredTooltip, Optional.empty(), mouseX, mouseY);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        drawFrame(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
        drawLogisticsMap(guiGraphics, mouseX, mouseY);
    }

    private void drawFrame(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int innerWidth = Math.max(0, width - BORDER_LEFT - BORDER_RIGHT);
        int innerHeight = Math.max(0, height - BORDER_TOP - BORDER_BOTTOM);

        guiGraphics.blit(FRAME, x, y, FRAME_U, FRAME_V, BORDER_LEFT, BORDER_TOP, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x + width - BORDER_RIGHT, y, FRAME_U + FRAME_WIDTH - BORDER_RIGHT, FRAME_V, BORDER_RIGHT, BORDER_TOP, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x, y + height - BORDER_BOTTOM, FRAME_U, FRAME_V + FRAME_HEIGHT - BORDER_BOTTOM, BORDER_LEFT, BORDER_BOTTOM, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x + width - BORDER_RIGHT, y + height - BORDER_BOTTOM, FRAME_U + FRAME_WIDTH - BORDER_RIGHT, FRAME_V + FRAME_HEIGHT - BORDER_BOTTOM, BORDER_RIGHT, BORDER_BOTTOM, TEXTURE_SIZE, TEXTURE_SIZE);

        guiGraphics.blit(FRAME, x + BORDER_LEFT, y, innerWidth, BORDER_TOP, FRAME_U + BORDER_LEFT, FRAME_V, INNER_SOURCE_WIDTH, BORDER_TOP, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x + BORDER_LEFT, y + height - BORDER_BOTTOM, innerWidth, BORDER_BOTTOM, FRAME_U + BORDER_LEFT, FRAME_V + FRAME_HEIGHT - BORDER_BOTTOM, INNER_SOURCE_WIDTH, BORDER_BOTTOM, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x, y + BORDER_TOP, BORDER_LEFT, innerHeight, FRAME_U, FRAME_V + BORDER_TOP, BORDER_LEFT, INNER_SOURCE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x + width - BORDER_RIGHT, y + BORDER_TOP, BORDER_RIGHT, innerHeight, FRAME_U + FRAME_WIDTH - BORDER_RIGHT, FRAME_V + BORDER_TOP, BORDER_RIGHT, INNER_SOURCE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(FRAME, x + BORDER_LEFT, y + BORDER_TOP, innerWidth, innerHeight, FRAME_U + BORDER_LEFT, FRAME_V + BORDER_TOP, INNER_SOURCE_WIDTH, INNER_SOURCE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void drawLogisticsMap(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MapRect rect = mapRect();
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), MAP_BACKGROUND);
        guiGraphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());

        ResourceKey<Level> dimension = currentDimension();
        List<RouteSegment> routes = visibleRoutes(dimension);
        List<AirshipStationSnapshot> stations = visibleStations(dimension);
        List<ShipMapMarker> markers = visibleMarkers(dimension);
        Map<UUID, Integer> routeColors = routeColors(routes);
        WorldBounds bounds = WorldBounds.from(routes, stations, markers);

        if (bounds.empty()) {
            guiGraphics.disableScissor();
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.logistics_terminal.empty"),
                    rect.centerX(),
                    rect.centerY() - 4,
                    0xFFD7C4A4);
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.create_aeronautics_automated_logistics.logistics_terminal.empty_hint"),
                    rect.centerX(),
                    rect.centerY() + 8,
                    0xFF9F8F78);
            return;
        }

        ViewState view = viewState(bounds, rect, stations, markers);
        Optional<UUID> hoveredMarkerId = hoveredMarkerId(markers, view, mouseX, mouseY);
        drawGrid(guiGraphics, rect, view);
        drawRoutes(guiGraphics, rect, routes, routeColors, view, mouseX, mouseY, hoveredMarkerId);
        drawStations(guiGraphics, stations, view, mouseX, mouseY);
        drawMarkers(guiGraphics, markers, view, mouseX, mouseY);

        guiGraphics.disableScissor();
        guiGraphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), 0x9057483D);
    }

    private void drawGrid(GuiGraphics guiGraphics, MapRect rect, ViewState view) {
        double worldSpacing = view.scale() <= 0.5D ? 64.0D : view.scale() <= 1.0D ? 32.0D : 16.0D;
        int pixelSpacing = Math.max(12, (int) Math.round(worldSpacing * view.scale()));
        for (int x = rect.x(); x < rect.right(); x += pixelSpacing) {
            guiGraphics.vLine(x, rect.y(), rect.bottom(), GRID_COLOR);
        }
        for (int y = rect.y(); y < rect.bottom(); y += pixelSpacing) {
            guiGraphics.hLine(rect.x(), rect.right(), y, GRID_COLOR);
        }
    }

    private void drawRoutes(
            GuiGraphics guiGraphics,
            MapRect rect,
            List<RouteSegment> routes,
            Map<UUID, Integer> routeColors,
            ViewState view,
            int mouseX,
            int mouseY,
            Optional<UUID> hoveredMarkerId
    ) {
        List<Integer> hoveredRouteIndices = new ArrayList<>();
        List<List<Point2>> projectedRoutePoints = new ArrayList<>(routes.size());
        int primaryHoveredRouteIndex = -1;
        double hoveredDistance = Double.MAX_VALUE;
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            RouteSegment route = routes.get(routeIndex);
            List<RoutePoint> points = route.points();
            int color = routeColors.getOrDefault(route.transponderId(), fallbackRouteColor(route.transponderId()));
            List<Point2> projectedPoints = new ArrayList<>(points.size());
            projectedRoutePoints.add(projectedPoints);
            for (RoutePoint point : points) {
                projectedPoints.add(project(point.position(), view, rect));
            }
            double routeDistance = Double.MAX_VALUE;
            for (int i = 1; i < points.size(); i++) {
                Point2 from = projectedPoints.get(i - 1);
                Point2 to = projectedPoints.get(i);
                drawLine(guiGraphics, from.x(), from.y(), to.x(), to.y(), color, 1);
                if (hoveredMarkerId.isEmpty() || hoveredMarkerId.filter(route.transponderId()::equals).isPresent()) {
                    double distance = distanceToSegment(mouseX, mouseY, from, to);
                    routeDistance = Math.min(routeDistance, distance);
                }
            }
            if (routeDistance <= ROUTE_HOVER_DISTANCE) {
                hoveredRouteIndices.add(routeIndex);
            }
            if (hoveredMarkerId.filter(route.transponderId()::equals).isPresent() && !hoveredRouteIndices.contains(routeIndex)) {
                hoveredRouteIndices.add(routeIndex);
            }
            if (routeDistance < hoveredDistance) {
                hoveredDistance = routeDistance;
                primaryHoveredRouteIndex = routeIndex;
            }
        }
        if (primaryHoveredRouteIndex < 0 && hoveredMarkerId.isPresent()) {
            primaryHoveredRouteIndex = routes.stream()
                    .filter(route -> route.transponderId().equals(hoveredMarkerId.get()))
                    .findFirst()
                    .map(routes::indexOf)
                    .orElse(-1);
        }
        if (!hoveredRouteIndices.isEmpty() && primaryHoveredRouteIndex >= 0) {
            for (int hoveredRouteIndex : hoveredRouteIndices) {
                List<Point2> projectedPoints = projectedRoutePoints.get(hoveredRouteIndex);
                for (int i = 1; i < projectedPoints.size(); i++) {
                    Point2 from = projectedPoints.get(i - 1);
                    Point2 to = projectedPoints.get(i);
                    drawLine(guiGraphics, from.x(), from.y(), to.x(), to.y(), ROUTE_HOVER_COLOR, 1);
                }
                drawDirectionArrows(guiGraphics, projectedPoints);
            }
            RouteSegment route = routes.get(primaryHoveredRouteIndex);
            hoveredTooltip = routeTooltip(route);
        }
    }

    private Optional<UUID> hoveredMarkerId(List<ShipMapMarker> markers, ViewState view, int mouseX, int mouseY) {
        for (ShipMapMarker marker : markers) {
            Point2 point = project(marker.position(), view, mapRect());
            if (distance(mouseX, mouseY, point.x(), point.y()) <= HOVER_RADIUS) {
                return Optional.of(marker.transponderId());
            }
        }
        return Optional.empty();
    }

    private void drawStations(GuiGraphics guiGraphics, List<AirshipStationSnapshot> stations, ViewState view, int mouseX, int mouseY) {
        for (AirshipStationSnapshot station : stations) {
            Point2 point = project(Vec3.atCenterOf(station.stationPos()), view, mapRect());
            drawStationNode(guiGraphics, point.x(), point.y(), station.stationId().equals(pinnedStationId.orElse(null)));
            drawLabel(guiGraphics, station.stationName(), point.x(), point.y() - 8, LABEL_COLOR);
            if (distance(mouseX, mouseY, point.x(), point.y()) <= HOVER_RADIUS) {
                hoveredEntry = new HoveredEntry(HoverKind.STATION, station.stationId(), Optional.empty());
                hoveredTooltip = stationTooltip(station);
            }
        }
    }

    private void drawMarkers(GuiGraphics guiGraphics, List<ShipMapMarker> markers, ViewState view, int mouseX, int mouseY) {
        for (ShipMapMarker marker : markers) {
            Point2 point = project(marker.position(), view, mapRect());
            drawMarker(guiGraphics, marker, point.x(), point.y(), marker.transponderId().equals(pinnedTransponderId.orElse(null)));
            drawLabel(guiGraphics, marker.shipName(), point.x(), point.y() - 12, LABEL_COLOR);
            if (importantState(marker.state())) {
                drawLabel(guiGraphics, marker.displayState(), point.x(), point.y() + 10, IMPORTANT_STATE_COLOR);
            }
            if (distance(mouseX, mouseY, point.x(), point.y()) <= HOVER_RADIUS) {
                hoveredEntry = new HoveredEntry(HoverKind.MARKER, null, Optional.of(marker.transponderId()));
                hoveredTooltip = markerTooltip(marker);
            }
        }
    }

    private void drawStationNode(GuiGraphics guiGraphics, int centerX, int centerY, boolean pinned) {
        int radius = pinned ? NODE_RADIUS + 1 : NODE_RADIUS;
        guiGraphics.fill(centerX - radius, centerY - radius, centerX + radius + 1, centerY + radius + 1, STATION_OUTLINE);
        guiGraphics.fill(centerX - radius + 1, centerY - radius + 1, centerX + radius, centerY + radius, STATION_FILL);
    }

    private void drawMarker(GuiGraphics guiGraphics, ShipMapMarker marker, int centerX, int centerY, boolean pinned) {
        int size = pinned ? ICON_SIZE + 2 : ICON_SIZE;
        int iconX = centerX - size / 2;
        int iconY = centerY - size / 2;
        float scale = size / (float) ICON_TEXTURE_SIZE;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX, iconY, 300);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.blit(iconFor(marker.transportMode()), 0, 0, 0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        guiGraphics.pose().popPose();
    }

    private void drawLabel(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        int width = font.width(text);
        int x = centerX - width / 2;
        guiGraphics.fill(x - 2, y - 1, x + width + 2, y + font.lineHeight - 1, LABEL_BACKGROUND);
        guiGraphics.drawString(font, text, x, y, color, false);
    }

    private void drawLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color, int thickness) {
        int argb = (color & 0xFF000000) == 0 ? ROUTE_LINE_ALPHA | (color & 0x00FFFFFF) : color;
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            guiGraphics.fill(x - thickness / 2, y - thickness / 2, x + thickness / 2 + 1, y + thickness / 2 + 1, argb);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private void drawDirectionArrows(GuiGraphics guiGraphics, List<Point2> points) {
        if (points.size() < 2) {
            return;
        }
        double totalLength = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            totalLength += segmentLength(points.get(i - 1), points.get(i));
        }
        if (totalLength < ROUTE_ARROW_MIN_ROUTE_PIXELS) {
            return;
        }

        double animatedOffset = (Util.getMillis() / 1000.0D) * ROUTE_ARROW_SPEED_PIXELS_PER_SECOND;
        double effectiveSpacing = Math.min(ROUTE_ARROW_SPACING_PIXELS, Math.max(totalLength, ROUTE_ARROW_SIZE * 3.0D));
        double firstDistance = animatedOffset % effectiveSpacing;
        for (double distanceAlong = firstDistance;
             distanceAlong <= totalLength;
             distanceAlong += effectiveSpacing) {
            ArrowPlacement placement = arrowPlacement(points, distanceAlong);
            if (placement == null) {
                continue;
            }
            double baseX = placement.tip().x() - placement.unitX() * ROUTE_ARROW_SIZE;
            double baseY = placement.tip().y() - placement.unitY() * ROUTE_ARROW_SIZE;
            double normalX = -placement.unitY();
            double normalY = placement.unitX();
            int leftX = (int) Math.round(baseX + normalX * ROUTE_ARROW_SIZE * 0.75D);
            int leftY = (int) Math.round(baseY + normalY * ROUTE_ARROW_SIZE * 0.75D);
            int rightX = (int) Math.round(baseX - normalX * ROUTE_ARROW_SIZE * 0.75D);
            int rightY = (int) Math.round(baseY - normalY * ROUTE_ARROW_SIZE * 0.75D);
            int tipX = placement.tip().x();
            int tipY = placement.tip().y();
            drawLine(guiGraphics, leftX, leftY, tipX, tipY, ROUTE_ARROW_SHADOW, 2);
            drawLine(guiGraphics, rightX, rightY, tipX, tipY, ROUTE_ARROW_SHADOW, 2);
            drawLine(guiGraphics, leftX, leftY, tipX, tipY, ROUTE_ARROW_COLOR, 1);
            drawLine(guiGraphics, rightX, rightY, tipX, tipY, ROUTE_ARROW_COLOR, 1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE7D6B4, false);
        String subtitle = Component.translatable("gui.create_aeronautics_automated_logistics.logistics_terminal.zoom", String.format(Locale.ROOT, "%.0f%%", zoom * 100.0D)).getString();
        guiGraphics.drawString(font, subtitle, imageWidth - BORDER_RIGHT - CONTENT_PADDING - font.width(subtitle), titleLabelY, 0xFF9F8F78, false);
        String hint = "Right-click to track";
        guiGraphics.drawString(
                font,
                hint,
                imageWidth - BORDER_RIGHT - CONTENT_PADDING - font.width(hint),
                imageHeight - BORDER_BOTTOM - FOOTER_HEIGHT,
                0xFF9F8F78,
                false
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isPointInsideMap(mouseX, mouseY)) {
            zoom = Mth.clamp(zoom + scrollY * 0.1D, 0.5D, 2.5D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isPointInsideMap(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingMap = true;
            lastDragX = (int) mouseX;
            lastDragY = (int) mouseY;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hoveredEntry != null) {
                switch (hoveredEntry.kind()) {
                    case MARKER -> {
                        UUID id = hoveredEntry.transponderId().orElseThrow();
                        boolean clear = pinnedTransponderId.filter(id::equals).isPresent();
                        pinnedTransponderId = clear ? Optional.empty() : Optional.of(id);
                        pinnedStationId = Optional.empty();
                    }
                    case STATION -> {
                        return true;
                    }
                }
                panWorldX = 0.0D;
                panWorldZ = 0.0D;
            } else if (hoveredTooltip.isEmpty()) {
                pinnedStationId = Optional.empty();
                pinnedTransponderId = Optional.empty();
                panWorldX = 0.0D;
                panWorldZ = 0.0D;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingMap && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ResourceKey<Level> dimension = currentDimension();
            WorldBounds bounds = WorldBounds.from(visibleRoutes(dimension), visibleStations(dimension), visibleMarkers(dimension));
            if (!bounds.empty()) {
                ViewState view = viewState(bounds, mapRect(), visibleStations(dimension), visibleMarkers(dimension));
                if (view.scale() > 0.0D) {
                    panWorldX -= (mouseX - lastDragX) / view.scale();
                    panWorldZ -= (mouseY - lastDragY) / view.scale();
                    pinnedStationId = Optional.empty();
                    pinnedTransponderId = Optional.empty();
                }
            }
            lastDragX = (int) mouseX;
            lastDragY = (int) mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingMap = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private List<RouteSegment> visibleRoutes(ResourceKey<Level> dimension) {
        return LogisticsTerminalClientState.routes().stream()
                .filter(route -> route.dimension().equals(dimension))
                .sorted(Comparator.comparingLong(RouteSegment::createdEpochMillis))
                .toList();
    }

    private List<AirshipStationSnapshot> visibleStations(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of();
        }
        UUID playerId = minecraft.player.getUUID();
        return AirshipStationRegistry.knownStations(dimension).stream()
                .filter(station -> StationPermissionService.canControl(playerId, false, station))
                .toList();
    }

    private List<ShipMapMarker> visibleMarkers(ResourceKey<Level> dimension) {
        return ShipMapClientState.markers().stream()
                .filter(marker -> marker.dimension().equals(dimension))
                .toList();
    }

    private ViewState viewState(WorldBounds bounds, MapRect rect, List<AirshipStationSnapshot> stations, List<ShipMapMarker> markers) {
        double targetX = bounds.centerX();
        double targetZ = bounds.centerZ();
        if (pinnedTransponderId.isPresent()) {
            UUID transponderId = pinnedTransponderId.get();
            Optional<ShipMapMarker> marker = markers.stream().filter(entry -> entry.transponderId().equals(transponderId)).findFirst();
            if (marker.isPresent()) {
                targetX = marker.get().position().x;
                targetZ = marker.get().position().z;
            }
        } else if (pinnedStationId.isPresent()) {
            UUID stationId = pinnedStationId.get();
            Optional<AirshipStationSnapshot> station = stations.stream().filter(entry -> entry.stationId().equals(stationId)).findFirst();
            if (station.isPresent()) {
                targetX = station.get().stationPos().getX() + 0.5D;
                targetZ = station.get().stationPos().getZ() + 0.5D;
            }
        }
        double width = Math.max(16.0D, bounds.maxX() - bounds.minX());
        double height = Math.max(16.0D, bounds.maxZ() - bounds.minZ());
        double fitScale = Math.min((rect.width() - 16.0D) / width, (rect.height() - 16.0D) / height);
        fitScale = Math.max(0.15D, fitScale);
        return new ViewState(targetX + panWorldX, targetZ + panWorldZ, fitScale * zoom);
    }

    private Point2 project(Vec3 position, ViewState view, MapRect rect) {
        int x = rect.centerX() + (int) Math.round((position.x - view.centerX()) * view.scale());
        int y = rect.centerY() + (int) Math.round((position.z - view.centerZ()) * view.scale());
        return new Point2(x, y);
    }

    private MapRect mapRect() {
        int x = leftPos + BORDER_LEFT + CONTENT_PADDING;
        int y = topPos + BORDER_TOP + HEADER_HEIGHT;
        int width = imageWidth - BORDER_LEFT - BORDER_RIGHT - CONTENT_PADDING * 2;
        int height = imageHeight - BORDER_TOP - BORDER_BOTTOM - HEADER_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING;
        return new MapRect(x, y, width, height);
    }

    private ResourceKey<Level> currentDimension() {
        return Minecraft.getInstance().level == null ? Level.OVERWORLD : Minecraft.getInstance().level.dimension();
    }

    private boolean isPointInsideMap(double mouseX, double mouseY) {
        MapRect rect = mapRect();
        return mouseX >= rect.x() && mouseX < rect.right() && mouseY >= rect.y() && mouseY < rect.bottom();
    }

    private Map<UUID, Integer> routeColors(List<RouteSegment> routes) {
        Map<UUID, Long> firstSeen = new HashMap<>();
        for (RouteSegment route : routes) {
            firstSeen.merge(route.transponderId(), route.createdEpochMillis(), Math::min);
        }

        List<UUID> vehicleIds = firstSeen.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue()
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getKey)
                .toList();

        Map<UUID, Integer> assigned = new HashMap<>();
        Set<Integer> usedColors = new HashSet<>();
        for (UUID vehicleId : vehicleIds) {
            int preferredIndex = Math.floorMod(vehicleId.hashCode(), ROUTE_COLORS.length);
            int color = ROUTE_COLORS[preferredIndex];
            if (usedColors.contains(color)) {
                color = firstUnusedColor(preferredIndex, usedColors);
            }
            assigned.put(vehicleId, color);
            usedColors.add(color);
        }
        return assigned;
    }

    private int firstUnusedColor(int preferredIndex, Set<Integer> usedColors) {
        for (int offset = 0; offset < ROUTE_COLORS.length; offset++) {
            int color = ROUTE_COLORS[(preferredIndex + offset) % ROUTE_COLORS.length];
            if (!usedColors.contains(color)) {
                return color;
            }
        }
        return ROUTE_COLORS[preferredIndex];
    }

    private int fallbackRouteColor(UUID vehicleId) {
        return ROUTE_COLORS[Math.floorMod(vehicleId.hashCode(), ROUTE_COLORS.length)];
    }

    private List<Component> routeTooltip(RouteSegment route) {
        return List.of(
                Component.literal(route.shipName()),
                Component.literal(route.startStationName() + " -> " + route.endStationName()),
                Component.literal(route.points().size() + " pts")
        );
    }

    private List<Component> stationTooltip(AirshipStationSnapshot station) {
        return List.of(
                Component.literal(station.stationName()),
                Component.literal(station.transportMode() == TransportMode.TRAIN ? "Train station" : "Airship station"),
                Component.literal(station.stationPos().getX() + ", " + station.stationPos().getY() + ", " + station.stationPos().getZ())
        );
    }

    private List<Component> markerTooltip(ShipMapMarker marker) {
        return List.of(
                Component.literal(marker.shipName()),
                Component.literal(marker.transportMode() == TransportMode.TRAIN ? "Train" : "Airship"),
                Component.literal("State: " + marker.displayState()),
                Component.literal(String.format(Locale.ROOT, "X %.1f  Z %.1f", marker.position().x, marker.position().z))
        );
    }

    private boolean importantState(String state) {
        String normalized = state == null ? "" : state.toUpperCase(Locale.ROOT);
        return normalized.contains("FAULT") || normalized.contains("PAUSED");
    }

    private ResourceLocation iconFor(TransportMode transportMode) {
        return transportMode == TransportMode.TRAIN ? TRAIN_ICON : AIRSHIP_ICON;
    }

    private static double distance(int mouseX, int mouseY, int x, int y) {
        return Math.hypot(mouseX - x, mouseY - y);
    }

    private static double distanceToSegment(int mouseX, int mouseY, Point2 from, Point2 to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        if (dx == 0.0D && dy == 0.0D) {
            return distance(mouseX, mouseY, from.x(), from.y());
        }
        double t = ((mouseX - from.x()) * dx + (mouseY - from.y()) * dy) / (dx * dx + dy * dy);
        t = Mth.clamp(t, 0.0D, 1.0D);
        double projectedX = from.x() + t * dx;
        double projectedY = from.y() + t * dy;
        return Math.hypot(mouseX - projectedX, mouseY - projectedY);
    }

    private static double segmentLength(Point2 from, Point2 to) {
        return Math.hypot(to.x() - from.x(), to.y() - from.y());
    }

    private static ArrowPlacement arrowPlacement(List<Point2> points, double distanceAlong) {
        double traversed = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            Point2 from = points.get(i - 1);
            Point2 to = points.get(i);
            double length = segmentLength(from, to);
            if (length <= 0.0D) {
                continue;
            }
            if (traversed + length >= distanceAlong) {
                double local = distanceAlong - traversed;
                double unitX = (to.x() - from.x()) / length;
                double unitY = (to.y() - from.y()) / length;
                int tipX = (int) Math.round(from.x() + unitX * local);
                int tipY = (int) Math.round(from.y() + unitY * local);
                return new ArrowPlacement(new Point2(tipX, tipY), unitX, unitY);
            }
            traversed += length;
        }
        return null;
    }

    private record ViewState(double centerX, double centerZ, double scale) {
    }

    private record Point2(int x, int y) {
    }

    private record ArrowPlacement(Point2 tip, double unitX, double unitY) {
    }

    private record MapRect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }
    }

    private record HoveredEntry(HoverKind kind, UUID stationId, Optional<UUID> transponderId) {
    }

    private enum HoverKind {
        STATION,
        MARKER
    }

    private record WorldBounds(double minX, double maxX, double minZ, double maxZ, boolean empty) {
        static WorldBounds from(List<RouteSegment> routes, List<AirshipStationSnapshot> stations, List<ShipMapMarker> markers) {
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            boolean found = false;

            for (RouteSegment route : routes) {
                for (RoutePoint point : route.points()) {
                    minX = Math.min(minX, point.position().x);
                    maxX = Math.max(maxX, point.position().x);
                    minZ = Math.min(minZ, point.position().z);
                    maxZ = Math.max(maxZ, point.position().z);
                    found = true;
                }
            }
            for (AirshipStationSnapshot station : stations) {
                minX = Math.min(minX, station.stationPos().getX() + 0.5D);
                maxX = Math.max(maxX, station.stationPos().getX() + 0.5D);
                minZ = Math.min(minZ, station.stationPos().getZ() + 0.5D);
                maxZ = Math.max(maxZ, station.stationPos().getZ() + 0.5D);
                found = true;
            }
            for (ShipMapMarker marker : markers) {
                minX = Math.min(minX, marker.position().x);
                maxX = Math.max(maxX, marker.position().x);
                minZ = Math.min(minZ, marker.position().z);
                maxZ = Math.max(maxZ, marker.position().z);
                found = true;
            }
            if (!found) {
                return new WorldBounds(0.0D, 0.0D, 0.0D, 0.0D, true);
            }
            return new WorldBounds(minX - 6.0D, maxX + 6.0D, minZ - 6.0D, maxZ + 6.0D, false);
        }

        double centerX() {
            return (minX + maxX) * 0.5D;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }
    }
}

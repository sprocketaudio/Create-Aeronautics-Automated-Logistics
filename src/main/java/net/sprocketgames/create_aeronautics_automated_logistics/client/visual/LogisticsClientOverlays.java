package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllSpecialTextures;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;

public final class LogisticsClientOverlays {
    private static final int LANDING_COLOR = 0x95E06C;
    private static final int[][][] LEG_COLORS = new int[][][]{
            {
                    {0x4FC3FF, 0x2C8DDE},
                    {0x63E6BE, 0x31BE93},
                    {0xFFD166, 0xE3A93A}
            },
            {
                    {0xFF7F6A, 0xDC5844},
                    {0xB197FC, 0x8A73D8},
                    {0x4DD4C6, 0x27AA9D}
            },
            {
                    {0xFFB347, 0xD98A24},
                    {0x5CD6FF, 0x2EAEDB},
                    {0xFF8FA3, 0xDB667D}
            },
            {
                    {0x9BE564, 0x73BE3F},
                    {0x4DABF7, 0x2A84CF},
                    {0xE879F9, 0xC154D2}
            },
            {
                    {0xFF93C9, 0xDE67A2},
                    {0x66E3D3, 0x38BAAA},
                    {0xF7E96C, 0xD2C548}
            },
            {
                    {0xC98C3A, 0x9F681A},
                    {0x8CA8FF, 0x667FDC},
                    {0x7EDC8A, 0x55B864}
            }
    };
    private static final int[][][] LEG_PULSE_COLORS = new int[][][]{
            {
                    {0xDDF5FF, 0xB6E6FF},
                    {0xD8FFF1, 0xB0F7DE},
                    {0xFFF1C7, 0xFFE49A}
            },
            {
                    {0xFFD9D1, 0xFFC0B5},
                    {0xE8DDFF, 0xD7C8FF},
                    {0xD8FBF8, 0xB7F1EA}
            },
            {
                    {0xFFE3B8, 0xFFD19A},
                    {0xD3F4FF, 0xB2E9FF},
                    {0xFFD7DE, 0xFFC0CC}
            },
            {
                    {0xDEF6BF, 0xCAF0A2},
                    {0xCFE9FF, 0xAFD9FF},
                    {0xF8D9FF, 0xEEBEFF}
            },
            {
                    {0xFFD9EA, 0xFFC1DA},
                    {0xD4FAF4, 0xB4F0E7},
                    {0xFFF9C8, 0xFFF1A2}
            },
            {
                    {0xF2DEBD, 0xE3C28D},
                    {0xDDE3FF, 0xC6CEFF},
                    {0xDDF8E0, 0xC2EDC8}
            }
    };
    private static final int START_COLOR = 0x95E06C;
    private static final int END_COLOR = 0xFFD36C;
    private static final int STATION_CHUNK_COLOR = 0xD89B55;
    private static final int DOCK_COLOR = 0x7AD7FF;
    private static final int CARGO_COLOR = 0x9BEA8B;
    private static final int TRANSPONDER_COLOR = 0xBFEFFF;
    private static final int[] LINK_CANDIDATE_COLORS = new int[]{0xFFE37A, 0xFFC84D};
    private static final int LANDING_SEGMENTS = 48;
    private static final int LATITUDE_RINGS = 5;
    private static final int ROUTE_ARROW_SPACING = 12;

    private static Optional<LandingAreaOverlay> landingAreaOverlay = Optional.empty();
    private static Optional<StationChunkOverlay> stationChunkOverlay = Optional.empty();
    private static Optional<BlockPos> dockOverlay = Optional.empty();
    private static Optional<BlockPos> shipTransponderOverlay = Optional.empty();
    private static List<BlockPos> dockLinkCandidates = List.of();
    private static List<BlockPos> cargoOverlay = List.of();
    private static List<List<BlockPos>> cargoOverlayGroups = List.of();
    private static List<List<BlockPos>> cargoLinkCandidates = List.of();
    private static List<Vec3> flightPath = List.of();
    private static List<Integer> flightPathLegEnds = List.of();
    private static List<UUID> flightPathLegPaletteKeys = List.of();
    private static List<Integer> flightPathLegColorSlots = List.of();
    private static List<Integer> flightPathBreakStarts = List.of();
    private static Set<UUID> previewedRouteIds = Set.of();
    private static Optional<BlockPos> previewedTransponderPos = Optional.empty();
    private static Optional<StationRoutePreview> previewedStationRoute = Optional.empty();

    private LogisticsClientOverlays() {
    }

    public static void toggleLandingArea(BlockPos stationPos, double radius) {
        if (landingAreaOverlay.isPresent() && landingAreaOverlay.get().stationPos().equals(stationPos)) {
            clearLandingArea();
            return;
        }
        clearLandingArea();
        landingAreaOverlay = Optional.of(new LandingAreaOverlay(stationPos.immutable(), radius));
    }

    public static void clearLandingArea() {
        landingAreaOverlay.ifPresent(LogisticsClientOverlays::removeLandingArea);
        landingAreaOverlay = Optional.empty();
    }

    public static boolean isLandingAreaVisible(BlockPos stationPos) {
        return landingAreaOverlay.isPresent() && landingAreaOverlay.get().stationPos().equals(stationPos);
    }

    public static void toggleStationChunks(BlockPos stationPos, int radiusChunks) {
        StationChunkOverlay next = new StationChunkOverlay(stationPos.immutable(), Math.max(0, radiusChunks));
        if (stationChunkOverlay.filter(next::equals).isPresent()) {
            clearStationChunks();
            return;
        }
        clearStationChunks();
        stationChunkOverlay = Optional.of(next);
    }

    public static void clearStationChunks() {
        stationChunkOverlay.ifPresent(LogisticsClientOverlays::removeStationChunks);
        stationChunkOverlay = Optional.empty();
    }

    public static boolean isStationChunksVisible(BlockPos stationPos, int radiusChunks) {
        return stationChunkOverlay
                .filter(overlay -> overlay.stationPos().equals(stationPos) && overlay.radiusChunks() == Math.max(0, radiusChunks))
                .isPresent();
    }

    public static void toggleDock(BlockPos dockPos) {
        if (dockOverlay.isPresent() && dockOverlay.get().equals(dockPos)) {
            clearDock();
            return;
        }
        clearDock();
        dockOverlay = Optional.of(dockPos.immutable());
    }

    public static void clearDock() {
        dockOverlay.ifPresent(LogisticsClientOverlays::removeDock);
        dockOverlay = Optional.empty();
    }

    public static void clearDockIfMatches(Optional<BlockPos> dockPos) {
        if (dockPos.isPresent() && dockOverlay.filter(dockPos.get()::equals).isPresent()) {
            clearDock();
        }
    }

    public static void setShipTransponderHighlight(BlockPos transponderPos) {
        Optional<BlockPos> normalized = Optional.of(transponderPos.immutable());
        if (shipTransponderOverlay.equals(normalized)) {
            return;
        }
        clearShipTransponderHighlight();
        shipTransponderOverlay = normalized;
    }

    public static void clearShipTransponderHighlight() {
        shipTransponderOverlay.ifPresent(LogisticsClientOverlays::removeShipTransponderHighlight);
        shipTransponderOverlay = Optional.empty();
    }

    public static void clearShipTransponderHighlightIfMatches(BlockPos transponderPos) {
        if (shipTransponderOverlay.filter(transponderPos::equals).isPresent()) {
            clearShipTransponderHighlight();
        }
    }

    public static void setDockLinkCandidates(List<BlockPos> candidatePositions) {
        List<BlockPos> normalized = normalizeCargoPositions(candidatePositions);
        if (dockLinkCandidates.equals(normalized)) {
            return;
        }
        clearDockLinkCandidates();
        dockLinkCandidates = normalized;
    }

    public static void clearDockLinkCandidates() {
        removeDockLinkCandidates(dockLinkCandidates);
        dockLinkCandidates = List.of();
    }

    public static boolean isDockVisible(BlockPos dockPos) {
        return dockOverlay.isPresent() && dockOverlay.get().equals(dockPos);
    }

    public static void toggleCargo(List<BlockPos> cargoPositions) {
        toggleCargoGroups(List.of(cargoPositions));
    }

    public static void toggleCargoGroups(List<List<BlockPos>> cargoPositionGroups) {
        List<List<BlockPos>> normalized = normalizeCargoGroups(cargoPositionGroups);
        List<BlockPos> flat = normalized.stream().flatMap(List::stream).toList();
        if (cargoOverlayGroups.equals(normalized)) {
            clearCargo();
            return;
        }
        clearCargo();
        cargoOverlayGroups = normalized;
        cargoOverlay = flat;
    }

    private static List<BlockPos> normalizeCargoPositions(List<BlockPos> cargoPositions) {
        List<BlockPos> normalized = cargoPositions.stream().map(BlockPos::immutable).distinct().sorted(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getZ())
                .thenComparingInt(pos -> pos.getX())).toList();
        return normalized;
    }

    private static List<List<BlockPos>> normalizeCargoGroups(List<List<BlockPos>> cargoPositionGroups) {
        return cargoPositionGroups.stream()
                .map(LogisticsClientOverlays::normalizeCargoPositions)
                .filter(group -> !group.isEmpty())
                .sorted(Comparator
                        .comparingInt((List<BlockPos> group) -> group.getFirst().getY())
                        .thenComparingInt(group -> group.getFirst().getZ())
                        .thenComparingInt(group -> group.getFirst().getX()))
                .toList();
    }

    public static void clearCargo() {
        removeCargo(cargoOverlayGroups);
        cargoOverlay = List.of();
        cargoOverlayGroups = List.of();
    }

    public static void clearCargoIfMatches(List<List<BlockPos>> cargoPositionGroups) {
        List<List<BlockPos>> normalized = normalizeCargoGroups(cargoPositionGroups);
        if (!normalized.isEmpty() && cargoOverlayGroups.equals(normalized)) {
            clearCargo();
        }
    }

    public static void setCargoLinkCandidates(List<List<BlockPos>> candidateGroups) {
        List<List<BlockPos>> normalized = normalizeCargoGroups(candidateGroups);
        if (cargoLinkCandidates.equals(normalized)) {
            return;
        }
        clearCargoLinkCandidates();
        cargoLinkCandidates = normalized;
    }

    public static void clearCargoLinkCandidates() {
        removeCargoCandidates(cargoLinkCandidates);
        cargoLinkCandidates = List.of();
    }

    public static boolean isCargoVisible(List<BlockPos> cargoPositions) {
        return isCargoVisibleGroups(List.of(cargoPositions));
    }

    public static boolean isCargoVisibleGroups(List<List<BlockPos>> cargoPositionGroups) {
        List<List<BlockPos>> normalized = normalizeCargoGroups(cargoPositionGroups);
        return cargoOverlayGroups.equals(normalized) && !cargoOverlayGroups.isEmpty();
    }

    public static void setFlightPath(
            List<Vec3> points,
            List<Integer> legEndIndices,
            List<UUID> legPaletteKeys,
            List<Integer> legColorSlots,
            List<Integer> breakStartIndices,
            List<UUID> routeIds,
            BlockPos transponderPos,
            UUID stationId,
            UUID stationFilterTransponderId
    ) {
        removeFlightPath(flightPath, flightPathLegEnds);
        flightPath = List.copyOf(points);
        flightPathLegEnds = List.copyOf(legEndIndices);
        flightPathLegPaletteKeys = List.copyOf(legPaletteKeys);
        flightPathLegColorSlots = List.copyOf(legColorSlots);
        flightPathBreakStarts = List.copyOf(breakStartIndices);
        previewedRouteIds = Set.copyOf(routeIds);
        previewedTransponderPos = Optional.ofNullable(transponderPos).map(BlockPos::immutable);
        previewedStationRoute = Optional.ofNullable(stationId)
                .map(id -> new StationRoutePreview(id, Optional.ofNullable(stationFilterTransponderId)));
    }

    public static void setPreviewedRouteIds(Collection<UUID> routeIds) {
        previewedRouteIds = routeIds == null ? Set.of() : Set.copyOf(routeIds);
        if (previewedRouteIds.isEmpty()) {
            previewedStationRoute = Optional.empty();
        }
    }

    public static void setPreviewedRouteId(UUID routeId) {
        setPreviewedRouteIds(routeId == null ? List.of() : List.of(routeId));
    }

    public static Optional<UUID> previewedRouteId() {
        if (previewedRouteIds.size() != 1) {
            return Optional.empty();
        }
        return previewedRouteIds.stream().findFirst();
    }

    public static Set<UUID> previewedRouteIds() {
        return Set.copyOf(previewedRouteIds);
    }

    public static Optional<BlockPos> previewedTransponderPos() {
        return previewedTransponderPos;
    }

    public static Optional<StationRoutePreview> previewedStationRoute() {
        return previewedStationRoute;
    }

    public static void clearFlightPath() {
        removeFlightPath(flightPath, flightPathLegEnds);
        flightPath = List.of();
        flightPathLegEnds = List.of();
        flightPathLegPaletteKeys = List.of();
        flightPathLegColorSlots = List.of();
        flightPathBreakStarts = List.of();
        previewedRouteIds = Set.of();
        previewedTransponderPos = Optional.empty();
        previewedStationRoute = Optional.empty();
    }

    public static void clearFlightPathIfPreviewingTransponder(BlockPos transponderPos) {
        if (previewedTransponderPos.filter(transponderPos::equals).isPresent()) {
            clearFlightPath();
        }
    }

    public static void clearFlightPathIfPreviewingRoutes(Collection<RouteSegment> routes) {
        if (previewedRouteIds.isEmpty()) {
            return;
        }
        boolean matches = routes.stream().map(route -> route.id().value()).anyMatch(previewedRouteIds::contains);
        if (matches) {
            clearFlightPath();
        }
    }

    public static void clearFlightPathIfPreviewingTransponderRoutes(UUID transponderId) {
        if (previewedStationRoute.flatMap(StationRoutePreview::filterTransponderId).filter(transponderId::equals).isPresent()) {
            clearFlightPath();
            return;
        }
        if (previewedRouteIds.isEmpty()) {
            return;
        }
        boolean matches = RouteSegmentRegistry.forTransponder(transponderId).stream()
                .map(route -> route.id().value())
                .anyMatch(previewedRouteIds::contains);
        if (matches) {
            clearFlightPath();
        }
    }

    public static void clearFlightPathIfPreviewingStationRoutes(UUID stationId) {
        if (previewedStationRoute.filter(preview -> preview.stationId().equals(stationId)).isPresent()) {
            clearFlightPath();
            return;
        }
        if (previewedRouteIds.isEmpty()) {
            return;
        }
        boolean matches = RouteSegmentRegistry.connectedToStation(stationId).stream()
                .map(route -> route.id().value())
                .anyMatch(previewedRouteIds::contains);
        if (matches) {
            clearFlightPath();
        }
    }

    public static void clearLandingAreaIfMatches(BlockPos stationPos) {
        if (landingAreaOverlay.filter(overlay -> overlay.stationPos().equals(stationPos)).isPresent()) {
            clearLandingArea();
        }
    }

    public static void clearStationChunksIfMatches(BlockPos stationPos) {
        if (stationChunkOverlay.filter(overlay -> overlay.stationPos().equals(stationPos)).isPresent()) {
            clearStationChunks();
        }
    }

    public static boolean hasFlightPath() {
        return flightPath.size() >= 2;
    }

    public static void refresh() {
        landingAreaOverlay.ifPresent(LogisticsClientOverlays::showLandingArea);
        stationChunkOverlay.ifPresent(LogisticsClientOverlays::showStationChunks);
        if (!dockLinkCandidates.isEmpty()) {
            showDockLinkCandidates(dockLinkCandidates);
        }
        dockOverlay.ifPresent(LogisticsClientOverlays::showDock);
        shipTransponderOverlay.ifPresent(LogisticsClientOverlays::showShipTransponderHighlight);
        if (!cargoLinkCandidates.isEmpty()) {
            showCargoCandidates(cargoLinkCandidates);
        }
        if (!cargoOverlay.isEmpty()) {
            showCargo(cargoOverlayGroups);
        }
        if (flightPath.size() >= 2) {
            showFlightPath(flightPath, flightPathLegEnds, flightPathLegPaletteKeys, flightPathLegColorSlots, flightPathBreakStarts);
        }
    }

    private static void showLandingArea(LandingAreaOverlay overlay) {
        Vec3 center = Vec3.atCenterOf(overlay.stationPos());
        double radius = overlay.radius();

        for (int axis = 0; axis < 3; axis++) {
            Vec3 previous = pointOnCircle(center, radius, 0, axis);
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                double theta = Math.PI * 2.0D * i / LANDING_SEGMENTS;
                Vec3 current = pointOnCircle(center, radius, theta, axis);
                Outliner.getInstance()
                        .showLine(Pair.of(Pair.of("landing_area_circle", overlay.stationPos()), axis + ":" + i), previous, current)
                        .colored(LANDING_COLOR)
                        .lineWidth(1 / 32f)
                        .disableLineNormals();
                previous = current;
            }
        }

        for (int ring = 1; ring <= LATITUDE_RINGS; ring++) {
            double normalized = -1.0D + (2.0D * ring) / (LATITUDE_RINGS + 1.0D);
            double yOffset = normalized * radius;
            double ringRadius = Math.sqrt(Math.max(0.0D, radius * radius - yOffset * yOffset));
            Vec3 ringCenter = center.add(0, yOffset, 0);
            Vec3 previous = pointOnCircle(ringCenter, ringRadius, 0, 0);
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                double theta = Math.PI * 2.0D * i / LANDING_SEGMENTS;
                Vec3 current = pointOnCircle(ringCenter, ringRadius, theta, 0);
                Outliner.getInstance()
                        .showLine(Pair.of(Pair.of("landing_area_latitude", overlay.stationPos()), ring + ":" + i), previous, current)
                        .colored(LANDING_COLOR)
                        .lineWidth(1 / 32f)
                        .disableLineNormals();
                previous = current;
            }
        }
    }

    private static void removeLandingArea(LandingAreaOverlay overlay) {
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                Outliner.getInstance().remove(Pair.of(Pair.of("landing_area_circle", overlay.stationPos()), axis + ":" + i));
            }
        }
        for (int ring = 1; ring <= LATITUDE_RINGS; ring++) {
            for (int i = 1; i <= LANDING_SEGMENTS; i++) {
                Outliner.getInstance().remove(Pair.of(Pair.of("landing_area_latitude", overlay.stationPos()), ring + ":" + i));
            }
        }
    }

    private static void showStationChunks(StationChunkOverlay overlay) {
        int centerChunkX = SectionPos.blockToSectionCoord(overlay.stationPos().getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(overlay.stationPos().getZ());
        int radius = overlay.radiusChunks();
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        double minX = minChunkX << 4;
        double maxX = (maxChunkX << 4) + 16;
        double minZ = minChunkZ << 4;
        double maxZ = (maxChunkZ << 4) + 16;
        double y = overlay.stationPos().getY() + 0.08D;

        int line = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX + 1; chunkX++) {
            double x = chunkX << 4;
            showStationChunkLine(overlay, line++, new Vec3(x, y, minZ), new Vec3(x, y, maxZ));
        }
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            double z = chunkZ << 4;
            showStationChunkLine(overlay, line++, new Vec3(minX, y, z), new Vec3(maxX, y, z));
        }
    }

    private static void showStationChunkLine(StationChunkOverlay overlay, int index, Vec3 start, Vec3 end) {
        Outliner.getInstance()
                .showLine(Pair.of(Pair.of("station_chunk_grid", overlay.stationPos()), index), start, end)
                .colored(STATION_CHUNK_COLOR)
                .lineWidth(1 / 20f)
                .disableLineNormals();
    }

    private static void removeStationChunks(StationChunkOverlay overlay) {
        int radius = overlay.radiusChunks();
        int lineCount = (2 * radius + 2) * 2;
        for (int i = 0; i < lineCount; i++) {
            Outliner.getInstance().remove(Pair.of(Pair.of("station_chunk_grid", overlay.stationPos()), i));
        }
    }

    private static void showDock(BlockPos dockPos) {
        Outliner.getInstance()
                .showAABB(Pair.of("dock_overlay", dockPos), AABB.ofSize(Vec3.atCenterOf(dockPos), 1.05D, 1.05D, 1.05D))
                .colored(DOCK_COLOR)
                .lineWidth(1 / 12f)
                .disableLineNormals()
                .withFaceTexture(AllSpecialTextures.SELECTION);
    }

    private static void removeDock(BlockPos dockPos) {
        Outliner.getInstance().remove(Pair.of("dock_overlay", dockPos));
    }

    private static void showShipTransponderHighlight(BlockPos transponderPos) {
        Outliner.getInstance()
                .showAABB(Pair.of("ship_transponder_overlay", transponderPos), AABB.ofSize(Vec3.atCenterOf(transponderPos), 1.08D, 1.08D, 1.08D))
                .colored(TRANSPONDER_COLOR)
                .lineWidth(1 / 10f)
                .disableLineNormals()
                .withFaceTexture(AllSpecialTextures.SELECTION);
    }

    private static void removeShipTransponderHighlight(BlockPos transponderPos) {
        Outliner.getInstance().remove(Pair.of("ship_transponder_overlay", transponderPos));
    }

    private static void showDockLinkCandidates(List<BlockPos> dockPositions) {
        int color = pulsingLinkCandidateColor();
        float lineWidth = pulsingLinkCandidateLineWidth();
        for (BlockPos dockPos : dockPositions) {
            Outliner.getInstance()
                    .showAABB(Pair.of("dock_link_candidate", dockPos), AABB.ofSize(Vec3.atCenterOf(dockPos), 1.12D, 1.12D, 1.12D))
                    .colored(color)
                    .lineWidth(lineWidth)
                    .disableLineNormals()
                    .withFaceTexture(AllSpecialTextures.SELECTION);
        }
    }

    private static void removeDockLinkCandidates(List<BlockPos> dockPositions) {
        for (BlockPos dockPos : dockPositions) {
            Outliner.getInstance().remove(Pair.of("dock_link_candidate", dockPos));
        }
    }

    private static void showCargo(List<List<BlockPos>> cargoPositionGroups) {
        for (List<BlockPos> group : cargoPositionGroups) {
            List<CargoCluster> clusters = clusterCargo(group);
            for (CargoCluster cluster : clusters) {
                Outliner.getInstance()
                        .showAABB(Pair.of("cargo_overlay", cluster.key()), cluster.bounds())
                        .colored(CARGO_COLOR)
                        .lineWidth(1 / 18f)
                        .disableLineNormals()
                        .withFaceTexture(AllSpecialTextures.SELECTION);
            }
        }
    }

    private static void removeCargo(List<List<BlockPos>> cargoPositionGroups) {
        for (List<BlockPos> group : cargoPositionGroups) {
            for (CargoCluster cluster : clusterCargo(group)) {
                Outliner.getInstance().remove(Pair.of("cargo_overlay", cluster.key()));
            }
        }
    }

    private static void showCargoCandidates(List<List<BlockPos>> cargoPositionGroups) {
        int color = pulsingLinkCandidateColor();
        float lineWidth = pulsingLinkCandidateLineWidth();
        for (List<BlockPos> group : cargoPositionGroups) {
            if (group.size() == 1) {
                BlockPos pos = group.getFirst();
                Outliner.getInstance()
                        .showAABB(Pair.of("cargo_link_candidate", pos), AABB.ofSize(Vec3.atCenterOf(pos), 1.05D, 1.05D, 1.05D))
                        .colored(color)
                        .lineWidth(lineWidth)
                        .disableLineNormals()
                        .withFaceTexture(AllSpecialTextures.SELECTION);
                continue;
            }
            for (CargoCluster cluster : clusterCargo(group)) {
                Outliner.getInstance()
                        .showAABB(Pair.of("cargo_link_candidate", cluster.key()), cluster.bounds())
                        .colored(color)
                        .lineWidth(lineWidth)
                        .disableLineNormals()
                        .withFaceTexture(AllSpecialTextures.SELECTION);
            }
        }
    }

    private static void removeCargoCandidates(List<List<BlockPos>> cargoPositionGroups) {
        for (List<BlockPos> group : cargoPositionGroups) {
            if (group.size() == 1) {
                Outliner.getInstance().remove(Pair.of("cargo_link_candidate", group.getFirst()));
                continue;
            }
            for (CargoCluster cluster : clusterCargo(group)) {
                Outliner.getInstance().remove(Pair.of("cargo_link_candidate", cluster.key()));
            }
        }
    }

    private static List<CargoCluster> clusterCargo(List<BlockPos> cargoPositions) {
        Set<BlockPos> remaining = new HashSet<>(cargoPositions.stream().map(BlockPos::immutable).toList());
        List<CargoCluster> clusters = new ArrayList<>();
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            remaining.remove(seed);
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            int minX = seed.getX();
            int minY = seed.getY();
            int minZ = seed.getZ();
            int maxX = seed.getX();
            int maxY = seed.getY();
            int maxZ = seed.getZ();
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                minX = Math.min(minX, current.getX());
                minY = Math.min(minY, current.getY());
                minZ = Math.min(minZ, current.getZ());
                maxX = Math.max(maxX, current.getX());
                maxY = Math.max(maxY, current.getY());
                maxZ = Math.max(maxZ, current.getZ());
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.relative(direction).immutable();
                    if (remaining.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            AABB bounds = new AABB(minX, minY, minZ, maxX + 1D, maxY + 1D, maxZ + 1D);
            clusters.add(new CargoCluster(seed, bounds));
        }
        return clusters.stream()
                .sorted(Comparator
                        .comparingInt((CargoCluster cluster) -> cluster.key().getY())
                        .thenComparingInt(cluster -> cluster.key().getZ())
                        .thenComparingInt(cluster -> cluster.key().getX()))
                .toList();
    }

    private record CargoCluster(BlockPos key, AABB bounds) {
    }

    private static void showFlightPath(
            List<Vec3> points,
            List<Integer> legEndIndices,
            List<UUID> legPaletteKeys,
            List<Integer> legColorSlots,
            List<Integer> breakStartIndices
    ) {
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        int pulsingSegment = points.size() <= 1 ? -1 : (int) (gameTime % (points.size() - 1));
        int legCount = Math.max(1, legEndIndices.size());
        boolean closedLoop = isClosedLoopPreview(points, legPaletteKeys);
        LinkedHashMap<UUID, Integer> paletteAssignments = buildPaletteAssignments(legPaletteKeys);

        for (int i = 1; i < points.size(); i++) {
            if (isBreakStart(i, breakStartIndices)) {
                continue;
            }
            int legIndex = legIndexForSegment(i, legEndIndices);
            int paletteIndex = paletteIndexForLeg(legIndex, legPaletteKeys, paletteAssignments);
            int colorIndex = colorIndexForLeg(legIndex, legCount, closedLoop, legColorSlots);
            int[] shades = LEG_COLORS[paletteIndex][colorIndex];
            int[] pulseShades = LEG_PULSE_COLORS[paletteIndex][colorIndex];
            int shadeIndex = i % 2 == 0 ? 0 : 1;
            int color = i == pulsingSegment ? pulseShades[shadeIndex] : shades[shadeIndex];
            Outliner.getInstance()
                    .showLine(Pair.of("flight_path_segment", Integer.valueOf(i)), points.get(i - 1), points.get(i))
                    .colored(color)
                    .lineWidth(i == pulsingSegment ? 1 / 8f : 1 / 16f)
                    .disableLineNormals();

            if (i % ROUTE_ARROW_SPACING == 0 || i == points.size() - 1) {
                showDirectionArrow(i, points.get(i - 1), points.get(i), color);
            }
        }

        int legStartIndex = 0;
        for (int legIndex = 0; legIndex < legEndIndices.size(); legIndex++) {
            int legEndIndex = Math.max(0, Math.min(points.size() - 1, legEndIndices.get(legIndex)));
            int boundedStartIndex = Math.max(0, Math.min(legStartIndex, points.size() - 1));
            Vec3 start = points.get(boundedStartIndex);
            Vec3 end = points.get(legEndIndex);
            int markerColor = lineColorForLeg(legIndex, legCount, closedLoop, legPaletteKeys, legColorSlots, paletteAssignments);
            Outliner.getInstance()
                    .showAABB(Pair.of("flight_path_start", Integer.valueOf(legIndex)), AABB.ofSize(start, .35, .35, .35))
                    .colored(markerColor)
                    .lineWidth(1 / 16f)
                    .disableLineNormals()
                    .withFaceTexture(AllSpecialTextures.SELECTION);
            Outliner.getInstance()
                    .showAABB(Pair.of("flight_path_end", Integer.valueOf(legIndex)), AABB.ofSize(end, .35, .35, .35))
                    .colored(markerColor)
                    .lineWidth(1 / 16f)
                    .disableLineNormals()
                    .withFaceTexture(AllSpecialTextures.SELECTION);
            legStartIndex = legEndIndex + 1;
        }
    }

    private static void showDirectionArrow(int index, Vec3 from, Vec3 to, int color) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        direction = direction.normalize();
        Vec3 midpoint = from.add(to).scale(0.5D);
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6D) {
            side = direction.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize();

        double stemHalf = 0.35D;
        double wingHalf = 0.18D;
        Vec3 tip = midpoint.add(direction.scale(stemHalf));
        Vec3 tail = midpoint.subtract(direction.scale(stemHalf));
        Vec3 left = tail.add(side.scale(wingHalf));
        Vec3 right = tail.subtract(side.scale(wingHalf));

        Outliner.getInstance()
                .showLine(Pair.of("flight_path_arrow_left", Integer.valueOf(index)), left, tip)
                .colored(color)
                .lineWidth(1 / 16f)
                .disableLineNormals();
        Outliner.getInstance()
                .showLine(Pair.of("flight_path_arrow_right", Integer.valueOf(index)), right, tip)
                .colored(color)
                .lineWidth(1 / 16f)
                .disableLineNormals();
    }

    private static void removeFlightPath(List<Vec3> points, List<Integer> legEndIndices) {
        for (int i = 1; i < points.size(); i++) {
            Outliner.getInstance().remove(Pair.of("flight_path_segment", Integer.valueOf(i)));
            if (i % ROUTE_ARROW_SPACING == 0 || i == points.size() - 1) {
                Outliner.getInstance().remove(Pair.of("flight_path_arrow_left", Integer.valueOf(i)));
                Outliner.getInstance().remove(Pair.of("flight_path_arrow_right", Integer.valueOf(i)));
            }
        }
        for (int legIndex = 0; legIndex < legEndIndices.size(); legIndex++) {
            Outliner.getInstance().remove(Pair.of("flight_path_start", Integer.valueOf(legIndex)));
            Outliner.getInstance().remove(Pair.of("flight_path_end", Integer.valueOf(legIndex)));
        }
    }

    private static int legIndexForSegment(int segmentPointIndex, List<Integer> legEndIndices) {
        if (legEndIndices.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < legEndIndices.size(); i++) {
            if (segmentPointIndex <= legEndIndices.get(i)) {
                return i;
            }
        }
        return legEndIndices.size() - 1;
    }

    private static int paletteIndexForLeg(int legIndex, List<UUID> legPaletteKeys, LinkedHashMap<UUID, Integer> paletteAssignments) {
        if (legPaletteKeys.isEmpty()) {
            return 0;
        }
        UUID paletteKey = legPaletteKeys.get(Math.max(0, Math.min(legIndex, legPaletteKeys.size() - 1)));
        return paletteAssignments.getOrDefault(paletteKey, 0);
    }

    private static int colorIndexForLeg(int legIndex, int legCount, boolean closedLoop, List<Integer> legColorSlots) {
        int paletteSize = LEG_COLORS[0].length;
        if (!legColorSlots.isEmpty()) {
            int slot = legColorSlots.get(Math.max(0, Math.min(legIndex, legColorSlots.size() - 1)));
            return Math.floorMod(slot, paletteSize);
        }
        int colorIndex = Math.floorMod(legIndex, paletteSize);
        if (!closedLoop || legCount <= 1 || legIndex != legCount - 1) {
            return colorIndex;
        }
        int firstColorIndex = 0;
        int previousColorIndex = Math.floorMod(legIndex - 1, paletteSize);
        if (colorIndex != firstColorIndex) {
            return colorIndex;
        }
        for (int offset = 1; offset < paletteSize; offset++) {
            int candidate = Math.floorMod(colorIndex + offset, paletteSize);
            if (candidate != firstColorIndex && candidate != previousColorIndex) {
                return candidate;
            }
        }
        return colorIndex;
    }

    private static int lineColorForLeg(
            int legIndex,
            int legCount,
            boolean closedLoop,
            List<UUID> legPaletteKeys,
            List<Integer> legColorSlots,
            LinkedHashMap<UUID, Integer> paletteAssignments
    ) {
        int paletteIndex = paletteIndexForLeg(legIndex, legPaletteKeys, paletteAssignments);
        int colorIndex = colorIndexForLeg(legIndex, legCount, closedLoop, legColorSlots);
        return LEG_COLORS[paletteIndex][colorIndex][0];
    }

    private static LinkedHashMap<UUID, Integer> buildPaletteAssignments(List<UUID> legPaletteKeys) {
        LinkedHashMap<UUID, Integer> assignments = new LinkedHashMap<>();
        boolean[] used = new boolean[LEG_COLORS.length];
        for (UUID paletteKey : legPaletteKeys) {
            if (assignments.containsKey(paletteKey)) {
                continue;
            }
            int preferred = Math.floorMod(paletteKey.hashCode(), LEG_COLORS.length);
            int chosen = choosePaletteIndex(preferred, used, assignments.values());
            assignments.put(paletteKey, chosen);
            used[chosen] = true;
        }
        return assignments;
    }

    private static int choosePaletteIndex(int preferred, boolean[] used, Collection<Integer> assignedPalettes) {
        if (assignedPalettes.isEmpty()) {
            return preferred;
        }

        int chosen = preferred;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int offset = 0; offset < LEG_COLORS.length; offset++) {
            int candidate = Math.floorMod(preferred + offset, LEG_COLORS.length);
            if (used[candidate]) {
                continue;
            }

            double separationScore = minimumPaletteDistance(candidate, assignedPalettes);
            double preferencePenalty = circularPaletteDistance(candidate, preferred) / (double) LEG_COLORS.length;
            double score = separationScore - preferencePenalty;
            if (score > bestScore) {
                bestScore = score;
                chosen = candidate;
            }
        }
        return chosen;
    }

    private static double minimumPaletteDistance(int candidate, Collection<Integer> assignedPalettes) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int assigned : assignedPalettes) {
            minimum = Math.min(minimum, paletteDistance(candidate, assigned));
        }
        return minimum;
    }

    private static double paletteDistance(int leftPalette, int rightPalette) {
        double distance = 0.0D;
        for (int colorIndex = 0; colorIndex < LEG_COLORS[leftPalette].length; colorIndex++) {
            distance += colorDistance(
                    LEG_COLORS[leftPalette][colorIndex][0],
                    LEG_COLORS[rightPalette][colorIndex][0]
            );
        }
        return distance;
    }

    private static double colorDistance(int leftColor, int rightColor) {
        int leftRed = (leftColor >> 16) & 0xFF;
        int leftGreen = (leftColor >> 8) & 0xFF;
        int leftBlue = leftColor & 0xFF;
        int rightRed = (rightColor >> 16) & 0xFF;
        int rightGreen = (rightColor >> 8) & 0xFF;
        int rightBlue = rightColor & 0xFF;
        int redDelta = leftRed - rightRed;
        int greenDelta = leftGreen - rightGreen;
        int blueDelta = leftBlue - rightBlue;
        return redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta;
    }

    private static int circularPaletteDistance(int candidate, int preferred) {
        int raw = Math.abs(candidate - preferred);
        return Math.min(raw, LEG_COLORS.length - raw);
    }

    private static boolean isClosedLoopPreview(List<Vec3> points, List<UUID> legPaletteKeys) {
        if (points.size() < 2 || legPaletteKeys.isEmpty()) {
            return false;
        }
        UUID firstKey = legPaletteKeys.getFirst();
        if (legPaletteKeys.stream().anyMatch(key -> !key.equals(firstKey))) {
            return false;
        }
        return points.getFirst().distanceTo(points.getLast()) <= 1.0D;
    }

    private static boolean isBreakStart(int pointIndex, List<Integer> breakStartIndices) {
        return breakStartIndices.contains(pointIndex);
    }

    private static int pulsingLinkCandidateColor() {
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        return LINK_CANDIDATE_COLORS[(int) ((gameTime / 10L) % LINK_CANDIDATE_COLORS.length)];
    }

    private static float pulsingLinkCandidateLineWidth() {
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        return (gameTime / 10L) % 2L == 0L ? 1 / 10f : 1 / 16f;
    }

    private static Vec3 pointOnCircle(Vec3 center, double radius, double theta, int axis) {
        double c = Math.cos(theta) * radius;
        double s = Math.sin(theta) * radius;
        return switch (axis) {
            case 0 -> new Vec3(center.x + c, center.y, center.z + s); // XZ
            case 1 -> new Vec3(center.x + c, center.y + s, center.z); // XY
            case 2 -> new Vec3(center.x, center.y + c, center.z + s); // YZ
            default -> new Vec3(center.x + c, center.y, center.z + s);
        };
    }

    private record LandingAreaOverlay(BlockPos stationPos, double radius) {
    }

    private record StationChunkOverlay(BlockPos stationPos, int radiusChunks) {
    }

    public record StationRoutePreview(UUID stationId, Optional<UUID> filterTransponderId) {
        public StationRoutePreview {
            filterTransponderId = filterTransponderId == null ? Optional.empty() : filterTransponderId;
        }
    }
}

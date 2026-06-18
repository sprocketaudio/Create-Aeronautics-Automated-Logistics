package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllSpecialTextures;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;

public final class LogisticsClientOverlays {
    private static final int LANDING_COLOR = 0x95E06C;
    private static final int[][] LEG_COLORS = new int[][]{
            {0x7AD7FF, 0x2F9BFF},
            {0x9BEA8B, 0x57C96C}
    };
    private static final int[][] LEG_PULSE_COLORS = new int[][]{
            {0xDFFDFF, 0xBFEFFF},
            {0xE8FFD9, 0xCAFFB2}
    };
    private static final int START_COLOR = 0x95E06C;
    private static final int END_COLOR = 0xFFD36C;
    private static final int DOCK_COLOR = 0x7AD7FF;
    private static final int CARGO_COLOR = 0x9BEA8B;
    private static final int TRANSPONDER_COLOR = 0xBFEFFF;
    private static final int[] LINK_CANDIDATE_COLORS = new int[]{0xFFE37A, 0xFFC84D};
    private static final int LANDING_SEGMENTS = 48;
    private static final int LATITUDE_RINGS = 5;
    private static final int ROUTE_ARROW_SPACING = 12;

    private static Optional<LandingAreaOverlay> landingAreaOverlay = Optional.empty();
    private static Optional<BlockPos> dockOverlay = Optional.empty();
    private static Optional<BlockPos> shipTransponderOverlay = Optional.empty();
    private static List<BlockPos> dockLinkCandidates = List.of();
    private static List<BlockPos> cargoOverlay = List.of();
    private static List<List<BlockPos>> cargoOverlayGroups = List.of();
    private static List<List<BlockPos>> cargoLinkCandidates = List.of();
    private static List<Vec3> flightPath = List.of();
    private static List<Integer> flightPathLegEnds = List.of();
    private static Optional<UUID> previewedRouteId = Optional.empty();
    private static Optional<UUID> previewedStationId = Optional.empty();
    private static Optional<UUID> previewedTransponderId = Optional.empty();
    private static Optional<BlockPos> previewedTransponderPos = Optional.empty();

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
            BlockPos transponderPos,
            UUID routeId,
            UUID stationId,
            UUID transponderId
    ) {
        removeFlightPath(flightPath);
        flightPath = List.copyOf(points);
        flightPathLegEnds = List.copyOf(legEndIndices);
        previewedRouteId = Optional.ofNullable(routeId);
        previewedStationId = Optional.ofNullable(stationId);
        previewedTransponderId = Optional.ofNullable(transponderId);
        previewedTransponderPos = Optional.ofNullable(transponderPos).map(BlockPos::immutable);
    }

    public static void setPreviewedRouteId(UUID routeId) {
        previewedRouteId = Optional.ofNullable(routeId);
    }

    public static Optional<UUID> previewedRouteId() {
        return previewedRouteId;
    }

    public static Optional<BlockPos> previewedTransponderPos() {
        return previewedTransponderPos;
    }

    public static Optional<UUID> previewedStationId() {
        return previewedStationId;
    }

    public static Optional<UUID> previewedTransponderId() {
        return previewedTransponderId;
    }

    public static void clearFlightPath() {
        removeFlightPath(flightPath);
        flightPath = List.of();
        flightPathLegEnds = List.of();
        previewedRouteId = Optional.empty();
        previewedStationId = Optional.empty();
        previewedTransponderId = Optional.empty();
        previewedTransponderPos = Optional.empty();
    }

    public static void clearFlightPathIfPreviewingTransponder(BlockPos transponderPos) {
        if (previewedTransponderPos.filter(transponderPos::equals).isPresent()) {
            clearFlightPath();
        }
    }

    public static void clearFlightPathIfPreviewingRoutes(Collection<RouteSegment> routes) {
        if (previewedRouteId.isEmpty()) {
            return;
        }
        boolean matches = routes.stream().map(route -> route.id().value()).anyMatch(previewedRouteId.get()::equals);
        if (matches) {
            clearFlightPath();
        }
    }

    public static void clearFlightPathIfPreviewingTransponderId(UUID transponderId) {
        if (previewedTransponderId.filter(transponderId::equals).isPresent()) {
            clearFlightPath();
        }
    }

    public static void clearFlightPathIfPreviewingStationId(UUID stationId) {
        if (previewedStationId.filter(stationId::equals).isPresent()) {
            clearFlightPath();
        }
    }

    public static void clearLandingAreaIfMatches(BlockPos stationPos) {
        if (landingAreaOverlay.filter(overlay -> overlay.stationPos().equals(stationPos)).isPresent()) {
            clearLandingArea();
        }
    }

    public static boolean hasFlightPath() {
        return flightPath.size() >= 2;
    }

    public static void refresh() {
        landingAreaOverlay.ifPresent(LogisticsClientOverlays::showLandingArea);
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
            showFlightPath(flightPath, flightPathLegEnds);
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

    private static void showFlightPath(List<Vec3> points, List<Integer> legEndIndices) {
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        int pulsingSegment = points.size() <= 1 ? -1 : (int) (gameTime % (points.size() - 1));

        for (int i = 1; i < points.size(); i++) {
            int legIndex = legIndexForSegment(i, legEndIndices);
            int[] shades = LEG_COLORS[Math.floorMod(legIndex, LEG_COLORS.length)];
            int[] pulseShades = LEG_PULSE_COLORS[Math.floorMod(legIndex, LEG_PULSE_COLORS.length)];
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

        Vec3 start = points.getFirst();
        Vec3 end = points.getLast();
        Outliner.getInstance()
                .showAABB("flight_path_start", AABB.ofSize(start, .35, .35, .35))
                .colored(START_COLOR)
                .lineWidth(1 / 16f)
                .disableLineNormals()
                .withFaceTexture(AllSpecialTextures.SELECTION);
        Outliner.getInstance()
                .showAABB("flight_path_end", AABB.ofSize(end, .35, .35, .35))
                .colored(END_COLOR)
                .lineWidth(1 / 16f)
                .disableLineNormals()
                .withFaceTexture(AllSpecialTextures.SELECTION);
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

    private static void removeFlightPath(List<Vec3> points) {
        for (int i = 1; i < points.size(); i++) {
            Outliner.getInstance().remove(Pair.of("flight_path_segment", Integer.valueOf(i)));
            if (i % ROUTE_ARROW_SPACING == 0 || i == points.size() - 1) {
                Outliner.getInstance().remove(Pair.of("flight_path_arrow_left", Integer.valueOf(i)));
                Outliner.getInstance().remove(Pair.of("flight_path_arrow_right", Integer.valueOf(i)));
            }
        }
        Outliner.getInstance().remove("flight_path_start");
        Outliner.getInstance().remove("flight_path_end");
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
}

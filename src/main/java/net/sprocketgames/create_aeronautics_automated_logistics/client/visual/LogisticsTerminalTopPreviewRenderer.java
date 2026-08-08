package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsClientConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.LogisticsTerminalBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapMarker;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;
import org.joml.Matrix4f;

public final class LogisticsTerminalTopPreviewRenderer {
    private static final ResourceLocation AIRSHIP_ICON =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/blimp.png");
    private static final ResourceLocation TRAIN_ICON =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "textures/gui/train.png");
    private static final int[] ROUTE_COLORS = {
            0xFF7CC9FF, 0xFFF7BE6A, 0xFF89E0A8, 0xFFFF8F8F,
            0xFFC6A0FF, 0xFFFFD36E, 0xFF7DE1D8, 0xFFFFA5D8
    };
    // The world-space lines use Minecraft's fixed pixel-width line renderer, so distant detail becomes visual noise.
    private static final double MAX_RENDER_DISTANCE = 24.0D;
    private static final float TOP_Y = 11.02F / 16.0F;
    private static final float MIN_X = 2.7F / 16.0F;
    private static final float MAX_X = 13.3F / 16.0F;
    private static final float MIN_Z = 2.7F / 16.0F;
    private static final float MAX_Z = 13.3F / 16.0F;
    private static final float STATION_OUTER_SIZE = 0.030F;
    private static final float STATION_INNER_SIZE = 0.017F;
    private static final float ICON_SIZE = 0.07F;
    private static final float ROUTE_Y = TOP_Y + 0.0015F;
    private static final float STATION_Y = TOP_Y + 0.0035F;
    private static final float ICON_Y = TOP_Y + 0.009F;

    private LogisticsTerminalTopPreviewRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!AutomatedLogisticsClientConfig.RENDER_LOGISTICS_TERMINAL_TOP_PREVIEW.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        DataSet dataSet = collect(dimension);
        if (dataSet.empty()) {
            return;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        for (var pos : LogisticsTerminalPreviewClientState.terminals()) {
            if (!(minecraft.level.getBlockEntity(pos) instanceof LogisticsTerminalBlockEntity terminal)) {
                continue;
            }
            if (!LogisticsTerminalPreviewClientState.canRender(pos)) {
                continue;
            }
            if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }
            renderTerminal(poseStack, buffer, camera, minecraft.player.getYRot(), pos.getX(), pos.getY(), pos.getZ(), dataSet);
        }
        buffer.endBatch(RenderType.lines());
        buffer.endBatch(RenderType.entityCutoutNoCull(AIRSHIP_ICON));
        buffer.endBatch(RenderType.entityCutoutNoCull(TRAIN_ICON));
    }

    private static DataSet collect(ResourceKey<Level> dimension) {
        List<RouteSegment> routes = LogisticsTerminalPreviewClientState.routes().stream()
                .filter(route -> route.dimension().equals(dimension))
                .sorted(Comparator.comparingLong(RouteSegment::createdEpochMillis))
                .toList();
        List<ShipMapMarker> markers = LogisticsTerminalPreviewClientState.markers().stream()
                .filter(marker -> marker.dimension().equals(dimension))
                .toList();
        List<AirshipStationSnapshot> stations = visibleStationsFor(routes, dimension);
        return new DataSet(routes, stations, markers, Bounds.from(routes, stations, markers), routeColors(routes));
    }

    private static List<AirshipStationSnapshot> visibleStationsFor(List<RouteSegment> routes, ResourceKey<Level> dimension) {
        if (routes.isEmpty()) {
            return List.of();
        }
        Set<UUID> visibleStationIds = new HashSet<>();
        for (RouteSegment route : routes) {
            visibleStationIds.add(route.startStationId());
            visibleStationIds.add(route.endStationId());
        }
        return AirshipStationRegistry.knownStations(dimension).stream()
                .filter(station -> visibleStationIds.contains(station.stationId()))
                .toList();
    }

    private static void renderTerminal(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffer,
            Vec3 camera,
            float playerYawDegrees,
            int blockX,
            int blockY,
            int blockZ,
            DataSet dataSet
    ) {
        poseStack.pushPose();
        poseStack.translate(blockX - camera.x, blockY - camera.y, blockZ - camera.z);

        VertexConsumer lineBuffer = buffer.getBuffer(RenderType.lines());
        for (RouteSegment route : dataSet.routes()) {
            int color = dataSet.routeColors().getOrDefault(route.transponderId(), fallbackRouteColor(route.transponderId()));
            for (int i = 1; i < route.points().size(); i++) {
                Point2 from = project(route.points().get(i - 1).position(), dataSet.bounds());
                Point2 to = project(route.points().get(i).position(), dataSet.bounds());
                addLine(poseStack, lineBuffer, from.x(), ROUTE_Y, from.z(), to.x(), ROUTE_Y, to.z(), color);
            }
        }

        for (AirshipStationSnapshot station : dataSet.stations()) {
            Point2 point = project(Vec3.atCenterOf(station.stationPos()), dataSet.bounds());
            addStationNode(poseStack, lineBuffer, point.x(), point.z());
        }

        for (ShipMapMarker marker : dataSet.markers()) {
            Point2 point = project(marker.position(), dataSet.bounds());
            renderIconQuad(
                    poseStack,
                    buffer.getBuffer(RenderType.entityCutoutNoCull(iconFor(marker.transportMode()))),
                    playerYawDegrees,
                    point.x(),
                    ICON_Y,
                    point.z()
            );
        }

        poseStack.popPose();
    }

    private static void addStationNode(PoseStack poseStack, VertexConsumer buffer, float x, float z) {
        addSquareOutline(poseStack, buffer, x, z, STATION_OUTER_SIZE, 0xFF4F4036);
        addSquareOutline(poseStack, buffer, x, z, STATION_INNER_SIZE, 0xFFE7D6B4);
    }

    private static void addSquareOutline(PoseStack poseStack, VertexConsumer buffer, float x, float z, float halfSize, int color) {
        addLine(poseStack, buffer, x - halfSize, STATION_Y, z - halfSize, x + halfSize, STATION_Y, z - halfSize, color);
        addLine(poseStack, buffer, x + halfSize, STATION_Y, z - halfSize, x + halfSize, STATION_Y, z + halfSize, color);
        addLine(poseStack, buffer, x + halfSize, STATION_Y, z + halfSize, x - halfSize, STATION_Y, z + halfSize, color);
        addLine(poseStack, buffer, x - halfSize, STATION_Y, z + halfSize, x - halfSize, STATION_Y, z - halfSize, color);
    }

    private static void renderIconQuad(PoseStack poseStack, VertexConsumer buffer, float playerYawDegrees, float x, float y, float z) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(playerYawDegrees));
        Matrix4f matrix = poseStack.last().pose();
        float half = ICON_SIZE * 0.5F;
        addTexturedVertex(buffer, matrix, -half, -half, 0.0F, 0.0F, 1.0F);
        addTexturedVertex(buffer, matrix, -half, half, 0.0F, 0.0F, 0.0F);
        addTexturedVertex(buffer, matrix, half, half, 0.0F, 1.0F, 0.0F);
        addTexturedVertex(buffer, matrix, half, -half, 0.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private static void addTexturedVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void addLine(PoseStack poseStack, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float normalX = x1 - x0;
        float normalY = y1 - y0;
        float normalZ = z1 - z0;
        float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length <= 0.0001F) {
            return;
        }
        normalX /= length;
        normalY /= length;
        normalZ /= length;
        Matrix4f matrix = poseStack.last().pose();
        buffer.addVertex(matrix, x0, y0, z0).setColor(red, green, blue, 1.0F).setNormal(normalX, normalY, normalZ);
        buffer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, 1.0F).setNormal(normalX, normalY, normalZ);
    }

    private static Point2 project(Vec3 position, Bounds bounds) {
        double width = Math.max(16.0D, bounds.maxX() - bounds.minX());
        double depth = Math.max(16.0D, bounds.maxZ() - bounds.minZ());
        float x = (float) (MIN_X + ((position.x - bounds.minX()) / width) * (MAX_X - MIN_X));
        float z = (float) (MIN_Z + ((position.z - bounds.minZ()) / depth) * (MAX_Z - MIN_Z));
        return new Point2(Mth.clamp(x, MIN_X, MAX_X), Mth.clamp(z, MIN_Z, MAX_Z));
    }

    private static ResourceLocation iconFor(TransportMode mode) {
        return mode == TransportMode.TRAIN ? TRAIN_ICON : AIRSHIP_ICON;
    }

    private static Map<UUID, Integer> routeColors(List<RouteSegment> routes) {
        Map<UUID, Long> firstSeen = new HashMap<>();
        for (RouteSegment route : routes) {
            firstSeen.merge(route.transponderId(), route.createdEpochMillis(), Math::min);
        }
        List<UUID> vehicleIds = firstSeen.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().thenComparing(entry -> entry.getKey().toString()))
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
            usedColors.add(color);
            assigned.put(vehicleId, color);
        }
        return assigned;
    }

    private static int firstUnusedColor(int startIndex, Set<Integer> usedColors) {
        for (int i = 0; i < ROUTE_COLORS.length; i++) {
            int color = ROUTE_COLORS[(startIndex + i) % ROUTE_COLORS.length];
            if (!usedColors.contains(color)) {
                return color;
            }
        }
        return ROUTE_COLORS[startIndex % ROUTE_COLORS.length];
    }

    private static int fallbackRouteColor(UUID vehicleId) {
        return ROUTE_COLORS[Math.floorMod(vehicleId.hashCode(), ROUTE_COLORS.length)];
    }

    private record Point2(float x, float z) {
    }

    private record DataSet(
            List<RouteSegment> routes,
            List<AirshipStationSnapshot> stations,
            List<ShipMapMarker> markers,
            Bounds bounds,
            Map<UUID, Integer> routeColors
    ) {
        boolean empty() {
            return bounds.empty();
        }
    }

    private record Bounds(double minX, double minZ, double maxX, double maxZ, boolean empty) {
        static Bounds from(List<RouteSegment> routes, List<AirshipStationSnapshot> stations, List<ShipMapMarker> markers) {
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (RouteSegment route : routes) {
                for (RoutePoint point : route.points()) {
                    minX = Math.min(minX, point.position().x);
                    minZ = Math.min(minZ, point.position().z);
                    maxX = Math.max(maxX, point.position().x);
                    maxZ = Math.max(maxZ, point.position().z);
                }
            }
            for (AirshipStationSnapshot station : stations) {
                minX = Math.min(minX, station.stationPos().getX() + 0.5D);
                minZ = Math.min(minZ, station.stationPos().getZ() + 0.5D);
                maxX = Math.max(maxX, station.stationPos().getX() + 0.5D);
                maxZ = Math.max(maxZ, station.stationPos().getZ() + 0.5D);
            }
            for (ShipMapMarker marker : markers) {
                minX = Math.min(minX, marker.position().x);
                minZ = Math.min(minZ, marker.position().z);
                maxX = Math.max(maxX, marker.position().x);
                maxZ = Math.max(maxZ, marker.position().z);
            }
            if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
                return new Bounds(0.0D, 0.0D, 0.0D, 0.0D, true);
            }
            return new Bounds(minX, minZ, maxX, maxZ, false);
        }
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public final class TrainDockingSupport {
    private static final int MAX_CONNECTED_SUBLEVELS = 24;
    private static final String BOGEY_CLASS_NAME = "com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity";
    private static final String COUPLER_CLASS_NAME = "com.crystaelix.simurail.content.automatic_coupler.AutomaticCouplerBlockEntity";

    private static volatile boolean reflectionInitialized;
    private static Class<?> bogeyClass;
    private static Class<?> couplerClass;
    private static Field bogeyFrontSubLevelField;
    private static Field bogeyBackSubLevelField;
    private static Field couplerPartnerSubLevelField;
    private static Field couplerGangwayPartnerSubLevelField;

    private TrainDockingSupport() {
    }

    public static boolean dockBelongsToConnectedTrain(ServerLevel level, UUID rootSubLevelId, BlockPos dockPos) {
        Optional<UUID> dockSubLevelId = net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController.subLevelIdAt(level, dockPos);
        if (dockSubLevelId.isEmpty()) {
            return false;
        }
        return connectedSubLevelIds(level, rootSubLevelId).contains(dockSubLevelId.get());
    }

    public static List<BlockPos> discoverConnectedTrainDocks(ServerLevel level, UUID rootSubLevelId, BlockPos originPos) {
        return connectedSubLevelIds(level, rootSubLevelId).stream()
                .map(subLevelId -> subLevel(level, subLevelId))
                .flatMap(Optional::stream)
                .flatMap(subLevel -> docksInSubLevel(level, subLevel).stream())
                .distinct()
                .sorted(Comparator
                        .comparingDouble((BlockPos pos) -> Sable.HELPER.distanceSquaredWithSubLevels(level, originPos.getCenter(), pos.getCenter()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .toList();
    }

    public static List<BlockPos> discoverConnectedTrainPositions(ServerLevel level, UUID rootSubLevelId, BlockPos originPos) {
        return connectedSubLevelIds(level, rootSubLevelId).stream()
                .map(subLevelId -> subLevel(level, subLevelId))
                .flatMap(Optional::stream)
                .flatMap(subLevel -> positionsWithin(subLevel.getPlot().getBoundingBox()).stream())
                .distinct()
                .sorted(Comparator
                        .comparingDouble((BlockPos pos) -> Sable.HELPER.distanceSquaredWithSubLevels(level, originPos.getCenter(), pos.getCenter()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX))
                .toList();
    }

    private static Set<UUID> connectedSubLevelIds(ServerLevel level, UUID rootSubLevelId) {
        LinkedHashSet<UUID> visited = new LinkedHashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        visited.add(rootSubLevelId);
        queue.add(rootSubLevelId);

        while (!queue.isEmpty() && visited.size() < MAX_CONNECTED_SUBLEVELS) {
            UUID currentId = queue.removeFirst();
            Optional<ServerSubLevel> current = subLevel(level, currentId);
            if (current.isEmpty()) {
                continue;
            }
            for (UUID linkedId : linkedSubLevels(current.get())) {
                if (linkedId == null || visited.contains(linkedId)) {
                    continue;
                }
                if (subLevel(level, linkedId).isEmpty()) {
                    continue;
                }
                visited.add(linkedId);
                queue.addLast(linkedId);
                if (visited.size() >= MAX_CONNECTED_SUBLEVELS) {
                    break;
                }
            }
        }

        return visited;
    }

    private static Optional<ServerSubLevel> subLevel(ServerLevel level, UUID subLevelId) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Optional.empty();
        }
        if (container.getSubLevel(subLevelId) instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return Optional.of(serverSubLevel);
        }
        return Optional.empty();
    }

    private static Set<UUID> linkedSubLevels(ServerSubLevel subLevel) {
        if (!initializeReflection()) {
            return Set.of();
        }

        LinkedHashSet<UUID> linked = new LinkedHashSet<>();
        for (BlockPos pos : positionsWithin(subLevel.getPlot().getBoundingBox())) {
            BlockEntity blockEntity = subLevel.getLevel().getBlockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            tryAddLinkedId(linked, blockEntity, bogeyClass, bogeyFrontSubLevelField);
            tryAddLinkedId(linked, blockEntity, bogeyClass, bogeyBackSubLevelField);
            tryAddLinkedId(linked, blockEntity, couplerClass, couplerPartnerSubLevelField);
            tryAddLinkedId(linked, blockEntity, couplerClass, couplerGangwayPartnerSubLevelField);
        }
        return linked;
    }

    private static List<BlockPos> docksInSubLevel(ServerLevel level, ServerSubLevel subLevel) {
        return positionsWithin(subLevel.getPlot().getBoundingBox()).stream()
                .filter(pos -> DockingConnectorDiscovery.isDock(level, pos))
                .toList();
    }

    private static List<BlockPos> positionsWithin(Object box) {
        try {
            Class<?> boxClass = box.getClass();
            int minX = ((Number) boxClass.getMethod("minX").invoke(box)).intValue();
            int minY = ((Number) boxClass.getMethod("minY").invoke(box)).intValue();
            int minZ = ((Number) boxClass.getMethod("minZ").invoke(box)).intValue();
            int maxX = ((Number) boxClass.getMethod("maxX").invoke(box)).intValue();
            int maxY = ((Number) boxClass.getMethod("maxY").invoke(box)).intValue();
            int maxZ = ((Number) boxClass.getMethod("maxZ").invoke(box)).intValue();
            return BlockPos.betweenClosedStream(
                            Math.min(minX, maxX),
                            Math.min(minY, maxY),
                            Math.min(minZ, maxZ),
                            Math.max(minX, maxX),
                            Math.max(minY, maxY),
                            Math.max(minZ, maxZ))
                    .map(BlockPos::immutable)
                    .toList();
        } catch (ReflectiveOperationException exception) {
            CreateAeronauticsAutomatedLogistics.debugDockingWarn(
                    "Train docking support could not read sublevel bounds: {}",
                    exception.toString()
            );
            return List.of();
        }
    }

    private static void tryAddLinkedId(Set<UUID> linked, BlockEntity blockEntity, Class<?> expectedClass, Field field) {
        if (expectedClass == null || field == null || !expectedClass.isInstance(blockEntity)) {
            return;
        }
        try {
            Object value = field.get(blockEntity);
            if (value instanceof UUID id) {
                linked.add(id);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static boolean initializeReflection() {
        if (reflectionInitialized) {
            return bogeyClass != null
                    && couplerClass != null
                    && bogeyFrontSubLevelField != null
                    && bogeyBackSubLevelField != null
                    && couplerPartnerSubLevelField != null
                    && couplerGangwayPartnerSubLevelField != null;
        }

        synchronized (TrainDockingSupport.class) {
            if (reflectionInitialized) {
                return bogeyClass != null
                        && couplerClass != null
                        && bogeyFrontSubLevelField != null
                        && bogeyBackSubLevelField != null
                        && couplerPartnerSubLevelField != null
                        && couplerGangwayPartnerSubLevelField != null;
            }
            try {
                bogeyClass = Class.forName(BOGEY_CLASS_NAME);
                couplerClass = Class.forName(COUPLER_CLASS_NAME);
                bogeyFrontSubLevelField = accessibleField(bogeyClass, "connectionFrontSubLevelID");
                bogeyBackSubLevelField = accessibleField(bogeyClass, "connectionBackSubLevelID");
                couplerPartnerSubLevelField = accessibleField(couplerClass, "partnerSubLevelID");
                couplerGangwayPartnerSubLevelField = accessibleField(couplerClass, "gangwayPartnerSubLevelID");
            } catch (ReflectiveOperationException exception) {
                CreateAeronauticsAutomatedLogistics.debugDockingWarn(
                        "Train docking support reflection unavailable: {}",
                        exception.toString()
                );
                bogeyClass = null;
                couplerClass = null;
                bogeyFrontSubLevelField = null;
                bogeyBackSubLevelField = null;
                couplerPartnerSubLevelField = null;
                couplerGangwayPartnerSubLevelField = null;
            }
            reflectionInitialized = true;
            return bogeyClass != null
                    && couplerClass != null
                    && bogeyFrontSubLevelField != null
                    && bogeyBackSubLevelField != null
                    && couplerPartnerSubLevelField != null
                    && couplerGangwayPartnerSubLevelField != null;
        }
    }

    private static Field accessibleField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

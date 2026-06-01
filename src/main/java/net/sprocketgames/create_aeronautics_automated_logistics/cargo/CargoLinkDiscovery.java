package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;

public final class CargoLinkDiscovery {
    public static final int DEFAULT_LINK_RADIUS = 6;

    private CargoLinkDiscovery() {
    }

    public static List<LinkedCargoEntry> discover(Level level, BlockPos origin) {
        return discover(level, origin, DEFAULT_LINK_RADIUS);
    }

    public static List<LinkedCargoEntry> discover(Level level, BlockPos origin, int radius) {
        Set<BlockPos> visited = new HashSet<>();
        List<LinkedCargoEntry> linked = new ArrayList<>();
        BlockPos min = origin.offset(-radius, -radius, -radius);
        BlockPos max = origin.offset(radius, radius, radius);
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = candidate.immutable();
            if (!visited.add(pos)) {
                continue;
            }
            LinkedCargoEntry root = entryAt(level, pos);
            if (root == null) {
                continue;
            }
            collectConnected(level, pos, min, max, visited, linked);
        }
        linked.sort(Comparator
                .comparingInt((LinkedCargoEntry entry) -> entry.pos().getY())
                .thenComparingInt(entry -> entry.pos().getZ())
                .thenComparingInt(entry -> entry.pos().getX()));
        return List.copyOf(linked);
    }

    public static LinkedCargoSummary summarize(Level level, List<LinkedCargoEntry> entries) {
        if (entries.isEmpty()) {
            return new LinkedCargoSummary(0, 0, 0, 0, 0);
        }
        if (level == null) {
            return summarizeWithoutLevel(entries);
        }
        Set<CargoStorageRootResolver.StorageRoot> validRoots = new LinkedHashSet<>();
        Set<CargoStorageRootResolver.StorageRoot> staleRoots = new LinkedHashSet<>();
        Set<CargoStorageRootResolver.StorageRoot> itemRoots = new LinkedHashSet<>();
        Set<CargoStorageRootResolver.StorageRoot> fluidRoots = new LinkedHashSet<>();
        for (LinkedCargoEntry entry : entries) {
            LinkedCargoEntry resolved = entryAt(level, entry.pos());
            if (resolved == null) {
                staleRoots.add(CargoStorageRootResolver.resolve(level, entry.pos()));
                continue;
            }
            CargoStorageRootResolver.StorageRoot root = CargoStorageRootResolver.resolve(level, resolved.pos());
            validRoots.add(root);
            if (resolved.itemStorage()) {
                itemRoots.add(root);
            }
            if (resolved.fluidStorage()) {
                fluidRoots.add(root);
            }
        }
        int totalLinks = validRoots.size() + (int) staleRoots.stream()
                .filter(root -> !validRoots.contains(root))
                .count();
        return new LinkedCargoSummary(totalLinks, validRoots.size(), totalLinks - validRoots.size(), itemRoots.size(), fluidRoots.size());
    }

    private static LinkedCargoSummary summarizeWithoutLevel(List<LinkedCargoEntry> entries) {
        int itemLinks = 0;
        int fluidLinks = 0;
        for (LinkedCargoEntry entry : entries) {
            if (entry.itemStorage()) {
                itemLinks++;
            }
            if (entry.fluidStorage()) {
                fluidLinks++;
            }
        }
        return new LinkedCargoSummary(entries.size(), entries.size(), 0, itemLinks, fluidLinks);
    }

    private static void collectConnected(
            Level level,
            BlockPos start,
            BlockPos min,
            BlockPos max,
            Set<BlockPos> visited,
            List<LinkedCargoEntry> linked
    ) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            LinkedCargoEntry entry = entryAt(level, current);
            if (entry == null) {
                continue;
            }
            linked.add(entry);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getX() < min.getX() || next.getY() < min.getY() || next.getZ() < min.getZ()
                        || next.getX() > max.getX() || next.getY() > max.getY() || next.getZ() > max.getZ()) {
                    continue;
                }
                if (!visited.add(next.immutable())) {
                    continue;
                }
                if (entryAt(level, next) != null) {
                    queue.addLast(next.immutable());
                }
            }
        }
    }

    private static LinkedCargoEntry entryAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null
                || blockEntity instanceof ShipTransponderBlockEntity
                || blockEntity instanceof AirshipStationBlockEntity
                || blockEntity instanceof DockingConnectorBlockEntity) {
            return null;
        }
        return CargoLinkSupport.supportedEntryAt(level, pos).orElse(null);
    }
}

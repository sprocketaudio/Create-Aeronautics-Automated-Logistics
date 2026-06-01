package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public final class CargoLinkSupport {
    private CargoLinkSupport() {
    }

    public static Optional<LinkedCargoEntry> supportedEntryAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !isSupportedBlockEntity(blockEntity)) {
            return Optional.empty();
        }
        boolean itemStorage = CargoCapabilityAccess.hasAnyItemHandler(level, pos);
        boolean fluidStorage = CargoCapabilityAccess.hasAnyFluidHandler(level, pos);
        if (!itemStorage && !fluidStorage) {
            CustomCargoEndpointSupport.EndpointKinds endpointKinds = CustomCargoEndpointSupport.detect(level, pos);
            itemStorage = endpointKinds.itemStorage();
            fluidStorage = endpointKinds.fluidStorage();
        }
        if (!itemStorage && !fluidStorage) {
            return Optional.empty();
        }
        return Optional.of(new LinkedCargoEntry(pos, itemStorage, fluidStorage));
    }

    public static boolean isSupportedLinkTarget(Level level, BlockPos pos) {
        return supportedEntryAt(level, pos).isPresent();
    }

    public static List<LinkedCargoEntry> discoverLinkedGroup(Level level, BlockPos ownerOrigin, int radius, BlockPos clickedPos) {
        Optional<LinkedCargoEntry> clickedEntry = supportedEntryAt(level, clickedPos);
        if (clickedEntry.isEmpty()) {
            return List.of();
        }

        CargoStorageRootResolver.StorageRoot targetRoot = CargoStorageRootResolver.resolve(level, clickedPos);
        Map<BlockPos, LinkedCargoEntry> found = new LinkedHashMap<>();
        BlockPos min = ownerOrigin.offset(-radius, -radius, -radius);
        BlockPos max = ownerOrigin.offset(radius, radius, radius);
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            Optional<LinkedCargoEntry> maybeEntry = supportedEntryAt(level, candidate);
            if (maybeEntry.isEmpty()) {
                continue;
            }
            LinkedCargoEntry entry = maybeEntry.get();
            CargoStorageRootResolver.StorageRoot root = CargoStorageRootResolver.resolve(level, entry.pos());
            if (!sameRoot(targetRoot, root)) {
                continue;
            }
            found.put(entry.pos(), entry);
        }

        if (found.isEmpty()) {
            found.put(clickedEntry.get().pos(), clickedEntry.get());
        }

        return found.values().stream()
                .sorted(Comparator
                        .comparingInt((LinkedCargoEntry entry) -> entry.pos().getY())
                        .thenComparingInt(entry -> entry.pos().getZ())
                        .thenComparingInt(entry -> entry.pos().getX()))
                .toList();
    }

    public static List<BlockPos> expandPreviewPositions(Level level, BlockPos ownerOrigin, int radius, List<LinkedCargoEntry> linkedEntries) {
        return expandPreviewPositionGroups(level, ownerOrigin, radius, linkedEntries).stream()
                .flatMap(List::stream)
                .distinct()
                .sorted(Comparator
                        .comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(pos -> pos.getZ())
                        .thenComparingInt(pos -> pos.getX()))
                .toList();
    }

    public static List<List<BlockPos>> expandPreviewPositionGroups(Level level, BlockPos ownerOrigin, int radius, List<LinkedCargoEntry> linkedEntries) {
        if (linkedEntries.isEmpty()) {
            return List.of();
        }

        Map<CargoStorageRootResolver.StorageRoot, Set<BlockPos>> positionsByRoot = new LinkedHashMap<>();
        for (LinkedCargoEntry linkedEntry : linkedEntries) {
            CargoStorageRootResolver.StorageRoot root = CargoStorageRootResolver.resolve(level, linkedEntry.pos());
            positionsByRoot.computeIfAbsent(root, ignored -> new LinkedHashSet<>()).add(linkedEntry.pos());
        }

        BlockPos min = ownerOrigin.offset(-radius, -radius, -radius);
        BlockPos max = ownerOrigin.offset(radius, radius, radius);
        for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
            Optional<LinkedCargoEntry> maybeEntry = supportedEntryAt(level, candidate);
            if (maybeEntry.isEmpty()) {
                continue;
            }
            CargoStorageRootResolver.StorageRoot candidateRoot = CargoStorageRootResolver.resolve(level, candidate);
            Set<BlockPos> positions = positionsByRoot.get(candidateRoot);
            if (positions != null) {
                positions.add(candidate.immutable());
            }
        }

        return positionsByRoot.values().stream()
                .map(positions -> positions.stream()
                        .distinct()
                        .sorted(Comparator
                                .comparingInt((BlockPos pos) -> pos.getY())
                                .thenComparingInt(pos -> pos.getZ())
                                .thenComparingInt(pos -> pos.getX()))
                        .toList())
                .filter(positions -> !positions.isEmpty())
                .sorted(Comparator
                        .comparingInt((List<BlockPos> positions) -> positions.getFirst().getY())
                        .thenComparingInt(positions -> positions.getFirst().getZ())
                        .thenComparingInt(positions -> positions.getFirst().getX()))
                .toList();
    }

    public static List<String> supportedBlockFamilies() {
        return List.of(
                "Chests / Trapped Chests",
                "Barrels",
                "Shulker Boxes",
                "Create Item Vaults",
                "Create Fluid Tanks",
                "Storage Drawers blocks and Drawer Controller networks",
                "Functional Storage drawers and controller-linked storage",
                "Sophisticated Storage blocks and controller-linked storage",
                "Placed Sophisticated Backpacks",
                "Iron Chests",
                "Mekanism storage blocks and supported multiblock tank storage",
                "Tom's Storage connector/interface/proxy endpoints",
                "Refined Storage 2 Grids / Storage Monitors / Interfaces / Exporters / Importers",
                "Applied Energistics 2 ME Interfaces / ME Chests / Cable Bus import-export parts"
        );
    }

    private static boolean sameRoot(CargoStorageRootResolver.StorageRoot left, CargoStorageRootResolver.StorageRoot right) {
        return left.kind().equals(right.kind()) && left.discriminator().equals(right.discriminator());
    }

    private static boolean isSupportedBlockEntity(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity
                || blockEntity instanceof BarrelBlockEntity
                || blockEntity instanceof ShulkerBoxBlockEntity) {
            return true;
        }

        String className = blockEntity.getClass().getName();
        if (className.equals("com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity")
                || className.equals("com.simibubi.create.content.fluids.tank.FluidTankBlockEntity")
                || className.startsWith("com.jaquadro.minecraft.storagedrawers.block.tile.")
                || className.startsWith("com.buuz135.functionalstorage.block.tile.")
                || className.startsWith("net.p3pp3rf1y.sophisticatedstorage.block.")
                || className.equals("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity")
                || className.startsWith("com.progwml6.ironchest")
                || className.startsWith("com.tom.storagemod.block.entity.")
                || className.startsWith("mekanism.")) {
            return true;
        }

        if (className.equals("com.refinedmods.refinedstorage.common.storagemonitor.StorageMonitorBlockEntity")
                || className.equals("com.refinedmods.refinedstorage.common.iface.InterfaceBlockEntity")
                || isClassOrSuperclass(blockEntity, "com.refinedmods.refinedstorage.common.exporter.AbstractExporterBlockEntity")
                || isClassOrSuperclass(blockEntity, "com.refinedmods.refinedstorage.common.importer.AbstractImporterBlockEntity")
                || className.startsWith("com.refinedmods.refinedstorage.common.grid.")) {
            return true;
        }

        return className.equals("appeng.blockentity.misc.InterfaceBlockEntity")
                || className.equals("appeng.blockentity.storage.MEChestBlockEntity")
                || (className.equals("appeng.blockentity.networking.CableBusBlockEntity")
                && CustomCargoEndpointSupport.detect(blockEntity.getLevel(), blockEntity.getBlockPos()).hasAnyStorage());
    }

    private static boolean isClassOrSuperclass(BlockEntity blockEntity, String className) {
        for (Class<?> current = blockEntity.getClass(); current != null; current = current.getSuperclass()) {
            if (className.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }
}

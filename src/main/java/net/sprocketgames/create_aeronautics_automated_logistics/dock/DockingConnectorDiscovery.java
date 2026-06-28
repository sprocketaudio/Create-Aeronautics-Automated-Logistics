package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public final class DockingConnectorDiscovery {
    private static final ResourceLocation SIMULATED_DOCKING_CONNECTOR = ResourceLocation.fromNamespaceAndPath(
            "simulated",
            "docking_connector"
    );

    private DockingConnectorDiscovery() {
    }

    public static boolean isDock(ServerLevel level, BlockPos pos) {
        if (dockingConnector(level, pos).isPresent()) {
            return true;
        }
        Block block = level.getBlockState(pos).getBlock();
        return SIMULATED_DOCKING_CONNECTOR.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static Optional<DockingConnectorBlockEntity> dockingConnector(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DockingConnectorBlockEntity) {
            return Optional.of((DockingConnectorBlockEntity) blockEntity);
        }
        return Optional.empty();
    }

    public static boolean isLockedPair(ServerLevel level, BlockPos first, BlockPos second) {
        Optional<DockingConnectorBlockEntity> firstConnector = dockingConnector(level, first);
        Optional<DockingConnectorBlockEntity> secondConnector = dockingConnector(level, second);
        if (firstConnector.isEmpty() || secondConnector.isEmpty()) {
            return false;
        }

        DockingConnectorBlockEntity firstDock = firstConnector.get();
        DockingConnectorBlockEntity secondDock = secondConnector.get();
        boolean firstPointsAtSecond = second.equals(firstDock.otherConnectorPosition);
        boolean secondPointsAtFirst = first.equals(secondDock.otherConnectorPosition);
        return firstPointsAtSecond
                && secondPointsAtFirst
                && firstDock.isLocked()
                && secondDock.isLocked();
    }

    public static boolean isExpectedPairExtended(ServerLevel level, BlockPos first, BlockPos second) {
        Optional<DockingConnectorBlockEntity> firstConnector = dockingConnector(level, first);
        Optional<DockingConnectorBlockEntity> secondConnector = dockingConnector(level, second);
        if (firstConnector.isEmpty() || secondConnector.isEmpty()) {
            return false;
        }

        DockingConnectorBlockEntity firstDock = firstConnector.get();
        DockingConnectorBlockEntity secondDock = secondConnector.get();
        boolean firstPointsAtSecond = second.equals(firstDock.otherConnectorPosition);
        boolean secondPointsAtFirst = first.equals(secondDock.otherConnectorPosition);
        return firstPointsAtSecond
                && secondPointsAtFirst
                && firstDock.isExtended()
                && secondDock.isExtended()
                && firstDock.isFeetExtended()
                && secondDock.isFeetExtended();
    }

    public static String lockDiagnostic(ServerLevel level, BlockPos first, BlockPos second) {
        Optional<DockingConnectorBlockEntity> firstConnector = dockingConnector(level, first);
        Optional<DockingConnectorBlockEntity> secondConnector = dockingConnector(level, second);
        return "first=" + connectorDiagnostic(level, first, second, firstConnector)
                + ", second=" + connectorDiagnostic(level, second, first, secondConnector);
    }

    private static String connectorDiagnostic(
            ServerLevel level,
            BlockPos pos,
            BlockPos expectedOther,
            Optional<DockingConnectorBlockEntity> connector
    ) {
        if (connector.isEmpty()) {
            return "{pos=" + pos.toShortString()
                    + ", loaded=" + level.isLoaded(pos)
                    + ", present=false}";
        }
        DockingConnectorBlockEntity dock = connector.get();
        return "{pos=" + pos.toShortString()
                + ", loaded=" + level.isLoaded(pos)
                + ", present=true"
                + ", extended=" + dock.isExtended()
                + ", feetExtended=" + dock.isFeetExtended()
                + ", locked=" + dock.isLocked()
                + ", other=" + (dock.otherConnectorPosition == null
                        ? "none"
                        : dock.otherConnectorPosition.toShortString())
                + ", expectedOther=" + expectedOther.toShortString()
                + ", expectedPair=" + expectedOther.equals(dock.otherConnectorPosition)
                + "}";
    }

    public static DockDiscoveryResult discoverAround(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> candidates = BlockPos.betweenClosedStream(
                        center.offset(-radius, -radius, -radius),
                        center.offset(radius, radius, radius)
                )
                .map(BlockPos::immutable)
                .filter(pos -> isDock(level, pos))
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .toList();
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock discovery at {} radius {} found {} candidate(s): {}",
                center,
                radius,
                candidates.size(),
                candidates
        );
        return fromCandidates(candidates);
    }

    public static DockDiscoveryResult fromCandidates(List<BlockPos> candidates) {
        if (candidates.isEmpty()) {
            return DockDiscoveryResult.missing();
        }
        if (candidates.size() > 1) {
            return DockDiscoveryResult.ambiguous(candidates.size());
        }
        return DockDiscoveryResult.linked(candidates.getFirst());
    }
}

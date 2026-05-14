package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteRotation;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class SableSubLevelVehicleController implements VehicleController {
    private static final double ANGULAR_DAMPING = 0.9D;
    private static final double ROTATION_ALIGNMENT_GAIN = 0.18D;
    private static final double MAX_ALIGNMENT_ANGULAR_CHANGE = 0.06D;
    private static final double HOLD_POSITION_GAIN = 0.45D;
    private static final double HOLD_VELOCITY_DAMPING = 1.1D;
    private static final double MAX_HOLD_LINEAR_CHANGE = 0.18D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final int MOVEMENT_LOG_INTERVAL_TICKS = 20;

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsAutomatedLogistics.MOD_ID,
            "sable_sublevel"
    );

    private final ServerSubLevel subLevel;
    private final BlockPos localControllerPos;
    private final Vec3 localAnchor;
    private int movementLogCooldown;

    private SableSubLevelVehicleController(ServerSubLevel subLevel, BlockPos localControllerPos, Vec3 localAnchor) {
        this.subLevel = subLevel;
        this.localControllerPos = localControllerPos;
        this.localAnchor = localAnchor;
    }

    public static Optional<SableSubLevelVehicleController> resolve(ServerLevel level, VehicleControllerRef controllerRef) {
        CreateAeronauticsAutomatedLogistics.debugLog("Resolving Sable controller from {}", controllerRef);
        Optional<ServerSubLevel> byId = controllerRef.vehicleId().flatMap(vehicleId -> findSubLevel(level, vehicleId));
        if (byId.isPresent()) {
            BlockPos localPos = resolveControllerLocalPos(byId.get(), controllerRef.controllerPos())
                    .orElseGet(() -> BlockPos.containing(0, 0, 0));
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Resolved Sable controller by id {} at local {}",
                    byId.get().getUniqueId(),
                    localPos
            );
            return Optional.of(new SableSubLevelVehicleController(byId.get(), localPos, Vec3.atCenterOf(localPos)));
        }

        Optional<SableSubLevelVehicleController> resolved = controllerRef.controllerPos()
                .flatMap(controllerPos -> findSubLevelContainingController(level, controllerPos, ModBlocks.SHIP_TRANSPONDER.get(), true));
        if (resolved.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugWarn("Could not resolve linked Sable controller from {}", controllerRef);
        }
        return resolved;
    }

    private static Optional<BlockPos> resolveControllerLocalPos(ServerSubLevel subLevel, Optional<BlockPos> storedPos) {
        if (storedPos.isEmpty()) {
            return Optional.empty();
        }

        BlockPos candidate = storedPos.get();
        if (isInsideStorageBounds(subLevel, candidate) && hasControllerBlockAtStorage(subLevel, candidate, ModBlocks.SHIP_TRANSPONDER.get())) {
            return Optional.of(candidate);
        }

        Vec3 worldAnchor = Vec3.atCenterOf(candidate);
        Vec3 localAnchor = subLevel.logicalPose().transformPositionInverse(worldAnchor);
        BlockPos localPos = BlockPos.containing(localAnchor.x, localAnchor.y, localAnchor.z);
        if (isInsideStorageBounds(subLevel, localPos) && hasControllerBlockAtStorage(subLevel, localPos, ModBlocks.SHIP_TRANSPONDER.get())) {
            return Optional.of(localPos);
        }

        return Optional.empty();
    }

    public static Optional<SableSubLevelVehicleController> resolveControllerBlock(
            ServerLevel level,
            BlockPos controllerPos,
            Block expectedBlock
    ) {
        return findSubLevelContainingController(level, controllerPos, expectedBlock, false);
    }

    public List<BlockPos> dockingConnectorPositionsNearController(int radius) {
        ServerLevel level = subLevel.getLevel();
        return BlockPos.betweenClosedStream(
                        localControllerPos.offset(-radius, -radius, -radius),
                        localControllerPos.offset(radius, radius, radius)
                )
                .map(BlockPos::immutable)
                .filter(pos -> isInsideStorageBounds(subLevel, pos))
                .filter(pos -> level.getBlockEntity(pos) instanceof DockingConnectorBlockEntity)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(localControllerPos)))
                .toList();
    }

    @Override
    public ResourceLocation controllerType() {
        return TYPE;
    }

    @Override
    public VehicleControllerRef ref() {
        return new VehicleControllerRef(
                TYPE,
                dimension(),
                Optional.of(subLevel.getUniqueId()),
                Optional.of(localControllerPos)
        );
    }

    @Override
    public ResourceKey<Level> dimension() {
        return subLevel.getLevel().dimension();
    }

    @Override
    public Vec3 position() {
        return subLevel.logicalPose().transformPosition(localAnchor);
    }

    @Override
    public Optional<Float> yaw() {
        Vec3 forward = worldDirectionFromLocalOffset(0.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 1.0E-6D) {
            return Optional.empty();
        }
        return Optional.of((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
    }

    @Override
    public Optional<RouteRotation> routeRotation() {
        return Optional.of(RouteRotation.of(subLevel.logicalPose().orientation()));
    }

    @Override
    public boolean isAssembled() {
        return !subLevel.isRemoved();
    }

    @Override
    public boolean isLoaded(ServerLevel level) {
        return findSubLevel(level, subLevel.getUniqueId()).isPresent();
    }

    @Override
    public boolean isAutomationCapable() {
        return true;
    }

    @Override
    public boolean isControlledByPlayer(UUID playerId) {
        return true;
    }

    @Override
    public VehicleMotionResult moveToward(ServerLevel level, Vec3 targetPosition, double maxSpeedMultiplier) {
        return moveToward(level, targetPosition, maxSpeedMultiplier, 1.2D);
    }

    @Override
    public VehicleMotionResult moveToward(
            ServerLevel level,
            Vec3 targetPosition,
            double maxSpeedMultiplier,
            double desiredSpeedBlocksPerTick
    ) {
        return moveToward(level, targetPosition, Optional.empty(), maxSpeedMultiplier, desiredSpeedBlocksPerTick);
    }

    @Override
    public VehicleMotionResult moveToward(
            ServerLevel level,
            Vec3 targetPosition,
            Optional<RouteRotation> targetRotation,
            double maxSpeedMultiplier,
            double desiredSpeedBlocksPerTick
    ) {
        if (!isLoaded(level) || !isAssembled()) {
            return VehicleMotionResult.failed(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
        }

        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            return VehicleMotionResult.failed(FailureReason.MOVEMENT_FAILURE);
        }

        Vec3 delta = targetPosition.subtract(position());
        double distance = delta.length();
        if (distance < 0.05D && targetRotation.isEmpty() && desiredSpeedBlocksPerTick <= 0.0D) {
            stop(level);
            return VehicleMotionResult.moved();
        }

        double requestedSpeedBlocksPerSecond = Math.max(0.0D, desiredSpeedBlocksPerTick * TICKS_PER_SECOND);
        double maxSpeedBlocksPerSecond = requestedSpeedBlocksPerSecond * maxSpeedMultiplier;
        double maxOneTickTravelSpeed = distance * TICKS_PER_SECOND;
        Vec3 desiredVelocity = distance < 1.0E-6D
                ? Vec3.ZERO
                : delta.normalize().scale(Math.min(maxOneTickTravelSpeed, maxSpeedBlocksPerSecond));
        Vector3dc currentVelocity = handle.getLinearVelocity();
        Vector3dc currentAngularVelocity = handle.getAngularVelocity();
        Vector3d velocityChange = new Vector3d(
                desiredVelocity.x - currentVelocity.x(),
                desiredVelocity.y - currentVelocity.y(),
                desiredVelocity.z - currentVelocity.z()
        );
        Vector3d angularCorrection = computeAngularCorrection(targetRotation, currentAngularVelocity);
        if (movementLogCooldown-- <= 0) {
            movementLogCooldown = MOVEMENT_LOG_INTERVAL_TICKS;
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Sable playback {} local={} distance={} desiredSpeed={} desiredVelocity={} currentVelocity=({}, {}, {}) angularVelocity=({}, {}, {}) targetRotation={} angularCorrection=({}, {}, {})",
                    subLevel.getUniqueId(),
                    localControllerPos,
                    distance,
                    requestedSpeedBlocksPerSecond,
                    desiredVelocity,
                    currentVelocity.x(),
                    currentVelocity.y(),
                    currentVelocity.z(),
                    currentAngularVelocity.x(),
                    currentAngularVelocity.y(),
                    currentAngularVelocity.z(),
                    targetRotation.map(RouteRotation::toString).orElse("none"),
                    angularCorrection.x(),
                    angularCorrection.y(),
                    angularCorrection.z()
            );
        }
        handle.addLinearAndAngularVelocity(velocityChange, angularCorrection);
        return VehicleMotionResult.moved();
    }

    @Override
    public void stop(ServerLevel level) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }
        Vector3dc linearVelocity = handle.getLinearVelocity();
        Vector3dc angularVelocity = handle.getAngularVelocity();
        handle.addLinearAndAngularVelocity(
                new Vector3d(-linearVelocity.x(), -linearVelocity.y(), -linearVelocity.z()),
                new Vector3d(-angularVelocity.x(), -angularVelocity.y(), -angularVelocity.z())
        );
    }

    @Override
    public void hold(ServerLevel level, Vec3 holdPosition, Optional<RouteRotation> holdRotation) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }
        applyHoldStabilization(handle, holdPosition, holdRotation);
    }

    @Override
    public void relocate(ServerLevel level, Vec3 targetPosition, Optional<RouteRotation> targetRotation) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }
        stop(level);
    }

    private void applyHoldStabilization(
            RigidBodyHandle handle,
            Vec3 holdPosition,
            Optional<RouteRotation> holdRotation
    ) {
        Vec3 positionError = holdPosition.subtract(position());
        Vector3dc currentVelocity = handle.getLinearVelocity();
        Vector3dc currentAngularVelocity = handle.getAngularVelocity();

        Vector3d linearCorrection = new Vector3d(
                positionError.x * HOLD_POSITION_GAIN - currentVelocity.x() * HOLD_VELOCITY_DAMPING,
                positionError.y * HOLD_POSITION_GAIN - currentVelocity.y() * HOLD_VELOCITY_DAMPING,
                positionError.z * HOLD_POSITION_GAIN - currentVelocity.z() * HOLD_VELOCITY_DAMPING
        );
        clampMagnitude(linearCorrection, MAX_HOLD_LINEAR_CHANGE);

        Vector3d angularCorrection = computeAngularCorrection(holdRotation, currentAngularVelocity);
        handle.addLinearAndAngularVelocity(linearCorrection, angularCorrection);
    }

    private static Optional<ServerSubLevel> findSubLevel(ServerLevel level, UUID subLevelId) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Optional.empty();
        }
        if (container.getSubLevel(subLevelId) instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return Optional.of(serverSubLevel);
        }
        return Optional.empty();
    }

    private Vector3d computeAngularCorrection(Optional<RouteRotation> targetRotation, Vector3dc currentAngularVelocity) {
        Vector3d angularCorrection = new Vector3d(
                -currentAngularVelocity.x() * ANGULAR_DAMPING,
                -currentAngularVelocity.y() * ANGULAR_DAMPING,
                -currentAngularVelocity.z() * ANGULAR_DAMPING
        );

        if (targetRotation.isPresent()) {
            Quaterniondc currentOrientation = subLevel.logicalPose().orientation();
            Quaterniond desiredOrientation = targetRotation.get().toQuaterniond();
            if (currentOrientation.dot(desiredOrientation) < 0.0D) {
                desiredOrientation.mul(-1.0D);
            }

            Quaterniond deltaQuaternion = new Quaterniond(desiredOrientation)
                    .mul(new Quaterniond(currentOrientation).conjugate())
                    .normalize();
            if (deltaQuaternion.w < 0.0D) {
                deltaQuaternion.mul(-1.0D);
            }
            AxisAngle4d axisAngle = new AxisAngle4d();
            deltaQuaternion.get(axisAngle);
            if (Double.isFinite(axisAngle.angle) && axisAngle.angle > 1.0E-4D) {
                Vector3d rotationError = new Vector3d(axisAngle.x, axisAngle.y, axisAngle.z)
                        .mul(axisAngle.angle * ROTATION_ALIGNMENT_GAIN);
                clampMagnitude(rotationError, MAX_ALIGNMENT_ANGULAR_CHANGE);
                angularCorrection.add(rotationError);
            }
        }

        return angularCorrection;
    }

    private Vec3 worldDirectionFromLocalOffset(double x, double y, double z) {
        Vec3 worldOrigin = position();
        Vec3 worldOffset = subLevel.logicalPose()
                .transformPosition(localAnchor.add(x, y, z))
                .subtract(worldOrigin);
        if (worldOffset.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return worldOffset.normalize();
    }

    private void clampMagnitude(Vector3d vector, double maxMagnitude) {
        double length = vector.length();
        if (length > maxMagnitude && length > 1.0E-9D) {
            vector.mul(maxMagnitude / length);
        }
    }

    public static Optional<SableSubLevelVehicleController> resolveTrackedPlayer(ServerPlayer player) {
        SubLevel tracked = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        if (!(tracked instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }

        Vec3 localAnchor = subLevel.logicalPose().transformPositionInverse(player.position());
        BlockPos localPos = BlockPos.containing(localAnchor.x, localAnchor.y, localAnchor.z);
        return Optional.of(new SableSubLevelVehicleController(subLevel, localPos, localAnchor));
    }

    public static Optional<UUID> subLevelIdAt(ServerLevel level, BlockPos pos) {
        SubLevel containing = Sable.HELPER.getContaining(level, pos);
        if (!(containing instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(subLevel.getUniqueId());
    }

    private static Optional<SableSubLevelVehicleController> findSubLevelContainingController(
            ServerLevel level,
            BlockPos controllerPos,
            Block expectedBlock,
            boolean logFailures
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugWarn("No Sable sublevel container while resolving controller at {}", controllerPos);
            }
            return Optional.empty();
        }

        if (logFailures) {
            CreateAeronauticsAutomatedLogistics.debugLog(
                    "Searching {} Sable sublevels for controller at {}",
                    container.getAllSubLevels().size(),
                    controllerPos
            );
        }
        Optional<SableSubLevelVehicleController> bySableHelper = findBySableHelper(level, controllerPos, expectedBlock, logFailures);
        if (bySableHelper.isPresent()) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugLog("Resolved controller at {} using Sable helper", controllerPos);
            }
            return bySableHelper;
        }

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }

            Optional<SableSubLevelVehicleController> byPlotStoragePos = findByPlotStoragePos(subLevel, controllerPos, expectedBlock);
            if (byPlotStoragePos.isPresent()) {
                if (logFailures) {
                    CreateAeronauticsAutomatedLogistics.debugLog(
                            "Resolved controller at {} using plot storage in Sable sublevel {}",
                            controllerPos,
                            subLevel.getUniqueId()
                    );
                }
                return byPlotStoragePos;
            }

            Optional<SableSubLevelVehicleController> byWorldPos = findByWorldPos(subLevel, controllerPos, expectedBlock);
            if (byWorldPos.isPresent()) {
                if (logFailures) {
                    CreateAeronauticsAutomatedLogistics.debugLog(
                            "Resolved controller at {} using world position in Sable sublevel {}",
                            controllerPos,
                            subLevel.getUniqueId()
                    );
                }
                return byWorldPos;
            }
        }

        return Optional.empty();
    }

    private static Optional<SableSubLevelVehicleController> findBySableHelper(
            ServerLevel level,
            BlockPos controllerPos,
            Block expectedBlock,
            boolean logFailures
    ) {
        SubLevel containing = Sable.HELPER.getContaining(level, controllerPos);
        if (!(containing instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugLog("Sable helper did not find a sublevel containing {}", controllerPos);
            }
            return Optional.empty();
        }

        if (!isInsideStorageBounds(subLevel, controllerPos)) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugLog(
                        "Sable helper found sublevel {} for {}, but the controller was outside plot bounds {}",
                        subLevel.getUniqueId(),
                        controllerPos,
                        subLevel.getPlot().getBoundingBox()
                );
            }
            return Optional.empty();
        }

        if (!hasControllerBlockAtStorage(subLevel, controllerPos, expectedBlock)) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugWarn(
                        "Using linked Sable sublevel {} at {} even though the controller block was not readable at storage position {}",
                        subLevel.getUniqueId(),
                        controllerPos,
                        controllerPos
                );
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(new SableSubLevelVehicleController(subLevel, controllerPos, Vec3.atCenterOf(controllerPos)));
    }

    private static Optional<SableSubLevelVehicleController> findByPlotStoragePos(
            ServerSubLevel subLevel,
            BlockPos controllerPos,
            Block expectedBlock
    ) {
        if (!subLevel.getPlot().contains(controllerPos.getX(), controllerPos.getZ())) {
            return Optional.empty();
        }

        if (isInsideStorageBounds(subLevel, controllerPos) && hasControllerBlockAtStorage(subLevel, controllerPos, expectedBlock)) {
            return Optional.of(new SableSubLevelVehicleController(subLevel, controllerPos, Vec3.atCenterOf(controllerPos)));
        }

        return Optional.empty();
    }

    private static Optional<SableSubLevelVehicleController> findByWorldPos(
            ServerSubLevel subLevel,
            BlockPos controllerPos,
            Block expectedBlock
    ) {
        Vec3 globalAnchor = Vec3.atCenterOf(controllerPos);
        if (!subLevel.boundingBox().contains(globalAnchor.x, globalAnchor.y, globalAnchor.z)) {
            return Optional.empty();
        }

        Vec3 localAnchor = subLevel.logicalPose().transformPositionInverse(globalAnchor);
        BlockPos localPos = BlockPos.containing(localAnchor.x, localAnchor.y, localAnchor.z);
        if (isInsideStorageBounds(subLevel, localPos) && hasControllerBlockAtStorage(subLevel, localPos, expectedBlock)) {
            return Optional.of(new SableSubLevelVehicleController(subLevel, localPos, localAnchor));
        }

        return Optional.empty();
    }

    private static boolean isInsideStorageBounds(ServerSubLevel subLevel, BlockPos pos) {
        return subLevel.getPlot().getBoundingBox().contains(pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean hasControllerBlockAtStorage(ServerSubLevel subLevel, BlockPos pos, Block expectedBlock) {
        try {
            ServerLevel level = subLevel.getLevel();
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                return false;
            }
            return level
                    .getBlockState(pos)
                    .is(expectedBlock);
        } catch (RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Failed to inspect Sable sublevel {} at storage controller position {}",
                    subLevel.getUniqueId(),
                    pos,
                    exception
            );
            return false;
        }
    }
}

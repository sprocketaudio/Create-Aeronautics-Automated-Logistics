package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
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
    private static final double ANGULAR_DAMPING = 0.75D;
    private static final double ROTATION_ALIGNMENT_GAIN = 0.85D;
    private static final double MAX_ALIGNMENT_ANGULAR_CHANGE = 0.55D;
    private static final double HOLD_HORIZONTAL_POSITION_GAIN = 0.45D;
    private static final double HOLD_HORIZONTAL_VELOCITY_DAMPING = 1.1D;
    private static final double MAX_HOLD_HORIZONTAL_CHANGE = 0.18D;
    private static final double HOLD_VERTICAL_POSITION_GAIN = 1.8D;
    private static final double HOLD_VERTICAL_VELOCITY_DAMPING = 2.1D;
    private static final double MAX_HOLD_VERTICAL_CHANGE = 0.7D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final int MOVEMENT_LOG_INTERVAL_TICKS = 20;
    private static final long RESOLVE_MISS_LOG_HEARTBEAT_TICKS = 1200L;
    private static final Map<ServerLevel, Map<ResolveMissLogKey, Long>> RESOLVE_MISS_LOGS = new WeakHashMap<>();

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
        Optional<ServerSubLevel> byId = controllerRef.vehicleId().flatMap(vehicleId -> findSubLevel(level, vehicleId));
        if (byId.isPresent()) {
            BlockPos localPos = resolveControllerLocalPos(byId.get(), controllerRef.controllerPos())
                    .orElseGet(() -> BlockPos.containing(0, 0, 0));
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Resolved Sable controller by id {} at storedLocal={} worldAnchor={}",
                    byId.get().getUniqueId(),
                    localPos,
                    byId.get().logicalPose().transformPosition(Vec3.atCenterOf(localPos))
            );
            return Optional.of(new SableSubLevelVehicleController(byId.get(), localPos, Vec3.atCenterOf(localPos)));
        }

        Optional<SableSubLevelVehicleController> resolved = controllerRef.controllerPos()
                .flatMap(controllerPos -> findSubLevelContainingController(level, controllerPos, ModBlocks.SHIP_TRANSPONDER.get(), false));
        if (resolved.isEmpty() && shouldLogResolveMiss(level, controllerRef)) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Could not resolve linked Sable controller from {}. storedLocalController={} vehicleId={}",
                    controllerRef,
                    controllerRef.controllerPos().map(BlockPos::toShortString).orElse("missing"),
                    controllerRef.vehicleId().map(UUID::toString).orElse("missing")
            );
        }
        return resolved;
    }

    private static boolean shouldLogResolveMiss(ServerLevel level, VehicleControllerRef controllerRef) {
        ResolveMissLogKey key = new ResolveMissLogKey(
                controllerRef.vehicleId(),
                controllerRef.controllerPos(),
                controllerRef.dimension().location().toString()
        );
        long gameTime = level.getGameTime();
        synchronized (RESOLVE_MISS_LOGS) {
            Map<ResolveMissLogKey, Long> levelLogs = RESOLVE_MISS_LOGS.computeIfAbsent(level, ignored -> new HashMap<>());
            Long previous = levelLogs.get(key);
            if (previous != null && gameTime - previous < RESOLVE_MISS_LOG_HEARTBEAT_TICKS) {
                return false;
            }
            levelLogs.put(key, gameTime);
            return true;
        }
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

    public static Optional<SableSubLevelVehicleController> resolveControllerBlock(
            ServerLevel level,
            UUID subLevelId,
            BlockPos localControllerPos
    ) {
        return findSubLevel(level, subLevelId)
                .filter(subLevel -> isInsideStorageBounds(subLevel, localControllerPos))
                .filter(subLevel -> hasControllerBlockAtStorage(subLevel, localControllerPos, ModBlocks.SHIP_TRANSPONDER.get()))
                .map(subLevel -> new SableSubLevelVehicleController(
                        subLevel,
                        localControllerPos.immutable(),
                        Vec3.atCenterOf(localControllerPos)
                ));
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

    public Optional<GlobalSavedSubLevelPointer> lastSerializationPointer() {
        return Optional.ofNullable(subLevel.getLastSerializationPointer());
    }

    public UUID ensureMaterializationTrackingPoint(Optional<UUID> existingTrackingPointId) {
        SubLevelTrackingPointSavedData trackingPoints = SubLevelTrackingPointSavedData.getOrLoad(subLevel.getLevel());
        UUID trackingPointId = existingTrackingPointId.orElseGet(UUID::randomUUID);
        GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
        Vector3d localPoint = new Vector3d(localAnchor.x, localAnchor.y, localAnchor.z);
        Vector3d globalPlaceholder = pointer == null
                ? subLevel.logicalPose().transformPosition(new Vector3d(localPoint))
                : null;
        TrackingPoint expected = new TrackingPoint(
                true,
                subLevel.getUniqueId(),
                pointer,
                localPoint,
                globalPlaceholder
        );
        if (!expected.equals(trackingPoints.getTrackingPoint(trackingPointId))) {
            trackingPoints.setTrackingPoint(trackingPointId, expected);
        }
        return trackingPointId;
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
        Vector3dc currentVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3dc currentAngularVelocity = handle.getAngularVelocity(new Vector3d());
        Vector3d velocityChange = new Vector3d(
                desiredVelocity.x - currentVelocity.x(),
                desiredVelocity.y - currentVelocity.y(),
                desiredVelocity.z - currentVelocity.z()
        );
        Vector3d angularCorrection = computeAngularCorrection(targetRotation, currentAngularVelocity);
        if (movementLogCooldown-- <= 0) {
            movementLogCooldown = MOVEMENT_LOG_INTERVAL_TICKS;
            CreateAeronauticsAutomatedLogistics.debugVehicle(
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
        Vector3dc linearVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3dc angularVelocity = handle.getAngularVelocity(new Vector3d());
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
        Quaterniond orientation = targetRotation
                .map(RouteRotation::toQuaterniond)
                .orElseGet(() -> new Quaterniond(subLevel.logicalPose().orientation()));
        Vector3d rotatedAnchorOffset = new Vector3d(localAnchor.x, localAnchor.y, localAnchor.z)
                .sub(subLevel.logicalPose().rotationPoint());
        orientation.transform(rotatedAnchorOffset);
        Vector3d posePosition = new Vector3d(
                targetPosition.x - rotatedAnchorOffset.x,
                targetPosition.y - rotatedAnchorOffset.y,
                targetPosition.z - rotatedAnchorOffset.z
        );
        stop(level);
        handle.teleport(posePosition, orientation);
    }

    private void applyHoldStabilization(
            RigidBodyHandle handle,
            Vec3 holdPosition,
            Optional<RouteRotation> holdRotation
    ) {
        Vec3 positionError = holdPosition.subtract(position());
        Vector3dc currentVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3dc currentAngularVelocity = handle.getAngularVelocity(new Vector3d());

        Vector3d horizontalCorrection = new Vector3d(
                positionError.x * HOLD_HORIZONTAL_POSITION_GAIN - currentVelocity.x() * HOLD_HORIZONTAL_VELOCITY_DAMPING,
                0.0D,
                positionError.z * HOLD_HORIZONTAL_POSITION_GAIN - currentVelocity.z() * HOLD_HORIZONTAL_VELOCITY_DAMPING
        );
        clampMagnitude(horizontalCorrection, MAX_HOLD_HORIZONTAL_CHANGE);
        double verticalCorrection = clampAbs(
                positionError.y * HOLD_VERTICAL_POSITION_GAIN - currentVelocity.y() * HOLD_VERTICAL_VELOCITY_DAMPING,
                MAX_HOLD_VERTICAL_CHANGE
        );

        Vector3d linearCorrection = new Vector3d(
                horizontalCorrection.x(),
                verticalCorrection,
                horizontalCorrection.z()
        );

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

    private static double clampAbs(double value, double maxAbs) {
        return Math.max(-maxAbs, Math.min(maxAbs, value));
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
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn("No Sable sublevel container while resolving controller at {}", controllerPos);
            }
            return Optional.empty();
        }

        if (logFailures) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Searching {} Sable sublevels for controller reference {}",
                    container.getAllSubLevels().size(),
                    controllerPos
            );
        }
        Optional<SableSubLevelVehicleController> bySableHelper = findBySableHelper(level, controllerPos, expectedBlock, logFailures);
        if (bySableHelper.isPresent()) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugVehicle("Resolved controller at {} using Sable helper", controllerPos);
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
                    CreateAeronauticsAutomatedLogistics.debugVehicle(
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
                    CreateAeronauticsAutomatedLogistics.debugVehicle(
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
                CreateAeronauticsAutomatedLogistics.debugVehicle("Sable helper did not find a sublevel containing {}", controllerPos);
            }
            return Optional.empty();
        }

        if (!isInsideStorageBounds(subLevel, controllerPos)) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugVehicle(
                        "Sable helper found sublevel {} for storedLocalController={}, but the controller was outside plot bounds {}",
                        subLevel.getUniqueId(),
                        controllerPos,
                        subLevel.getPlot().getBoundingBox()
                );
            }
            return Optional.empty();
        }

        if (!hasControllerBlockAtStorage(subLevel, controllerPos, expectedBlock)) {
            if (logFailures) {
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                        "Using linked Sable sublevel {} at storedLocalController={} even though the controller block was not readable at that storage position",
                        subLevel.getUniqueId(),
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

    private record ResolveMissLogKey(
            Optional<UUID> vehicleId,
            Optional<BlockPos> controllerPos,
            String dimension
    ) {
    }
}

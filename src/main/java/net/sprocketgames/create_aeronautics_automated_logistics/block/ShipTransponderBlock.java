package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.Containers;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteBlockBreakProtection;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ScheduleRouteCleanup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShipTransponderBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<ShipTransponderBlock> CODEC = simpleCodec(ShipTransponderBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SHAPE = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 12.0D, 14.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(2.0D, 0.0D, 1.0D, 14.0D, 12.0D, 15.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 12.0D, 14.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(2.0D, 0.0D, 1.0D, 14.0D, 12.0D, 15.0D);

    public ShipTransponderBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(POWERED, false)
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(POWERED, false)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        RouteBlockBreakProtection.warnIfBlocked(
                level,
                player,
                Component.translatable("message.create_aeronautics_automated_logistics.transponder.break_requires_crouch")
        );
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (RouteBlockBreakProtection.shouldBlockBreak(player)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof ShipTransponderBlockEntity transponder)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            if (placer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                transponder.setOwner(serverPlayer);
            }
            transponder.refreshRuntimeShip(serverLevel);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ShipTransponderBlockEntity transponder) {
            if (!serverPlayer.isSpectator() && DockLinkInteractionService.cancelPendingIfSource(serverPlayer, pos)) {
                return InteractionResult.CONSUME;
            }
            if (!serverPlayer.isSpectator() && CargoLinkInteractionService.cancelPendingIfSource(serverPlayer, pos)) {
                return InteractionResult.CONSUME;
            }
            transponder.refreshRuntimeShip((ServerLevel) level);
            transponder.refreshShipDockLink((ServerLevel) level);
            AutomatedLogisticsServices.SCHEDULES.reconcileRuntimeStatus((ServerLevel) level, transponder);
            if (!AutomatedLogisticsServices.SCHEDULES.hasActiveRuntime((ServerLevel) level, transponder.transponderId())) {
                AutomatedLogisticsServices.SCHEDULES.reconcileRuntimeStatus((ServerLevel) level, transponder);
            }
            ShipTransponderMenu.InitialRecordingState recordingState =
                    ShipTransponderMenu.resolveInitialRecordingState(serverPlayer, transponder, false);
            ShipTransponderMenu statusMenu = new ShipTransponderMenu(
                    0,
                    serverPlayer.getInventory(),
                    pos,
                    recordingState.recordingMode(),
                    recordingState.recordingSessionActive(),
                    recordingState.appendToSchedule(),
                    transponder.recordingDestinationStationId(),
                    transponder.runtimeStatus(),
                    transponder.dockOutputActive(),
                    transponder.hasOwnedStops(),
                    transponder.ownedSchedule(),
                    transponder.linkedCargoRevision(),
                    transponder.linkedCargoSummary(),
                    transponder.linkedCargo(),
                    AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId()),
                    AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId()),
                    ShipTransponderMenu.StatusSnapshot.idle()
            );
            ShipTransponderMenu.StatusSnapshot statusSnapshot = statusMenu.buildStatusSnapshot(serverPlayer);
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder openMenu id={} pos={} player={} spectator={} runtimeStatus={} scheduleActive={} scheduleHeld={} dockOutput={} hasOwnedStops={} ownedStopsCount={} linkedCargoCount={} cargoSummary={} snapshotText='{}' snapshotColor={}",
                    transponder.transponderId(),
                    pos,
                    serverPlayer.getName().getString(),
                    serverPlayer.isSpectator(),
                    transponder.runtimeStatus(),
                    transponder.scheduleActive(),
                    transponder.scheduleHeld(),
                    transponder.dockOutputActive(),
                    transponder.hasOwnedStops(),
                    transponder.ownedSchedule().entries().size(),
                    transponder.linkedCargo().size(),
                    transponder.linkedCargoSummary(),
                    statusSnapshot.text(),
                    Integer.toHexString(statusSnapshot.color())
            );
            serverPlayer.openMenu(transponder, buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeBoolean(recordingState.recordingMode());
                buffer.writeBoolean(recordingState.recordingSessionActive());
                buffer.writeBoolean(recordingState.appendToSchedule());
                buffer.writeBoolean(transponder.recordingDestinationStationId().isPresent());
                transponder.recordingDestinationStationId().ifPresent(buffer::writeUUID);
                buffer.writeEnum(transponder.runtimeStatus());
                buffer.writeBoolean(transponder.dockOutputActive());
                buffer.writeBoolean(transponder.hasOwnedStops());
                buffer.writeNbt(AirshipScheduleNbtSerializer.write(transponder.ownedSchedule()));
                ShipTransponderMenu.writeCargoRevision(buffer, transponder.linkedCargoRevision());
                ShipTransponderMenu.writeCargoSummary(buffer, transponder.linkedCargoSummary());
                ShipTransponderMenu.writeLinkedCargoEntries(buffer, transponder.linkedCargo());
                ShipTransponderMenu.writeCargoFailureContext(
                        buffer,
                        AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId())
                );
                ShipTransponderMenu.writePlaybackFailure(
                        buffer,
                        AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId())
                );
                ShipTransponderMenu.writeStatusSnapshot(buffer, statusSnapshot);
            });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipTransponderBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.SHIP_TRANSPONDER.get(),
                ShipTransponderBlockEntity::serverTick
        );
    }

    @Override
    protected RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        if (level.getBlockEntity(pos) instanceof ShipTransponderBlockEntity transponder && transponder.dockOutputActive()) {
            return 15;
        }
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ShipTransponderBlockEntity transponder) {
            if (level.isClientSide) {
                LogisticsClientOverlays.clearFlightPathIfPreviewingTransponder(pos);
                LogisticsClientOverlays.clearFlightPathIfPreviewingTransponderRoutes(transponder.transponderId());
                LogisticsClientOverlays.clearShipTransponderHighlightIfMatches(pos);
                LogisticsClientOverlays.clearDockIfMatches(transponder.shipDockPos());
                List<List<BlockPos>> cargoGroups = CargoLinkSupport.expandPreviewPositionGroups(level, pos, 6, transponder.linkedCargo());
                if (cargoGroups.isEmpty()) {
                    cargoGroups = transponder.linkedCargo().stream()
                            .map(entry -> java.util.List.of(entry.pos()))
                            .toList();
                }
                LogisticsClientOverlays.clearCargoIfMatches(cargoGroups);
            }
            if (!transponder.installedScheduleStack().isEmpty()) {
                Containers.dropItemStack(
                        level,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.5D,
                        pos.getZ() + 0.5D,
                        transponder.installedScheduleStack().copy()
                );
                transponder.clearContent();
            }
            if (level instanceof ServerLevel serverLevel) {
                transponder.refreshRuntimeShip(serverLevel);
                java.util.Optional<net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot> teardownSnapshot =
                        ShipTransponderRegistry.snapshot(transponder.transponderId());
                java.util.Optional<net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef> teardownControllerRef =
                        transponder.controllerRef(serverLevel).or(() -> teardownSnapshot.flatMap(net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot::controllerRef));
                java.util.Optional<java.util.UUID> teardownRuntimeShipId =
                        transponder.runtimeShipId().or(() -> teardownSnapshot.flatMap(net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot::runtimeShipId));
                AutomatedLogisticsServices.SCHEDULES.stop(serverLevel, transponder.transponderId());
                AutomatedLogisticsServices.PLAYBACK.stopLinkedPlaybacks(
                        serverLevel,
                        teardownControllerRef,
                        teardownRuntimeShipId,
                        FailureReason.NONE
                );
                teardownControllerRef.ifPresent(controllerRef ->
                        AutomatedLogisticsServices.RECORDING.cancelRecordingForController(serverLevel, controllerRef));
                transponder.setRecordingDestinationStationId(java.util.Optional.empty());
                transponder.setDockOutputActive(false);
                transponder.setRuntimeStatus(RouteStatus.IDLE);
                ScheduleRouteCleanup.removeRoutesForDeletedTransponder(serverLevel, transponder.transponderId());
                ShipTransponderRegistry.unregister(transponder.transponderId());
                IdentityDirectorySavedData.removeShip(serverLevel.getServer(), transponder.transponderId());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.network.chat.Component;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipStationMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteBlockBreakProtection;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ScheduleRouteCleanup;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import org.jetbrains.annotations.Nullable;

public class AirshipStationBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<AirshipStationBlock> CODEC = simpleCodec(AirshipStationBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SHAPE = Block.box(1.0D, 0.0D, 0.0D, 15.0D, 14.0D, 15.0D);
    private static final VoxelShape EAST_SHAPE = rotateShape(NORTH_SHAPE, net.minecraft.core.Direction.EAST);
    private static final VoxelShape SOUTH_SHAPE = rotateShape(NORTH_SHAPE, net.minecraft.core.Direction.SOUTH);
    private static final VoxelShape WEST_SHAPE = rotateShape(NORTH_SHAPE, net.minecraft.core.Direction.WEST);

    public AirshipStationBlock(BlockBehaviour.Properties properties) {
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
                Component.translatable("message.create_aeronautics_automated_logistics.station.break_requires_crouch")
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (DockLinkInteractionService.cancelPendingIfSource(serverPlayer, pos)) {
            return InteractionResult.CONSUME;
        }
        if (CargoLinkInteractionService.cancelPendingIfSource(serverPlayer, pos)) {
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(pos) instanceof AirshipStationBlockEntity station)) {
            return InteractionResult.CONSUME;
        }
        CreateAeronauticsAutomatedLogistics.debugLog(
                "Station openMenu id={} pos={} linkedCargoCount={} cargoSummary={}",
                station.stationId(),
                pos,
                station.linkedCargo().size(),
                station.linkedCargoSummary()
        );
        serverPlayer.openMenu(station, buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(station.selectedTransponderId().isPresent());
            station.selectedTransponderId().ifPresent(buffer::writeUUID);
            buffer.writeUtf(station.selectedShipName(), 64);
            ShipTransponderMenu.writeCargoRevision(buffer, station.linkedCargoRevision());
            ShipTransponderMenu.writeCargoSummary(buffer, station.linkedCargoSummary());
            ShipTransponderMenu.writeLinkedCargoEntries(buffer, station.linkedCargo());
            ShipTransponderMenu.writeCargoFailureContext(
                    buffer,
                    station.selectedTransponderId().flatMap(AutomatedLogisticsServices.SCHEDULES::lastCargoFailureContext)
            );
            AirshipStationMenu.writeRouteChoiceSummaries(
                    buffer,
                    AirshipStationMenu.buildRouteChoiceSummaries(serverPlayer, station)
            );
            AirshipStationMenu.writeClientState(
                    buffer,
                    AirshipStationMenu.buildClientState(serverPlayer, station)
            );
        });
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AirshipStationBlockEntity station) {
            station.setOwner(serverPlayer);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirshipStationBlockEntity(pos, state);
    }

    @Override
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
                ModBlockEntities.AIRSHIP_STATION.get(),
                AirshipStationBlockEntity::serverTick
        );
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        if (level.getBlockEntity(pos) instanceof AirshipStationBlockEntity station && station.dockOutputActive()) {
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
                && level.getBlockEntity(pos) instanceof AirshipStationBlockEntity station
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ScheduleRouteCleanup.removeRoutesForDeletedStation(serverLevel, station.stationId());
            IdentityDirectorySavedData.removeStation(serverLevel.getServer(), station.stationId());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static VoxelShape rotateShape(VoxelShape shape, net.minecraft.core.Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(16.0D - 15.0D, 0.0D, 1.0D, 16.0D - 0.0D, 14.0D, 15.0D);
            case SOUTH -> Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 16.0D);
            case WEST -> Block.box(0.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
            default -> shape;
        };
    }
}

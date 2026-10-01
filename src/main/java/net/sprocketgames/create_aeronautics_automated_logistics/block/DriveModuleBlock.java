package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;
import org.jetbrains.annotations.Nullable;

/**
 * A floor-mounted endpoint for one future Drive Module redstone contract.
 * Linking and redstone output are intentionally added in later 0.7 phases.
 */
public class DriveModuleBlock extends BaseEntityBlock {
    public static final MapCodec<DriveModuleBlock> CODEC = simpleCodec(DriveModuleBlock::new);
    public static final DirectionProperty FACING = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private static final VoxelShape SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 5.0D, 15.0D);

    public DriveModuleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Drive Modules follow repeater placement: the output points away from
        // the player placing the block.
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos supportPos = pos.below();
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
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
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (direction != state.getValue(FACING)) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof DriveModuleBlockEntity module
                ? module.outputSignal()
                : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DriveModuleBlockEntity module ? module : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DriveModuleBlockEntity module) {
            if (!TransponderPermissionService.ensureCanControl(serverPlayer, module.ownerId())) {
                return InteractionResult.CONSUME;
            }
            serverPlayer.openMenu(module, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DriveModuleBlockEntity module) {
            module.setOwner(serverPlayer);
            String itemName = net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames.itemName(stack);
            if (!itemName.isBlank() && !itemName.equals(stack.getItem().getDescription().getString())) {
                module.setModuleName(itemName);
            }
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DriveModuleBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.DRIVE_MODULE.get(), DriveModuleBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
}

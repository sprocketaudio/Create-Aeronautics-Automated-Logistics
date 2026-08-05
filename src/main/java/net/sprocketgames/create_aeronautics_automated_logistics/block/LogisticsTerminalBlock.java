package net.sprocketgames.create_aeronautics_automated_logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.LogisticsTerminalBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapProjectionService;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncIdentityDirectoryPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncShipMapMarkersPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.service.LogisticsTerminalPermissionService;
import org.jetbrains.annotations.Nullable;

public class LogisticsTerminalBlock extends BaseEntityBlock {
    public static final MapCodec<LogisticsTerminalBlock> CODEC = simpleCodec(LogisticsTerminalBlock::new);
    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 11.0D, 16.0D);

    public LogisticsTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LogisticsTerminalBlockEntity terminal ? terminal : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LogisticsTerminalBlockEntity terminal) {
            if (!LogisticsTerminalPermissionService.ensureCanOpen(serverPlayer, terminal)) {
                return InteractionResult.CONSUME;
            }
            SyncIdentityDirectoryPayload.sendTo(serverPlayer);
            SyncLogisticsTerminalRoutesPayload.sendTo(serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new SyncShipMapMarkersPayload(ShipMapProjectionService.snapshotsFor(serverPlayer)));
            serverPlayer.openMenu(terminal, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LogisticsTerminalBlockEntity terminal) {
            terminal.setOwner(serverPlayer);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsTerminalBlockEntity(pos, state);
    }
}

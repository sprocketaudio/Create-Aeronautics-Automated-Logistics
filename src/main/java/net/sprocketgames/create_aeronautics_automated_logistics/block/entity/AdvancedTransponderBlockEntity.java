package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;

public class AdvancedTransponderBlockEntity extends ShipTransponderBlockEntity {
    public AdvancedTransponderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ADVANCED_TRANSPONDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AdvancedTransponderBlockEntity transponder) {
        ShipTransponderBlockEntity.serverTick(level, pos, state, transponder);
    }

    @Override
    protected Block controllerBlock() {
        return ModBlocks.ADVANCED_TRANSPONDER.get();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.advanced_transponder.title");
    }

}

package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;

public class DriveModuleMenu extends AbstractContainerMenu {
    private final BlockPos modulePos;
    private final Level level;

    public DriveModuleMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer == null || buffer.readableBytes() < Long.BYTES ? BlockPos.ZERO : buffer.readBlockPos());
    }

    public DriveModuleMenu(int containerId, Inventory playerInventory, BlockPos modulePos) {
        super(ModMenus.DRIVE_MODULE.get(), containerId);
        this.level = playerInventory.player.level();
        this.modulePos = modulePos.immutable();
    }

    public BlockPos modulePos() {
        return modulePos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return false;
        }
        if (!player.isSpectator() && !player.canInteractWithBlock(modulePos, 8.0)) {
            return false;
        }
        BlockState state = level.getBlockState(modulePos);
        return state.is(ModBlocks.DRIVE_MODULE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.map.ShipMapProjectionService;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncIdentityDirectoryPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncLogisticsTerminalRoutesPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncShipMapMarkersPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;

public class LogisticsTerminalMenu extends AbstractContainerMenu {
    private final BlockPos terminalPos;
    private final Level level;

    public LogisticsTerminalMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer == null || buffer.readableBytes() < Long.BYTES ? BlockPos.ZERO : buffer.readBlockPos());
    }

    public LogisticsTerminalMenu(int containerId, Inventory playerInventory, BlockPos terminalPos) {
        super(ModMenus.LOGISTICS_TERMINAL.get(), containerId);
        this.level = playerInventory.player.level();
        this.terminalPos = terminalPos.immutable();
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            SyncIdentityDirectoryPayload.sendTo(serverPlayer);
            SyncLogisticsTerminalRoutesPayload.sendTo(serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new SyncShipMapMarkersPayload(ShipMapProjectionService.snapshotsFor(serverPlayer)));
        }
    }

    public BlockPos terminalPos() {
        return terminalPos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return false;
        }
        if (!player.isSpectator() && !player.canInteractWithBlock(terminalPos, 8.0)) {
            return false;
        }
        BlockState state = level.getBlockState(terminalPos);
        return state.is(ModBlocks.LOGISTICS_TERMINAL.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

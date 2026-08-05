package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsTerminalPreviewClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.LogisticsTerminalMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

public class LogisticsTerminalBlockEntity extends BlockEntity implements MenuProvider {
    private static final String OWNER_ID = "ownerId";
    private static final String OWNER_NAME = "ownerName";
    private static final String OWNER_TEAM_ID = "ownerTeamId";

    private Optional<UUID> ownerId = Optional.empty();
    private Optional<UUID> ownerTeamId = Optional.empty();
    private String ownerName = "";

    public LogisticsTerminalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LOGISTICS_TERMINAL.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            LogisticsTerminalPreviewClientState.register(worldPosition);
        } else {
            refreshOwnerTeamId();
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            LogisticsTerminalPreviewClientState.unregister(worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && level.isClientSide) {
            LogisticsTerminalPreviewClientState.register(worldPosition);
        }
    }

    public Optional<UUID> ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public Optional<UUID> ownerTeamId() {
        return ownerTeamId;
    }

    public void setOwner(ServerPlayer player) {
        setOwner(player.getUUID(), player.getGameProfile().getName());
    }

    public void setOwner(UUID ownerId, String ownerName) {
        Optional<UUID> normalizedId = Optional.ofNullable(ownerId);
        Optional<UUID> normalizedTeamId = normalizedId.flatMap(FtbTeamsCompat::teamIdForPlayer);
        String normalizedName = IdentityNames.sanitize(ownerName);
        if (this.ownerId.equals(normalizedId)
                && this.ownerTeamId.equals(normalizedTeamId)
                && this.ownerName.equals(normalizedName)) {
            return;
        }
        this.ownerId = normalizedId;
        this.ownerTeamId = normalizedTeamId;
        this.ownerName = normalizedName;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.logistics_terminal.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LogisticsTerminalMenu(containerId, playerInventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerId.ifPresent(id -> tag.putUUID(OWNER_ID, id));
        ownerTeamId.ifPresent(id -> tag.putUUID(OWNER_TEAM_ID, id));
        tag.putString(OWNER_NAME, ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID(OWNER_ID) ? Optional.of(tag.getUUID(OWNER_ID)) : Optional.empty();
        ownerTeamId = tag.hasUUID(OWNER_TEAM_ID) ? Optional.of(tag.getUUID(OWNER_TEAM_ID)) : Optional.empty();
        ownerName = IdentityNames.sanitize(tag.getString(OWNER_NAME));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void refreshOwnerTeamId() {
        if (level == null || level.isClientSide) {
            return;
        }
        Optional<UUID> resolvedTeamId = ownerId.flatMap(FtbTeamsCompat::teamIdForPlayer);
        if (ownerTeamId.equals(resolvedTeamId)) {
            return;
        }
        ownerTeamId = resolvedTeamId;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}

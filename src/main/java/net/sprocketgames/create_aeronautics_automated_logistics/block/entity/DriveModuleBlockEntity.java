package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveEncodingType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleHealth;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleLink;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleState;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent local state for a Drive Module. Linking and configuration are
 * intentionally stored on the endpoint, never inferred from block faces.
 */
public class DriveModuleBlockEntity extends BlockEntity {
    private static final String MODULE_ID = "moduleId";
    private static final String CONTROLLER_TRANSPONDER_ID = "controllerTransponderId";
    private static final String INTENT = "intent";
    private static final String ENCODING = "encoding";

    private UUID moduleId = UUID.randomUUID();
    private Optional<DriveModuleLink> link = Optional.empty();
    private Optional<DriveModuleConfig> config = Optional.empty();

    public DriveModuleBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.DRIVE_MODULE.get(), pos, blockState);
    }

    public UUID moduleId() {
        return moduleId;
    }

    public DriveModuleState state() {
        return new DriveModuleState(moduleId, link, config, link.isPresent() ? DriveModuleHealth.LINKED : DriveModuleHealth.UNLINKED);
    }

    public void linkTo(UUID controllerTransponderId, DriveModuleConfig config) {
        link = Optional.of(new DriveModuleLink(moduleId, controllerTransponderId));
        this.config = Optional.of(config);
        markUpdated();
    }

    public void unlink() {
        if (link.isEmpty()) {
            return;
        }
        link = Optional.empty();
        config = Optional.empty();
        markUpdated();
    }

    public void setConfiguration(DriveModuleConfig config) {
        if (link.isEmpty()) {
            throw new IllegalStateException("Cannot configure an unlinked Drive Module");
        }
        this.config = Optional.of(config);
        markUpdated();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(MODULE_ID, moduleId);
        link.ifPresent(value -> tag.putUUID(CONTROLLER_TRANSPONDER_ID, value.controllerTransponderId()));
        config.ifPresent(value -> {
            tag.putString(INTENT, value.intent().name());
            tag.putString(ENCODING, value.encoding().name());
        });
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        moduleId = tag.hasUUID(MODULE_ID) ? tag.getUUID(MODULE_ID) : UUID.randomUUID();
        link = tag.hasUUID(CONTROLLER_TRANSPONDER_ID)
                ? Optional.of(new DriveModuleLink(moduleId, tag.getUUID(CONTROLLER_TRANSPONDER_ID)))
                : Optional.empty();
        config = readConfig(tag);
        if (link.isEmpty() != config.isEmpty()) {
            link = Optional.empty();
            config = Optional.empty();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private Optional<DriveModuleConfig> readConfig(CompoundTag tag) {
        if (!tag.contains(INTENT) || !tag.contains(ENCODING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DriveModuleConfig(
                    DriveIntentType.valueOf(tag.getString(INTENT)),
                    DriveEncodingType.valueOf(tag.getString(ENCODING))
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}

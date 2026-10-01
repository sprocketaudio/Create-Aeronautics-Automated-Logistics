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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.DriveModuleBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveEncodingType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleHealth;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleLink;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleState;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.DriveModuleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DriveModuleLinkService;
import org.jetbrains.annotations.Nullable;

public class DriveModuleBlockEntity extends BlockEntity implements MenuProvider {
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final String MODULE_ID = "moduleId";
    private static final String MODULE_NAME = "moduleName";
    private static final String OWNER_ID = "ownerId";
    private static final String OWNER_NAME = "ownerName";
    private static final String OWNER_TEAM_ID = "ownerTeamId";
    private static final String LINK = "link";
    private static final String CONTROLLER_TRANSPONDER_ID = "controllerTransponderId";
    private static final String CONFIG = "config";
    private static final String INTENT = "intent";
    private static final String ENCODING = "encoding";
    private static final String INVERTED = "inverted";
    private static final String HEALTH = "health";

    private UUID moduleId = UUID.randomUUID();
    private String moduleName = IdentityNames.defaultDriveModuleName(moduleId);
    private Optional<UUID> ownerId = Optional.empty();
    private String ownerName = "";
    private Optional<UUID> ownerTeamId = Optional.empty();
    private Optional<DriveModuleLink> link = Optional.empty();
    private Optional<DriveModuleConfig> config = Optional.empty();
    private DriveModuleHealth health = DriveModuleHealth.UNLINKED;
    private int outputSignal;

    public DriveModuleBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.DRIVE_MODULE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DriveModuleBlockEntity module) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % REFRESH_INTERVAL_TICKS == 0L) {
            module.refreshLinkState(serverLevel);
        }
    }

    public UUID moduleId() {
        return moduleId;
    }

    public String moduleName() {
        return moduleName;
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

    public Optional<DriveModuleLink> link() {
        return link;
    }

    public Optional<DriveModuleConfig> config() {
        return config;
    }

    public DriveModuleHealth health() {
        return health;
    }

    public int outputSignal() {
        return outputSignal;
    }

    public void setOutputSignal(int outputSignal) {
        int clampedSignal = Math.clamp(outputSignal, 0, 15);
        if (this.outputSignal == clampedSignal) {
            return;
        }
        this.outputSignal = clampedSignal;
        setChanged();
        updatePoweredState();
        notifyRedstoneNeighbors();
    }

    public DriveModuleState state() {
        return new DriveModuleState(moduleId, link, config, health);
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
        syncClientState();
    }

    public void setModuleName(String moduleName) {
        String sanitized = IdentityNames.sanitize(moduleName);
        this.moduleName = sanitized.isBlank() ? IdentityNames.defaultDriveModuleName(moduleId) : sanitized;
        setChanged();
        syncClientState();
    }

    public void setIntent(DriveIntentType intent) {
        config = Optional.of(new DriveModuleConfig(intent, intent.encoding()));
        setChanged();
        syncClientState();
    }

    public void toggleInverted() {
        if (config.isEmpty() || !config.get().supportsInversion()) {
            return;
        }
        DriveModuleConfig current = config.get();
        config = Optional.of(new DriveModuleConfig(current.intent(), current.encoding(), !current.inverted()));
        setChanged();
        syncClientState();
    }

    public void unlink() {
        if (link.isEmpty() && health == DriveModuleHealth.UNLINKED) {
            return;
        }
        link = Optional.empty();
        health = DriveModuleHealth.UNLINKED;
        setOutputSignal(0);
        DriveModuleLinkService.untrack(this);
        setChanged();
        syncClientState();
    }

    public boolean tryLink(ServerLevel level) {
        if (config.isEmpty()) {
            health = DriveModuleHealth.CONFLICTING_CONFIGURATION;
            setChanged();
            syncClientState();
            return false;
        }
        DriveModuleLinkService.Resolution resolution = DriveModuleLinkService.resolve(level, worldPosition, Optional.empty());
        if (resolution.health() != DriveModuleHealth.LINKED || resolution.resolvedController().isEmpty()) {
            link = Optional.empty();
            health = resolution.health();
            setChanged();
            syncClientState();
            return false;
        }
        link = Optional.of(new DriveModuleLink(moduleId, resolution.resolvedController().get().transponderId()));
        health = DriveModuleHealth.LINKED;
        DriveModuleLinkService.track(this);
        setChanged();
        syncClientState();
        return true;
    }

    public void refreshLinkState(ServerLevel level) {
        DriveModuleHealth previousHealth = health;
        Optional<DriveModuleLink> previousLink = link;
        if (config.isEmpty()) {
            link = Optional.empty();
            health = DriveModuleHealth.UNLINKED;
            setOutputSignal(0);
            DriveModuleLinkService.untrack(this);
            updatePoweredState();
            if (!previousLink.equals(link) || previousHealth != health) {
                setChanged();
                syncClientState();
            }
            return;
        }
        DriveModuleLinkService.Resolution resolution = DriveModuleLinkService.resolve(
                level,
                worldPosition,
                link.map(DriveModuleLink::controllerTransponderId)
        );

        if (link.isPresent()) {
            DriveModuleHealth resolvedHealth = resolution.health();
            if (resolvedHealth == DriveModuleHealth.LINKED && resolution.resolvedController().isPresent()) {
                DriveModuleLink currentLink = link.get();
                UUID resolvedId = resolution.resolvedController().get().transponderId();
                if (!currentLink.controllerTransponderId().equals(resolvedId)) {
                    link = Optional.of(new DriveModuleLink(moduleId, resolvedId));
                }
            }
            health = resolvedHealth;
        } else {
            health = switch (resolution.health()) {
                case LINKED -> DriveModuleHealth.UNLINKED;
                default -> resolution.health();
            };
        }

        updatePoweredState();
        if (health == DriveModuleHealth.LINKED && link.isPresent()) {
            DriveModuleLinkService.track(this);
        } else {
            setOutputSignal(0);
            DriveModuleLinkService.untrack(this);
        }
        if (!previousLink.equals(link) || previousHealth != health) {
            CreateAeronauticsAutomatedLogistics.debugDriveModules(
                    "Drive module {} at {} link refresh: health {} -> {}, link {} -> {}, resolvedVehicle={}, resolvedController={}",
                    moduleId,
                    worldPosition.toShortString(),
                    previousHealth,
                    health,
                    previousLink.map(value -> value.controllerTransponderId().toString()).orElse("none"),
                    link.map(value -> value.controllerTransponderId().toString()).orElse("none"),
                    resolution.vehicleId().map(UUID::toString).orElse("none"),
                    resolution.resolvedController().map(value -> value.transponderId().toString()).orElse("none")
            );
            setChanged();
            syncClientState();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            refreshOwnerTeamId();
            refreshLinkState(serverLevel);
        }
    }

    @Override
    public void setRemoved() {
        DriveModuleLinkService.untrack(this);
        super.setRemoved();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.drive_module.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DriveModuleMenu(containerId, playerInventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(MODULE_ID, moduleId);
        tag.putString(MODULE_NAME, moduleName);
        ownerId.ifPresent(id -> tag.putUUID(OWNER_ID, id));
        ownerTeamId.ifPresent(id -> tag.putUUID(OWNER_TEAM_ID, id));
        tag.putString(OWNER_NAME, ownerName);
        link.ifPresent(currentLink -> {
            CompoundTag linkTag = new CompoundTag();
            linkTag.putUUID(CONTROLLER_TRANSPONDER_ID, currentLink.controllerTransponderId());
            tag.put(LINK, linkTag);
        });
        config.ifPresent(currentConfig -> {
            CompoundTag configTag = new CompoundTag();
            configTag.putString(INTENT, currentConfig.intent().name());
            configTag.putString(ENCODING, currentConfig.encoding().name());
            configTag.putBoolean(INVERTED, currentConfig.inverted());
            tag.put(CONFIG, configTag);
        });
        tag.putString(HEALTH, health.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        moduleId = tag.hasUUID(MODULE_ID) ? tag.getUUID(MODULE_ID) : UUID.randomUUID();
        moduleName = IdentityNames.sanitize(tag.getString(MODULE_NAME));
        if (moduleName.isBlank()) {
            moduleName = IdentityNames.defaultDriveModuleName(moduleId);
        }
        ownerId = tag.hasUUID(OWNER_ID) ? Optional.of(tag.getUUID(OWNER_ID)) : Optional.empty();
        ownerTeamId = tag.hasUUID(OWNER_TEAM_ID) ? Optional.of(tag.getUUID(OWNER_TEAM_ID)) : Optional.empty();
        ownerName = IdentityNames.sanitize(tag.getString(OWNER_NAME));
        link = readLink(tag).map(controllerId -> new DriveModuleLink(moduleId, controllerId));
        config = readConfig(tag);
        if (config.isEmpty()) {
            link = Optional.empty();
            health = DriveModuleHealth.UNLINKED;
        } else {
            health = readHealth(tag).orElse(link.isPresent() ? DriveModuleHealth.CONTROLLER_UNAVAILABLE : DriveModuleHealth.UNLINKED);
        }
        updatePoweredState();
        outputSignal = 0;
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

    private Optional<UUID> readLink(CompoundTag tag) {
        if (!tag.contains(LINK, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag linkTag = tag.getCompound(LINK);
        return linkTag.hasUUID(CONTROLLER_TRANSPONDER_ID) ? Optional.of(linkTag.getUUID(CONTROLLER_TRANSPONDER_ID)) : Optional.empty();
    }

    private Optional<DriveModuleConfig> readConfig(CompoundTag tag) {
        if (!tag.contains(CONFIG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag configTag = tag.getCompound(CONFIG);
        try {
            DriveIntentType intent = DriveIntentType.valueOf(configTag.getString(INTENT));
            DriveEncodingType encoding = DriveEncodingType.valueOf(configTag.getString(ENCODING));
            return Optional.of(new DriveModuleConfig(intent, encoding, configTag.getBoolean(INVERTED)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<DriveModuleHealth> readHealth(CompoundTag tag) {
        try {
            return Optional.of(DriveModuleHealth.valueOf(tag.getString(HEALTH)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
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
        syncClientState();
    }

    private void updatePoweredState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(DriveModuleBlock.POWERED)) {
            return;
        }
        // The block state represents the live redstone output, not merely whether
        // the module is linked. This gives moving Sable sub-levels a normal block
        // state transition to propagate to neighbouring redstone consumers.
        boolean powered = outputSignal > 0;
        if (state.getValue(DriveModuleBlock.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(DriveModuleBlock.POWERED, powered), 3);
        }
    }

    private void syncClientState() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void notifyRedstoneNeighbors() {
        if (level == null || level.isClientSide) {
            return;
        }
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }
}

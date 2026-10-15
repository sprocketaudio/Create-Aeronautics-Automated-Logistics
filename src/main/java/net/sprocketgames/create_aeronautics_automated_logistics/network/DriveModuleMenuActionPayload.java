package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveIntentType;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleHealth;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record DriveModuleMenuActionPayload(BlockPos pos, int action, String value) implements CustomPacketPayload {
    public static final int ACTION_LINK = 0;
    public static final int ACTION_UNLINK = 1;
    public static final int ACTION_SET_INTENT = 2;
    public static final int ACTION_TOGGLE_INVERTED = 3;

    public static final Type<DriveModuleMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "drive_module_menu_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DriveModuleMenuActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(DriveModuleMenuActionPayload::write, DriveModuleMenuActionPayload::read);

    private static DriveModuleMenuActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new DriveModuleMenuActionPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(action);
        buffer.writeUtf(value, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriveModuleMenuActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.distanceToSqr(payload.pos().getX() + 0.5D, payload.pos().getY() + 0.5D, payload.pos().getZ() + 0.5D) > 64.0D) {
            return;
        }
        if (!(player.serverLevel().getBlockEntity(payload.pos()) instanceof DriveModuleBlockEntity module)) {
            return;
        }
        if (!TransponderPermissionService.ensureCanControl(player, module.ownerId())) {
            return;
        }

        switch (payload.action()) {
            case ACTION_LINK -> {
                boolean linked = module.tryLink(player.serverLevel());
                if (linked) {
                    SetMenuActionBarMessagePayload.send(player,
                            Component.translatable("message.create_aeronautics_automated_logistics.drive_module.linked"));
                } else {
                    SetMenuActionBarMessagePayload.send(player, failureMessage(module.health()));
                }
            }
            case ACTION_UNLINK -> {
                module.unlink();
                SetMenuActionBarMessagePayload.send(player,
                        Component.translatable("message.create_aeronautics_automated_logistics.drive_module.unlinked"));
            }
            case ACTION_SET_INTENT -> {
                try {
                    module.setIntent(DriveIntentType.valueOf(payload.value()));
                    module.refreshLinkState(player.serverLevel());
                } catch (IllegalArgumentException ignored) {
                }
            }
            case ACTION_TOGGLE_INVERTED -> module.toggleInverted();
            default -> {
            }
        }
    }

    private static Component failureMessage(DriveModuleHealth health) {
        return switch (health) {
            case NO_ASSEMBLED_VEHICLE -> Component.translatable("message.create_aeronautics_automated_logistics.drive_module.link_failed.no_vehicle");
            case NO_ADVANCED_CONTROLLER -> Component.translatable("message.create_aeronautics_automated_logistics.drive_module.link_failed.no_advanced");
            case MULTIPLE_CONTROLLERS -> Component.translatable("message.create_aeronautics_automated_logistics.drive_module.link_failed.multiple");
            case CONTROLLER_UNAVAILABLE -> Component.translatable("message.create_aeronautics_automated_logistics.drive_module.link_failed.unavailable");
            case WRONG_VEHICLE -> Component.translatable("message.create_aeronautics_automated_logistics.drive_module.link_failed.wrong_vehicle");
            default -> Component.translatable("message.create_aeronautics_automated_logistics.playback.failed", "Link failed");
        };
    }

}

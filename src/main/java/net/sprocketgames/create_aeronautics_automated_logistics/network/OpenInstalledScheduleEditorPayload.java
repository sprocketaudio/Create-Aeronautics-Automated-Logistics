package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record OpenInstalledScheduleEditorPayload(BlockPos transponderPos, boolean returnToRecordingMode) implements CustomPacketPayload {
    public static final Type<OpenInstalledScheduleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "open_installed_schedule_editor")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenInstalledScheduleEditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenInstalledScheduleEditorPayload::write, OpenInstalledScheduleEditorPayload::read);

    private static OpenInstalledScheduleEditorPayload read(RegistryFriendlyByteBuf buffer) {
        return new OpenInstalledScheduleEditorPayload(buffer.readBlockPos(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeBoolean(returnToRecordingMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenInstalledScheduleEditorPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
            return;
        }
        if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
            return;
        }
        AirshipSchedule schedule = transponder.ownedSchedule();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignoredPlayer) -> new AirshipScheduleMenu(
                                containerId,
                                inventory,
                                payload.transponderPos(),
                                payload.returnToRecordingMode()
                        ),
                        net.minecraft.network.chat.Component.literal(schedule.title())
                ),
                buffer -> {
                    buffer.writeBoolean(true);
                    buffer.writeBlockPos(payload.transponderPos());
                    buffer.writeBoolean(payload.returnToRecordingMode());
                }
        );
    }
}

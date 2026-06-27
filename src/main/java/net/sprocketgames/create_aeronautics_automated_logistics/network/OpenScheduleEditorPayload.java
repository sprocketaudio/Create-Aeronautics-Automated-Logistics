package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.UUID;
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
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record OpenScheduleEditorPayload(BlockPos transponderPos, boolean returnToRecordingMode) implements CustomPacketPayload {
    public static final Type<OpenScheduleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "open_schedule_editor")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenScheduleEditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenScheduleEditorPayload::write, OpenScheduleEditorPayload::read);

    private static OpenScheduleEditorPayload read(RegistryFriendlyByteBuf buffer) {
        return new OpenScheduleEditorPayload(buffer.readBlockPos(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeBoolean(returnToRecordingMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenScheduleEditorPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "OpenScheduleEditor rejected missing transponder pos={} player={} spectator={}",
                    payload.transponderPos(),
                    player.getName().getString(),
                    player.isSpectator()
            );
            return;
        }
        if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "OpenScheduleEditor rejected permission pos={} id={} player={} spectator={} owner={}",
                    payload.transponderPos(),
                    transponder.transponderId(),
                    player.getName().getString(),
                    player.isSpectator(),
                    transponder.ownerId().map(UUID::toString).orElse("<none>")
            );
            return;
        }
        AirshipSchedule schedule = transponder.ownedSchedule();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "OpenScheduleEditor opening pos={} id={} player={} spectator={} entries={} title='{}'",
                payload.transponderPos(),
                transponder.transponderId(),
                player.getName().getString(),
                player.isSpectator(),
                schedule.entries().size(),
                schedule.title()
        );
        SyncIdentityDirectoryPayload.sendTo(player);
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
                    buffer.writeNbt(AirshipScheduleNbtSerializer.write(schedule));
                }
        );
    }
}

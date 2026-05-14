package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;

public record UpdateAirshipScheduleTitlePayload(String title) implements CustomPacketPayload {
    public static final Type<UpdateAirshipScheduleTitlePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "update_airship_schedule_title")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAirshipScheduleTitlePayload> STREAM_CODEC =
            StreamCodec.ofMember(UpdateAirshipScheduleTitlePayload::write, UpdateAirshipScheduleTitlePayload::read);

    private static UpdateAirshipScheduleTitlePayload read(RegistryFriendlyByteBuf buffer) {
        return new UpdateAirshipScheduleTitlePayload(buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(title, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateAirshipScheduleTitlePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AirshipScheduleMenu menu)) {
            return;
        }
        AirshipSchedule schedule = menu.schedule(player);
        menu.writeSchedule(player, schedule.withTitle(payload.title()));
    }
}

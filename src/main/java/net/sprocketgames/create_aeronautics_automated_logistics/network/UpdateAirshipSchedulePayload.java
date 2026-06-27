package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;

public record UpdateAirshipSchedulePayload(CompoundTag scheduleTag) implements CustomPacketPayload {
    public static final Type<UpdateAirshipSchedulePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "update_airship_schedule")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAirshipSchedulePayload> STREAM_CODEC =
            StreamCodec.ofMember(UpdateAirshipSchedulePayload::write, UpdateAirshipSchedulePayload::read);

    private static UpdateAirshipSchedulePayload read(RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new UpdateAirshipSchedulePayload(tag == null ? new CompoundTag() : tag);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(scheduleTag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateAirshipSchedulePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AirshipScheduleMenu menu)) {
            return;
        }
        AirshipSchedule schedule = AirshipScheduleNbtSerializer.read(payload.scheduleTag());
        if (!withinScheduleLimits(schedule)) {
            CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                    "Rejected oversized airship schedule update from {}: entries={}",
                    player.getGameProfile().getName(),
                    schedule.entries().size()
            );
            return;
        }
        menu.writeSchedule(player, schedule);
    }

    private static boolean withinScheduleLimits(AirshipSchedule schedule) {
        if (schedule.entries().size() > NetworkLimits.MAX_SCHEDULE_ENTRIES) {
            return false;
        }
        int groups = 0;
        int conditions = 0;
        for (var entry : schedule.entries()) {
            groups += entry.effectiveConditionGroups().size();
            if (groups > NetworkLimits.MAX_SCHEDULE_CONDITION_GROUPS) {
                return false;
            }
            for (java.util.List<AirshipScheduleCondition> group : entry.effectiveConditionGroups()) {
                conditions += group.size();
                if (conditions > NetworkLimits.MAX_SCHEDULE_CONDITIONS) {
                    return false;
                }
            }
        }
        return true;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.ShipTransponderScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;

public record SyncTransponderOwnedSchedulePayload(BlockPos transponderPos, CompoundTag scheduleTag)
        implements CustomPacketPayload {
    public static final Type<SyncTransponderOwnedSchedulePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "sync_transponder_owned_schedule"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTransponderOwnedSchedulePayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncTransponderOwnedSchedulePayload::write, SyncTransponderOwnedSchedulePayload::read);

    private static SyncTransponderOwnedSchedulePayload read(RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new SyncTransponderOwnedSchedulePayload(
                buffer.readBlockPos(),
                tag == null ? new CompoundTag() : tag
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(scheduleTag);
        buffer.writeBlockPos(transponderPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncTransponderOwnedSchedulePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            AirshipSchedule schedule = AirshipScheduleNbtSerializer.read(payload.scheduleTag());
            BlockPos zero = BlockPos.ZERO;
            if (Minecraft.getInstance().level == null) {
                return;
            }
            if (!(Minecraft.getInstance().level.getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
                if (Minecraft.getInstance().player != null
                        && Minecraft.getInstance().player.containerMenu instanceof ShipTransponderMenu menu
                        && (menu.transponderPos().equals(payload.transponderPos()) || menu.transponderPos().equals(zero))) {
                    menu.setClientOwnedSchedule(schedule);
                }
                if (Minecraft.getInstance().screen instanceof ShipTransponderScreen screen
                        && (screen.getMenu().transponderPos().equals(payload.transponderPos())
                        || screen.getMenu().transponderPos().equals(zero))) {
                    screen.getMenu().setClientOwnedSchedule(schedule);
                }
                return;
            }
            transponder.setOwnedSchedule(schedule);
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof ShipTransponderMenu menu
                    && (menu.transponderPos().equals(payload.transponderPos()) || menu.transponderPos().equals(zero))) {
                menu.setClientOwnedSchedule(schedule);
            }
            if (Minecraft.getInstance().screen instanceof ShipTransponderScreen screen
                    && (screen.getMenu().transponderPos().equals(payload.transponderPos())
                    || screen.getMenu().transponderPos().equals(zero))) {
                screen.getMenu().setClientOwnedSchedule(schedule);
            }
        });
    }
}

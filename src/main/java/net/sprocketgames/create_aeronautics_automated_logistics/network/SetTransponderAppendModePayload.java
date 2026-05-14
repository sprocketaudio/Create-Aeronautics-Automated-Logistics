package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record SetTransponderAppendModePayload(BlockPos transponderPos, boolean appendToSchedule)
        implements CustomPacketPayload {
    public static final Type<SetTransponderAppendModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_transponder_append_mode")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetTransponderAppendModePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetTransponderAppendModePayload::write, SetTransponderAppendModePayload::read);

    private static SetTransponderAppendModePayload read(RegistryFriendlyByteBuf buffer) {
        return new SetTransponderAppendModePayload(buffer.readBlockPos(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        buffer.writeBoolean(appendToSchedule);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTransponderAppendModePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getBlockEntity(payload.transponderPos()) instanceof ShipTransponderBlockEntity transponder) {
            if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
                return;
            }
            transponder.setAppendToSchedule(payload.appendToSchedule());
        }
    }
}

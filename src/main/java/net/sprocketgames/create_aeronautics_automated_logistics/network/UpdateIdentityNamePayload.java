package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationPermissionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public record UpdateIdentityNamePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<UpdateIdentityNamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "update_identity_name")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateIdentityNamePayload> STREAM_CODEC =
            StreamCodec.ofMember(UpdateIdentityNamePayload::write, UpdateIdentityNamePayload::read);

    private static UpdateIdentityNamePayload read(RegistryFriendlyByteBuf buffer) {
        return new UpdateIdentityNamePayload(buffer.readBlockPos(), buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(name, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateIdentityNamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.distanceToSqr(payload.pos().getX() + 0.5D, payload.pos().getY() + 0.5D, payload.pos().getZ() + 0.5D) > 64.0D) {
            return;
        }

        if (player.serverLevel().getBlockEntity(payload.pos()) instanceof AirshipStationBlockEntity station) {
            if (!StationPermissionService.ensureCanControl(player, station)) {
                return;
            }
            station.setStationName(payload.name());
            return;
        }
        if (player.serverLevel().getBlockEntity(payload.pos()) instanceof ShipTransponderBlockEntity transponder) {
            if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
                return;
            }
            transponder.setShipName(payload.name());
            transponder.refreshRuntimeShip(player.serverLevel());
        }
    }
}

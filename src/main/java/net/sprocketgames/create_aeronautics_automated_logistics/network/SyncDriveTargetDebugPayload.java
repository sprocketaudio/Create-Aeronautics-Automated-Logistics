package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;

public record SyncDriveTargetDebugPayload(List<Target> targets) implements CustomPacketPayload {
    public static final Type<SyncDriveTargetDebugPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "sync_drive_target_debug")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDriveTargetDebugPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncDriveTargetDebugPayload::write, SyncDriveTargetDebugPayload::read);

    private static SyncDriveTargetDebugPayload read(RegistryFriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(
                buffer,
                NetworkLimits.MAX_DRIVE_TARGET_DEBUG_NODES,
                "drive target debug nodes"
        );
        List<Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID routeId = buffer.readUUID();
            Optional<BlockPos> transponderPos = buffer.readBoolean()
                    ? Optional.of(buffer.readBlockPos())
                    : Optional.empty();
            int targetIndex = buffer.readVarInt();
            Vec3 position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            targets.add(new Target(routeId, transponderPos, targetIndex, position));
        }
        return new SyncDriveTargetDebugPayload(targets);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(targets.size());
        for (Target target : targets) {
            buffer.writeUUID(target.routeId());
            buffer.writeBoolean(target.transponderPos().isPresent());
            target.transponderPos().ifPresent(buffer::writeBlockPos);
            buffer.writeVarInt(target.targetIndex());
            buffer.writeDouble(target.position().x);
            buffer.writeDouble(target.position().y);
            buffer.writeDouble(target.position().z);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncDriveTargetDebugPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LogisticsClientOverlays.replaceDriveTargetDebugNodes(payload.targets()));
    }

    public record Target(UUID routeId, Optional<BlockPos> transponderPos, int targetIndex, Vec3 position) {
    }
}

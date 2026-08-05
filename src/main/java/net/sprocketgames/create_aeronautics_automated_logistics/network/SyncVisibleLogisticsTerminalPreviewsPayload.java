package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.LogisticsTerminalBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsTerminalPreviewClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.service.LogisticsTerminalPermissionService;

public record SyncVisibleLogisticsTerminalPreviewsPayload(List<BlockPos> positions) implements CustomPacketPayload {
    public static final Type<SyncVisibleLogisticsTerminalPreviewsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "sync_visible_logistics_terminal_previews")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncVisibleLogisticsTerminalPreviewsPayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncVisibleLogisticsTerminalPreviewsPayload::write, SyncVisibleLogisticsTerminalPreviewsPayload::read);
    private static final int MAX_PREVIEW_DISTANCE = 96;

    public SyncVisibleLogisticsTerminalPreviewsPayload {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncVisibleLogisticsTerminalPreviewsPayload(visibleTerminalPositions(player)));
    }

    private static List<BlockPos> visibleTerminalPositions(ServerPlayer player) {
        int chunkRadius = (MAX_PREVIEW_DISTANCE >> 4) + 1;
        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;
        List<BlockPos> positions = new ArrayList<>();
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                var chunk = player.serverLevel().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk levelChunk)) {
                    continue;
                }
                for (var blockEntity : levelChunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof LogisticsTerminalBlockEntity terminal)) {
                        continue;
                    }
                    if (blockEntity.getBlockPos().distToCenterSqr(player.position()) > (double) MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE) {
                        continue;
                    }
                    if (LogisticsTerminalPermissionService.canSeePreview(player, terminal)) {
                        positions.add(blockEntity.getBlockPos().immutable());
                    }
                }
            }
        }
        return positions;
    }

    private static SyncVisibleLogisticsTerminalPreviewsPayload read(RegistryFriendlyByteBuf buffer) {
        int count = NetworkLimits.readBoundedCount(buffer, NetworkLimits.MAX_IDENTITY_SNAPSHOTS, "visible logistics terminal previews");
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buffer.readBlockPos().immutable());
        }
        return new SyncVisibleLogisticsTerminalPreviewsPayload(positions);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buffer.writeBlockPos(pos);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncVisibleLogisticsTerminalPreviewsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) {
                return;
            }
            LogisticsTerminalPreviewClientState.replaceVisibleTerminals(payload.positions());
        });
    }
}

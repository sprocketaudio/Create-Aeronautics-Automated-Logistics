package net.sprocketgames.create_aeronautics_automated_logistics.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AdvancedTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import org.joml.Matrix4f;

public final class AdvancedTransponderOverlayRenderer {
    private static final float TEXT_SCALE = 0.025F;
    private static final double PORT_LABEL_DISTANCE = 0.8D;
    private static final double PORT_LABEL_HEIGHT = 0.35D;
    private static final double EDGE_LABEL_OUTSET = 0.85D;
    private static final int PORT_LABEL_COLOR = 0xFFF4D78A;
    private static final int DIRECTION_LABEL_COLOR = 0xFFE8F6FF;

    private AdvancedTransponderOverlayRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos blockPos = blockHitResult.getBlockPos();
        if (!(minecraft.level.getBlockEntity(blockPos) instanceof AdvancedTransponderBlockEntity transponder)) {
            return;
        }
        if (!AdvancedTransponderOverlayClientState.isEnabled(blockPos)) {
            return;
        }
        BlockState blockState = transponder.getBlockState();
        if (!(blockState.getBlock() instanceof AdvancedTransponderBlock)) {
            return;
        }

        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(transponder);
        List<OverlayLabel> labels = new ArrayList<>();
        labels.addAll(portLabels(blockPos, blockState, subLevel));
        labels.addAll(directionLabels(subLevel));
        if (labels.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        for (OverlayLabel label : labels) {
            renderLabel(poseStack, buffer, cameraPosition, minecraft.font, minecraft, label);
        }
        buffer.endBatch();
    }

    private static List<OverlayLabel> portLabels(BlockPos blockPos, BlockState blockState, ClientSubLevel subLevel) {
        List<OverlayLabel> labels = new ArrayList<>(4);
        labels.add(portLabel(blockPos, subLevel, blockState.getValue(ShipTransponderBlock.FACING), "DOCK"));
        labels.add(portLabel(blockPos, subLevel, blockState.getValue(ShipTransponderBlock.FACING).getOpposite(), "LIFT"));
        labels.add(portLabel(blockPos, subLevel, blockState.getValue(ShipTransponderBlock.FACING).getCounterClockWise(), "NS"));
        labels.add(portLabel(blockPos, subLevel, blockState.getValue(ShipTransponderBlock.FACING).getClockWise(), "EW"));
        return labels;
    }

    private static OverlayLabel portLabel(BlockPos blockPos, ClientSubLevel subLevel, Direction face, String text) {
        Vec3 labelPosition = worldPosition(
                blockPos,
                new Vec3(face.getStepX() * PORT_LABEL_DISTANCE, PORT_LABEL_HEIGHT, face.getStepZ() * PORT_LABEL_DISTANCE),
                subLevel
        );
        return new OverlayLabel(labelPosition, Component.literal(text), PORT_LABEL_COLOR);
    }

    private static List<OverlayLabel> directionLabels(ClientSubLevel subLevel) {
        if (subLevel == null) {
            return List.of();
        }
        var bounds = subLevel.boundingBox();
        if (bounds == null) {
            return List.of();
        }
        Vec3 center = new Vec3(
                (bounds.minX() + bounds.maxX()) * 0.5D,
                (bounds.minY() + bounds.maxY()) * 0.5D,
                (bounds.minZ() + bounds.maxZ()) * 0.5D
        );
        double labelY = Math.max(center.y, bounds.maxY()) + 0.75D;
        return List.of(
                new OverlayLabel(new Vec3(center.x, labelY, bounds.minZ() - EDGE_LABEL_OUTSET), Component.literal("N"), DIRECTION_LABEL_COLOR),
                new OverlayLabel(new Vec3(center.x, labelY, bounds.maxZ() + EDGE_LABEL_OUTSET), Component.literal("S"), DIRECTION_LABEL_COLOR),
                new OverlayLabel(new Vec3(bounds.maxX() + EDGE_LABEL_OUTSET, labelY, center.z), Component.literal("E"), DIRECTION_LABEL_COLOR),
                new OverlayLabel(new Vec3(bounds.minX() - EDGE_LABEL_OUTSET, labelY, center.z), Component.literal("W"), DIRECTION_LABEL_COLOR)
        );
    }

    private static Vec3 worldPosition(BlockPos blockPos, Vec3 localOffset, ClientSubLevel subLevel) {
        Vec3 localPosition = Vec3.atCenterOf(blockPos).add(localOffset);
        if (subLevel == null) {
            return localPosition;
        }
        return subLevel.logicalPose().transformPosition(localPosition);
    }

    private static void renderLabel(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffer,
            Vec3 cameraPosition,
            Font font,
            Minecraft minecraft,
            OverlayLabel label
    ) {
        poseStack.pushPose();
        poseStack.translate(
                label.position().x - cameraPosition.x,
                label.position().y - cameraPosition.y,
                label.position().z - cameraPosition.z
        );
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(label.text()) / 2.0F;
        float backgroundOpacity = minecraft.options.getBackgroundOpacity(0.25F);
        int background = (int) (backgroundOpacity * 255.0F) << 24;
        font.drawInBatch(label.text(), x, 0.0F, 0x21000000, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, background, 0xF000F0);
        font.drawInBatch(label.text(), x, 0.0F, label.color(), false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        poseStack.popPose();
    }

    private record OverlayLabel(Vec3 position, Component text, int color) {
    }
}

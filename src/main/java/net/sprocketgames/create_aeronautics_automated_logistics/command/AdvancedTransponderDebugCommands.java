package net.sprocketgames.create_aeronautics_automated_logistics.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AdvancedTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;

final class AdvancedTransponderDebugCommands {
    private AdvancedTransponderDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("advanced_transponder")
                .then(Commands.literal("force")
                        .then(forceIntentLiteral("dock", AdvancedTransponderBlock.OutputPort.DOCK))
                        .then(forceIntentLiteral("lift", AdvancedTransponderBlock.OutputPort.LIFT))
                        .then(forceIntentLiteral("north_south", AdvancedTransponderBlock.OutputPort.NORTH_SOUTH))
                        .then(forceIntentLiteral("east_west", AdvancedTransponderBlock.OutputPort.EAST_WEST)))
                .then(Commands.literal("clear")
                        .executes(context -> clearDebug(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> showDebugStatus(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> forceIntentLiteral(
            String name,
            AdvancedTransponderBlock.OutputPort port
    ) {
        return Commands.literal(name)
                .then(Commands.argument("strength", IntegerArgumentType.integer(0, 15))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                .executes(context -> forceIntent(
                                        context.getSource(),
                                        port,
                                        IntegerArgumentType.getInteger(context, "strength"),
                                        IntegerArgumentType.getInteger(context, "seconds")
                                ))));
    }

    private static int forceIntent(
            CommandSourceStack source,
            AdvancedTransponderBlock.OutputPort port,
            int strength,
            int seconds
    ) {
        AdvancedTransponderBlockEntity transponder = lookedAtAdvancedTransponder(source);
        if (transponder == null) {
            return 0;
        }
        int ticks = seconds * 20;
        transponder.forceSingleOutput(port, strength, ticks);
        source.sendSuccess(() -> Component.literal(
                "Forced Advanced Transponder intent "
                        + debugIntentName(port)
                        + " to "
                        + strength
                        + " for "
                        + seconds
                        + "s."
        ), true);
        return 1;
    }

    private static int clearDebug(CommandSourceStack source) {
        AdvancedTransponderBlockEntity transponder = lookedAtAdvancedTransponder(source);
        if (transponder == null) {
            return 0;
        }
        transponder.clearForcedOutputs();
        source.sendSuccess(() -> Component.literal("Cleared forced Advanced Transponder debug outputs."), true);
        return 1;
    }

    private static int showDebugStatus(CommandSourceStack source) {
        AdvancedTransponderBlockEntity transponder = lookedAtAdvancedTransponder(source);
        if (transponder == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Advanced Transponder debug state: forced="
                        + transponder.forcedOutputsActive()
                        + ", ticksRemaining="
                        + transponder.forcedOutputTicksRemaining()
                        + ", dock="
                        + transponder.outputSignal(AdvancedTransponderBlock.OutputPort.DOCK)
                        + ", lift="
                        + transponder.outputSignal(AdvancedTransponderBlock.OutputPort.LIFT)
                        + ", north_south="
                        + transponder.outputSignal(AdvancedTransponderBlock.OutputPort.NORTH_SOUTH)
                        + ", east_west="
                        + transponder.outputSignal(AdvancedTransponderBlock.OutputPort.EAST_WEST)
        ), false);
        return 1;
    }

    private static AdvancedTransponderBlockEntity lookedAtAdvancedTransponder(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use Advanced Transponder debug commands."));
            return null;
        }
        HitResult hitResult = player.pick(8.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look directly at an Advanced Transponder first."));
            return null;
        }
        BlockPos blockPos = blockHitResult.getBlockPos();
        if (!(player.serverLevel().getBlockEntity(blockPos) instanceof AdvancedTransponderBlockEntity transponder)) {
            source.sendFailure(Component.literal("The targeted block is not an Advanced Transponder."));
            return null;
        }
        return transponder;
    }

    private static String debugIntentName(AdvancedTransponderBlock.OutputPort port) {
        return switch (port) {
            case DOCK -> "dock";
            case LIFT -> "lift";
            case NORTH_SOUTH -> "north_south";
            case EAST_WEST -> "east_west";
            case NONE -> "none";
        };
    }
}

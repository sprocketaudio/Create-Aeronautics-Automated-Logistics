package net.sprocketgames.create_aeronautics_automated_logistics.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ShipRecoveryService;

public final class AutomatedLogisticsCommands {
    private static final SuggestionProvider<CommandSourceStack> SHIP_REFERENCE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    escapeSuggestions(ShipRecoveryService.knownShipNames(context.getSource().getServer()).stream()),
                    builder
            );
    private static final SuggestionProvider<CommandSourceStack> SHIP_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ShipRecoveryService.knownShipIds(context.getSource().getServer()), builder);
    private static final SuggestionProvider<CommandSourceStack> STATION_NAME_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    escapeSuggestions(ShipRecoveryService.knownStationNames(context.getSource().getServer()).stream()),
                    builder
            );
    private static final SuggestionProvider<CommandSourceStack> STATION_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ShipRecoveryService.knownStationIds(context.getSource().getServer()), builder);

    private AutomatedLogisticsCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aal")
                .requires(source -> source.hasPermission(2))
                .then(tpTree()));
        event.getDispatcher().register(Commands.literal("automated_logistics")
                .requires(source -> source.hasPermission(2))
                .then(tpTree()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tpTree() {
        return Commands.literal("tp_to_ship")
                .then(Commands.literal("ship")
                        .then(Commands.argument("ship", StringArgumentType.string())
                                .suggests(SHIP_REFERENCE_SUGGESTIONS)
                                .executes(context -> tpToShip(context, StringArgumentType.getString(context, "ship")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> tpToShip(
                                                context,
                                                StringArgumentType.getString(context, "ship"),
                                                EntityArgument.getPlayer(context, "player")
                                        )))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> recoverTree() {
        return Commands.literal("recover")
                .then(Commands.literal("ship")
                        .then(Commands.argument("ship", StringArgumentType.string())
                                .suggests(SHIP_REFERENCE_SUGGESTIONS)
                                .then(Commands.literal("to_station")
                                        .then(Commands.argument("station", StringArgumentType.string())
                                                .suggests(STATION_NAME_SUGGESTIONS)
                                                .executes(context -> recoverToStation(
                                                        context,
                                                        StringArgumentType.getString(context, "ship"),
                                                        StringArgumentType.getString(context, "station")
                                                ))))))
                .then(Commands.literal("transponder")
                        .then(Commands.argument("transponder", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .then(Commands.literal("to_station")
                                        .then(Commands.argument("station", StringArgumentType.string())
                                                .suggests(STATION_NAME_SUGGESTIONS)
                                                .executes(context -> recoverTransponderToStation(
                                                        context,
                                                        StringArgumentType.getString(context, "transponder"),
                                                        StringArgumentType.getString(context, "station")
                                                ))))));
    }

    private static int recoverToStation(CommandContext<CommandSourceStack> context, String shipIdentifier, String stationIdentifier) {
        ShipRecoveryService.RecoveryResult result = ShipRecoveryService.recoverToStation(
                context.getSource().getLevel(),
                ShipRecoveryService.parseStoredShipSelector(shipIdentifier),
                ShipRecoveryService.parseStationSelector(stationIdentifier)
        );
        return sendResult(context.getSource(), result);
    }

    private static int recoverTransponderToStation(
            CommandContext<CommandSourceStack> context,
            String transponderIdentifier,
            String stationIdentifier
    ) {
        ShipRecoveryService.RecoveryResult result = ShipRecoveryService.recoverToStation(
                context.getSource().getLevel(),
                ShipRecoveryService.parseTransponderSelector(transponderIdentifier),
                ShipRecoveryService.parseStationSelector(stationIdentifier)
        );
        return sendResult(context.getSource(), result);
    }

    private static int tpToShip(CommandContext<CommandSourceStack> context, String shipIdentifier)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return tpToShip(context, shipIdentifier, context.getSource().getPlayerOrException());
    }

    private static int tpToShip(CommandContext<CommandSourceStack> context, String shipIdentifier, net.minecraft.server.level.ServerPlayer player) {
        ShipRecoveryService.RecoveryResult result = ShipRecoveryService.teleportPlayerToShip(
                player,
                ShipRecoveryService.parseShipNameSelector(shipIdentifier)
        );
        return sendResult(context.getSource(), result);
    }

    private static int sendResult(CommandSourceStack source, ShipRecoveryService.RecoveryResult result) {
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()), true);
            return 1;
        }
        source.sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static java.util.List<String> escapeSuggestions(java.util.stream.Stream<String> values) {
        return values
                .map(StringArgumentType::escapeIfRequired)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}

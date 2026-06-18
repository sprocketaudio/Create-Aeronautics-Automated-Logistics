package net.sprocketgames.create_aeronautics_automated_logistics.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.network.ShowShipTransponderHighlightPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ShipRecoveryService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AirshipScheduleExecutionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.VehicleRoutePlaybackService;

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
    private static final SuggestionProvider<CommandSourceStack> RUNTIME_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    visibleRuntimeSummaries(context.getSource()).stream()
                            .map(VehicleRoutePlaybackService.RuntimePlaybackSummary::transponderId)
                            .flatMap(Optional::stream)
                            .map(UUID::toString)
                            .distinct(),
                    builder
            );
    private static final int SHOW_HIGHLIGHT_TICKS = 20 * 10;

    private AutomatedLogisticsCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aal")
                .then(tpTree().requires(source -> source.hasPermission(2)))
                .then(runtimeTree()));
        event.getDispatcher().register(Commands.literal("automated_logistics")
                .then(tpTree().requires(source -> source.hasPermission(2)))
                .then(runtimeTree()));
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> runtimeTree() {
        return Commands.literal("runtime")
                .then(Commands.literal("list")
                        .executes(AutomatedLogisticsCommands::listRuntimePlaybacks))
                .then(Commands.literal("pause")
                        .then(Commands.argument("runtime", StringArgumentType.word())
                                .suggests(RUNTIME_ID_SUGGESTIONS)
                                .executes(context -> pauseRuntimePlayback(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "runtime")
                                ))))
                .then(Commands.literal("show")
                        .then(Commands.argument("runtime", StringArgumentType.word())
                                .suggests(RUNTIME_ID_SUGGESTIONS)
                                .executes(context -> showRuntimePlayback(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "runtime")
                                ))))
                .then(Commands.literal("kill")
                        .then(Commands.argument("runtime", StringArgumentType.word())
                                .suggests(RUNTIME_ID_SUGGESTIONS)
                                .executes(context -> killRuntimePlayback(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "runtime")
                                ))));
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

    private static int listRuntimePlaybacks(CommandContext<CommandSourceStack> context) {
        List<VehicleRoutePlaybackService.RuntimePlaybackSummary> summaries = visibleRuntimeSummaries(context.getSource());
        if (summaries.isEmpty()) {
            if (commandOwnerFilter(context.getSource()).isPresent()) {
                context.getSource().sendSuccess(() -> Component.literal("You have no active or pending runtime routes."), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("No active or pending runtime routes."), false);
            }
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("Runtime routes: " + summaries.size()), false);
        for (VehicleRoutePlaybackService.RuntimePlaybackSummary summary : summaries) {
            context.getSource().sendSuccess(() -> runtimeSummaryLine(summary), false);
        }
        return summaries.size();
    }

    private static int pauseRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID transponderId = parseUuid(source, runtimeIdText, "runtime id");
        if (transponderId == null) {
            return 0;
        }

        VehicleRoutePlaybackService.RuntimePlaybackSummary summary = findVisibleRuntimePlayback(source, transponderId);
        if (summary == null) {
            source.sendFailure(Component.literal("No runtime route found for " + runtimeIdText + "."));
            return 0;
        }
        if (summary.state().startsWith("PENDING_")) {
            source.sendFailure(Component.literal("Runtime " + runtimeIdText + " is pending restore, not actively moving."));
            return 0;
        }

        boolean paused = AutomatedLogisticsServices.PLAYBACK.pauseRuntimePlayback(source.getServer(), summary.routeId());
        if (!paused) {
            source.sendFailure(Component.literal("Could not pause runtime " + runtimeIdText + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Paused runtime " + runtimeIdText + " in hold state."), true);
        return 1;
    }

    private static int showRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID transponderId = parseUuid(source, runtimeIdText, "runtime id");
        if (transponderId == null) {
            return 0;
        }

        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use runtime show."));
            return 0;
        }

        VehicleRoutePlaybackService.RuntimePlaybackSummary summary = findVisibleRuntimePlayback(source, transponderId);
        if (summary == null) {
            source.sendFailure(Component.literal("No runtime route found for " + runtimeIdText + "."));
            return 0;
        }
        if (summary.transponderPos().isEmpty()) {
            source.sendFailure(Component.literal("Could not locate the transponder for runtime " + runtimeIdText + "."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new ShowShipTransponderHighlightPayload(
                summary.transponderPos().get(),
                SHOW_HIGHLIGHT_TICKS
        ));
        source.sendSuccess(() -> Component.literal("Highlighted transponder for runtime " + runtimeIdText + "."), false);
        return 1;
    }

    private static int killRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID transponderId = parseUuid(source, runtimeIdText, "runtime id");
        if (transponderId == null) {
            return 0;
        }

        VehicleRoutePlaybackService.RuntimePlaybackSummary summary = findVisibleRuntimePlayback(source, transponderId);
        if (summary == null) {
            source.sendFailure(Component.literal("No runtime route found for " + runtimeIdText + "."));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(summary.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Could not resolve the runtime dimension for " + runtimeIdText + "."));
            return 0;
        }
        AirshipScheduleExecutionService schedules = AutomatedLogisticsServices.SCHEDULES;
        schedules.stop(level, transponderId);
        source.sendSuccess(() -> Component.literal("Killed runtime " + runtimeIdText + " and released ship physics."), true);
        return 1;
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

    private static UUID parseUuid(CommandSourceStack source, String rawId, String label) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Invalid " + label + ": " + rawId));
            return null;
        }
    }

    private static VehicleRoutePlaybackService.RuntimePlaybackSummary findVisibleRuntimePlayback(
            CommandSourceStack source,
            UUID transponderId
    ) {
        return visibleRuntimeSummaries(source).stream()
                .filter(summary -> summary.transponderId().filter(transponderId::equals).isPresent())
                .findFirst()
                .orElse(null);
    }

    private static List<VehicleRoutePlaybackService.RuntimePlaybackSummary> visibleRuntimeSummaries(CommandSourceStack source) {
        Optional<UUID> ownerFilter = commandOwnerFilter(source);
        return AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(source.getServer()).stream()
                .filter(summary -> ownerFilter.isEmpty() || summary.ownerId().filter(ownerFilter.get()::equals).isPresent())
                .toList();
    }

    private static Optional<UUID> commandOwnerFilter(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return Optional.empty();
        }
        return Optional.ofNullable(source.getEntity()).map(net.minecraft.world.entity.Entity::getUUID);
    }

    private static Component runtimeSummaryLine(VehicleRoutePlaybackService.RuntimePlaybackSummary summary) {
        MutableComponent line = Component.literal("- ")
                .append(Component.literal(summary.shipName()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.routeName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.state()).withStyle(stateColor(summary.state())))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.dimension().location().toString()).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" | "))
                .append(Component.literal(shortRouteId(summary.routeId())).withStyle(ChatFormatting.GRAY)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal(summary.routeId().value().toString())
                        ))));

        summary.position().ifPresent(position -> line.append(Component.literal(" | "))
                .append(Component.literal(formatPosition(position)).withStyle(ChatFormatting.GRAY)));
        if (summary.restoreCooldownTicks() > 0) {
            line.append(Component.literal(" | "))
                    .append(Component.literal("cooldown " + summary.restoreCooldownTicks() + "t").withStyle(ChatFormatting.GOLD));
        }

        line.append(Component.literal("  "));
        if (summary.transponderPos().isPresent()) {
            line.append(actionButton(
                    "[show]",
                    "/aal runtime show " + summary.transponderId().orElse(summary.routeId().value()),
                    "Highlight this ship transponder for 10 seconds"
            ));
        } else {
            line.append(inactiveAction("[show]", "The linked transponder could not be located."));
        }
        line.append(Component.literal(" "));
        if (summary.state().startsWith("PENDING_")) {
            line.append(inactiveAction("[pause]", "Pending restore routes cannot be paused."));
        } else if (summary.state().equals("PAUSED")) {
            line.append(inactiveAction("[pause]", "This route is already paused."));
        } else {
            line.append(actionButton(
                    "[pause]",
                    "/aal runtime pause " + summary.transponderId().orElse(summary.routeId().value()),
                    "Pause this route in a hold state"
            ));
        }
        line.append(Component.literal(" "));
        line.append(actionButton(
                "[kill]",
                "/aal runtime kill " + summary.transponderId().orElse(summary.routeId().value()),
                "Stop this runtime and release ship physics"
        ));
        return line;
    }

    private static MutableComponent actionButton(String label, String command, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static MutableComponent inactiveAction(String label, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.DARK_GRAY)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static ChatFormatting stateColor(String state) {
        if (state.startsWith("PENDING_")) {
            return ChatFormatting.GOLD;
        }
        return switch (state) {
            case "ACTIVE" -> ChatFormatting.GREEN;
            case "WAITING" -> ChatFormatting.AQUA;
            case "PAUSED" -> ChatFormatting.YELLOW;
            case "UNLOADED_TRANSIT" -> ChatFormatting.LIGHT_PURPLE;
            case "COMPLETED" -> ChatFormatting.GRAY;
            default -> ChatFormatting.WHITE;
        };
    }

    private static String shortRouteId(RouteId routeId) {
        String full = routeId.value().toString();
        return full.substring(0, Math.min(8, full.length()));
    }

    private static String formatPosition(net.minecraft.world.phys.Vec3 position) {
        return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", position.x, position.y, position.z);
    }
}

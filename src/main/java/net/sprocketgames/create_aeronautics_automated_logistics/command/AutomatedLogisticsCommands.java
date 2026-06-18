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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.network.ShowShipTransponderHighlightPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ShipRecoveryService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AirshipScheduleExecutionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RuntimeSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RuntimeState;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteSegmentDirectorySavedData.PendingDeletionRecord;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteSegmentDirectorySavedData.StoredSegmentRecord;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ShipMaterializationService;
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
                    visibleRuntimeSnapshots(context.getSource()).stream()
                            .map(RuntimeSnapshot::runtimeId)
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
                .then(debugTree().requires(source -> source.hasPermission(2)))
                .then(runtimeTree()));
        event.getDispatcher().register(Commands.literal("automated_logistics")
                .then(tpTree().requires(source -> source.hasPermission(2)))
                .then(debugTree().requires(source -> source.hasPermission(2)))
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> debugTree() {
        return Commands.literal("debug")
                .then(Commands.literal("routes")
                        .then(Commands.literal("transponder")
                                .then(Commands.argument("transponder", StringArgumentType.word())
                                        .suggests(SHIP_ID_SUGGESTIONS)
                                        .executes(context -> debugRoutesForTransponder(
                                                context,
                                                StringArgumentType.getString(context, "transponder")
                                        ))))
                        .then(Commands.literal("directory")
                                .executes(AutomatedLogisticsCommands::debugRouteDirectory))
                        .then(Commands.literal("pending-deletions")
                                .executes(AutomatedLogisticsCommands::debugPendingRouteDeletions))
                        .then(Commands.literal("rebuild-index")
                                .executes(AutomatedLogisticsCommands::debugRebuildRouteIndex)))
                .then(Commands.literal("runtime")
                        .then(Commands.literal("dump")
                                .executes(AutomatedLogisticsCommands::debugRuntimeDump)))
                .then(Commands.literal("materialization")
                        .then(Commands.literal("transponder")
                                .then(Commands.argument("transponder", StringArgumentType.word())
                                        .suggests(SHIP_ID_SUGGESTIONS)
                                        .executes(context -> debugMaterializationForTransponder(
                                                context,
                                                StringArgumentType.getString(context, "transponder")
                                        )))))
                .then(Commands.literal("playback")
                        .then(Commands.literal("dump")
                                .executes(AutomatedLogisticsCommands::debugPlaybackDump)))
                .then(Commands.literal("restore")
                        .then(Commands.literal("dump")
                                .executes(AutomatedLogisticsCommands::debugRestoreDump)));
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
        List<RuntimeSnapshot> snapshots = visibleRuntimeSnapshots(context.getSource());
        if (snapshots.isEmpty()) {
            if (commandOwnerFilter(context.getSource()).isPresent()) {
                context.getSource().sendSuccess(() -> Component.literal("You have no active or pending runtime routes."), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("No active or pending runtime routes."), false);
            }
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("Runtime routes: " + snapshots.size()), false);
        for (RuntimeSnapshot snapshot : snapshots) {
            context.getSource().sendSuccess(() -> runtimeSummaryLine(snapshot), false);
        }
        return snapshots.size();
    }

    private static int pauseRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID transponderId = parseUuid(source, runtimeIdText, "runtime id");
        if (transponderId == null) {
            return 0;
        }

        RuntimeSnapshot snapshot = findVisibleRuntime(source, transponderId);
        if (snapshot == null) {
            source.sendFailure(Component.literal("No runtime route found for " + runtimeIdText + "."));
            return 0;
        }
        if (snapshot.state() == RuntimeState.PAUSED_MANUAL || snapshot.state() == RuntimeState.PAUSED_FAULT) {
            source.sendFailure(Component.literal("Runtime " + runtimeIdText + " is already paused."));
            return 0;
        }
        if (snapshot.pendingPlayback()) {
            source.sendFailure(Component.literal("Runtime " + runtimeIdText + " is pending restore, not actively moving."));
            return 0;
        }
        if (!snapshot.canPause()) {
            source.sendFailure(Component.literal("Runtime " + runtimeIdText + " cannot be paused in its current state."));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(snapshot.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Could not resolve the runtime dimension for " + runtimeIdText + "."));
            return 0;
        }

        boolean paused = AutomatedLogisticsServices.SCHEDULES.pauseRuntime(level, transponderId, "command_pause");
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

        RuntimeSnapshot snapshot = findVisibleRuntime(source, transponderId);
        if (snapshot == null) {
            source.sendFailure(Component.literal("No runtime route found for " + runtimeIdText + "."));
            return 0;
        }
        if (snapshot.transponderPos().isEmpty()) {
            source.sendFailure(Component.literal("Could not locate the transponder for runtime " + runtimeIdText + "."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new ShowShipTransponderHighlightPayload(
                snapshot.transponderPos().get(),
                SHOW_HIGHLIGHT_TICKS
        ));
        source.sendSuccess(() -> Component.literal("Highlighted transponder for runtime " + runtimeIdText + "."), false);
        return 1;
    }

    private static int killRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID runtimeId = parseUuid(source, runtimeIdText, "runtime id");
        if (runtimeId == null) {
            return 0;
        }

        RuntimeSnapshot snapshot = findVisibleRuntime(source, runtimeId);
        if (snapshot == null) {
            source.sendFailure(Component.literal("No runtime route found for " + runtimeIdText + "."));
            return 0;
        }

        if (snapshot.state() == RuntimeState.ORPHAN_PLAYBACK && snapshot.activeRouteId().isPresent()) {
            boolean stopped = AutomatedLogisticsServices.PLAYBACK.stopRuntimePlayback(
                    source.getServer(),
                    snapshot.activeRouteId().get(),
                    FailureReason.NONE
            );
            if (!stopped) {
                source.sendFailure(Component.literal("Could not kill orphan playback " + runtimeIdText + "."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Killed orphan playback " + runtimeIdText + " and released ship physics."), true);
            return 1;
        }

        ServerLevel level = source.getServer().getLevel(snapshot.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Could not resolve the runtime dimension for " + runtimeIdText + "."));
            return 0;
        }
        AirshipScheduleExecutionService schedules = AutomatedLogisticsServices.SCHEDULES;
        if (!schedules.killRuntime(level, runtimeId, "command_kill")) {
            source.sendFailure(Component.literal("Could not kill runtime " + runtimeIdText + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Killed runtime " + runtimeIdText + " and released ship physics."), true);
        return 1;
    }

    private static int debugRoutesForTransponder(CommandContext<CommandSourceStack> context, String transponderIdText) {
        CommandSourceStack source = context.getSource();
        UUID transponderId = parseUuid(source, transponderIdText, "transponder id");
        if (transponderId == null) {
            return 0;
        }

        MinecraftServer server = source.getServer();
        List<StoredSegmentRecord> stored = AutomatedLogisticsServices.ROUTES.storedSegmentsForTransponder(server, transponderId);
        List<RouteSegment> loadedPersistent = AutomatedLogisticsServices.ROUTES.loadedSegmentsForTransponder(server, transponderId);
        List<RouteSegment> cached = RouteSegmentRegistry.forTransponder(transponderId);
        source.sendSuccess(() -> Component.literal("Route repository dump for transponder " + transponderId), false);
        source.sendSuccess(() -> Component.literal("Persistent directory records: " + stored.size()
                + " | loaded station records: " + loadedPersistent.size()
                + " | loaded index/cache records: " + cached.size()), false);
        for (StoredSegmentRecord record : stored) {
            source.sendSuccess(() -> Component.literal("- stored " + storedSegmentDebug(record)), false);
        }
        for (RouteSegment segment : loadedPersistent) {
            source.sendSuccess(() -> Component.literal("- loaded station segment=" + routeSegmentDebug(segment)), false);
        }
        for (RouteSegment segment : cached) {
            source.sendSuccess(() -> Component.literal("- cache segment=" + routeSegmentDebug(segment)), false);
        }
        return Math.max(1, stored.size() + loadedPersistent.size() + cached.size());
    }

    private static int debugRouteDirectory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<StoredSegmentRecord> records = net.sprocketgames.create_aeronautics_automated_logistics.service.RouteSegmentDirectorySavedData
                .allStoredSegments(source.getServer());
        List<RouteSegment> loadedPersistent = AutomatedLogisticsServices.ROUTES.loadedSegments(source.getServer());
        long distinctSegments = records.stream().map(StoredSegmentRecord::segmentId).distinct().count();
        long distinctTransponders = records.stream().map(StoredSegmentRecord::transponderId).distinct().count();
        source.sendSuccess(() -> Component.literal("Route segment directory records: " + records.size()
                + " | distinct segments: " + distinctSegments
                + " | transponders: " + distinctTransponders
                + " | loaded station records visible without loading chunks: " + loadedPersistent.size()), false);
        for (StoredSegmentRecord record : records) {
            source.sendSuccess(() -> Component.literal("- " + storedSegmentDebug(record)), false);
        }
        return Math.max(1, records.size());
    }

    private static int debugPendingRouteDeletions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<PendingDeletionRecord> pending = AutomatedLogisticsServices.ROUTES.allPendingDeletions(source.getServer());
        source.sendSuccess(() -> Component.literal("Pending deferred route deletions: " + pending.size()), false);
        for (PendingDeletionRecord record : pending) {
            source.sendSuccess(() -> Component.literal("- holderStation=" + record.holderStationId()
                    + " segment=" + record.segmentId().value()), false);
        }
        return Math.max(1, pending.size());
    }

    private static int debugRebuildRouteIndex(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int indexed = AutomatedLogisticsServices.ROUTES.rebuildLoadedRouteIndex(source.getServer());
        source.sendSuccess(() -> Component.literal("Rebuilt loaded route index/cache from loaded persistent stations. Indexed segments: " + indexed), true);
        return Math.max(1, indexed);
    }

    private static int debugRuntimeDump(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<RuntimeSnapshot> snapshots = AutomatedLogisticsServices.SCHEDULES.activeSnapshots(source.getServer(), Optional.empty());
        source.sendSuccess(() -> Component.literal("Runtime snapshots: " + snapshots.size()), false);
        for (RuntimeSnapshot snapshot : snapshots) {
            source.sendSuccess(() -> Component.literal("- runtime=" + snapshot.runtimeId()
                    + " transponder=" + snapshot.transponderId()
                    + " state=" + snapshot.state()
                    + " route=" + snapshot.activeRouteId().map(id -> id.value().toString()).orElse("none")
                    + " pendingPlayback=" + snapshot.pendingPlayback()
                    + " failure=" + snapshot.lastFailure().map(Enum::name).orElse("none")
                    + " dim=" + snapshot.dimension().location()
                    + " transponderPos=" + snapshot.transponderPos().map(AutomatedLogisticsCommands::formatBlockPos).orElse("unknown")), false);
        }
        return Math.max(1, snapshots.size());
    }

    private static int debugPlaybackDump(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<VehicleRoutePlaybackService.RuntimePlaybackSummary> summaries =
                AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(source.getServer());
        source.sendSuccess(() -> Component.literal("Playback/motion/wait summaries: " + summaries.size()
                + " | active visual ships: " + AutomatedLogisticsServices.PLAYBACK.activeVisualShipIds().size()), false);
        for (VehicleRoutePlaybackService.RuntimePlaybackSummary summary : summaries) {
            source.sendSuccess(() -> Component.literal("- route=" + summary.routeId().value()
                    + " ship=" + summary.shipName()
                    + " state=" + summary.state()
                    + " transponder=" + summary.transponderId().map(UUID::toString).orElse("unknown")
                    + " vehicle=" + summary.vehicleId().map(UUID::toString).orElse("unknown")
                    + " controllerPos=" + summary.controllerPos().map(AutomatedLogisticsCommands::formatBlockPos).orElse("unknown")
                    + " cooldown=" + summary.restoreCooldownTicks()
                    + " dim=" + summary.dimension().location()), false);
        }
        return Math.max(1, summaries.size());
    }

    private static int debugMaterializationForTransponder(CommandContext<CommandSourceStack> context, String transponderIdText) {
        CommandSourceStack source = context.getSource();
        UUID transponderId = parseUuid(source, transponderIdText, "transponder id");
        if (transponderId == null) {
            return 0;
        }

        Optional<ShipTransponderSnapshot> snapshot = ShipTransponderRegistry.snapshot(transponderId);
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal("No loaded transponder identity snapshot for " + transponderId + ". This does not invalidate routes or schedules."));
            return 0;
        }

        ShipTransponderSnapshot ship = snapshot.get();
        ShipMaterializationService materialization = new ShipMaterializationService();
        source.sendSuccess(() -> Component.literal("Materialization snapshot for transponder " + transponderId
                + " ship=" + ship.shipName()
                + " dim=" + ship.dimension().location()
                + " transponderPos=" + formatBlockPos(ship.transponderPos())
                + " runtimeShip=" + ship.runtimeShipId().map(UUID::toString).orElse("none")), false);

        if (ship.controllerRef().isPresent()) {
            ShipMaterializationService.LiveBodyLookupResult result = materialization.resolveLiveBody(
                    new ShipMaterializationService.LiveBodyLookupRequest(
                            source.getServer(),
                            ship.dimension(),
                            ship.controllerRef().get(),
                            Optional.of(transponderId),
                            ship.runtimeShipId().or(ship.controllerRef().get()::vehicleId),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            "debug_command",
                            "manual_materialization_snapshot"
                    )
            );
            source.sendSuccess(() -> Component.literal("Typed live/stored result: " + materializationResultDebug(result.result())
                    + " | controllerResolved=" + result.controller().isPresent()), false);
            return 1;
        }

        if (ship.runtimeShipId().isPresent()) {
            ShipMaterializationService.MaterializationResult result = materialization.bodyAvailability(
                    source.getServer(),
                    ship.dimension(),
                    Optional.of(transponderId),
                    ship.runtimeShipId().get(),
                    Optional.empty(),
                    Optional.empty()
            );
            source.sendSuccess(() -> Component.literal("Typed body availability result: " + materializationResultDebug(result)
                    + " | controllerRef=missing"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Typed materialization result: CONTROLLER_MISSING reason=no_controller_ref_or_runtime_ship_id"), false);
        return 1;
    }

    private static int debugRestoreDump(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<RuntimeSnapshot> runtimes = AutomatedLogisticsServices.SCHEDULES.activeSnapshots(source.getServer(), Optional.empty());
        List<VehicleRoutePlaybackService.RuntimePlaybackSummary> playbacks =
                AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(source.getServer());
        long orphanPlaybacks = runtimes.stream().filter(snapshot -> snapshot.state() == RuntimeState.ORPHAN_PLAYBACK).count();
        long pendingPlaybacks = playbacks.stream().filter(summary -> summary.state().startsWith("PENDING_")).count();
        source.sendSuccess(() -> Component.literal("Restore coordinator state: stateless staged restore; observable runtime records="
                + runtimes.size()
                + " playback records=" + playbacks.size()
                + " orphanPlaybackSnapshots=" + orphanPlaybacks
                + " pendingPlaybackRecords=" + pendingPlaybacks
                + " activeVisualShips=" + AutomatedLogisticsServices.PLAYBACK.activeVisualShipIds().size()), false);
        source.sendSuccess(() -> Component.literal("Use /aal debug runtime dump and /aal debug playback dump for record-level details."), false);
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

    private static RuntimeSnapshot findVisibleRuntime(
            CommandSourceStack source,
            UUID transponderId
    ) {
        return visibleRuntimeSnapshots(source).stream()
                .filter(snapshot -> snapshot.runtimeId().equals(transponderId))
                .findFirst()
                .orElse(null);
    }

    private static List<RuntimeSnapshot> visibleRuntimeSnapshots(CommandSourceStack source) {
        return AutomatedLogisticsServices.SCHEDULES.activeSnapshots(source.getServer(), commandOwnerFilter(source));
    }

    private static Optional<UUID> commandOwnerFilter(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return Optional.empty();
        }
        return Optional.ofNullable(source.getEntity()).map(net.minecraft.world.entity.Entity::getUUID);
    }

    private static Component runtimeSummaryLine(RuntimeSnapshot summary) {
        MutableComponent line = Component.literal("- ")
                .append(Component.literal(summary.shipName()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.routeName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.state().name()).withStyle(stateColor(summary.state())))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.dimension().location().toString()).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" | "));

        summary.activeRouteId().ifPresentOrElse(
                routeId -> line.append(Component.literal(shortRouteId(routeId)).withStyle(ChatFormatting.GRAY)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal(routeId.value().toString())
                        )))),
                () -> line.append(Component.literal("no-route").withStyle(ChatFormatting.DARK_GRAY))
        );

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
                    "/aal runtime show " + summary.runtimeId(),
                    "Highlight this ship transponder for 10 seconds"
            ));
        } else {
            line.append(inactiveAction("[show]", "The linked transponder could not be located."));
        }
        line.append(Component.literal(" "));
        if (!summary.canPause()) {
            line.append(inactiveAction("[pause]", "This runtime cannot be paused in its current state."));
        } else if (summary.state() == RuntimeState.PAUSED_MANUAL || summary.state() == RuntimeState.PAUSED_FAULT) {
            line.append(inactiveAction("[pause]", "This route is already paused."));
        } else {
            line.append(actionButton(
                    "[pause]",
                    "/aal runtime pause " + summary.runtimeId(),
                    "Pause this route in a hold state"
            ));
        }
        line.append(Component.literal(" "));
        line.append(actionButton(
                "[kill]",
                "/aal runtime kill " + summary.runtimeId(),
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

    private static ChatFormatting stateColor(RuntimeState state) {
        return switch (state) {
            case STARTING, MATERIALIZING, RECOVERING -> ChatFormatting.GOLD;
            case RUNNING_LOADED -> ChatFormatting.GREEN;
            case RUNNING_UNLOADED -> ChatFormatting.LIGHT_PURPLE;
            case WAITING -> ChatFormatting.AQUA;
            case PAUSED_MANUAL, PAUSED_FAULT -> ChatFormatting.YELLOW;
            case COMPLETED, KILLED -> ChatFormatting.GRAY;
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

    private static String formatBlockPos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String storedSegmentDebug(StoredSegmentRecord record) {
        return "segment=" + record.segmentId().value()
                + " holderStation=" + record.holderStationId()
                + " start=" + record.startStationId()
                + " end=" + record.endStationId()
                + " transponder=" + record.transponderId()
                + " dim=" + record.dimension().location();
    }

    private static String routeSegmentDebug(RouteSegment segment) {
        return segment.id().value()
                + " start=" + segment.startStationId()
                + " end=" + segment.endStationId()
                + " transponder=" + segment.transponderId()
                + " dim=" + segment.dimension().location();
    }

    private static String materializationResultDebug(ShipMaterializationService.MaterializationResult result) {
        return result.type()
                + " success=" + result.success()
                + " reason=" + result.reasonCode()
                + " message=\"" + result.message() + "\"";
    }
}

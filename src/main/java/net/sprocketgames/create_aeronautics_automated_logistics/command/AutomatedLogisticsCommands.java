package net.sprocketgames.create_aeronautics_automated_logistics.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.SableStoredShipRepository;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.ShipBodyDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.StoredBodyCandidate;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.StoredBodyLookupResult;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.StoredBodyPointer;
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
                    java.util.stream.Stream.concat(
                                    visibleRuntimeSummaries(context.getSource()).stream()
                                            .flatMap(summary -> java.util.stream.Stream.concat(
                                                    summary.transponderId().stream().map(UUID::toString),
                                                    java.util.stream.Stream.of(summary.routeId().value().toString())
                                            )),
                                    visibleScheduleOnlyRuntimeSummaries(context.getSource()).stream()
                                            .map(summary -> summary.transponderId().toString())
                            )
                            .distinct(),
                    builder
            );
    private static final SuggestionProvider<CommandSourceStack> STORED_POINTER_SUGGESTIONS = (context, builder) -> {
        UUID transponderId;
        try {
            transponderId = UUID.fromString(StringArgumentType.getString(context, "transponder"));
        } catch (IllegalArgumentException exception) {
            return builder.buildFuture();
        }
        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = ShipBodyDirectorySavedData
                .get(context.getSource().getServer())
                .byTransponder(transponderId);
        if (identity.isEmpty()) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                SableStoredShipRepository.candidates(
                                context.getSource().getServer(),
                                identity.get().dimension(),
                                identity.get().sableShipId()
                        ).stream()
                        .map(candidate -> candidate.pointer().selector()),
                builder
        );
    };
    private static final SuggestionProvider<CommandSourceStack> DIMENSION_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    context.getSource().getServer().levelKeys().stream()
                            .map(key -> key.location().toString()),
                    builder
            );
    private static final SuggestionProvider<CommandSourceStack> DANGLING_POINTER_SUGGESTIONS = (context, builder) -> {
        ResourceKey<Level> dimension = parseDimension(StringArgumentType.getString(context, "dimension"));
        return SharedSuggestionProvider.suggest(
                SableStoredShipRepository.danglingIndexes(context.getSource().getServer()).stream()
                        .filter(index -> index.dimension().equals(dimension))
                        .map(index -> index.pointer().selector()),
                builder
        );
    };
    private static final int SHOW_HIGHLIGHT_TICKS = 20 * 10;

    private AutomatedLogisticsCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aal")
                .then(tpTree().requires(source -> source.hasPermission(2)))
                .then(recoverTree().requires(source -> source.hasPermission(2)))
                .then(materializationTree().requires(source -> source.hasPermission(2)))
                .then(runtimeTree()));
        event.getDispatcher().register(Commands.literal("automated_logistics")
                .then(tpTree().requires(source -> source.hasPermission(2)))
                .then(recoverTree().requires(source -> source.hasPermission(2)))
                .then(materializationTree().requires(source -> source.hasPermission(2)))
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
                .then(Commands.literal("ship_name")
                        .then(Commands.argument("ship_name", StringArgumentType.string())
                                .suggests(SHIP_REFERENCE_SUGGESTIONS)
                                .then(Commands.literal("to_station")
                                        .then(Commands.argument("station", StringArgumentType.string())
                                                .suggests(STATION_NAME_SUGGESTIONS)
                                                .executes(context -> recoverShipNameToStation(
                                                        context,
                                                        StringArgumentType.getString(context, "ship_name"),
                                                        StringArgumentType.getString(context, "station")
                                                ))))))
                .then(Commands.literal("transponder_id")
                        .then(Commands.argument("transponder_id", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .then(Commands.literal("to_station")
                                        .then(Commands.argument("station", StringArgumentType.string())
                                                .suggests(STATION_NAME_SUGGESTIONS)
                                                .executes(context -> recoverTransponderToStation(
                                                        context,
                                                        StringArgumentType.getString(context, "transponder_id"),
                                                        StringArgumentType.getString(context, "station")
                                                ))))));
    }

    private static int recoverShipNameToStation(CommandContext<CommandSourceStack> context, String shipName, String stationIdentifier) {
        ShipRecoveryService.RecoveryResult result = ShipRecoveryService.recoverToStation(
                context.getSource().getLevel(),
                ShipRecoveryService.parseShipNameSelector(shipName),
                ShipRecoveryService.parseStationSelector(stationIdentifier)
        );
        return sendResult(context.getSource(), result);
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
        List<AirshipScheduleExecutionService.ScheduleRuntimeSummary> scheduleOnlySummaries =
                visibleScheduleOnlyRuntimeSummaries(context.getSource());
        if (summaries.isEmpty() && scheduleOnlySummaries.isEmpty()) {
            if (commandOwnerFilter(context.getSource()).isPresent()) {
                context.getSource().sendSuccess(() -> Component.literal("You have no active or pending runtime routes."), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("No active or pending runtime routes."), false);
            }
            return 1;
        }

        int total = summaries.size() + scheduleOnlySummaries.size();
        context.getSource().sendSuccess(() -> Component.literal("Runtime records: " + total), false);
        for (VehicleRoutePlaybackService.RuntimePlaybackSummary summary : summaries) {
            context.getSource().sendSuccess(() -> runtimeSummaryLine(summary), false);
        }
        for (AirshipScheduleExecutionService.ScheduleRuntimeSummary summary : scheduleOnlySummaries) {
            context.getSource().sendSuccess(() -> scheduleOnlyRuntimeSummaryLine(summary), false);
        }
        return total;
    }

    private static int pauseRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID runtimeId = parseUuid(source, runtimeIdText, "runtime id");
        if (runtimeId == null) {
            return 0;
        }

        VehicleRoutePlaybackService.RuntimePlaybackSummary summary = findVisibleRuntimePlayback(source, runtimeId);
        if (summary == null) {
            if (findVisibleScheduleOnlyRuntime(source, runtimeId) != null) {
                source.sendFailure(Component.literal("Orphan schedule runtime " + runtimeIdText + " has no playback to pause."));
            } else {
                source.sendFailure(Component.literal("No runtime record found for " + runtimeIdText + "."));
            }
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> materializationTree() {
        return Commands.literal("materialization")
                .then(Commands.literal("duplicates")
                        .executes(context -> listMaterializationDuplicates(context.getSource())))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("transponder", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .executes(context -> listMaterializationCandidates(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "transponder")
                                ))))
                .then(Commands.literal("candidates")
                        .then(Commands.argument("transponder", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .executes(context -> listMaterializationCandidates(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "transponder")
                                ))))
                .then(Commands.literal("select")
                        .then(Commands.argument("transponder", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .then(Commands.argument("pointer", StringArgumentType.greedyString())
                                        .suggests(STORED_POINTER_SUGGESTIONS)
                                        .executes(context -> selectMaterializationCandidate(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "transponder"),
                                                StringArgumentType.getString(context, "pointer")
                                        )))))
                .then(Commands.literal("show")
                        .then(Commands.argument("transponder", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .then(Commands.argument("pointer", StringArgumentType.greedyString())
                                        .suggests(STORED_POINTER_SUGGESTIONS)
                                        .executes(context -> showMaterializationCandidate(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "transponder"),
                                                StringArgumentType.getString(context, "pointer")
                                        )))))
                .then(Commands.literal("quarantine")
                        .then(Commands.literal("dangling")
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .suggests(DIMENSION_SUGGESTIONS)
                                        .then(Commands.argument("pointer", StringArgumentType.greedyString())
                                                .suggests(DANGLING_POINTER_SUGGESTIONS)
                                                .executes(context -> quarantineDanglingMaterializationIndex(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "dimension"),
                                                        StringArgumentType.getString(context, "pointer")
                                                )))))
                        .then(Commands.argument("transponder", StringArgumentType.word())
                                .suggests(SHIP_ID_SUGGESTIONS)
                                .then(Commands.argument("pointer", StringArgumentType.greedyString())
                                        .suggests(STORED_POINTER_SUGGESTIONS)
                                        .executes(context -> quarantineMaterializationCandidate(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "transponder"),
                                                StringArgumentType.getString(context, "pointer")
                                        )))));
    }

    private static int listMaterializationDuplicates(CommandSourceStack source) {
        Map<String, List<StoredBodyCandidate>> groups = new LinkedHashMap<>();
        for (StoredBodyCandidate candidate : SableStoredShipRepository.allCandidates(source.getServer())) {
            String key = candidate.dimension().location() + "|" + candidate.sableShipId();
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(candidate);
        }

        List<List<StoredBodyCandidate>> duplicates = groups.values().stream()
                .filter(candidates -> candidates.stream().filter(StoredBodyCandidate::readable).count() > 1L)
                .toList();
        List<SableStoredShipRepository.DanglingStoredBodyIndex> dangling =
                SableStoredShipRepository.danglingIndexes(source.getServer());
        if (duplicates.isEmpty() && dangling.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No duplicate readable or dangling unreadable Sable stored-body indexes found."
            ), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                "Sable stored-body issues: duplicateGroups=" + duplicates.size()
                        + ", danglingIndexes=" + dangling.size()
        ), false);
        ShipBodyDirectorySavedData directory = ShipBodyDirectorySavedData.get(source.getServer());
        for (List<StoredBodyCandidate> candidates : duplicates) {
            StoredBodyCandidate first = candidates.getFirst();
            String transponders = directory.bySableShipId(first.sableShipId()).stream()
                    .filter(identity -> identity.dimension().equals(first.dimension()))
                    .map(identity -> identity.transponderId().toString())
                    .distinct()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("unknown");
            source.sendSuccess(() -> Component.literal(
                    "- sableShip=" + first.sableShipId()
                            + " dimension=" + first.dimension().location()
                            + " candidates=" + candidates.size()
                            + " transponder=" + transponders
            ).withStyle(ChatFormatting.GOLD), false);
        }
        for (SableStoredShipRepository.DanglingStoredBodyIndex index : dangling) {
            source.sendSuccess(() -> Component.literal(
                    "- dangling index dimension=" + index.dimension().location()
                            + " pointer=" + index.pointer().selector()
                            + " reason=" + index.reason()
                            + " payload=missing "
            ).withStyle(ChatFormatting.RED).append(actionButton(
                    "[quarantine-index]",
                    "/aal materialization quarantine dangling "
                            + index.dimension().location()
                            + " "
                            + index.pointer().selector(),
                    "Remove this unreadable holding index only. No stored body payload is deleted."
            )), false);
        }
        return Math.max(1, duplicates.size() + dangling.size());
    }

    private static int listMaterializationCandidates(CommandSourceStack source, String transponderText) {
        UUID transponderId = parseUuid(source, transponderText, "transponder id");
        if (transponderId == null) {
            return 0;
        }
        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = ShipBodyDirectorySavedData
                .get(source.getServer())
                .byTransponder(transponderId);
        if (identity.isEmpty()) {
            source.sendFailure(Component.literal("No body-directory record exists for transponder " + transponderId + "."));
            return 0;
        }
        ShipBodyDirectorySavedData.BodyIdentity body = identity.get();
        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                source.getServer(),
                Optional.of(transponderId),
                body.dimension(),
                body.sableShipId()
        );
        source.sendSuccess(() -> Component.literal(
                "Materialization candidates for " + transponderId
                        + ": status=" + lookup.status()
                        + ", reason=" + lookup.reasonCode()
                        + ", count=" + lookup.candidates().size()
                        + ", sableShip=" + body.sableShipId()
                        + ", dimension=" + body.dimension().location()
                        + ", canonical=" + body.canonicalPointer().map(StoredBodyPointer::selector).orElse("none")
        ), false);
        long readableCount = lookup.candidates().stream().filter(StoredBodyCandidate::readable).count();
        for (StoredBodyCandidate candidate : lookup.candidates()) {
            boolean selected = lookup.selected().filter(candidate::equals).isPresent();
            source.sendSuccess(() -> materializationCandidateLine(
                    source,
                    transponderId,
                    lookup,
                    readableCount,
                    candidate,
                    selected
            ), false);
        }
        return Math.max(1, lookup.candidates().size());
    }

    private static Component materializationCandidateLine(
            CommandSourceStack source,
            UUID transponderId,
            StoredBodyLookupResult lookup,
            long readableCount,
            StoredBodyCandidate candidate,
            boolean selected
    ) {
        String distance = source.getLevel().dimension().equals(candidate.dimension())
                ? String.format(java.util.Locale.ROOT, "%.1fm", source.getPosition().distanceTo(candidate.posePosition()))
                : "other dimension";
        MutableComponent line = Component.literal(selected ? "* " : "- ")
                .append(Component.literal(candidate.pointer().selector()).withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal(" | " + candidate.health()))
                .append(Component.literal(" | pose=" + formatPosition(candidate.posePosition())))
                .append(Component.literal(" | distance=" + distance));
        if (selected) {
            line.append(Component.literal(" | selected").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(actionButton(
                            "[show]",
                            "/aal materialization show " + transponderId + " " + candidate.pointer().selector(),
                            "Highlight this stored body's saved pose. This does not load the ship."
                    ));
        } else if (candidate.readable()) {
            line.append(Component.literal(" "))
                    .append(actionButton(
                            "[show]",
                            "/aal materialization show " + transponderId + " " + candidate.pointer().selector(),
                            "Highlight this stored body's saved pose. This does not load the ship."
                    ))
                    .append(Component.literal(" "))
                    .append(actionButton(
                            "[select]",
                            "/aal materialization select " + transponderId + " " + candidate.pointer().selector(),
                            "Mark this stored body pointer as canonical metadata. No storage is deleted."
                    ));
            if (lookup.selected().isPresent() && readableCount > 1L) {
                line.append(Component.literal(" "))
                        .append(actionButton(
                                "[quarantine-index]",
                                "/aal materialization quarantine " + transponderId + " " + candidate.pointer().selector(),
                                "Remove this non-selected active holding index only. Payload is preserved."
                        ));
            }
        }
        return line.withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static int selectMaterializationCandidate(
            CommandSourceStack source,
            String transponderText,
            String pointerText
    ) {
        UUID transponderId = parseUuid(source, transponderText, "transponder id");
        if (transponderId == null) {
            return 0;
        }
        Optional<StoredBodyPointer> pointer = StoredBodyPointer.parse(pointerText);
        if (pointer.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Invalid pointer. Expected chunkX,chunkZ,storageIndex,subLevelIndex."
            ));
            return 0;
        }
        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = ShipBodyDirectorySavedData
                .get(source.getServer())
                .byTransponder(transponderId);
        if (identity.isEmpty()) {
            source.sendFailure(Component.literal("No body-directory record exists for transponder " + transponderId + "."));
            return 0;
        }
        ShipBodyDirectorySavedData.BodyIdentity body = identity.get();
        Optional<StoredBodyCandidate> candidate = SableStoredShipRepository.candidates(
                        source.getServer(),
                        body.dimension(),
                        body.sableShipId()
                ).stream()
                .filter(stored -> stored.pointer().equals(pointer.get()))
                .findFirst();
        if (candidate.isEmpty()) {
            source.sendFailure(Component.literal("That pointer is not a current candidate for this transponder."));
            return 0;
        }
        if (!candidate.get().readable()) {
            source.sendFailure(Component.literal("That stored-body candidate is structurally corrupt and cannot be selected."));
            return 0;
        }
        boolean selected = ShipBodyDirectorySavedData.selectCanonicalPointer(
                source.getServer(),
                transponderId,
                body.sableShipId(),
                pointer.get(),
                source.getLevel().getGameTime()
        );
        if (!selected) {
            source.sendFailure(Component.literal("Body identity changed while selecting the candidate; nothing was changed."));
            return 0;
        }
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "Admin selected canonical Sable stored-body pointer: actor={} transponder={} sableShip={} dimension={} pointer={} action=metadata_only_no_storage_deleted",
                source.getTextName(),
                transponderId,
                body.sableShipId(),
                body.dimension().location(),
                pointer.get()
        );
        source.sendSuccess(() -> Component.literal(
                "Selected canonical pointer " + pointer.get().selector()
                        + " for transponder " + transponderId + ". No Sable storage was deleted."
        ), true);
        return 1;
    }

    private static int showMaterializationCandidate(
            CommandSourceStack source,
            String transponderText,
            String pointerText
    ) {
        UUID transponderId = parseUuid(source, transponderText, "transponder id");
        if (transponderId == null) {
            return 0;
        }
        Optional<StoredBodyPointer> pointer = StoredBodyPointer.parse(pointerText);
        if (pointer.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Invalid pointer. Expected chunkX,chunkZ,storageIndex,subLevelIndex."
            ));
            return 0;
        }
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can highlight stored body candidates."));
            return 0;
        }
        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = ShipBodyDirectorySavedData
                .get(source.getServer())
                .byTransponder(transponderId);
        if (identity.isEmpty()) {
            source.sendFailure(Component.literal("No body-directory record exists for transponder " + transponderId + "."));
            return 0;
        }
        ShipBodyDirectorySavedData.BodyIdentity body = identity.get();
        Optional<StoredBodyCandidate> candidate = SableStoredShipRepository.candidates(
                        source.getServer(),
                        body.dimension(),
                        body.sableShipId()
                ).stream()
                .filter(stored -> stored.pointer().equals(pointer.get()))
                .findFirst();
        if (candidate.isEmpty()) {
            source.sendFailure(Component.literal("That pointer is not a current candidate for this transponder."));
            return 0;
        }
        if (!source.getLevel().dimension().equals(candidate.get().dimension())) {
            source.sendFailure(Component.literal(
                    "Candidate is in " + candidate.get().dimension().location()
                            + "; change dimension before highlighting it."
            ));
            return 0;
        }
        BlockPos poseBlock = BlockPos.containing(candidate.get().posePosition());
        PacketDistributor.sendToPlayer(player, new ShowShipTransponderHighlightPayload(poseBlock, SHOW_HIGHLIGHT_TICKS));
        source.sendSuccess(() -> Component.literal(
                "Highlighted stored-body pose " + formatPosition(candidate.get().posePosition())
                        + " for " + SHOW_HIGHLIGHT_TICKS / 20 + "s. This is a saved pose marker, not a live ship outline."
        ), false);
        return 1;
    }

    private static int quarantineMaterializationCandidate(
            CommandSourceStack source,
            String transponderText,
            String pointerText
    ) {
        UUID transponderId = parseUuid(source, transponderText, "transponder id");
        if (transponderId == null) {
            return 0;
        }
        Optional<StoredBodyPointer> pointer = StoredBodyPointer.parse(pointerText);
        if (pointer.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Invalid pointer. Expected chunkX,chunkZ,storageIndex,subLevelIndex."
            ));
            return 0;
        }
        Optional<ShipBodyDirectorySavedData.BodyIdentity> identity = ShipBodyDirectorySavedData
                .get(source.getServer())
                .byTransponder(transponderId);
        if (identity.isEmpty()) {
            source.sendFailure(Component.literal("No body-directory record exists for transponder " + transponderId + "."));
            return 0;
        }

        ShipBodyDirectorySavedData.BodyIdentity body = identity.get();
        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                source.getServer(),
                Optional.of(transponderId),
                body.dimension(),
                body.sableShipId()
        );
        Optional<StoredBodyCandidate> candidate = lookup.candidates().stream()
                .filter(stored -> stored.pointer().equals(pointer.get()))
                .findFirst();
        Optional<StoredBodyPointer> selectedPointer = lookup.selected()
                .map(StoredBodyCandidate::pointer)
                .or(body::canonicalPointer);
        long readableCount = lookup.candidates().stream().filter(StoredBodyCandidate::readable).count();
        boolean pointerReadable = candidate.filter(StoredBodyCandidate::readable).isPresent();
        boolean hasReadableAlternative = lookup.candidates().stream()
                .filter(StoredBodyCandidate::readable)
                .map(StoredBodyCandidate::pointer)
                .anyMatch(candidatePointer -> !candidatePointer.equals(pointer.get()));
        boolean hasSelectedAlternative = selectedPointer
                .filter(candidatePointer -> !candidatePointer.equals(pointer.get()))
                .isPresent();
        if (!hasReadableAlternative && !hasSelectedAlternative) {
            source.sendFailure(Component.literal(
                    "Refusing to quarantine without another readable or selected stored-body pointer for this transponder."
            ));
            return 0;
        }
        if (selectedPointer.filter(pointer.get()::equals).isPresent()) {
            source.sendFailure(Component.literal("Refusing to quarantine the selected canonical pointer."));
            return 0;
        }

        boolean quarantined = SableStoredShipRepository.quarantineAdminPointerIndex(
                source.getServer(),
                body.dimension(),
                body.sableShipId(),
                pointer.get(),
                "admin_manual_noncanonical_duplicate"
        );
        if (!quarantined) {
            source.sendFailure(Component.literal(
                    "Pointer could not be quarantined; it may already be unindexed or Sable storage was unavailable. No payload was deleted."
            ));
            return 0;
        }
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "Admin quarantined non-canonical Sable stored-body pointer: actor={} transponder={} sableShip={} dimension={} pointer={} selectedPointer={} pointerReadable={} readableAlternatives={} action=index_removed_payload_preserved",
                source.getTextName(),
                transponderId,
                body.sableShipId(),
                body.dimension().location(),
                pointer.get(),
                selectedPointer.map(StoredBodyPointer::selector).orElse("none"),
                pointerReadable,
                readableCount
        );
        source.sendSuccess(() -> Component.literal(
                "Quarantined non-canonical pointer " + pointer.get().selector()
                        + " for transponder " + transponderId + ". Payload was preserved."
        ), true);
        return 1;
    }

    private static int quarantineDanglingMaterializationIndex(
            CommandSourceStack source,
            String dimensionText,
            String pointerText
    ) {
        ResourceKey<Level> dimension = parseDimension(dimensionText);
        if (source.getServer().getLevel(dimension) == null) {
            source.sendFailure(Component.literal("Dimension is not loaded or does not exist: " + dimensionText));
            return 0;
        }
        Optional<StoredBodyPointer> pointer = StoredBodyPointer.parse(pointerText);
        if (pointer.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Invalid pointer. Expected chunkX,chunkZ,storageIndex,subLevelIndex."
            ));
            return 0;
        }

        boolean quarantined = SableStoredShipRepository.quarantineDanglingIndex(
                source.getServer(),
                dimension,
                pointer.get(),
                "admin_manual_dangling_index"
        );
        if (!quarantined) {
            source.sendFailure(Component.literal(
                    "Pointer was not quarantined. It may be readable, missing, or unavailable. No payload was deleted."
            ));
            return 0;
        }
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "Admin quarantined dangling Sable stored-body index: actor={} dimension={} pointer={} proof=payload_missing action=index_removed_payload_none",
                source.getTextName(),
                dimension.location(),
                pointer.get()
        );
        source.sendSuccess(() -> Component.literal(
                "Quarantined dangling index " + pointer.get().selector()
                        + " in " + dimension.location() + ". No stored body payload was deleted."
        ), true);
        return 1;
    }

    private static int showRuntimePlayback(CommandSourceStack source, String runtimeIdText) {
        UUID runtimeId = parseUuid(source, runtimeIdText, "runtime id");
        if (runtimeId == null) {
            return 0;
        }

        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use runtime show."));
            return 0;
        }

        VehicleRoutePlaybackService.RuntimePlaybackSummary summary = findVisibleRuntimePlayback(source, runtimeId);
        if (summary == null) {
            AirshipScheduleExecutionService.ScheduleRuntimeSummary scheduleOnly =
                    findVisibleScheduleOnlyRuntime(source, runtimeId);
            if (scheduleOnly == null) {
                source.sendFailure(Component.literal("No runtime record found for " + runtimeIdText + "."));
                return 0;
            }
            if (!source.getLevel().dimension().equals(scheduleOnly.dimension())) {
                source.sendFailure(Component.literal(
                        "Runtime " + runtimeIdText + " was last recorded in "
                                + scheduleOnly.dimension().location() + " at "
                                + scheduleOnly.transponderPos().toShortString() + "."
                ));
                return 0;
            }
            PacketDistributor.sendToPlayer(player, new ShowShipTransponderHighlightPayload(
                    scheduleOnly.transponderPos(),
                    SHOW_HIGHLIGHT_TICKS
            ));
            source.sendSuccess(() -> Component.literal(
                    "Highlighted the last saved transponder position for orphan runtime " + runtimeIdText + "."
            ), false);
            return 1;
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
        UUID runtimeId = parseUuid(source, runtimeIdText, "runtime id");
        if (runtimeId == null) {
            return 0;
        }

        VehicleRoutePlaybackService.RuntimePlaybackSummary summary = findVisibleRuntimePlayback(source, runtimeId);
        if (summary == null) {
            AirshipScheduleExecutionService.ScheduleRuntimeSummary scheduleOnly =
                    findVisibleScheduleOnlyRuntime(source, runtimeId);
            if (scheduleOnly == null) {
                source.sendFailure(Component.literal("No runtime record found for " + runtimeIdText + "."));
                return 0;
            }
            ServerLevel scheduleLevel = source.getServer().getLevel(scheduleOnly.dimension());
            if (scheduleLevel == null) {
                source.sendFailure(Component.literal("Could not resolve the orphan runtime dimension for " + runtimeIdText + "."));
                return 0;
            }
            AirshipScheduleExecutionService schedules = AutomatedLogisticsServices.SCHEDULES;
            schedules.stop(scheduleLevel, scheduleOnly.transponderId());
            if (schedules.isRunning(scheduleOnly.transponderId())) {
                source.sendFailure(Component.literal("Could not kill orphan schedule runtime " + runtimeIdText + "."));
                return 0;
            }
            CreateAeronauticsAutomatedLogistics.LOGGER.info(
                    "Admin runtime cleanup applied source=runtime_kill actor={} transponder={} activeRoute={} reason=manual_orphan_schedule_cleanup action=schedule_runtime_removed",
                    source.getTextName(),
                    scheduleOnly.transponderId(),
                    scheduleOnly.activeRouteId().map(id -> id.value().toString()).orElse("none")
            );
            source.sendSuccess(() -> Component.literal(
                    "Killed orphan schedule runtime " + runtimeIdText + ". Persistent routes and the installed schedule were not deleted."
            ), true);
            return 1;
        }

        ServerLevel level = source.getServer().getLevel(summary.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Could not resolve the runtime dimension for " + runtimeIdText + "."));
            return 0;
        }
        AirshipScheduleExecutionService schedules = AutomatedLogisticsServices.SCHEDULES;
        boolean killed = false;
        if (summary.transponderId().isPresent()) {
            schedules.stop(level, summary.transponderId().get());
            killed = true;
        } else {
            killed = AutomatedLogisticsServices.PLAYBACK.stopRuntimePlayback(source.getServer(), summary.routeId(), FailureReason.NONE);
        }
        if (!killed) {
            source.sendFailure(Component.literal("Could not kill runtime " + runtimeIdText + "."));
            return 0;
        }
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

    private static ResourceKey<Level> parseDimension(String rawId) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(rawId));
    }

    private static VehicleRoutePlaybackService.RuntimePlaybackSummary findVisibleRuntimePlayback(
            CommandSourceStack source,
            UUID runtimeId
    ) {
        return visibleRuntimeSummaries(source).stream()
                .filter(summary ->
                        summary.transponderId().filter(runtimeId::equals).isPresent()
                                || summary.routeId().value().equals(runtimeId))
                .findFirst()
                .orElse(null);
    }

    private static AirshipScheduleExecutionService.ScheduleRuntimeSummary findVisibleScheduleOnlyRuntime(
            CommandSourceStack source,
            UUID transponderId
    ) {
        return visibleScheduleOnlyRuntimeSummaries(source).stream()
                .filter(summary -> summary.transponderId().equals(transponderId))
                .findFirst()
                .orElse(null);
    }

    private static List<VehicleRoutePlaybackService.RuntimePlaybackSummary> visibleRuntimeSummaries(CommandSourceStack source) {
        Optional<UUID> ownerFilter = commandOwnerFilter(source);
        return AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(source.getServer()).stream()
                .filter(summary -> ownerFilter.isEmpty() || summary.ownerId().filter(ownerFilter.get()::equals).isPresent())
                .toList();
    }

    private static List<AirshipScheduleExecutionService.ScheduleRuntimeSummary> visibleScheduleOnlyRuntimeSummaries(
            CommandSourceStack source
    ) {
        if (!source.hasPermission(2)) {
            return List.of();
        }
        List<VehicleRoutePlaybackService.RuntimePlaybackSummary> playbacks =
                AutomatedLogisticsServices.PLAYBACK.runtimePlaybackSummaries(source.getServer());
        return AutomatedLogisticsServices.SCHEDULES.runtimeScheduleSummaries().stream()
                .filter(schedule -> playbacks.stream().noneMatch(playback ->
                        playback.transponderId().filter(schedule.transponderId()::equals).isPresent()
                                || schedule.activeRouteId().filter(playback.routeId()::equals).isPresent()))
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

    private static Component scheduleOnlyRuntimeSummaryLine(
            AirshipScheduleExecutionService.ScheduleRuntimeSummary summary
    ) {
        String shipName = summary.shipName() == null || summary.shipName().isBlank()
                ? summary.transponderId().toString().substring(0, 8)
                : summary.shipName();
        MutableComponent line = Component.literal("- ")
                .append(Component.literal(shipName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | "))
                .append(Component.literal(summary.scheduleTitle()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | "))
                .append(Component.literal("ORPHAN_SCHEDULE").withStyle(ChatFormatting.RED))
                .append(Component.literal(" | entry " + summary.entryIndex()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" | " + summary.currentStationName()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" | " + summary.dimension().location()).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" | " + summary.transponderId().toString().substring(0, 8))
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal(summary.transponderId().toString())
                        ))));
        line.append(Component.literal("  "));
        line.append(actionButton(
                "[show]",
                "/aal runtime show " + summary.transponderId(),
                "Highlight the last saved transponder position"
        ));
        line.append(Component.literal(" "));
        line.append(actionButton(
                "[kill]",
                "/aal runtime kill " + summary.transponderId(),
                "Remove this orphan runtime only; persistent routes and schedules are preserved"
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
            case "DOCK_QUEUED" -> ChatFormatting.AQUA;
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

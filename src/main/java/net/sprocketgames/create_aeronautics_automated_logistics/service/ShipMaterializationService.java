package net.sprocketgames.create_aeronautics_automated_logistics.service;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.SableStoredShipRepository;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.SableStoredShipMaterializer;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.ShipBodyDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.StoredBodyLookupResult;
import net.sprocketgames.create_aeronautics_automated_logistics.materialization.StoredBodyPointer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

public final class ShipMaterializationService {
    private static final long LIVE_LOOKUP_LOG_HEARTBEAT_TICKS = 1200L;
    private static final long EXPECTED_WAIT_LOG_HEARTBEAT_TICKS = 1200L;
    private static final long CONTROLLER_REGISTRATION_WAIT_TICKS = 100L;
    private static final int MAX_LIVE_LOOKUP_LOG_STATES = 2048;
    private static final Map<MinecraftServer, Map<LiveLookupLogKey, LiveLookupLogState>> LIVE_LOOKUP_LOGS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<MaterializationLogKey, LiveLookupLogState>> MATERIALIZATION_LOGS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, MaterializationLifecycle>> MATERIALIZATION_LIFECYCLES =
            new WeakHashMap<>();

    public enum MaterializationResultType {
        LOADED_BODY_AVAILABLE,
        LIVE_BODY_MISSING,
        STORED_BODY_AVAILABLE,
        STORED_BODY_MISSING,
        STORED_BODY_AMBIGUOUS,
        STORED_BODY_CORRUPT,
        SUBLEVEL_LOAD_FAILED,
        CHUNK_LOAD_NOT_READY,
        CONTROLLER_MISSING,
        UNSAFE_TO_RELOCATE_OR_MATERIALIZE,
        SABLE_API_UNAVAILABLE,
        STARTUP_GRACE_WAITING,
        SABLE_HOLDING_BODY_WAITING,
        CONTROLLER_REGISTRATION_WAITING,
        UNKNOWN_FAILURE,
        MATERIALIZED
    }

    public record MaterializationRequest(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId,
            Optional<UUID> sableShipId,
            Optional<RouteId> routeId,
            Optional<Integer> currentLegIndex,
            Optional<UUID> stationId,
            Optional<BlockPos> localControllerPos,
            Vec3 targetPose,
            String description,
            String reasonCode,
            boolean forceAtHoldPoint,
            boolean attemptAllowed
    ) {
        public MaterializationRequest {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(dimension, "dimension");
            transponderId = Objects.requireNonNull(transponderId, "transponderId");
            sableShipId = Objects.requireNonNull(sableShipId, "sableShipId");
            routeId = Objects.requireNonNull(routeId, "routeId");
            currentLegIndex = Objects.requireNonNull(currentLegIndex, "currentLegIndex");
            stationId = Objects.requireNonNull(stationId, "stationId");
            localControllerPos = Objects.requireNonNull(localControllerPos, "localControllerPos");
            Objects.requireNonNull(targetPose, "targetPose");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }

    public record MaterializationResult(
            MaterializationResultType type,
            boolean success,
            String reasonCode,
            String message
    ) {
        public MaterializationResult {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(message, "message");
        }
    }

    public record LiveBodyLookupRequest(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            VehicleControllerRef controllerRef,
            Optional<UUID> transponderId,
            Optional<UUID> sableShipId,
            Optional<RouteId> routeId,
            Optional<Integer> currentLegIndex,
            Optional<UUID> stationId,
            String source,
            String reasonCode
    ) {
        public LiveBodyLookupRequest {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(controllerRef, "controllerRef");
            transponderId = Objects.requireNonNull(transponderId, "transponderId");
            sableShipId = Objects.requireNonNull(sableShipId, "sableShipId");
            routeId = Objects.requireNonNull(routeId, "routeId");
            currentLegIndex = Objects.requireNonNull(currentLegIndex, "currentLegIndex");
            stationId = Objects.requireNonNull(stationId, "stationId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }

    public record LiveBodyLookupResult(
            MaterializationResult result,
            Optional<VehicleController> controller
    ) {
        public LiveBodyLookupResult {
            Objects.requireNonNull(result, "result");
            controller = Objects.requireNonNull(controller, "controller");
        }
    }

    public MaterializationResult materializeStoredBodyAt(MaterializationRequest request) {
        return materializeStoredBodyAt(request, request::attemptAllowed);
    }

    public MaterializationResult materializeStoredBodyAt(MaterializationRequest request, BooleanSupplier attemptGate) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(attemptGate, "attemptGate");

        logRequest("materialization request", request, MaterializationResultType.UNKNOWN_FAILURE, request.reasonCode());

        ServerLevel level = request.server().getLevel(request.dimension());
        if (level == null) {
            return failure(
                    request,
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    "dimension_unavailable",
                    "The stored ship's dimension is not loaded."
            );
        }
        if (request.sableShipId().isEmpty() || request.localControllerPos().isEmpty()) {
            return failure(
                    request,
                    MaterializationResultType.CONTROLLER_MISSING,
                    "controller_ref_incomplete",
                    "Controller reference is missing a Sable ship id or local controller position."
            );
        }

        UUID shipId = request.sableShipId().get();
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return failure(
                    request,
                    MaterializationResultType.SABLE_API_UNAVAILABLE,
                    "sable_container_unavailable",
                    "Sable sublevel container is not available."
            );
        }
        if (!StationChunkLoadingService.isStartupRestoreReady()) {
            return failure(
                    request,
                    MaterializationResultType.STARTUP_GRACE_WAITING,
                    "startup_restore_not_ready",
                    "Startup restore readiness has not completed."
            );
        }
        MaterializationLifecycle lifecycle = lifecycle(request.server(), shipId);
        if (container.getSubLevel(shipId) != null) {
            Optional<BlockPos> localControllerPos = request.localControllerPos();
            if (localControllerPos.isPresent()) {
                Optional<SableSubLevelVehicleController> controller = SableSubLevelVehicleController.resolveControllerBlock(
                        level,
                        shipId,
                        localControllerPos.get()
                );
                if (controller.isPresent()) {
                    lifecycle.transitionToLive(request, controller.get(), level.getGameTime(), "live_body_available");
                } else {
                    lifecycle.waitForController(request, level.getGameTime(), CONTROLLER_REGISTRATION_WAIT_TICKS, "live_body_controller_registration_pending");
                    return failure(
                            request,
                            MaterializationResultType.CONTROLLER_REGISTRATION_WAITING,
                            "live_body_controller_registration_pending",
                            "Sable body is loaded, but the expected controller has not registered yet."
                    );
                }
            }
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.LOADED_BODY_AVAILABLE,
                    true,
                    "live_body_available",
                    "Sable body is already loaded."
            );
            logResult("live body lookup result", request, result);
            return result;
        }
        Optional<MaterializationResult> lifecycleBlocker = lifecycle.blocker(level.getGameTime());
        if (lifecycleBlocker.isPresent()) {
            MaterializationResult result = lifecycleBlocker.get();
            logResult("restore/materialize refused", request, result);
            return result;
        }
        HoldingSubLevel holdingBody = container.getHoldingChunkMap().getHoldingSubLevel(shipId);
        Optional<StoredBodyPointer> holdingPointer = holdingBody == null
                ? Optional.empty()
                : Optional.of(StoredBodyPointer.fromSable(holdingBody.pointer()));
        if (holdingBody != null) {
            lifecycle.transitionToHeldBySable(request, holdingPointer.get(), level.getGameTime(), "sable_holding_body_present");
            logResult(
                    "live body lookup result",
                    request,
                    new MaterializationResult(
                            MaterializationResultType.SABLE_HOLDING_BODY_WAITING,
                            false,
                            "sable_holding_body_present",
                            "Sable already owns this body in a holding chunk; lifecycle ownership will decide whether it can be consumed."
                    )
            );
        }
        logResult(
                "live body lookup result",
                request,
                new MaterializationResult(
                        MaterializationResultType.LIVE_BODY_MISSING,
                        false,
                        "live_body_missing",
                        "Live Sable body is not loaded."
                )
        );

        BlockPos targetBlock = BlockPos.containing(request.targetPose());
        if (!level.isLoaded(targetBlock)) {
            return failure(
                    request,
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    "target_chunk_unloaded",
                    "Target pose chunk is not loaded: " + new ChunkPos(targetBlock)
            );
        }
        if (!StationChunkLoadingService.isStartupRestoreReady()) {
            return failure(
                    request,
                    MaterializationResultType.STARTUP_GRACE_WAITING,
                    "startup_restore_not_ready",
                    "Startup restore readiness has not completed."
            );
        }

        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                request.server(),
                request.transponderId(),
                request.dimension(),
                shipId
        );
        MaterializationResult storedLookup = materializationResult(lookup);
        logResult("stored body lookup result", request, storedLookup);
        if (lookup.selected().isEmpty()) {
            lifecycle.transitionFromLookupFailure(request, lookup, level.getGameTime());
            return storedLookup;
        }
        StoredBodyPointer selectedPointer = lookup.selected().get().pointer();
        if (holdingPointer.isPresent() && !holdingPointer.get().equals(selectedPointer)) {
            lifecycle.transitionToAmbiguous(
                    request,
                    holdingPointer.get(),
                    level.getGameTime(),
                    "holding_pointer_not_canonical_selected_pointer=" + selectedPointer.selector()
            );
            return failure(
                    request,
                    MaterializationResultType.STORED_BODY_AMBIGUOUS,
                    "holding_pointer_not_canonical",
                    "Sable is holding pointer " + holdingPointer.get().selector()
                            + " but the canonical stored-body lookup selected " + selectedPointer.selector()
                            + "; refusing to load either pointer."
            );
        }
        lifecycle.transitionToStoredCanonical(request, selectedPointer, level.getGameTime(), lookup.reasonCode());
        if (!attemptGate.getAsBoolean()) {
            return failure(
                    request,
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    "materialization_retry_cooldown",
                    "Materialization retry cooldown has not elapsed."
            );
        }

        Optional<MaterializationResult> beginRefusal = lifecycle.tryBeginLoad(
                request,
                level.getGameTime(),
                selectedPointer,
                holdingPointer.isPresent() ? "consume_sable_holding_pointer" : "consume_stored_pointer"
        );
        if (beginRefusal.isPresent()) {
            MaterializationResult result = beginRefusal.get();
            logResult("restore/materialize refused", request, result);
            return result;
        }

        if (holdingBody != null) {
            logRequest("restore/materialize attempt", request, MaterializationResultType.SABLE_HOLDING_BODY_WAITING, "attempting_sable_holding_materialize");
            SableStoredShipMaterializer.Result materialized = SableStoredShipMaterializer.materialize(
                    new SableStoredShipMaterializer.Request(
                            request.server(),
                            request.transponderId(),
                            request.dimension(),
                            shipId,
                            request.localControllerPos().get(),
                            request.targetPose(),
                            request.description(),
                            Optional.of(selectedPointer)
                    )
            );
            MaterializationResult result = materializationResult(materialized);
            if (result.type() == MaterializationResultType.CONTROLLER_REGISTRATION_WAITING
                    || result.type() == MaterializationResultType.CONTROLLER_MISSING
                    || result.type() == MaterializationResultType.SABLE_HOLDING_BODY_WAITING) {
                lifecycle.waitForController(request, level.getGameTime(), CONTROLLER_REGISTRATION_WAIT_TICKS, result.reasonCode());
                result = new MaterializationResult(
                        MaterializationResultType.CONTROLLER_REGISTRATION_WAITING,
                        false,
                        "controller_registration_wait_started",
                        "Sable accepted the holding body load; waiting for the expected controller to register before retrying."
                );
            } else if (result.success()) {
                if (materialized.controller().isPresent()) {
                    lifecycle.transitionToLive(request, materialized.controller().get(), level.getGameTime(), result.reasonCode());
                } else {
                    lifecycle.transitionToLiveWithoutController(request, level.getGameTime(), result.reasonCode());
                }
            } else {
                lifecycle.transitionToFaulted(request, Optional.of(selectedPointer), level.getGameTime(), result.reasonCode());
            }
            logResult(materialized.success() ? "restore/materialize success" : "restore/materialize failure", request, result);
            return result;
        }

        logRequest("restore/materialize attempt", request, MaterializationResultType.STORED_BODY_AVAILABLE, "attempting_materialize");
        lookup.selected().ifPresent(selected -> SableStoredShipRepository.quarantineDuplicateIndexesExcept(
                request.server(),
                request.dimension(),
                shipId,
                selected.pointer(),
                "before_materialize_" + request.reasonCode()
        ));
        SableStoredShipMaterializer.Result materialized = SableStoredShipMaterializer.materialize(
                new SableStoredShipMaterializer.Request(
                        request.server(),
                        request.transponderId(),
                        request.dimension(),
                        shipId,
                        request.localControllerPos().get(),
                        request.targetPose(),
                        request.description(),
                        Optional.of(selectedPointer)
                )
        );
        MaterializationResult result = materializationResult(materialized);
        if (result.type() == MaterializationResultType.CONTROLLER_REGISTRATION_WAITING
                || result.type() == MaterializationResultType.CONTROLLER_MISSING
                || result.type() == MaterializationResultType.SABLE_HOLDING_BODY_WAITING) {
            lifecycle.waitForController(request, level.getGameTime(), CONTROLLER_REGISTRATION_WAIT_TICKS, result.reasonCode());
            result = new MaterializationResult(
                    MaterializationResultType.CONTROLLER_REGISTRATION_WAITING,
                    false,
                    "controller_registration_wait_started",
                    "Sable accepted the stored body load; waiting for the expected controller to register before retrying."
            );
        } else if (result.success()) {
            if (materialized.controller().isPresent()) {
                lifecycle.transitionToLive(request, materialized.controller().get(), level.getGameTime(), result.reasonCode());
            } else {
                lifecycle.transitionToLiveWithoutController(request, level.getGameTime(), result.reasonCode());
            }
        } else {
            lifecycle.transitionToFaulted(request, Optional.of(selectedPointer), level.getGameTime(), result.reasonCode());
        }
        logResult(materialized.success() ? "restore/materialize success" : "restore/materialize failure", request, result);
        return result;
    }

    public LiveBodyLookupResult resolveLiveBody(LiveBodyLookupRequest request) {
        Objects.requireNonNull(request, "request");

        ServerLevel level = request.server().getLevel(request.dimension());
        if (level == null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    false,
                    "dimension_unavailable",
                    "Dimension is not loaded."
            );
            logLiveLookupResult("live body lookup result", request, result);
            return new LiveBodyLookupResult(result, Optional.empty());
        }

        boolean sableController = request.controllerRef().controllerType().equals(SableSubLevelVehicleController.TYPE);
        Optional<VehicleController> resolved = VehicleControllerResolver.resolve(level, request.controllerRef());
        if (resolved.isPresent()) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.LOADED_BODY_AVAILABLE,
                    true,
                    "live_body_available",
                    "Live vehicle controller and body are available."
            );
            logLiveLookupResult("live body lookup result", request, result);
            return new LiveBodyLookupResult(result, resolved);
        }

        if (!sableController) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.CONTROLLER_MISSING,
                    false,
                    "live_controller_missing",
                    "Live vehicle controller could not be resolved."
            );
            logLiveLookupResult("live body lookup result", request, result);
            return new LiveBodyLookupResult(result, Optional.empty());
        }

        if (request.sableShipId().isEmpty() || request.controllerRef().controllerPos().isEmpty()) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.CONTROLLER_MISSING,
                    false,
                    "controller_ref_incomplete",
                    "Sable controller reference is missing a ship id or controller position."
            );
            logLiveLookupResult("live body lookup result", request, result);
            return new LiveBodyLookupResult(result, Optional.empty());
        }

        if (!StationChunkLoadingService.isStartupRestoreReady()) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.STARTUP_GRACE_WAITING,
                    false,
                    "startup_restore_not_ready",
                    "Startup restore readiness has not completed."
            );
            logLiveLookupResult("live body lookup result", request, result);
            return new LiveBodyLookupResult(result, Optional.empty());
        }

        MaterializationResult liveMissing = new MaterializationResult(
                MaterializationResultType.LIVE_BODY_MISSING,
                false,
                "live_body_missing",
                "Live Sable body is not loaded."
        );
        logLiveLookupResult("live body lookup result", request, liveMissing);

        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                request.server(),
                request.transponderId(),
                request.dimension(),
                request.sableShipId().get()
        );
        MaterializationResult storedLookup = materializationResult(lookup);
        logLiveLookupResult("stored body lookup result", request, storedLookup);
        return new LiveBodyLookupResult(storedLookup, Optional.empty());
    }

    public String describeStoredBody(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID shipId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(shipId, "shipId");
        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(server, Optional.empty(), dimension, shipId);
        return "dimension=" + dimension.location()
                + ", sableShip=" + shipId
                + ", storedStatus=" + lookup.status()
                + ", candidates=" + lookup.candidates().size()
                + ", selectedPointer=" + lookup.selected().map(candidate -> candidate.pointer().toString()).orElse("none")
                + ", reason=" + lookup.reasonCode();
    }

    public MaterializationResult bodyAvailability(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId,
            UUID shipId,
            Optional<RouteId> routeId,
            Optional<UUID> stationId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        transponderId = Objects.requireNonNull(transponderId, "transponderId");
        Objects.requireNonNull(shipId, "shipId");
        routeId = Objects.requireNonNull(routeId, "routeId");
        stationId = Objects.requireNonNull(stationId, "stationId");

        logBodyLookup("materialization request", transponderId, shipId, routeId, stationId, dimension, "body_availability");
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    false,
                    "dimension_unavailable",
                    "Dimension is not loaded."
            );
            logBodyResult(server, "live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
            return result;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.SABLE_API_UNAVAILABLE,
                    false,
                    "sable_container_unavailable",
                    "Sable sublevel container is not available."
            );
            logBodyResult(server, "live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
            return result;
        }
        if (container.getSubLevel(shipId) != null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.LOADED_BODY_AVAILABLE,
                    true,
                    "live_body_available",
                    "Sable body is already loaded."
            );
            logBodyResult(server, "live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
            return result;
        }
        if (container.getHoldingChunkMap().getHoldingSubLevel(shipId) != null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.SABLE_HOLDING_BODY_WAITING,
                    false,
                    "sable_holding_body_present",
                    "Sable already owns this body in a holding chunk."
            );
            logBodyResult(server, "live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
            return result;
        }
        MaterializationResult liveMissing = new MaterializationResult(
                MaterializationResultType.LIVE_BODY_MISSING,
                false,
                "live_body_missing",
                "Live Sable body is not loaded."
        );
        logBodyResult(server, "live body lookup result", transponderId, shipId, routeId, stationId, dimension, liveMissing);

        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                server,
                transponderId,
                dimension,
                shipId
        );
        MaterializationResult storedResult = materializationResult(lookup);
        logBodyResult(server, "stored body lookup result", transponderId, shipId, routeId, stationId, dimension, storedResult);
        return storedResult;
    }

    public boolean hasStoredBody(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            UUID shipId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(shipId, "shipId");
        return SableStoredShipRepository.lookup(server, Optional.empty(), dimension, shipId).selected().isPresent();
    }

    private static MaterializationResult materializationResult(StoredBodyLookupResult lookup) {
        return switch (lookup.status()) {
            case VERIFIED_SINGLE, VERIFIED_POINTER -> new MaterializationResult(
                    MaterializationResultType.STORED_BODY_AVAILABLE,
                    true,
                    lookup.reasonCode(),
                    "Stored Sable body is available from " + lookup.candidates().size() + " candidate(s)."
            );
            case AMBIGUOUS -> new MaterializationResult(
                    MaterializationResultType.STORED_BODY_AMBIGUOUS,
                    false,
                    lookup.reasonCode(),
                    "Multiple stored Sable bodies match and no verified pointer proves which one is canonical."
            );
            case READ_FAILED -> new MaterializationResult(
                    MaterializationResultType.STORED_BODY_CORRUPT,
                    false,
                    lookup.reasonCode(),
                    "Stored Sable body candidates exist but none are structurally readable."
            );
            case NOT_READY -> new MaterializationResult(
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    false,
                    lookup.reasonCode(),
                    "Stored Sable body lookup is not ready."
            );
            case NOT_FOUND -> new MaterializationResult(
                    MaterializationResultType.STORED_BODY_MISSING,
                    false,
                    lookup.reasonCode(),
                    "Stored Sable body is not visible after a fresh read-only scan."
            );
        };
    }

    private static MaterializationResult materializationResult(SableStoredShipMaterializer.Result result) {
        MaterializationResultType type = switch (result.type()) {
            case ALREADY_LOADED -> MaterializationResultType.LOADED_BODY_AVAILABLE;
            case MATERIALIZED -> MaterializationResultType.MATERIALIZED;
            case MISSING -> MaterializationResultType.STORED_BODY_MISSING;
            case AMBIGUOUS -> MaterializationResultType.STORED_BODY_AMBIGUOUS;
            case CORRUPT -> MaterializationResultType.STORED_BODY_CORRUPT;
            case NOT_READY -> MaterializationResultType.CHUNK_LOAD_NOT_READY;
            case API_UNAVAILABLE -> MaterializationResultType.SABLE_API_UNAVAILABLE;
            case CONTROLLER_MISSING -> MaterializationResultType.CONTROLLER_REGISTRATION_WAITING;
            case HOLDING_BODY_WAITING -> MaterializationResultType.SABLE_HOLDING_BODY_WAITING;
            case UNSAFE_TARGET, RELOCATION_FAILED -> MaterializationResultType.UNSAFE_TO_RELOCATE_OR_MATERIALIZE;
            case LOAD_FAILED -> MaterializationResultType.SUBLEVEL_LOAD_FAILED;
        };
        return new MaterializationResult(type, result.success(), result.reasonCode(), result.message());
    }

    private static MaterializationResult failure(
            MaterializationRequest request,
            MaterializationResultType type,
            String reasonCode,
            String message
    ) {
        MaterializationResult result = new MaterializationResult(type, false, reasonCode, message);
        String event = switch (type) {
            case STORED_BODY_MISSING -> "stored body lookup result";
            case LOADED_BODY_AVAILABLE -> "live body lookup result";
            case SABLE_HOLDING_BODY_WAITING, CONTROLLER_REGISTRATION_WAITING -> "live body lookup result";
            default -> "restore/materialize failure";
        };
        logResult(event, request, result);
        return result;
    }

    private static MaterializationLifecycle lifecycle(MinecraftServer server, UUID shipId) {
        synchronized (MATERIALIZATION_LIFECYCLES) {
            return MATERIALIZATION_LIFECYCLES
                    .computeIfAbsent(server, ignored -> new HashMap<>())
                    .computeIfAbsent(shipId, ignored -> new MaterializationLifecycle());
        }
    }

    private enum BodyLifecycleState {
        UNKNOWN,
        STORED_CANONICAL,
        HELD_BY_SABLE,
        LIVE,
        LOADING,
        HELD_STORED,
        AMBIGUOUS_DUPLICATE,
        FAULTED
    }

    private static final class MaterializationLifecycle {
        private BodyLifecycleState state = BodyLifecycleState.UNKNOWN;
        private Optional<StoredBodyPointer> activePointer = Optional.empty();
        private long controllerWaitUntil = -1L;
        private String reason = "none";

        private Optional<MaterializationResult> blocker(long gameTime) {
            if (isControllerWaitActive(gameTime)) {
                return Optional.of(new MaterializationResult(
                        MaterializationResultType.CONTROLLER_REGISTRATION_WAITING,
                        false,
                        "materialization_load_in_flight",
                        "A previous Sable body load is still waiting for controller registration; refusing another load attempt."
                ));
            }
            if (state == BodyLifecycleState.AMBIGUOUS_DUPLICATE) {
                return Optional.of(new MaterializationResult(
                        MaterializationResultType.STORED_BODY_AMBIGUOUS,
                        false,
                        "body_lifecycle_ambiguous",
                        "Sable body ownership is ambiguous for pointer "
                                + activePointer.map(StoredBodyPointer::selector).orElse("missing")
                                + "; refusing runtime materialization until the index is repaired."
                ));
            }
            return Optional.empty();
        }

        private Optional<MaterializationResult> tryBeginLoad(
                MaterializationRequest request,
                long gameTime,
                StoredBodyPointer pointer,
                String reason
        ) {
            if (state == BodyLifecycleState.LOADING && isControllerWaitActive(gameTime)) {
                return Optional.of(new MaterializationResult(
                        MaterializationResultType.CONTROLLER_REGISTRATION_WAITING,
                        false,
                        "materialization_load_in_flight",
                        "A Sable body load is already in flight for pointer "
                                + activePointer.map(StoredBodyPointer::selector).orElse("missing")
                                + "; refusing a second load request."
                ));
            }
            if (state == BodyLifecycleState.FAULTED && activePointer.filter(pointer::equals).isPresent()) {
                return Optional.of(new MaterializationResult(
                        MaterializationResultType.SUBLEVEL_LOAD_FAILED,
                        false,
                        "body_lifecycle_faulted",
                        "The selected Sable pointer already failed to load in this lifecycle; refusing retry without new evidence."
                ));
            }
            transition(
                    request,
                    BodyLifecycleState.LOADING,
                    Optional.of(pointer),
                    gameTime,
                    reason,
                    Optional.of(ShipBodyDirectorySavedData.VerificationState.LOADING)
            );
            controllerWaitUntil = Math.max(controllerWaitUntil, gameTime + CONTROLLER_REGISTRATION_WAIT_TICKS);
            return Optional.empty();
        }

        private void waitForController(MaterializationRequest request, long gameTime, long ticks, String reason) {
            transition(
                    request,
                    BodyLifecycleState.LOADING,
                    activePointer,
                    gameTime,
                    reason,
                    Optional.of(ShipBodyDirectorySavedData.VerificationState.LOADING)
            );
            this.controllerWaitUntil = Math.max(this.controllerWaitUntil, gameTime + ticks);
        }

        private void transitionToStoredCanonical(
                MaterializationRequest request,
                StoredBodyPointer pointer,
                long gameTime,
                String reason
        ) {
            transition(
                    request,
                    BodyLifecycleState.STORED_CANONICAL,
                    Optional.of(pointer),
                    gameTime,
                    reason,
                    Optional.of(ShipBodyDirectorySavedData.VerificationState.STORED_VERIFIED)
            );
        }

        private void transitionToHeldBySable(
                MaterializationRequest request,
                StoredBodyPointer pointer,
                long gameTime,
                String reason
        ) {
            transition(
                    request,
                    BodyLifecycleState.HELD_BY_SABLE,
                    Optional.of(pointer),
                    gameTime,
                    reason,
                    Optional.of(ShipBodyDirectorySavedData.VerificationState.HELD_BY_SABLE)
            );
        }

        private void transitionToAmbiguous(
                MaterializationRequest request,
                StoredBodyPointer pointer,
                long gameTime,
                String reason
        ) {
            transition(
                    request,
                    BodyLifecycleState.AMBIGUOUS_DUPLICATE,
                    Optional.of(pointer),
                    gameTime,
                    reason,
                    Optional.of(ShipBodyDirectorySavedData.VerificationState.AMBIGUOUS)
            );
        }

        private void transitionFromLookupFailure(
                MaterializationRequest request,
                StoredBodyLookupResult lookup,
                long gameTime
        ) {
            ShipBodyDirectorySavedData.VerificationState directoryState = switch (lookup.status()) {
                case AMBIGUOUS -> ShipBodyDirectorySavedData.VerificationState.AMBIGUOUS;
                case READ_FAILED -> ShipBodyDirectorySavedData.VerificationState.CORRUPT;
                case NOT_FOUND -> ShipBodyDirectorySavedData.VerificationState.MISSING;
                case NOT_READY -> ShipBodyDirectorySavedData.VerificationState.UNVERIFIED;
                case VERIFIED_SINGLE, VERIFIED_POINTER -> ShipBodyDirectorySavedData.VerificationState.FAULTED;
            };
            BodyLifecycleState targetState = lookup.status() == StoredBodyLookupResult.Status.AMBIGUOUS
                    ? BodyLifecycleState.AMBIGUOUS_DUPLICATE
                    : BodyLifecycleState.FAULTED;
            Optional<StoredBodyPointer> pointer = lookup.candidates().size() == 1
                    ? Optional.of(lookup.candidates().getFirst().pointer())
                    : Optional.empty();
            transition(
                    request,
                    targetState,
                    pointer,
                    gameTime,
                    lookup.reasonCode(),
                    Optional.of(directoryState)
            );
        }

        private void transitionToFaulted(
                MaterializationRequest request,
                Optional<StoredBodyPointer> pointer,
                long gameTime,
                String reason
        ) {
            transition(
                    request,
                    BodyLifecycleState.FAULTED,
                    pointer,
                    gameTime,
                    reason,
                    Optional.of(ShipBodyDirectorySavedData.VerificationState.FAULTED)
            );
        }

        private void transitionToLive(
                MaterializationRequest request,
                SableSubLevelVehicleController controller,
                long gameTime,
                String reason
        ) {
            BodyLifecycleState previous = state;
            state = BodyLifecycleState.LIVE;
            activePointer = Optional.empty();
            controllerWaitUntil = -1L;
            this.reason = reason;
            boolean directoryUpdated = false;
            if (request.transponderId().isPresent()
                    && request.sableShipId().isPresent()
                    && request.localControllerPos().isPresent()) {
                Optional<UUID> existingTrackingPointId = ShipBodyDirectorySavedData.get(request.server())
                        .byTransponder(request.transponderId().get())
                        .filter(identity -> identity.sableShipId().equals(request.sableShipId().get()))
                        .flatMap(ShipBodyDirectorySavedData.BodyIdentity::trackingPointId);
                UUID trackingPointId = controller.ensureMaterializationTrackingPoint(existingTrackingPointId);
                ShipBodyDirectorySavedData.observeLiveBody(
                        request.server(),
                        request.transponderId().get(),
                        request.sableShipId().get(),
                        request.dimension(),
                        request.localControllerPos().get(),
                        Optional.of(trackingPointId),
                        controller.lastSerializationPointer(),
                        gameTime
                );
                directoryUpdated = true;
            }
            logOwnershipTransition(
                    request,
                    previous,
                    BodyLifecycleState.LIVE,
                    Optional.empty(),
                    reason,
                    "LIVE_VERIFIED",
                    directoryUpdated
            );
        }

        private void transitionToLiveWithoutController(MaterializationRequest request, long gameTime, String reason) {
            transition(
                    request,
                    BodyLifecycleState.LIVE,
                    Optional.empty(),
                    gameTime,
                    reason,
                    Optional.empty()
            );
        }

        private void transition(
                MaterializationRequest request,
                BodyLifecycleState targetState,
                Optional<StoredBodyPointer> pointer,
                long gameTime,
                String reason,
                Optional<ShipBodyDirectorySavedData.VerificationState> directoryState
        ) {
            BodyLifecycleState previous = state;
            state = targetState;
            activePointer = pointer;
            this.reason = reason;
            if (targetState != BodyLifecycleState.LOADING) {
                controllerWaitUntil = -1L;
            }
            boolean directoryUpdated = false;
            if (directoryState.isPresent()
                    && request.transponderId().isPresent()
                    && request.sableShipId().isPresent()
                    && request.localControllerPos().isPresent()) {
                directoryUpdated = ShipBodyDirectorySavedData.observeOwnershipState(
                        request.server(),
                        request.transponderId().get(),
                        request.sableShipId().get(),
                        request.dimension(),
                        request.localControllerPos().get(),
                        pointer,
                        directoryState.get(),
                        gameTime
                );
            }
            logOwnershipTransition(
                    request,
                    previous,
                    targetState,
                    pointer,
                    reason,
                    directoryState.map(Enum::name).orElse("not_applicable"),
                    directoryUpdated
            );
        }

        private boolean isControllerWaitActive(long gameTime) {
            return controllerWaitUntil >= 0L && gameTime <= controllerWaitUntil;
        }
    }

    private static void logOwnershipTransition(
            MaterializationRequest request,
            BodyLifecycleState fromState,
            BodyLifecycleState toState,
            Optional<StoredBodyPointer> pointer,
            String reasonCode,
            String directoryState,
            boolean directoryUpdated
    ) {
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Sable body ownership transition: transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose={} fromState={} toState={} pointer={} reason={} directoryState={} directoryUpdated={} action=materialization_owner_transition",
                format(request.transponderId()),
                format(request.sableShipId()),
                request.routeId().map(id -> id.value().toString()).orElse("missing"),
                request.currentLegIndex().map(Object::toString).orElse("missing"),
                format(request.stationId()),
                request.dimension().location(),
                request.targetPose(),
                fromState,
                toState,
                pointer.map(StoredBodyPointer::selector).orElse("missing"),
                reasonCode,
                directoryState,
                directoryUpdated
        );
    }

    private static void logRequest(
            String event,
            MaterializationRequest request,
            MaterializationResultType resultType,
            String reasonCode
    ) {
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "{} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose={} forceAtHold={} resultType={} reason={}",
                event,
                format(request.transponderId()),
                format(request.sableShipId()),
                request.routeId().map(id -> id.value().toString()).orElse("missing"),
                request.currentLegIndex().map(Object::toString).orElse("missing"),
                format(request.stationId()),
                request.dimension().location(),
                request.targetPose(),
                request.forceAtHoldPoint(),
                resultType,
                reasonCode
        );
    }

    private static void logResult(
            String event,
            MaterializationRequest request,
            MaterializationResult result
    ) {
        if (!shouldLogMaterialization(event, request, result)) {
            return;
        }
        if (result.success()) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "{} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose={} resultType={} reason={} message={}",
                    event,
                    format(request.transponderId()),
                    format(request.sableShipId()),
                    request.routeId().map(id -> id.value().toString()).orElse("missing"),
                    request.currentLegIndex().map(Object::toString).orElse("missing"),
                    format(request.stationId()),
                    request.dimension().location(),
                    request.targetPose(),
                    result.type(),
                    result.reasonCode(),
                    result.message()
            );
            return;
        }
        if (isExpectedWaitingResult(result)) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "{} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose={} resultType={} reason={} message={}",
                    event,
                    format(request.transponderId()),
                    format(request.sableShipId()),
                    request.routeId().map(id -> id.value().toString()).orElse("missing"),
                    request.currentLegIndex().map(Object::toString).orElse("missing"),
                    format(request.stationId()),
                    request.dimension().location(),
                    request.targetPose(),
                    result.type(),
                    result.reasonCode(),
                    result.message()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "{} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose={} resultType={} reason={} message={}",
                event,
                format(request.transponderId()),
                format(request.sableShipId()),
                request.routeId().map(id -> id.value().toString()).orElse("missing"),
                request.currentLegIndex().map(Object::toString).orElse("missing"),
                format(request.stationId()),
                request.dimension().location(),
                request.targetPose(),
                result.type(),
                result.reasonCode(),
                result.message()
        );
    }

    private static void logBodyLookup(
            String event,
            Optional<UUID> transponderId,
            UUID shipId,
            Optional<RouteId> routeId,
            Optional<UUID> stationId,
            ResourceKey<Level> dimension,
            String reasonCode
    ) {
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "{} transponder={} sableShip={} route={} station={} dimension={} targetPose=none resultType={} reason={}",
                event,
                format(transponderId),
                shipId,
                routeId.map(id -> id.value().toString()).orElse("missing"),
                format(stationId),
                dimension.location(),
                MaterializationResultType.UNKNOWN_FAILURE,
                reasonCode
        );
    }

    private static void logBodyResult(
            MinecraftServer server,
            String event,
            Optional<UUID> transponderId,
            UUID shipId,
            Optional<RouteId> routeId,
            Optional<UUID> stationId,
            ResourceKey<Level> dimension,
            MaterializationResult result
    ) {
        if (!shouldLogBodyResult(server, event, transponderId, shipId, routeId, dimension, result)) {
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "{} transponder={} sableShip={} route={} station={} dimension={} targetPose=none resultType={} reason={} message={}",
                event,
                format(transponderId),
                shipId,
                routeId.map(id -> id.value().toString()).orElse("missing"),
                format(stationId),
                dimension.location(),
                result.type(),
                result.reasonCode(),
                result.message()
        );
    }

    private static void logLiveLookupResult(
            String event,
            LiveBodyLookupRequest request,
            MaterializationResult result
    ) {
        if (!shouldLogLiveLookup(event, request, result)) {
            return;
        }
        if (result.success()) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "{} source={} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose=none resultType={} reason={} message={}",
                    event,
                    request.source(),
                    format(request.transponderId()),
                    format(request.sableShipId()),
                    request.routeId().map(id -> id.value().toString()).orElse("missing"),
                    request.currentLegIndex().map(Object::toString).orElse("missing"),
                    format(request.stationId()),
                    request.dimension().location(),
                    result.type(),
                    result.reasonCode(),
                    result.message()
            );
            return;
        }
        if (isExpectedWaitingResult(result) || result.type() == MaterializationResultType.LIVE_BODY_MISSING) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "{} source={} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose=none resultType={} reason={} message={}",
                    event,
                    request.source(),
                    format(request.transponderId()),
                    format(request.sableShipId()),
                    request.routeId().map(id -> id.value().toString()).orElse("missing"),
                    request.currentLegIndex().map(Object::toString).orElse("missing"),
                    format(request.stationId()),
                    request.dimension().location(),
                    result.type(),
                    result.reasonCode(),
                    result.message()
            );
            return;
        }
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "{} source={} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose=none resultType={} reason={} message={}",
                event,
                request.source(),
                format(request.transponderId()),
                format(request.sableShipId()),
                request.routeId().map(id -> id.value().toString()).orElse("missing"),
                request.currentLegIndex().map(Object::toString).orElse("missing"),
                format(request.stationId()),
                request.dimension().location(),
                result.type(),
                result.reasonCode(),
                result.message()
        );
    }

    private static boolean shouldLogLiveLookup(
            String event,
            LiveBodyLookupRequest request,
            MaterializationResult result
    ) {
        long gameTime = request.server().overworld().getGameTime();
        LiveLookupLogKey key = new LiveLookupLogKey(
                event,
                request.source(),
                request.transponderId(),
                request.sableShipId(),
                request.routeId(),
                request.dimension().location().toString()
        );
        String signature = result.type() + "|" + result.reasonCode() + '|' + result.success();
        synchronized (LIVE_LOOKUP_LOGS) {
            Map<LiveLookupLogKey, LiveLookupLogState> serverLogs = LIVE_LOOKUP_LOGS.computeIfAbsent(
                    request.server(),
                    ignored -> new HashMap<>()
            );
            LiveLookupLogState previous = serverLogs.get(key);
            if (previous != null
                    && previous.signature().equals(signature)
                    && gameTime - previous.gameTime() < LIVE_LOOKUP_LOG_HEARTBEAT_TICKS) {
                return false;
            }
            if (!serverLogs.containsKey(key) && serverLogs.size() >= MAX_LIVE_LOOKUP_LOG_STATES) {
                serverLogs.entrySet().removeIf(entry ->
                        gameTime - entry.getValue().gameTime() >= LIVE_LOOKUP_LOG_HEARTBEAT_TICKS);
                if (serverLogs.size() >= MAX_LIVE_LOOKUP_LOG_STATES) {
                    serverLogs.entrySet().stream()
                            .min(Map.Entry.comparingByValue((left, right) -> Long.compare(left.gameTime(), right.gameTime())))
                            .map(Map.Entry::getKey)
                            .ifPresent(serverLogs::remove);
                }
            }
            serverLogs.put(key, new LiveLookupLogState(signature, gameTime));
            return true;
        }
    }

    private static boolean shouldLogMaterialization(
            String event,
            MaterializationRequest request,
            MaterializationResult result
    ) {
        if (!isExpectedWaitingResult(result) && result.type() != MaterializationResultType.LIVE_BODY_MISSING) {
            return true;
        }
        long gameTime = request.server().overworld().getGameTime();
        MaterializationLogKey key = new MaterializationLogKey(
                event,
                request.transponderId(),
                request.sableShipId(),
                request.routeId(),
                request.dimension().location().toString()
        );
        return shouldLogMaterializationKey(
                request.server(),
                key,
                result.type() + "|" + result.reasonCode() + '|' + result.success(),
                gameTime
        );
    }

    private static boolean shouldLogBodyResult(
            MinecraftServer server,
            String event,
            Optional<UUID> transponderId,
            UUID shipId,
            Optional<RouteId> routeId,
            ResourceKey<Level> dimension,
            MaterializationResult result
    ) {
        if (!isExpectedWaitingResult(result) && result.type() != MaterializationResultType.LIVE_BODY_MISSING) {
            return true;
        }
        MaterializationLogKey key = new MaterializationLogKey(
                event,
                transponderId,
                Optional.of(shipId),
                routeId,
                dimension.location().toString()
        );
        return shouldLogMaterializationKey(
                server,
                key,
                result.type() + "|" + result.reasonCode() + '|' + result.success(),
                server.overworld().getGameTime()
        );
    }

    private static boolean shouldLogMaterializationKey(
            MinecraftServer server,
            MaterializationLogKey key,
            String signature,
            long gameTime
    ) {
        if (server == null) {
            return true;
        }
        synchronized (MATERIALIZATION_LOGS) {
            Map<MaterializationLogKey, LiveLookupLogState> serverLogs = MATERIALIZATION_LOGS.computeIfAbsent(
                    server,
                    ignored -> new HashMap<>()
            );
            LiveLookupLogState previous = serverLogs.get(key);
            if (previous != null
                    && previous.signature().equals(signature)
                    && gameTime - previous.gameTime() < EXPECTED_WAIT_LOG_HEARTBEAT_TICKS) {
                return false;
            }
            serverLogs.put(key, new LiveLookupLogState(signature, gameTime));
            return true;
        }
    }

    private static boolean isExpectedWaitingResult(MaterializationResult result) {
        return result.type() == MaterializationResultType.SABLE_HOLDING_BODY_WAITING
                || result.type() == MaterializationResultType.CONTROLLER_REGISTRATION_WAITING
                || result.type() == MaterializationResultType.CHUNK_LOAD_NOT_READY
                || result.type() == MaterializationResultType.STARTUP_GRACE_WAITING;
    }

    private static String format(Optional<UUID> value) {
        return value.map(UUID::toString).orElse("missing");
    }

    private record LiveLookupLogKey(
            String event,
            String source,
            Optional<UUID> transponderId,
            Optional<UUID> sableShipId,
            Optional<RouteId> routeId,
            String dimension
    ) {
    }

    private record MaterializationLogKey(
            String event,
            Optional<UUID> transponderId,
            Optional<UUID> sableShipId,
            Optional<RouteId> routeId,
            String dimension
    ) {
    }

    private record LiveLookupLogState(String signature, long gameTime) {
    }
}

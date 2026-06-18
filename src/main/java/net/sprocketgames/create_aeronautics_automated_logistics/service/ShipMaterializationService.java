package net.sprocketgames.create_aeronautics_automated_logistics.service;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

public final class ShipMaterializationService {
    public enum MaterializationResultType {
        LOADED_BODY_AVAILABLE,
        LIVE_BODY_MISSING,
        STORED_BODY_AVAILABLE,
        STORED_BODY_MISSING,
        SUBLEVEL_LOAD_FAILED,
        CHUNK_LOAD_NOT_READY,
        CONTROLLER_MISSING,
        UNSAFE_TO_RELOCATE_OR_MATERIALIZE,
        SABLE_API_UNAVAILABLE,
        STARTUP_GRACE_WAITING,
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
        if (container.getSubLevel(shipId) != null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.LOADED_BODY_AVAILABLE,
                    true,
                    "live_body_available",
                    "Sable body is already loaded."
            );
            logResult("live body lookup result", request, result);
            return result;
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
        if (StationChunkLoadingService.isStartupSableStorageGraceActive()) {
            return failure(
                    request,
                    MaterializationResultType.STARTUP_GRACE_WAITING,
                    "startup_sable_storage_grace",
                    "Startup Sable storage grace is active."
            );
        }

        boolean storedAvailable = ShipRecoveryService.hasStoredShip(request.server(), request.dimension(), shipId);
        MaterializationResult storedLookup = storedAvailable
                ? new MaterializationResult(
                        MaterializationResultType.STORED_BODY_AVAILABLE,
                        true,
                        "stored_body_available",
                        "Stored Sable body is available."
                )
                : new MaterializationResult(
                        MaterializationResultType.STORED_BODY_MISSING,
                        false,
                        "stored_body_missing",
                        "Stored Sable body is missing."
                );
        logResult("stored body lookup result", request, storedLookup);
        if (!storedAvailable) {
            return storedLookup;
        }
        if (!attemptGate.getAsBoolean()) {
            return failure(
                    request,
                    MaterializationResultType.CHUNK_LOAD_NOT_READY,
                    "materialization_retry_cooldown",
                    "Materialization retry cooldown has not elapsed."
            );
        }

        logRequest("restore/materialize attempt", request, MaterializationResultType.STORED_BODY_AVAILABLE, "attempting_materialize");
        ShipRecoveryService.RecoveryResult recovery = ShipRecoveryService.materializeStoredShipControllerAt(
                request.server(),
                request.dimension(),
                shipId,
                request.localControllerPos().get(),
                request.targetPose(),
                request.description()
        );
        if (recovery.success()) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.MATERIALIZED,
                    true,
                    "materialized",
                    recovery.message()
            );
            logResult("restore/materialize success", request, result);
            return result;
        }

        MaterializationResult result = new MaterializationResult(
                classifyFailure(recovery.message()),
                false,
                classifyReason(recovery.message()),
                recovery.message()
        );
        logResult("restore/materialize failure", request, result);
        return result;
    }

    public LiveBodyLookupResult resolveLiveBody(LiveBodyLookupRequest request) {
        Objects.requireNonNull(request, "request");

        logLiveLookup("materialization request", request, MaterializationResultType.UNKNOWN_FAILURE, request.reasonCode());

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

        boolean sableController = request.controllerRef().controllerType().equals(SableSubLevelVehicleController.TYPE);
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

        if (StationChunkLoadingService.isStartupSableStorageGraceActive()) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.STARTUP_GRACE_WAITING,
                    false,
                    "startup_sable_storage_grace",
                    "Startup Sable storage grace is active."
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

        boolean storedAvailable = hasStoredBody(request.server(), request.dimension(), request.sableShipId().get());
        MaterializationResult storedLookup = storedAvailable
                ? new MaterializationResult(
                        MaterializationResultType.STORED_BODY_AVAILABLE,
                        true,
                        "stored_body_available",
                        "Stored Sable body is available."
                )
                : new MaterializationResult(
                        MaterializationResultType.STORED_BODY_MISSING,
                        false,
                        "stored_body_missing",
                        "Stored Sable body is missing."
                );
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
        return ShipRecoveryService.describeStoredShip(server, dimension, shipId);
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
            logBodyResult("live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
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
            logBodyResult("live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
            return result;
        }
        if (container.getSubLevel(shipId) != null) {
            MaterializationResult result = new MaterializationResult(
                    MaterializationResultType.LOADED_BODY_AVAILABLE,
                    true,
                    "live_body_available",
                    "Sable body is already loaded."
            );
            logBodyResult("live body lookup result", transponderId, shipId, routeId, stationId, dimension, result);
            return result;
        }
        MaterializationResult liveMissing = new MaterializationResult(
                MaterializationResultType.LIVE_BODY_MISSING,
                false,
                "live_body_missing",
                "Live Sable body is not loaded."
        );
        logBodyResult("live body lookup result", transponderId, shipId, routeId, stationId, dimension, liveMissing);

        boolean stored = hasStoredBody(server, dimension, shipId);
        MaterializationResult storedResult = stored
                ? new MaterializationResult(
                        MaterializationResultType.STORED_BODY_AVAILABLE,
                        true,
                        "stored_body_available",
                        "Stored Sable body is available."
                )
                : new MaterializationResult(
                        MaterializationResultType.STORED_BODY_MISSING,
                        false,
                        "stored_body_missing",
                        "Stored Sable body is missing."
                );
        logBodyResult("stored body lookup result", transponderId, shipId, routeId, stationId, dimension, storedResult);
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
        return ShipRecoveryService.hasStoredShip(server, dimension, shipId);
    }

    public int pruneStoredEntriesForLoadedBody(CleanupRequest request) {
        Objects.requireNonNull(request, "request");
        logCleanup("cleanup/prune request", request, "request_received", 0);
        if (request.sableShipId().isEmpty()) {
            logCleanup("cleanup/prune refused", request, "missing_sable_ship_id", 0);
            return 0;
        }
        int removed = ShipRecoveryService.pruneStoredEntriesForLoadedShip(
                request.server(),
                request.dimension(),
                request.sableShipId().get()
        );
        if (removed > 0) {
            logCleanup("cleanup/prune applied", request, "loaded_body_current_pointer_proof", removed);
        } else {
            logCleanup("cleanup/prune refused", request, "no_duplicate_stored_body_proof", 0);
        }
        return removed;
    }

    public record CleanupRequest(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            Optional<UUID> transponderId,
            Optional<UUID> sableShipId,
            Optional<RouteId> routeId,
            Optional<Integer> currentLegIndex,
            Optional<UUID> stationId,
            String source,
            String reasonCode
    ) {
        public CleanupRequest {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(dimension, "dimension");
            transponderId = Objects.requireNonNull(transponderId, "transponderId");
            sableShipId = Objects.requireNonNull(sableShipId, "sableShipId");
            routeId = Objects.requireNonNull(routeId, "routeId");
            currentLegIndex = Objects.requireNonNull(currentLegIndex, "currentLegIndex");
            stationId = Objects.requireNonNull(stationId, "stationId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
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
            default -> "restore/materialize failure";
        };
        logResult(event, request, result);
        return result;
    }

    private static MaterializationResultType classifyFailure(String message) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("no stored sable ship")) {
            return MaterializationResultType.STORED_BODY_MISSING;
        }
        if (normalized.contains("container is not available")) {
            return MaterializationResultType.SABLE_API_UNAVAILABLE;
        }
        if (normalized.contains("could not be loaded") || normalized.contains("did not instantiate")) {
            return MaterializationResultType.SUBLEVEL_LOAD_FAILED;
        }
        if (normalized.contains("incomplete or unsafe")
                || normalized.contains("refusing")
                || normalized.contains("not safe to load")) {
            return MaterializationResultType.UNSAFE_TO_RELOCATE_OR_MATERIALIZE;
        }
        if (normalized.contains("dimension is not loaded")) {
            return MaterializationResultType.CHUNK_LOAD_NOT_READY;
        }
        return MaterializationResultType.UNKNOWN_FAILURE;
    }

    private static String classifyReason(String message) {
        return switch (classifyFailure(message)) {
            case STORED_BODY_MISSING -> "stored_body_missing";
            case SABLE_API_UNAVAILABLE -> "sable_api_unavailable";
            case SUBLEVEL_LOAD_FAILED -> "sublevel_load_failed";
            case UNSAFE_TO_RELOCATE_OR_MATERIALIZE -> "unsafe_to_relocate_or_materialize";
            case CHUNK_LOAD_NOT_READY -> "dimension_or_chunk_not_ready";
            default -> "unknown_materialization_failure";
        };
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

    private static void logCleanup(String event, CleanupRequest request, String reasonCode, int removed) {
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "{} source={} transponder={} sableShip={} route={} leg={} station={} dimension={} resultType={} reason={} removed={}",
                event,
                request.source(),
                format(request.transponderId()),
                format(request.sableShipId()),
                request.routeId().map(id -> id.value().toString()).orElse("missing"),
                request.currentLegIndex().map(Object::toString).orElse("missing"),
                format(request.stationId()),
                request.dimension().location(),
                removed > 0 ? "APPLIED" : "REFUSED_OR_NOOP",
                reasonCode,
                removed
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
            String event,
            Optional<UUID> transponderId,
            UUID shipId,
            Optional<RouteId> routeId,
            Optional<UUID> stationId,
            ResourceKey<Level> dimension,
            MaterializationResult result
    ) {
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

    private static void logLiveLookup(
            String event,
            LiveBodyLookupRequest request,
            MaterializationResultType resultType,
            String reasonCode
    ) {
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "{} source={} transponder={} sableShip={} route={} leg={} station={} dimension={} targetPose=none resultType={} reason={}",
                event,
                request.source(),
                format(request.transponderId()),
                format(request.sableShipId()),
                request.routeId().map(id -> id.value().toString()).orElse("missing"),
                request.currentLegIndex().map(Object::toString).orElse("missing"),
                format(request.stationId()),
                request.dimension().location(),
                resultType,
                reasonCode
        );
    }

    private static void logLiveLookupResult(
            String event,
            LiveBodyLookupRequest request,
            MaterializationResult result
    ) {
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

    private static String format(Optional<UUID> value) {
        return value.map(UUID::toString).orElse("missing");
    }
}

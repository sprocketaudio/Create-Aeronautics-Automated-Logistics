package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;

/** Loads a proven stored body through Sable's holding-map lifecycle, then relocates the live controller. */
public final class SableStoredShipMaterializer {
    private static final double RELOCATION_EPSILON_SQR = 0.25D;
    private static Field loadedHoldingChunksField;

    private SableStoredShipMaterializer() {
    }

    public static Result materialize(Request request) {
        Objects.requireNonNull(request, "request");
        ServerLevel level = request.server().getLevel(request.dimension());
        if (level == null) {
            return Result.failure(ResultType.NOT_READY, "dimension_unavailable", "The ship dimension is not loaded.");
        }
        if (!finite(request.targetControllerPosition())) {
            return Result.failure(ResultType.UNSAFE_TARGET, "target_pose_not_finite", "Target controller position is not finite.");
        }
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(request.targetControllerPosition()))) {
            return Result.failure(ResultType.UNSAFE_TARGET, "target_outside_world_border", "Target controller position is outside the world border.");
        }

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return Result.failure(ResultType.API_UNAVAILABLE, "sable_container_unavailable", "Sable sublevel container is unavailable.");
        }
        if (container.getSubLevel(request.sableShipId()) != null) {
            Optional<SableSubLevelVehicleController> loaded = SableSubLevelVehicleController.resolveControllerBlock(
                    level,
                    request.sableShipId(),
                    request.localControllerPos()
            );
            return loaded.<Result>map(controller -> Result.success(
                            ResultType.ALREADY_LOADED,
                            "body_already_loaded",
                            "Sable body is already loaded.",
                            controller
                    ))
                    .orElseGet(() -> Result.failure(
                            ResultType.CONTROLLER_MISSING,
                            "loaded_body_controller_missing",
                            "Sable body is loaded but the expected transponder controller is missing."
                    ));
        }
        HoldingSubLevel holdingBody = container.getHoldingChunkMap().getHoldingSubLevel(request.sableShipId());
        if (holdingBody != null) {
            StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                    request.server(),
                    request.transponderId(),
                    request.dimension(),
                    request.sableShipId()
            );
            if (lookup.selected().isEmpty()) {
                return lookupFailure(lookup);
            }
            StoredBodyCandidate selected = lookup.selected().get();
            if (request.expectedPointer().isPresent() && !request.expectedPointer().get().equals(selected.pointer())) {
                return Result.failure(
                        ResultType.AMBIGUOUS,
                        "selected_pointer_changed",
                        "Stored body lookup selected " + selected.pointer().selector()
                                + " but the materialization owner requested " + request.expectedPointer().get().selector()
                                + "; refusing to load either pointer."
                );
            }
            StoredBodyPointer holdingPointer = StoredBodyPointer.fromSable(holdingBody.pointer());
            if (!holdingPointer.equals(selected.pointer())) {
                return Result.failure(
                        ResultType.AMBIGUOUS,
                        "holding_pointer_not_canonical",
                        "Sable is holding pointer " + holdingPointer.selector()
                                + " but the canonical stored-body lookup selected " + selected.pointer().selector()
                                + "; refusing to load either pointer."
                );
            }
            SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
            SubLevelStorage storage = holdingMap.getStorage();
            PreflightResult preflight = preflight(storage, selected);
            if (preflight.failure().isPresent()) {
                return Result.failure(ResultType.LOAD_FAILED, "holding_body_preflight_failed", preflight.failure().get());
            }
            HoldingLocationResult holdingLocation = resolveHoldingLocation(
                    holdingMap,
                    request.sableShipId(),
                    holdingBody.pointer()
            );
            if (holdingLocation.failure().isPresent()) {
                return Result.failure(
                        holdingLocation.ambiguous() ? ResultType.AMBIGUOUS : ResultType.NOT_READY,
                        holdingLocation.ambiguous()
                                ? "sable_holding_body_location_ambiguous"
                                : "sable_holding_body_location_unavailable",
                        holdingLocation.failure().get()
                );
            }
            GlobalSavedSubLevelPointer snatchPointer = holdingLocation.pointer().orElseThrow();
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Sable-owned materialization loading canonical holding body: transponder={} sableShip={} storedPointer={} owningHoldingChunk={} snatchPointer={} proof={} targetController={} description={}",
                    request.transponderId().map(UUID::toString).orElse("missing"),
                    request.sableShipId(),
                    holdingBody.pointer(),
                    snatchPointer.chunkPos(),
                    snatchPointer,
                    lookup.reasonCode(),
                    request.targetControllerPosition(),
                    request.description()
            );
            return loadAndRelocate(
                    level,
                    container,
                    request,
                    snatchPointer,
                    preflight.plan().orElseThrow(),
                    "sable_owned_holding_body_load_and_live_relocation",
                    "Sable loaded its existing holding body and the live controller was relocated.",
                    "existingHoldingPointer"
            );
        }

        StoredBodyLookupResult lookup = SableStoredShipRepository.lookup(
                request.server(),
                request.transponderId(),
                request.dimension(),
                request.sableShipId()
        );
        if (lookup.selected().isEmpty()) {
            return lookupFailure(lookup);
        }

        StoredBodyCandidate selected = lookup.selected().get();
        if (request.expectedPointer().isPresent() && !request.expectedPointer().get().equals(selected.pointer())) {
            return Result.failure(
                    ResultType.AMBIGUOUS,
                    "selected_pointer_changed",
                    "Stored body lookup selected " + selected.pointer().selector()
                            + " but the materialization owner requested " + request.expectedPointer().get().selector()
                            + "; refusing to load either pointer."
            );
        }
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Sable-owned materialization selected stored body: transponder={} sableShip={} dimension={} pointer={} candidates={} proof={} targetController={} description={}",
                request.transponderId().map(UUID::toString).orElse("missing"),
                request.sableShipId(),
                request.dimension().location(),
                selected.pointer(),
                lookup.candidates().size(),
                lookup.reasonCode(),
                request.targetControllerPosition(),
                request.description()
        );
        long sameChunkMatches = lookup.candidates().stream()
                .filter(candidate -> candidate.sableShipId().equals(request.sableShipId()))
                .filter(candidate -> candidate.pointer().chunkPos().equals(selected.pointer().chunkPos()))
                .count();
        if (sameChunkMatches > 1) {
            return Result.failure(
                    ResultType.AMBIGUOUS,
                    "same_chunk_duplicate_uuid_not_addressable",
                    "Sable cannot address one of multiple same-UUID bodies in the selected holding chunk safely."
            );
        }

        SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
        SubLevelStorage storage = holdingMap.getStorage();
        PreflightResult preflight = preflight(storage, selected);
        if (preflight.failure().isPresent()) {
            return Result.failure(ResultType.LOAD_FAILED, "stored_body_preflight_failed", preflight.failure().get());
        }

        return loadAndRelocate(
                level,
                container,
                request,
                selected.pointer().toSable(),
                preflight.plan().orElseThrow(),
                "sable_owned_load_and_live_relocation",
                "Sable loaded the selected stored body and the live controller was relocated.",
                selected.pointer().toString()
        );
    }

    private static HoldingLocationResult resolveHoldingLocation(
            SubLevelHoldingChunkMap holdingMap,
            UUID sableShipId,
            GlobalSavedSubLevelPointer storedPointer
    ) {
        List<SubLevelHoldingChunk> owners = new ArrayList<>();
        try {
            if (loadedHoldingChunksField == null) {
                loadedHoldingChunksField = SubLevelHoldingChunkMap.class.getDeclaredField("loadedHoldingChunks");
                loadedHoldingChunksField.setAccessible(true);
            }
            Object value = loadedHoldingChunksField.get(holdingMap);
            if (!(value instanceof Map<?, ?> loadedHoldingChunks)) {
                return HoldingLocationResult.failure(
                        false,
                        "Sable's loaded holding-chunk index has an unexpected type; refusing to load the body."
                );
            }
            for (Object candidate : loadedHoldingChunks.values()) {
                if (!(candidate instanceof SubLevelHoldingChunk holdingChunk)) {
                    continue;
                }
                for (HoldingSubLevel body : holdingChunk.getLoadedHoldingSubLevels()) {
                    if (body.data().uuid().equals(sableShipId)) {
                        owners.add(holdingChunk);
                        break;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable holding-body ownership lookup failed: sableShip={} storedPointer={} reason={} action=refused_no_load",
                    sableShipId,
                    storedPointer,
                    exception.toString()
            );
            return HoldingLocationResult.failure(
                    false,
                    "Sable's in-memory holding-body owner could not be inspected; refusing to load the body."
            );
        }

        if (owners.isEmpty()) {
            return HoldingLocationResult.failure(
                    false,
                    "Sable reports a held body, but no loaded holding chunk owns it yet; waiting for Sable to finish its transition."
            );
        }
        if (owners.size() > 1) {
            return HoldingLocationResult.failure(
                    true,
                    "Multiple loaded Sable holding chunks claim the same ship body; refusing an ambiguous load."
            );
        }

        SubLevelHoldingChunk owner = owners.getFirst();
        GlobalSavedSubLevelPointer snatchPointer = new GlobalSavedSubLevelPointer(
                owner.getChunkPos(),
                storedPointer.storageIndex(),
                storedPointer.subLevelIndex()
        );
        if (!owner.getChunkPos().equals(storedPointer.chunkPos())) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Sable holding-body pointer location corrected for load: sableShip={} storedPointer={} owningHoldingChunk={} snatchPointer={} action=snatch_existing_held_body",
                    sableShipId,
                    storedPointer,
                    owner.getChunkPos(),
                    snatchPointer
            );
        }
        return HoldingLocationResult.success(snatchPointer);
    }

    private static Result lookupFailure(StoredBodyLookupResult lookup) {
        return switch (lookup.status()) {
            case AMBIGUOUS -> Result.failure(
                    ResultType.AMBIGUOUS,
                    lookup.reasonCode(),
                    "Stored body lookup is ambiguous across " + lookup.candidates().size() + " candidates."
            );
            case READ_FAILED -> Result.failure(
                    ResultType.CORRUPT,
                    lookup.reasonCode(),
                    "Stored body candidates exist but none are structurally readable."
            );
            case NOT_READY -> Result.failure(ResultType.NOT_READY, lookup.reasonCode(), "Stored body lookup is not ready.");
            case NOT_FOUND -> Result.failure(ResultType.MISSING, lookup.reasonCode(), "Stored body was not found.");
            case VERIFIED_SINGLE, VERIFIED_POINTER -> Result.failure(
                    ResultType.LOAD_FAILED,
                    "selected_candidate_missing",
                    "Stored body lookup returned a selectable status without a candidate."
            );
        };
    }

    private static Result loadAndRelocate(
            ServerLevel level,
            ServerSubLevelContainer container,
            Request request,
            GlobalSavedSubLevelPointer pointer,
            LoadPlan loadPlan,
            String successReasonCode,
            String successMessage,
            String sourcePointer
    ) {
        SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
        boolean holdingBodyStillPresentBeforeSnatch = holdingMap.getHoldingSubLevel(request.sableShipId()) != null;
        try {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "AAL invoking Sable snatchAndLoad: transponder={} sableShip={} pointer={} holdingBodyPresentBefore={} reason=materialization_load_request",
                    request.transponderId().map(UUID::toString).orElse("missing"),
                    request.sableShipId(),
                    pointer,
                    holdingBodyStillPresentBeforeSnatch
            );
            holdingMap.snatchAndLoad(pointer, request.sableShipId());
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "AAL completed Sable snatchAndLoad call: transponder={} sableShip={} pointer={} liveAfter={} holdingBodyPresentAfter={} reason=materialization_load_returned",
                    request.transponderId().map(UUID::toString).orElse("missing"),
                    request.sableShipId(),
                    pointer,
                    container.getSubLevel(request.sableShipId()) != null,
                    holdingMap.getHoldingSubLevel(request.sableShipId()) != null
            );
        } catch (RuntimeException exception) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable-owned materialization load failed: transponder={} sableShip={} pointer={} reason=sable_snatch_load_exception exception={}",
                    request.transponderId().map(UUID::toString).orElse("missing"),
                    request.sableShipId(),
                    pointer,
                    exception.getClass().getName()
            );
            return Result.failure(
                    ResultType.LOAD_FAILED,
                    "sable_snatch_load_exception",
                    "Sable threw while loading the selected stored body: " + exception.getClass().getSimpleName()
            );
        }

        if (container.getSubLevel(request.sableShipId()) == null
                && holdingBodyStillPresentBeforeSnatch
                && holdingMap.getHoldingSubLevel(request.sableShipId()) != null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable-owned materialization snatch did not consume holding body; refusing direct payload load to avoid duplicate Sable body state: transponder={} sableShip={} pointer={} dependencies={} reason=sable_snatch_missing_holding_index action=fault_no_direct_load",
                    request.transponderId().map(UUID::toString).orElse("missing"),
                    request.sableShipId(),
                    pointer,
                    loadPlan.bodies().size() - 1
            );
        }

        if (container.getSubLevel(request.sableShipId()) == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable-owned materialization load failed: transponder={} sableShip={} pointer={} reason=sable_snatch_no_live_sublevel action=fault_no_controller_wait",
                    request.transponderId().map(UUID::toString).orElse("missing"),
                    request.sableShipId(),
                    pointer
            );
            return Result.failure(
                    ResultType.LOAD_FAILED,
                    "sable_snatch_no_live_sublevel",
                    "Sable did not produce a live sublevel for the verified stored body; refusing controller-registration wait."
            );
        }
        Optional<SableSubLevelVehicleController> controller = SableSubLevelVehicleController.resolveControllerBlock(
                level,
                request.sableShipId(),
                request.localControllerPos()
        );
        if (controller.isEmpty()) {
            return Result.failure(
                    ResultType.CONTROLLER_MISSING,
                    "materialized_body_controller_missing",
                    "Sable loaded the body but the expected transponder controller is missing."
            );
        }

        SableSubLevelVehicleController liveController = controller.get();
        liveController.relocate(level, request.targetControllerPosition(), Optional.empty());
        liveController.hold(level, request.targetControllerPosition(), Optional.empty());
        if (liveController.position().distanceToSqr(request.targetControllerPosition()) > RELOCATION_EPSILON_SQR) {
            return Result.failure(
                    ResultType.RELOCATION_FAILED,
                    "live_body_relocation_not_applied",
                    "Sable loaded the body, but its controller did not reach the requested target pose."
            );
        }

        Optional<UUID> existingTrackingPointId = request.transponderId()
                .flatMap(ShipBodyDirectorySavedData.get(request.server())::byTransponder)
                .filter(identity -> identity.sableShipId().equals(request.sableShipId()))
                .flatMap(ShipBodyDirectorySavedData.BodyIdentity::trackingPointId);
        UUID trackingPointId = liveController.ensureMaterializationTrackingPoint(existingTrackingPointId);
        request.transponderId().ifPresent(transponderId -> ShipBodyDirectorySavedData.observeLiveBody(
                request.server(),
                transponderId,
                request.sableShipId(),
                request.dimension(),
                request.localControllerPos(),
                Optional.of(trackingPointId),
                liveController.lastSerializationPointer(),
                level.getGameTime()
        ));
        SableStoredShipRepository.invalidate(request.server());
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Sable-owned materialization completed: transponder={} sableShip={} sourcePointer={} targetController={} actualController={} result=materialized",
                request.transponderId().map(UUID::toString).orElse("missing"),
                request.sableShipId(),
                sourcePointer,
                request.targetControllerPosition(),
                liveController.position()
        );
        return Result.success(
                ResultType.MATERIALIZED,
                successReasonCode,
                successMessage,
                liveController
        );
    }

    private static PreflightResult preflight(SubLevelStorage storage, StoredBodyCandidate selected) {
        SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(selected.pointer().chunkPos());
        if (holdingChunk == null) {
            return PreflightResult.failure("Selected Sable holding chunk is not readable.");
        }
        if (!holdingChunk.getSubLevelPointers().contains(selected.pointer().toSable().local())) {
            return PreflightResult.failure("Selected Sable pointer is no longer listed by its holding chunk.");
        }

        Map<UUID, SubLevelData> dataById = new HashMap<>();
        Map<UUID, GlobalSavedSubLevelPointer> pointerById = new HashMap<>();
        for (SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
            SubLevelData data = storage.attemptLoadSubLevel(selected.pointer().chunkPos(), pointer);
            if (data != null) {
                dataById.put(data.uuid(), data);
                pointerById.put(
                        data.uuid(),
                        new GlobalSavedSubLevelPointer(
                                selected.pointer().chunkPos(),
                                pointer.storageIndex(),
                                pointer.subLevelIndex()
                        )
                );
            }
        }
        SubLevelData root = dataById.get(selected.sableShipId());
        if (root == null) {
            return PreflightResult.failure("Selected Sable body data could not be read during preflight.");
        }
        if (!root.uuid().equals(selected.sableShipId())) {
            return PreflightResult.failure("Selected Sable pointer resolved to a different body UUID.");
        }
        List<UUID> missingDependencies = root.dependencies().stream()
                .filter(dependency -> !dataById.containsKey(dependency))
                .toList();
        if (!missingDependencies.isEmpty()) {
            return PreflightResult.failure("Selected Sable body is missing loading dependencies: " + missingDependencies);
        }
        List<HoldingSubLevel> bodies = new ArrayList<>();
        bodies.add(new HoldingSubLevel(root, pointerById.get(root.uuid())));
        for (UUID dependency : root.dependencies()) {
            bodies.add(new HoldingSubLevel(dataById.get(dependency), pointerById.get(dependency)));
        }
        return PreflightResult.success(new LoadPlan(selected.pointer(), bodies));
    }

    private static boolean finite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    public record Request(
            MinecraftServer server,
            Optional<UUID> transponderId,
            ResourceKey<Level> dimension,
            UUID sableShipId,
            BlockPos localControllerPos,
            Vec3 targetControllerPosition,
            String description,
            Optional<StoredBodyPointer> expectedPointer
    ) {
        public Request {
            Objects.requireNonNull(server, "server");
            transponderId = Objects.requireNonNull(transponderId, "transponderId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(sableShipId, "sableShipId");
            localControllerPos = Objects.requireNonNull(localControllerPos, "localControllerPos").immutable();
            Objects.requireNonNull(targetControllerPosition, "targetControllerPosition");
            description = Objects.requireNonNull(description, "description");
            expectedPointer = Objects.requireNonNull(expectedPointer, "expectedPointer");
        }
    }

    public record Result(
            ResultType type,
            boolean success,
            String reasonCode,
            String message,
            Optional<SableSubLevelVehicleController> controller
    ) {
        public Result {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(message, "message");
            controller = Objects.requireNonNull(controller, "controller");
        }

        private static Result success(
                ResultType type,
                String reasonCode,
                String message,
                SableSubLevelVehicleController controller
        ) {
            return new Result(type, true, reasonCode, message, Optional.of(controller));
        }

        private static Result failure(ResultType type, String reasonCode, String message) {
            return new Result(type, false, reasonCode, message, Optional.empty());
        }
    }

    public enum ResultType {
        ALREADY_LOADED,
        MATERIALIZED,
        MISSING,
        AMBIGUOUS,
        CORRUPT,
        NOT_READY,
        API_UNAVAILABLE,
        CONTROLLER_MISSING,
        HOLDING_BODY_WAITING,
        UNSAFE_TARGET,
        LOAD_FAILED,
        RELOCATION_FAILED
    }

    private record LoadPlan(StoredBodyPointer rootPointer, List<HoldingSubLevel> bodies) {
        private LoadPlan {
            Objects.requireNonNull(rootPointer, "rootPointer");
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
        }
    }

    private record PreflightResult(Optional<LoadPlan> plan, Optional<String> failure) {
        private PreflightResult {
            plan = Objects.requireNonNull(plan, "plan");
            failure = Objects.requireNonNull(failure, "failure");
        }

        private static PreflightResult success(LoadPlan plan) {
            return new PreflightResult(Optional.of(plan), Optional.empty());
        }

        private static PreflightResult failure(String message) {
            return new PreflightResult(Optional.empty(), Optional.of(message));
        }
    }

    private record HoldingLocationResult(
            Optional<GlobalSavedSubLevelPointer> pointer,
            Optional<String> failure,
            boolean ambiguous
    ) {
        private static HoldingLocationResult success(GlobalSavedSubLevelPointer pointer) {
            return new HoldingLocationResult(Optional.of(pointer), Optional.empty(), false);
        }

        private static HoldingLocationResult failure(boolean ambiguous, String message) {
            return new HoldingLocationResult(Optional.empty(), Optional.of(message), ambiguous);
        }
    }
}

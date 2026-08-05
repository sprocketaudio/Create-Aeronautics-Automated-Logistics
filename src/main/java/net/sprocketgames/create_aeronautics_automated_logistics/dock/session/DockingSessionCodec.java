package net.sprocketgames.create_aeronautics_automated_logistics.dock.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public final class DockingSessionCodec {
    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String REQUEST = "request";
    private static final String REQUEST_ID = "requestId";
    private static final String ROUTE_ID = "routeId";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SCHEDULE_EXECUTION_ID = "scheduleExecutionId";
    private static final String STOP_ID = "stopId";
    private static final String TARGET = "target";
    private static final String DIMENSION = "dimension";
    private static final String STATION_ID = "stationId";
    private static final String STATION_POS = "stationPos";
    private static final String STATION_DOCK_POS = "stationDockPos";
    private static final String PHASE = "phase";
    private static final String RESUME_PHASE = "resumePhase";
    private static final String RUNTIME_MODE = "runtimeMode";
    private static final String QUEUE_POSITION = "queuePosition";
    private static final String OUTPUTS_OWNED = "outputsOwned";
    private static final String EXPECTED_CONNECTOR_LOCKED = "expectedConnectorLocked";
    private static final String WAIT_ACTIVE = "waitActive";
    private static final String PAUSED = "paused";
    private static final String REASON = "reason";
    private static final String REASON_DETAIL = "reasonDetail";
    private static final String OBSERVED_GAME_TIME = "observedGameTime";

    private DockingSessionCodec() {
    }

    public static CompoundTag write(DockingSession session) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(SCHEMA_VERSION, session.schemaVersion());
        tag.putUUID(ROUTE_ID, session.routeId().value());

        CompoundTag request = new CompoundTag();
        request.putUUID(REQUEST_ID, session.requestId().value());
        request.putUUID(ROUTE_ID, session.requestId().legacyRouteId().value());
        session.requestId().transponderId().ifPresent(id -> request.putUUID(TRANSPONDER_ID, id));
        session.requestId().scheduleExecutionId().ifPresent(id -> request.putUUID(SCHEDULE_EXECUTION_ID, id));
        session.requestId().stopId().ifPresent(id -> request.putUUID(STOP_ID, id));
        tag.put(REQUEST, request);

        session.target().ifPresent(target -> {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putString(DIMENSION, target.dimension().location().toString());
            target.stationId().ifPresent(id -> targetTag.putUUID(STATION_ID, id));
            targetTag.put(STATION_POS, NbtUtils.writeBlockPos(target.stationPos()));
            target.stationDockPos().ifPresent(pos -> targetTag.put(STATION_DOCK_POS, NbtUtils.writeBlockPos(pos)));
            tag.put(TARGET, targetTag);
        });

        tag.putString(PHASE, session.phase().name());
        session.resumePhase().ifPresent(phase -> tag.putString(RESUME_PHASE, phase.name()));
        tag.putString(RUNTIME_MODE, session.runtimeMode().name());
        tag.putInt(QUEUE_POSITION, session.queuePosition());
        tag.putString(OUTPUTS_OWNED, session.outputsOwned().name());
        tag.putString(EXPECTED_CONNECTOR_LOCKED, session.expectedConnectorLocked().name());
        tag.putBoolean(WAIT_ACTIVE, session.waitActive());
        tag.putBoolean(PAUSED, session.paused());
        tag.putString(REASON, session.reason().name());
        session.reasonDetail().ifPresent(detail -> tag.putString(REASON_DETAIL, detail));
        tag.putLong(OBSERVED_GAME_TIME, session.observedGameTime());
        return tag;
    }

    public static DockingSessionReadResult read(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return new DockingSessionReadResult(
                    DockingSessionReadResult.Status.LEGACY_MISSING,
                    Optional.empty(),
                    List.of("typed_docking_session_missing")
            );
        }

        List<String> diagnostics = new ArrayList<>();
        try {
            int schemaVersion = tag.getInt(SCHEMA_VERSION);
            if (schemaVersion <= 0 || schemaVersion > DockingSession.CURRENT_SCHEMA_VERSION) {
                return invalid("unsupported_schema_" + schemaVersion);
            }
            if (!tag.hasUUID(ROUTE_ID) || !tag.contains(REQUEST, Tag.TAG_COMPOUND)) {
                return invalid("missing_route_or_request_identity");
            }
            RouteId routeId = new RouteId(tag.getUUID(ROUTE_ID));
            CompoundTag requestTag = tag.getCompound(REQUEST);
            RouteId requestRouteId = requestTag.hasUUID(ROUTE_ID)
                    ? new RouteId(requestTag.getUUID(ROUTE_ID))
                    : routeId;
            Optional<UUID> transponderId = optionalUuid(requestTag, TRANSPONDER_ID);
            Optional<UUID> scheduleExecutionId = optionalUuid(requestTag, SCHEDULE_EXECUTION_ID);
            Optional<UUID> stopId = optionalUuid(requestTag, STOP_ID);
            DockRequestId requestId = new DockRequestId(
                    requestTag.hasUUID(REQUEST_ID)
                            ? requestTag.getUUID(REQUEST_ID)
                            : scheduleExecutionId.orElseGet(() -> DockRequestId.legacy(
                                    requestRouteId,
                                    transponderId,
                                    stopId
                            ).value()),
                    transponderId,
                    scheduleExecutionId,
                    stopId,
                    requestRouteId
            );

            Optional<DockTargetId> target = Optional.empty();
            if (tag.contains(TARGET, Tag.TAG_COMPOUND)) {
                CompoundTag targetTag = tag.getCompound(TARGET);
                if (!targetTag.contains(DIMENSION, Tag.TAG_STRING)
                        || !targetTag.contains(STATION_POS)) {
                    return invalid("incomplete_dock_target");
                }
                ResourceLocation dimensionId = ResourceLocation.tryParse(targetTag.getString(DIMENSION));
                Optional<BlockPos> stationPos = NbtUtils.readBlockPos(targetTag, STATION_POS);
                if (dimensionId == null || stationPos.isEmpty()) {
                    return invalid("invalid_dock_target");
                }
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                Optional<BlockPos> stationDockPos = targetTag.contains(STATION_DOCK_POS)
                        ? NbtUtils.readBlockPos(targetTag, STATION_DOCK_POS).map(BlockPos::immutable)
                        : Optional.empty();
                target = Optional.of(new DockTargetId(
                        dimension,
                        optionalUuid(targetTag, STATION_ID),
                        stationPos.get(),
                        stationDockPos
                ));
            }

            DockingPhase phase = enumValue(tag, PHASE, DockingPhase.class);
            Optional<DockingPhase> resumePhase = tag.contains(RESUME_PHASE, Tag.TAG_STRING)
                    ? Optional.of(enumValue(tag, RESUME_PHASE, DockingPhase.class))
                    : Optional.empty();
            DockingSession session = new DockingSession(
                    schemaVersion,
                    requestId,
                    routeId,
                    target,
                    phase,
                    resumePhase,
                    enumValue(tag, RUNTIME_MODE, DockingRuntimeMode.class),
                    tag.contains(QUEUE_POSITION, Tag.TAG_ANY_NUMERIC) ? tag.getInt(QUEUE_POSITION) : -1,
                    enumValue(tag, OUTPUTS_OWNED, DockingEvidence.class),
                    enumValue(tag, EXPECTED_CONNECTOR_LOCKED, DockingEvidence.class),
                    tag.getBoolean(WAIT_ACTIVE),
                    tag.getBoolean(PAUSED),
                    enumValue(tag, REASON, DockingReason.class),
                    tag.contains(REASON_DETAIL, Tag.TAG_STRING)
                            ? Optional.of(tag.getString(REASON_DETAIL))
                            : Optional.empty(),
                    tag.getLong(OBSERVED_GAME_TIME)
            );
            if (!session.requestId().legacyRouteId().equals(session.routeId())) {
                diagnostics.add("request_route_differs_from_session_route");
            }
            if (session.phase() == DockingPhase.QUEUED && session.queuePosition() <= 0) {
                diagnostics.add("queued_phase_without_queue_position");
            }
            return new DockingSessionReadResult(
                    DockingSessionReadResult.Status.RESTORED,
                    Optional.of(session),
                    diagnostics
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return invalid("invalid_typed_session_" + exception.getClass().getSimpleName());
        }
    }

    private static Optional<UUID> optionalUuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? Optional.of(tag.getUUID(key)) : Optional.empty();
    }

    private static <E extends Enum<E>> E enumValue(CompoundTag tag, String key, Class<E> type) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return Enum.valueOf(type, tag.getString(key));
    }

    private static DockingSessionReadResult invalid(String diagnostic) {
        return new DockingSessionReadResult(
                DockingSessionReadResult.Status.INVALID,
                Optional.empty(),
                List.of(diagnostic)
        );
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockTargetId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;

public final class DockReservationService {
    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String RESERVATIONS = "reservations";
    private static final String DIMENSION = "dimension";
    private static final String STATION_ID = "stationId";
    private static final String STATION_POS = "stationPos";
    private static final String STATION_DOCK = "stationDock";
    private static final String HOLDER = "holder";
    private static final String WAITING = "waiting";
    private static final String REQUEST_ID = "requestId";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String SCHEDULE_EXECUTION_ID = "scheduleExecutionId";
    private static final String STOP_ID = "stopId";
    private static final String ROUTE_ID = "routeId";

    private final Map<DockTargetId, DockReservation> reservations = new HashMap<>();

    public ReservationResult request(
            DockTargetId target,
            DockRequestId request,
            Predicate<DockRequestId> activeRequest
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(activeRequest, "activeRequest");
        if (!target.exact()) {
            return ReservationResult.refused(ReservationReason.INCOMPLETE_TARGET);
        }

        migrateLegacyTarget(target);
        migrateLegacyRequestAcrossTargets(request);
        consolidateDuplicateRequestAtTarget(request, target);
        Optional<DockTargetId> existingTarget = targetFor(request);
        if (existingTarget.isPresent() && !samePhysicalDock(existingTarget.get(), target)) {
            return ReservationResult.refused(ReservationReason.HELD_OTHER_TARGET);
        }
        DockReservation reservation = reservations.computeIfAbsent(target, ignored -> new DockReservation());
        reservation.migrateLegacyRequest(request);
        if (reservation.scrubInactive(activeRequest, request.value())) {
            logState("scrubbed_inactive", target, request, reservation, -1);
        }
        ReservationResult result = reservation.request(request);
        if (result.changed()) {
            logState(result.granted() ? "granted" : "queued", target, request, reservation, result.queuePosition());
        }
        return result;
    }

    public boolean release(DockRequestId request) {
        Objects.requireNonNull(request, "request");
        boolean released = false;
        Iterator<Map.Entry<DockTargetId, DockReservation>> iterator = reservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DockTargetId, DockReservation> entry = iterator.next();
            if (!entry.getValue().release(request.value())) {
                continue;
            }
            released = true;
            logState("released", entry.getKey(), request, entry.getValue(), -1);
            if (entry.getValue().empty()) {
                iterator.remove();
            }
        }
        return released;
    }

    public boolean release(DockTargetId target, DockRequestId request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        Optional<Map.Entry<DockTargetId, DockReservation>> entry = matchingTarget(target);
        if (entry.isEmpty() || !entry.get().getValue().release(request.value())) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation targeted release refused: request={} route={} target={} reason=not_held_or_queued",
                    request.value(),
                    request.legacyRouteId().value(),
                    targetSummary(target)
            );
            return false;
        }
        logState("targeted_release", entry.get().getKey(), request, entry.get().getValue(), -1);
        if (entry.get().getValue().empty()) {
            reservations.remove(entry.get().getKey());
        }
        return true;
    }

    public ReservationStatus status(DockRequestId request) {
        Objects.requireNonNull(request, "request");
        for (DockReservation reservation : reservations.values()) {
            ReservationStatus status = reservation.status(request.value());
            if (status.tracked()) {
                return status;
            }
        }
        return ReservationStatus.untracked();
    }

    public Optional<DockTargetId> targetFor(DockRequestId request) {
        Objects.requireNonNull(request, "request");
        return reservations.entrySet().stream()
                .filter(entry -> entry.getValue().status(request.value()).tracked())
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public List<ReservationSnapshot> snapshots() {
        return reservations.entrySet().stream()
                .map(entry -> new ReservationSnapshot(
                        entry.getKey(),
                        entry.getValue().holder(),
                        entry.getValue().waiting()
                ))
                .sorted((first, second) -> targetSummary(first.target()).compareTo(targetSummary(second.target())))
                .toList();
    }

    public void reset(String reason) {
        int dockCount = reservations.size();
        int held = (int) reservations.values().stream().filter(value -> value.holder().isPresent()).count();
        int queued = reservations.values().stream().mapToInt(DockReservation::waitingCount).sum();
        reservations.clear();
        if (dockCount > 0 || held > 0 || queued > 0) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation runtime reset: reason={} docks={} held={} queued={}",
                    reason,
                    dockCount,
                    held,
                    queued
            );
        }
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        ListTag savedReservations = new ListTag();
        snapshots().forEach(snapshot -> {
            CompoundTag tag = writeTarget(snapshot.target());
            snapshot.holder().ifPresent(holder -> tag.put(HOLDER, writeRequest(holder)));
            ListTag waiting = new ListTag();
            snapshot.waiting().forEach(request -> waiting.add(writeRequest(request)));
            tag.put(WAITING, waiting);
            savedReservations.add(tag);
        });
        root.put(RESERVATIONS, savedReservations);
        return root;
    }

    public void load(CompoundTag root) {
        reservations.clear();
        if (root == null || !root.contains(RESERVATIONS, Tag.TAG_LIST)) {
            CreateAeronauticsAutomatedLogistics.debugDocking(
                    "Dock reservation runtime restore found no saved reservation snapshot"
            );
            return;
        }
        int schema = root.contains(SCHEMA_VERSION, Tag.TAG_ANY_NUMERIC) ? root.getInt(SCHEMA_VERSION) : 1;
        ListTag savedReservations = root.getList(RESERVATIONS, Tag.TAG_COMPOUND);
        int migrated = 0;
        for (int i = 0; i < savedReservations.size(); i++) {
            CompoundTag tag = savedReservations.getCompound(i);
            Optional<DockTargetId> target = readTarget(tag, schema);
            if (target.isEmpty()) {
                continue;
            }
            Optional<DockRequestId> holder = readHolder(tag, schema);
            ArrayDeque<DockRequestId> waiting = readWaiting(tag, schema, holder);
            if (holder.isEmpty() && waiting.isEmpty()) {
                continue;
            }
            reservations.put(target.get(), new DockReservation(holder.orElse(null), waiting));
            if (schema < CURRENT_SCHEMA_VERSION) {
                migrated++;
            }
        }
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation runtime restored: schema={} docks={} held={} queued={} legacyMigrated={}",
                schema,
                reservations.size(),
                reservations.values().stream().filter(value -> value.holder().isPresent()).count(),
                reservations.values().stream().mapToInt(DockReservation::waitingCount).sum(),
                migrated
        );
    }

    private void migrateLegacyTarget(DockTargetId exactTarget) {
        Optional<Map.Entry<DockTargetId, DockReservation>> legacy = matchingTarget(exactTarget)
                .filter(entry -> !entry.getKey().equals(exactTarget));
        if (legacy.isEmpty()) {
            return;
        }
        reservations.remove(legacy.get().getKey());
        DockReservation existing = reservations.putIfAbsent(exactTarget, legacy.get().getValue());
        if (existing != null) {
            existing.merge(legacy.get().getValue());
        }
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation target migrated: oldTarget={} exactTarget={}",
                targetSummary(legacy.get().getKey()),
                targetSummary(exactTarget)
        );
    }

    private void migrateLegacyRequestAcrossTargets(DockRequestId request) {
        if (request.scheduleExecutionId().isEmpty()) {
            return;
        }
        reservations.values().forEach(reservation -> reservation.migrateLegacyRequest(request));
    }

    private void consolidateDuplicateRequestAtTarget(DockRequestId request, DockTargetId currentTarget) {
        Optional<Map.Entry<DockTargetId, DockReservation>> current = matchingTarget(currentTarget);
        if (current.isEmpty() || !current.get().getValue().status(request.value()).tracked()) {
            return;
        }
        Iterator<Map.Entry<DockTargetId, DockReservation>> iterator = reservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DockTargetId, DockReservation> entry = iterator.next();
            if (samePhysicalDock(entry.getKey(), currentTarget)
                    || !entry.getValue().release(request.value())) {
                continue;
            }
            logState("removed_duplicate_request", entry.getKey(), request, entry.getValue(), -1);
            if (entry.getValue().empty()) {
                iterator.remove();
            }
        }
    }

    private Optional<Map.Entry<DockTargetId, DockReservation>> matchingTarget(DockTargetId target) {
        return reservations.entrySet().stream()
                .filter(entry -> samePhysicalDock(entry.getKey(), target))
                .findFirst();
    }

    private boolean samePhysicalDock(DockTargetId first, DockTargetId second) {
        return first.dimension().equals(second.dimension())
                && first.stationDockPos().isPresent()
                && first.stationDockPos().equals(second.stationDockPos());
    }

    private CompoundTag writeTarget(DockTargetId target) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION, target.dimension().location().toString());
        target.stationId().ifPresent(id -> tag.putUUID(STATION_ID, id));
        tag.put(STATION_POS, NbtUtils.writeBlockPos(target.stationPos()));
        target.stationDockPos().ifPresent(pos -> tag.put(STATION_DOCK, NbtUtils.writeBlockPos(pos)));
        return tag;
    }

    private Optional<DockTargetId> readTarget(CompoundTag tag, int schema) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        Optional<BlockPos> stationDock = NbtUtils.readBlockPos(tag, STATION_DOCK);
        if (dimensionId == null || stationDock.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockPos> stationPos = schema >= 2
                ? NbtUtils.readBlockPos(tag, STATION_POS)
                : stationDock;
        if (stationPos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DockTargetId(
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                tag.hasUUID(STATION_ID) ? Optional.of(tag.getUUID(STATION_ID)) : Optional.empty(),
                stationPos.get(),
                stationDock
        ));
    }

    private Optional<DockRequestId> readHolder(CompoundTag tag, int schema) {
        if (schema >= 2 && tag.contains(HOLDER, Tag.TAG_COMPOUND)) {
            return readRequest(tag.getCompound(HOLDER));
        }
        return tag.hasUUID(HOLDER)
                ? Optional.of(DockRequestId.legacy(
                        new RouteId(tag.getUUID(HOLDER)),
                        Optional.empty(),
                        Optional.empty()
                ))
                : Optional.empty();
    }

    private ArrayDeque<DockRequestId> readWaiting(
            CompoundTag tag,
            int schema,
            Optional<DockRequestId> holder
    ) {
        ArrayDeque<DockRequestId> waiting = new ArrayDeque<>();
        if (!tag.contains(WAITING, Tag.TAG_LIST)) {
            return waiting;
        }
        ListTag list = tag.getList(WAITING, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag requestTag = list.getCompound(i);
            Optional<DockRequestId> request = schema >= 2
                    ? readRequest(requestTag)
                    : requestTag.hasUUID(ROUTE_ID)
                            ? Optional.of(DockRequestId.legacy(
                                    new RouteId(requestTag.getUUID(ROUTE_ID)),
                                    Optional.empty(),
                                    Optional.empty()
                            ))
                            : Optional.empty();
            request.filter(candidate -> holder.map(value -> !value.value().equals(candidate.value())).orElse(true))
                    .filter(candidate -> waiting.stream().noneMatch(existing -> existing.value().equals(candidate.value())))
                    .ifPresent(waiting::addLast);
        }
        return waiting;
    }

    private CompoundTag writeRequest(DockRequestId request) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(REQUEST_ID, request.value());
        tag.putUUID(ROUTE_ID, request.legacyRouteId().value());
        request.transponderId().ifPresent(id -> tag.putUUID(TRANSPONDER_ID, id));
        request.scheduleExecutionId().ifPresent(id -> tag.putUUID(SCHEDULE_EXECUTION_ID, id));
        request.stopId().ifPresent(id -> tag.putUUID(STOP_ID, id));
        return tag;
    }

    private Optional<DockRequestId> readRequest(CompoundTag tag) {
        if (!tag.hasUUID(REQUEST_ID) || !tag.hasUUID(ROUTE_ID)) {
            return Optional.empty();
        }
        return Optional.of(new DockRequestId(
                tag.getUUID(REQUEST_ID),
                tag.hasUUID(TRANSPONDER_ID) ? Optional.of(tag.getUUID(TRANSPONDER_ID)) : Optional.empty(),
                tag.hasUUID(SCHEDULE_EXECUTION_ID) ? Optional.of(tag.getUUID(SCHEDULE_EXECUTION_ID)) : Optional.empty(),
                tag.hasUUID(STOP_ID) ? Optional.of(tag.getUUID(STOP_ID)) : Optional.empty(),
                new RouteId(tag.getUUID(ROUTE_ID))
        ));
    }

    private void logState(
            String action,
            DockTargetId target,
            DockRequestId request,
            DockReservation reservation,
            int queuePosition
    ) {
        CreateAeronauticsAutomatedLogistics.debugDocking(
                "Dock reservation {}: request={} transponder={} execution={} stop={} route={} target={} holder={} queuePosition={} queueSize={}",
                action,
                request.value(),
                request.transponderId().map(UUID::toString).orElse("unknown"),
                request.scheduleExecutionId().map(UUID::toString).orElse("legacy"),
                request.stopId().map(UUID::toString).orElse("unknown"),
                request.legacyRouteId().value(),
                targetSummary(target),
                reservation.holder().map(DockRequestId::value).map(UUID::toString).orElse("none"),
                queuePosition,
                reservation.waitingCount()
        );
    }

    private static String targetSummary(DockTargetId target) {
        return target.dimension().location()
                + ":station=" + target.stationId().map(UUID::toString).orElse("legacy")
                + "@" + target.stationPos().toShortString()
                + ":dock=" + target.stationDockPos().map(BlockPos::toShortString).orElse("unknown");
    }

    public enum ReservationReason {
        GRANTED,
        QUEUED,
        INCOMPLETE_TARGET,
        HELD_OTHER_TARGET
    }

    public record ReservationResult(
            boolean granted,
            int queuePosition,
            ReservationReason reason,
            boolean changed
    ) {
        private static ReservationResult granted(boolean changed) {
            return new ReservationResult(true, 0, ReservationReason.GRANTED, changed);
        }

        private static ReservationResult queued(int queuePosition, boolean changed) {
            return new ReservationResult(false, queuePosition, ReservationReason.QUEUED, changed);
        }

        private static ReservationResult refused(ReservationReason reason) {
            return new ReservationResult(false, -1, reason, false);
        }
    }

    public record ReservationStatus(boolean tracked, boolean granted, int queuePosition) {
        public static ReservationStatus untracked() {
            return new ReservationStatus(false, false, -1);
        }

        private static ReservationStatus held() {
            return new ReservationStatus(true, true, 0);
        }

        private static ReservationStatus queued(int queuePosition) {
            return new ReservationStatus(true, false, queuePosition);
        }
    }

    public record ReservationSnapshot(
            DockTargetId target,
            Optional<DockRequestId> holder,
            List<DockRequestId> waiting
    ) {
        public ReservationSnapshot {
            waiting = List.copyOf(waiting);
        }
    }

    private static final class DockReservation {
        private DockRequestId holder;
        private final ArrayDeque<DockRequestId> waiting = new ArrayDeque<>();

        private DockReservation() {
        }

        private DockReservation(DockRequestId holder, ArrayDeque<DockRequestId> waiting) {
            this.holder = holder;
            this.waiting.addAll(waiting);
        }

        private ReservationResult request(DockRequestId request) {
            if (holder != null && holder.value().equals(request.value())) {
                holder = request;
                return ReservationResult.granted(false);
            }
            if (holder == null && (waiting.isEmpty() || waiting.peekFirst().value().equals(request.value()))) {
                remove(request.value());
                holder = request;
                return ReservationResult.granted(true);
            }
            int queuePosition = queuePosition(request.value());
            boolean added = queuePosition < 0;
            if (added) {
                waiting.addLast(request);
                queuePosition = waiting.size();
            } else {
                replaceWaiting(request);
            }
            return ReservationResult.queued(queuePosition, added);
        }

        private boolean release(UUID requestId) {
            boolean changed = false;
            if (holder != null && holder.value().equals(requestId)) {
                holder = waiting.pollFirst();
                changed = true;
            }
            if (remove(requestId)) {
                changed = true;
            }
            return changed;
        }

        private boolean scrubInactive(Predicate<DockRequestId> activeRequest, UUID requestingId) {
            boolean changed = false;
            if (holder != null
                    && !holder.value().equals(requestingId)
                    && !activeRequest.test(holder)) {
                holder = null;
                changed = true;
            }
            if (waiting.removeIf(request -> !request.value().equals(requestingId) && !activeRequest.test(request))) {
                changed = true;
            }
            if (holder == null && !waiting.isEmpty()) {
                holder = waiting.pollFirst();
                changed = true;
            }
            return changed;
        }

        private void migrateLegacyRequest(DockRequestId request) {
            if (request.scheduleExecutionId().isEmpty()) {
                return;
            }
            if (holder != null
                    && holder.scheduleExecutionId().isEmpty()
                    && holder.legacyRouteId().equals(request.legacyRouteId())) {
                holder = request;
            }
            List<DockRequestId> replaced = new ArrayList<>(waiting.size());
            for (DockRequestId queued : waiting) {
                replaced.add(queued.scheduleExecutionId().isEmpty()
                        && queued.legacyRouteId().equals(request.legacyRouteId())
                        ? request
                        : queued);
            }
            waiting.clear();
            replaced.stream()
                    .filter(candidate -> holder == null || !holder.value().equals(candidate.value()))
                    .filter(candidate -> waiting.stream().noneMatch(existing -> existing.value().equals(candidate.value())))
                    .forEach(waiting::addLast);
        }

        private void merge(DockReservation other) {
            if (holder == null) {
                holder = other.holder;
            } else if (other.holder != null && !holder.value().equals(other.holder.value())) {
                waiting.addFirst(other.holder);
            }
            for (DockRequestId queued : other.waiting) {
                if ((holder == null || !holder.value().equals(queued.value()))
                        && waiting.stream().noneMatch(existing -> existing.value().equals(queued.value()))) {
                    waiting.addLast(queued);
                }
            }
        }

        private ReservationStatus status(UUID requestId) {
            if (holder != null && holder.value().equals(requestId)) {
                return ReservationStatus.held();
            }
            int queuePosition = queuePosition(requestId);
            return queuePosition > 0 ? ReservationStatus.queued(queuePosition) : ReservationStatus.untracked();
        }

        private int queuePosition(UUID requestId) {
            int position = 1;
            for (DockRequestId queued : waiting) {
                if (queued.value().equals(requestId)) {
                    return position;
                }
                position++;
            }
            return -1;
        }

        private boolean remove(UUID requestId) {
            return waiting.removeIf(request -> request.value().equals(requestId));
        }

        private void replaceWaiting(DockRequestId request) {
            List<DockRequestId> updated = waiting.stream()
                    .map(existing -> existing.value().equals(request.value()) ? request : existing)
                    .toList();
            waiting.clear();
            waiting.addAll(updated);
        }

        private Optional<DockRequestId> holder() {
            return Optional.ofNullable(holder);
        }

        private int waitingCount() {
            return waiting.size();
        }

        private List<DockRequestId> waiting() {
            return List.copyOf(waiting);
        }

        private boolean empty() {
            return holder == null && waiting.isEmpty();
        }
    }
}

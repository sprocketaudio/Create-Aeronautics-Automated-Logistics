package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockRequestId;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.session.DockTargetId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import org.junit.jupiter.api.Test;

class DockReservationServiceTest {
    private final DockReservationService reservations = new DockReservationService();

    @Test
    void legacyRouteQueueLoadsAndMigratesInPlaceToStableRequestIdentity() {
        RouteId legacyHolder = routeId("00000000-0000-0000-0000-000000000001");
        RouteId legacyQueued = routeId("00000000-0000-0000-0000-000000000002");
        BlockPos dockPos = new BlockPos(12, 64, 34);
        reservations.load(legacyRuntime(reservation(dockPos, legacyHolder, legacyQueued)));

        assertStatus(reservations.status(DockRequestId.legacy(
                legacyHolder,
                Optional.empty(),
                Optional.empty()
        )), true, true, 0);
        assertStatus(reservations.status(DockRequestId.legacy(
                legacyQueued,
                Optional.empty(),
                Optional.empty()
        )), true, false, 1);

        DockRequestId stable = scheduledRequest(
                legacyHolder,
                "00000000-0000-0000-0000-000000000010",
                "00000000-0000-0000-0000-000000000011"
        );
        DockReservationService.ReservationResult result = reservations.request(
                exactTarget(dockPos),
                stable,
                ignored -> true
        );

        assertTrue(result.granted());
        assertStatus(reservations.status(stable), true, true, 0);
        assertStatus(reservations.status(DockRequestId.legacy(
                legacyQueued,
                Optional.empty(),
                Optional.empty()
        )), true, false, 1);
        CompoundTag saved = reservations.save();
        assertEquals(2, saved.getInt("schemaVersion"));
        CompoundTag savedReservation = saved.getList("reservations", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(stable.value(), savedReservation.getCompound("holder").getUUID("requestId"));
    }

    @Test
    void stableExecutionIdentityDoesNotDependOnRouteLegHandoff() {
        DockTargetId target = exactTarget(new BlockPos(30, 70, -2));
        UUID executionId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID transponderId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        DockRequestId firstLeg = DockRequestId.scheduled(
                transponderId,
                executionId,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000022")),
                routeId("00000000-0000-0000-0000-000000000023")
        );
        DockRequestId nextLeg = DockRequestId.scheduled(
                transponderId,
                executionId,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000024")),
                routeId("00000000-0000-0000-0000-000000000025")
        );

        assertTrue(reservations.request(target, firstLeg, ignored -> true).granted());
        assertTrue(reservations.status(nextLeg).granted());
        assertEquals(target, reservations.targetFor(nextLeg).orElseThrow());
    }

    @Test
    void requestCannotMoveToNextDockUntilPreviousTargetIsExplicitlyReleased() {
        DockRequestId request = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000030"),
                "00000000-0000-0000-0000-000000000031",
                "00000000-0000-0000-0000-000000000032"
        );
        DockTargetId first = exactTarget(new BlockPos(4, 90, 4));
        DockTargetId second = new DockTargetId(
                Level.OVERWORLD,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000033")),
                new BlockPos(40, 90, 40),
                Optional.of(new BlockPos(41, 90, 40))
        );

        assertTrue(reservations.request(first, request, ignored -> true).granted());
        DockReservationService.ReservationResult refused = reservations.request(second, request, ignored -> true);
        assertFalse(refused.granted());
        assertEquals(DockReservationService.ReservationReason.HELD_OTHER_TARGET, refused.reason());
        assertEquals(first, reservations.targetFor(request).orElseThrow());

        assertTrue(reservations.release(first, request));
        assertTrue(reservations.request(second, request, ignored -> true).granted());
    }

    @Test
    void targetedReleasePromotesOnlyTheMatchingDockQueue() {
        DockTargetId firstDock = exactTarget(new BlockPos(4, 90, 4));
        DockTargetId otherDock = new DockTargetId(
                Level.OVERWORLD,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000040")),
                new BlockPos(40, 90, 40),
                Optional.of(new BlockPos(41, 90, 40))
        );
        DockRequestId holder = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000041"),
                "00000000-0000-0000-0000-000000000042",
                "00000000-0000-0000-0000-000000000043"
        );
        DockRequestId promoted = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000044"),
                "00000000-0000-0000-0000-000000000045",
                "00000000-0000-0000-0000-000000000046"
        );
        DockRequestId otherHolder = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000047"),
                "00000000-0000-0000-0000-000000000048",
                "00000000-0000-0000-0000-000000000049"
        );

        reservations.request(firstDock, holder, ignored -> true);
        reservations.request(firstDock, promoted, ignored -> true);
        reservations.request(otherDock, otherHolder, ignored -> true);
        assertTrue(reservations.release(firstDock, holder));

        assertStatus(reservations.status(holder), false, false, -1);
        assertStatus(reservations.status(promoted), true, true, 0);
        assertStatus(reservations.status(otherHolder), true, true, 0);
    }

    @Test
    void saveLoadPreservesStableQueueOrderAndExactTarget() {
        DockTargetId target = exactTarget(new BlockPos(99, 70, 99));
        DockRequestId holder = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000050"),
                "00000000-0000-0000-0000-000000000051",
                "00000000-0000-0000-0000-000000000052"
        );
        DockRequestId first = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000053"),
                "00000000-0000-0000-0000-000000000054",
                "00000000-0000-0000-0000-000000000055"
        );
        DockRequestId second = scheduledRequest(
                routeId("00000000-0000-0000-0000-000000000056"),
                "00000000-0000-0000-0000-000000000057",
                "00000000-0000-0000-0000-000000000058"
        );
        reservations.request(target, holder, ignored -> true);
        reservations.request(target, first, ignored -> true);
        reservations.request(target, second, ignored -> true);

        CompoundTag saved = reservations.save();
        DockReservationService restored = new DockReservationService();
        restored.load(saved);

        assertEquals(target, restored.snapshots().getFirst().target());
        assertEquals(holder.value(), restored.snapshots().getFirst().holder().orElseThrow().value());
        assertEquals(
                java.util.List.of(first.value(), second.value()),
                restored.snapshots().getFirst().waiting().stream().map(DockRequestId::value).toList()
        );
    }

    @Test
    void explicitCurrentTargetConsolidatesLegacyDuplicateRouteReservations() {
        RouteId legacyRoute = routeId("00000000-0000-0000-0000-000000000060");
        BlockPos currentDock = new BlockPos(10, 80, 10);
        BlockPos staleDock = new BlockPos(50, 80, 50);
        reservations.load(legacyRuntime(
                reservation(currentDock, legacyRoute),
                reservation(staleDock, legacyRoute)
        ));
        DockRequestId stable = scheduledRequest(
                legacyRoute,
                "00000000-0000-0000-0000-000000000061",
                "00000000-0000-0000-0000-000000000062"
        );

        DockReservationService.ReservationResult result =
                reservations.request(exactTarget(currentDock), stable, ignored -> true);

        assertTrue(result.granted());
        assertEquals(1, reservations.snapshots().size());
        assertEquals(currentDock, reservations.snapshots().getFirst().target().stationDockPos().orElseThrow());
    }

    private static DockRequestId scheduledRequest(RouteId routeId, String executionId, String transponderId) {
        UUID execution = UUID.fromString(executionId);
        return DockRequestId.scheduled(
                UUID.fromString(transponderId),
                execution,
                Optional.of(UUID.nameUUIDFromBytes(("stop:" + execution).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                routeId
        );
    }

    private static DockTargetId exactTarget(BlockPos dockPos) {
        return new DockTargetId(
                Level.OVERWORLD,
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000099")),
                dockPos.offset(-1, 0, 0),
                Optional.of(dockPos)
        );
    }

    private static void assertStatus(
            DockReservationService.ReservationStatus status,
            boolean tracked,
            boolean granted,
            int queuePosition
    ) {
        assertEquals(tracked, status.tracked());
        assertEquals(granted, status.granted());
        assertEquals(queuePosition, status.queuePosition());
    }

    private static CompoundTag legacyRuntime(CompoundTag... entries) {
        CompoundTag runtime = new CompoundTag();
        ListTag reservations = new ListTag();
        for (CompoundTag entry : entries) {
            reservations.add(entry);
        }
        runtime.put("reservations", reservations);
        return runtime;
    }

    private static CompoundTag reservation(BlockPos stationDock, RouteId holder, RouteId... waiting) {
        CompoundTag reservation = new CompoundTag();
        reservation.putString("dimension", Level.OVERWORLD.location().toString());
        reservation.put("stationDock", NbtUtils.writeBlockPos(stationDock));
        reservation.putUUID("holder", holder.value());
        ListTag waitingRoutes = new ListTag();
        for (RouteId routeId : waiting) {
            CompoundTag routeTag = new CompoundTag();
            routeTag.putUUID("routeId", routeId.value());
            waitingRoutes.add(routeTag);
        }
        reservation.put("waiting", waitingRoutes);
        return reservation;
    }

    private static RouteId routeId(String uuid) {
        return new RouteId(UUID.fromString(uuid));
    }
}

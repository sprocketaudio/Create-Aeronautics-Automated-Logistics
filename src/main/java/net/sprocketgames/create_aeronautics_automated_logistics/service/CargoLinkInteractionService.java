package net.sprocketgames.create_aeronautics_automated_logistics.service;

import com.simibubi.create.AllSoundEvents;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkDiscovery;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetCargoLinkPromptPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;

public final class CargoLinkInteractionService {
    private static final long LINK_TIMEOUT_TICKS = 20L * 30L;
    private static final Map<UUID, PendingCargoLink> PENDING = new ConcurrentHashMap<>();

    private CargoLinkInteractionService() {
    }

    public static void beginStationLink(ServerPlayer player, BlockPos stationPos) {
        PENDING.put(player.getUUID(), new PendingCargoLink(LinkTarget.STATION, stationPos.immutable(), player.level().dimension(), player.level().getGameTime() + LINK_TIMEOUT_TICKS));
        PacketDistributor.sendToPlayer(player, new SetCargoLinkPromptPayload(true, false, stationPos.immutable(), discoverCandidateGroups(player, stationPos)));
    }

    public static void beginTransponderLink(ServerPlayer player, BlockPos transponderPos) {
        PENDING.put(player.getUUID(), new PendingCargoLink(LinkTarget.TRANSPONDER, transponderPos.immutable(), player.level().dimension(), player.level().getGameTime() + LINK_TIMEOUT_TICKS));
        PacketDistributor.sendToPlayer(player, new SetCargoLinkPromptPayload(true, true, transponderPos.immutable(), discoverCandidateGroups(player, transponderPos)));
    }

    public static boolean hasPendingStationLink(ServerPlayer player, BlockPos stationPos) {
        PendingCargoLink pending = pending(player);
        return pending != null && pending.target() == LinkTarget.STATION && pending.ownerPos().equals(stationPos);
    }

    public static boolean hasPendingTransponderLink(ServerPlayer player, BlockPos transponderPos) {
        PendingCargoLink pending = pending(player);
        return pending != null && pending.target() == LinkTarget.TRANSPONDER && pending.ownerPos().equals(transponderPos);
    }

    public static boolean cancelPendingIfSource(ServerPlayer player, BlockPos ownerPos) {
        PendingCargoLink pending = pending(player);
        if (pending == null || !pending.ownerPos().equals(ownerPos)) {
            return false;
        }
        return cancelPending(player);
    }

    public static boolean cancelPending(ServerPlayer player) {
        PendingCargoLink removed = PENDING.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SetCargoLinkPromptPayload(false, false, BlockPos.ZERO, List.of()));
        if (removed == null) {
            return false;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.cancelled"));
        return true;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PendingCargoLink pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!pending.dimension().equals(player.level().dimension()) || player.level().getGameTime() > pending.expiresAt()) {
            PENDING.remove(player.getUUID());
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.expired"));
            PacketDistributor.sendToPlayer(player, new SetCargoLinkPromptPayload(false, false, BlockPos.ZERO, List.of()));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        BlockPos clickedPos = event.getPos();
        if (clickedPos.equals(pending.ownerPos())) {
            cancelPending(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (!CargoLinkSupport.isSupportedLinkTarget(player.serverLevel(), clickedPos)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.unsupported"));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        switch (pending.target()) {
            case STATION -> tryLinkStation(player, pending.ownerPos(), clickedPos);
            case TRANSPONDER -> tryLinkTransponder(player, pending.ownerPos(), clickedPos);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void tryLinkStation(ServerPlayer player, BlockPos stationPos, BlockPos clickedPos) {
        if (!(player.serverLevel().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.owner_missing"));
            return;
        }
        if (!StationPermissionService.ensureCanControl(player, station)) {
            return;
        }
        if (station.isRecording()
                || station.selectedTransponderId()
                .map(AutomatedLogisticsServices.SCHEDULES::isRunning)
                .orElse(false)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), stationPos, 0.5f, 1.0f);
            return;
        }
        if (!isWithinRange(stationPos, clickedPos)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.station_invalid"));
            AllSoundEvents.DENY.playOnServer(player.level(), stationPos, 0.5f, 1.0f);
            return;
        }
        int added = addEntries(player, stationPos, clickedPos, station::addLinkedCargoEntries);
        if (added <= 0) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.already_linked"));
            return;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.station_added", added));
        AllSoundEvents.CONFIRM.playOnServer(player.level(), stationPos, 0.6f, 1.0f);
    }

    private static void tryLinkTransponder(ServerPlayer player, BlockPos transponderPos, BlockPos clickedPos) {
        if (!(player.serverLevel().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.owner_missing"));
            return;
        }
        if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
            return;
        }
        if (AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID()).isPresent()
                || AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), transponderPos, 0.5f, 1.0f);
            return;
        }
        if (!isWithinRange(transponderPos, clickedPos)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.transponder_invalid"));
            AllSoundEvents.DENY.playOnServer(player.level(), transponderPos, 0.5f, 1.0f);
            return;
        }
        int added = addEntries(player, transponderPos, clickedPos, transponder::addLinkedCargoEntries);
        if (added <= 0) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.already_linked"));
            return;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.transponder_added", added));
        AllSoundEvents.CONFIRM.playOnServer(player.level(), transponderPos, 0.6f, 1.0f);
    }

    private static int addEntries(ServerPlayer player, BlockPos ownerPos, BlockPos clickedPos, EntryAdder adder) {
        java.util.List<LinkedCargoEntry> entries = CargoLinkSupport.discoverLinkedGroup(
                player.serverLevel(),
                ownerPos,
                CargoLinkDiscovery.DEFAULT_LINK_RADIUS,
                clickedPos
        );
        if (entries.isEmpty()) {
            return 0;
        }
        return adder.add(entries);
    }

    private static boolean isWithinRange(BlockPos ownerPos, BlockPos clickedPos) {
        int radius = CargoLinkDiscovery.DEFAULT_LINK_RADIUS;
        return Math.abs(ownerPos.getX() - clickedPos.getX()) <= radius
                && Math.abs(ownerPos.getY() - clickedPos.getY()) <= radius
                && Math.abs(ownerPos.getZ() - clickedPos.getZ()) <= radius;
    }

    private static void actionBar(ServerPlayer player, Component message) {
        SetMenuActionBarMessagePayload.send(player, message);
    }

    private static PendingCargoLink pending(ServerPlayer player) {
        PendingCargoLink pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return null;
        }
        if (!pending.dimension().equals(player.level().dimension()) || player.level().getGameTime() > pending.expiresAt()) {
            PENDING.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new SetCargoLinkPromptPayload(false, false, BlockPos.ZERO, List.of()));
            return null;
        }
        return pending;
    }

    private static List<List<BlockPos>> discoverCandidateGroups(ServerPlayer player, BlockPos ownerPos) {
        return CargoLinkSupport.discoverSupportedGroups(player.serverLevel(), ownerPos, CargoLinkDiscovery.DEFAULT_LINK_RADIUS).stream()
                .sorted(Comparator
                        .comparingDouble((List<BlockPos> group) -> group.getFirst().distSqr(ownerPos))
                        .thenComparingInt(group -> group.getFirst().getY())
                        .thenComparingInt(group -> group.getFirst().getZ())
                        .thenComparingInt(group -> group.getFirst().getX()))
                .toList();
    }

    @FunctionalInterface
    private interface EntryAdder {
        int add(java.util.List<LinkedCargoEntry> entries);
    }

    private enum LinkTarget {
        STATION,
        TRANSPONDER
    }

    private record PendingCargoLink(LinkTarget target, BlockPos ownerPos, net.minecraft.resources.ResourceKey<Level> dimension, long expiresAt) {
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import com.simibubi.create.AllSoundEvents;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetDockLinkPromptPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockDiscoveryResult;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockingConnectorDiscovery;

public final class DockLinkInteractionService {
    private static final long LINK_TIMEOUT_TICKS = 20L * 30L;
    private static final Map<UUID, PendingDockLink> PENDING = new ConcurrentHashMap<>();

    private DockLinkInteractionService() {
    }

    public static void beginStationLink(ServerPlayer player, BlockPos stationPos) {
        PENDING.put(player.getUUID(), new PendingDockLink(LinkTarget.STATION, stationPos.immutable(), player.level().dimension(), player.level().getGameTime() + LINK_TIMEOUT_TICKS));
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.station_begin"));
        PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(true, false, stationPos.immutable()));
    }

    public static void beginTransponderLink(ServerPlayer player, BlockPos transponderPos) {
        PENDING.put(player.getUUID(), new PendingDockLink(LinkTarget.TRANSPONDER, transponderPos.immutable(), player.level().dimension(), player.level().getGameTime() + LINK_TIMEOUT_TICKS));
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.transponder_begin"));
        PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(true, true, transponderPos.immutable()));
    }

    public static void clearPending(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(false, false, BlockPos.ZERO));
    }

    public static boolean cancelPending(ServerPlayer player) {
        PendingDockLink removed = PENDING.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(false, false, BlockPos.ZERO));
        if (removed == null) {
            return false;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.cancelled"));
        return true;
    }

    public static boolean cancelPendingIfSource(ServerPlayer player, BlockPos ownerPos) {
        PendingDockLink pending = pending(player);
        if (pending == null || !pending.ownerPos().equals(ownerPos)) {
            return false;
        }
        return cancelPending(player);
    }

    public static boolean hasPendingStationLink(ServerPlayer player, BlockPos stationPos) {
        PendingDockLink pending = pending(player);
        return pending != null
                && pending.target() == LinkTarget.STATION
                && pending.ownerPos().equals(stationPos);
    }

    public static boolean hasPendingTransponderLink(ServerPlayer player, BlockPos transponderPos) {
        PendingDockLink pending = pending(player);
        return pending != null
                && pending.target() == LinkTarget.TRANSPONDER
                && pending.ownerPos().equals(transponderPos);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PendingDockLink pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!pending.dimension().equals(player.level().dimension()) || player.level().getGameTime() > pending.expiresAt()) {
            PENDING.remove(player.getUUID());
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.expired"));
            PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(false, false, BlockPos.ZERO));
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
        if (!DockingConnectorDiscovery.isDock(player.serverLevel(), clickedPos)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.not_a_dock"));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        boolean linked = switch (pending.target()) {
            case STATION -> tryLinkStation(player, pending.ownerPos(), clickedPos);
            case TRANSPONDER -> tryLinkTransponder(player, pending.ownerPos(), clickedPos);
        };
        if (linked) {
            PENDING.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(false, false, BlockPos.ZERO));
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean tryLinkStation(ServerPlayer player, BlockPos stationPos, BlockPos dockPos) {
        if (!(player.serverLevel().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.owner_missing"));
            return false;
        }
        if (!StationPermissionService.ensureCanControl(player, station)) {
            return false;
        }
        DockDiscoveryResult result = station.setGroundDockLink(player.serverLevel(), dockPos);
        if (result.status() == DockLinkStatus.LINKED) {
            actionBar(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.dock_link.station_saved",
                    dockPos.getX(),
                    dockPos.getY(),
                    dockPos.getZ()
            ));
            AllSoundEvents.DEPOT_PLOP.playOnServer(player.level(), stationPos, 0.4f, 1.15f);
            return true;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.station_invalid"));
        return false;
    }

    private static boolean tryLinkTransponder(ServerPlayer player, BlockPos transponderPos, BlockPos dockPos) {
        if (!(player.serverLevel().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.owner_missing"));
            return false;
        }
        if (!TransponderPermissionService.ensureCanControl(player, transponder)) {
            return false;
        }
        DockDiscoveryResult result = transponder.setShipDockLink(player.serverLevel(), dockPos);
        if (result.status() == DockLinkStatus.LINKED) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.transponder_saved"));
            AllSoundEvents.DEPOT_PLOP.playOnServer(player.level(), transponderPos, 0.4f, 1.15f);
            return true;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.transponder_invalid"));
        return false;
    }

    private static void actionBar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        SetMenuActionBarMessagePayload.send(player, message);
    }

    private static PendingDockLink pending(ServerPlayer player) {
        PendingDockLink pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return null;
        }
        if (!pending.dimension().equals(player.level().dimension()) || player.level().getGameTime() > pending.expiresAt()) {
            PENDING.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new SetDockLinkPromptPayload(false, false, BlockPos.ZERO));
            return null;
        }
        return pending;
    }

    private enum LinkTarget {
        STATION,
        TRANSPONDER
    }

    private record PendingDockLink(LinkTarget target, BlockPos ownerPos, net.minecraft.resources.ResourceKey<Level> dimension, long expiresAt) {
    }
}

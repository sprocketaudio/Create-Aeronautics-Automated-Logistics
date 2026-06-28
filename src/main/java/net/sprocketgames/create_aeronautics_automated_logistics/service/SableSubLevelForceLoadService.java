package net.sprocketgames.create_aeronautics_automated_logistics.service;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class SableSubLevelForceLoadService {
    private static final SubLevelLoadingTicketType<String> RUNTIME_DOCK_STOP =
            SubLevelLoadingTicketType.create(
                    ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "runtime_dock_stop"),
                    Codec.STRING
            );
    private static final Map<MinecraftServer, Map<RouteId, Lease>> ACTIVE_LEASES = new WeakHashMap<>();
    private static Field activeTicketsField;

    private SableSubLevelForceLoadService() {
    }

    public static void bootstrap() {
        // Do not register the old AAL dock-stop Sable ticket type.
        // Persisted Sable tickets restore before AAL's materialization owner can arbitrate
        // ship-body ownership, so AAL must not use them for dock runtime liveness.
    }

    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        Map<RouteId, Lease> leases = ACTIVE_LEASES.remove(event.getServer());
        if (leases == null || leases.isEmpty()) {
            return;
        }
        for (Lease lease : leases.values()) {
            ServerLevel level = event.getServer().getLevel(lease.dimension());
            if (level != null) {
                removeRuntimeTicket(level, lease.shipId(), lease.routeId(), "server_stopped");
            }
        }
    }

    public static boolean holdForDockStop(ServerLevel level, Route route, String reason) {
        UUID shipId = route.linkedController().vehicleId().orElse(null);
        return holdForDockStop(level, shipId, route.id(), reason);
    }

    public static synchronized boolean holdForDockStop(ServerLevel level, UUID shipId, RouteId routeId, String reason) {
        if (shipId == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable runtime dock lease refused: route={} ship=missing reason={} action=refused_no_ship_id",
                    routeId.value(),
                    reason
            );
            return false;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable runtime dock lease refused: route={} ship={} reason={} action=refused_no_sable_container",
                    routeId.value(),
                    shipId,
                    reason
            );
            return false;
        }
        if (!(container.getSubLevel(shipId) instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Sable runtime dock lease waiting: route={} ship={} reason={} action=refused_body_not_live",
                    routeId.value(),
                    shipId,
                    reason
            );
            return false;
        }

        Boolean added = addRuntimeTicket(level, subLevel, shipId, routeId, reason);
        if (added == null) {
            return false;
        }
        Lease previous = ACTIVE_LEASES
                .computeIfAbsent(level.getServer(), ignored -> new java.util.LinkedHashMap<>())
                .put(routeId, new Lease(level.dimension(), shipId, routeId));
        if (added || previous == null || !previous.shipId().equals(shipId)) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Sable runtime dock lease acquired: route={} ship={} reason={} persistentTicket=false action={}",
                    routeId.value(),
                    shipId,
                    reason,
                    added ? "runtime_active_ticket_added" : "runtime_lease_tracked"
            );
            logLeaseDiagnostic(level, shipId, routeId, reason + "_after_acquire");
        }
        return true;
    }

    public static void logLeaseDiagnostic(ServerLevel level, Route route, String reason) {
        UUID shipId = route.linkedController().vehicleId().orElse(null);
        logLeaseDiagnostic(level, shipId, route.id(), reason);
    }

    public static synchronized void logLeaseDiagnostic(ServerLevel level, UUID shipId, RouteId routeId, String reason) {
        if (shipId == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Sable runtime dock lease diagnostic: route={} ship=missing reason={} action=no_ship_id",
                    routeId.value(),
                    reason
            );
            return;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable runtime dock lease diagnostic: route={} ship={} reason={} action=no_sable_container",
                    routeId.value(),
                    shipId,
                    reason
            );
            return;
        }
        Map<ServerSubLevel, ObjectSet<SubLevelLoadingTicket<?>>> activeTickets = activeTickets(level);
        ServerSubLevel subLevel = container.getSubLevel(shipId) instanceof ServerSubLevel loaded ? loaded : null;
        ObjectSet<SubLevelLoadingTicket<?>> tickets = subLevel == null || activeTickets == null ? null : activeTickets.get(subLevel);
        boolean forceLoaded = subLevel != null && container.collectForceLoadedSubLevels().contains(subLevel);
        var allTicketInfo = container.getAllTickets().get(shipId);
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Sable runtime dock lease diagnostic: route={} ship={} reason={} live={} removed={} activeTicketCount={} forceLoaded={} persistentTicketCount={} pointer={}",
                routeId.value(),
                shipId,
                reason,
                subLevel != null,
                subLevel != null && subLevel.isRemoved(),
                tickets == null ? 0 : tickets.size(),
                forceLoaded,
                allTicketInfo == null ? 0 : allTicketInfo.tickets().size(),
                subLevel == null ? "none" : String.valueOf(subLevel.getLastSerializationPointer())
        );
    }

    public static void releaseDockStop(ServerLevel level, Route route, String reason) {
        UUID shipId = route.linkedController().vehicleId().orElse(null);
        releaseDockStop(level, shipId, route.id(), reason);
    }

    public static synchronized void releaseDockStop(ServerLevel level, UUID shipId, RouteId routeId, String reason) {
        Map<RouteId, Lease> serverLeases = ACTIVE_LEASES.get(level.getServer());
        Lease lease = serverLeases == null ? null : serverLeases.remove(routeId);
        UUID effectiveShipId = shipId != null ? shipId : lease == null ? null : lease.shipId();
        if (serverLeases != null && serverLeases.isEmpty()) {
            ACTIVE_LEASES.remove(level.getServer());
        }
        if (effectiveShipId == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicle(
                    "Sable runtime dock lease release skipped: route={} ship=missing reason={} action=no_runtime_lease",
                    routeId.value(),
                    reason
            );
            return;
        }
        boolean removed = removeRuntimeTicket(level, effectiveShipId, routeId, reason);
        CreateAeronauticsAutomatedLogistics.debugVehicle(
                "Sable runtime dock lease release {}: route={} ship={} reason={} persistentTicket=false action={}",
                removed ? "applied" : "skipped",
                routeId.value(),
                effectiveShipId,
                reason,
                removed ? "runtime_active_ticket_removed" : "runtime_active_ticket_missing"
        );
    }

    private static Boolean addRuntimeTicket(
            ServerLevel level,
            ServerSubLevel subLevel,
            UUID shipId,
            RouteId routeId,
            String reason
    ) {
        Map<ServerSubLevel, ObjectSet<SubLevelLoadingTicket<?>>> activeTickets = activeTickets(level);
        if (activeTickets == null) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable runtime dock lease refused: route={} ship={} reason={} action=active_ticket_map_unavailable",
                    routeId.value(),
                    shipId,
                    reason
            );
            return null;
        }
        SubLevelLoadingTicket<String> ticket = ticket(shipId, routeId);
        ObjectSet<SubLevelLoadingTicket<?>> tickets =
                activeTickets.computeIfAbsent(subLevel, ignored -> new ObjectArraySet<>());
        return tickets.add(ticket);
    }

    private static boolean removeRuntimeTicket(ServerLevel level, UUID shipId, RouteId routeId, String reason) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        Map<ServerSubLevel, ObjectSet<SubLevelLoadingTicket<?>>> activeTickets = activeTickets(level);
        if (activeTickets == null) {
            return false;
        }
        if (!(container.getSubLevel(shipId) instanceof ServerSubLevel subLevel)) {
            return false;
        }
        ObjectSet<SubLevelLoadingTicket<?>> tickets = activeTickets.get(subLevel);
        if (tickets == null) {
            return false;
        }
        boolean removed = tickets.remove(ticket(shipId, routeId));
        if (tickets.isEmpty()) {
            activeTickets.remove(subLevel);
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    private static Map<ServerSubLevel, ObjectSet<SubLevelLoadingTicket<?>>> activeTickets(ServerLevel level) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        try {
            if (activeTicketsField == null) {
                activeTicketsField = ServerSubLevelContainer.class.getDeclaredField("activeTickets");
                activeTicketsField.setAccessible(true);
            }
            return (Map<ServerSubLevel, ObjectSet<SubLevelLoadingTicket<?>>>) activeTicketsField.get(container);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Sable runtime dock lease active-ticket access failed: action=refused reason={}",
                    exception.toString()
            );
            return null;
        }
    }

    private static SubLevelLoadingTicket<String> ticket(UUID shipId, RouteId routeId) {
        return new SubLevelLoadingTicket<>(RUNTIME_DOCK_STOP, shipId, routeId.value().toString());
    }

    private record Lease(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, UUID shipId, RouteId routeId) {
    }
}

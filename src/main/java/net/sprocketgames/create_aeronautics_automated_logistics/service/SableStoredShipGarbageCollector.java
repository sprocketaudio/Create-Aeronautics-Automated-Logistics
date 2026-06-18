package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public final class SableStoredShipGarbageCollector {
    private SableStoredShipGarbageCollector() {
    }

    public static int pruneDanglingStoredShipEntries(MinecraftServer server, String source, String reasonCode) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reasonCode, "reasonCode");

        log("cleanup/prune request", source, reasonCode, "dangling_or_colliding_stored_pointer_scan", 0);
        int removed = ShipRecoveryService.pruneDanglingStoredShipEntries(server);
        if (removed > 0) {
            log("cleanup/prune applied", source, reasonCode, "stored_pointer_missing_or_colliding_proof", removed);
        } else {
            log("cleanup/prune refused", source, reasonCode, "no_dangling_or_colliding_pointer_proof", 0);
        }
        return removed;
    }

    public static int pruneLoadedDuplicateStoredShips(MinecraftServer server, String source, String reasonCode) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reasonCode, "reasonCode");

        log("cleanup/prune request", source, reasonCode, "loaded_duplicate_stored_pointer_scan", 0);
        int removed = ShipRecoveryService.pruneLoadedDuplicateStoredShips(server);
        if (removed > 0) {
            log("cleanup/prune applied", source, reasonCode, "loaded_sable_body_duplicate_pointer_proof", removed);
        } else {
            log("cleanup/prune refused", source, reasonCode, "no_loaded_duplicate_pointer_proof", 0);
        }
        return removed;
    }

    private static void log(String event, String source, String reasonCode, String proof, int removed) {
        CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                "{} source={} transponder=missing sableShip=missing route=missing station=missing dimension=all resultType={} reason={} proof={} removed={}",
                event,
                source,
                removed > 0 ? "APPLIED" : "REFUSED_OR_NOOP",
                reasonCode,
                proof,
                removed
        );
    }
}

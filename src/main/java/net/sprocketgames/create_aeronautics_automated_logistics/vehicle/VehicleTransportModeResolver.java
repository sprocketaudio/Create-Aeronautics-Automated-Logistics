package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;

public final class VehicleTransportModeResolver {
    private static final int TRAIN_SIGNATURE_SCAN_RADIUS = 12;
    private static final String SIMURAIL_MOD_ID = "simurail";

    private VehicleTransportModeResolver() {
    }

    public static TransportMode resolve(
            ServerLevel level,
            Optional<VehicleControllerRef> controllerRef,
            Optional<TransportMode> fallback
    ) {
        if (controllerRef.isEmpty()) {
            return fallback.orElse(TransportMode.DEFAULT);
        }

        VehicleControllerRef ref = controllerRef.get();
        if (ref.controllerType().equals(EntityVehicleController.TYPE)) {
            return TransportMode.AIRSHIP;
        }

        if (ref.controllerType().equals(SableSubLevelVehicleController.TYPE)) {
            Optional<TransportMode> sableMode = SableSubLevelVehicleController.resolve(level, ref)
                    .map(VehicleTransportModeResolver::resolveSableTransportMode);
            if (sableMode.isPresent()) {
                return sableMode.get();
            }
        }

        return fallback.orElse(TransportMode.DEFAULT);
    }

    private static TransportMode resolveSableTransportMode(SableSubLevelVehicleController controller) {
        if (controller.hasBlockFromNamespaceNearController(SIMURAIL_MOD_ID, TRAIN_SIGNATURE_SCAN_RADIUS)) {
            return TransportMode.TRAIN;
        }
        return TransportMode.AIRSHIP;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AdvancedTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.DriveModuleBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleHealth;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.DriveModuleLink;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;

public final class DriveModuleLinkService {
    private static final Map<UUID, Set<BlockPos>> MODULES_BY_CONTROLLER = new HashMap<>();

    private DriveModuleLinkService() {
    }

    public static Resolution resolve(ServerLevel level, BlockPos modulePos, Optional<UUID> linkedControllerId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(modulePos, "modulePos");
        Objects.requireNonNull(linkedControllerId, "linkedControllerId");

        Optional<SableSubLevelVehicleController> moduleVehicle = SableSubLevelVehicleController.resolveControllerBlock(
                level,
                modulePos,
                ModBlocks.DRIVE_MODULE.get()
        );
        if (moduleVehicle.isEmpty()) {
            return new Resolution(Optional.empty(), List.of(), List.of(), Optional.empty(), DriveModuleHealth.NO_ASSEMBLED_VEHICLE);
        }

        Optional<UUID> vehicleId = moduleVehicle.get().ref().vehicleId();
        if (vehicleId.isEmpty()) {
            return new Resolution(Optional.empty(), List.of(), List.of(), Optional.empty(), DriveModuleHealth.NO_ASSEMBLED_VEHICLE);
        }

        List<ShipTransponderBlockEntity> allControllers = new ArrayList<>();
        List<AdvancedTransponderBlockEntity> advancedControllers = new ArrayList<>();

        ShipTransponderRegistry.knownShips(level.dimension()).forEach(snapshot -> {
            if (snapshot.runtimeShipId().isEmpty() || !snapshot.runtimeShipId().get().equals(vehicleId.get())) {
                return;
            }
            if (!(level.getBlockEntity(snapshot.transponderPos()) instanceof ShipTransponderBlockEntity transponder)) {
                return;
            }
            transponder.refreshRuntimeShip(level);
            if (transponder.runtimeShipId().filter(vehicleId.get()::equals).isEmpty()) {
                return;
            }
            allControllers.add(transponder);
            if (transponder instanceof AdvancedTransponderBlockEntity advanced) {
                advancedControllers.add(advanced);
            }
        });

        Optional<AdvancedTransponderBlockEntity> linkedController = linkedControllerId
                .flatMap(controllerId -> advancedControllers.stream()
                        .filter(controller -> controller.transponderId().equals(controllerId))
                        .findFirst());

        DriveModuleHealth health;
        if (allControllers.size() > 1) {
            health = DriveModuleHealth.MULTIPLE_CONTROLLERS;
        } else if (advancedControllers.isEmpty()) {
            health = linkedControllerId.isPresent()
                    ? DriveModuleHealth.CONTROLLER_UNAVAILABLE
                    : DriveModuleHealth.NO_ADVANCED_CONTROLLER;
        } else if (advancedControllers.size() > 1) {
            health = DriveModuleHealth.MULTIPLE_CONTROLLERS;
        } else if (linkedControllerId.isPresent() && linkedController.isEmpty()) {
            health = DriveModuleHealth.WRONG_VEHICLE;
        } else {
            health = DriveModuleHealth.LINKED;
        }

        Optional<AdvancedTransponderBlockEntity> resolvedController =
                linkedController.isPresent() ? linkedController : advancedControllers.stream().findFirst();
        return new Resolution(Optional.of(vehicleId.get()), List.copyOf(allControllers), List.copyOf(advancedControllers), resolvedController, health);
    }

    public static void track(DriveModuleBlockEntity module) {
        if (module.getLevel() == null || module.getLevel().isClientSide) {
            return;
        }
        module.link().ifPresent(link -> MODULES_BY_CONTROLLER
                .computeIfAbsent(link.controllerTransponderId(), ignored -> new HashSet<>())
                .add(module.getBlockPos().immutable()));
    }

    public static void untrack(DriveModuleBlockEntity module) {
        MODULES_BY_CONTROLLER.values().forEach(positions -> positions.remove(module.getBlockPos()));
        MODULES_BY_CONTROLLER.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static List<DriveModuleBlockEntity> linkedModules(ServerLevel level, UUID controllerTransponderId) {
        Set<BlockPos> positions = MODULES_BY_CONTROLLER.get(controllerTransponderId);
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        List<DriveModuleBlockEntity> modules = new ArrayList<>();
        Set<BlockPos> stalePositions = new HashSet<>();
        for (BlockPos position : positions) {
            if (level.getBlockEntity(position) instanceof DriveModuleBlockEntity module
                    && module.link().map(DriveModuleLink::controllerTransponderId).filter(controllerTransponderId::equals).isPresent()
                    && module.health() == DriveModuleHealth.LINKED
                    && module.config().isPresent()) {
                modules.add(module);
            } else {
                stalePositions.add(position);
            }
        }
        positions.removeAll(stalePositions);
        if (positions.isEmpty()) {
            MODULES_BY_CONTROLLER.remove(controllerTransponderId);
        }
        return List.copyOf(modules);
    }

    public record Resolution(
            Optional<UUID> vehicleId,
            List<ShipTransponderBlockEntity> allControllers,
            List<AdvancedTransponderBlockEntity> advancedControllers,
            Optional<AdvancedTransponderBlockEntity> resolvedController,
            DriveModuleHealth health
    ) {
        public Resolution {
            Objects.requireNonNull(vehicleId, "vehicleId");
            allControllers = List.copyOf(Objects.requireNonNull(allControllers, "allControllers"));
            advancedControllers = List.copyOf(Objects.requireNonNull(advancedControllers, "advancedControllers"));
            Objects.requireNonNull(resolvedController, "resolvedController");
            Objects.requireNonNull(health, "health");
        }
    }
}

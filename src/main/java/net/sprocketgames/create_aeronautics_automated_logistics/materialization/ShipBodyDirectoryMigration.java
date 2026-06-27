package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;

/** Conservative one-time backfill for worlds created before the body directory existed. */
public final class ShipBodyDirectoryMigration {
    private static final int CURRENT_MIGRATION_VERSION = 1;
    private static final String TRANSPONDER_BLOCK_ENTITY_ID =
            "create_aeronautics_automated_logistics:ship_transponder";
    private static final String TRANSPONDER_ID = "transponderId";
    private static final String RUNTIME_SHIP_ID = "runtimeShipId";

    private ShipBodyDirectoryMigration() {
    }

    public static void migrateIfNeeded(MinecraftServer server) {
        ShipBodyDirectorySavedData directory = ShipBodyDirectorySavedData.get(server);
        if (directory.migrationVersion() >= CURRENT_MIGRATION_VERSION) {
            return;
        }

        SableStoredShipRepository.invalidate(server);
        Map<UUID, IdentityDirectorySavedData.PersistedShipIdentity> knownTransponders =
                IdentityDirectorySavedData.get(server).allShips().stream().collect(Collectors.toMap(
                        IdentityDirectorySavedData.PersistedShipIdentity::transponderId,
                        identity -> identity,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Map<UUID, List<StoredObservation>> observations = new HashMap<>();
        int rejected = 0;

        for (StoredBodyCandidate candidate : SableStoredShipRepository.allCandidates(server)) {
            if (!candidate.readable()) {
                continue;
            }
            for (CompoundTag blockEntity : storedBlockEntities(candidate.serializedData())) {
                Optional<StoredObservation> observation = readObservation(candidate, blockEntity);
                if (observation.isEmpty()) {
                    continue;
                }
                StoredObservation value = observation.get();
                IdentityDirectorySavedData.PersistedShipIdentity known = knownTransponders.get(value.transponderId());
                if (known == null || !known.dimension().equals(candidate.dimension())) {
                    rejected++;
                    CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                            "Ship body migration refused unowned stored observation: transponderId={} sableShipId={} dimension={} pointer={} reason={} action=retained_storage_no_directory_write",
                            value.transponderId(),
                            candidate.sableShipId(),
                            candidate.dimension().location(),
                            candidate.pointer(),
                            known == null ? "transponder_not_in_legacy_identity_directory" : "identity_dimension_mismatch"
                    );
                    continue;
                }
                observations.computeIfAbsent(value.transponderId(), ignored -> new ArrayList<>()).add(value);
            }
        }

        int migrated = 0;
        int ambiguous = 0;
        int conflicts = 0;
        for (Map.Entry<UUID, List<StoredObservation>> entry : observations.entrySet()) {
            UUID transponderId = entry.getKey();
            if (directory.byTransponder(transponderId).isPresent()) {
                continue;
            }
            List<StoredObservation> distinct = entry.getValue().stream()
                    .distinct()
                    .sorted(Comparator.comparing(observation -> observation.pointer().toString()))
                    .toList();
            Set<BodyControllerKey> bodyControllers = distinct.stream()
                    .map(StoredObservation::bodyControllerKey)
                    .collect(Collectors.toSet());
            if (bodyControllers.size() != 1) {
                conflicts++;
                CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                        "Ship body migration refused conflicting evidence: transponderId={} observations={} reason=multiple_body_or_controller_identities action=retained_storage_no_directory_write",
                        transponderId,
                        distinct.stream().map(StoredObservation::diagnostic).toList()
                );
                continue;
            }

            StoredObservation evidence = distinct.getFirst();
            boolean duplicatePointers = distinct.stream().map(StoredObservation::pointer).distinct().count() > 1;
            Optional<StoredBodyPointer> canonicalPointer = duplicatePointers
                    ? Optional.empty()
                    : Optional.of(evidence.pointer());
            ShipBodyDirectorySavedData.VerificationState state = duplicatePointers
                    ? ShipBodyDirectorySavedData.VerificationState.AMBIGUOUS
                    : ShipBodyDirectorySavedData.VerificationState.STORED_VERIFIED;
            if (ShipBodyDirectorySavedData.migrateStoredBody(
                    server,
                    evidence.transponderId(),
                    evidence.sableShipId(),
                    evidence.candidate().dimension(),
                    evidence.localControllerPos(),
                    canonicalPointer,
                    state,
                    server.overworld().getGameTime()
            )) {
                migrated++;
                if (duplicatePointers) {
                    ambiguous++;
                }
                CreateAeronauticsAutomatedLogistics.debugVehicle(
                        "Ship body migration recorded legacy identity: source=0.4.5_or_experimental_0.5 transponderId={} sableShipId={} dimension={} localControllerPos={} state={} pointer={} candidates={} proof=stored_transponder_runtime_ship_id_match action=metadata_only_no_storage_modified",
                        evidence.transponderId(),
                        evidence.sableShipId(),
                        evidence.candidate().dimension().location(),
                        evidence.localControllerPos(),
                        state,
                        canonicalPointer.map(StoredBodyPointer::selector).orElse("none"),
                        distinct.size()
                );
            }
        }

        directory.completeMigration(CURRENT_MIGRATION_VERSION);
        CreateAeronauticsAutomatedLogistics.LOGGER.info(
                "Ship body directory migration complete: migrated={}, ambiguous={}, conflicts={}, refused={}. No Sable storage was modified.",
                migrated,
                ambiguous,
                conflicts,
                rejected
        );
    }

    private static List<CompoundTag> storedBlockEntities(CompoundTag serializedBody) {
        if (!serializedBody.contains("plot", Tag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag plot = serializedBody.getCompound("plot");
        if (!plot.contains("chunks", Tag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag chunks = plot.getCompound("chunks");
        List<CompoundTag> result = new ArrayList<>();
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(chunkKey);
            if (!chunk.contains("block_entities", Tag.TAG_LIST)) {
                continue;
            }
            ListTag blockEntities = chunk.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntities.size(); i++) {
                result.add(blockEntities.getCompound(i));
            }
        }
        return result;
    }

    private static Optional<StoredObservation> readObservation(
            StoredBodyCandidate candidate,
            CompoundTag blockEntity
    ) {
        if (!TRANSPONDER_BLOCK_ENTITY_ID.equals(blockEntity.getString("id"))) {
            return Optional.empty();
        }
        if (!blockEntity.hasUUID(TRANSPONDER_ID)
                || !blockEntity.hasUUID(RUNTIME_SHIP_ID)
                || !blockEntity.contains("x", Tag.TAG_ANY_NUMERIC)
                || !blockEntity.contains("y", Tag.TAG_ANY_NUMERIC)
                || !blockEntity.contains("z", Tag.TAG_ANY_NUMERIC)) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Ship body migration refused incomplete stored transponder: sableShipId={} dimension={} pointer={} reason=missing_identity_or_position_fields action=retained_storage_no_directory_write",
                    candidate.sableShipId(),
                    candidate.dimension().location(),
                    candidate.pointer()
            );
            return Optional.empty();
        }
        UUID runtimeShipId = blockEntity.getUUID(RUNTIME_SHIP_ID);
        if (!candidate.sableShipId().equals(runtimeShipId)) {
            CreateAeronauticsAutomatedLogistics.debugVehicleWarn(
                    "Ship body migration refused mismatched stored transponder: transponderId={} savedRuntimeShipId={} candidateSableShipId={} dimension={} pointer={} reason=runtime_ship_id_mismatch action=retained_storage_no_directory_write",
                    blockEntity.getUUID(TRANSPONDER_ID),
                    runtimeShipId,
                    candidate.sableShipId(),
                    candidate.dimension().location(),
                    candidate.pointer()
            );
            return Optional.empty();
        }
        return Optional.of(new StoredObservation(
                blockEntity.getUUID(TRANSPONDER_ID),
                runtimeShipId,
                new BlockPos(blockEntity.getInt("x"), blockEntity.getInt("y"), blockEntity.getInt("z")),
                candidate.pointer(),
                candidate
        ));
    }

    private record StoredObservation(
            UUID transponderId,
            UUID sableShipId,
            BlockPos localControllerPos,
            StoredBodyPointer pointer,
            StoredBodyCandidate candidate
    ) {
        private BodyControllerKey bodyControllerKey() {
            return new BodyControllerKey(sableShipId, localControllerPos, candidate.dimension().location().toString());
        }

        private String diagnostic() {
            return sableShipId + "@" + localControllerPos + "/" + pointer.selector();
        }
    }

    private record BodyControllerKey(UUID sableShipId, BlockPos localControllerPos, String dimension) {
    }
}

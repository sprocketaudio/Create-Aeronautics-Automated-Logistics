package net.sprocketgames.create_aeronautics_automated_logistics.materialization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed discovery result. Only VERIFIED_SINGLE and VERIFIED_POINTER identify an automatic selection. */
public record StoredBodyLookupResult(
        Status status,
        List<StoredBodyCandidate> candidates,
        Optional<StoredBodyCandidate> selected,
        String reasonCode
) {
    public StoredBodyLookupResult {
        Objects.requireNonNull(status, "status");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        selected = Objects.requireNonNull(selected, "selected");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        if (selected.isPresent() && !candidates.contains(selected.get())) {
            throw new IllegalArgumentException("selected candidate must be present in candidates");
        }
        if (selected.isPresent() && !status.automaticallySelectable()) {
            throw new IllegalArgumentException("status " + status + " cannot select a stored body automatically");
        }
    }

    public static StoredBodyLookupResult verifiedSingle(StoredBodyCandidate candidate) {
        return new StoredBodyLookupResult(
                Status.VERIFIED_SINGLE,
                List.of(candidate),
                Optional.of(candidate),
                "single_readable_candidate"
        );
    }

    public static StoredBodyLookupResult verifiedPointer(
            List<StoredBodyCandidate> candidates,
            StoredBodyCandidate selected,
            String reasonCode
    ) {
        return new StoredBodyLookupResult(Status.VERIFIED_POINTER, candidates, Optional.of(selected), reasonCode);
    }

    public static StoredBodyLookupResult unresolved(Status status, List<StoredBodyCandidate> candidates, String reasonCode) {
        if (status.automaticallySelectable()) {
            throw new IllegalArgumentException("selectable status requires a selected candidate");
        }
        return new StoredBodyLookupResult(status, candidates, Optional.empty(), reasonCode);
    }

    public enum Status {
        VERIFIED_SINGLE(true),
        VERIFIED_POINTER(true),
        AMBIGUOUS(false),
        NOT_FOUND(false),
        READ_FAILED(false),
        NOT_READY(false);

        private final boolean automaticallySelectable;

        Status(boolean automaticallySelectable) {
            this.automaticallySelectable = automaticallySelectable;
        }

        public boolean automaticallySelectable() {
            return automaticallySelectable;
        }
    }
}

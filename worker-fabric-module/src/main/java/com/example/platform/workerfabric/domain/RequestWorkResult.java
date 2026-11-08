package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Idempotently persisted result of one logical RequestWork. */
public sealed interface RequestWorkResult
        permits RequestWorkResult.Granted,
                RequestWorkResult.NoWork,
                RequestWorkResult.Rejected,
                RequestWorkResult.ReprobeRequired,
                RequestWorkResult.CapacityUnavailable {

    RequestWorkId requestWorkId();

    default boolean granted() {
        return this instanceof Granted;
    }

    /** Same still-valid Task D grant is returned for an idempotent replay. */
    record Granted(RequestWorkId requestWorkId, AssignmentGrantReference grant)
            implements RequestWorkResult {

        public Granted {
            Objects.requireNonNull(requestWorkId, "requestWorkId");
            Objects.requireNonNull(grant, "grant");
            if (!requestWorkId.equals(grant.requestWorkId())) {
                throw new IllegalArgumentException(
                        "assignment grant must bind the resolving RequestWorkId");
            }
        }
    }

    /** Deterministic terminal result: no legal pending task existed for this request. */
    record NoWork(RequestWorkId requestWorkId) implements RequestWorkResult {

        public NoWork {
            Objects.requireNonNull(requestWorkId, "requestWorkId");
        }
    }

    /** Deterministic typed terminal rejection. */
    record Rejected(RequestWorkId requestWorkId, RequestWorkFailureReason reason)
            implements RequestWorkResult {

        public Rejected {
            Objects.requireNonNull(requestWorkId, "requestWorkId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Non-terminal capacity wait: no resolution is persisted and the same RequestWork may retry. */
    record CapacityUnavailable(RequestWorkId requestWorkId, RequestWorkFailureReason reason)
            implements RequestWorkResult {

        public CapacityUnavailable {
            Objects.requireNonNull(requestWorkId, "requestWorkId");
            Objects.requireNonNull(reason, "reason");
            if (reason != RequestWorkFailureReason.RESERVATION_CONFLICT) {
                throw new IllegalArgumentException("capacity wait requires reservation conflict");
            }
        }
    }

    /** Fail-closed response requiring fresh evidence before a new logical request. */
    record ReprobeRequired(RequestWorkId requestWorkId, RequestWorkFailureReason reason)
            implements RequestWorkResult {

        public ReprobeRequired {
            Objects.requireNonNull(requestWorkId, "requestWorkId");
            Objects.requireNonNull(reason, "reason");
            if (reason != RequestWorkFailureReason.STALE_HOST_RESOURCE_SNAPSHOT
                    && reason != RequestWorkFailureReason.HOST_RESOURCE_SNAPSHOT_MISMATCH
                    && reason != RequestWorkFailureReason.UNKNOWN_RUNTIME_ELIGIBILITY) {
                throw new IllegalArgumentException(
                        "reprobe result requires a snapshot/runtime evidence reason");
            }
        }
    }
}

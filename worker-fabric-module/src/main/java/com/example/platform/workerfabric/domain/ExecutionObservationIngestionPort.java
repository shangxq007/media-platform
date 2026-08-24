package com.example.platform.workerfabric.domain;

/**
 * Idempotent evidence-ingestion boundary.
 *
 * <p>Implementations deduplicate by {@link ObservationId}. Current-generation evidence may update
 * an evidence projection. Stale-generation evidence may be retained for audit, but cannot invoke
 * or substitute for the completion authority transition.
 */
@FunctionalInterface
public interface ExecutionObservationIngestionPort {

    IngestionResult ingest(ExecutionObservation observation);

    enum IngestionResult {
        RECORDED_CURRENT_EVIDENCE,
        RECORDED_STALE_EVIDENCE,
        DUPLICATE_NOOP
    }
}

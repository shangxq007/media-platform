package com.example.platform.extension.runtime;

import java.time.Instant;

/**
 * Execution progress OBSERVATION (frozen PRV2-ADR-012, AR-PRV2-14).
 *
 * <p>Progress is an observation record, not a mutable domain lifecycle authority.
 * The runtime may report execution progress; it must NEVER mutate RenderJob,
 * DeliveryJob, Workflow or Publication lifecycle state.</p>
 *
 * @param phase           progress phase label (e.g. "STARTING", "RUNNING", "FINALIZING")
 * @param completedUnits  completed work units (>= 0, 0 when unknown)
 * @param totalUnits      total work units (>= 0, 0 when unknown)
 * @param message         optional human-readable message
 * @param observedAt      observation timestamp
 */
public record PluginExecutionProgress(
        String phase,
        long completedUnits,
        long totalUnits,
        String message,
        Instant observedAt) {

    public PluginExecutionProgress {
        if (phase == null || phase.isBlank()) {
            throw new IllegalArgumentException("phase must not be blank");
        }
        if (completedUnits < 0 || totalUnits < 0) {
            throw new IllegalArgumentException("units must not be negative");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must not be null");
        }
    }
}

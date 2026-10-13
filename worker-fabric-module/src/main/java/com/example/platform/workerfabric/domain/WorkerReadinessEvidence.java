package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * WORKER_READINESS_EVIDENCE_V1 (P1-1A.4): the authoritative, incarnation-bound readiness record for
 * one (physical host, host incarnation, worker runtime, runtime incarnation).
 *
 * <p>Environment readiness and sandbox readiness are two <b>independent</b> signals (owner ruling,
 * option (i)): one never endorses the other. The record is valid only while it is not expired and its
 * identities match the current incarnation; callers must fail closed otherwise and must never treat
 * {@code UNKNOWN} as available.
 *
 * @param physicalHostId               physical host identity
 * @param physicalHostIncarnationId    physical host incarnation this evidence was observed for
 * @param workerRuntimeId              worker runtime identity
 * @param workerRuntimeIncarnationId   worker runtime incarnation this evidence was observed for
 * @param environmentReadiness         independent environment readiness observation
 * @param sandboxReadiness             independent sandbox readiness observation
 * @param snapshotGeneration           the host snapshot generation this evidence was observed with
 * @param capturedAt                   observation time
 * @param validUntil                   evidence expiry (exclusive)
 */
public record WorkerReadinessEvidence(
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        RuntimeEnvironmentAvailability environmentReadiness,
        SandboxRuntimeAvailability sandboxReadiness,
        HostResourceSnapshotGeneration snapshotGeneration,
        Instant capturedAt,
        Instant validUntil) {

    public WorkerReadinessEvidence {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(environmentReadiness, "environmentReadiness");
        Objects.requireNonNull(sandboxReadiness, "sandboxReadiness");
        Objects.requireNonNull(snapshotGeneration, "snapshotGeneration");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(validUntil, "validUntil");
        if (!validUntil.isAfter(capturedAt)) {
            throw new IllegalArgumentException("readiness evidence must expire after it is captured");
        }
    }

    /** True while this evidence has not expired at {@code now} (expiry is exclusive). */
    public boolean isFresh(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.isBefore(validUntil);
    }
}

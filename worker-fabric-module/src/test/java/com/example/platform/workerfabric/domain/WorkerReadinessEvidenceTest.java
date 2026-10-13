package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** P1-1A.4 Gate A: readiness evidence is incarnation-bound and expires fail-closed. */
class WorkerReadinessEvidenceTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-12T00:00:00Z");

    private static WorkerReadinessEvidence evidence(
            RuntimeEnvironmentAvailability environment,
            SandboxRuntimeAvailability sandbox,
            Instant validUntil) {
        return new WorkerReadinessEvidence(
                PhysicalHostId.of("local"),
                PhysicalHostIncarnationId.of("host-inc"),
                WorkerRuntimeId.of("local-ffmpeg"),
                WorkerRuntimeIncarnationId.of("local-ffmpeg-v1"),
                environment,
                sandbox,
                HostResourceSnapshotGeneration.first(),
                CAPTURED_AT,
                validUntil);
    }

    @Test
    void carriesIndependentEnvironmentAndSandboxSignals() {
        WorkerReadinessEvidence record = evidence(
                RuntimeEnvironmentAvailability.AVAILABLE,
                SandboxRuntimeAvailability.UNAVAILABLE,
                CAPTURED_AT.plus(Duration.ofSeconds(30)));

        // Neither signal can stand in for the other; they are stored and read separately.
        assertThat(record.environmentReadiness())
                .isEqualTo(RuntimeEnvironmentAvailability.AVAILABLE);
        assertThat(record.sandboxReadiness())
                .isEqualTo(SandboxRuntimeAvailability.UNAVAILABLE);
    }

    @Test
    void isFreshOnlyBeforeItsExclusiveExpiry() {
        WorkerReadinessEvidence record = evidence(
                RuntimeEnvironmentAvailability.AVAILABLE,
                SandboxRuntimeAvailability.AVAILABLE,
                CAPTURED_AT.plus(Duration.ofSeconds(30)));

        assertThat(record.isFresh(CAPTURED_AT)).isTrue();
        assertThat(record.isFresh(CAPTURED_AT.plusSeconds(29))).isTrue();
        // Expiry is exclusive: exactly at validUntil the evidence is no longer fresh.
        assertThat(record.isFresh(CAPTURED_AT.plusSeconds(30))).isFalse();
        assertThat(record.isFresh(CAPTURED_AT.plusSeconds(31))).isFalse();
    }

    @Test
    void rejectsEvidenceThatDoesNotExpireAfterItIsCaptured() {
        assertThatThrownBy(() -> evidence(
                        RuntimeEnvironmentAvailability.AVAILABLE,
                        SandboxRuntimeAvailability.AVAILABLE,
                        CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expire after");
    }

    @Test
    void rejectsMissingIdentitiesOrSignals() {
        assertThatThrownBy(() -> new WorkerReadinessEvidence(
                        null,
                        PhysicalHostIncarnationId.of("host-inc"),
                        WorkerRuntimeId.of("local-ffmpeg"),
                        WorkerRuntimeIncarnationId.of("local-ffmpeg-v1"),
                        RuntimeEnvironmentAvailability.AVAILABLE,
                        SandboxRuntimeAvailability.AVAILABLE,
                        HostResourceSnapshotGeneration.first(),
                        CAPTURED_AT,
                        CAPTURED_AT.plusSeconds(30)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkerReadinessEvidence(
                        PhysicalHostId.of("local"),
                        PhysicalHostIncarnationId.of("host-inc"),
                        WorkerRuntimeId.of("local-ffmpeg"),
                        WorkerRuntimeIncarnationId.of("local-ffmpeg-v1"),
                        null,
                        SandboxRuntimeAvailability.AVAILABLE,
                        HostResourceSnapshotGeneration.first(),
                        CAPTURED_AT,
                        CAPTURED_AT.plusSeconds(30)))
                .isInstanceOf(NullPointerException.class);
    }
}

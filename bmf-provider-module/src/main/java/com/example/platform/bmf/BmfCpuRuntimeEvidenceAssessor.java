package com.example.platform.bmf;

import com.example.platform.workerfabric.domain.RuntimeDependencyFingerprint;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatchResult;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatcher;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure BMF CPU assessment of canonical runtime dependency evidence. */
public final class BmfCpuRuntimeEvidenceAssessor {

    private BmfCpuRuntimeEvidenceAssessor() {}

    public static BmfCpuRuntimeDependencyAssessment assess(
            WorkerRuntimeId workerRuntimeId,
            Optional<RuntimeDependencyObservation> observation,
            Optional<RuntimeDependencyFingerprint> observedFingerprint,
            Instant assessedAt) {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(observedFingerprint, "observedFingerprint");
        Objects.requireNonNull(assessedAt, "assessedAt");

        RuntimeDependencyMatchResult dependencyMatchResult = RuntimeDependencyMatcher.match(
                BmfCpuProvider.IMPLEMENTATION_ID,
                workerRuntimeId,
                Optional.empty(),
                BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS,
                observation,
                assessedAt);

        List<BmfCpuRuntimeEvidenceIssue> issues;
        if (observedFingerprint.isEmpty()) {
            issues = List.of(BmfCpuRuntimeEvidenceIssue.MISSING_FINGERPRINT);
        } else if (!observedFingerprint.orElseThrow()
                .equals(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT)) {
            issues = List.of(BmfCpuRuntimeEvidenceIssue.FINGERPRINT_MISMATCH);
        } else {
            issues = List.of();
        }

        return new BmfCpuRuntimeDependencyAssessment(
                dependencyMatchResult,
                BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT,
                observedFingerprint,
                issues);
    }
}

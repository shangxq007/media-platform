package com.example.platform.bmf;

import com.example.platform.workerfabric.domain.RuntimeDependencyFingerprint;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatchResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-local diagnostic composition of H1 dependency and fingerprint evidence. */
public record BmfCpuRuntimeDependencyAssessment(
        RuntimeDependencyMatchResult dependencyMatchResult,
        RuntimeDependencyFingerprint expectedFingerprint,
        Optional<RuntimeDependencyFingerprint> observedFingerprint,
        List<BmfCpuRuntimeEvidenceIssue> issues) {

    public BmfCpuRuntimeDependencyAssessment {
        Objects.requireNonNull(dependencyMatchResult, "dependencyMatchResult");
        Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");
        Objects.requireNonNull(observedFingerprint, "observedFingerprint");
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean matchesRuntimeEvidence() {
        return dependencyMatchResult.canMatch() && issues.isEmpty();
    }
}

package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import java.util.Objects;

/** Runtime reuse lookup followed by mandatory Artifact authority validation. */
public final class ArtifactReuseResolver {

    private final ArtifactReuseIndexPort index;
    private final ArtifactQueryService artifacts;

    public ArtifactReuseResolver(ArtifactReuseIndexPort index, ArtifactQueryService artifacts) {
        this.index = Objects.requireNonNull(index, "index");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    public ValidatedReuseDecision resolve(
            String tenantId,
            ExecutionReuseKey executionReuseKey,
            Cacheability cacheability) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(executionReuseKey, "executionReuseKey");
        Objects.requireNonNull(cacheability, "cacheability");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (cacheability != Cacheability.CACHEABLE) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.NOT_CACHEABLE,
                    "execution is not proven fully pinned and deterministically cacheable");
        }
        ReusableArtifactRecord record = index.lookup(tenantId, executionReuseKey).orElse(null);
        if (record == null) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.MISS, "reuse index miss");
        }
        if (!tenantId.equals(record.tenantId())) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.UNAUTHORIZED,
                    "reuse index record tenant does not match request tenant");
        }
        Artifact artifact = artifacts.getArtifact(tenantId, record.artifactPin().artifactId())
                .orElse(null);
        if (artifact == null) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.STALE,
                    "Artifact authority has no tenant-authorized Artifact");
        }
        if (!artifact.contentDigest().matches(record.artifactPin().contentDigest())) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.CORRUPT,
                    "Artifact authority ContentDigest does not match reuse index pin");
        }
        if (artifact.state() == ArtifactState.QUARANTINED
                || artifact.state() == ArtifactState.FAILED) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.CORRUPT,
                    "Artifact is quarantined or failed");
        }
        if (artifact.state() != ArtifactState.AVAILABLE) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.STALE,
                    "Artifact is not AVAILABLE");
        }
        return ValidatedReuseDecision.hit(record);
    }
}

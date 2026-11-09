package com.example.platform.workerfabric.reuse;

import com.example.platform.execution.taskgraph.Cacheability;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import java.util.Objects;

/** Runtime reuse lookup followed by mandatory Artifact authority validation. */
public final class ArtifactReuseResolver {

    private final ArtifactReuseIndexPort index;
    private final ArtifactQueryService artifacts;
    private final com.example.platform.artifact.app.ArtifactApplicationQuery scopedArtifacts;

    public ArtifactReuseResolver(ArtifactReuseIndexPort index, ArtifactQueryService artifacts) {
        this(index, artifacts, null);
    }
    public ArtifactReuseResolver(ArtifactReuseIndexPort index, ArtifactQueryService artifacts,
            com.example.platform.artifact.app.ArtifactApplicationQuery scopedArtifacts) {
        this.scopedArtifacts = scopedArtifacts;
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
        ReusableArtifactRecord record;
        try {
            record = index.lookup(tenantId, executionReuseKey).orElse(null);
        } catch (RuntimeException lookupFailure) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.MISS,
                    "reuse index lookup failed closed");
        }
        if (record == null) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.MISS, "reuse index miss");
        }
        if (!tenantId.equals(record.tenantId())) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.UNAUTHORIZED,
                    "reuse index record tenant does not match request tenant");
        }
        Artifact artifact;
        try {
            artifact = artifacts.getArtifact(tenantId, record.artifactPin().artifactId())
                    .orElse(null);
        } catch (RuntimeException authorityFailure) {
            return ValidatedReuseDecision.reject(
                    ValidatedReuseDecision.Outcome.STALE,
                    "Artifact authority validation failed closed");
        }
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

    /** Validates an intermediate as an authorized input, never as the current execution's output. */
    public ValidatedReuseDecision resolveIntermediate(String tenantId, String projectId,
            ExecutionReuseKey key, Cacheability cacheability,
            com.example.platform.execution.taskgraph.ExecutableTaskId expectedTask) {
        var decision = resolve(tenantId, key, cacheability);
        if (decision.outcome() != ValidatedReuseDecision.Outcome.VALIDATED_HIT) return decision;
        var record = decision.record().orElseThrow();
        if (scopedArtifacts == null || projectId == null || !expectedTask.equals(record.executableTaskId()))
            return ValidatedReuseDecision.reject(ValidatedReuseDecision.Outcome.UNAUTHORIZED, "intermediate input scope or computation proof absent");
        try {
            var artifact = scopedArtifacts.findProjectArtifact(
                    new com.example.platform.artifact.app.ArtifactProjectScope(tenantId, projectId), record.artifactPin().artifactId());
            if (artifact.isEmpty() || artifact.orElseThrow().state() != ArtifactState.AVAILABLE
                    || artifact.orElseThrow().artifactKind() != com.example.platform.artifact.domain.ArtifactKind.DERIVED_MEDIA
                    || !artifact.orElseThrow().contentDigest().matches(record.artifactPin().contentDigest()))
                return ValidatedReuseDecision.reject(ValidatedReuseDecision.Outcome.UNAUTHORIZED, "no committed derived input in execution project");
            return decision;
        } catch (RuntimeException unavailable) {
            return ValidatedReuseDecision.reject(ValidatedReuseDecision.Outcome.MISS, "intermediate input authority unavailable; execute normally");
        }
    }

    /** Removes only invalid index metadata discovered by an authoritative validation. */
    public boolean evictInvalid(
            String tenantId,
            ExecutionReuseKey executionReuseKey,
            ValidatedReuseDecision decision) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(executionReuseKey, "executionReuseKey");
        Objects.requireNonNull(decision, "decision");
        if (decision.outcome() != ValidatedReuseDecision.Outcome.STALE
                && decision.outcome() != ValidatedReuseDecision.Outcome.CORRUPT) {
            throw new IllegalArgumentException(
                    "only stale or corrupt reuse metadata may be evicted by validation");
        }
        try {
            return index.evict(tenantId, executionReuseKey);
        } catch (RuntimeException evictionFailure) {
            return false;
        }
    }
}

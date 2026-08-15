package com.example.platform.shared.version;

import java.util.Objects;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-4): execution version
 * provenance — a durable semantic/reproducibility record of what was resolved
 * and executed.
 *
 * <p>Composable TYPED optional sections (never one nullable god-object): not
 * every execution has every dimension — local FFmpeg has no model section,
 * cloud AI has provider+model, pure semantic operations have no worker runtime.
 * No fake placeholder ids.
 *
 * <p>EXECUTION_IS_PINNED_TO_RESOLVED_VERSIONS_V1: after REQUEST -> RESOLVE ->
 * PLAN, execution must not perform another dynamic "latest" lookup; the plan
 * pins the exact candidates recorded here.
 *
 * <p>ExecutionProvenance is NOT an OpenTelemetry trace (runtime correlation);
 * trace ids may cross-reference (EXECUTION_TRACE_VS_PROVENANCE).
 */
public record ExecutionProvenance(
        // resolved platform/application release context
        ReleaseVersion platformReleaseVersion,
        BuildIdentity buildIdentity,
        ReleaseChannel releaseChannel,
        // resolved capability/implementation context
        String capabilityId,
        String capabilityContractVersion,
        String capabilityImplementationId,
        String capabilityImplementationVersion,
        String pluginId,
        String pluginVersion,
        // provider/runtime context
        String providerId,
        String workerProtocolVersion,
        String workerRuntimeVersion,
        // AI model context (absent when not applicable)
        String modelId,
        String modelVersion,
        // rollout context
        RolloutPolicy rolloutPolicy,
        String rolloutCohort,
        // inputs/outputs
        String configurationDigest,
        String inputRevisionId,
        String inputArtifactId,
        String inputContentDigest,
        String outputRevisionId,
        String outputArtifactId,
        String outputContentDigest,
        // correlation
        String traceId) {

    public ExecutionProvenance {
        if (traceId != null && traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (capabilityId != null && (capabilityImplementationId == null)) {
            // capability without implementation is allowed for semantic-only ops
        }
        Objects.requireNonNull(platformReleaseVersion, "platformReleaseVersion");
        Objects.requireNonNull(buildIdentity, "buildIdentity");
    }

    /** Build identity is independent from release version and never a
     *  compatibility authority (VCG-1E). */
    public record BuildIdentity(String gitSha, String buildId, String imageDigest) {
        public BuildIdentity {
            Objects.requireNonNull(gitSha, "gitSha");
        }

        public static BuildIdentity of(String gitSha) {
            return new BuildIdentity(gitSha, null, null);
        }
    }
}

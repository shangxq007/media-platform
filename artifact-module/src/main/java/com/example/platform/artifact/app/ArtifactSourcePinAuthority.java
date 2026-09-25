package com.example.platform.artifact.app;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/**
 * Canonical Artifact-authority resolution of an immutable source pin
 * (ARTIFACT_SOURCE_PIN_AUTHORITY_V1).
 *
 * <p>Artifact identity, tenant/project scope, content digest and lifecycle are Artifact-owned
 * facts. This port is the single place where "this pinned source is usable" is decided, so
 * consumers never re-derive Artifact semantics from partial data.
 *
 * <p>Resolution is scope-bound by contract: it requires an explicit, non-blank tenant and
 * project. There is no ambient TenantContext fallback, no storage lookup and no legacy Media
 * lookup. The verdict is a closed outcome, not a boolean, so consumers can report the exact
 * reason without inspecting Artifact internals.
 */
public interface ArtifactSourcePinAuthority {

    /** Closed resolution verdict. */
    enum Outcome {
        /** Pin identity exists, is inside the requested scope, is usable and matches the pin. */
        RESOLVED,
        /** No Artifact with this identity exists inside the requested tenant. */
        UNKNOWN_ARTIFACT,
        /** The Artifact exists in the tenant but belongs to a different project. */
        OUT_OF_SCOPE,
        /** The Artifact lifecycle status is not usable (tombstoned/failed/quarantined). */
        NOT_USABLE,
        /** The recorded Artifact content digest differs from the pinned digest. */
        PIN_MISMATCH
    }

    /**
     * Immutable resolution result.
     *
     * @param outcome        verdict
     * @param artifactId     requested pin identity
     * @param tenantId       requested tenant scope
     * @param projectId      requested project scope
     * @param pinnedDigest   canonical digest carried by the pin
     * @param artifactDigest canonical digest recorded by the Artifact authority, or {@code null}
     *                       when the Artifact is unknown
     */
    record PinResolution(
            Outcome outcome,
            ArtifactId artifactId,
            String tenantId,
            String projectId,
            String pinnedDigest,
            String artifactDigest) {

        public PinResolution {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(pinnedDigest, "pinnedDigest");
        }

        public boolean resolved() {
            return outcome == Outcome.RESOLVED;
        }
    }

    /**
     * Resolves one immutable source pin against the Artifact authority.
     *
     * @throws IllegalArgumentException when the tenant or project scope is missing/blank
     * @throws NullPointerException     when the Artifact identity or pinned digest is absent
     */
    PinResolution resolvePin(
            String tenantId, String projectId, ArtifactId artifactId, ContentDigest pinnedDigest);
}

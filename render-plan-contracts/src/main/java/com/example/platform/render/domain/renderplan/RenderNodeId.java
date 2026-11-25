package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Deterministic, revision-context-free render node identity (C6/C22).
 *
 * <p>Formula — canonical string:
 * {@code "<KIND>|<componentPath>|<operationKey>|<requirementsFingerprint>"} where
 * {@code requirementsFingerprint} is the SHA-256 hex over the canonical encoding of
 * the node's (artifactReferences + capabilityRequirements + outputRequirements).
 *
 * <p>Same semantic inputs -> same id. Node identity is REVISION-CONTEXT-FREE:
 * plan-level revision context lives in {@link RenderPlanId}/{@link RenderPlanFingerprint},
 * preserving C22 stability. NO plan id, NO revision id, NO provider/worker/device/
 * price/timestamp/availability in node identity.
 *
 * <p>Implements {@link Comparable} by value; the natural order equals the canonical
 * string, satisfying the graph kernel's determinism (node natural order = toString).
 */
public record RenderNodeId(String value) implements Comparable<RenderNodeId> {

    public RenderNodeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RenderNodeId must not be blank");
        }
    }

    /**
     * Factory: builds the deterministic node id.
     *
     * @param kind      node kind (canonical name used)
     * @param path      component path (canonical toString used)
     * @param operationKey bounded semantic op key
     * @param reqFp     requirements fingerprint (SHA-256 hex)
     */
    public static RenderNodeId of(
            RenderNodeKind kind,
            RenderComponentPath path,
            String operationKey,
            String reqFp) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(operationKey, "operationKey");
        Objects.requireNonNull(reqFp, "reqFp");
        if (operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey must not be blank");
        }
        return new RenderNodeId(kind.canonicalName() + "|" + path + "|" + operationKey + "|" + reqFp);
    }

    @Override
    public int compareTo(RenderNodeId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

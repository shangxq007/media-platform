package com.example.platform.execution.binding;

import java.util.Objects;

/**
 * Stable, payload-free reference to one durable bound-graph record.
 *
 * <p>This is what a durable workflow may carry: identities plus digests only — never
 * the physical plan, never the candidate declarations, never any media payload.
 *
 * @param tenantId                          owning tenant
 * @param renderJobId                       owning render job (business identity)
 * @param planRef                           storage reference of the persisted inputs
 * @param planDigest                        digest of the persisted physical plan bytes
 * @param expectedExecutableTaskGraphDigest digest the re-derived binding must reproduce
 */
public record BoundGraphReference(
        String tenantId,
        String renderJobId,
        String planRef,
        String planDigest,
        String expectedExecutableTaskGraphDigest) {

    public BoundGraphReference {
        tenantId = requireIdentity(tenantId, "tenantId");
        renderJobId = requireIdentity(renderJobId, "renderJobId");
        planRef = requireIdentity(planRef, "planRef");
        planDigest = requireCanonicalDigest(planDigest, "planDigest");
        expectedExecutableTaskGraphDigest = requireCanonicalDigest(
                expectedExecutableTaskGraphDigest, "expectedExecutableTaskGraphDigest");
    }

    private static String requireIdentity(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be a non-blank canonical value");
        }
        return value;
    }

    /** Digests are lowercase SHA-256 hex; a tampered or truncated digest fails closed here. */
    private static String requireCanonicalDigest(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
        return value;
    }
}

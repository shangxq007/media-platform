package com.example.platform.workflow.temporal.mediatask;

import java.util.List;
import java.util.Objects;

/**
 * Stable, payload-safe summary of a prepared bound graph.
 *
 * <p>This is what crosses the Temporal boundary. It deliberately contains only identities and
 * canonical digests: the typed graph and the encoded inputs stay inside the worker activity, so the
 * frozen "stable references only" payload rule is preserved.
 */
public record PreparedTaskRef(
        String tenantId,
        String renderJobId,
        String planRef,
        String planDigest,
        String expectedExecutableTaskGraphDigest,
        List<String> executableTaskIds) {

    public PreparedTaskRef {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(renderJobId, "renderJobId");
        Objects.requireNonNull(planRef, "planRef");
        Objects.requireNonNull(planDigest, "planDigest");
        Objects.requireNonNull(expectedExecutableTaskGraphDigest, "expectedExecutableTaskGraphDigest");
        executableTaskIds = executableTaskIds == null ? List.of() : List.copyOf(executableTaskIds);
    }
}

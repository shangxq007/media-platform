package com.example.platform.shared.authorization;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded, typed context accompanying an {@link AuthorizationRequest}.
 *
 * <p>Deliberately NOT a free-form JSON bag. It carries only the structured signals
 * the canonical authorization decision may legitimately consider: the request/source
 * origin and an optional entitlement/feature snapshot that was ALREADY evaluated by
 * the separate entitlement/feature path. Context never grants authority on its own —
 * it is advisory input to an otherwise security-agnostic decision.</p>
 *
 * @param requestSource  where the call originated (e.g. "web", "api-key", "system", "graphql")
 * @param workspaceId    optional workspace scope for the decision
 * @param additionalReadOnlySignals read-only advisory signals (immutable, never authority)
 */
public record AuthorizationContext(
        String requestSource,
        String workspaceId,
        Map<String, String> additionalReadOnlySignals) {

    public AuthorizationContext {
        additionalReadOnlySignals = additionalReadOnlySignals == null
                ? Map.of()
                : Map.copyOf(additionalReadOnlySignals);
    }

    public AuthorizationContext(String requestSource) {
        this(requestSource, null, Map.of());
    }
}

package com.example.platform.shared.authorization;

import java.util.Objects;

/**
 * The complete, immutable inputs to an authorization decision.
 *
 * <p>Composed of a {@link CanonicalActor} (who), an {@link AuthorizationAction}
 * (what), an {@link AuthorizableResourceRef} (on what), and an
 * {@link AuthorizationContext} (advisory signals). This is the sole input to
 * {@link AuthorizationDecisionPort#decide}.</p>
 */
public record AuthorizationRequest(
        CanonicalActor actor,
        AuthorizationAction action,
        AuthorizableResourceRef resource,
        AuthorizationContext context) {

    public AuthorizationRequest {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(context, "context must not be null");
    }
}

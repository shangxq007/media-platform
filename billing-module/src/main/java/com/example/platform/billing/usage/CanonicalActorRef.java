package com.example.platform.billing.usage;

import com.example.platform.shared.authorization.CanonicalActor;

import java.util.Objects;

/**
 * Stable, bounded actor snapshot for usage attribution.
 *
 * <p>Mirrors the shared {@link CanonicalActor} semantics (actorId + actorType) but is a
 * deliberately minimal, persistence-safe copy. It imports NO SecurityContext, JWT, OAuth
 * principal, API key, or credentials — only the stable {@code actorId} and the
 * {@code actorType} name (USER / API_KEY_PRINCIPAL / SYSTEM).</p>
 *
 * @param actorId   stable subject identifier
 * @param actorType actor type name (one of USER, API_KEY_PRINCIPAL, SYSTEM)
 */
public record CanonicalActorRef(String actorId, String actorType) {

    public CanonicalActorRef {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
    }

    /**
     * Copies only the stable fields from a shared {@link CanonicalActor}.
     *
     * @param actor the shared canonical actor
     * @return a bounded snapshot reference
     */
    public static CanonicalActorRef from(CanonicalActor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        return new CanonicalActorRef(actor.actorId(), actor.actorType().name());
    }
}

package com.example.platform.shared.authorization;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * The normalized, authorization-model-agnostic representation of the caller.
 *
 * <p>Produced by a {@link CanonicalActorResolver} from whatever fragmented source
 * exists (JWT request attributes, Spring Security context, API key MDC, explicit
 * system context). All downstream authorization decisions consume this canonical
 * form — business services never read {@code jwt.subject} or the SecurityContext
 * directly as an authority (AR-AUTH-009).</p>
 *
 * @param actorId   stable subject identifier (never null after resolution)
 * @param actorType the frozen actor type
 * @param tenantId  the tenant the actor is operating within (may be null only for
 *                  SYSTEM actors operating outside a tenant scope)
 * @param roles     role keys assigned to the actor in this context (immutable, never null)
 * @param authSource provenance tag describing where the actor was resolved from
 *                  (e.g. "jwt", "api-key", "oauth2", "system") — informational and
 *                  for audit, never an authority input
 */
public record CanonicalActor(
        String actorId,
        ActorType actorType,
        String tenantId,
        Set<String> roles,
        String authSource) {

    public CanonicalActor {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * Convenience factory for the common authenticated-user case.
     */
    public static CanonicalActor user(String actorId, String tenantId, Set<String> roles, String authSource) {
        return new CanonicalActor(actorId, ActorType.USER, tenantId, roles, authSource);
    }

    /**
     * Convenience factory for an API-key principal.
     */
    public static CanonicalActor apiKey(String actorId, String tenantId, Set<String> roles, String authSource) {
        return new CanonicalActor(actorId, ActorType.API_KEY_PRINCIPAL, tenantId, roles, authSource);
    }

    /**
     * Convenience factory for an explicit system context.
     */
    public static CanonicalActor system(String actorId, String tenantId) {
        return new CanonicalActor(actorId, ActorType.SYSTEM, tenantId, Set.of(), "system");
    }

    public boolean isSystem() {
        return actorType == ActorType.SYSTEM;
    }
}

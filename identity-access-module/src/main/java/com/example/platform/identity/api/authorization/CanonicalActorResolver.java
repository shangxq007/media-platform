package com.example.platform.identity.api.authorization;

import com.example.platform.shared.authorization.*;

import java.util.Optional;

/**
 * Port that resolves the current {@link CanonicalActor} from the ambient request
 * context (HTTP request attributes, SecurityContext, API-key MDC, system context).
 *
 * <p>Identity publishes this contract; consumers do not depend on resolver implementations.</p>
 *
 * <p>Resolvers MUST NOT treat "no actor" as SYSTEM. When no authenticated principal
 * is present they return {@link Optional#empty()}, letting callers distinguish an
 * unauthenticated/dev request from an explicit SYSTEM context.</p>
 */
@FunctionalInterface
public interface CanonicalActorResolver {

    /**
     * Resolve the current actor, or {@link Optional#empty()} if no authenticated
     * principal is present in the current context.
     */
    Optional<CanonicalActor> resolveCurrentActor();
}

package com.example.platform.shared.authorization;

import java.util.Optional;

/**
 * Port that resolves the current {@link CanonicalActor} from the ambient request
 * context (HTTP request attributes, SecurityContext, API-key MDC, system context).
 *
 * <p>Implementations live in the infrastructure/identity layer (request-attribute
 * resolver, Spring Security resolver, API-key resolver, system resolver). The
 * interface resides in shared-kernel so any module — including workflow-module,
 * which cannot depend on identity-access — can consume it.</p>
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

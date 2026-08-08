package com.example.platform.shared.authorization;

/**
 * The only actor types recognized by the canonical authorization model.
 *
 * <p>This vocabulary is frozen: USER, API_KEY_PRINCIPAL, SYSTEM. No other actor
 * types may be introduced (see APPD-CHV1 scope). A {@code SYSTEM} actor is never
 * implied by the absence of an actor — it must be produced explicitly by a system
 * resolver operating within an explicit system context.</p>
 */
public enum ActorType {

    /**
     * An authenticated human user (JWT or OAuth2 subject).
     */
    USER,

    /**
     * An authenticated principal resolved from an API key.
     */
    API_KEY_PRINCIPAL,

    /**
     * An internal platform/system context (scheduler, worker, dispatcher, admin).
     * <p>SYSTEM is NOT a universal implicit allow — it requires an explicit
     * system action/resource policy, else it is denied.</p>
     */
    SYSTEM
}

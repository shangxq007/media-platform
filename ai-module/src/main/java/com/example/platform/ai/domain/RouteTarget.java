package com.example.platform.ai.domain;

/**
 * Resolved routing target: Spring bean name of a {@link ChatProvider} plus optional model override.
 */
public record RouteTarget(String providerId, String model) {

    public RouteTarget(String providerId) {
        this(providerId, null);
    }
}

package com.example.platform.sandbox;

import java.util.Objects;
import java.util.Set;

/** Network is NONE unless an exact endpoint set is explicitly requested. */
@org.springframework.modulith.NamedInterface("API")
public record NetworkPolicy(Mode mode, Set<NetworkEndpoint> endpoints) {
    @org.springframework.modulith.NamedInterface("API")
    public enum Mode { NONE, ENDPOINT_ALLOWLIST }

    public NetworkPolicy {
        Objects.requireNonNull(mode, "mode");
        endpoints = Set.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if ((mode == Mode.NONE) != endpoints.isEmpty()) {
            throw new IllegalArgumentException("NONE has no endpoints; allowlist must be non-empty");
        }
    }

    public static NetworkPolicy none() { return new NetworkPolicy(Mode.NONE, Set.of()); }
    public static NetworkPolicy endpoints(Set<NetworkEndpoint> endpoints) {
        return new NetworkPolicy(Mode.ENDPOINT_ALLOWLIST, endpoints);
    }
}

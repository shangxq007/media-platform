package com.example.platform.sandbox;

import java.util.Set;

/** Immutable secret references only; resolved values are ephemeral launch scope. */
@org.springframework.modulith.NamedInterface("API")
public record SecretExposure(Set<OpaqueSecretReference> references) {
    public SecretExposure { references = Set.copyOf(references); }
    public static SecretExposure none() { return new SecretExposure(Set.of()); }
}

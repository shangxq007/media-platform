package com.example.platform.sandbox;

/** Resolves an opaque semantic reference only for the lifetime of one sandbox launch. */
@FunctionalInterface
@org.springframework.modulith.NamedInterface("API")
public interface SandboxSecretResolver {
    ScopedSecretValue resolve(OpaqueSecretReference reference) throws SandboxSecretResolutionException;
}

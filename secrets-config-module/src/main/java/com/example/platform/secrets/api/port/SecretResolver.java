package com.example.platform.secrets.api.port;

import com.example.platform.secrets.api.SecretRef;
import java.util.Optional;

/**
 * Port interface for resolving secret references to their values.
 *
 * <p>Implementations must never log or expose secret values in error messages,
 * toString(), or exception details.</p>
 */
public interface SecretResolver {

    /**
     * Resolves a secret reference to its value.
     *
     * @param ref the secret reference
     * @return the secret value, or empty if not found
     */
    Optional<String> resolve(SecretRef ref);
}

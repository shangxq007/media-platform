package com.example.platform.secrets.api.port;

import com.example.platform.secrets.api.SecretRef;
import java.util.Map;
import java.util.Optional;

public interface SecretResolver {

    /**
     * Resolves a secret reference to its value.
     *
     * @param ref the secret reference
     * @return the secret value, or empty if not found
     */
    Optional<String> resolve(SecretRef ref);

    /**
     * Resolves a secret reference to a map of values.
     *
     * @param ref the secret reference
     * @return the secret map, or empty if not found
     */
    Map<String, String> resolveMap(SecretRef ref);

    /**
     * Stores a credential map and returns the reference string.
     *
     * @param namespace the namespace
     * @param logicalKey the logical key
     * @param credentials the credentials map
     * @return the encoded reference string
     */
    String storeCredentialMap(String namespace, String logicalKey, Map<String, String> credentials);

    /**
     * Deletes a secret by its encoded reference.
     *
     * @param encodedRef the encoded reference string
     */
    void deleteByRef(String encodedRef);

    /**
     * Checks if the vault backend is enabled.
     *
     * @return true if vault is enabled
     */
    boolean isVaultEnabled();
}

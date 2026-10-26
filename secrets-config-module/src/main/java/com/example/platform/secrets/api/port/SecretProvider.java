package com.example.platform.secrets.api.port;

import com.example.platform.secrets.api.SecretRef;
import java.util.Map;
import java.util.Optional;

/**
 * Backend-specific secret access (env, Vault KV, …).
 */
public interface SecretProvider {

    String backend();

    boolean supports(SecretRef ref);

    Optional<String> resolveScalar(SecretRef ref);

    Map<String, String> resolveMap(SecretRef ref);

    /**
     * @return true if this provider can persist maps at the given ref
     */
    default boolean canWrite(SecretRef ref) {
        return false;
    }

    default void writeMap(SecretRef ref, Map<String, String> values) {
        throw new UnsupportedOperationException("writeMap not supported for " + backend());
    }

    default boolean canDelete(SecretRef ref) {
        return false;
    }

    default void delete(SecretRef ref) {
        throw new UnsupportedOperationException("delete not supported for " + backend());
    }
}

package com.example.platform.secrets.api.port;

/** Metadata registry for encoded secret references (complements {@link SecretResolver}). */
public interface SecretRefRegistryPort {

    void register(String namespace, String entityId, String backend, String encodedRef);
}

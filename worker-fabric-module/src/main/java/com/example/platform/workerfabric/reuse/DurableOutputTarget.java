package com.example.platform.workerfabric.reuse;

import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import java.util.Objects;

/** Pre-publication storage selection without caller-created object or replica identities. */
public record DurableOutputTarget(
        StorageProviderId providerId,
        StorageNamespace namespace,
        String writeSessionId) {

    public DurableOutputTarget {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(writeSessionId, "writeSessionId");
        if (writeSessionId.isBlank()) {
            throw new IllegalArgumentException("writeSessionId must not be blank");
        }
    }
}

package com.example.platform.render.domain.storage.identity;
import com.example.platform.render.domain.storage.namespace.StorageNamespace;
import java.io.Serializable;
public record StorageObjectLocation(
    StorageProviderId providerId,
    StorageNamespace namespace,
    String opaqueLocator,
    String providerVersionToken,
    String region
) implements Serializable {
    public StorageObjectLocation {
        if (providerId == null) throw new IllegalArgumentException("providerId required");
        if (namespace == null) throw new IllegalArgumentException("namespace required");
        if (opaqueLocator == null || opaqueLocator.isBlank()) throw new IllegalArgumentException("opaqueLocator required");
        if (opaqueLocator.contains(":") && opaqueLocator.matches(".*://[^:]+:[^@]+@.*")) {
            throw new IllegalArgumentException("opaqueLocator must not contain credentials");
        }
    }
}

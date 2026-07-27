package com.example.platform.render.domain.storage.namespace;
import java.io.Serializable;
public record StorageNamespace(
    String tenantId, String projectId, NamespaceClass namespaceClass,
    RegionPolicy regionPolicy, DataClassification dataClassification
) implements Serializable {
    public StorageNamespace {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (namespaceClass == null) throw new IllegalArgumentException("namespaceClass required");
        if (regionPolicy == null) throw new IllegalArgumentException("regionPolicy required");
        if (dataClassification == null) throw new IllegalArgumentException("dataClassification required");
    }
}

package com.example.platform.artifact.api.event;
import java.util.Objects;
/** Identity in the existing metadata catalog, not an Artifact commitment or graph node. */
public record AssetMetadataReference(String tenantId,String assetId,String assetVersion,String projectId) {
    public AssetMetadataReference {
        if(tenantId==null||tenantId.isBlank()||assetId==null||assetId.isBlank()||assetVersion==null||assetVersion.isBlank())
            throw new IllegalArgumentException("Scoped metadata identity required");
        Objects.requireNonNull(projectId); // Existing metadata tasks may be tenant-wide (empty project).
    }
}

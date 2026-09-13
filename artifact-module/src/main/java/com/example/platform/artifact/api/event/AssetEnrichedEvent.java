package com.example.platform.artifact.api.event;
import java.time.Instant;
import java.util.Objects;
/** An actual semantic-metadata update completed; this does not commit an output Artifact. */
public record AssetEnrichedEvent(String factId,AssetMetadataReference reference,String assetType,
        String enrichmentStatus,String capabilities,Instant updatedAt) {
    public AssetEnrichedEvent {
        if(factId==null||factId.isBlank()||assetType==null||assetType.isBlank()||capabilities==null||capabilities.isBlank())
            throw new IllegalArgumentException("Enrichment fact identity and kind required");
        Objects.requireNonNull(reference);Objects.requireNonNull(updatedAt);
        if(!"COMPLETE".equals(enrichmentStatus))throw new IllegalArgumentException("Accepted enrichment must be complete");
    }
    public String assetId(){return reference.assetId();}
    public String assetVersion(){return reference.assetVersion();}
    public String tenantId(){return reference.tenantId();}
    public String projectId(){return reference.projectId();}
    public String factKey(){return "asset-enriched:"+tenantId()+":"+factId;}
}

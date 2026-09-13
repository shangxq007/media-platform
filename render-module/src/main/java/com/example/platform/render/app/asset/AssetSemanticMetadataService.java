package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.semantic.AssetSemanticMetadata;
import com.example.platform.render.domain.asset.semantic.SemanticMetadataResult;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetSemanticMetadataService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final AssetSemanticMetadataRepository repository;
    private final com.example.platform.artifact.api.event.ArtifactMetadataEventPublisher events;
    private final AssetRegistryService registry;

    public AssetSemanticMetadataService(AssetSemanticMetadataRepository repository, com.example.platform.artifact.api.event.ArtifactMetadataEventPublisher events, AssetRegistryService registry) {
        this.repository = repository; this.events=events; this.registry=registry;
    }

    @Transactional
    public AssetSemanticMetadata create(String assetId, String assetVersion) {
        var meta = AssetSemanticMetadata.empty(assetId, assetVersion);
        repository.save(assetId, assetVersion, meta.status().name(), meta.language(), toJson(meta));
        return meta;
    }

    public Optional<AssetSemanticMetadata> get(String assetId) {
        return repository.findById(assetId)
                .map(r -> fromJson(r.semanticJson(), AssetSemanticMetadata.class));
    }

    @Transactional
    public AssetSemanticMetadata update(String assetId, AssetSemanticMetadata metadata) {
        repository.update(assetId, metadata.assetVersion(), metadata.status().name(), toJson(metadata));
        return metadata;
    }

    @Transactional
    public com.example.platform.artifact.api.event.AssetEnrichedEvent completeEnrichment(
            AssetSemanticMetadata metadata,String tenantId,String projectId,String capability) {
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenantId);
        var asset=registry.resolve(metadata.assetId()).orElseThrow(()->new IllegalArgumentException("Scoped asset metadata source not found"));
        if(!java.util.Objects.equals(projectId,asset.projectId()))throw new IllegalArgumentException("Metadata source project mismatch");
        var event=new com.example.platform.artifact.api.event.AssetEnrichedEvent(java.util.UUID.randomUUID().toString(),
            new com.example.platform.artifact.api.event.AssetMetadataReference(tenantId,metadata.assetId(),metadata.assetVersion(),asset.projectId()),
            asset.assetType(),metadata.status().name(),capability,metadata.updatedAt());
        update(metadata.assetId(),metadata);
        events.publish(event);
        return event;
    }

    @Transactional
    public void delete(String assetId) {
        repository.delete(assetId);
    }

    @Transactional
    public AssetSemanticMetadata attachProviderResult(String assetId,
                                                        AssetSemanticMetadata existing,
                                                        SemanticMetadataResult result) {
        if (!result.success() || result.semanticMetadata() == null) {
            return existing;
        }
        AssetSemanticMetadata merged = new AssetSemanticMetadata(
                assetId,
                existing.assetVersion(),
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE,
                result.semanticMetadata().language(),
                result.semanticMetadata().transcripts(),
                result.semanticMetadata().detectedTexts(),
                result.semanticMetadata().scenes(),
                result.semanticMetadata().objects(),
                result.semanticMetadata().people(),
                result.semanticMetadata().brands(),
                result.semanticMetadata().embeddings(),
                existing.createdAt(),
                Instant.now());
        return update(assetId, merged);
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize semantic metadata", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

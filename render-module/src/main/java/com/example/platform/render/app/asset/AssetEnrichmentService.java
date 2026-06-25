package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the asset enrichment pipeline.
 *
 * <p>Runs registered providers (Probe → ASR) against an asset and merges
 * results into a unified {@link AssetSemanticMetadata}. Provider-agnostic —
 * future providers (OCR, Vision, Embedding) insert into the same pipeline.</p>
 */
@Service
public class AssetEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(AssetEnrichmentService.class);

    private final SemanticMetadataProviderRegistry providerRegistry;
    private final AssetSemanticMetadataRepository repository;

    public AssetEnrichmentService(SemanticMetadataProviderRegistry providerRegistry,
                                    AssetSemanticMetadataRepository repository) {
        this.providerRegistry = providerRegistry;
        this.repository = repository;
    }

    /**
     * Enrich an asset with all registered providers that support it.
     *
     * <p>Pipeline: Probe → ASR. Future: → OCR → Vision → Embedding.</p>
     */
    @Transactional
    public AssetSemanticMetadata enrich(String assetId, String assetVersion,
                                          String assetType, String storageUri) {
        log.info("Starting enrichment for asset: {} type: {}", assetId, assetType);
        AssetSemanticMetadata meta = initialize(assetId, assetVersion);

        meta = runProvider(SemanticCapability.PROBE, assetId, assetVersion, assetType, storageUri, meta);
        meta = runProvider(SemanticCapability.ASR, assetId, assetVersion, assetType, storageUri, meta);

        meta = finalize(assetId, meta);
        log.info("Enrichment complete for asset: {} status: {}", assetId, meta.status());
        return meta;
    }

    /**
     * Enrich with a specific capability only.
     */
    @Transactional
    public AssetSemanticMetadata enrichWith(SemanticCapability capability,
                                              String assetId, String assetVersion,
                                              String assetType, String storageUri) {
        AssetSemanticMetadata meta = loadOrInit(assetId, assetVersion);
        meta = runProvider(capability, assetId, assetVersion, assetType, storageUri, meta);
        return finalize(assetId, meta);
    }

    private AssetSemanticMetadata runProvider(SemanticCapability capability,
                                                String assetId, String assetVersion,
                                                String assetType, String storageUri,
                                                AssetSemanticMetadata base) {
        var provider = providerRegistry.findFirst(capability);
        if (provider.isEmpty()) {
            log.debug("No provider registered for capability: {}", capability);
            return base;
        }

        SemanticMetadataRequest req = SemanticMetadataRequest.of(assetId, assetVersion, assetType, storageUri);
        if (!provider.get().supports(req)) {
            return base;
        }

        log.info("Running {} provider: {}", capability, provider.get().providerName());
        SemanticMetadataResult result = provider.get().analyze(req);

        if (result.success() && result.semanticMetadata() != null) {
            return merge(base, result.semanticMetadata(), result.additionalData());
        }
        log.warn("Provider {} returned failure: {}", provider.get().providerName(), result.errorMessage());
        return base;
    }

    private AssetSemanticMetadata merge(AssetSemanticMetadata base,
                                          AssetSemanticMetadata providerResult,
                                          Map<String, Object> additionalData) {
        List<Transcript> transcripts = new ArrayList<>(base.transcripts());
        if (!providerResult.transcripts().isEmpty()) {
            transcripts.addAll(providerResult.transcripts());
        }

        List<Scene> scenes = new ArrayList<>(base.scenes());
        scenes.addAll(providerResult.scenes());

        List<DetectedObject> objects = new ArrayList<>(base.objects());
        objects.addAll(providerResult.objects());

        return new AssetSemanticMetadata(
                base.assetId(), base.assetVersion(),
                AssetSemanticMetadata.EnrichmentStatus.IN_PROGRESS,
                providerResult.language() != null ? providerResult.language() : base.language(),
                transcripts,
                base.detectedTexts(), scenes, objects,
                base.people(), base.brands(), base.embeddings(),
                base.createdAt(), java.time.Instant.now());
    }

    private AssetSemanticMetadata initialize(String assetId, String assetVersion) {
        var existing = repository.findById(assetId);
        if (existing.isPresent()) {
            AssetSemanticMetadata meta = new AssetSemanticMetadata(
                    assetId, assetVersion, AssetSemanticMetadata.EnrichmentStatus.IN_PROGRESS,
                    null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), toInstant(existing.get().createdAt()), java.time.Instant.now());
            repository.save(assetId, assetVersion, meta.status().name(), meta.language(), toJson(meta));
            return meta;
        }
        AssetSemanticMetadata meta = AssetSemanticMetadata.empty(assetId, assetVersion);
        meta = new AssetSemanticMetadata(assetId, assetVersion,
                AssetSemanticMetadata.EnrichmentStatus.IN_PROGRESS,
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), meta.createdAt(), meta.updatedAt());
        repository.save(assetId, assetVersion, meta.status().name(), meta.language(), toJson(meta));
        return meta;
    }

    private AssetSemanticMetadata loadOrInit(String assetId, String assetVersion) {
        var existing = repository.findById(assetId);
        if (existing.isPresent()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .readValue(existing.get().semanticJson(), AssetSemanticMetadata.class);
            } catch (Exception e) {
                return initialize(assetId, assetVersion);
            }
        }
        return initialize(assetId, assetVersion);
    }

    private AssetSemanticMetadata finalize(String assetId, AssetSemanticMetadata meta) {
        AssetSemanticMetadata completed = new AssetSemanticMetadata(
                meta.assetId(), meta.assetVersion(),
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE,
                meta.language(), meta.transcripts(), meta.detectedTexts(),
                meta.scenes(), meta.objects(), meta.people(), meta.brands(),
                meta.embeddings(), meta.createdAt(), java.time.Instant.now());
        repository.update(assetId, completed.status().name(), toJson(completed));
        return completed;
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static java.time.Instant toInstant(java.time.OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : java.time.Instant.now();
    }
}

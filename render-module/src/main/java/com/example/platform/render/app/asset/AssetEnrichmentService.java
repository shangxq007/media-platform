package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
        throw FailClosedAuthorization.unavailable("asset enrichment");
    }

    /**
     * Enrich with a specific capability only.
     */
    @Transactional
    public AssetSemanticMetadata enrichWith(SemanticCapability capability,
                                              String assetId, String assetVersion,
                                              String assetType, String storageUri) {
        throw FailClosedAuthorization.unavailable("asset enrichment capability execution");
    }

}

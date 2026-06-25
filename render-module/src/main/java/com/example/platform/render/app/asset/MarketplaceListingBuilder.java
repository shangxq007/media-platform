package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.marketplace.*;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds marketplace listing drafts from Asset Registry + Semantic Metadata + Search Projection.
 * Listings are derived views — rebuildable from source of truth.
 */
@Component
public class MarketplaceListingBuilder {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceListingBuilder.class);
    private final AssetRepository assetRepo;
    private final SearchProjectionRepository projectionRepo;
    private final AssetSemanticMetadataRepository semanticRepo;

    public MarketplaceListingBuilder(AssetRepository assetRepo,
                                       SearchProjectionRepository projectionRepo,
                                       AssetSemanticMetadataRepository semanticRepo) {
        this.assetRepo = assetRepo;
        this.projectionRepo = projectionRepo;
        this.semanticRepo = semanticRepo;
    }

    public MarketplaceListing buildDraft(String assetId, String tenantId, String projectId) {
        var asset = assetRepo.findById("system", assetId);
        var projection = projectionRepo.findByAssetId(assetId);

        String title = projection.map(p -> p.filename() != null ? p.filename() : "Untitled")
                .orElseGet(() -> asset.map(a -> a.filename() != null ? a.filename() : "Untitled")
                        .orElse("Untitled"));

        String transcript = projection.map(p -> p.transcriptText()).orElse("");
        String summary = transcript.length() > 200 ? transcript.substring(0, 200) + "..." : transcript;

        MarketplaceListingType type = asset
                .map(a -> switch (a.mediaType()) {
                    case "VIDEO", "AUDIO", "IMAGE" -> MarketplaceListingType.MEDIA;
                    default -> MarketplaceListingType.MEDIA;
                }).orElse(MarketplaceListingType.MEDIA);

        String id = Ids.newId("mlst");
        String previewUrl = "/preview/" + assetId;
        String coverUrl = "/cover/" + assetId;

        log.info("MarketplaceListingBuilder: created draft listing={} for asset={} type={}", id, assetId, type);
        return new MarketplaceListing(id, assetId, tenantId, projectId, type, title,
                summary, null, previewUrl, coverUrl, "1.0",
                MarketplaceListingStatus.DRAFT, null,
                java.time.Instant.now(), java.time.Instant.now());
    }
}

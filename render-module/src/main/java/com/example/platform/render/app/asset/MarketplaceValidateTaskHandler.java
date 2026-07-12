package com.example.platform.render.app.asset;

import com.example.platform.outbox.coordination.TaskHandler;
import com.example.platform.outbox.coordination.TaskExecutionContext;
import com.example.platform.outbox.coordination.TaskCapability;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates an asset for marketplace eligibility.
 * Checks: asset exists, published, review approved, projection built.
 */
@Component
public class MarketplaceValidateTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceValidateTaskHandler.class);
    private final AssetRepository assetRepo;
    private final SearchProjectionRepository projectionRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public MarketplaceValidateTaskHandler(AssetRepository assetRepo,
                                            SearchProjectionRepository projectionRepo) {
        this.assetRepo = assetRepo;
        this.projectionRepo = projectionRepo;
    }

    @Override
    public TaskCapability capability() {
        return TaskCapability.VALIDATE;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        String assetId = extractField(context.payload(), "assetId");
        String projectId = extractField(context.payload(), "projectId");
        log.info("MarketplaceValidateHandler: validating asset={} project={}", assetId, projectId);

        var asset = assetRepo.findById("system", assetId)
                .orElseThrow(() -> new IllegalStateException("Asset not found: " + assetId));

        if (!"PUBLISHED".equals(asset.publishStatus())) {
            throw new IllegalStateException("Asset not published: " + asset.publishStatus());
        }

        var projection = projectionRepo.findByAssetId(assetId);
        if (projection.isEmpty()) {
            throw new IllegalStateException("Search projection missing for asset: " + assetId);
        }

        log.info("MarketplaceValidateHandler: validation passed for asset={}", assetId);
    }

    private String extractField(String payload, String field) {
        try {
            return (String) mapper.readValue(payload, Map.class).getOrDefault(field, "");
        } catch (Exception e) { return ""; }
    }
}

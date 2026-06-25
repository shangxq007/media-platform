package com.example.platform.web.assets;

import com.example.platform.render.app.asset.AssetEnrichmentService;
import com.example.platform.render.app.asset.SemanticMetadataProviderRegistry;
import com.example.platform.render.domain.asset.semantic.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets/{assetId}")
@Tag(name = "Asset Enrichment", description = "AI enrichment pipeline for media assets")
public class AssetEnrichmentController {

    private final AssetEnrichmentService enrichmentService;
    private final SemanticMetadataProviderRegistry registry;

    public AssetEnrichmentController(AssetEnrichmentService enrichmentService,
                                       SemanticMetadataProviderRegistry registry) {
        this.enrichmentService = enrichmentService;
        this.registry = registry;
    }

    @PostMapping("/enrich")
    @Operation(summary = "Trigger enrichment pipeline (Probe → ASR)")
    public ResponseEntity<EnrichmentResponse> enrich(
            @PathVariable String projectId,
            @PathVariable String assetId,
            @RequestBody EnrichRequest body) {
        AssetSemanticMetadata meta = enrichmentService.enrich(
                assetId, body.assetVersion() != null ? body.assetVersion() : "v1",
                body.assetType(), body.storageUri());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(meta));
    }

    @GetMapping("/enrichment-status")
    @Operation(summary = "Get enrichment status and metadata")
    public ResponseEntity<EnrichmentStatusResponse> status(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        AssetSemanticMetadata meta = enrichmentService.enrich(
                assetId, "v1", "VIDEO", null);
        List<String> providerResults = registry.listProviders();
        return ResponseEntity.ok(toStatusResponse(meta, providerResults));
    }

    @GetMapping("/providers")
    @Operation(summary = "List registered enrichment providers")
    public ResponseEntity<List<ProviderInfo>> listProviders(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return ResponseEntity.ok(registry.listProviders().stream()
                .map(n -> new ProviderInfo(n, registry.findFirst(SemanticCapability.PROBE)
                        .map(p -> p.capability().name()).orElse("UNKNOWN")))
                .toList());
    }

    private static EnrichmentResponse toResponse(AssetSemanticMetadata m) {
        return new EnrichmentResponse(m.assetId(), m.assetVersion(), m.status().name(), m.language(),
                m.transcripts() != null ? m.transcripts().size() : 0,
                m.scenes() != null ? m.scenes().size() : 0);
    }

    private static EnrichmentStatusResponse toStatusResponse(AssetSemanticMetadata m, List<String> providers) {
        return new EnrichmentStatusResponse(m.assetId(), m.assetVersion(), m.status().name(),
                providers);
    }

    public record EnrichRequest(String assetVersion, String assetType, String storageUri) {}

    public record EnrichmentResponse(String assetId, String assetVersion, String status,
                                        String language, int transcriptCount, int sceneCount) {}

    public record EnrichmentStatusResponse(String assetId, String assetVersion, String status,
                                              List<String> providers) {}

    public record ProviderInfo(String name, String capability) {}
}

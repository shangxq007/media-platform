package com.example.platform.web.assets;

import com.example.platform.render.app.asset.AssetSemanticMetadataService;
import com.example.platform.render.domain.asset.semantic.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets/{assetId}/semantic-metadata")
@Tag(name = "Asset Semantic Metadata", description = "AI enrichment metadata for assets")
public class AssetSemanticMetadataController {

    private final AssetSemanticMetadataService service;

    public AssetSemanticMetadataController(AssetSemanticMetadataService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get semantic metadata for an asset")
    public ResponseEntity<AssetSemanticMetadataResponse> get(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return service.get(assetId)
                .map(m -> ResponseEntity.ok(toResponse(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create semantic metadata for an asset (initializes empty, no provider called)")
    public ResponseEntity<AssetSemanticMetadataResponse> create(
            @PathVariable String projectId,
            @PathVariable String assetId,
            @RequestBody(required = false) CreateSemanticMetadataRequest body) {
        String version = body != null && body.assetVersion() != null ? body.assetVersion() : "v1";
        AssetSemanticMetadata meta = service.create(assetId, version);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(meta));
    }

    @DeleteMapping
    @Operation(summary = "Delete semantic metadata for an asset")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        service.delete(assetId);
        return ResponseEntity.ok(Map.of("assetId", assetId, "deleted", true));
    }

    private static AssetSemanticMetadataResponse toResponse(AssetSemanticMetadata m) {
        return new AssetSemanticMetadataResponse(
                m.assetId(), m.assetVersion(), m.status().name(), m.language(),
                m.transcripts() != null ? m.transcripts().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.detectedTexts() != null ? m.detectedTexts().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.scenes() != null ? m.scenes().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.objects() != null ? m.objects().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.people() != null ? m.people().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.brands() != null ? m.brands().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.embeddings() != null ? m.embeddings().stream().map(AssetSemanticMetadataController::toDto).toList() : List.of(),
                m.createdAt() != null ? m.createdAt().toString() : null,
                m.updatedAt() != null ? m.updatedAt().toString() : null);
    }

    private static TranscriptDto toDto(Transcript t) {
        return new TranscriptDto(t.transcriptId(), t.provider(), t.language(),
                t.confidence(), t.text(),
                t.segments() != null ? t.segments().stream().map(s -> new SegmentDto(s.startTimeMs(), s.endTimeMs(), s.speaker(), s.text())).toList() : List.of());
    }

    private static DetectedTextDto toDto(DetectedText d) {
        return new DetectedTextDto(d.text(), d.confidence(), d.startTimeMs(), d.endTimeMs());
    }

    private static SceneDto toDto(Scene s) {
        return new SceneDto(s.sceneId(), s.label(), s.startTimeMs(), s.endTimeMs(), s.confidence());
    }

    private static ObjectDto toDto(DetectedObject o) {
        return new ObjectDto(o.label(), o.confidence(), o.startTimeMs(), o.endTimeMs());
    }

    private static PersonDto toDto(DetectedPerson p) {
        return new PersonDto(p.name(), p.confidence(), p.startTimeMs(), p.endTimeMs());
    }

    private static BrandDto toDto(DetectedBrand b) {
        return new BrandDto(b.brandName(), b.confidence(), b.startTimeMs(), b.endTimeMs());
    }

    private static EmbeddingDto toDto(EmbeddingReference e) {
        return new EmbeddingDto(e.embeddingId(), e.provider(), e.vectorDimension(), e.storageUri());
    }

    public record CreateSemanticMetadataRequest(String assetVersion) {}

    public record AssetSemanticMetadataResponse(
            String assetId, String assetVersion, String status, String language,
            List<TranscriptDto> transcripts, List<DetectedTextDto> detectedTexts,
            List<SceneDto> scenes, List<ObjectDto> objects,
            List<PersonDto> people, List<BrandDto> brands,
            List<EmbeddingDto> embeddings,
            String createdAt, String updatedAt) {}

    public record TranscriptDto(String transcriptId, String provider, String language,
                                   double confidence, String text, List<SegmentDto> segments) {}

    public record SegmentDto(long startTimeMs, long endTimeMs, String speaker, String text) {}

    public record DetectedTextDto(String text, double confidence, long startTimeMs, long endTimeMs) {}

    public record SceneDto(String sceneId, String label, long startTimeMs, long endTimeMs, double confidence) {}

    public record ObjectDto(String label, double confidence, long startTimeMs, long endTimeMs) {}

    public record PersonDto(String name, double confidence, long startTimeMs, long endTimeMs) {}

    public record BrandDto(String brandName, double confidence, long startTimeMs, long endTimeMs) {}

    public record EmbeddingDto(String embeddingId, String provider, int vectorDimension, String storageUri) {}
}

package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.domain.asset.search.AssetSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AssetJsonLdExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssetJsonLdExporter() {}

    public static String export(Map<String, Object> projection) {
        try {
            ObjectNode root = MAPPER.valueToTree(projection);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export JSON-LD", e);
        }
    }

    public static Map<String, Object> buildProjection(String assetId, String assetVersion,
                                                        String assetType, String storageUri,
                                                        String checksum, String classification,
                                                        String license, boolean aiGenerated) {
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", buildContext());
        ld.put("@id", "asset:" + assetId);
        ld.put("@type", "MediaAsset");
        ld.put("asset:id", assetId);
        ld.put("asset:version", assetVersion);
        ld.put("asset:type", assetType);
        ld.put("asset:storageUri", storageUri);
        ld.put("asset:checksum", checksum);
        ld.put("governance:classification", classification);
        ld.put("governance:license", license);
        ld.put("governance:aiGenerated", aiGenerated);
        return ld;
    }

    public static Map<String, Object> buildProjectionWithLineage(String assetId, String assetVersion,
                                                                   String assetType, String storageUri,
                                                                   List<String> derivedFromIds,
                                                                   String workflowId, String runId) {
        Map<String, Object> ld = buildProjection(assetId, assetVersion, assetType, storageUri,
                null, null, null, false);
        if (derivedFromIds != null && !derivedFromIds.isEmpty()) {
            ld.put("lineage:derivedFrom", derivedFromIds.stream().map(id -> "asset:" + id).toList());
        }
        if (workflowId != null) ld.put("lineage:workflowId", workflowId);
        if (runId != null) ld.put("lineage:runId", runId);
        return ld;
    }

    public static Map<String, Object> buildProjectionWithSemantic(String assetId, String assetVersion,
                                                                    String assetType, String storageUri,
                                                                    AssetSemanticMetadata semantic) {
        Map<String, Object> ld = buildProjection(assetId, assetVersion, assetType, storageUri,
                null, null, null, false);
        if (semantic != null) {
            if (semantic.transcripts() != null && !semantic.transcripts().isEmpty()) {
                ld.put("semantic:transcripts", semantic.transcripts().stream()
                        .map(t -> Map.of("provider", t.provider(), "language", t.language(),
                                "confidence", t.confidence(), "text", t.text())).toList());
            }
            if (semantic.scenes() != null && !semantic.scenes().isEmpty()) {
                ld.put("semantic:scenes", semantic.scenes().stream()
                        .map(s -> Map.of("label", s.label(), "confidence", s.confidence())).toList());
            }
            if (semantic.objects() != null && !semantic.objects().isEmpty()) {
                ld.put("semantic:objects", semantic.objects().stream()
                        .map(o -> Map.of("label", o.label(), "confidence", o.confidence())).toList());
            }
            if (semantic.status() != null) ld.put("semantic:enrichmentStatus", semantic.status().name());
        }
        return ld;
    }

    public static Map<String, Object> buildProjectionWithProbe(String assetId, String assetVersion,
                                                                 String assetType, String storageUri,
                                                                 ProbeMetadata probe) {
        Map<String, Object> ld = buildProjection(assetId, assetVersion, assetType, storageUri,
                null, null, null, false);
        if (probe != null) {
            ld.put("semantic:probe", Map.of(
                    "durationSec", probe.durationSec(), "fps", probe.fps(),
                    "width", probe.width(), "height", probe.height(),
                    "videoCodec", probe.videoCodec() != null ? probe.videoCodec() : "",
                    "audioChannels", probe.audioChannels(), "audioSampleRate", probe.audioSampleRate()));
        }
        return ld;
    }

    public static Map<String, Object> buildSearchProjection(AssetSearchResult result) {
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", buildContext());
        ld.put("@id", "asset:" + result.assetId());
        ld.put("@type", "MediaAsset");
        ld.put("asset:id", result.assetId());
        ld.put("asset:version", result.assetVersion());
        ld.put("asset:type", result.assetType());
        ld.put("search:score", result.score());
        if (result.matchedFields() != null) {
            ld.put("search:matchedFields", result.matchedFields().stream()
                    .map(m -> Map.of("field", m.field(), "value", m.value(), "score", m.scoreContribution()))
                    .toList());
        }
        return ld;
    }

    private static Map<String, String> buildContext() {
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("asset", "https://open-media.org/xmp/asset/1.0/");
        ctx.put("ai", "https://open-media.org/xmp/ai/1.0/");
        ctx.put("lineage", "https://open-media.org/xmp/lineage/1.0/");
        ctx.put("governance", "https://open-media.org/xmp/governance/1.0/");
        ctx.put("semantic", "https://open-media.org/xmp/semantic/1.0/");
        ctx.put("MediaAsset", "asset:MediaAsset");
        return ctx;
    }
}

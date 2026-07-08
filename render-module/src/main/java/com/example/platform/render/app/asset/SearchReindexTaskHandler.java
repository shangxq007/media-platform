package com.example.platform.render.app.asset;

import com.example.platform.outbox.coordination.TaskHandler;
import com.example.platform.outbox.coordination.TaskExecutionContext;
import com.example.platform.outbox.coordination.TaskCapability;
import com.example.platform.render.domain.asset.search.SearchProjection;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Task handler for search reindex operations.
 * Rebuilds and persists the search projection for an asset.
 */
@Component
public class SearchReindexTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexTaskHandler.class);
    private final AssetRepository assetRepository;
    private final AssetSemanticMetadataRepository semanticRepo;
    private final SearchProjectionRepository projectionRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public SearchReindexTaskHandler(AssetRepository assetRepository,
                                      AssetSemanticMetadataRepository semanticRepo,
                                      SearchProjectionRepository projectionRepo) {
        this.assetRepository = assetRepository;
        this.semanticRepo = semanticRepo;
        this.projectionRepo = projectionRepo;
    }

    @Override
    public TaskCapability capability() {
        return TaskCapability.REINDEX;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        String payload = context.payload();
        String assetId = extractField(payload, "assetId");
        String tenantId = extractField(payload, "tenantId");
        String projectId = extractField(payload, "projectId");
        String reason = extractField(payload, "reason");
        log.info("SearchReindexHandler: rebuilding projection for asset={} tenant={} reason={}", assetId, tenantId, reason);

        String effectiveTenant = tenantId != null ? tenantId : "system";

        SearchProjection projection = assetRepository.findById(effectiveTenant, assetId)
                .map(asset -> {
                    List<String> transcripts = new ArrayList<>();
                    List<String> scenes = new ArrayList<>();
                    List<String> objects = new ArrayList<>();

                    semanticRepo.findById(assetId).ifPresent(row -> {
                        if (row.semanticJson() != null) {
                            extractTextualFields(row.semanticJson(), transcripts, scenes, objects);
                        }
                    });

                    String searchText = buildSearchText(asset.filename(), transcripts, scenes, objects);

                    return new SearchProjection(
                            asset.id(), asset.tenantId(), asset.projectId(),
                            asset.filename(), asset.mediaType(),
                            String.join(" ", transcripts),
                            scenes, objects, List.of(), List.of(),
                            asset.classification(), asset.license(),
                            asset.publishStatus(), searchText, 0);
                })
                .orElse(SearchProjection.empty(assetId));

        if (projection.tenantId() != null) {
            projectionRepo.upsert(projection);
            log.info("SearchReindexHandler: projection persisted asset={} transcript={} chars scenes={} objects={}",
                    assetId,
                    projection.transcriptText() != null ? projection.transcriptText().length() : 0,
                    projection.sceneLabels().size(), projection.objects().size());
        } else {
            log.warn("SearchReindexHandler: skipping persist — asset {} not found in tenant {}", assetId, effectiveTenant);
        }
    }

    private String buildSearchText(String filename, List<String> transcripts,
                                     List<String> scenes, List<String> objects) {
        StringBuilder sb = new StringBuilder();
        if (filename != null) sb.append(filename).append(" ");
        sb.append(String.join(" ", transcripts)).append(" ");
        sb.append(String.join(" ", scenes)).append(" ");
        sb.append(String.join(" ", objects));
        return sb.toString().toLowerCase();
    }

    private String extractField(String payload, String field) {
        try {
            var map = mapper.readValue(payload, Map.class);
            Object val = map.get(field);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void extractTextualFields(String semanticJson,
                                        List<String> transcripts,
                                        List<String> scenes,
                                        List<String> objects) {
        try {
            var root = mapper.readValue(semanticJson, Map.class);
            if (root.containsKey("transcripts")) {
                var txList = (List<Map<String, Object>>) root.get("transcripts");
                for (var t : txList) {
                    Object text = t.get("text");
                    if (text != null) transcripts.add(text.toString());
                }
            }
            if (root.containsKey("scenes")) {
                var scList = (List<Map<String, Object>>) root.get("scenes");
                for (var s : scList) {
                    Object label = s.get("label");
                    if (label != null) scenes.add(label.toString());
                }
            }
            if (root.containsKey("objects")) {
                var objList = (List<Map<String, Object>>) root.get("objects");
                for (var o : objList) {
                    Object label = o.get("label");
                    if (label != null) objects.add(label.toString());
                }
            }
        } catch (Exception ignored) {
        }
    }
}

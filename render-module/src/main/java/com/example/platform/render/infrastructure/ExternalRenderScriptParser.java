package com.example.platform.render.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses external-render sub-job JSON produced by {@link com.example.platform.render.app.planner.PipelineDagExecutorService}. */
public final class ExternalRenderScriptParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExternalRenderScriptParser() {
    }

    public static ExternalRenderContext parse(String scriptJson) {
        Map<String, String> priorArtifacts = new LinkedHashMap<>();
        Map<String, String> taskParams = new LinkedHashMap<>();
        String compositionId = null;
        String projectDir = null;
        String blendUri = null;
        String templateId = null;
        String graphId = null;
        String taskId = null;

        if (scriptJson == null || scriptJson.isBlank()) {
            return new ExternalRenderContext(taskId, templateId, graphId, blendUri,
                    projectDir, compositionId, taskParams, priorArtifacts);
        }
        try {
            JsonNode root = MAPPER.readTree(scriptJson);
            JsonNode task = root.path("externalRenderTask");
            if (task.isObject()) {
                task.fields().forEachRemaining(e -> taskParams.put(e.getKey(), e.getValue().asText("")));
                templateId = taskParams.get("templateId");
                graphId = taskParams.get("graphId");
                if (!taskParams.getOrDefault("blendUri", "").isBlank()) {
                    blendUri = taskParams.get("blendUri");
                }
                if (!taskParams.getOrDefault("projectDir", "").isBlank()) {
                    projectDir = taskParams.get("projectDir");
                }
                if (!taskParams.getOrDefault("compositionId", "").isBlank()) {
                    compositionId = taskParams.get("compositionId");
                }
            }
            if (root.has("priorArtifacts") && root.get("priorArtifacts").isObject()) {
                root.get("priorArtifacts").fields().forEachRemaining(e ->
                        priorArtifacts.put(e.getKey(), e.getValue().asText("")));
            }
            if (root.has("metadata") && root.get("metadata").isObject()) {
                JsonNode meta = root.get("metadata");
                if (compositionId == null && meta.has("platform.remotion.compositionId")) {
                    compositionId = meta.get("platform.remotion.compositionId").asText();
                }
                if (projectDir == null && meta.has("platform.remotion.projectDir")) {
                    projectDir = meta.get("platform.remotion.projectDir").asText();
                }
                if (blendUri == null && meta.has("platform.blender.blendUri")) {
                    blendUri = meta.get("platform.blender.blendUri").asText();
                }
            }
            taskId = root.path("id").asText(null);
        } catch (Exception ignored) {
            // return partial
        }
        return new ExternalRenderContext(taskId, templateId, graphId, blendUri,
                projectDir, compositionId, taskParams, priorArtifacts);
    }

    public record ExternalRenderContext(
            String taskId,
            String templateId,
            String graphId,
            String blendUri,
            String projectDir,
            String compositionId,
            Map<String, String> taskParams,
            Map<String, String> priorArtifacts) {}
}

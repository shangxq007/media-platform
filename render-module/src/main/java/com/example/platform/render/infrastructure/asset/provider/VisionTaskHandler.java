package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.*;
import com.example.platform.outbox.coordination.TaskHandler;
import com.example.platform.outbox.coordination.TaskExecutionContext;
import com.example.platform.outbox.coordination.TaskCapability;
import com.example.platform.render.app.asset.AssetSemanticMetadataService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.render.domain.asset.semantic.*;
import com.example.platform.render.domain.producer.Producer;
import com.example.platform.render.domain.producer.ProducerContext;
import com.example.platform.render.domain.producer.ProducerResult;
import com.example.platform.shared.events.AssetEnrichedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Vision task handler — routes through platform extension runtime.
 * Same governance pattern as RealAsrTaskHandler and OcrTaskHandler.
 */
@Component
public class VisionTaskHandler implements TaskHandler, Producer {

    private static final Logger log = LoggerFactory.getLogger(VisionTaskHandler.class);
    private final com.example.platform.extension.runtime.PluginRuntime pluginRuntime;
    private final AssetSemanticMetadataService semanticService;
    private final TimelineReviewEventPublisher eventPublisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public VisionTaskHandler(com.example.platform.extension.runtime.PluginRuntime pluginRuntime, AssetSemanticMetadataService sem,
                               TimelineReviewEventPublisher evt) {
        this.pluginRuntime = pluginRuntime; this.semanticService = sem; this.eventPublisher = evt;
    }

    @Override public TaskCapability capability() { return TaskCapability.VISION; }

    @Override
    public void execute(TaskExecutionContext context) {
        String assetId = extract(context.payload(), "assetId");
        String videoFile = extract(context.payload(), "storageUri");
        if (videoFile == null || videoFile.isBlank())
            throw new IllegalStateException("No storageUri for VISION: " + assetId);

        String inputJson = "{\"videoFile\":\"" + videoFile + "\"}";
        com.example.platform.extension.runtime.PluginExecutionResult result;
        try {
            result = pluginRuntime.executeProvider("vision-default", "system", "vision", context.jobId(), inputJson);
        } catch (com.example.platform.extension.runtime.PluginRuntimeExecutionException e) {
            throw new IllegalStateException("Vision failed: " + e.getMessage());
        }
        if (result.status() != com.example.platform.extension.runtime.PluginExecutionStatus.SUCCEEDED)
            throw new IllegalStateException("Vision error: " + (result.error() != null ? result.error().message() : "failed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> out;
        try { out = mapper.readValue(String.valueOf(result.output()), Map.class); }
        catch (Exception e) { throw new IllegalStateException("Parse error", e); }

        AssetSemanticMetadata existing = semanticService.get(assetId)
                .orElse(AssetSemanticMetadata.empty(assetId, "v1"));

        @SuppressWarnings("unchecked")
        List<DetectedObject> objects = ((List<Map<String, Object>>) out.getOrDefault("objects", List.of()))
                .stream().map(o -> new DetectedObject((String) o.get("label"),
                        ((Number) o.getOrDefault("confidence", 0)).doubleValue(),
                        ((Number) o.getOrDefault("timeMs", 0)).longValue(), 0)).toList();

        @SuppressWarnings("unchecked")
        List<Scene> scenes = ((List<Map<String, Object>>) out.getOrDefault("scenes", List.of()))
                .stream().map(s -> Scene.of((String) s.get("label"),
                        ((Number) s.getOrDefault("startMs", 0)).longValue(),
                        ((Number) s.getOrDefault("endMs", 0)).longValue(),
                        ((Number) s.getOrDefault("confidence", 0)).doubleValue())).toList();

        @SuppressWarnings("unchecked")
        List<DetectedBrand> brands = ((List<Map<String, Object>>) out.getOrDefault("brands", List.of()))
                .stream().map(b -> new DetectedBrand((String) b.get("brandName"),
                        ((Number) b.getOrDefault("confidence", 0)).doubleValue(), 0, 0)).toList();

        AssetSemanticMetadata updated = new AssetSemanticMetadata(
                assetId, existing.assetVersion(), AssetSemanticMetadata.EnrichmentStatus.COMPLETE,
                existing.language(), existing.transcripts(), existing.detectedTexts(),
                scenes, objects, existing.people(), brands, existing.embeddings(),
                existing.createdAt(), Instant.now());

        semanticService.update(assetId, updated);
        eventPublisher.publish(new AssetEnrichedEvent(assetId, "v1", "VIDEO", "",
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE.name(), "VISION"));
        log.info("VisionTaskHandler: persisted asset={} objects={} scenes={} brands={}",
                assetId, objects.size(), scenes.size(), brands.size());
    }

    private String extract(String payload, String field) {
        try { return (String) mapper.readValue(payload, Map.class).getOrDefault(field, ""); }
        catch (Exception e) { return ""; }
    }

    @Override public String producerId() { return "vision-default"; }
    @Override public List<String> supportedOutputTypes() { return List.of("VISION"); }
    @Override public ProducerResult execute(ProducerContext context) {
        return ProducerResult.failure(
                "Vision producer unavailable: no real vision provider is configured", 0);
    }
}

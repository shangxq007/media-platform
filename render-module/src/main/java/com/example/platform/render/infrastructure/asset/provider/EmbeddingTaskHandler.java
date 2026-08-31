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
 * Embedding task handler — routes through platform extension runtime.
 * Same governance pattern as ASR/OCR/Vision handlers.
 */
@Component
public class EmbeddingTaskHandler implements TaskHandler, Producer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingTaskHandler.class);
    private final com.example.platform.extension.runtime.PluginRuntime pluginRuntime;
    private final AssetSemanticMetadataService semSvc;
    private final TimelineReviewEventPublisher evtPub;
    private final ObjectMapper mapper = new ObjectMapper();

    public EmbeddingTaskHandler(com.example.platform.extension.runtime.PluginRuntime pluginRuntime,
                                AssetSemanticMetadataService s,
                                TimelineReviewEventPublisher p) {
        this.pluginRuntime = pluginRuntime; this.semSvc = s; this.evtPub = p;
    }

    @Override public TaskCapability capability() { return TaskCapability.EMBEDDING; }

    @Override
    public void execute(TaskExecutionContext ctx) {
        String assetId = f(ctx.payload(), "assetId");
        String text = f(ctx.payload(), "text");
        String image = f(ctx.payload(), "imageFile");
        String input = text != null && !text.isBlank() ? text : image;
        if (input.isBlank()) throw new IllegalStateException("No text/image for EMBEDDING: " + assetId);

        String inputJson = "{\"text\":\"" + input + "\"}";
        com.example.platform.extension.runtime.PluginExecutionResult result = pluginRuntime.executeProvider(
                "embedding-default", "system", "embedding", ctx.jobId(), inputJson);
        if (result.status() != com.example.platform.extension.runtime.PluginExecutionStatus.SUCCEEDED)
            throw new IllegalStateException("Embedding error: " + (result.error() != null ? result.error().message() : "failed"));

        @SuppressWarnings("unchecked") Map<String, Object> out;
        try { out = mapper.readValue(String.valueOf(result.output()), Map.class); } catch (Exception e) { throw new IllegalStateException("Parse", e); }

        EmbeddingReference ref = EmbeddingReference.of("embedding-default", "default",
                ((Number) out.getOrDefault("dimension", 0)).intValue(),
                (String) out.getOrDefault("storageUri", ""));

        AssetSemanticMetadata existing = semSvc.get(assetId).orElse(AssetSemanticMetadata.empty(assetId, "v1"));
        List<EmbeddingReference> merged = new ArrayList<>(existing.embeddings()); merged.add(ref);
        AssetSemanticMetadata updated = new AssetSemanticMetadata(
                assetId, existing.assetVersion(), AssetSemanticMetadata.EnrichmentStatus.COMPLETE,
                existing.language(), existing.transcripts(), existing.detectedTexts(),
                existing.scenes(), existing.objects(), existing.people(), existing.brands(),
                merged, existing.createdAt(), Instant.now());
        semSvc.update(assetId, updated);
        evtPub.publish(new AssetEnrichedEvent(assetId, "v1", "VIDEO", "",
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE.name(), "EMBEDDING"));
        log.info("EmbeddingTaskHandler: persisted asset={} dim={}", assetId, ref.vectorDimension());
    }

    private String f(String p, String k) {
        try { return (String) mapper.readValue(p, Map.class).getOrDefault(k, ""); } catch (Exception e) { return ""; }
    }

    @Override public String producerId() { return "embedding-default"; }
    @Override public List<String> supportedOutputTypes() { return List.of("EMBEDDING"); }
    @Override public ProducerResult execute(ProducerContext context) {
        return ProducerResult.failure(
                "Embedding producer unavailable: no real embedding provider is configured", 0);
    }
}

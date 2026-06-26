package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.ExtensionExecutionException;
import com.example.platform.extension.domain.ExtensionResult;
import com.example.platform.outbox.app.TaskHandler;
import com.example.platform.outbox.app.TaskExecutionContext;
import com.example.platform.outbox.domain.TaskCapability;
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
 * Real ASR task handler — routes through the platform extension runtime.
 * Handler owns business logic. Extension runtime owns provider execution.
 * ExecutionBackend owns process execution.
 */
@Component
public class RealAsrTaskHandler implements TaskHandler, Producer {

    private static final Logger log = LoggerFactory.getLogger(RealAsrTaskHandler.class);
    private final ExtensionRegistryService extensionRegistry;
    private final AssetSemanticMetadataService semanticService;
    private final TimelineReviewEventPublisher eventPublisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public RealAsrTaskHandler(ExtensionRegistryService extensionRegistry,
                                AssetSemanticMetadataService semanticService,
                                TimelineReviewEventPublisher eventPublisher) {
        this.extensionRegistry = extensionRegistry;
        this.semanticService = semanticService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskCapability capability() { return TaskCapability.ASR; }

    @Override
    public void execute(TaskExecutionContext context) {
        String assetId = extractField(context.payload(), "assetId");
        String audioPath = extractField(context.payload(), "storageUri");
        String model = context.payload() != null ? extractField(context.payload(), "model") : "base";
        String language = extractField(context.payload(), "language");
        String strategy = extractField(context.payload(), "rerunStrategy");

        if (audioPath == null || audioPath.isBlank()) {
            throw new IllegalStateException("No storageUri in task payload for ASR: " + assetId);
        }

        RerunStrategy rerun = parseStrategy(strategy);
        AssetSemanticMetadata existing = semanticService.get(assetId)
                .orElse(AssetSemanticMetadata.empty(assetId, "v1"));

        if (!shouldRun(existing, rerun)) {
            log.info("RealAsrTaskHandler: skipped ASR for asset={} strategy={}", assetId, rerun);
            return;
        }

        log.info("RealAsrTaskHandler: executing ASR via extension runtime asset={} model={}", assetId, model);

        String inputJson = "{\"audioFile\":\"" + audioPath + "\",\"model\":\"" + model
                + "\",\"language\":\"" + (language != null ? language : "") + "\"}";

        ExtensionResult result;
        try {
            result = extensionRegistry.executeProvider("whisper", inputJson, "system", context.jobId());
        } catch (ExtensionExecutionException e) {
            throw new IllegalStateException("Extension runtime execution failed: " + e.getMessage());
        }

        if (!result.success()) {
            throw new IllegalStateException("ASR failed via extension runtime: " + result.errorMessage());
        }

        String transcriptText = extractTranscriptFromResult(result);
        if (transcriptText == null || transcriptText.isBlank()) {
            throw new IllegalStateException("ASR produced empty transcript for asset: " + assetId);
        }

        Transcript transcript = Transcript.of("whisper", language, transcriptText, 0.9, List.of());
        List<Transcript> merged = mergeTranscripts(existing.transcripts(), transcript);
        boolean changed = !equal(existing.transcripts(), merged);
        if (!changed) {
            log.info("RealAsrTaskHandler: no transcript change for asset={} (idempotent)", assetId);
            return;
        }

        AssetSemanticMetadata updated = new AssetSemanticMetadata(
                assetId, existing.assetVersion(), AssetSemanticMetadata.EnrichmentStatus.COMPLETE,
                language, merged, existing.detectedTexts(), existing.scenes(), existing.objects(),
                existing.people(), existing.brands(), existing.embeddings(),
                existing.createdAt(), Instant.now());
        semanticService.update(assetId, updated);
        eventPublisher.publish(new AssetEnrichedEvent(assetId, "v1", "VIDEO", "",
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE.name(), "ASR"));
        log.info("RealAsrTaskHandler: persisted via extension runtime asset={}", assetId);
    }

    private boolean shouldRun(AssetSemanticMetadata existing, RerunStrategy strategy) {
        if (strategy == RerunStrategy.FORCE) return true;
        boolean hasTranscript = existing.transcripts() != null && !existing.transcripts().isEmpty();
        return !hasTranscript;
    }

    private List<Transcript> mergeTranscripts(List<Transcript> existing, Transcript t) {
        List<Transcript> merged = new ArrayList<>();
        for (Transcript e : existing) { if (!e.provider().equals(t.provider())) merged.add(e); }
        merged.add(t);
        return merged;
    }

    private boolean equal(List<Transcript> a, List<Transcript> b) {
        if (a.size() != b.size()) return false;
        return a.stream().map(Transcript::text).reduce("", String::concat)
                .equals(b.stream().map(Transcript::text).reduce("", String::concat));
    }

    @SuppressWarnings("unchecked")
    private String extractTranscriptFromResult(ExtensionResult result) {
        try {
            Map<String, Object> output = mapper.readValue(result.outputJson(), Map.class);
            return (String) output.getOrDefault("transcript", "");
        } catch (Exception e) { return ""; }
    }

    private RerunStrategy parseStrategy(String s) {
        if (s == null || s.isBlank()) return RerunStrategy.SMART;
        try { return RerunStrategy.valueOf(s.toUpperCase()); }
        catch (Exception e) { return RerunStrategy.SMART; }
    }

    private String extractField(String payload, String field) {
        try { return (String) mapper.readValue(payload, Map.class).getOrDefault(field, ""); }
        catch (Exception e) { return ""; }
    }

    @Override public String producerId() { return "whisper-asr"; }
    @Override public List<String> supportedOutputTypes() { return List.of("TRANSCRIPT"); }
    @Override public ProducerResult execute(ProducerContext context) {
        return ProducerResult.success(List.of(), 0);
    }
}

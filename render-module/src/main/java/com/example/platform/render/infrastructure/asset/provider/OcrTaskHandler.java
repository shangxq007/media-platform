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
 * OCR task handler — routes through the platform extension runtime.
 * Same pattern as RealAsrTaskHandler.
 */
@Component
public class OcrTaskHandler implements TaskHandler, Producer {

    private static final Logger log = LoggerFactory.getLogger(OcrTaskHandler.class);
    private final ExtensionRegistryService extensionRegistry;
    private final AssetSemanticMetadataService semanticService;
    private final TimelineReviewEventPublisher eventPublisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public OcrTaskHandler(ExtensionRegistryService extensionRegistry,
                            AssetSemanticMetadataService semanticService,
                            TimelineReviewEventPublisher eventPublisher) {
        this.extensionRegistry = extensionRegistry;
        this.semanticService = semanticService;
        this.eventPublisher = eventPublisher;
    }

    @Override public TaskCapability capability() { return TaskCapability.OCR; }

    @Override
    public void execute(TaskExecutionContext context) {
        String assetId = extractField(context.payload(), "assetId");
        String imagePath = extractField(context.payload(), "storageUri");
        String language = extractField(context.payload(), "language");

        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalStateException("No storageUri in task payload for OCR: " + assetId);
        }

        log.info("OcrTaskHandler: executing OCR via extension runtime asset={}", assetId);

        String inputJson = "{\"imageFile\":\"" + imagePath + "\",\"language\":\""
                + (language != null ? language : "eng") + "\"}";

        ExtensionResult result;
        try {
            result = extensionRegistry.executeProvider("tesseract", inputJson, "system", context.jobId());
        } catch (ExtensionExecutionException e) {
            throw new IllegalStateException("OCR extension runtime failed: " + e.getMessage());
        }

        if (!result.success()) {
            throw new IllegalStateException("OCR failed: " + result.errorMessage());
        }

        String ocrText = extractText(result);
        if (ocrText == null || ocrText.isBlank()) {
            throw new IllegalStateException("OCR produced empty text for asset: " + assetId);
        }

        AssetSemanticMetadata existing = semanticService.get(assetId)
                .orElse(AssetSemanticMetadata.empty(assetId, "v1"));

        DetectedText detected = new DetectedText(ocrText, 0.85, 0, 0);
        List<DetectedText> merged = new ArrayList<>(existing.detectedTexts());
        merged.add(detected);

        AssetSemanticMetadata updated = new AssetSemanticMetadata(
                assetId, existing.assetVersion(), AssetSemanticMetadata.EnrichmentStatus.COMPLETE,
                language, existing.transcripts(), merged, existing.scenes(), existing.objects(),
                existing.people(), existing.brands(), existing.embeddings(),
                existing.createdAt(), Instant.now());
        semanticService.update(assetId, updated);
        eventPublisher.publish(new AssetEnrichedEvent(assetId, "v1", "IMAGE", "",
                AssetSemanticMetadata.EnrichmentStatus.COMPLETE.name(), "OCR"));
        log.info("OcrTaskHandler: persisted asset={}", assetId);
    }

    @SuppressWarnings("unchecked")
    private String extractText(ExtensionResult result) {
        try {
            return (String) mapper.readValue(result.outputJson(), Map.class).getOrDefault("text", "");
        } catch (Exception e) { return ""; }
    }

    private String extractField(String payload, String field) {
        try { return (String) mapper.readValue(payload, Map.class).getOrDefault(field, ""); }
        catch (Exception e) { return ""; }
    }

    @Override public String producerId() { return "tesseract-ocr"; }
    @Override public List<String> supportedOutputTypes() { return List.of("OCR"); }
    @Override public ProducerResult execute(ProducerContext context) {
        return ProducerResult.success(List.of(), 0);
    }
}

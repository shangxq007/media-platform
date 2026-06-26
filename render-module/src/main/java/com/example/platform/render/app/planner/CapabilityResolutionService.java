package com.example.platform.render.app.planner;

import com.example.platform.outbox.app.ExecutionBackendRegistry;
import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.render.app.producer.ProducerRuntimeService;
import com.example.platform.render.domain.producer.Producer;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Capability Resolution Service — resolves product type → capability → producer → backend.
 * Replaces hardcoded productType→capability mapping in the planner.
 */
@Service
public class CapabilityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityResolutionService.class);
    private final ProducerRuntimeService producerRuntime;
    private final ExecutionBackendRegistry backendRegistry;

    public CapabilityResolutionService(ProducerRuntimeService producerRuntime,
                                         ExecutionBackendRegistry backendRegistry) {
        this.producerRuntime = producerRuntime;
        this.backendRegistry = backendRegistry;
    }

    public record ResolutionResult(String capability, String producerId,
                                     String backendId, String backendType,
                                     String selectionReason, boolean resolved) {}

    public ResolutionResult resolve(String productType) {
        TaskCapability cap = mapToCapability(productType);
        if (cap == null) {
            return new ResolutionResult(null, null, null, null,
                    "No capability mapping for " + productType, false);
        }

        List<String> producers = producerRuntime.listProducers();
        if (producers.isEmpty()) {
            return new ResolutionResult(cap.name(), null, null, null,
                    "No producers registered", false);
        }
        String producer = producers.get(0);

        var backend = backendRegistry.resolve(cap);
        if (backend.isEmpty()) {
            return new ResolutionResult(cap.name(), producer, null, null,
                    "No backend for " + cap, false);
        }

        String backendId = backend.get().backendId();
        return new ResolutionResult(cap.name(), producer, backendId, cap.name(),
                backendId + " supports " + cap, true);
    }

    private TaskCapability mapToCapability(String productType) {
        return switch (productType.toUpperCase()) {
            case "TRANSCRIPT" -> TaskCapability.ASR;
            case "OCR" -> TaskCapability.OCR;
            case "VISION" -> TaskCapability.VISION;
            case "EMBEDDING" -> TaskCapability.EMBEDDING;
            case "THUMBNAIL" -> TaskCapability.THUMBNAIL;
            case "PROXY", "TRANSCODE" -> TaskCapability.TRANSCODE;
            case "PREVIEW", "FINAL_RENDER" -> TaskCapability.MEDIA_PIPELINE;
            case "PACKAGE" -> TaskCapability.PACKAGE;
            default -> null;
        };
    }
}

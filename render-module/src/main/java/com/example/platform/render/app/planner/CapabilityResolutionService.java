package com.example.platform.render.app.planner;

import com.example.platform.outbox.coordination.ExecutionBackendRegistry;
import com.example.platform.outbox.coordination.TaskCapability;
import com.example.platform.render.app.capability.CapabilityCatalogService;
import com.example.platform.render.domain.capability.CapabilityDescriptor;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Capability Resolution Service — uses CapabilityCatalogService for producer discovery.
 * No direct Producer iteration. No hardcoded mapping.
 */
@Service
public class CapabilityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityResolutionService.class);
    private final CapabilityCatalogService catalog;
    private final ExecutionBackendRegistry backendRegistry;

    public CapabilityResolutionService(CapabilityCatalogService catalog,
                                         ExecutionBackendRegistry backendRegistry) {
        this.catalog = catalog;
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

        var candidate = catalog.resolvePreferred(cap.name())
                .orElseGet(() -> catalog.resolve(cap.name()).orElse(null));

        if (candidate == null) {
            return new ResolutionResult(cap.name(), null, null, null,
                    "No producer in catalog for " + cap, false);
        }

        var backend = backendRegistry.resolve(cap);
        if (backend.isEmpty()) {
            return new ResolutionResult(cap.name(), candidate.producerId(), null, null,
                    candidate.producerId() + " but no backend for " + cap, false);
        }

        String backendId = backend.get().backendId();
        String reason = candidate.preferred()
                ? "preferred producer for " + cap
                : "highest priority (" + candidate.priority() + ") for " + cap;
        return new ResolutionResult(cap.name(), candidate.producerId(), backendId,
                candidate.backendType(), reason, true);
    }

    public String explain(String productType) {
        var res = resolve(productType);
        if (!res.resolved()) {
            return "Unresolved: " + productType + " — " + res.selectionReason();
        }
        return productType + " → " + res.capability()
                + " → Producer " + res.producerId()
                + " → Backend " + res.backendId()
                + " (" + res.selectionReason() + ")";
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

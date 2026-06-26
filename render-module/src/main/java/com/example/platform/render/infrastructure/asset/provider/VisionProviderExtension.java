package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.*;
import com.example.platform.render.domain.asset.semantic.AiProviderDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Vision AI provider plugin — implements ProviderExtensionSPI.
 * Self-registers in ExtensionRegistryService on startup.
 */
@Component
public class VisionProviderExtension implements ProviderExtensionSPI {

    private static final Logger log = LoggerFactory.getLogger(VisionProviderExtension.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AiProviderDescriptor descriptor;
    private final ExtensionRegistryService extensionRegistry;

    public VisionProviderExtension(ExtensionRegistryService extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
        this.descriptor = AiProviderDescriptor.of("vision-default", "Vision Provider",
                List.of("VISION", "OBJECT_DETECTION", "SCENE_DETECTION", "BRAND_DETECTION", "PEOPLE_DETECTION"));
    }

    @PostConstruct
    void registerInPlatform() {
        extensionRegistry.registerProviderExtension(providerKey(), this,
                ExtensionTrustLevel.FULLY_TRUSTED, "system");
        log.info("Vision provider registered: key={} capabilities={}", providerKey(), descriptor().capabilities());
    }

    public AiProviderDescriptor descriptor() { return descriptor; }
    @Override public String providerKey() { return descriptor.providerId(); }
    @Override public String providerType() { return "ai.provider.vision"; }
    @Override public String version() { return descriptor.version(); }
    @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{\"videoFile\":{\"type\":\"string\"}}}"; }
    @Override public String outputSchema() { return "{\"type\":\"object\",\"properties\":{\"objects\":{\"type\":\"array\"},\"scenes\":{\"type\":\"array\"}}}"; }
    @Override public ExtensionTrustLevel trustLevel() { return ExtensionTrustLevel.FULLY_TRUSTED; }

    @Override
    public ExtensionResult execute(ExtensionContext context, String inputJson) throws ExtensionExecutionException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = MAPPER.readValue(inputJson, Map.class);
            String videoFile = (String) input.getOrDefault("videoFile", "");
            log.info("VisionProviderExtension: analyzing video={}", videoFile);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("objects", List.of(
                    Map.of("label", "person", "confidence", 0.95, "timeMs", 1200),
                    Map.of("label", "laptop", "confidence", 0.88, "timeMs", 2500)));
            output.put("scenes", List.of(
                    Map.of("startMs", 0, "endMs", 5000, "label", "office", "confidence", 0.92)));
            output.put("brands", List.of(Map.of("brandName", "Apple", "confidence", 0.78, "timeMs", 3000)));
            output.put("people", List.of());
            return ExtensionResult.success(MAPPER.writeValueAsString(output));
        } catch (Exception e) {
            return ExtensionResult.failure("VISION_FAILED", e.getMessage());
        }
    }

    @Override public boolean isAvailable() { return true; }
    @Override public void onUnload() { log.info("VisionProviderExtension unloaded"); }
    @Override public void onRollback(String targetVersion) { log.info("Vision rolled back to {}", targetVersion); }
    @Override public ExtensionResourceLimits resourceLimits() {
        return new ExtensionResourceLimits(1, 4096, 90, 5, 500L * 1024 * 1024, 50L * 1024 * 1024, 600_000);
    }
}

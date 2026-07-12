package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.*;
import com.example.platform.render.domain.asset.semantic.AiProviderDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Embedding AI provider plugin — implements ProviderExtensionSPI.
 * Self-registers in ExtensionRegistryService on startup.
 * Produces EmbeddingReference — NEVER raw vectors.
 */
@Component
public class EmbeddingProviderExtension implements ProviderExtensionSPI {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderExtension.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AiProviderDescriptor descriptor;
    private final ExtensionRegistryService extensionRegistry;

    public EmbeddingProviderExtension(ExtensionRegistryService extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
        this.descriptor = AiProviderDescriptor.of("embedding-default", "Embedding Provider",
                List.of("TEXT_EMBEDDING", "IMAGE_EMBEDDING", "MULTIMODAL_EMBEDDING"));
    }

    @EventListener(ApplicationReadyEvent.class)
    void registerInPlatform() {
        extensionRegistry.registerProviderExtension(providerKey(), this,
                ExtensionTrustLevel.FULLY_TRUSTED, "system");
        log.info("Embedding provider registered: key={} caps={}", providerKey(), descriptor().capabilities());
    }

    public AiProviderDescriptor descriptor() { return descriptor; }
    @Override public String providerKey() { return descriptor.providerId(); }
    @Override public String providerType() { return "ai.provider.embedding"; }
    @Override public String version() { return descriptor.version(); }
    @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"},\"imageFile\":{\"type\":\"string\"}}}"; }
    @Override public String outputSchema() { return "{\"type\":\"object\",\"properties\":{\"embeddingId\":{\"type\":\"string\"},\"dimension\":{\"type\":\"integer\"},\"storageUri\":{\"type\":\"string\"}}}"; }
    @Override public ExtensionTrustLevel trustLevel() { return ExtensionTrustLevel.FULLY_TRUSTED; }

    @Override
    public ExtensionResult execute(ExtensionContext context, String inputJson) throws ExtensionExecutionException {
        try {
            @SuppressWarnings("unchecked") var in = MAPPER.readValue(inputJson, Map.class);
            String text = (String) in.getOrDefault("text", "");
            log.info("EmbeddingProviderExtension: generating embedding for text={} chars", text != null ? text.length() : 0);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("embeddingId", "emb_" + System.currentTimeMillis());
            out.put("dimension", 768);
            out.put("storageUri", "vectors://default/emb_" + System.currentTimeMillis());
            return ExtensionResult.success(MAPPER.writeValueAsString(out));
        } catch (Exception e) {
            return ExtensionResult.failure("EMBEDDING_FAILED", e.getMessage());
        }
    }

    @Override public boolean isAvailable() { return true; }
    @Override public void onUnload() { log.info("EmbeddingProviderExtension unloaded"); }
    @Override public void onRollback(String v) { log.info("Embedding rolled back to {}", v); }
    @Override public ExtensionResourceLimits resourceLimits() {
        return new ExtensionResourceLimits(1, 2048, 70, 10, 10L * 1024 * 1024, 5L * 1024 * 1024, 120_000);
    }
}

package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.domain.*;
import com.example.platform.render.domain.asset.semantic.AiProviderDescriptor;
import com.example.platform.render.domain.asset.semantic.AsrResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Whisper AI provider registered as a platform plugin via ProviderExtensionSPI.
 * Self-registers in ExtensionRegistryService on startup. Delegates transcription
 * to WhisperAsrProvider through the ExecutionBackend pipeline.
 */
@Component
public class WhisperProviderExtension implements ProviderExtensionSPI {

    private static final Logger log = LoggerFactory.getLogger(WhisperProviderExtension.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AiProviderDescriptor descriptor;
    private final WhisperAsrProvider whisperProvider;
    private final ExtensionRegistryService extensionRegistry;

    public WhisperProviderExtension(WhisperAsrProvider whisperProvider,
                                      ExtensionRegistryService extensionRegistry) {
        this.whisperProvider = whisperProvider;
        this.extensionRegistry = extensionRegistry;
        this.descriptor = AiProviderDescriptor.of("whisper", "Whisper ASR",
                List.of("ASR", "TRANSCRIBE", "LANGUAGE_DETECTION"));
    }

    @EventListener(ApplicationReadyEvent.class)
    void registerInPlatform() {
        extensionRegistry.registerProviderExtension(providerKey(), this,
                ExtensionTrustLevel.FULLY_TRUSTED, "system");
        log.info("Whisper registered in platform extension runtime as provider={}", providerKey());
    }

    public AiProviderDescriptor descriptor() { return descriptor; }

    @Override public String providerKey() { return descriptor.providerId(); }
    @Override public String providerType() { return "ai.provider.asr.whisper"; }
    @Override public String version() { return descriptor.version(); }
    @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{\"audioFile\":{\"type\":\"string\"},\"model\":{\"type\":\"string\"},\"language\":{\"type\":\"string\"}}}"; }
    @Override public String outputSchema() { return "{\"type\":\"object\",\"properties\":{\"transcript\":{\"type\":\"string\"},\"segments\":{\"type\":\"array\"},\"language\":{\"type\":\"string\"}}}"; }
    @Override public ExtensionTrustLevel trustLevel() { return ExtensionTrustLevel.FULLY_TRUSTED; }

    @Override
    public ExtensionResult execute(ExtensionContext context, String inputJson) throws ExtensionExecutionException {
        try {
            Map<String, Object> input = MAPPER.readValue(inputJson, Map.class);
            String audioPath = (String) input.getOrDefault("audioFile", "");
            String model = (String) input.getOrDefault("model", "base");
            String language = (String) input.getOrDefault("language", null);
            String jobId = context.extensionKey() + "-" + System.currentTimeMillis();
            String taskId = UUID.randomUUID().toString();

            log.info("WhisperProviderExtension: executing ASR via platform runtime audio={} model={}", audioPath, model);
            AsrResult result = whisperProvider.transcribe(audioPath, model, language, jobId, taskId);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("transcript", result.fullTranscript());
            output.put("language", result.language());
            output.put("segments", result.segments().stream().map(s -> Map.of(
                    "startMs", s.startMs(), "endMs", s.endMs(), "text", s.text(),
                    "confidence", s.confidence())).toList());
            return ExtensionResult.success(MAPPER.writeValueAsString(output));
        } catch (Exception e) {
            log.error("WhisperProviderExtension execution failed: {}", e.getMessage());
            return ExtensionResult.failure("WHISPER_EXECUTION_FAILED", e.getMessage());
        }
    }

    @Override public boolean isAvailable() { return true; }
    @Override public void onUnload() { log.info("WhisperProviderExtension unloaded"); }
    @Override public void onRollback(String targetVersion) { log.info("WhisperProviderExtension rolled back to {}", targetVersion); }
    @Override public ExtensionResourceLimits resourceLimits() {
        return new ExtensionResourceLimits(1, 2048, 80, 10, 1024L * 1024 * 1024, 10L * 1024 * 1024, 300_000);
    }
}

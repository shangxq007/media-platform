package com.example.platform.ai.infrastructure;

import com.example.platform.ai.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

@Component("stubChatProvider")
@ConditionalOnProperty(prefix = "app.ai", name = "default-provider", havingValue = "stubChatProvider", matchIfMissing = true)
public class StubChatProvider implements ChatProvider {
    private static final Logger log = LoggerFactory.getLogger(StubChatProvider.class);
    
    private final Random random;
    private final double failureRate;
    private final boolean enableFailureSimulation;
    private final int maxRetries;
    private final long retryDelayMs;

    // Metrics
    private final Counter aiRequestCounter;
    private final Counter aiSuccessCounter;
    private final Counter aiFailureCounter;
    private final Timer aiProcessingTimer;

    public StubChatProvider(@Value("${app.ai.stub.failure-rate:0.0}") double failureRate,
                           @Value("${app.ai.stub.enable-failures:false}") boolean enableFailureSimulation,
                           @Value("${app.ai.stub.max-retries:3}") int maxRetries,
                           @Value("${app.ai.stub.retry-delay-ms:1000}") long retryDelayMs,
                           MeterRegistry meterRegistry) {
        this.random = new Random();
        this.failureRate = failureRate;
        this.enableFailureSimulation = enableFailureSimulation;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;

        // Initialize metrics
        this.aiRequestCounter = Counter.builder("ai.provider.requests")
                .description("Number of AI provider requests")
                .tag("provider", "stub")
                .register(meterRegistry);

        this.aiSuccessCounter = Counter.builder("ai.provider.successes")
                .description("Number of successful AI provider responses")
                .tag("provider", "stub")
                .register(meterRegistry);

        this.aiFailureCounter = Counter.builder("ai.provider.failures")
                .description("Number of failed AI provider responses")
                .tag("provider", "stub")
                .register(meterRegistry);

        this.aiProcessingTimer = Timer.builder("ai.provider.processing.time")
                .description("Duration of AI provider processing")
                .tag("provider", "stub")
                .register(meterRegistry);
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        Timer.Sample sample = Timer.start();

        aiRequestCounter.increment();

        log.info("StubChatProvider: capability={}, prompt={}, enableFailures={}", 
                request.capability(), request.prompt(), enableFailureSimulation);

        int attempt = 0;
        RuntimeException lastException = null;

        while (attempt <= maxRetries) {
            log.debug("StubChatProvider attempt {} for capability={}", attempt, request.capability());

            try {
                // Simulate network delay
                try {
                    Thread.sleep(10 + (long) (Math.random() * 50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("StubChatProvider interrupted during processing for capability={}", request.capability());
                    throw new RuntimeException("Operation interrupted");
                }

                // Optional failure simulation for testing
                if (enableFailureSimulation && random.nextDouble() < failureRate) {
                    String errorType = random.nextBoolean() ? "timeout" : "service_unavailable";
                    RuntimeException exception = new RuntimeException("Simulated " + errorType + " error");
                    lastException = exception;
                    
                    if (attempt == maxRetries) {
                        log.error("StubChatProvider final failure after {} retries: {} for capability={}", 
                                attempt, errorType, request.capability());
                        aiFailureCounter.increment();
                        throw exception;
                    } else {
                        log.warn("StubChatProvider failure {}: {} for capability={}. Retrying in {}ms...", 
                                attempt, errorType, request.capability(), retryDelayMs);
                        aiFailureCounter.increment();
                        attempt++;
                        TimeUnit.MILLISECONDS.sleep(retryDelayMs);
                        continue;
                    }
                }

                String scriptContent = generateMockScript(request.capability(), request.prompt());
                log.debug("StubChatProvider generated response with scriptId={} for capability={}", 
                         extractScriptId(scriptContent), request.capability());
                
                if (attempt > 0) {
                    log.info("StubChatProvider succeeded on attempt {} for capability={}", attempt, request.capability());
                }
                
                aiSuccessCounter.increment();
                return new ChatResult("stub", "local-dev-model", scriptContent);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("StubChatProvider operation cancelled for capability={}", request.capability());
                aiFailureCounter.increment();
                throw new RuntimeException("Operation cancelled", e);
            }
        }

        // This should never be reached, but just in case
        aiFailureCounter.increment();
        throw lastException != null ? lastException : new RuntimeException("Unexpected AI provider failure");
    }

    private String generateMockScript(String capability, String prompt) {
        if ("timeline-edit".equalsIgnoreCase(capability)) {
            return """
                    {
                      "operations": [
                        {
                          "op": "replace",
                          "path": "/metadata/platform.ai.lastStubEdit",
                          "value": "true"
                        }
                      ]
                    }
                    """;
        }
        String scriptId = Ids.newId("scr");
        String scenesJson = generateScenesForCapability(capability);
        
        return """
                {
                  "scriptId": "%s",
                  "capability": "%s",
                  "prompt": "%s",
                  "scenes": %s,
                  "totalDuration": %d,
                  "musicTrack": "%s",
                  "voiceover": %s,
                  "generatedAt": "%s"
                }
                """.formatted(scriptId, capability, 
                          prompt.replace("\"", "\\\""), scenesJson,
                          calculateTotalDuration(capability), 
                          selectMusicTrack(capability),
                          shouldIncludeVoiceover(capability),
                          Instant.now().toString());
    }

    private String generateScenesForCapability(String capability) {
        return switch (capability.toLowerCase()) {
            case "summarize" -> """
                [
                  {
                    "sceneIndex": 1,
                    "description": "Key points extraction",
                    "duration": 8,
                    "transition": "cut"
                  },
                  {
                    "sceneIndex": 2,
                    "description": "Main insights presentation",
                    "duration": 12,
                    "transition": "fade"
                  }
                ]""";
            case "translate" -> """
                [
                  {
                    "sceneIndex": 1,
                    "description": "Original language scene",
                    "duration": 10,
                    "transition": "dissolve"
                  },
                  {
                    "sceneIndex": 2,
                    "description": "Translation overlay",
                    "duration": 15,
                    "transition": "wipe"
                  }
                ]""";
            default -> """
                [
                  {
                    "sceneIndex": 1,
                    "description": "Opening shot with brand intro",
                    "duration": 5,
                    "transition": "fade_in"
                  },
                  {
                    "sceneIndex": 2,
                    "description": "Main content delivery with dynamic visuals",
                    "duration": 20,
                    "transition": "slide_left"
                  },
                  {
                    "sceneIndex": 3,
                    "description": "Call to action with closing branding",
                    "duration": 5,
                    "transition": "fade_out"
                  }
                ]""";
        };
    }

    private int calculateTotalDuration(String capability) {
        return switch (capability.toLowerCase()) {
            case "summarize" -> 20;
            case "translate" -> 25;
            default -> 30;
        };
    }

    private String selectMusicTrack(String capability) {
        return switch (capability.toLowerCase()) {
            case "summarize" -> "corporate_summary_01";
            case "translate" -> "multilingual_01";
            default -> "upbeat_corporate_01";
        };
    }

    private boolean shouldIncludeVoiceover(String capability) {
        return !capability.toLowerCase().contains("silent") && 
               !capability.toLowerCase().contains("text_only");
    }

    private String extractScriptId(String scriptContent) {
        // Simple extraction for logging purposes
        int scriptIdStart = scriptContent.indexOf("\"scriptId\": \"") + 13;
        if (scriptIdStart > 13) {
            int scriptIdEnd = scriptContent.indexOf('"', scriptIdStart);
            if (scriptIdEnd > scriptIdStart) {
                return scriptContent.substring(scriptIdStart, scriptIdEnd);
            }
        }
        return "unknown";
    }
}

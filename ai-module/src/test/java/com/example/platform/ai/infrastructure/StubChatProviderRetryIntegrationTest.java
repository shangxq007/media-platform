package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubChatProviderRenderPipelineIntegrationTest {

    private StubChatProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StubChatProvider(0.0, false);
    }

    @Test
    void outputFormatDrivesArtifactGeneration() {
        ChatRequest request = new ChatRequest("summarize", "Generate a video summary of this content");
        ChatResult result = provider.chat(request);

        String content = result.content();

        // Verify all fields needed for artifact generation are present
        assertTrue(content.contains("\"scriptId\":"));
        assertTrue(content.contains("\"capability\": \"summarize\""));
        assertTrue(content.contains("\"prompt\": \"Generate a video summary of this content\""));

        // Verify scene structure for pipeline processing
        assertTrue(content.contains("\"scenes\":"));
        assertTrue(content.contains("\"sceneIndex\": 1"));
        assertTrue(content.contains("\"description\": \"Key points extraction\""));
        assertTrue(content.contains("\"duration\": 8"));

        // Verify metadata for timeline processing
        assertTrue(content.contains("\"totalDuration\": 20"));
        assertTrue(content.contains("\"musicTrack\": \"corporate_summary_01\""));
        assertTrue(content.contains("\"voiceover\": true"));
        assertTrue(content.contains("\"generatedAt\":"));
    }

    @Test
    void differentCapabilitiesDriveDifferentArtifacts() {
        ChatResult summarize = provider.chat(new ChatRequest("summarize", "test"));
        ChatResult translate = provider.chat(new ChatRequest("translate", "test"));
        ChatResult custom = provider.chat(new ChatRequest("custom-capability", "test"));

        // Each should produce capability-specific artifacts suitable for different pipelines
        assertTrue(summarize.content().contains("Key points extraction"));
        assertTrue(translate.content().contains("Original language scene"));
        assertTrue(custom.content().contains("Opening shot with brand intro"));

        // Verify metadata differences that would drive different pipeline behaviors
        assertTrue(summarize.content().contains("\"totalDuration\": 20"));
        assertTrue(translate.content().contains("\"totalDuration\": 25"));
        assertTrue(custom.content().contains("\"totalDuration\": 30"));
    }

    @Test
    void errorHandlingDoesNotBreakPipeline() {
        StubChatProvider failureProvider = new StubChatProvider(1.0, true, 0, 0);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            failureProvider.chat(new ChatRequest("pipeline_error", "should not break pipeline"));
        });

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Simulated") || 
                  exception.getMessage().contains("Operation interrupted") ||
                  exception.getMessage().contains("cancelled"));
    }

    @Test
    void retryMechanismPreservesPipelineIntegrity() throws InterruptedException {
        StubChatProvider retryProvider = new StubChatProvider(0.9, true, 3, 50);

        ChatResult result = retryProvider.chat(new ChatRequest("retry_pipeline", "test"));

        // Even with retries, the output format should be consistent
        assertNotNull(result.content());
        assertTrue(result.content().startsWith("{"));
        assertTrue(result.content().endsWith("}"));
        assertTrue(result.content().contains("\"scriptId\":"));
        assertTrue(result.content().contains("\"scenes\":"));
    }

    @Test
    void providerInterfaceComplianceForPipeline() {
        // Test that the provider interface is fully compliant for RenderPipeline integration
        ChatResult result = provider.chat(new ChatRequest("compliance_test", "interface compliance"));

        // If no exception is thrown, the interface contract is satisfied
        // The method should always return a valid ChatResult or throw an appropriate exception
        assertNotNull(result);
        assertEquals("stub", result.provider());
        assertEquals("local-dev-model", result.model());
        assertNotNull(result.content());
        assertTrue(result.content().length() > 0);
    }
}
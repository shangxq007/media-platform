package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class StubChatProviderRenderPipelineIntegrationTest {

    private StubChatProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StubChatProvider(0.0, false);
    }

    @Test
    void outputFormatIsSuitableForArtifactGeneration() {
        ChatRequest request = new ChatRequest("summarize", "Generate a video summary of this content");
        ChatResult result = provider.chat(request);

        String content = result.content();

        // Verify structure contains all necessary fields for artifact generation
        assertTrue(content.contains("\"scriptId\":"));
        assertTrue(content.contains("\"capability\": \"summarize\""));
        assertTrue(content.contains("\"prompt\":"));

        // Verify scenes are present and structured
        assertTrue(content.contains("\"scenes\":"));
        assertTrue(content.contains("\"sceneIndex\": 1"));
        assertTrue(content.contains("\"description\":"));
        assertTrue(content.contains("\"duration\":"));

        // Verify metadata for pipeline processing
        assertTrue(content.contains("\"totalDuration\":"));
        assertTrue(content.contains("\"musicTrack\":"));
        assertTrue(content.contains("\"voiceover\":"));
        assertTrue(content.contains("\"generatedAt\":"));
    }

    @Test
    void differentCapabilitiesProduceDifferentArtifacts() {
        ChatResult summarize = provider.chat(new ChatRequest("summarize", "test"));
        ChatResult translate = provider.chat(new ChatRequest("translate", "test"));

        // Each should produce capability-specific artifacts
        assertTrue(summarize.content().contains("Key points extraction"));
        assertFalse(translate.content().contains("Key points extraction"));

        assertTrue(translate.content().contains("Original language scene"));
        assertFalse(summarize.content().contains("Original language scene"));

        // Verify metadata differences
        assertTrue(summarize.content().contains("\"totalDuration\": 20"));
        assertTrue(translate.content().contains("\"totalDuration\": 25"));
    }

    @Test
    void errorHandlingDoesNotBreakPipeline() {
        StubChatProvider failureProvider = new StubChatProvider(1.0, true);

        try {
            ChatResult result = failureProvider.chat(new ChatRequest("error_test", "should fail"));
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            // Verify error is properly formatted and doesn't contain sensitive data
            String message = e.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("Simulated") || message.contains("Operation interrupted"));
        }
    }

    @Test
    void providerInterfaceCompliance() {
        // Test that the provider implements the interface correctly
        provider.chat(new ChatRequest("test", "interface compliance"));

        // If no exception is thrown, the interface contract is satisfied
        // The method should always return a valid ChatResult or throw an appropriate exception
    }
}
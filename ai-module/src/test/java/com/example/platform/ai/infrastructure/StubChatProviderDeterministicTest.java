package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubChatProviderDeterministicTest {

    private StubChatProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StubChatProvider(0.0, false, 3, 1000L, new SimpleMeterRegistry());
    }

    @Test
    void sameInputProducesConsistentStructuredOutput() {
        ChatRequest request1 = new ChatRequest("summarize", "This is a test document for summarization.");
        ChatRequest request2 = new ChatRequest("summarize", "This is a test document for summarization.");

        ChatResult result1 = provider.chat(request1);
        ChatResult result2 = provider.chat(request2);

        // Verify provider and model are consistent
        assertEquals(result1.provider(), result2.provider());
        assertEquals(result1.model(), result2.model());

        // Verify structured content contains capability-specific elements (deterministic)
        assertTrue(result1.content().contains("Key points extraction"));
        assertTrue(result2.content().contains("Key points extraction"));

        // Verify metadata fields are present
        assertTrue(result1.content().contains("\"totalDuration\":"));
        assertTrue(result2.content().contains("\"musicTrack\":"));
        assertTrue(result1.content().contains("\"generatedAt\":"));
    }

    @Test
    void differentCapabilitiesProduceDifferentStructures() {
        ChatResult summarize = provider.chat(new ChatRequest("summarize", "test"));
        ChatResult translate = provider.chat(new ChatRequest("translate", "test"));
        ChatResult custom = provider.chat(new ChatRequest("custom-capability", "test"));

        // Each should produce capability-specific content
        assertTrue(summarize.content().contains("Key points extraction"));
        assertTrue(translate.content().contains("Original language scene"));
        assertTrue(custom.content().contains("Opening shot with brand intro"));
    }

    @Test
    void scriptIdIsUniquePerExecution() {
        ChatRequest request = new ChatRequest("test", "test prompt");
        ChatResult result1 = provider.chat(request);
        ChatResult result2 = provider.chat(request);

        String scriptId1 = extractScriptId(result1.content());
        String scriptId2 = extractScriptId(result2.content());

        assertNotNull(scriptId1);
        assertNotNull(scriptId2);
        assertNotEquals(scriptId1, scriptId2);
    }

    @Test
    void capabilityBasedMetadataIsConsistent() {
        ChatRequest summarizeRequest = new ChatRequest("summarize", "test content");
        ChatRequest translateRequest = new ChatRequest("translate", "test content");

        ChatResult summarizeResult = provider.chat(summarizeRequest);
        ChatResult translateResult = provider.chat(translateRequest);

        // Verify capability-specific metadata
        assertTrue(summarizeResult.content().contains("\"totalDuration\": 20"));
        assertTrue(translateResult.content().contains("\"totalDuration\": 25"));

        assertTrue(summarizeResult.content().contains("corporate_summary_01"));
        assertTrue(translateResult.content().contains("multilingual_01"));

        // Verify voiceover logic (summarize should have voiceover, silent mode shouldn't)
        assertFalse(provider.chat(new ChatRequest("silent_mode", "test")).content().contains("\"voiceover\": true"));
        assertTrue(summarizeResult.content().contains("\"voiceover\": true"));
    }

    @Test
    void providerAndModelConsistency() {
        ChatRequest request = new ChatRequest("any-capability", "any prompt");
        ChatResult result = provider.chat(request);

        assertEquals("stub", result.provider());
        assertEquals("local-dev-model", result.model());
    }

    private String extractScriptId(String jsonContent) {
        int scriptIdStart = jsonContent.indexOf("\"scriptId\": \"") + 13;
        if (scriptIdStart > 13) {
            int scriptIdEnd = jsonContent.indexOf('"', scriptIdStart);
            if (scriptIdEnd > scriptIdStart) {
                return jsonContent.substring(scriptIdStart, scriptIdEnd);
            }
        }
        return null;
    }
}
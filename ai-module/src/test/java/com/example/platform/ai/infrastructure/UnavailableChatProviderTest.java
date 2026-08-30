package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.ai.api.AiProviderUnavailableException;
import com.example.platform.ai.domain.ChatRequest;
import org.junit.jupiter.api.Test;

class UnavailableChatProviderTest {

    @Test
    void missingProviderIsTypedUnavailableAndNeverReturnsContent() {
        AiProviderUnavailableException failure = assertThrows(
                AiProviderUnavailableException.class,
                () -> new UnavailableChatProvider().chat(
                        new ChatRequest("timeline-edit", "prompt")));
        assertEquals("AI-503-002", failure.getErrorCode().code());
        assertEquals(503, failure.getErrorCode().status());
    }
}

package com.example.platform.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubChatProviderFailureSimulationTest {

    private StubChatProvider provider;

    @BeforeEach
    void setUp() {
        // Enable failure simulation with 100% failure rate for testing
        provider = new StubChatProvider(1.0, true, 0, 0L, new SimpleMeterRegistry());
    }

    @Test
    void failureSimulationThrowsRuntimeException() {
        ChatRequest request = new ChatRequest("test", "should fail");
        assertThrows(RuntimeException.class, () -> provider.chat(request));
    }

    @Test
    void failureMessageContainsExpectedPattern() {
        try {
            provider.chat(new ChatRequest("timeout_test", "should timeout"));
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            assertTrue(message.contains("Simulated") || message.contains("Operation interrupted"));
        }
    }

    @Test
    void lowFailureRateStillProducesSuccessMostly() {
        // Create a provider with very low failure rate
        StubChatProvider lowFailureProvider = new StubChatProvider(0.1, true, 3, 1000L, new SimpleMeterRegistry());

        int successes = 0;
        int totalAttempts = 50;

        for (int i = 0; i < totalAttempts; i++) {
            try {
                ChatResult result = lowFailureProvider.chat(new ChatRequest("test", "attempt " + i));
                assertNotNull(result.content());
                successes++;
            } catch (RuntimeException e) {
                // Expected occasional failures
                if (!e.getMessage().contains("Simulated")) {
                    throw e; // Unexpected error
                }
            }
        }

        // Should have mostly successes (at least 80%)
        assertTrue(successes >= (totalAttempts * 0.8), 
                   "Expected at least 80% success rate, got " + (successes * 100 / totalAttempts) + "%");
    }

    @Test
    void zeroFailureRateAlwaysSucceeds() {
        StubChatProvider noFailureProvider = new StubChatProvider(0.0, false, 3, 1000L, new SimpleMeterRegistry());

        for (int i = 0; i < 10; i++) {
            ChatResult result = noFailureProvider.chat(new ChatRequest("test", "iteration " + i));
            assertNotNull(result);
            assertEquals("stub", result.provider());
            assertEquals("local-dev-model", result.model());
            assertTrue(result.content().contains("scriptId"));
        }
    }

    @Test
    void failureSimulationDoesNotAffectNormalProvider() {
        // Test that normal provider (without failure simulation) is not affected
        StubChatProvider normalProvider = new StubChatProvider(0.0, false, 3, 1000L, new SimpleMeterRegistry());

        ChatResult result1 = normalProvider.chat(new ChatRequest("test", "first call"));
        ChatResult result2 = normalProvider.chat(new ChatRequest("test", "second call"));

        // Both should succeed
        assertNotNull(result1.content());
        assertNotNull(result2.content());

        // Content should contain expected fields (deterministic structure)
        assertTrue(result1.content().contains("\"scriptId\":"));
        assertTrue(result1.content().contains("\"capability\":"));
        assertTrue(result1.content().contains("\"prompt\":"));
        assertTrue(result1.content().contains("\"scenes\":"));
    }
}
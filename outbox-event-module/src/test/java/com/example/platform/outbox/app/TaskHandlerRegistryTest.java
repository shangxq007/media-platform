package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.outbox.coordination.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskHandlerRegistryTest {

    private TaskHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        MockProbeTaskHandler probe = new MockProbeTaskHandler();
        MockAsrTaskHandler asr = new MockAsrTaskHandler();
        registry = new TaskHandlerRegistry(List.of(probe, asr));
        registry.init();
    }

    @Test
    void shouldAutoRegisterHandlers() {
        assertEquals(2, registry.size());
    }

    @Test
    void shouldResolveProbeHandler() {
        TaskHandler handler = registry.resolve(TaskCapability.PROBE);
        assertNotNull(handler);
        assertTrue(handler instanceof MockProbeTaskHandler);
    }

    @Test
    void shouldResolveAsrHandler() {
        TaskHandler handler = registry.resolve(TaskCapability.ASR);
        assertNotNull(handler);
        assertTrue(handler instanceof MockAsrTaskHandler);
    }

    @Test
    void shouldReturnNullForUnknownCapability() {
        assertNull(registry.resolve(TaskCapability.EMBEDDING));
    }
}

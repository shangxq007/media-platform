package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.outbox.coordination.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TaskHandlerRegistry single-authority tests (R21E C-01).
 *
 * ONE_TASK_CAPABILITY_ONE_HANDLER_AUTHORITY_V1: a capability resolves to
 * exactly one active handler; duplicate registration fails closed regardless
 * of order; missing capability is never silently successful (dispatcher
 * fails the task — see PlatformTaskDispatcher).
 */
class TaskHandlerRegistryTest {

    private static final class ProbeHandler implements TaskHandler {
        @Override
        public TaskCapability capability() {
            return TaskCapability.PROBE;
        }

        @Override
        public void execute(TaskExecutionContext context) {
        }
    }

    private static final class AsrHandler implements TaskHandler {
        @Override
        public TaskCapability capability() {
            return TaskCapability.ASR;
        }

        @Override
        public void execute(TaskExecutionContext context) {
        }
    }

    // TEST-A: single ASR handler resolves exactly that handler
    @Test
    void singleAsrHandlerResolvesExactly() {
        AsrHandler asr = new AsrHandler();
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(asr));
        registry.init();
        assertSame(asr, registry.resolve(TaskCapability.ASR));
    }

    // TEST-B: single PROBE handler resolves exactly that handler
    @Test
    void singleProbeHandlerResolvesExactly() {
        ProbeHandler probe = new ProbeHandler();
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(probe));
        registry.init();
        assertSame(probe, registry.resolve(TaskCapability.PROBE));
    }

    // TEST-C: two ASR handlers → initialization fails closed
    @Test
    void duplicateAsrRegistrationFailsClosed() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(
                List.of(new AsrHandler(), new AsrHandler()));
        IllegalStateException ex = assertThrows(IllegalStateException.class, registry::init);
        assertTrue(ex.getMessage().contains("ASR"), "failure must name the capability");
        assertTrue(ex.getMessage().contains("duplicate"), "failure must name the duplicate");
    }

    // TEST-D: two PROBE handlers → initialization fails closed
    @Test
    void duplicateProbeRegistrationFailsClosed() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(
                List.of(new ProbeHandler(), new ProbeHandler()));
        assertThrows(IllegalStateException.class, registry::init);
    }

    // TEST-E: reversed registration order → still fails closed
    @Test
    void duplicateRegistrationFailsRegardlessOfOrder() {
        TaskHandlerRegistry a = new TaskHandlerRegistry(List.of(new AsrHandler(), new AsrHandler()));
        TaskHandlerRegistry b = new TaskHandlerRegistry(List.of(new AsrHandler(), new AsrHandler()));
        assertThrows(IllegalStateException.class, a::init);
        assertThrows(IllegalStateException.class, b::init);
    }

    // TEST-F: missing capability resolves to null → dispatcher fails the task
    // (PlatformTaskDispatcher: handler == null → coordinationService.failTask)
    @Test
    void missingCapabilityIsNeverASuccessfulResolution() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(new AsrHandler()));
        registry.init();
        assertNull(registry.resolve(TaskCapability.EMBEDDING),
                "missing capability must NOT resolve to a silent success handler");
    }

    @Test
    void sizeCountsRegisteredCapabilities() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(new AsrHandler(), new ProbeHandler()));
        registry.init();
        assertEquals(2, registry.size());
    }
}

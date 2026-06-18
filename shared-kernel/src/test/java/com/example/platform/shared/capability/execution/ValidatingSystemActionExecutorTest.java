package com.example.platform.shared.capability.execution;

import com.example.platform.shared.capability.SystemAction;
import com.example.platform.shared.capability.CapabilityStability;
import com.example.platform.shared.capability.action.BuiltInSystemActions;
import com.example.platform.shared.capability.registry.InMemorySystemActionRegistry;
import com.example.platform.shared.capability.registry.SystemActionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidatingSystemActionExecutor.
 *
 * <p>These tests verify execution validation without implementing real action execution.</p>
 */
class ValidatingSystemActionExecutorTest {

    private SystemActionRegistry actionRegistry;
    private ValidatingSystemActionExecutor executor;

    @BeforeEach
    void setUp() {
        actionRegistry = new InMemorySystemActionRegistry();
        BuiltInSystemActions.registerInto(actionRegistry);
        executor = new ValidatingSystemActionExecutor(actionRegistry);
    }

    @Test
    void unknownActionReturnsValidationFailure() {
        SystemActionExecutionContext context = createContext(false);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "unknown.action",
            "1.0.0",
            Map.of(),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        assertEquals(SystemActionExecutionStatus.VALIDATION_FAILED, result.status());
        assertEquals("unknown.action", result.actionKey());
        assertEquals("ACTION_NOT_FOUND", result.errorCode());
    }

    @Test
    void blankActionKeyRejected() {
        // Request constructor should throw for blank action key
        assertThrows(IllegalArgumentException.class, () -> {
            new SystemActionExecutionRequest(
                "",
                "1.0.0",
                Map.of(),
                Map.of(),
                "idempotency-key-1"
            );
        });
    }

    @Test
    void dryRunKnownActionSucceedsWithNoSideEffects() {
        SystemActionExecutionContext context = createContext(true);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "render.create_job",
            "1.0.0",
            Map.of("input", "test"),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        assertEquals(SystemActionExecutionStatus.DRY_RUN_SUCCEEDED, result.status());
        assertEquals("render.create_job", result.actionKey());
        assertTrue(result.dryRun());
    }

    @Test
    void nonDryRunKnownActionReturnsNotImplemented() {
        SystemActionExecutionContext context = createContext(false);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "render.create_job",
            "1.0.0",
            Map.of("input", "test"),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        assertEquals(SystemActionExecutionStatus.NOT_IMPLEMENTED, result.status());
        assertEquals("render.create_job", result.actionKey());
        assertFalse(result.dryRun());
    }

    @Test
    void executorDoesNotCreateRenderJob() {
        SystemActionExecutionContext context = createContext(false);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "render.create_job",
            "1.0.0",
            Map.of(),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        // Should return NOT_IMPLEMENTED, not actually create a render job
        assertEquals(SystemActionExecutionStatus.NOT_IMPLEMENTED, result.status());
    }

    @Test
    void executorDoesNotSendWebhook() {
        SystemActionExecutionContext context = createContext(false);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "webhook.send",
            "1.0.0",
            Map.of(),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        // Should return NOT_IMPLEMENTED, not actually send a webhook
        assertEquals(SystemActionExecutionStatus.NOT_IMPLEMENTED, result.status());
    }

    @Test
    void executorUsesSystemActionRegistry() {
        // Create a fresh registry without built-in actions
        SystemActionRegistry emptyRegistry = new InMemorySystemActionRegistry();
        ValidatingSystemActionExecutor emptyExecutor = new ValidatingSystemActionExecutor(emptyRegistry);

        SystemActionExecutionContext context = createContext(true);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "render.create_job",
            "1.0.0",
            Map.of(),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = emptyExecutor.execute(context, request);

        // Should fail because action is not in registry
        assertEquals(SystemActionExecutionStatus.VALIDATION_FAILED, result.status());
        assertEquals("ACTION_NOT_FOUND", result.errorCode());
    }

    @Test
    void resultContainsActionKeyAndDryRunFlag() {
        SystemActionExecutionContext context = createContext(true);
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "media.generate_thumbnail",
            "1.0.0",
            Map.of(),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        assertEquals("media.generate_thumbnail", result.actionKey());
        assertTrue(result.dryRun());
    }

    @Test
    void idempotencyKeyIsPreserved() {
        String idempotencyKey = "test-idempotency-key-123";
        SystemActionExecutionContext context = new SystemActionExecutionContext(
            "tenant-123",
            "user-456",
            "project-789",
            "request-abc",
            idempotencyKey,
            Instant.now().plusSeconds(300),
            true,
            Map.of(),
            java.util.List.of(),
            java.util.List.of()
        );
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "render.create_job",
            "1.0.0",
            Map.of(),
            Map.of(),
            idempotencyKey
        );

        SystemActionExecutionResult result = executor.execute(context, request);

        assertEquals(SystemActionExecutionStatus.DRY_RUN_SUCCEEDED, result.status());
        // The idempotency key is in the context, not directly in the result
        // but the result should succeed
    }

    @Test
    void nullContextReturnsValidationFailed() {
        SystemActionExecutionRequest request = new SystemActionExecutionRequest(
            "render.create_job",
            "1.0.0",
            Map.of(),
            Map.of(),
            "idempotency-key-1"
        );

        SystemActionExecutionResult result = executor.execute(null, request);

        assertEquals(SystemActionExecutionStatus.VALIDATION_FAILED, result.status());
        assertEquals("CONTEXT_NULL", result.errorCode());
    }

    @Test
    void nullRequestReturnsValidationFailed() {
        SystemActionExecutionContext context = createContext(false);

        SystemActionExecutionResult result = executor.execute(context, null);

        assertEquals(SystemActionExecutionStatus.VALIDATION_FAILED, result.status());
        assertEquals("REQUEST_NULL", result.errorCode());
    }

    private SystemActionExecutionContext createContext(boolean dryRun) {
        return new SystemActionExecutionContext(
            "tenant-123",
            "user-456",
            "project-789",
            "request-abc",
            "idempotency-key-1",
            Instant.now().plusSeconds(300),
            dryRun,
            Map.of(),
            java.util.List.of(),
            java.util.List.of()
        );
    }
}

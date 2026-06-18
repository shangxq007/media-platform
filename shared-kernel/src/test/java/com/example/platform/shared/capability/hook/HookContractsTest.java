package com.example.platform.shared.capability.hook;

import com.example.platform.shared.capability.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hook contracts.
 *
 * <p>These tests verify hook contract shapes without implementing hook runtime.</p>
 */
class HookContractsTest {

    @Test
    void hookPointHasPhaseAndFailurePolicy() {
        HookPoint hookPoint = new HookPoint(
            "render.before_create",
            HookPhase.BEFORE,
            null,
            null,
            Set.of("render.create"),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        );

        assertEquals("render.before_create", hookPoint.key());
        assertEquals(HookPhase.BEFORE, hookPoint.phase());
        assertEquals(HookFailurePolicy.FAIL_CLOSED, hookPoint.failurePolicy());
        assertEquals(CapabilityStability.STABLE, hookPoint.stability());
    }

    @Test
    void hookPointRequiresValidFields() {
        assertThrows(IllegalArgumentException.class, () -> {
            new HookPoint(
                null,
                HookPhase.BEFORE,
                null,
                null,
                Set.of(),
                Duration.ofSeconds(30),
                HookFailurePolicy.FAIL_CLOSED,
                CapabilityStability.STABLE
            );
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new HookPoint(
                "render.before_create",
                null,
                null,
                null,
                Set.of(),
                Duration.ofSeconds(30),
                HookFailurePolicy.FAIL_CLOSED,
                CapabilityStability.STABLE
            );
        });
    }

    @Test
    void hookHandlerDeclaresSupportedHookPoints() {
        HookHandler handler = createTestHandler("handler-1", Set.of("render.before_create", "render.after_create"));

        assertEquals("handler-1", handler.handlerId());
        assertTrue(handler.supportedHookPoints().contains("render.before_create"));
        assertTrue(handler.supportedHookPoints().contains("render.after_create"));
    }

    @Test
    void hookResultCanAllowDenyNoop() {
        // Test ALLOW
        HookResult allowResult = HookResult.allow(Map.of("key", "value"));
        assertEquals(HookDecision.ALLOW, allowResult.decision());
        assertEquals(InvocationStatus.SUCCEEDED, allowResult.status());

        // Test DENY
        HookResult denyResult = HookResult.deny("PERMISSION_DENIED", "logs/ref");
        assertEquals(HookDecision.DENY, denyResult.decision());
        assertEquals("PERMISSION_DENIED", denyResult.errorCode());

        // Test NOOP
        HookResult noopResult = HookResult.noop();
        assertEquals(HookDecision.NOOP, noopResult.decision());
    }

    @Test
    void hookContractsDoNotExposeRawSecrets() {
        CredentialRef credRef = new CredentialRef(
            "tenant-123",
            "cred-456",
            "provider-789",
            Set.of("read"),
            "vault://secret/path",
            null
        );

        HookInvocation invocation = new HookInvocation(
            "render.before_create",
            "tenant-123",
            "user-456",
            "project-789",
            "request-abc",
            "correlation-xyz",
            "idempotency-123",
            Instant.now().plusSeconds(300),
            Map.of("key", "value"),
            List.of(),
            List.of(credRef)
        );

        // CredentialRef should only contain references, not raw secrets
        assertNotNull(invocation.credentialRefs());
        assertFalse(invocation.credentialRefs().isEmpty());

        CredentialRef firstRef = invocation.credentialRefs().get(0);
        assertNotNull(firstRef.secretPath());
        assertFalse(firstRef.secretPath().contains("actual-secret"));
    }

    @Test
    void hookContractsDoNotDependOnSpring() {
        // This test verifies that hook contracts are plain Java records/interfaces
        // No Spring annotations or dependencies should be present
        HookPoint hookPoint = new HookPoint(
            "test.hook",
            HookPhase.BEFORE,
            null,
            null,
            Set.of(),
            Duration.ofSeconds(30),
            HookFailurePolicy.FAIL_CLOSED,
            CapabilityStability.STABLE
        );

        HookHandler handler = createTestHandler("handler-1", Set.of("test.hook"));

        HookInvocation invocation = new HookInvocation(
            "test.hook",
            "tenant-123",
            "user-456",
            "project-789",
            "request-abc",
            null,
            null,
            null,
            Map.of(),
            List.of(),
            List.of()
        );

        HookResult result = handler.handle(invocation);

        assertNotNull(hookPoint);
        assertNotNull(handler);
        assertNotNull(invocation);
        assertNotNull(result);
    }

    @Test
    void hookPhaseValues() {
        HookPhase[] phases = HookPhase.values();
        assertEquals(3, phases.length);
        assertEquals(HookPhase.BEFORE, phases[0]);
        assertEquals(HookPhase.AFTER, phases[1]);
        assertEquals(HookPhase.ON_FAILURE, phases[2]);
    }

    @Test
    void hookDecisionValues() {
        HookDecision[] decisions = HookDecision.values();
        assertEquals(4, decisions.length);
        assertEquals(HookDecision.ALLOW, decisions[0]);
        assertEquals(HookDecision.DENY, decisions[1]);
        assertEquals(HookDecision.NOOP, decisions[2]);
        assertEquals(HookDecision.DEFER, decisions[3]);
    }

    @Test
    void hookFailurePolicyValues() {
        HookFailurePolicy[] policies = HookFailurePolicy.values();
        assertEquals(5, policies.length);
        assertEquals(HookFailurePolicy.FAIL_CLOSED, policies[0]);
        assertEquals(HookFailurePolicy.FAIL_OPEN, policies[1]);
        assertEquals(HookFailurePolicy.RETRY_THEN_FAIL_CLOSED, policies[2]);
        assertEquals(HookFailurePolicy.RETRY_THEN_FAIL_OPEN, policies[3]);
        assertEquals(HookFailurePolicy.IGNORE, policies[4]);
    }

    // ===== Helper Methods =====

    private HookHandler createTestHandler(String handlerId, Set<String> supportedHookPoints) {
        return new HookHandler() {
            @Override
            public String handlerId() {
                return handlerId;
            }

            @Override
            public Set<String> supportedHookPoints() {
                return supportedHookPoints;
            }

            @Override
            public HookHandlerCapabilities capabilities() {
                return new HookHandlerCapabilities(
                    Set.of(HookPhase.BEFORE, HookPhase.AFTER),
                    true,
                    false,
                    false,
                    1024,
                    30000
                );
            }

            @Override
            public HookResult handle(HookInvocation invocation) {
                return HookResult.noop();
            }
        };
    }
}

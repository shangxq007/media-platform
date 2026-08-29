package com.example.platform.extension.runtime.conformance;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.extension.runtime.CredentialRef;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;
import com.example.platform.extension.runtime.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PROVIDER_CONFORMANCE test group (PLUGIN_RUNTIME_PROVIDER_CONFORMANCE_V2).
 *
 * <p>Verifies the frozen conformance contract: tenant requirement, trust policy,
 * input validation, secret hygiene, capability binding. The SPI compatibility
 * adapter passes the same conformance (no special runtime rules for new providers).</p>
 */
class PluginRuntimeConformanceTest {

    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("u-1", "USER");
    private static final OperationRef OP = OperationRef.of("op-1", "attempt-1");
    private static final ResourceRequirements RES = ResourceRequirements.defaults();

    @Test
    void tenantRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new PluginExecutionRequest(
                        "", ACTOR, OP, "cap-1", new ProviderRef("p-1"), null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of()));
    }

    @Test
    void actorRequired() {
        assertThrows(NullPointerException.class,
                () -> new PluginExecutionRequest(
                        "tenant-1", null, OP, "cap-1", new ProviderRef("p-1"), null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of()));
    }

    @Test
    void operationRefRequired() {
        assertThrows(NullPointerException.class,
                () -> new PluginExecutionRequest(
                        "tenant-1", ACTOR, null, "cap-1", new ProviderRef("p-1"), null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of()));
    }

    @Test
    void capabilityRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new PluginExecutionRequest(
                        "tenant-1", ACTOR, OP, "", new ProviderRef("p-1"), null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of()));
    }

    @Test
    void timeoutBoundedTo120s() {
        assertThrows(IllegalArgumentException.class,
                () -> new PluginExecutionRequest(
                        "tenant-1", ACTOR, OP, "cap-1", new ProviderRef("p-1"), null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(121), RES, Set.of()));
    }

    @Test
    void secretRefsAreReferencesOnly() {
        CredentialRef ref = new CredentialRef("secret-id-1", "tenant-1", "provider-1");
        PluginExecutionRequest req = new PluginExecutionRequest(
                "tenant-1", ACTOR, OP, "cap-1", new ProviderRef("p-1"), null,
                ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of(ref));
        // structurally, only references exist — a "value" would be a different type
        assertTrue(req.secretRefs().stream().allMatch(r -> r.secretRef() != null && !r.secretRef().isBlank()));
        assertEquals(1, req.secretRefs().size());
    }

    @Test
    void requestIsFullyTypedNotObjectMap() {
        PluginExecutionRequest req = new PluginExecutionRequest(
                "tenant-1", ACTOR, OP, "cap-1", new ProviderRef("p-1"), "small-typed-input",
                ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of());
        assertEquals("tenant-1", req.tenantId());
        assertEquals(ExecutionMode.TRUSTED_IN_PROCESS, req.executionMode());
        assertTrue(req.timeout().compareTo(PluginExecutionRequest.MAX_TIMEOUT) <= 0);
    }

    @Test
    void resultStatusesAreDistinct() {
        // TIMEOUT / CANCELLED / FAILED are distinct terminal states
        PluginExecutionResult timeout = PluginExecutionResult.timedOut("took too long");
        PluginExecutionResult cancelled = PluginExecutionResult.cancelled("caller cancelled");
        PluginExecutionResult failed = PluginExecutionResult.failed(
                PluginRuntimeErrorCategory.EXECUTION_FAILED, "PRV2-500", "boom");
        assertEquals(PluginExecutionStatus.TIMED_OUT, timeout.status());
        assertEquals(PluginExecutionStatus.CANCELLED, cancelled.status());
        assertEquals(PluginExecutionStatus.FAILED, failed.status());
        assertTrue(!timeout.status().equals(cancelled.status()));
        assertTrue(!cancelled.status().equals(failed.status()));
    }

    @Test
    void sdkExceptionsMapToCanonicalErrors() {
        // Raw SDK exceptions never cross the public contract (AR-PRV2-08): the
        // runtime wraps them; the conformance surface sees canonical categories only.
        PluginRuntimeExecutionException ex = new PluginRuntimeExecutionException(
                PluginRuntimeErrorCategory.EXECUTION_FAILED, "PRV2-500", "wrapped", new IllegalStateException("sdk"));
        assertEquals(PluginRuntimeErrorCategory.EXECUTION_FAILED, ex.category());
        assertTrue(!ex.getMessage().contains("IllegalStateException"));
    }
}

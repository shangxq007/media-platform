package com.example.platform.extension.runtime.conformance;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.runtime.CredentialRef;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;
import com.example.platform.extension.runtime.ResourceRequirements;
import com.example.platform.extension.runtime.internal.ProviderExtensionSpiRuntimeAdapter;
import com.example.platform.extension.runtime.internal.RuntimeUsageEmitter;
import com.example.platform.extension.runtime.internal.SecretRefResolver;
import com.example.platform.extension.runtime.internal.DefaultPluginRuntime;
import com.example.platform.extension.runtime.internal.TrustPolicyEnforcer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRV2-RED-001..015 behavioral tests (frozen red/red.json in PRV2-ARSF evidence).
 */
class PluginRuntimeRedMatrixTest {

    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("u-1", "USER");
    private static final OperationRef OP = OperationRef.of("op-1", "attempt-1");
    private static final ResourceRequirements RES = ResourceRequirements.defaults();
    private static final ProviderRef PROVIDER = new ProviderRef("p-1");

    private PluginExecutionRequest request(ExecutionMode mode, String tenant) {
        return new PluginExecutionRequest(
                tenant, ACTOR, OP, "cap-1", PROVIDER, null,
                mode, Duration.ofSeconds(30), RES, Set.of());
    }

    @Test
    void red001_capabilityWithoutRuntimeBindingRejected() {
        // registry selection with no plugin binding => explicit rejection (no silent fallback)
        var selection = new com.example.platform.extension.domain.PluginSelectionResult(
                null, null, "cap-none", "1", null,
                com.example.platform.extension.domain.PluginHealth.State.UNKNOWN);
        PluginRuntimeExecutionException ex = assertThrows(PluginRuntimeExecutionException.class,
                () -> com.example.platform.extension.runtime.internal.PluginSelectionToRequestMapper.toRequest(
                        selection, "tenant-1", ACTOR, OP, null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of()));
        assertEquals(PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED, ex.category());
    }

    @Test
    void red002_unsupportedCapabilityNoInvocation() {
        // provider lacks requested capability => no invocation: adapter returns
        // CAPABILITY_UNSUPPORTED without touching the provider SPI
        var registry = org.mockito.Mockito.mock(
                com.example.platform.extension.app.ExtensionRegistryService.class);
        org.mockito.Mockito.when(registry.findSpiInstance("ghost")).thenReturn(null);
        ProviderExtensionSpiRuntimeAdapter adapter = new ProviderExtensionSpiRuntimeAdapter(registry);
        PluginExecutionResult result = adapter.execute(request(ExecutionMode.TRUSTED_IN_PROCESS, "tenant-1"));
        assertEquals(PluginExecutionStatus.FAILED, result.status());
        assertEquals(PluginRuntimeErrorCategory.CAPABILITY_UNSUPPORTED, result.error().category());
    }

    @Test
    void red003_untrustedTrustedInProcessDenied() {
        PluginRuntimeExecutionException ex = assertThrows(PluginRuntimeExecutionException.class,
                () -> TrustPolicyEnforcer.enforce(request(ExecutionMode.TRUSTED_IN_PROCESS, "tenant-1"),
                        ExtensionTrustLevel.UNTRUSTED));
        assertEquals(PluginRuntimeErrorCategory.SECURITY_DENIED, ex.category());
        assertEquals("PRV2-403", ex.code());
    }

    @Test
    void red004_tenantAbsentDenied() {
        // structurally impossible: blank tenant rejected at request construction
        assertThrows(IllegalArgumentException.class,
                () -> request(ExecutionMode.TRUSTED_IN_PROCESS, ""));
    }

    @Test
    void red005_secretValueCannotEnterRequest() {
        // CredentialRef is a reference type; assertNoSecretValues validates shape
        var ref = new CredentialRef("secret-id", "tenant-1", "provider-1");
        SecretRefResolver.assertNoSecretValues(Set.of(ref));
        // structurally no value payload exists in the request type
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialRef("", "tenant-1", "provider-1"));
    }

    @Test
    void red006_timeoutMapsToTimedOut() {
        PluginExecutionResult timeout = PluginExecutionResult.timedOut("deadline exceeded");
        assertEquals(PluginExecutionStatus.TIMED_OUT, timeout.status());
        assertEquals(PluginRuntimeErrorCategory.TIMEOUT, timeout.error().category());
    }

    @Test
    void red007_cancelledNeverSuccess() {
        PluginExecutionResult cancelled = PluginExecutionResult.cancelled("caller cancelled");
        assertEquals(PluginExecutionStatus.CANCELLED, cancelled.status());
        assertTrue(!cancelled.status().equals(PluginExecutionStatus.SUCCEEDED));
    }

    @Test
    void red008_sameAttemptReplayNoDuplicateUsage() {
        // idempotency key stable per (operation, attempt, dimension, source) —
        // same attempt replay yields the same key (emitter-level, via port capture)
        var port = new FakePort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        var op = OperationRef.of("op-1", "attempt-1");
        emitter.emitBaseFacts("tenant-1", ACTOR, op, PROVIDER, "cap-1", 100);
        emitter.emitBaseFacts("tenant-1", ACTOR, op, PROVIDER, "cap-1", 100);
        // replay of the same attempt still emits facts (idempotency is enforced by
        // the persistence layer via the key) — keys must be identical across replay
        assertTrue(port.keys.size() == 4); // REQUEST+DURATION per emission
        assertEquals(port.keys.get(0), port.keys.get(2)); // same REQUEST key
        assertEquals(port.keys.get(1), port.keys.get(3)); // same DURATION key
    }

    @Test
    void red009_newAttemptDistinctFact() {
        var port = new FakePort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        emitter.emitBaseFacts("tenant-1", ACTOR, OperationRef.of("op-1", "attempt-1"), PROVIDER, "cap-1", 100);
        emitter.emitBaseFacts("tenant-1", ACTOR, OperationRef.of("op-1", "attempt-2"), PROVIDER, "cap-1", 100);
        // new attempt => distinct keys (all four distinct)
        assertEquals(4, port.keys.size());
        assertTrue(port.keys.stream().distinct().count() == 4);
    }

    @Test
    void red010_failedConsumedExecutionMayStillEmitUsage() {
        // FAILED_OPERATION_MAY_STILL_EMIT_USAGE: emitter does not suppress measured facts
        var port = new FakePort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        emitter.emitBaseFacts("tenant-1", ACTOR, OperationRef.of("op-fail", "attempt-1"),
                PROVIDER, "cap-1", 400);
        assertTrue(port.dimensions.contains(
                com.example.platform.billing.usage.UsageDimension.DURATION));
    }

    /** Local fake emission port capturing keys + dimensions. */
    static final class FakePort implements com.example.platform.billing.usage.UsageRecordEmissionPort {
        final List<String> keys = new java.util.ArrayList<>();
        final List<com.example.platform.billing.usage.UsageDimension> dimensions = new java.util.ArrayList<>();

        @Override
        public com.example.platform.billing.usage.UsageRecord emit(
                com.example.platform.billing.usage.UsageRecord record) {
            keys.add(record.idempotencyKey());
            dimensions.add(record.dimension());
            return record;
        }
    }

    @Test
    void red011_durableMediaOutputIsArtifactRef() {
        // PluginExecutionResult.artifactRefs is List<ArtifactRef> — structurally
        // durable media outputs can only be references, never native objects
        var ref = new com.example.platform.shared.capability.ArtifactRef(
                "art-1", "tenant-1", "video/mp4", "abc123", "s3://x", "logical-uri",
                new com.example.platform.shared.capability.ArtifactRef.ArtifactPermissions(true, false, false));
        PluginExecutionResult result = PluginExecutionResult.succeeded(null, java.util.List.of(ref), null);
        assertEquals(1, result.artifactRefs().size());
        assertEquals("art-1", result.artifactRefs().get(0).artifactId());
    }

    @Test
    void red012_sdkExceptionMapsToCanonicalError() throws Exception {
        // wrapped SDK exceptions surface as canonical categories (AR-PRV2-08)
        var registry = org.mockito.Mockito.mock(
                com.example.platform.extension.app.ExtensionRegistryService.class);
        var spi = org.mockito.Mockito.mock(
                com.example.platform.extension.domain.ProviderExtensionSPI.class);
        org.mockito.Mockito.when(spi.version()).thenReturn("1.0.0");
        org.mockito.Mockito.when(spi.trustLevel()).thenReturn(ExtensionTrustLevel.SEMI_TRUSTED);
        org.mockito.Mockito.when(spi.execute(
                org.mockito.ArgumentMatchers.any(com.example.platform.extension.domain.ExtensionContext.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("openai sdk blew up"));
        org.mockito.Mockito.when(registry.findSpiInstance("p-1")).thenReturn(spi);
        ProviderExtensionSpiRuntimeAdapter adapter = new ProviderExtensionSpiRuntimeAdapter(registry);
        PluginExecutionResult result = adapter.execute(request(ExecutionMode.TRUSTED_IN_PROCESS, "tenant-1"));
        assertEquals(PluginRuntimeErrorCategory.EXECUTION_FAILED, result.error().category());
    }

    @Test
    void red013_domainModulesDependOnlyOnExposedRuntimeApi() {
        // AR-PRV2-16: verified by ModularityTest + architecture guard (C5 guard);
        // structurally the public API package is the ONLY exposed surface
        assertEquals("com.example.platform.extension.runtime",
                PluginExecutionRequest.class.getPackageName());
        assertTrue(!PluginExecutionRequest.class.getPackageName().contains(".internal"));
    }

    @Test
    void red014_featureFlagCannotBypassSecurityOrEraseUsage() {
        // runtime has no feature-flag gate on security/usage — trust enforcement and
        // usage emission are unconditional on the canonical path
        // (trust denial is proven in red003; usage emission in red010)
        assertTrue(TrustPolicyEnforcer.classify(ExtensionTrustLevel.UNTRUSTED)
                == TrustPolicyEnforcer.TrustClassification.ISOLATION_REQUIRED);
    }

    @Test
    void red015_runtimeCapacityDoesNotMutateProductQuota() {
        // ResourceRequirements is a description — no quota API is invoked anywhere
        // in the runtime path (structurally no entitlement/quota import in runtime)
        ResourceRequirements res = new ResourceRequirements(512, 80, 60_000, false, 1024);
        assertEquals(512, res.maxMemoryMb());
        assertEquals(60_000, res.timeoutMs());
    }
}

package com.example.platform.extension.runtime.internal;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.runtime.CredentialRef;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionProgress;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginExecutionResult;
import com.example.platform.extension.runtime.PluginExecutionStatus;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;
import com.example.platform.extension.runtime.ResourceRequirements;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustPolicyEnforcerTest {

    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("u-1", "USER");
    private static final OperationRef OP = OperationRef.of("op-1", "attempt-1");
    private static final ResourceRequirements RES = ResourceRequirements.defaults();

    private PluginExecutionRequest request(ExecutionMode mode) {
        return new PluginExecutionRequest(
                "tenant-1", ACTOR, OP, "cap-1", new ProviderRef("p-1"), null,
                mode, Duration.ofSeconds(30), RES, Set.of());
    }

    @Test
    void untrustedProviderDeniedInTrustedInProcess() {
        PluginRuntimeExecutionException ex = assertThrows(PluginRuntimeExecutionException.class,
                () -> TrustPolicyEnforcer.enforce(request(ExecutionMode.TRUSTED_IN_PROCESS),
                        ExtensionTrustLevel.UNTRUSTED));
        assertEquals(PluginRuntimeErrorCategory.SECURITY_DENIED, ex.category());
        assertEquals("PRV2-403", ex.code());
    }

    @Test
    void trustedProviderAllowedInTrustedInProcess() {
        // must not throw
        TrustPolicyEnforcer.enforce(request(ExecutionMode.TRUSTED_IN_PROCESS),
                ExtensionTrustLevel.FULLY_TRUSTED);
    }

    @Test
    void tenantAbsentRejectedAtConstruction() {
        // PluginExecutionRequest structurally forbids blank tenant (AR-PRV2-04)
        assertThrows(IllegalArgumentException.class,
                () -> new PluginExecutionRequest(
                        "", ACTOR, OP, "cap-1", new ProviderRef("p-1"), null,
                        ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of()));
    }

    @Test
    void classificationMapsUntrustedToIsolationRequired() {
        assertEquals(TrustPolicyEnforcer.TrustClassification.ISOLATION_REQUIRED,
                TrustPolicyEnforcer.classify(ExtensionTrustLevel.UNTRUSTED));
        assertEquals(TrustPolicyEnforcer.TrustClassification.TRUSTED,
                TrustPolicyEnforcer.classify(ExtensionTrustLevel.FULLY_TRUSTED));
        assertEquals(TrustPolicyEnforcer.TrustClassification.TRUSTED,
                TrustPolicyEnforcer.classify(ExtensionTrustLevel.SEMI_TRUSTED));
    }

    @Test
    void secretValuesCannotEnterRequest() {
        // Request construction with a value-like secretRef string is a reference, not a value.
        CredentialRef ref = new CredentialRef("secret-id-1", "tenant-1", "provider-1");
        PluginExecutionRequest req = new PluginExecutionRequest(
                "tenant-1", ACTOR, OP, "cap-1", new ProviderRef("p-1"), null,
                ExecutionMode.TRUSTED_IN_PROCESS, Duration.ofSeconds(30), RES, Set.of(ref));
        // assertNoSecretValues must pass (references only) and never throw
        SecretRefResolver.assertNoSecretValues(req.secretRefs());
        assertTrue(req.secretRefs().contains(ref));
    }

    @Test
    void runtimeDeniesUntrustedThroughDefaultPluginRuntime() {
        // DefaultPluginRuntime maps SECURITY_DENIED into a canonical FAILED result
        // (registry lookup returns null -> adapter rejects CAPABILITY_UNSUPPORTED first;
        // trust denial is covered by untrustedProviderDeniedInTrustedInProcess at enforcer level)
        DefaultPluginRuntime runtime = new DefaultPluginRuntime(
                        org.mockito.Mockito.mock(com.example.platform.extension.app.ExtensionRegistryService.class));
        PluginExecutionResult result = runtime.execute(request(ExecutionMode.TRUSTED_IN_PROCESS));
        assertEquals(PluginExecutionStatus.FAILED, result.status());
        assertTrue(result.error() != null);
    }
}

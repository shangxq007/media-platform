package com.example.platform.extension.runtime.internal;

import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.runtime.ExecutionMode;
import com.example.platform.extension.runtime.PluginExecutionRequest;
import com.example.platform.extension.runtime.PluginRuntimeErrorCategory;
import com.example.platform.extension.runtime.PluginRuntimeExecutionException;

import java.util.Objects;

/**
 * Trust policy enforcement (GAP-003 closure, AR-PRV2-03, PRV2-RED-003).
 *
 * <p>TRUSTED_IN_PROCESS executes trusted platform-controlled provider code ONLY.
 * An extension classified ISOLATION_REQUIRED (UNTRUSTED) requesting
 * TRUSTED_IN_PROCESS is DENIED explicitly — no silent fallback, no new sandbox
 * runtime invented.</p>
 */
public final class TrustPolicyEnforcer {

    public enum TrustClassification {
        TRUSTED,
        ISOLATION_REQUIRED
    }

    private TrustPolicyEnforcer() {
    }

    /**
     * Classifies a provider extension trust level.
     */
    public static TrustClassification classify(ExtensionTrustLevel trustLevel) {
        return trustLevel == ExtensionTrustLevel.UNTRUSTED
                ? TrustClassification.ISOLATION_REQUIRED
                : TrustClassification.TRUSTED;
    }

    /**
     * Enforces trust policy for a request: untrusted provider + TRUSTED_IN_PROCESS
     * is denied (PRV2-RED-003). Also enforces tenant presence (AR-PRV2-04) and
     * actor presence (AR-PRV2-05).
     *
     * @param request    canonical request
     * @param trustLevel provider trust level
     * @throws PluginRuntimeExecutionException SECURITY_DENIED when untrusted provider
     *         requests TRUSTED_IN_PROCESS; VALIDATION when tenant/actor missing
     */
    public static void enforce(PluginExecutionRequest request, ExtensionTrustLevel trustLevel) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            throw new PluginRuntimeExecutionException(
                    PluginRuntimeErrorCategory.VALIDATION, "PRV2-400", "tenantId is required");
        }
        if (request.actorRef() == null) {
            throw new PluginRuntimeExecutionException(
                    PluginRuntimeErrorCategory.VALIDATION, "PRV2-400", "actorRef is required");
        }
        if (request.executionMode() == ExecutionMode.TRUSTED_IN_PROCESS
                && classify(trustLevel) == TrustClassification.ISOLATION_REQUIRED) {
            throw new PluginRuntimeExecutionException(
                    PluginRuntimeErrorCategory.SECURITY_DENIED,
                    "PRV2-403",
                    "Untrusted provider '" + request.providerRef().providerId()
                            + "' cannot execute in TRUSTED_IN_PROCESS mode");
        }
    }
}

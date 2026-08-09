package com.example.platform.extension.runtime;

import java.util.Objects;

/**
 * Reference to a provider credential — NEVER a secret value (PLUGIN_RUNTIME_SECRET_INJECTION_POLICY_V1).
 *
 * <p>The canonical runtime request carries only this reference. The actual secret
 * value is resolved at the execution boundary, bound to tenant + provider/capability
 * context, and must never enter outbox, UsageRecord, Artifact provenance, logs or
 * exception messages (AR-PRV2-06, PRV2-RED-005).</p>
 *
 * @param secretRef     stable secret/credential reference identifier (required)
 * @param tenantId      tenant scope (required — SYSTEM does not bypass tenant scope)
 * @param providerScope provider/capability scope (required)
 */
public record CredentialRef(String secretRef, String tenantId, String providerScope) {

    public CredentialRef {
        Objects.requireNonNull(secretRef, "secretRef must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(providerScope, "providerScope must not be null");
        if (secretRef.isBlank() || tenantId.isBlank() || providerScope.isBlank()) {
            throw new IllegalArgumentException("CredentialRef fields must not be blank");
        }
    }
}

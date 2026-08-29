package com.example.platform.extension.runtime;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical plugin execution request (frozen PRV2-ADR-006).
 *
 * <p>Typed contract. Structurally FORBIDDEN: SecurityContext, Authentication, JWT,
 * OAuth token, API key value, secret value, Servlet request, provider SDK request,
 * arbitrary Object, unbounded Map&lt;String,Object&gt;.</p>
 *
 * <p>Secrets are represented ONLY via {@link CredentialRef} references
 * (PLUGIN_RUNTIME_SECRET_INJECTION_POLICY_V1, AR-PRV2-06).</p>
 *
 * @param tenantId               tenant (REQUIRED — all executions, even system-triggered)
 * @param actorRef               canonical actor (REQUIRED — USER/API_KEY_PRINCIPAL/SYSTEM only)
 * @param operationRef           operation + attempt (REQUIRED — idempotency anchor)
 * @param capability             requested capability (REQUIRED)
 * @param providerRef            selected provider (REQUIRED)
 * @param input                  typed small input payload (bounded; null allowed for no-input providers)
 * @param executionMode          execution mode (REQUIRED)
 * @param timeout                bounded timeout (REQUIRED, non-negative, capped at 120s)
 * @param resourceRequirements   resource requirements (REQUIRED)
 * @param secretRefs             credential references only (empty when none)
 */
public record PluginExecutionRequest(
        String tenantId,
        CanonicalActorRef actorRef,
        OperationRef operationRef,
        String capability,
        ProviderRef providerRef,
        Object input,
        ExecutionMode executionMode,
        Duration timeout,
        ResourceRequirements resourceRequirements,
        Set<CredentialRef> secretRefs) {

    public static final Duration MAX_TIMEOUT = Duration.ofSeconds(120);

    public PluginExecutionRequest {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(actorRef, "actorRef must not be null");
        Objects.requireNonNull(operationRef, "operationRef must not be null");
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(providerRef, "providerRef must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(resourceRequirements, "resourceRequirements must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (capability.isBlank()) {
            throw new IllegalArgumentException("capability must not be blank");
        }
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout exceeds max of 120s");
        }
        if (secretRefs == null) {
            secretRefs = Set.of();
        }
    }
}

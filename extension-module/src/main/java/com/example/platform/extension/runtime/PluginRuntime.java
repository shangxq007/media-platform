package com.example.platform.extension.runtime;

/**
 * Canonical Plugin Runtime V2 execution authority (frozen PRV2-ADR-001/003).
 *
 * <p>PluginRuntime is the SINGULAR effect-execution authority. Domain modules
 * depend on this interface (via the {@code extension::runtime} named interface),
 * never on runtime implementation internals (AR-PRV2-16).</p>
 *
 * <p>Execution is synchronous in the TRUSTED_IN_PROCESS foundation scope.
 * Retry is NOT owned by the runtime (PLUGIN_RUNTIME_RETRY_OWNERSHIP_V1).</p>
 */
public interface PluginRuntime {

    /**
     * Executes a plugin execution request.
     *
     * @param request canonical typed request (tenant/actor/operation/capability required)
     * @return canonical typed result
     * @throws PluginRuntimeExecutionException on pre-execution rejection
     *         (VALIDATION, CAPABILITY_UNSUPPORTED, SECURITY_DENIED, RESOURCE_UNAVAILABLE)
     */
    PluginExecutionResult execute(PluginExecutionRequest request) throws PluginRuntimeExecutionException;

    /**
     * PMPR-S1-CRR1: canonical convenience execution for provider effects.
     * Single authority remains {@link #execute(PluginExecutionRequest)}; this default
     * builds the canonical request (TRUSTED_IN_PROCESS) and delegates.
     *
     * @param providerId  registered provider binding key
     * @param tenantId    tenant context
     * @param actorId     acting user/system id
     * @param operationId correlation operation id
     * @param input       provider input payload
     * @return canonical result (never a raw provider exception)
     * @throws PluginRuntimeExecutionException on pre-execution rejection
     */
    default PluginExecutionResult executeProvider(String providerId, String tenantId, String actorId,
                                                  String operationId, Object input)
            throws PluginRuntimeExecutionException {
        return execute(new PluginExecutionRequest(
                tenantId,
                new com.example.platform.billing.usage.CanonicalActorRef(actorId, "SYSTEM"),
                com.example.platform.billing.usage.OperationRef.of(operationId),
                "provider-effect",
                new com.example.platform.billing.usage.ProviderRef(providerId),
                input,
                ExecutionMode.TRUSTED_IN_PROCESS,
                java.time.Duration.ofSeconds(30),
                ResourceRequirements.defaults(),
                java.util.Set.of()));
    }
}

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
}

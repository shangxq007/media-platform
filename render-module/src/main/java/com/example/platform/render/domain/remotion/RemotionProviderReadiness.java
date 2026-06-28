package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Remotion provider readiness diagnostics — combines runtime availability
 * with policy and provider status.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * @param documentGenerationReady whether document generation is allowed
 * @param runtimeToolsAvailable   whether runtime tools are detected
 * @param executionReady          whether execution is ready (false in v0)
 * @param productionEligible      whether provider is production eligible (false)
 * @param autoDispatch            whether auto-dispatch is enabled (false)
 * @param providerStatus          provider status (POC/SPIKE)
 * @param blockedReasons          reasons why execution is blocked
 * @param runtimeAvailability     underlying runtime availability
 */
public record RemotionProviderReadiness(
        boolean documentGenerationReady,
        boolean runtimeToolsAvailable,
        boolean executionReady,
        boolean productionEligible,
        boolean autoDispatch,
        String providerStatus,
        List<String> blockedReasons,
        RemotionRuntimeAvailability runtimeAvailability) {

    /**
     * Build readiness from runtime availability.
     */
    public static RemotionProviderReadiness from(RemotionRuntimeAvailability runtime) {
        boolean toolsAvailable = runtime.allToolsAvailable();

        java.util.List<String> blocked = new java.util.ArrayList<>();
        blocked.add("Execution disabled by policy");
        blocked.add("Provider status: POC (not production eligible)");
        blocked.add("autoDispatch: false");
        if (!toolsAvailable) {
            blocked.add("Runtime tools missing: " + runtime.issues());
        }

        return new RemotionProviderReadiness(
                true,           // documentGenerationReady (P1R.0-P1R.3 complete)
                toolsAvailable, // runtimeToolsAvailable
                false,          // executionReady — always false
                false,          // productionEligible — always false
                false,          // autoDispatch — always false
                "POC",
                List.copyOf(blocked),
                runtime);
    }
}

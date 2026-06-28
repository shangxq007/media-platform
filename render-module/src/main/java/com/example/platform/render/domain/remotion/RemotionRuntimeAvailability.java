package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Remotion runtime availability — diagnostic readiness model.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>Runtime availability is not execution permission.
 * Even if all tools are available, Remotion execution remains disabled.</p>
 *
 * @param nodeAvailable        whether node is available
 * @param nodeVersion          node version (null if unavailable)
 * @param npmAvailable         whether npm is available
 * @param npmVersion           npm version
 * @param npxAvailable         whether npx is available
 * @param npxVersion           npx version
 * @param remotionCliAvailable whether remotion CLI is detected
 * @param remotionCliVersion   remotion CLI version
 * @param documentGenerationReady whether document generation is possible
 * @param executionReady       whether execution is ready (false in v0)
 * @param disabledByPolicy     whether execution is disabled by policy (true in v0)
 * @param toolStatuses         per-tool statuses
 * @param issues               diagnostic issues
 */
public record RemotionRuntimeAvailability(
        boolean nodeAvailable,
        String nodeVersion,
        boolean npmAvailable,
        String npmVersion,
        boolean npxAvailable,
        String npxVersion,
        boolean remotionCliAvailable,
        String remotionCliVersion,
        boolean documentGenerationReady,
        boolean executionReady,
        boolean disabledByPolicy,
        List<RemotionRuntimeToolStatus> toolStatuses,
        List<String> issues) {

    /**
     * Returns true if all required runtime tools are available.
     */
    public boolean allToolsAvailable() {
        return nodeAvailable && npmAvailable && npxAvailable;
    }

    /**
     * Returns true if any required tool is missing.
     */
    public boolean hasMissingTools() {
        return !nodeAvailable || !npmAvailable || !npxAvailable;
    }

    /**
     * Default availability when no detection has been performed.
     */
    public static RemotionRuntimeAvailability notChecked() {
        return new RemotionRuntimeAvailability(
                false, null, false, null, false, null,
                false, null, false, false, true,
                List.of(), List.of("Runtime availability not checked"));
    }
}

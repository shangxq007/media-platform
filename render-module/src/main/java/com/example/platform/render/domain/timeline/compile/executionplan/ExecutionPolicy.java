package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Policy configuration for execution plan evaluation.
 *
 * <p>Internal only — controls what the plan compiler and policy guard allow.</p>
 *
 * @param mode                      execution mode: PRODUCTION, MANUAL, EXPERIMENT, DRY_RUN
 * @param allowManualProviders      whether POC/OPTIONAL providers are allowed
 * @param allowExperimentalProviders whether HOLD/SPIKE providers are allowed
 * @param allowOpenCueSubmit        whether OpenCue submission is enabled
 * @param allowProviderExecution    whether provider execution is allowed (false for v0)
 * @param maxStepCount              maximum steps allowed in plan (0 = unlimited)
 */
public record ExecutionPolicy(
        String mode,
        boolean allowManualProviders,
        boolean allowExperimentalProviders,
        boolean allowOpenCueSubmit,
        boolean allowProviderExecution,
        int maxStepCount) {

    /**
     * Production policy: only production-eligible providers, no OpenCue, no execution.
     */
    public static ExecutionPolicy production() {
        return new ExecutionPolicy("PRODUCTION", false, false, false, false, 0);
    }

    /**
     * Manual policy: allows POC/OPTIONAL, no execution.
     */
    public static ExecutionPolicy manual() {
        return new ExecutionPolicy("MANUAL", true, false, false, false, 0);
    }

    /**
     * Experiment policy: allows all configurable providers, no execution.
     */
    public static ExecutionPolicy experiment() {
        return new ExecutionPolicy("EXPERIMENT", true, true, false, false, 0);
    }

    /**
     * Dry-run policy: planning only, no execution.
     */
    public static ExecutionPolicy dryRun() {
        return new ExecutionPolicy("DRY_RUN", false, false, false, false, 0);
    }

    /**
     * Returns true if the mode requires production-eligible providers only.
     */
    public boolean isProductionMode() {
        return "PRODUCTION".equals(mode);
    }

    /**
     * Returns true if the mode allows manual provider override.
     */
    public boolean isManualMode() {
        return "MANUAL".equals(mode);
    }

    /**
     * Returns true if the mode is experiment or dry-run.
     */
    public boolean isExperimentalOrDryRun() {
        return "EXPERIMENT".equals(mode) || "DRY_RUN".equals(mode);
    }
}

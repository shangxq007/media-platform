package com.example.platform.render.domain;

/**
 * Deterministic render job states.
 *
 * <p>State transitions are governed by {@link RenderJobStateMachine}.
 * No service should mutate job status directly - all transitions must go through the state machine.
 *
 * <p>States:
 * <ul>
 *   <li>{@link #QUEUED} - Job created, waiting for execution</li>
 *   <li>{@link #SELECTING_PROVIDER} - Provider runtime is resolving the best provider</li>
 *   <li>{@link #PROVIDER_SELECTED} - Provider selected, preparing for execution</li>
 *   <li>{@link #EXECUTING} - Provider is actively rendering</li>
 *   <li>{@link #FALLBACKING} - Primary provider failed, trying fallback</li>
 *   <li>{@link #RETRYING} - Retrying after transient failure</li>
 *   <li>{@link #COMPLETING} - Render complete, finalizing artifacts</li>
 *   <li>{@link #COMPLETED} - Job successfully completed</li>
 *   <li>{@link #FAILED} - Job failed (terminal state)</li>
 *   <li>{@link #CANCELLED} - Job cancelled by user (terminal state)</li>
 *   <li>{@link #REJECTED} - Job rejected by policy/quota (terminal state)</li>
 * </ul>
 */
public enum RenderJobStatus {

    /**
     * Job created, waiting for execution.
     */
    QUEUED(false, false),

    /**
     * Provider runtime is resolving the best provider.
     * Triggered when ProviderRuntimeEngine.resolveProvider() is called.
     */
    SELECTING_PROVIDER(false, false),

    /**
     * Provider selected, preparing for execution.
     * Provider has been chosen but render not yet started.
     */
    PROVIDER_SELECTED(false, false),

    /**
     * Provider is actively rendering the job.
     */
    EXECUTING(false, false),

    /**
     * Primary provider failed, attempting fallback provider.
     * Transitions back to EXECUTING if fallback succeeds.
     */
    FALLBACKING(false, false),

    /**
     * Retrying after transient failure.
     * Transitions back to EXECUTING.
     */
    RETRYING(false, false),

    /**
     * Render complete, finalizing artifacts (upload, metadata).
     */
    COMPLETING(false, false),

    /**
     * Job successfully completed (terminal state).
     */
    COMPLETED(true, false),

    /**
     * Job failed (terminal state).
     */
    FAILED(true, false),

    /**
     * Job cancelled by user (terminal state).
     */
    CANCELLED(true, false),

    /**
     * Job rejected by policy/quota (terminal state).
     */
    REJECTED(true, false);

    // Legacy compatibility aliases
    @Deprecated
    public static final RenderJobStatus AI_PROCESSING = SELECTING_PROVIDER;
    @Deprecated
    public static final RenderJobStatus RENDERING = EXECUTING;

    private final boolean terminal;
    private final boolean canRetry;

    RenderJobStatus(boolean terminal, boolean canRetry) {
        this.terminal = terminal;
        this.canRetry = canRetry;
    }

    /**
     * Returns true if this is a terminal state (no further transitions).
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Returns true if the job can be retried from this state.
     */
    public boolean isCanRetry() {
        return canRetry;
    }

    /**
     * Returns true if the job is in an active execution state.
     */
    public boolean isActive() {
        return !terminal && this != QUEUED;
    }

    /**
     * Returns true if the job is in a provider-related state.
     */
    public boolean isProviderState() {
        return this == SELECTING_PROVIDER || this == PROVIDER_SELECTED
                || this == FALLBACKING || this == RETRYING;
    }

    /**
     * Legacy compatibility: map to old status names.
     */
    public String toLegacyStatus() {
        return switch (this) {
            case QUEUED -> "QUEUED";
            case SELECTING_PROVIDER, PROVIDER_SELECTED -> "AI_PROCESSING";
            case EXECUTING, FALLBACKING, RETRYING -> "RENDERING";
            case COMPLETING -> "RENDERING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case REJECTED -> "REJECTED";
        };
    }

    /**
     * Parse from legacy status name.
     */
    public static RenderJobStatus fromLegacyStatus(String legacy) {
        if (legacy == null) return QUEUED;
        return switch (legacy.toUpperCase()) {
            case "QUEUED" -> QUEUED;
            case "AI_PROCESSING" -> SELECTING_PROVIDER;
            case "RENDERING" -> EXECUTING;
            case "COMPLETED" -> COMPLETED;
            case "FAILED" -> FAILED;
            case "CANCELLED" -> CANCELLED;
            case "REJECTED" -> REJECTED;
            default -> QUEUED;
        };
    }
}

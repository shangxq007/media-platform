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
 *   <li>{@link #COMPLETING} - Render complete, finalizing artifacts</li>
 *   <li>{@link #COMPLETED} - Job successfully completed</li>
 *   <li>{@link #FAILED} - Job failed (terminal state)</li>
 *   <li>{@link #CANCELLED} - Job cancelled by user (terminal state)</li>
 *   <li>{@link #REJECTED} - Job rejected by policy/quota (terminal state)</li>
 * </ul>
 *
 * <p>Retry and Fallback create new RenderJob attempts — they do not reuse the old one.
 */
public enum RenderJobStatus {

    QUEUED(false, false),
    SELECTING_PROVIDER(false, false),
    PROVIDER_SELECTED(false, false),
    EXECUTING(false, false),
    COMPLETING(false, false),
    COMPLETED(true, false),
    FAILED(true, false),
    CANCELLED(true, false),
    REJECTED(true, false);

    private final boolean terminal;
    private final boolean canRetry;

    RenderJobStatus(boolean terminal, boolean canRetry) {
        this.terminal = terminal;
        this.canRetry = canRetry;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isCanRetry() {
        return canRetry;
    }

    public boolean isActive() {
        return !terminal && this != QUEUED;
    }

    public boolean isProviderState() {
        return this == SELECTING_PROVIDER || this == PROVIDER_SELECTED;
    }
}

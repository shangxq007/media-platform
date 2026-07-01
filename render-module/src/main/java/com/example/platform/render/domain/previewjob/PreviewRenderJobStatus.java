package com.example.platform.render.domain.previewjob;

/**
 * Lifecycle states for a Preview Render Job.
 *
 * <p>Transitions:
 * <pre>
 *   QUEUED ──→ EXECUTING ──→ COMPLETED  (terminal)
 *     │            │
 *     │            └──→ FAILED     (terminal)
 *     │
 *     └──→ CANCELLED   (terminal)
 * </pre>
 *
 * <p>No service should mutate job status directly — all transitions
 * must go through {@link PreviewRenderJob} domain methods.</p>
 */
public enum PreviewRenderJobStatus {

    /** Job created, waiting for execution. */
    QUEUED(false),

    /** FFmpeg render in progress. */
    EXECUTING(false),

    /** Job successfully completed; output Product registered. */
    COMPLETED(true),

    /** Job failed (terminal). */
    FAILED(true),

    /** Job cancelled by user (terminal). */
    CANCELLED(true);

    private final boolean terminal;

    PreviewRenderJobStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * Returns true if this is a terminal state (no further transitions).
     */
    public boolean isTerminal() {
        return terminal;
    }
}

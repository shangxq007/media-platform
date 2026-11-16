package com.example.platform.execution.admission;

/**
 * Canonical Temporal workflow-id scheme for capability executions.
 *
 * <p>Both the authenticated API cancellation path and the worker dispatch listener must address the
 * same workflow, so the scheme lives with the execution admission contract instead of either
 * composition slice. The bytes are part of the platform contract: changing them would orphan
 * in-flight executions.</p>
 */
public final class CapabilityExecutionWorkflowIds {

    private CapabilityExecutionWorkflowIds() {
    }

    /** Returns {@code capability:<tenant>:<execution>:<generation>} exactly. */
    public static String workflowId(String tenant, String execution, long generation) {
        return "capability:" + tenant + ":" + execution + ":" + generation;
    }
}

package com.example.platform.render.domain.timeline.compile.executionplan;

import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import com.example.platform.render.domain.timeline.compile.binding.BoundProviderRef;
import com.example.platform.render.domain.timeline.compile.execution.ProviderExecutionDocumentDraft;
import java.util.List;
import java.util.Map;

/**
 * A single step in a render execution plan.
 *
 * <p>Internal only — a planning placeholder. Does not execute.</p>
 *
 * @param stepId          deterministic step identifier
 * @param type            type of execution step
 * @param status          current status (PENDING in v0)
 * @param nodeId          source capability graph node ID (null for plan-level steps)
 * @param artifactNodeType artifact node type (null for plan-level steps)
 * @param providerName    bound provider name (null for non-provider steps)
 * @param providerRef     bound provider reference (null for non-provider steps)
 * @param documentDraft   execution document draft (null for non-document steps)
 * @param dependencies    step IDs this step depends on
 * @param executionReady  whether this step is ready for execution (false in v0)
 * @param executionEnvironmentTarget target environment for this step
 * @param label           human-readable label
 * @param metadata        immutable metadata map
 */
public record RenderExecutionStep(
        String stepId,
        RenderExecutionStepType type,
        RenderExecutionStepStatus status,
        String nodeId,
        ArtifactNodeType artifactNodeType,
        String providerName,
        BoundProviderRef providerRef,
        ProviderExecutionDocumentDraft documentDraft,
        List<String> dependencies,
        boolean executionReady,
        ExecutionEnvironmentTarget executionEnvironmentTarget,
        String label,
        Map<String, String> metadata) {

    /**
     * Returns true if this step is a provider execution step.
     */
    public boolean isProviderExecution() {
        return type == RenderExecutionStepType.EXECUTE_PROVIDER;
    }

    /**
     * Returns true if this step is for the final output.
     */
    public boolean isFinalOutput() {
        return artifactNodeType == ArtifactNodeType.FINAL_RENDER;
    }

    /**
     * Returns true if this step has dependencies.
     */
    public boolean hasDependencies() {
        return dependencies != null && !dependencies.isEmpty();
    }

    /**
     * Returns true if this step is pending (not yet executed).
     */
    public boolean isPending() {
        return status == RenderExecutionStepStatus.PENDING;
    }

    /**
     * Returns true if this step is failed.
     */
    public boolean isFailed() {
        return status == RenderExecutionStepStatus.FAILED;
    }

    /**
     * Returns true if this step is blocked.
     */
    public boolean isBlocked() {
        return status == RenderExecutionStepStatus.BLOCKED;
    }
}

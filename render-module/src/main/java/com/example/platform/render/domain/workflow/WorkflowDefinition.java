package com.example.platform.render.domain.workflow;

import java.util.List;
import java.util.Map;

/**
 * Reusable workflow definition describing processing flow semantics.
 *
 * <p>Internal domain model. Provider-neutral, storage-neutral, engine-neutral.</p>
 *
 * <p>Must not contain provider names, storage internals,
 * FFmpeg commands, Remotion props, or execution environment IDs.</p>
 */
public record WorkflowDefinition(
        WorkflowDefinitionId id,
        WorkflowVersion version,
        WorkflowDisplayMetadata metadata,
        List<WorkflowInput> inputs,
        List<WorkflowStep> steps,
        List<WorkflowOutput> outputs,
        Map<String, String> safeMetadata) {

    public WorkflowDefinition {
        if (id == null)
            throw new IllegalArgumentException("Workflow ID must not be null");
        if (version == null)
            throw new IllegalArgumentException("Workflow version must not be null");
        if (steps == null || steps.isEmpty())
            throw new IllegalArgumentException("Steps must not be empty");
    }

    public boolean hasApplyTemplateStep() {
        return steps.stream().anyMatch(WorkflowStep::isApplyTemplate);
    }
}

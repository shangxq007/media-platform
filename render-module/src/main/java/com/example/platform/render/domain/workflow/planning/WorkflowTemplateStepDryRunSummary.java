package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.template.TemplateDefinitionId;
import com.example.platform.render.domain.template.TemplateVersion;
import java.util.Map;

/**
 * Summary of an APPLY_TEMPLATE step in dry-run context.
 * Internal domain model. Does not call template compilers.
 */
public record WorkflowTemplateStepDryRunSummary(
        TemplateDefinitionId templateId,
        TemplateVersion templateVersion,
        String templateKind,
        int targetCount,
        int parameterCount,
        boolean compositeCandidate,
        Map<String, String> safeMetadata) {}

package com.example.platform.render.domain.template.composite;

import com.example.platform.render.domain.template.TemplateDefinitionId;
import com.example.platform.render.domain.template.TemplateDisplayMetadata;
import com.example.platform.render.domain.template.TemplateVersion;
import java.util.List;
import java.util.Map;

/**
 * Composite template definition — composes Atomic Template profiles.
 *
 * <p>Internal domain model. Not a WorkflowDefinition.</p>
 *
 * <p>Must not contain provider names, storage internals,
 * FFmpeg commands, or Remotion props.</p>
 */
public record CompositeTemplateDefinition(
        CompositeTemplateDefinitionId id,
        TemplateDefinitionId templateId,
        TemplateVersion version,
        TemplateDisplayMetadata metadata,
        List<CompositeTemplateChild> children,
        List<TemplateTargetBinding> targetBindings,
        List<TemplateParameterBinding> parameterBindings,
        CompositeTemplateMergePolicy mergePolicy,
        CompositeTemplateConflictPolicy conflictPolicy,
        Map<String, String> safeMetadata) {

    public CompositeTemplateDefinition {
        if (id == null) throw new IllegalArgumentException("Composite template ID must not be null");
        if (templateId == null) throw new IllegalArgumentException("Template ID must not be null");
        if (version == null) throw new IllegalArgumentException("Version must not be null");
        if (children == null || children.isEmpty())
            throw new IllegalArgumentException("Children must not be empty");
    }

    public boolean hasChild(TemplateDefinitionId childTemplateId) {
        return children.stream()
                .anyMatch(c -> c.childTemplateId().equals(childTemplateId));
    }
}

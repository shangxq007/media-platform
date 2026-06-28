package com.example.platform.render.domain.template;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral Timeline operation intent.
 * Internal domain model. Does not contain FFmpeg commands or Remotion props.
 */
public record TemplateOperation(
        String operationId,
        TemplateOperationType type,
        TemplateTargetRole targetRole,
        Map<String, TemplateParameterValue> parameters,
        List<TemplateCapabilityRequirement> requiredCapabilities) {

    public TemplateOperation {
        if (operationId == null || operationId.isBlank())
            throw new IllegalArgumentException("Operation ID must not be blank");
        if (type == null)
            throw new IllegalArgumentException("Operation type must not be null");
        if (targetRole == null)
            throw new IllegalArgumentException("Target role must not be null");
    }
}

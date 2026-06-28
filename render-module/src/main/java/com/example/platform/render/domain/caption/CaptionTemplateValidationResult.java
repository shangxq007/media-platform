package com.example.platform.render.domain.caption;

import java.util.List;

/**
 * Validation result for CaptionTemplateRenderRequest.
 * Internal domain model.
 */
public record CaptionTemplateValidationResult(
        boolean valid,
        List<String> errors) {}

package com.example.platform.render.domain.template;

import java.util.List;

/**
 * Result of template application — provider-neutral.
 * Internal domain model.
 *
 * <p>Does not contain FFmpeg commands, Remotion props, or storage internals.</p>
 */
public record TemplateApplicationResult(
        TemplateApplicationStatus status,
        TemplateValidationResult validationResult,
        String safeMessage,
        List<String> warnings) {

    public static TemplateApplicationResult success(String message) {
        return new TemplateApplicationResult(
                TemplateApplicationStatus.SUCCESS,
                TemplateValidationResult.success(), message, List.of());
    }

    public static TemplateApplicationResult validationFailed(TemplateValidationResult validation) {
        return new TemplateApplicationResult(
                TemplateApplicationStatus.VALIDATION_FAILED,
                validation, "Validation failed", List.of());
    }

    public static TemplateApplicationResult compilationFailed(String message) {
        return new TemplateApplicationResult(
                TemplateApplicationStatus.COMPILATION_FAILED,
                null, message, List.of());
    }

    public boolean isSuccess() {
        return status == TemplateApplicationStatus.SUCCESS;
    }
}

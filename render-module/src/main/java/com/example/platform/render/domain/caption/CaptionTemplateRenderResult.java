package com.example.platform.render.domain.caption;

import java.util.List;
import java.util.Map;

/**
 * Caption Template Render result — MVP contract.
 *
 * <p>Internal domain model — safe result without provider/storage internals.</p>
 *
 * @param renderJobId       render job identifier
 * @param outputProductId   output Product ID (null if not yet produced)
 * @param status            result status
 * @param ready             whether output is READY
 * @param outputProfile     output profile used
 * @param validationErrors  validation errors (empty if valid)
 * @param safeMessage       safe human-readable message
 * @param safeMetadata      safe metadata only
 */
public record CaptionTemplateRenderResult(
        String renderJobId,
        String outputProductId,
        String status,
        boolean ready,
        CaptionOutputProfileSpec outputProfile,
        List<String> validationErrors,
        String safeMessage,
        Map<String, String> safeMetadata) {

    public boolean isSuccess() {
        return "READY".equals(status) && ready;
    }

    public boolean hasValidationErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }

    public static CaptionTemplateRenderResult validationFailed(List<String> errors) {
        return new CaptionTemplateRenderResult(
                null, null, "VALIDATION_FAILED", false, null,
                errors, "Validation failed: " + errors.size() + " error(s)", Map.of());
    }

    public static CaptionTemplateRenderResult success(
            String renderJobId, String outputProductId, CaptionOutputProfileSpec profile) {
        return new CaptionTemplateRenderResult(
                renderJobId, outputProductId, "READY", true, profile,
                List.of(), "Caption template render completed", Map.of());
    }
}

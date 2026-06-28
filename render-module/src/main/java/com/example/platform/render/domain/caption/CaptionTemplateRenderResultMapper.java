package com.example.platform.render.domain.caption;

import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import java.util.List;
import java.util.Map;

/**
 * Maps internal render results to CaptionTemplateRenderResult.
 *
 * <p>Internal domain mapper — filters out provider/storage internals.</p>
 */
public class CaptionTemplateRenderResultMapper {

    /**
     * Map a successful render result.
     */
    public CaptionTemplateRenderResult mapSuccess(
            TimelineRevisionRenderService.RevisionRenderResult renderResult,
            CaptionOutputProfileSpec profile) {
        return CaptionTemplateRenderResult.success(
                renderResult.renderJobId(),
                renderResult.outputProductId(),
                profile);
    }

    /**
     * Map a failed render result.
     */
    public CaptionTemplateRenderResult mapFailure(
            String renderJobId, String message) {
        return new CaptionTemplateRenderResult(
                renderJobId, null, "FAILED", false, null,
                List.of(), message, Map.of());
    }

    /**
     * Map validation failure.
     */
    public CaptionTemplateRenderResult mapValidationFailure(List<String> errors) {
        return CaptionTemplateRenderResult.validationFailed(errors);
    }
}

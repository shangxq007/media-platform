package com.example.platform.render.domain.caption;

import java.util.List;
import java.util.Map;

/**
 * Caption Template Render request — MVP contract.
 *
 * <p>Internal domain model — not a public API DTO.</p>
 *
 * @param projectId        project identifier (required)
 * @param sourceProductId  source video Product ID (required)
 * @param captionSegments  caption segments with timing (required, non-empty)
 * @param template         caption template/style (null = defaults)
 * @param outputProfile    output profile (null = 1080p default)
 * @param safeMetadata     safe metadata only
 */
public record CaptionTemplateRenderRequest(
        String projectId,
        String sourceProductId,
        List<CaptionSegmentSpec> captionSegments,
        CaptionTemplateSpec template,
        CaptionOutputProfileSpec outputProfile,
        Map<String, String> safeMetadata) {

    public CaptionTemplateSpec effectiveTemplate() {
        return template != null ? template : CaptionTemplateSpec.defaults();
    }

    public CaptionOutputProfileSpec effectiveOutputProfile() {
        return outputProfile != null ? outputProfile : CaptionOutputProfileSpec.hd1080p();
    }
}

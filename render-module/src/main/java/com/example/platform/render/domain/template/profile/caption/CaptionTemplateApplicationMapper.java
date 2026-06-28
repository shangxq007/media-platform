package com.example.platform.render.domain.template.profile.caption;

import com.example.platform.render.domain.caption.*;
import com.example.platform.render.domain.template.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps CaptionTemplateRenderRequest to TemplateApplicationRequest.
 *
 * <p>Internal domain mapper — pure and deterministic.</p>
 *
 * <p>Mapping:
 * <ul>
 *   <li>sourceProductId → TemplateTarget(MAIN_VIDEO, PRODUCT)</li>
 *   <li>captionSegments → CAPTION_TRACK target reference</li>
 *   <li>style/template → safe parameters</li>
 *   <li>outputProfile → safe parameters</li>
 *   <li>projectId → projectId</li>
 * </ul>
 *
 * <p>Does not create provider/storage fields.</p>
 */
public final class CaptionTemplateApplicationMapper {

    private CaptionTemplateApplicationMapper() {}

    /**
     * Map a caption request to a template application request.
     */
    public static TemplateApplicationRequest map(CaptionTemplateRenderRequest request) {
        List<TemplateTarget> targets = new ArrayList<>();

        // Source video → MAIN_VIDEO PRODUCT target
        targets.add(TemplateTarget.product(
                TemplateTargetRole.MAIN_VIDEO, request.sourceProductId()));

        // Caption segments → CAPTION_TRACK TEXT target
        // Segment data is encoded in parameters, not in target ID
        targets.add(new TemplateTarget(
                TemplateTargetRole.CAPTION_TRACK,
                TemplateTargetType.TEXT,
                "caption-track-" + request.projectId(),
                Map.of("segmentCount", String.valueOf(
                        request.captionSegments() != null ? request.captionSegments().size() : 0))));

        // Build parameters from template/style/output
        List<TemplateParameter> parameters = new ArrayList<>();

        CaptionStyleSpec style = request.effectiveTemplate().style();
        if (style != null) {
            if (style.font() != null && style.font().family() != null) {
                parameters.add(new TemplateParameter(
                        "fontFamily", "Font Family", "STRING", false, style.font().family()));
            }
            if (style.fontSize() > 0) {
                parameters.add(new TemplateParameter(
                        "fontSize", "Font Size", "NUMBER", false, String.valueOf(style.fontSize())));
            }
            if (style.placement() != null) {
                parameters.add(new TemplateParameter(
                        "placement", "Placement", "ENUM", false, style.placement().name()));
            }
        }

        CaptionOutputProfileSpec profile = request.effectiveOutputProfile();
        if (profile != null) {
            parameters.add(new TemplateParameter(
                    "outputWidth", "Output Width", "NUMBER", false, String.valueOf(profile.width())));
            parameters.add(new TemplateParameter(
                    "outputHeight", "Output Height", "NUMBER", false, String.valueOf(profile.height())));
            parameters.add(new TemplateParameter(
                    "outputFps", "Output FPS", "NUMBER", false, String.valueOf(profile.fps())));
            parameters.add(new TemplateParameter(
                    "outputContainer", "Output Container", "STRING", false, profile.container()));
        }

        // Safe metadata
        Map<String, String> metadata = request.safeMetadata() != null
                ? Map.copyOf(request.safeMetadata()) : Map.of();

        return new TemplateApplicationRequest(
                request.projectId(),
                new TemplateDefinitionId(CaptionTemplateProfile.TEMPLATE_ID),
                new TemplateVersion(CaptionTemplateProfile.TEMPLATE_VERSION),
                targets,
                parameters,
                metadata);
    }
}

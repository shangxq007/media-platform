package com.example.platform.render.domain.template.profile.watermark;

import com.example.platform.render.domain.template.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps WatermarkTemplateApplicationInput to TemplateApplicationRequest.
 *
 * <p>Internal domain mapper — pure and deterministic.</p>
 *
 * <p>Does not create provider/storage fields.</p>
 */
public final class WatermarkTemplateApplicationMapper {

    private WatermarkTemplateApplicationMapper() {}

    public static TemplateApplicationRequest map(WatermarkTemplateApplicationInput input) {
        List<TemplateTarget> targets = new ArrayList<>();

        // Main video → MAIN_VIDEO PRODUCT target
        targets.add(TemplateTarget.product(
                TemplateTargetRole.MAIN_VIDEO, input.mainVideoProductId()));

        // Watermark → WATERMARK_IMAGE PRODUCT target
        targets.add(TemplateTarget.product(
                TemplateTargetRole.WATERMARK_IMAGE, input.watermarkProductId()));

        // Build parameters
        List<TemplateParameter> parameters = new ArrayList<>();
        parameters.add(new TemplateParameter(
                "placement", "Placement", "ENUM", false, input.effectivePlacement()));
        parameters.add(new TemplateParameter(
                "opacityPercent", "Opacity Percent", "NUMBER", false,
                String.valueOf(input.effectiveOpacity())));
        parameters.add(new TemplateParameter(
                "marginX", "Margin X", "NUMBER", false,
                String.valueOf(input.effectiveMarginX())));
        parameters.add(new TemplateParameter(
                "marginY", "Margin Y", "NUMBER", false,
                String.valueOf(input.effectiveMarginY())));

        Map<String, String> metadata = input.safeMetadata() != null
                ? Map.copyOf(input.safeMetadata()) : Map.of();

        return new TemplateApplicationRequest(
                input.projectId(),
                new TemplateDefinitionId(WatermarkTemplateProfile.TEMPLATE_ID),
                new TemplateVersion(WatermarkTemplateProfile.TEMPLATE_VERSION),
                targets,
                parameters,
                metadata);
    }
}

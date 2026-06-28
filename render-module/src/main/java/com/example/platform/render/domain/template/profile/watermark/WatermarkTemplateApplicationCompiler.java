package com.example.platform.render.domain.template.profile.watermark;

import com.example.platform.render.domain.template.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Watermark-specific TemplateApplicationCompiler.
 *
 * <p>Internal domain compiler — validates Watermark Template application
 * and returns a provider-neutral result.</p>
 *
 * <p>Does not call FFmpeg, Remotion, StorageRuntime, or ProductRuntime.</p>
 */
public class WatermarkTemplateApplicationCompiler implements TemplateApplicationCompiler {

    @Override
    public boolean supports(TemplateDefinition definition) {
        return definition != null
                && WatermarkTemplateProfile.isWatermarkProfile(definition.id());
    }

    @Override
    public TemplateApplicationResult compile(
            TemplateDefinition definition,
            TemplateApplicationRequest request) {

        if (definition == null) {
            return TemplateApplicationResult.compilationFailed("Template definition must not be null");
        }
        if (!supports(definition)) {
            return TemplateApplicationResult.compilationFailed(
                    "Unsupported template: " + (definition.id() != null ? definition.id().value() : "null"));
        }
        if (request == null) {
            return TemplateApplicationResult.compilationFailed("Request must not be null");
        }

        List<TemplateValidationError> errors = new ArrayList<>();

        // Validate MAIN_VIDEO target
        boolean hasMainVideo = request.targets().stream()
                .anyMatch(t -> t.role() == TemplateTargetRole.MAIN_VIDEO);
        if (!hasMainVideo) {
            errors.add(new TemplateValidationError("targets", "MAIN_VIDEO_MISSING",
                    "MAIN_VIDEO target is required"));
        }

        // Validate WATERMARK_IMAGE or LOGO target
        boolean hasWatermark = request.targets().stream()
                .anyMatch(t -> t.role() == TemplateTargetRole.WATERMARK_IMAGE
                        || t.role() == TemplateTargetRole.LOGO);
        if (!hasWatermark) {
            errors.add(new TemplateValidationError("targets", "WATERMARK_TARGET_MISSING",
                    "WATERMARK_IMAGE or LOGO target is required"));
        }

        // Validate opacity if present
        request.parameters().stream()
                .filter(p -> "opacityPercent".equals(p.parameterId()))
                .findFirst()
                .ifPresent(p -> {
                    try {
                        int opacity = Integer.parseInt(p.defaultValue());
                        if (opacity < 0 || opacity > 100) {
                            errors.add(new TemplateValidationError("opacityPercent",
                                    "OPACITY_OUT_OF_RANGE",
                                    "Opacity must be between 0 and 100"));
                        }
                    } catch (NumberFormatException e) {
                        errors.add(new TemplateValidationError("opacityPercent",
                                "OPACITY_INVALID", "Opacity must be a number"));
                    }
                });

        // Validate margins if present
        for (String marginParam : List.of("marginX", "marginY")) {
            final String paramId = marginParam;
            request.parameters().stream()
                    .filter(p -> paramId.equals(p.parameterId()))
                    .findFirst()
                    .ifPresent(p -> {
                        try {
                            int val = Integer.parseInt(p.defaultValue());
                            if (val < 0) {
                                errors.add(new TemplateValidationError(paramId,
                                        "MARGIN_NEGATIVE", paramId + " must be non-negative"));
                            }
                        } catch (NumberFormatException e) {
                            errors.add(new TemplateValidationError(paramId,
                                    "MARGIN_INVALID", paramId + " must be a number"));
                        }
                    });
        }

        if (!errors.isEmpty()) {
            return TemplateApplicationResult.validationFailed(
                    TemplateValidationResult.failure(errors));
        }

        return TemplateApplicationResult.success(
                "Watermark template application validated. Ready for timeline adaptation.");
    }
}

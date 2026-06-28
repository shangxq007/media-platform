package com.example.platform.render.domain.template.profile.caption;

import com.example.platform.render.domain.template.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Caption-specific TemplateApplicationCompiler.
 *
 * <p>Internal domain compiler — validates Caption Template application
 * and returns a provider-neutral result.</p>
 *
 * <p>Does not call FFmpeg, Remotion, StorageRuntime, or ProductRuntime.</p>
 */
public class CaptionTemplateApplicationCompiler implements TemplateApplicationCompiler {

    @Override
    public boolean supports(TemplateDefinition definition) {
        return definition != null
                && CaptionTemplateProfile.isCaptionProfile(definition.id());
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

        // Validate required targets
        List<TemplateValidationError> errors = new ArrayList<>();

        boolean hasMainVideo = request.targets().stream()
                .anyMatch(t -> t.role() == TemplateTargetRole.MAIN_VIDEO);
        if (!hasMainVideo) {
            errors.add(new TemplateValidationError("targets", "MAIN_VIDEO_MISSING",
                    "MAIN_VIDEO target is required"));
        }

        boolean hasCaptionTrack = request.targets().stream()
                .anyMatch(t -> t.role() == TemplateTargetRole.CAPTION_TRACK);
        if (!hasCaptionTrack) {
            errors.add(new TemplateValidationError("targets", "CAPTION_TRACK_MISSING",
                    "CAPTION_TRACK target is required"));
        }

        if (!errors.isEmpty()) {
            return TemplateApplicationResult.validationFailed(
                    TemplateValidationResult.failure(errors));
        }

        // Validation passed — return success
        // TimelineSpec generation is deferred to existing CaptionTemplateTimelineAdapter
        return TemplateApplicationResult.success(
                "Caption template application validated. Ready for timeline adaptation.");
    }
}

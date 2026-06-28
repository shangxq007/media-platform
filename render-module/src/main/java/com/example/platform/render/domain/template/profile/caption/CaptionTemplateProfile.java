package com.example.platform.render.domain.template.profile.caption;

import com.example.platform.render.domain.template.*;
import java.util.List;
import java.util.Map;

/**
 * Built-in Caption Template profile — first vertical Template Application profile.
 *
 * <p>Internal domain model. Creates the canonical TemplateDefinition
 * representing the built-in caption template capability.</p>
 *
 * <p>Maps to ADR-022 concepts:
 * <ul>
 *   <li>TemplateDefinition: basic-caption</li>
 *   <li>TargetRoles: MAIN_VIDEO, CAPTION_TRACK</li>
 *   <li>Operations: ADD_TEXT_OVERLAY, APPLY_TEXT_STYLE</li>
 *   <li>Capabilities: TEXT_OVERLAY, SUBTITLE_BURN_IN</li>
 * </ul>
 */
public final class CaptionTemplateProfile {

    public static final String TEMPLATE_ID = "builtin.caption.basic";
    public static final String TEMPLATE_VERSION = "1.0.0";

    private CaptionTemplateProfile() {}

    /**
     * Create the built-in caption TemplateDefinition.
     */
    public static TemplateDefinition definition() {
        return new TemplateDefinition(
                new TemplateDefinitionId(TEMPLATE_ID),
                new TemplateVersion(TEMPLATE_VERSION),
                TemplateType.CAPTION,
                new TemplateDisplayMetadata(
                        "Basic Caption",
                        "Burn-in caption/subtitle overlay on video using FFmpeg/libass baseline",
                        null),
                List.of(TemplateTargetRole.MAIN_VIDEO, TemplateTargetRole.CAPTION_TRACK),
                List.of(), // parameters are passed via request, not declared in definition
                List.of(
                        new TemplateOperation("op-add-text-overlay",
                                TemplateOperationType.ADD_TEXT_OVERLAY,
                                TemplateTargetRole.CAPTION_TRACK,
                                Map.of(), List.of(
                                        TemplateCapabilityRequirement.required("TEXT_OVERLAY"),
                                        TemplateCapabilityRequirement.required("SUBTITLE_BURN_IN"))),
                        new TemplateOperation("op-apply-text-style",
                                TemplateOperationType.APPLY_TEXT_STYLE,
                                TemplateTargetRole.CAPTION_TRACK,
                                Map.of(), List.of(
                                        TemplateCapabilityRequirement.required("TEXT_OVERLAY")))),
                List.of(),
                List.of(
                        TemplateCapabilityRequirement.required("TEXT_OVERLAY"),
                        TemplateCapabilityRequirement.required("SUBTITLE_BURN_IN")));
    }

    /**
     * Returns true if the given template ID matches this profile.
     */
    public static boolean isCaptionProfile(TemplateDefinitionId templateId) {
        return templateId != null && TEMPLATE_ID.equals(templateId.value());
    }
}

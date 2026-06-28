package com.example.platform.render.domain.template.profile.watermark;

import com.example.platform.render.domain.template.*;
import java.util.List;
import java.util.Map;

/**
 * Built-in Watermark Template profile — second vertical Template Application profile.
 *
 * <p>Internal domain model. Creates the canonical TemplateDefinition
 * representing the built-in watermark template capability.</p>
 *
 * <p>Maps to ADR-022 concepts:
 * <ul>
 *   <li>TemplateDefinition: basic-watermark</li>
 *   <li>TargetRoles: MAIN_VIDEO, WATERMARK_IMAGE, LOGO</li>
 *   <li>Operations: ADD_WATERMARK, ADD_IMAGE_OVERLAY</li>
 *   <li>Capabilities: IMAGE_OVERLAY, VIDEO_COMPOSITE</li>
 * </ul>
 */
public final class WatermarkTemplateProfile {

    public static final String TEMPLATE_ID = "builtin.watermark.basic";
    public static final String TEMPLATE_VERSION = "1.0.0";

    private WatermarkTemplateProfile() {}

    /**
     * Create the built-in watermark TemplateDefinition.
     */
    public static TemplateDefinition definition() {
        return new TemplateDefinition(
                new TemplateDefinitionId(TEMPLATE_ID),
                new TemplateVersion(TEMPLATE_VERSION),
                TemplateType.WATERMARK,
                new TemplateDisplayMetadata(
                        "Basic Watermark",
                        "Image or logo watermark overlay on video",
                        null),
                List.of(TemplateTargetRole.MAIN_VIDEO, TemplateTargetRole.WATERMARK_IMAGE),
                List.of(),
                List.of(
                        new TemplateOperation("op-add-watermark",
                                TemplateOperationType.ADD_WATERMARK,
                                TemplateTargetRole.WATERMARK_IMAGE,
                                Map.of(), List.of(
                                        TemplateCapabilityRequirement.required("IMAGE_OVERLAY"),
                                        TemplateCapabilityRequirement.required("VIDEO_COMPOSITE"))),
                        new TemplateOperation("op-add-image-overlay",
                                TemplateOperationType.ADD_IMAGE_OVERLAY,
                                TemplateTargetRole.WATERMARK_IMAGE,
                                Map.of(), List.of(
                                        TemplateCapabilityRequirement.required("IMAGE_OVERLAY")))),
                List.of(),
                List.of(
                        TemplateCapabilityRequirement.required("IMAGE_OVERLAY"),
                        TemplateCapabilityRequirement.required("VIDEO_COMPOSITE")));
    }

    /**
     * Returns true if the given template ID matches this profile.
     */
    public static boolean isWatermarkProfile(TemplateDefinitionId templateId) {
        return templateId != null && TEMPLATE_ID.equals(templateId.value());
    }
}

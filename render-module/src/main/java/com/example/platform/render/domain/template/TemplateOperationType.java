package com.example.platform.render.domain.template;

/**
 * Type of template operation — provider-neutral Timeline intent.
 * Internal domain model. Does not contain FFmpeg commands or Remotion props.
 */
public enum TemplateOperationType {
    ADD_TEXT_OVERLAY,
    APPLY_TEXT_STYLE,
    ADD_IMAGE_OVERLAY,
    ADD_VIDEO_CLIP,
    ADD_AUDIO_TRACK,
    DUCK_AUDIO,
    ADD_WATERMARK,
    ADD_TRANSITION,
    FIT_TO_CANVAS,
    CROP,
    TRIM,
    ADD_INTRO,
    ADD_OUTRO,
    APPLY_BRAND_STYLE
}

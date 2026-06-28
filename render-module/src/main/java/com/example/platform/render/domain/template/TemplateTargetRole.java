package com.example.platform.render.domain.template;

/**
 * Semantic role of a template target asset.
 * Internal domain model — roles are semantic, not storage/provider types.
 */
public enum TemplateTargetRole {
    MAIN_VIDEO,
    B_ROLL_VIDEO,
    CAPTION_TRACK,
    TITLE_TEXT,
    SUBTITLE_TEXT,
    LOGO,
    WATERMARK_IMAGE,
    PRODUCT_IMAGE,
    BACKGROUND_MUSIC,
    VOICEOVER_AUDIO,
    INTRO_CLIP,
    OUTRO_CLIP,
    BRAND_KIT
}

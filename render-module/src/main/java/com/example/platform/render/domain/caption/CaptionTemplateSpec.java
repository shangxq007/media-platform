package com.example.platform.render.domain.caption;

/**
 * Caption template specification — combines style with a template reference.
 * Internal domain model.
 *
 * @param templateId  template identifier (null = inline style only)
 * @param name        template name
 * @param style       caption style
 */
public record CaptionTemplateSpec(
        String templateId,
        String name,
        CaptionStyleSpec style) {

    public static CaptionTemplateSpec inline(CaptionStyleSpec style) {
        return new CaptionTemplateSpec(null, "inline", style);
    }

    public static CaptionTemplateSpec defaults() {
        return inline(CaptionStyleSpec.defaults());
    }
}

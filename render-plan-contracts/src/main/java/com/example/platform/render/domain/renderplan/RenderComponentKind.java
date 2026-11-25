package com.example.platform.render.domain.renderplan;

/**
 * Component-kind discriminator for {@link RenderComponentPath}. Identifies the
 * authored entity a render node is bound to.
 */
public enum RenderComponentKind {
    CLIP,
    EFFECT,
    TEXT_ELEMENT,
    AUDIO_ROUTE,
    AUDIO_MIX,
    COMPOSITE,
    OUTPUT,
    PLAN
}

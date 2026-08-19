package com.example.platform.render.domain.renderplan;

/**
 * Typed output artifact role (C15). {@link #RENDER_MASTER} is the full-fidelity
 * master; {@link #DELIVERY_RENDITION} is a delivery-target rendition.
 */
public enum RenderOutputRole {
    RENDER_MASTER,
    DELIVERY_RENDITION
}

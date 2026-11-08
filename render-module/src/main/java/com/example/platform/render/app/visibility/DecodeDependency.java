package com.example.platform.render.app.visibility;

import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.Objects;

/**
 * Source-side dependency retained by the analysis. A source retained window is
 * not a proof of the full materialized artifact or GOP extent.
 */
public record DecodeDependency(
        RenderSampleWindow sourceRetainedWindow,
        boolean required,
        String reason) {

    public DecodeDependency {
        Objects.requireNonNull(sourceRetainedWindow, "sourceRetainedWindow");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
    }
}

package com.example.platform.render.domain.previewjob;

import java.util.Objects;

/**
 * Strongly-typed identifier for a Preview Render Job.
 *
 * <p>Prevents primitive obsession and ensures only valid IDs
 * (non-null, non-blank) circulate through the domain.</p>
 */
public record PreviewRenderJobId(String value) {

    public PreviewRenderJobId {
        Objects.requireNonNull(value, "PreviewRenderJobId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PreviewRenderJobId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

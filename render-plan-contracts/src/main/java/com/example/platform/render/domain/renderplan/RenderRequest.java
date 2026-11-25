package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * Render request: exact extent + output requirements (C9/C14). The request id is
 * excluded from the plan fingerprint (C7); the extent and outputs participate.
 *
 * @param id      request correlation id (fingerprint-excluded)
 * @param extent  exact half-open render extent
 * @param outputs ordered output requirements
 */
public record RenderRequest(
        RenderRequestId id,
        RenderExtent extent,
        List<RenderOutputRequirement> outputs) {

    public RenderRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(extent, "extent");
        Objects.requireNonNull(outputs, "outputs");
        outputs = List.copyOf(outputs);
    }
}

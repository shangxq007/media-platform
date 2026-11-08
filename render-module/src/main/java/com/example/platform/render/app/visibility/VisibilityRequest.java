package com.example.platform.render.app.visibility;

import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.FrameRate;
import java.util.Objects;

/** Stable identity and exact output interval for a read-only visibility analysis. */
public record VisibilityRequest(
        String tenantId,
        String projectId,
        String revisionId,
        RenderRequest renderRequest) {

    public VisibilityRequest {
        tenantId = require(tenantId, "tenantId");
        projectId = require(projectId, "projectId");
        revisionId = require(revisionId, "revisionId");
        Objects.requireNonNull(renderRequest, "renderRequest");
        if (!renderRequest.extent().isValid()) {
            throw new IllegalArgumentException("RenderExtent must have start < end");
        }
    }

    public MediaTime start() { return renderRequest.extent().start(); }
    public MediaTime end() { return renderRequest.extent().end(); }
    public FrameRate frameRate() { return renderRequest.extent().frameRate(); }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

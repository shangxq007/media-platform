package com.example.platform.config.render;

import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.domain.renderplan.RenderExtent;
import java.util.Objects;

/**
 * Internal request accepted by the First Render Planning Application Service.
 *
 * <p>This is an internal application DTO (not an HTTP DTO): the HTTP boundary (Phase 4) maps its
 * own request onto this type. It carries the caller-resolved render initiator and profile because
 * the frozen render-job creation boundary ({@code RenderJobService.createForProject}) requires
 * them; the tenant/revision/extent identify the render target.
 *
 * @param tenantId   owning tenant
 * @param revisionId the Timeline Revision to render
 * @param initiator  the authenticated render initiator (resolved by the caller)
 * @param extent     the real render time range (never fabricated)
 * @param profile    the render profile required by the render-job creation boundary
 */
public record RevisionRenderPlanningRequest(
        String tenantId,
        String revisionId,
        RenderInitiator initiator,
        RenderExtent extent,
        String profile) {

    public RevisionRenderPlanningRequest {
        tenantId = requireText(tenantId, "tenantId");
        revisionId = requireText(revisionId, "revisionId");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(extent, "extent");
        profile = requireText(profile, "profile");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

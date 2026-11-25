package com.example.platform.web.render;

import com.example.platform.config.render.RevisionRenderPlanningAndBindingService;
import com.example.platform.config.render.RevisionRenderPlanningAndBindingService.RasterLimits;
import com.example.platform.shared.web.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RenderOutputCapabilityController {
    private final TimelineProjectAuthorizationService authorization;
    private final RevisionRenderPlanningAndBindingService planning;

    public RenderOutputCapabilityController(TimelineProjectAuthorizationService authorization,
            RevisionRenderPlanningAndBindingService planning) {
        this.authorization = authorization;
        this.planning = planning;
    }

    public record FeasibilityRequest(
            @jakarta.validation.constraints.NotBlank String contentHash,
            @jakarta.validation.Valid @jakarta.validation.constraints.NotNull
            com.example.platform.config.render.RenderJobExtentHttpRequest extent) {}

    @org.springframework.web.bind.annotation.PostMapping("/api/render/projects/{projectId}/revisions/{revisionId}/feasibility")
    public RevisionRenderPlanningAndBindingService.StaticFeasibility feasibility(@PathVariable String projectId,
            @PathVariable String revisionId, @jakarta.validation.Valid
            @org.springframework.web.bind.annotation.RequestBody FeasibilityRequest request) {
        String tenant = TenantContext.get();
        authorization.requireRead(tenant, projectId);
        return planning.feasibility(tenant, projectId, revisionId, request.contentHash(), request.extent().toExtent());
    }

    @GetMapping("/api/render/projects/{projectId}/output-rasters")
    public List<RasterLimits> outputRasters(@PathVariable String projectId) {
        authorization.requireRead(TenantContext.get(), projectId);
        return planning.outputRasters();
    }
}

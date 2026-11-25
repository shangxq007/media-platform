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

    @GetMapping("/api/render/projects/{projectId}/output-rasters")
    public List<RasterLimits> outputRasters(@PathVariable String projectId) {
        authorization.requireRead(TenantContext.get(), projectId);
        return planning.outputRasters();
    }
}

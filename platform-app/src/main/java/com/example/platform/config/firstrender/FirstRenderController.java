package com.example.platform.config.firstrender;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * First Render HTTP entry (Phase 4 endpoint, Phase 6 full chain).
 *
 * <p>Maps an HTTP request to the internal {@link FirstRenderPlanningRequest}, delegates to the Phase
 * 5 {@link FirstRenderPlanningAndBindingService#planAndBind} (which runs #20 → #21 → #22 once), then
 * persists + starts via the Phase 6 {@link FirstRenderSaveAndStartService}. The request contract,
 * authentication and HTTP 201 semantics are unchanged.
 */
@RestController
@RequestMapping("/api/first-render")
public class FirstRenderController {

    /** Planning-stage status: the plan inputs were prepared; nothing was executed or queued. */
    static final String STATUS_PLANNED = "PLANNED";

    private final FirstRenderPlanningAndBindingService planningAndBindingService;
    private final FirstRenderSaveAndStartService saveAndStartService;
    private final CanonicalActorResolver canonicalActorResolver;

    public FirstRenderController(
            FirstRenderPlanningAndBindingService planningAndBindingService,
            FirstRenderSaveAndStartService saveAndStartService,
            CanonicalActorResolver canonicalActorResolver) {
        this.planningAndBindingService = planningAndBindingService;
        this.saveAndStartService = saveAndStartService;
        this.canonicalActorResolver = canonicalActorResolver;
    }

    @PostMapping("/plans")
    public ResponseEntity<FirstRenderPlanHttpResponse> createPlan(
            @Valid @RequestBody FirstRenderPlanHttpRequest request) {
        // Initiator (and its tenant) come only from the authenticated context — never the body.
        RenderInitiator initiator = canonicalActorResolver.resolveCurrentActor()
                .map(RenderInitiator::from)
                .orElseThrow(() -> new IllegalStateException("Authenticated render initiator is required"));

        RenderExtent extent = toExtent(request.extent());

        // Phase 5: the single chain entry — planning + binding run exactly once.
        FirstRenderPlanningAndBindingResult planning = planningAndBindingService.planAndBind(
                new FirstRenderPlanningRequest(
                        initiator.tenantId(), request.revisionId(), initiator, extent, request.profile()));

        // Phase 6: persist the bound-graph inputs, then start the workflow.
        FirstRenderStartResult startResult = saveAndStartService.start(planning);

        return ResponseEntity.status(HttpStatus.CREATED).body(new FirstRenderPlanHttpResponse(
                planning.renderJobId(),
                planning.renderRequestId().value(),
                planning.boundGraphInputs().physicalPlan().planId().value(),
                STATUS_PLANNED,
                startResult.workflowId(),
                startResult.workflowStarted()));
    }

    private static RenderExtent toExtent(FirstRenderExtentHttpRequest extent) {
        RenderExtent renderExtent = new RenderExtent(
                MediaTime.ofMillis(extent.startMillis()),
                MediaTime.ofMillis(extent.endMillis()),
                FrameRate.of(extent.frameRateNumerator(), extent.frameRateDenominator()));
        if (!renderExtent.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "extent start must be before end");
        }
        return renderExtent;
    }
}

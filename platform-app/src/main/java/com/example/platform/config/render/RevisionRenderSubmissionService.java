package com.example.platform.config.render;

import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderAcceptanceContextService;
import com.example.platform.render.domain.renderplan.RenderExtent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** One DB commit owns the reservation, accepted Job, revision, graph and dispatch intent. */
@Service
public class RevisionRenderSubmissionService {
    private final TransactionTemplate transactions;
    private final RevisionRenderPlanningApplicationService preparation;
    private final RevisionRenderPlanningAndBindingService planning;
    private final RenderBoundGraphService graphs;
    private final RenderSubmissionIdempotencyService idempotency;
    private final RenderAcceptanceContextService acceptance;
    private final RenderJobDispatchService dispatch;
    private final com.example.platform.render.app.RenderJobService jobs;

    public RevisionRenderSubmissionService(org.springframework.transaction.PlatformTransactionManager manager,
            RevisionRenderPlanningApplicationService preparation, RevisionRenderPlanningAndBindingService planning,
            RenderBoundGraphService graphs, RenderSubmissionIdempotencyService idempotency,
            RenderAcceptanceContextService acceptance, RenderJobDispatchService dispatch,
            com.example.platform.render.app.RenderJobService jobs) {
        this.transactions = new TransactionTemplate(manager);
        this.preparation = preparation; this.planning = planning; this.graphs = graphs;
        this.idempotency = idempotency; this.acceptance = acceptance; this.dispatch = dispatch; this.jobs=jobs;
    }
    public record Submission(boolean created, RenderJobSubmissionHttpResponse response) {}
    public Submission submit(RenderJobSubmissionHttpRequest request, RenderExtent extent, RenderInitiator actor, String key) {
        return submit(request, extent, actor, key, null);
    }
    public Submission submitIncremental(IncrementalRenderHttpRequest request, RenderExtent extent, RenderInitiator actor, String key) {
        return submit(request.target(), extent, actor, key, request);
    }
    private Submission submit(RenderJobSubmissionHttpRequest request, RenderExtent extent, RenderInitiator actor, String key,
            IncrementalRenderHttpRequest incremental) {
        if (key != null && (key.isBlank() || key.length() > 255))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        var accepted = transactions.execute(status -> {
            String tenant = actor.tenantId();
            String project = preparation.projectIdForRevision(tenant, request.revisionId());
            // Authorize every submission, including replay/conflict, before revealing reservation identity.
            acceptance.prepare(tenant, project, actor);
            if (incremental != null) jobs.requireIncrementalReference(tenant, project,
                    incremental.referenceRenderJobId(), incremental.referenceRevisionId());
            if (key != null) {
                String hash = RenderSubmissionIdempotencyService.requestHash(request.revisionId(), request.extent().startMillis(),
                        request.extent().endMillis(), request.extent().frameRateNumerator(), request.extent().frameRateDenominator(),
                        request.profile(), request.outputFormat().name());
                if (incremental != null) {
                    var canonical=new com.example.platform.execution.planning.CanonicalWriter().tag("incremental-render.request.v1")
                            .field("target",hash).field("referenceJob",incremental.referenceRenderJobId())
                            .field("referenceRevision",incremental.referenceRevisionId()).build();
                    try {hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
                    catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
                }
                var old = idempotency.reserve(tenant, project, key, hash);
                if (!old.created()) return new Submission(false, new RenderJobSubmissionHttpResponse(old.renderJobId(), old.requestId(),
                        old.executionPlanId(), "PLANNED", old.workflowId(), old.workflowStarted()));
            }
            var plan = planning.planAndBind(new RevisionRenderPlanningRequest(tenant, request.revisionId(), actor, extent, request.profile()));
            var reference = graphs.save(plan);
            if (incremental != null) jobs.pinIncrementalReference(tenant, project, plan.renderJobId(),
                    incremental.referenceRenderJobId(), incremental.referenceRevisionId());
            String workflow = com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowIdentity.forJob(tenant, plan.renderJobId());
            String executionPlan = plan.boundGraphInputs().physicalPlan().planId().value();
            if (key != null) idempotency.complete(tenant, project, key, plan.renderJobId(), plan.renderRequestId().value(), executionPlan, workflow, false);
            return new Submission(true, new RenderJobSubmissionHttpResponse(plan.renderJobId(), plan.renderRequestId().value(), executionPlan, "PLANNED", workflow, false));
        });
        // No Temporal side effect is permitted until the entire submission committed.
        boolean started = dispatch.tryDispatch(actor.tenantId(), accepted.response().renderJobId());
        var r = accepted.response();
        return new Submission(accepted.created(), new RenderJobSubmissionHttpResponse(r.renderJobId(), r.requestId(),
                r.executionPlanId(), r.status(), r.workflowId(), r.workflowStarted() || started));
    }
}

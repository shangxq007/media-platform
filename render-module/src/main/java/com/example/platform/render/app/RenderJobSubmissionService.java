package com.example.platform.render.app;

import com.example.platform.timeline.app.InternalTimelineJson;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.app.timeline.AiTimelineEditContext;
import com.example.platform.render.app.timeline.AiTimelineEditService;
import com.example.platform.render.app.timeline.SegmentPlanFilter;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.shared.commercial.CommercialAdmissionPort;
import com.example.platform.shared.commercial.CommercialAdmissionRequest;
import com.example.platform.shared.commercial.CommercialDecision;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderInitiator;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Handles render job submission: validation, quota check, job creation,
 * timeline/script resolution, and AI edit application.
 *
 * <p>Extracted from {@link RenderOrchestratorService} to separate the submit path
 * from the execute/finish paths. The orchestrator delegates {@code submitRenderJob}
 * to this service, then calls {@code executeExistingRenderJob} for execution.
 *
 * <p>Inline jOOQ in this class is a known debt item — it should be migrated to
 * {@link RenderJobRepository} in a follow-up phase.
 */
@Service
public class RenderJobSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(RenderJobSubmissionService.class);

    private final DSLContext dsl;
    private final RenderJobRepository renderJobRepository;
    private final CommercialAdmissionPort commercialAdmission;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TimelineScriptParser timelineScriptParser;
    private final EffectTimelineInspector effectTimelineInspector;
    private final RenderProfileResolver renderProfileResolver;
    private final AiTimelineEditService aiTimelineEditService;
    private final com.example.platform.render.app.cache.RenderCacheTenantGuard cacheTenantGuard;

    public RenderJobSubmissionService(DSLContext dsl,
            RenderJobRepository renderJobRepository,
            CommercialAdmissionPort commercialAdmission,
            RenderJobStatusHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher,
            TimelineScriptParser timelineScriptParser,
            EffectTimelineInspector effectTimelineInspector,
            RenderProfileResolver renderProfileResolver,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            AiTimelineEditService aiTimelineEditService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.example.platform.render.app.cache.RenderCacheTenantGuard cacheTenantGuard) {
        this.dsl = dsl;
        this.renderJobRepository = renderJobRepository;
        this.commercialAdmission = commercialAdmission;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.timelineScriptParser = timelineScriptParser;
        this.effectTimelineInspector = effectTimelineInspector;
        this.renderProfileResolver = renderProfileResolver;
        this.aiTimelineEditService = aiTimelineEditService;
        this.cacheTenantGuard = cacheTenantGuard;
    }

    /**
     * Submit a render job: validate, check quota, create job row, resolve timeline,
     * apply AI edits, and return the job ID ready for execution.
     *
     * <p>This method does NOT execute the render — the caller (orchestrator) is
     * responsible for calling {@code executeExistingRenderJob} after this returns.
     *
     * @return the created job ID
     * @throws IllegalStateException if quota exceeded
     * @throws IllegalArgumentException if tenant/project validation fails
     */
    @Transactional
    public String submit(SubmitRenderJobRequest request, RenderInitiator initiator) {
        log.info("Submitting render job: tenant={}, project={}, profile={}",
                request.tenantId(), request.projectId(), request.profileOrDefault());

        assertInitiatorScope(request.tenantId(), initiator);
        assertTenantAccess(request.tenantId());
        assertProjectBelongsToTenant(request.tenantId(), request.projectId());

        if (request.baseJobId() != null && !request.baseJobId().isBlank() && cacheTenantGuard != null) {
            cacheTenantGuard.requireBaseJobAccess(
                    request.tenantId(), request.projectId(), request.baseJobId());
        }

        Instant now = Instant.now();
        Period period = period(now);
        CommercialDecision decision = commercialAdmission.decide(new CommercialAdmissionRequest(
                PrincipalRef.tenantScoped(
                        request.tenantId(), PrincipalType.ORGANIZATION, request.tenantId()),
                "render.submit", "render.job.create", "render.job.create", 1,
                period.start(), period.end(), "render-submit:" + request.projectId(), now));
        if (!decision.allowed()) {
            return handleCommercialDecisionRejected(request, initiator, decision);
        }

        return createQueuedJob(request, initiator);
    }

    private String handleCommercialDecisionRejected(SubmitRenderJobRequest request,
            RenderInitiator initiator, CommercialDecision decision) {
        String rejectedJobId = Ids.newId("rj");
        String profile = request.profileOrDefault();
        String code = decision.reason().name();
        String reason = decision.reason() == CommercialDecisionReason.QUOTA_EXCEEDED
                ? "Quota exceeded"
                : "Commercial admission denied: " + code;

        renderJobRepository.createRejected(rejectedJobId, request.projectId(), request.tenantId(),
                "snap_" + rejectedJobId, profile, reason, initiator, OffsetDateTime.now());
        historyRepository.record(rejectedJobId, null, RenderJobStatus.REJECTED.name(),
                reason, code);
        eventPublisher.publishEvent(new RenderJobFailedEvent(
                rejectedJobId, request.projectId(), reason, Instant.now(), initiator));

        log.warn("Commercial decision rejected for tenant {}: {} - {}",
                request.tenantId(), code, reason);
        throw new IllegalStateException(reason);
    }

    private static Period period(Instant instant) {
        YearMonth month = YearMonth.from(instant.atZone(ZoneOffset.UTC));
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Period(start, end);
    }

    private record Period(Instant start, Instant end) {}

    private String createQueuedJob(SubmitRenderJobRequest request, RenderInitiator initiator) {
        String jobId = Ids.newId("rj");
        String profile = request.profileOrDefault();
        String inlineScript = resolveInlineTimelineScript(request);
        if (inlineScript != null) {
            EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(inlineScript);
            profile = renderProfileResolver.resolve(profile, usage.effectKeys(), inlineScript);
        }
        String snapshotId = resolveSnapshotId(request, jobId);

        renderJobRepository.create(jobId, request.projectId(), request.tenantId(),
                snapshotId, profile, RenderJobStatus.QUEUED.name(), initiator, OffsetDateTime.now());
        historyRepository.record(jobId, null, RenderJobStatus.QUEUED.name(), "Job created", null);

        eventPublisher.publishEvent(
                new RenderJobCreatedEvent(jobId, request.projectId(), snapshotId, profile, null));

        persistInlineScriptIfPresent(jobId, request);
        applyAiEditInstructionIfPresent(jobId, request);

        return jobId;
    }

    private void persistInlineScriptIfPresent(String jobId, SubmitRenderJobRequest request) {
        String inline = resolveInlineTimelineScript(request);
        if (inline != null) {
            renderJobRepository.updateAiScript(jobId, inline);
        }
    }

    private String resolveInlineTimelineScript(SubmitRenderJobRequest request) {
        if (request.prompt() != null && timelineScriptParser.isTimelineJson(request.prompt())) {
            return request.prompt().trim();
        }
        return null;
    }

    private String resolveSnapshotId(SubmitRenderJobRequest request, String jobId) {
        if (request.timelineSnapshotId() != null && !request.timelineSnapshotId().isBlank()) {
            return request.timelineSnapshotId();
        }
        return "snap_" + jobId;
    }

    private void applyAiEditInstructionIfPresent(String jobId, SubmitRenderJobRequest request) {
        if (aiTimelineEditService == null
                || request.aiEditInstruction() == null
                || request.aiEditInstruction().isBlank()) {
            return;
        }
        if (request.baseJobId() == null || request.baseJobId().isBlank()) {
            throw new IllegalArgumentException("aiEditInstruction requires baseJobId");
        }
        AiTimelineEditContext ctx = AiTimelineEditContext.fromSubmit(
                request.tenantId(),
                request.projectId(),
                request.editSessionId(),
                request.baseJobId(),
                request.aiEditIntent(),
                request.aiEditInstruction());
        var result = aiTimelineEditService.editFromBaseJob(
                request.tenantId(), request.baseJobId(), request.aiEditInstruction(), ctx);
        String timelineJson = result.timelineJson();
        if (request.targetSegmentIds() != null && !request.targetSegmentIds().isEmpty()) {
            try {
                var node = com.example.platform.timeline.app.InternalTimelineJson.parse(timelineJson);
                if (node.isObject()) {
                    java.util.Map<String, String> segMeta = new java.util.LinkedHashMap<>();
                    SegmentPlanFilter.embedTargetSegmentIds(segMeta, request.targetSegmentIds());
                    var doc = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                    var meta = doc.has("metadata") && doc.get("metadata").isObject()
                            ? (com.fasterxml.jackson.databind.node.ObjectNode) doc.get("metadata")
                            : com.example.platform.timeline.app.InternalTimelineJson.mapper()
                                    .createObjectNode();
                    segMeta.forEach(meta::put);
                    doc.set("metadata", meta);
                    timelineJson = com.example.platform.timeline.app.InternalTimelineJson.write(doc);
                }
            } catch (Exception e) {
                log.warn("Could not embed targetSegmentIds: {}", e.getMessage());
            }
        }
        renderJobRepository.updateAiScript(jobId, timelineJson);
        log.info("Applied AI timeline edit for job {} from base {}", jobId, request.baseJobId());
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }

    private void assertInitiatorScope(String tenantId, RenderInitiator initiator) {
        if (initiator == null) {
            throw new NullPointerException("initiator must not be null");
        }
        if (!tenantId.equals(initiator.tenantId())) {
            throw new IllegalArgumentException("Render initiator tenant does not match request tenant");
        }
    }

    private void assertProjectBelongsToTenant(String tenantId, String projectId) {
        String projectTenantId = renderJobRepository.findProjectTenantId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!tenantId.equals(projectTenantId)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }
    }
}

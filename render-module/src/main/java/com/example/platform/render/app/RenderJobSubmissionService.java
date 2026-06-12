package com.example.platform.render.app;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.app.timeline.AiTimelineEditContext;
import com.example.platform.render.app.timeline.AiTimelineEditService;
import com.example.platform.render.app.timeline.SegmentPlanFilter;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.Ids;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

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
    private final RenderQuotaService quotaService;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final TimelineScriptParser timelineScriptParser;
    private final EffectTimelineInspector effectTimelineInspector;
    private final RenderProfileResolver renderProfileResolver;
    private final AiTimelineEditService aiTimelineEditService;
    private final com.example.platform.render.app.cache.RenderCacheTenantGuard cacheTenantGuard;

    public RenderJobSubmissionService(DSLContext dsl,
            RenderJobRepository renderJobRepository,
            RenderQuotaService quotaService,
            RenderJobStatusHistoryRepository historyRepository,
            NotificationEventPublisher notificationEventPublisher,
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
        this.quotaService = quotaService;
        this.historyRepository = historyRepository;
        this.notificationEventPublisher = notificationEventPublisher;
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
    public String submit(SubmitRenderJobRequest request) {
        log.info("Submitting render job: tenant={}, project={}, profile={}",
                request.tenantId(), request.projectId(), request.profileOrDefault());

        assertTenantAccess(request.tenantId());
        assertProjectBelongsToTenant(request.tenantId(), request.projectId());

        if (request.baseJobId() != null && !request.baseJobId().isBlank() && cacheTenantGuard != null) {
            cacheTenantGuard.requireBaseJobAccess(
                    request.tenantId(), request.projectId(), request.baseJobId());
        }

        if (!quotaService.checkQuota(request.tenantId(), "render", 1)) {
            return handleQuotaRejected(request);
        }

        return createQueuedJob(request);
    }

    private String handleQuotaRejected(SubmitRenderJobRequest request) {
        String rejectedJobId = Ids.newId("rj");
        String profile = request.profileOrDefault();
        renderJobRepository.createRejected(rejectedJobId, request.projectId(), request.tenantId(),
                "snap_" + rejectedJobId, profile, "Quota exceeded", OffsetDateTime.now());
        historyRepository.record(rejectedJobId, null, RenderJobStatus.REJECTED.name(),
                "Quota exceeded", "QUOTA_EXCEEDED");
        eventPublisher.publishEvent(new RenderJobFailedEvent(
                rejectedJobId, request.projectId(), "Quota exceeded", Instant.now()));
        throw new IllegalStateException("Quota exceeded for tenant: " + request.tenantId());
    }

    private String createQueuedJob(SubmitRenderJobRequest request) {
        String jobId = Ids.newId("rj");
        String profile = request.profileOrDefault();
        String inlineScript = resolveInlineTimelineScript(request);
        if (inlineScript != null) {
            EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(inlineScript);
            profile = renderProfileResolver.resolve(profile, usage.effectKeys(), inlineScript);
        }
        String snapshotId = resolveSnapshotId(request, jobId);

        renderJobRepository.create(jobId, request.projectId(), request.tenantId(),
                snapshotId, profile, RenderJobStatus.QUEUED.name(), OffsetDateTime.now());
        historyRepository.record(jobId, null, RenderJobStatus.QUEUED.name(), "Job created", null);

        notificationEventPublisher.publish(
                new RenderJobCreatedEvent(jobId, request.projectId(), snapshotId, profile, "ffmpeg"));

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
                var node = com.example.platform.render.app.timeline.InternalTimelineJson.parse(timelineJson);
                if (node.isObject()) {
                    java.util.Map<String, String> segMeta = new java.util.LinkedHashMap<>();
                    SegmentPlanFilter.embedTargetSegmentIds(segMeta, request.targetSegmentIds());
                    var doc = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                    var meta = doc.has("metadata") && doc.get("metadata").isObject()
                            ? (com.fasterxml.jackson.databind.node.ObjectNode) doc.get("metadata")
                            : com.example.platform.render.app.timeline.InternalTimelineJson.mapper()
                                    .createObjectNode();
                    segMeta.forEach(meta::put);
                    doc.set("metadata", meta);
                    timelineJson = com.example.platform.render.app.timeline.InternalTimelineJson.write(doc);
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

    private void assertProjectBelongsToTenant(String tenantId, String projectId) {
        String projectTenantId = renderJobRepository.findProjectTenantId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!tenantId.equals(projectTenantId)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }
    }
}

package com.example.platform.render.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.EffectEntitlementPort;
import com.example.platform.render.api.port.RenderJobSubmitContinuation;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.app.cache.RenderCacheHashInvalidationNotifier;
import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import com.example.platform.render.app.planner.PipelineDagExecutorService;
import com.example.platform.render.app.timeline.AiRenderScriptNormalizer;
import com.example.platform.render.app.timeline.AiTimelineEditContext;
import com.example.platform.render.app.timeline.AiTimelineEditService;
import com.example.platform.render.app.timeline.BaseJobTimelineLoader;
import com.example.platform.render.app.timeline.SegmentPlanFilter;
import com.example.platform.render.domain.timeline.TimelinePlatformMetadata;
import com.example.platform.render.app.timeline.IncrementalRenderOrchestrationService;
import com.example.platform.render.app.timeline.TimelineSpecResolver;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderArtifactStorageService;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.render.infrastructure.timeline.EditorTimelineConverter;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.Ids;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Service
public class RenderOrchestratorService implements RenderOrchestratorPort {
    private static final Logger log = LoggerFactory.getLogger(RenderOrchestratorService.class);

    private final DSLContext dsl;
    private final RenderQuotaService quotaService;
    private final AiGatewayPort aiGatewayPort;
    private final RenderProviderRouter renderProviderRouter;
    private final NotificationEventPublisher notificationEventPublisher;
    private final StorageCatalogPort storageCatalogPort;
    private final BlobStorage blobStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final TimelineScriptParser timelineScriptParser;
    private final TimelineSpecResolver timelineSpecResolver;
    private final IncrementalRenderOrchestrationService incrementalRenderOrchestrationService;
    private final RenderArtifactStorageService artifactStorageService;
    private final TimelineSnapshotService timelineSnapshotService;
    private final EditorTimelineConverter editorTimelineConverter;
    private final EffectTimelineInspector effectTimelineInspector;
    private final EffectEntitlementPort effectEntitlementPort;
    private final RenderProfileResolver renderProfileResolver;
    private final RenderWorkerQueueService renderWorkerQueueService;
    private final RenderWorkerQueueProperties renderWorkerQueueProperties;
    private final PipelineDagExecutorService pipelineDagExecutorService;
    private final TimelineExtensionsReader timelineExtensionsReader;
    private final EntitlementPort entitlementPort;
    private final RenderCacheTenantGuard cacheTenantGuard;
    private final RenderCacheHashInvalidationNotifier hashInvalidationNotifier;
    private final AiRenderScriptNormalizer aiRenderScriptNormalizer;
    private final AiTimelineEditService aiTimelineEditService;
    private final BaseJobTimelineLoader baseJobTimelineLoader;
    private final RenderJobSubmitContinuation submitContinuation;

    public RenderOrchestratorService(DSLContext dsl, RenderQuotaService quotaService,
            AiGatewayPort aiGatewayPort, RenderProviderRouter renderProviderRouter,
            NotificationEventPublisher notificationEventPublisher,
            StorageCatalogPort storageCatalogPort,
            BlobStorage blobStorage, ApplicationEventPublisher eventPublisher,
            RenderJobStatusHistoryRepository historyRepository,
            TimelineScriptParser timelineScriptParser,
            TimelineSpecResolver timelineSpecResolver,
            IncrementalRenderOrchestrationService incrementalRenderOrchestrationService,
            RenderArtifactStorageService artifactStorageService,
            TimelineSnapshotService timelineSnapshotService,
            EditorTimelineConverter editorTimelineConverter,
            EffectTimelineInspector effectTimelineInspector,
            RenderProfileResolver renderProfileResolver,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            EffectEntitlementPort effectEntitlementPort,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderWorkerQueueService renderWorkerQueueService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderWorkerQueueProperties renderWorkerQueueProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            PipelineDagExecutorService pipelineDagExecutorService,
            TimelineExtensionsReader timelineExtensionsReader,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            EntitlementPort entitlementPort,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderCacheTenantGuard cacheTenantGuard,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderCacheHashInvalidationNotifier hashInvalidationNotifier,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            AiRenderScriptNormalizer aiRenderScriptNormalizer,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            AiTimelineEditService aiTimelineEditService,
            BaseJobTimelineLoader baseJobTimelineLoader,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderJobSubmitContinuation submitContinuation) {
        this.dsl = dsl;
        this.quotaService = quotaService;
        this.aiGatewayPort = aiGatewayPort;
        this.renderProviderRouter = renderProviderRouter;
        this.notificationEventPublisher = notificationEventPublisher;
        this.storageCatalogPort = storageCatalogPort;
        this.blobStorage = blobStorage;
        this.eventPublisher = eventPublisher;
        this.historyRepository = historyRepository;
        this.timelineScriptParser = timelineScriptParser;
        this.timelineSpecResolver = timelineSpecResolver;
        this.incrementalRenderOrchestrationService = incrementalRenderOrchestrationService;
        this.artifactStorageService = artifactStorageService;
        this.timelineSnapshotService = timelineSnapshotService;
        this.editorTimelineConverter = editorTimelineConverter;
        this.effectTimelineInspector = effectTimelineInspector;
        this.renderProfileResolver = renderProfileResolver;
        this.effectEntitlementPort = effectEntitlementPort;
        this.renderWorkerQueueService = renderWorkerQueueService;
        this.renderWorkerQueueProperties = renderWorkerQueueProperties;
        this.pipelineDagExecutorService = pipelineDagExecutorService;
        this.timelineExtensionsReader = timelineExtensionsReader;
        this.entitlementPort = entitlementPort;
        this.cacheTenantGuard = cacheTenantGuard;
        this.hashInvalidationNotifier = hashInvalidationNotifier;
        this.aiRenderScriptNormalizer = aiRenderScriptNormalizer;
        this.aiTimelineEditService = aiTimelineEditService;
        this.baseJobTimelineLoader = baseJobTimelineLoader;
        this.submitContinuation = submitContinuation;
        this.stateMachine = new RenderJobStateMachine();
    }

    @Override
    @Transactional
    public String submitRenderJob(SubmitRenderJobRequest request) {
        log.info("Submitting render job: tenant={}, project={}, profile={}",
                request.tenantId(), request.projectId(), request.profileOrDefault());

        assertTenantAccess(request.tenantId());
        assertProjectBelongsToTenant(request.tenantId(), request.projectId());
        if (request.baseJobId() != null && !request.baseJobId().isBlank() && cacheTenantGuard != null) {
            cacheTenantGuard.requireBaseJobAccess(
                    request.tenantId(), request.projectId(), request.baseJobId());
        }

        if (!quotaService.checkQuota(request.tenantId(), "render", 1)) {
            String rejectedJobId = Ids.newId("rj");
            String profile = request.profileOrDefault();
            dsl.insertInto(table("render_job"))
                    .columns(field("id"), field("project_id"), field("tenant_id"),
                            field("timeline_snapshot_id"),
                            field("profile"), field("status"), field("created_at"), field("error_message"))
                    .values(rejectedJobId, request.projectId(), request.tenantId(),
                            "snap_" + rejectedJobId, profile,
                            RenderJobStatus.REJECTED.name(), OffsetDateTime.now(),
                            "Quota exceeded")
                    .execute();
            historyRepository.record(rejectedJobId, null, RenderJobStatus.REJECTED.name(),
                    "Quota exceeded", "QUOTA_EXCEEDED");
            eventPublisher.publishEvent(new RenderJobFailedEvent(
                    rejectedJobId, request.projectId(), "Quota exceeded", Instant.now()));
            throw new IllegalStateException("Quota exceeded for tenant: " + request.tenantId());
        }

        String jobId = Ids.newId("rj");
        String profile = request.profileOrDefault();
        String inlineScript = resolveInlineTimelineScript(request);
        if (inlineScript != null) {
            EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(inlineScript);
            profile = renderProfileResolver.resolve(profile, usage.effectKeys(), inlineScript);
        }
        String snapshotId = resolveSnapshotId(request, jobId);

        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("base_job_id"),
                        field("profile"), field("status"), field("created_at"))
                .values(jobId, request.projectId(), request.tenantId(),
                        snapshotId, request.baseJobId(),
                        profile, RenderJobStatus.QUEUED.name(), OffsetDateTime.now())
                .execute();
        historyRepository.record(jobId, null, RenderJobStatus.QUEUED.name(), "Job created", null);

        notificationEventPublisher.publish(
                new RenderJobCreatedEvent(jobId, request.projectId(), snapshotId, profile, "ffmpeg"));

        persistInlineScriptIfPresent(jobId, request);
        applyAiEditInstructionIfPresent(jobId, request);

        if (submitContinuation != null) {
            return submitContinuation.continueAfterSubmit(request.tenantId(), jobId, request);
        }
        return executeExistingRenderJob(request.tenantId(), jobId);
    }

    private void persistInlineScriptIfPresent(String jobId, SubmitRenderJobRequest request) {
        String inline = resolveInlineTimelineScript(request);
        if (inline != null) {
            dsl.update(table("render_job"))
                    .set(field("ai_script"), inline)
                    .where(field("id").eq(jobId))
                    .execute();
        }
    }

    @Override
    @Transactional
    public String executeExistingRenderJob(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        Record job = loadJob(jobId);
        String projectId = job.get(field("project_id"), String.class);
        String jobTenantId = job.get(field("tenant_id"), String.class);
        if (!tenantId.equals(jobTenantId)) {
            throw new IllegalArgumentException("Render job not found for tenant");
        }
        String profile = job.get(field("profile"), String.class);
        String snapshotId = job.get(field("timeline_snapshot_id"), String.class);
        String status = job.get(field("status"), String.class);

        if (RenderJobStatus.COMPLETED.name().equals(status)) {
            return jobId;
        }

        updateStatus(jobId, projectId, RenderJobStatus.valueOf(status), RenderJobStatus.AI_PROCESSING, null);

        String aiScript = resolveRenderScript(jobId, snapshotId, null, projectId);

        EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(aiScript);
        String resolvedProfile = renderProfileResolver.resolve(profile, usage.effectKeys(), aiScript);
        if (!resolvedProfile.equals(profile)) {
            profile = resolvedProfile;
            dsl.update(table("render_job"))
                    .set(field("profile"), profile)
                    .where(field("id").eq(jobId))
                    .execute();
            log.info("Updated render job {} profile to {}", jobId, profile);
        }
        if (effectEntitlementPort != null) {
            effectEntitlementPort.validateEffectAccess(tenantId, null, usage.effectKeys(), usage.packIds());
        }

        dsl.update(table("render_job"))
                .set(field("ai_script"), aiScript)
                .where(field("id").eq(jobId))
                .execute();

        updateStatus(jobId, projectId, RenderJobStatus.AI_PROCESSING, RenderJobStatus.RENDERING, null);

        if (renderWorkerQueueService != null && profile.startsWith("natron_")) {
            renderWorkerQueueService.enqueueNatron(jobId, tenantId, profile);
        }

        if (shouldDeferNatronRender(profile)) {
            log.info("Deferred Natron render job {} to worker queue", jobId);
            return jobId;
        }

        return finishRenderPhase(tenantId, jobId);
    }

    @Override
    @Transactional
    public String finishRenderPhase(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        Record job = loadJob(jobId);
        String projectId = job.get(field("project_id"), String.class);
        String jobTenantId = job.get(field("tenant_id"), String.class);
        if (!tenantId.equals(jobTenantId)) {
            throw new IllegalArgumentException("Render job not found for tenant");
        }
        String status = job.get(field("status"), String.class);
        if (RenderJobStatus.COMPLETED.name().equals(status)) {
            return jobId;
        }

        String profile = job.get(field("profile"), String.class);
        String aiScript = job.get(field("ai_script"), String.class);
        if (aiScript == null || aiScript.isBlank()) {
            String snapshotId = job.get(field("timeline_snapshot_id"), String.class);
            aiScript = resolveRenderScript(jobId, snapshotId, null, projectId);
            dsl.update(table("render_job"))
                    .set(field("ai_script"), aiScript)
                    .where(field("id").eq(jobId))
                    .execute();
        }

        EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(aiScript);
        if (effectEntitlementPort != null) {
            effectEntitlementPort.validateEffectAccess(tenantId, null, usage.effectKeys(), usage.packIds());
        }

        if (!RenderJobStatus.RENDERING.name().equals(status)) {
            updateStatus(jobId, projectId, RenderJobStatus.valueOf(status), RenderJobStatus.RENDERING, null);
        }

        RenderProvider.RenderResult renderResult;
        try {
            assertJobNotInTerminalState(jobId);
            String baseJobId = job.get(field("base_job_id", String.class));
            renderResult = executeRenderWithOptionalDag(jobId, projectId, aiScript, profile, tenantId, baseJobId);
        } catch (Exception e) {
            log.error("Render failed for job {}", jobId, e);
            failJob(jobId, projectId, RenderJobStatus.RENDERING, "RENDER_FAILED", "Render failed: " + e.getMessage());
            throw new IllegalStateException("Render failed", e);
        }

        String artifactId = renderResult.artifactId();
        String storageUri = renderResult.storageUri();

        try {
            String contentType = contentTypeForFormat(renderResult.format());
            String relativePath = renderResult.storageUri().replace("localFsStorageProvider://", "");
            artifactStorageService.uploadJobOutput(jobId, projectId, artifactId, relativePath, contentType);
        } catch (Exception e) {
            log.error("Storage failed for job {}", jobId, e);
            failJob(jobId, projectId, RenderJobStatus.RENDERING, "STORAGE_FAILED", "Storage failed: " + e.getMessage());
            throw new IllegalStateException("Storage failed", e);
        }

        dsl.update(table("render_job"))
                .set(field("artifact_uri"), storageUri)
                .where(field("id").eq(jobId))
                .execute();

        updateStatus(jobId, projectId, RenderJobStatus.RENDERING, RenderJobStatus.COMPLETED, null);
        quotaService.consumeQuota(tenantId, "render", 1);

        notificationEventPublisher.publish(
                new ArtifactCreatedEvent(artifactId, jobId, projectId, storageUri, Instant.now()));
        eventPublisher.publishEvent(new RenderJobCompletedEvent(jobId, projectId, artifactId, storageUri, Instant.now()));

        log.info("Render job {} completed successfully with artifact {}", jobId, artifactId);
        return jobId;
    }

    private RenderProvider.RenderResult executeRenderWithOptionalDag(String jobId, String projectId, String aiScript,
                                                                     String profile, String tenantId,
                                                                     String baseJobId) {
        Optional<TimelineSpec> specOpt = timelineSpecResolver.resolve(aiScript);
        if (pipelineDagExecutorService != null && specOpt.isPresent()
                && pipelineDagExecutorService.shouldExecuteAsDag(specOpt.get(), profile)) {
            TimelineSpec spec = specOpt.get();
            String outputFormat = resolveOutputFormat(spec);
            String tier = resolveTier(tenantId);
            PipelineDagExecutorService.DagExecutionResult dag;
            Optional<IncrementalRenderOrchestrationService.IncrementalExecution> incremental =
                    incrementalRenderOrchestrationService.tryResolve(
                            aiScript, baseJobId, tenantId, spec, profile, tier, outputFormat);
            if (incremental.isPresent()) {
                PipelineExecutionPlan plan = incremental.get().plan();
                var incrementalPlan = incremental.get().incrementalPlan();
                log.info("Executing render job {} via incremental DAG (mode={})", jobId,
                        incrementalPlan.mode());
                publishHashInvalidationIfNeeded(tenantId, projectId, jobId, baseJobId, plan.metadata());
                dag = pipelineDagExecutorService.executeWithPlan(
                        jobId, spec, plan, profile, tier, outputFormat);
            } else {
                log.info("Executing render job {} via pipeline DAG (format={})", jobId, outputFormat);
                dag = pipelineDagExecutorService.execute(jobId, spec, profile, tier, outputFormat);
            }
            if (!dag.success()) {
                throw new IllegalStateException(
                        "Pipeline DAG failed: " + (dag.errorMessage() != null ? dag.errorMessage() : "unknown"));
            }
            String artifactId = dag.pipelineResult() != null && dag.pipelineResult().artifactId() != null
                    ? dag.pipelineResult().artifactId()
                    : Ids.newId("art");
            String storageUri = dag.finalStorageUri() != null ? dag.finalStorageUri()
                    : "localFsStorageProvider://artifacts/" + jobId + "/transcode-output.mp4";
            String format = spec.outputSpec() != null ? spec.outputSpec().format() : "mp4";
            long durationSec = Math.max(1L, Math.round(spec.computeDuration()));
            String resolution = spec.outputSpec() != null ? spec.outputSpec().resolution() : "1920x1080";
            return new RenderProvider.RenderResult(artifactId, storageUri, durationSec, format, resolution);
        }

        EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(aiScript);
        RenderProvider provider = usage.effectKeys().isEmpty()
                ? renderProviderRouter.route(profile)
                : renderProviderRouter.route(profile, usage.effectKeys());
        if (provider == null) {
            throw new IllegalStateException("No render provider for profile: " + profile);
        }
        return provider.render(jobId, aiScript, profile);
    }

    private void publishHashInvalidationIfNeeded(String tenantId, String projectId, String jobId,
                                                 String baseJobId, Map<String, String> planMetadata) {
        if (hashInvalidationNotifier == null || planMetadata == null) {
            return;
        }
        var taskIds = RenderCacheHashInvalidationNotifier.parseInvalidatedTaskIds(planMetadata);
        hashInvalidationNotifier.notifyIfNeeded(tenantId, projectId, jobId, baseJobId, taskIds);
    }

    private String resolveOutputFormat(TimelineSpec spec) {
        var ext = timelineExtensionsReader.fromSpec(spec);
        if (ext.packagingHints() != null && ext.packagingHints().containsKey("format")) {
            return ext.packagingHints().get("format");
        }
        if (spec.outputSpec() != null && spec.outputSpec().format() != null) {
            String fmt = spec.outputSpec().format();
            if (fmt.equalsIgnoreCase("dash") || fmt.equalsIgnoreCase("hls")
                    || fmt.equalsIgnoreCase("cmaf") || fmt.equalsIgnoreCase("dash_drm")) {
                return fmt.toLowerCase();
            }
        }
        return "mp4";
    }

    private String resolveTier(String tenantId) {
        if (entitlementPort != null && tenantId != null && !tenantId.isBlank()) {
            String tier = entitlementPort.getTier(tenantId);
            if (tier != null && !tier.isBlank()) {
                return tier.toUpperCase();
            }
        }
        return "FREE";
    }

    private boolean shouldDeferNatronRender(String profile) {
        return profile != null
                && profile.startsWith("natron_")
                && renderWorkerQueueService != null
                && renderWorkerQueueProperties != null
                && renderWorkerQueueProperties.isEnabled()
                && renderWorkerQueueProperties.isConsumeEnabled();
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
        dsl.update(table("render_job"))
                .set(field("ai_script"), timelineJson)
                .where(field("id").eq(jobId))
                .execute();
        log.info("Applied AI timeline edit for job {} from base {}", jobId, request.baseJobId());
    }

    private String resolveRenderScript(String jobId, String snapshotId, String prompt, String projectId) {
        if (jobId != null) {
            Record row = dsl.select(field("ai_script", String.class))
                    .from(table("render_job"))
                    .where(field("id").eq(jobId))
                    .fetchOne();
            if (row != null) {
                String existing = row.get(field("ai_script", String.class));
                if (existing != null
                        && !existing.isBlank()
                        && timelineScriptParser.isTimelineJson(existing)) {
                    log.info("Using existing ai_script on job {} for render", jobId);
                    return existing.trim();
                }
            }
        }
        Optional<String> snapshotPayload = timelineSnapshotService.findPayload(snapshotId);
        if (snapshotPayload.isPresent()) {
            String payload = snapshotPayload.get().trim();
            if (timelineScriptParser.isTimelineJson(payload)) {
                log.info("Using persisted timeline snapshot {} as render script (project={})", snapshotId, projectId);
                return payload;
            }
            String otioJson = editorTimelineConverter.toOtioJson(payload);
            log.info("Using persisted timeline snapshot {} for render (project={})", snapshotId, projectId);
            return otioJson;
        }
        if (prompt != null && timelineScriptParser.isTimelineJson(prompt)) {
            return prompt.trim();
        }
        if (prompt != null && !prompt.isBlank()) {
            try {
                var chatResult = aiGatewayPort.chat("script-generation", prompt);
                String content = chatResult.content();
                if (aiRenderScriptNormalizer != null) {
                    String tenant = TenantContext.get() != null ? TenantContext.get() : projectId;
                    return aiRenderScriptNormalizer.normalize(
                            tenant,
                            projectId,
                            content,
                            AiTimelineEditContext.of(tenant, projectId));
                }
                return content;
            } catch (Exception e) {
                throw new IllegalStateException("AI script generation failed", e);
            }
        }
        throw new IllegalStateException(
                "No timeline snapshot or prompt available for render (snapshotId=" + snapshotId + ")");
    }

    private Record loadJob(String jobId) {
        Record job = dsl.select(field("project_id"), field("tenant_id"), field("profile"),
                        field("timeline_snapshot_id"), field("base_job_id"), field("status"), field("ai_script"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (job == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return job;
    }

    private void failJob(String jobId, String projectId, RenderJobStatus from, String code, String message) {
        updateStatus(jobId, projectId, from, RenderJobStatus.FAILED, code);
        dsl.update(table("render_job"))
                .set(field("error_message"), message)
                .where(field("id").eq(jobId))
                .execute();
        eventPublisher.publishEvent(new RenderJobFailedEvent(jobId, projectId, message, Instant.now()));
    }

    private void updateStatus(String jobId, String projectId, RenderJobStatus oldStatus,
                            RenderJobStatus newStatus, String errorCode) {
        stateMachine.validateTransition(oldStatus, newStatus);
        dsl.update(table("render_job"))
                .set(field("status"), newStatus.name())
                .where(field("id").eq(jobId))
                .execute();
        historyRepository.record(jobId, oldStatus.name(), newStatus.name(), null, errorCode);
        notificationEventPublisher.publish(
                new RenderJobStatusChangedEvent(jobId, projectId, oldStatus.name(), newStatus.name(), Instant.now()));
    }

    private void assertJobNotInTerminalState(String jobId) {
        Record record = dsl.select(field("status"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record != null) {
            String statusStr = record.get(field("status"), String.class);
            RenderJobStatus status = RenderJobStatus.valueOf(statusStr);
            if (status == RenderJobStatus.CANCELLED) {
                throw new IllegalStateException("Job has been cancelled: " + jobId);
            }
        }
    }

    @Override
    public String loadJobTimelineJson(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        if (cacheTenantGuard != null) {
            cacheTenantGuard.requireJobTenant(tenantId, jobId);
        }
        return baseJobTimelineLoader.loadInternalTimelineJson(jobId, tenantId)
                .orElseGet(() -> {
                    Record r = dsl.select(field("ai_script", String.class))
                            .from(table("render_job"))
                            .where(field("id").eq(jobId))
                            .fetchOne();
                    return r != null && r.get(field("ai_script", String.class)) != null
                            ? r.get(field("ai_script", String.class))
                            : "";
                });
    }

    public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
        Record jobRecord = dsl.select(field("tenant_id"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (jobRecord == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        String jobTenantId = jobRecord.get(field("tenant_id"), String.class);
        assertTenantAccess(jobTenantId);

        return storageCatalogPort.findArtifactsByJob(jobId).stream()
                .map(a -> new ArtifactInfoResponse(a.artifactId(), a.renderJobId(), a.projectId(),
                        a.storageUri(), a.format(), a.resolution(), a.duration(), a.createdAt()))
                .toList();
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }

    private static String contentTypeForFormat(String format) {
        if (format == null) {
            return "video/mp4";
        }
        return switch (format.toLowerCase()) {
            case "dash" -> "application/dash+xml";
            case "hls" -> "application/vnd.apple.mpegurl";
            default -> "video/mp4";
        };
    }

    private void assertProjectBelongsToTenant(String tenantId, String projectId) {
        Record projectRecord = dsl.select(field("tenant_id"))
                .from(table("project"))
                .where(field("id").eq(projectId))
                .fetchOne();
        if (projectRecord == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        String projectTenantId = projectRecord.get(field("tenant_id"), String.class);
        if (!tenantId.equals(projectTenantId)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }
    }
}

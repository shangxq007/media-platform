package com.example.platform.render.app;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.render.api.port.EffectEntitlementPort;
import com.example.platform.render.app.cache.RenderCacheHashInvalidationNotifier;
import com.example.platform.render.app.planner.PipelineDagExecutorService;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.timeline.AiRenderScriptNormalizer;
import com.example.platform.render.app.timeline.AiTimelineEditContext;
import com.example.platform.render.app.timeline.BaseJobTimelineLoader;
import com.example.platform.render.app.timeline.IncrementalRenderOrchestrationService;
import com.example.platform.render.app.timeline.TimelineSpecResolver;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.artifact.ArtifactGraph;
import com.example.platform.render.domain.artifact.ArtifactNode;
import com.example.platform.render.domain.artifact.ArtifactNodeType;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.infrastructure.RenderArtifactStorageService;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.render.infrastructure.artifact.ArtifactGraphRepository;
import com.example.platform.render.infrastructure.providerruntime.engine.ProviderRuntimeEngine;
import com.example.platform.render.infrastructure.timeline.EditorTimelineConverter;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.notification.app.NotificationEventPublisher;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaConsumptionPort;
import com.example.platform.shared.commercial.QuotaConsumptionRequest;
import com.example.platform.shared.web.TenantContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles render job execution: loading jobs, resolving scripts, invoking providers,
 * updating status/artifacts, and handling failures.
 *
 * <p>Extracted from {@link RenderOrchestratorService} to separate the execute/finish
 * paths from the submit and artifact query paths.
 */
@Service
public class RenderJobExecutionService {
    private static final Logger log = LoggerFactory.getLogger(RenderJobExecutionService.class);

    private final RenderJobRepository renderJobRepository;
    private final QuotaConsumptionPort quotaConsumption;
    private final AiGatewayPort aiGatewayPort;
    private final RenderProviderRouter renderProviderRouter;
    private final ProviderRuntimeEngine providerRuntimeEngine;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final TimelineScriptParser timelineScriptParser;
    private final TimelineSpecResolver timelineSpecResolver;
    private final IncrementalRenderOrchestrationService incrementalRenderOrchestrationService;
    private final RenderArtifactStorageService artifactStorageService;
    private final ArtifactGraphRepository artifactGraphRepository;
    private final TimelineSnapshotService timelineSnapshotService;
    private final EditorTimelineConverter editorTimelineConverter;
    private final EffectTimelineInspector effectTimelineInspector;
    private final EffectEntitlementPort effectEntitlementPort;
    private final RenderProfileResolver renderProfileResolver;
    private final RenderWorkerQueueService renderWorkerQueueService;
    private final RenderWorkerQueueProperties renderWorkerQueueProperties;
    private final PipelineDagExecutorService pipelineDagExecutorService;
    private final TimelineExtensionsReader timelineExtensionsReader;
    private final RenderCacheHashInvalidationNotifier hashInvalidationNotifier;
    private final AiRenderScriptNormalizer aiRenderScriptNormalizer;
    private final RenderJobClaimService claimService;
    private final RenderJobFailureService failureService;

    public RenderJobExecutionService(
            RenderJobRepository renderJobRepository,
            QuotaConsumptionPort quotaConsumption,
            AiGatewayPort aiGatewayPort,
            RenderProviderRouter renderProviderRouter,
            ProviderRuntimeEngine providerRuntimeEngine,
            NotificationEventPublisher notificationEventPublisher,
            ApplicationEventPublisher eventPublisher,
            RenderJobStatusHistoryRepository historyRepository,
            TimelineScriptParser timelineScriptParser,
            TimelineSpecResolver timelineSpecResolver,
            IncrementalRenderOrchestrationService incrementalRenderOrchestrationService,
            RenderArtifactStorageService artifactStorageService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ArtifactGraphRepository artifactGraphRepository,
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
            RenderCacheHashInvalidationNotifier hashInvalidationNotifier,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            AiRenderScriptNormalizer aiRenderScriptNormalizer,
            RenderJobClaimService claimService,
            RenderJobFailureService failureService) {
        this.renderJobRepository = renderJobRepository;
        this.quotaConsumption = quotaConsumption;
        this.aiGatewayPort = aiGatewayPort;
        this.renderProviderRouter = renderProviderRouter;
        this.providerRuntimeEngine = providerRuntimeEngine;
        this.notificationEventPublisher = notificationEventPublisher;
        this.eventPublisher = eventPublisher;
        this.historyRepository = historyRepository;
        this.timelineScriptParser = timelineScriptParser;
        this.timelineSpecResolver = timelineSpecResolver;
        this.incrementalRenderOrchestrationService = incrementalRenderOrchestrationService;
        this.artifactStorageService = artifactStorageService;
        this.artifactGraphRepository = artifactGraphRepository;
        this.timelineSnapshotService = timelineSnapshotService;
        this.editorTimelineConverter = editorTimelineConverter;
        this.effectTimelineInspector = effectTimelineInspector;
        this.renderProfileResolver = renderProfileResolver;
        this.effectEntitlementPort = effectEntitlementPort;
        this.renderWorkerQueueService = renderWorkerQueueService;
        this.renderWorkerQueueProperties = renderWorkerQueueProperties;
        this.pipelineDagExecutorService = pipelineDagExecutorService;
        this.timelineExtensionsReader = timelineExtensionsReader;
        this.hashInvalidationNotifier = hashInvalidationNotifier;
        this.aiRenderScriptNormalizer = aiRenderScriptNormalizer;
        this.claimService = claimService;
        this.failureService = failureService;
        this.stateMachine = new RenderJobStateMachine();
    }

    /**
     * Execute an existing render job through the full pipeline:
     * load → resolve script → invoke provider → persist artifact → complete.
     *
     * @return the job ID
     * @throws IllegalArgumentException if job not found or tenant mismatch
     * @throws IllegalStateException if render fails
     */
    public String execute(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        Record job = renderJobRepository.requireJobRecord(jobId);
        String projectId = job.get("project_id", String.class);
        String jobTenantId = job.get("tenant_id", String.class);
        if (!tenantId.equals(jobTenantId)) {
            throw new IllegalArgumentException("Render job not found for tenant");
        }
        String profile = job.get("profile", String.class);
        String snapshotId = job.get("timeline_snapshot_id", String.class);
        String status = job.get("status", String.class);

        if (RenderJobStatus.COMPLETED.name().equals(status)) {
            return jobId;
        }

        // Atomic CAS claim: QUEUED → SELECTING_PROVIDER
        // Committed in REQUIRES_NEW — survives any later failures
        if ("QUEUED".equals(status)) {
            boolean claimed = claimService.claimForSelection(jobId);
            if (!claimed) {
                log.info("Render job {} already claimed by another request", jobId);
                return jobId;
            }
            // Reload after claim to avoid stale entity overwrite
            job = renderJobRepository.requireJobRecord(jobId);
            status = job.get("status", String.class);
        } else if (!"SELECTING_PROVIDER".equals(status) && !"EXECUTING".equals(status)) {
            throw new IllegalStateException("Render job " + jobId + " is in state " + status + ", cannot start");
        }

        String aiScript;
        try {
            aiScript = resolveRenderScript(jobId, snapshotId, null, projectId, tenantId);
        } catch (Exception e) {
            failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage());
            throw e;
        }

        EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(aiScript);
        String resolvedProfile = renderProfileResolver.resolve(profile, usage.effectKeys(), aiScript);
        if (!resolvedProfile.equals(profile)) {
            profile = resolvedProfile;
            renderJobRepository.updateProfile(jobId, profile);
            log.info("Updated render job {} profile to {}", jobId, profile);
        }
        if (effectEntitlementPort != null) {
            effectEntitlementPort.validateEffectAccess(tenantId, null, usage.effectKeys(), usage.packIds());
        }

        renderJobRepository.updateAiScript(jobId, aiScript);

        // Transition to PROVIDER_SELECTED (provider will be selected in executeRenderWithOptionalDag)
        stateMachine.transition(jobId, RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED,
                "Script resolved, ready for provider selection", "RenderJobExecutionService");
        updateStatus(jobId, projectId, RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED, null);

        // Transition to EXECUTING
        stateMachine.transition(jobId, RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING,
                "Starting render execution", "RenderJobExecutionService");
        updateStatus(jobId, projectId, RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING, null);

        if (renderWorkerQueueService != null && profile.startsWith("natron_")) {
            renderWorkerQueueService.enqueueNatron(jobId, tenantId, profile);
        }

        if (shouldDeferNatronRender(profile)) {
            log.info("Deferred Natron render job {} to worker queue", jobId);
            return jobId;
        }

        return finishRenderPhaseInternal(tenantId, jobId);
    }

    /**
     * Execute a just-submitted render job in the same transaction that created it.
     * Skips the REQUIRES_NEW claim because the row hasn't been committed yet.
     *
     * <p>Called only from {@link RenderOrchestratorService#submitRenderJob}.
     */
    @Transactional
    String executeAfterSubmit(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        Record job = renderJobRepository.requireJobRecord(jobId);
        String projectId = job.get("project_id", String.class);
        String jobTenantId = job.get("tenant_id", String.class);
        if (!tenantId.equals(jobTenantId)) {
            throw new IllegalArgumentException("Render job not found for tenant");
        }
        String profile = job.get("profile", String.class);
        String snapshotId = job.get("timeline_snapshot_id", String.class);
        String status = job.get("status", String.class);

        if (RenderJobStatus.COMPLETED.name().equals(status)) {
            return jobId;
        }

        // Same-transaction submit: transition QUEUED → SELECTING_PROVIDER directly
        if ("QUEUED".equals(status)) {
            updateStatus(jobId, projectId, RenderJobStatus.QUEUED, RenderJobStatus.SELECTING_PROVIDER, null);
            status = RenderJobStatus.SELECTING_PROVIDER.name();
        } else if (!"SELECTING_PROVIDER".equals(status) && !"EXECUTING".equals(status)) {
            throw new IllegalStateException("Render job " + jobId + " is in state " + status + ", cannot start");
        }

        String aiScript;
        try {
            aiScript = resolveRenderScript(jobId, snapshotId, null, projectId, tenantId);
        } catch (Exception e) {
            failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage());
            throw e;
        }

        EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(aiScript);
        String resolvedProfile = renderProfileResolver.resolve(profile, usage.effectKeys(), aiScript);
        if (!resolvedProfile.equals(profile)) {
            profile = resolvedProfile;
            renderJobRepository.updateProfile(jobId, profile);
        }
        if (effectEntitlementPort != null) {
            effectEntitlementPort.validateEffectAccess(tenantId, null, usage.effectKeys(), usage.packIds());
        }

        renderJobRepository.updateAiScript(jobId, aiScript);

        stateMachine.transition(jobId, RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED,
                "Script resolved, ready for provider selection", "RenderJobExecutionService");
        updateStatus(jobId, projectId, RenderJobStatus.SELECTING_PROVIDER, RenderJobStatus.PROVIDER_SELECTED, null);

        stateMachine.transition(jobId, RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING,
                "Starting render execution", "RenderJobExecutionService");
        updateStatus(jobId, projectId, RenderJobStatus.PROVIDER_SELECTED, RenderJobStatus.EXECUTING, null);

        if (renderWorkerQueueService != null && profile.startsWith("natron_")) {
            renderWorkerQueueService.enqueueNatron(jobId, tenantId, profile);
        }

        if (shouldDeferNatronRender(profile)) {
            return jobId;
        }

        return finishRenderPhaseInternal(tenantId, jobId);
    }

    /**
     * Complete the render phase for an existing job that is already in RENDERING status.
     * This is the second half of the execution pipeline.
     */
    @Transactional
    public String finishRenderPhase(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        return finishRenderPhaseInternal(tenantId, jobId);
    }

    private String finishRenderPhaseInternal(String tenantId, String jobId) {
        Record job = renderJobRepository.requireJobRecord(jobId);
        String projectId = job.get("project_id", String.class);
        String jobTenantId = job.get("tenant_id", String.class);
        if (!tenantId.equals(jobTenantId)) {
            throw new IllegalArgumentException("Render job not found for tenant");
        }
        String status = job.get("status", String.class);
        if (RenderJobStatus.COMPLETED.name().equals(status)) {
            return jobId;
        }

        String profile = job.get("profile", String.class);
        String aiScript = job.get("ai_script", String.class);
        if (aiScript == null || aiScript.isBlank()) {
            String snapshotId = job.get("timeline_snapshot_id", String.class);
            aiScript = resolveRenderScript(jobId, snapshotId, null, projectId, tenantId);
            renderJobRepository.updateAiScript(jobId, aiScript);
        }

        EffectTimelineInspector.EffectUsage usage = effectTimelineInspector.extractFromScript(aiScript);
        if (effectEntitlementPort != null) {
            effectEntitlementPort.validateEffectAccess(tenantId, null, usage.effectKeys(), usage.packIds());
        }

        // Ensure we're in EXECUTING state
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(status);
        if (currentStatus != RenderJobStatus.EXECUTING && !currentStatus.isTerminal()) {
            stateMachine.transition(jobId, currentStatus, RenderJobStatus.EXECUTING,
                    "Resuming render execution", "RenderJobExecutionService");
            updateStatus(jobId, projectId, currentStatus, RenderJobStatus.EXECUTING, null);
        }

        long startTime = System.currentTimeMillis();

        RenderProvider.RenderResult renderResult;
        try {
            assertJobNotInTerminalState(jobId);
            String baseJobId = job.get("base_job_id", String.class);
            renderResult = executeRenderWithOptionalDag(jobId, projectId, aiScript, profile, tenantId, baseJobId);
        } catch (Exception e) {
            log.error("Render failed for job {}", jobId, e);
            failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());
            throw new IllegalStateException("Render failed", e);
        }

        // Transition to COMPLETING
        stateMachine.transition(jobId, RenderJobStatus.EXECUTING, RenderJobStatus.COMPLETING,
                "Render completed, finalizing artifacts", "RenderJobExecutionService");
        updateStatus(jobId, projectId, RenderJobStatus.EXECUTING, RenderJobStatus.COMPLETING, null);

        String artifactId = renderResult.artifactId();
        String storageUri = renderResult.storageUri();

        try {
            String contentType = contentTypeForFormat(renderResult.format());
            String relativePath = renderResult.storageUri().replace("localFsStorageProvider://", "");
            artifactStorageService.uploadJobOutput(jobId, projectId, artifactId, relativePath, contentType);
        } catch (Exception e) {
            log.error("Storage failed for job {}", jobId, e);
            failureService.recordDurableFailure(jobId, "Storage failed: " + e.getMessage());
            throw new IllegalStateException("Storage failed", e);
        }

        // Create ArtifactGraph with the rendered output
        String contentHash = computeContentHash(storageUri);
        ArtifactNode rootNode = ArtifactNode.create(
                artifactId,
                jobId,
                ArtifactNodeType.fromExtension(renderResult.format()),
                storageUri,
                List.of(), // No parents for root artifact
                contentHash
        );

        ArtifactGraph artifactGraph = ArtifactGraph.create(jobId, rootNode);

        // Add additional artifacts if available (e.g., thumbnail, timeline JSON)
        if (renderResult.duration() > 0) {
            // Create timeline JSON artifact
            String timelineArtifactId = Ids.newId("art-timeline");
            ArtifactNode timelineNode = ArtifactNode.create(
                    timelineArtifactId,
                    jobId,
                    ArtifactNodeType.TIMELINE_JSON,
                    "timeline://" + jobId + "/timeline.json",
                    List.of(artifactId), // Parent is the video artifact
                    computeContentHash("timeline://" + jobId)
            );
            artifactGraph = artifactGraph.addNode(timelineNode);
        }

        // Save artifact graph
        if (artifactGraphRepository != null) {
            artifactGraphRepository.saveGraph(artifactGraph);
            log.info("Saved artifact graph for job {} with {} nodes", jobId, artifactGraph.size());
        }

        // Update job with artifact URI (backward compatibility)
        renderJobRepository.updateArtifactUri(jobId, storageUri);

        // Transition to COMPLETED
        stateMachine.transition(jobId, RenderJobStatus.COMPLETING, RenderJobStatus.COMPLETED,
                "Job successfully completed", "RenderJobExecutionService");
        updateStatus(jobId, projectId, RenderJobStatus.COMPLETING, RenderJobStatus.COMPLETED, null);
        consumeRenderQuota(tenantId, jobId);

        notificationEventPublisher.publish(
                new ArtifactCreatedEvent(artifactId, jobId, projectId, Instant.now()));
        eventPublisher.publishEvent(new RenderJobCompletedEvent(jobId, projectId, artifactId, storageUri, Instant.now()));

        log.info("Render job {} completed successfully with artifact graph {}", jobId, artifactGraph.graphId());
        return jobId;
    }

    // --- Private helpers ---

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

        // Use ProviderRuntimeEngine for provider selection (replaces legacy routing)
        java.util.Set<String> requiredCapabilities = new java.util.HashSet<>(usage.effectKeys());
        ProviderRuntimeEngine.ProviderResolutionRequest resolutionRequest =
                new ProviderRuntimeEngine.ProviderResolutionRequest(
                        jobId,
                        null, // traceId will be generated
                        requiredCapabilities,
                        profile,
                        Map.of("aiScript", aiScript, "tenantId", tenantId)
                );

        ProviderRuntimeEngine.ProviderResolutionResult resolutionResult =
                providerRuntimeEngine.resolveProvider(resolutionRequest);

        if (!resolutionResult.isSuccess()) {
            throw new IllegalStateException("No render provider available for profile: " + profile
                    + " (candidates: " + resolutionResult.candidateNames() + ")");
        }

        RenderProvider provider = resolutionResult.selectedProvider();
        String providerName = resolutionResult.selectedProviderName();

        log.info("[{}] Provider selected: {} (candidates: {}, time: {}ms)",
                resolutionResult.traceId(), providerName,
                resolutionResult.candidateNames(), resolutionResult.resolutionTimeMs());

        // Store trace ID and selected Provider in job for observability
        renderJobRepository.updateTraceId(jobId, resolutionResult.traceId());
        renderJobRepository.updateSelectedProvider(jobId, providerName);

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
        return "ENTITLED";
    }

    private void consumeRenderQuota(String tenantId, String jobId) {
        Instant now = Instant.now();
        YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
        Instant periodStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        quotaConsumption.consume(new QuotaConsumptionRequest(
                PrincipalRef.tenantScoped(tenantId, PrincipalType.ORGANIZATION, tenantId),
                "render.job.create", 1, periodStart, periodEnd,
                "render-job:" + jobId + ":completion",
                "render-job:" + jobId, "render completion " + jobId, now));
    }

    private boolean shouldDeferNatronRender(String profile) {
        return profile != null
                && profile.startsWith("natron_")
                && renderWorkerQueueService != null
                && renderWorkerQueueProperties != null
                && renderWorkerQueueProperties.isEnabled()
                && renderWorkerQueueProperties.isConsumeEnabled();
    }

    private String resolveRenderScript(String jobId, String snapshotId, String prompt, String projectId, String tenantId) {
        if (jobId != null) {
            Optional<String> existing = renderJobRepository.findAiScriptById(jobId);
            if (existing.isPresent() && !existing.get().isBlank()
                    && timelineScriptParser.isTimelineJson(existing.get())) {
                log.info("Using existing ai_script on job {} for render", jobId);
                return existing.get().trim();
            }
        }
        Optional<String> snapshotPayload = timelineSnapshotService
                .findOwnedById(projectId, tenantId, snapshotId)
                .map(TimelineSnapshotService.SnapshotInfo::payloadJson);
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
                            tenant, projectId, content, AiTimelineEditContext.of(tenant, projectId));
                }
                return content;
            } catch (Exception e) {
                throw new IllegalStateException("AI script generation failed", e);
            }
        }
        throw new IllegalStateException(
                "No timeline snapshot or prompt available for render (snapshotId=" + snapshotId + ")");
    }

    private void failJob(String jobId, String projectId, RenderJobStatus from, String code, String message) {
        updateStatus(jobId, projectId, from, RenderJobStatus.FAILED, code);
        renderJobRepository.updateErrorMessage(jobId, message);
        eventPublisher.publishEvent(new RenderJobFailedEvent(jobId, projectId, message, Instant.now()));
    }

    private void updateStatus(String jobId, String projectId, RenderJobStatus oldStatus,
                              RenderJobStatus newStatus, String errorCode) {
        stateMachine.validateTransition(oldStatus, newStatus);
        renderJobRepository.updateStatus(jobId, newStatus.name());
        historyRepository.record(jobId, oldStatus.name(), newStatus.name(), null, errorCode);
        notificationEventPublisher.publish(
                new RenderJobStatusChangedEvent(jobId, projectId, oldStatus.name(), newStatus.name(), Instant.now()));
    }

    private void assertJobNotInTerminalState(String jobId) {
        if (renderJobRepository.isCancelled(jobId)) {
            throw new IllegalStateException("Job has been cancelled: " + jobId);
        }
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

    /**
     * Compute a content hash for deduplication.
     * In production, this would hash the actual file content.
     */
    private String computeContentHash(String uri) {
        if (uri == null) return "";
        // Simple hash based on URI (in production, hash actual content)
        return "hash-" + Integer.toHexString(uri.hashCode());
    }
}

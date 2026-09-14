package com.example.platform.render.app;

import com.example.platform.timeline.api.revision.TimelineSnapshotView;
import com.example.platform.timeline.api.revision.TimelineSnapshotQueries;
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
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.render.infrastructure.providerruntime.engine.ProviderRuntimeEngine;
import com.example.platform.render.infrastructure.timeline.EditorTimelineConverter;
import com.example.platform.render.api.event.RenderJobCompletedEvent;
import com.example.platform.render.api.event.RenderJobFailedEvent;
import com.example.platform.render.api.event.RenderJobStatusChangedEvent;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.entitlement.api.commercial.QuotaConsumptionPort;
import com.example.platform.entitlement.api.commercial.QuotaConsumptionRequest;
import com.example.platform.shared.web.TenantContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
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
    private final AiGatewayPort aiGatewayPort;
    private final RenderProviderRouter renderProviderRouter;
    private final ProviderRuntimeEngine providerRuntimeEngine;
    private final RenderJobStateMachine stateMachine;
    private final TimelineScriptParser timelineScriptParser;
    private final TimelineSpecResolver timelineSpecResolver;
    private final IncrementalRenderOrchestrationService incrementalRenderOrchestrationService;
    private final RenderJobLifecycleService lifecycle;
    private final TimelineSnapshotQueries timelineSnapshotService;
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
    private final com.example.platform.render.api.context.ExecutionContextQueries executionContexts;
    private final RenderJobFailureService failureService;

    public RenderJobExecutionService(
            RenderJobRepository renderJobRepository,
            AiGatewayPort aiGatewayPort,
            RenderProviderRouter renderProviderRouter,
            ProviderRuntimeEngine providerRuntimeEngine,
            TimelineScriptParser timelineScriptParser,
            TimelineSpecResolver timelineSpecResolver,
            IncrementalRenderOrchestrationService incrementalRenderOrchestrationService,
            RenderJobLifecycleService lifecycle,
            TimelineSnapshotQueries timelineSnapshotService,
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
            RenderJobFailureService failureService, com.example.platform.render.api.context.ExecutionContextQueries executionContexts) {
        this.renderJobRepository = renderJobRepository;
        this.aiGatewayPort = aiGatewayPort;
        this.renderProviderRouter = renderProviderRouter;
        this.providerRuntimeEngine = providerRuntimeEngine;
        this.timelineScriptParser = timelineScriptParser;
        this.timelineSpecResolver = timelineSpecResolver;
        this.incrementalRenderOrchestrationService = incrementalRenderOrchestrationService;
        this.lifecycle = lifecycle;
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
        this.executionContexts = executionContexts;
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
        var acceptedContext = executionContexts.get(tenantId, projectId, jobId);
        if (!tenantId.equals(acceptedContext.tenantId()) || !projectId.equals(acceptedContext.projectId())
                || !tenantId.equals(acceptedContext.consumptionPrincipalId())
                || !java.util.Objects.equals(job.get("initiator_id",String.class),acceptedContext.actorId())
                || !java.util.Objects.equals(job.get("initiator_type",String.class),acceptedContext.actorKind().name()))
            throw new IllegalStateException("Persisted execution scope mismatch");
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
            failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage(), com.example.platform.render.api.event.RenderFailureReason.INPUT_RESOLUTION_FAILED);
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
            failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage(), com.example.platform.render.api.event.RenderFailureReason.INPUT_RESOLUTION_FAILED);
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
    public String finishRenderPhase(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        return finishRenderPhaseInternal(tenantId, jobId);
    }

    private String finishRenderPhaseInternal(String tenantId, String jobId) {
        Record job = renderJobRepository.requireJobRecord(jobId);
        RenderInitiator initiator = RenderJobRepository.initiatorFrom(job);
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
            failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage(), com.example.platform.render.api.event.RenderFailureReason.EXECUTION_FAILED);
            throw new IllegalStateException("Render failed", e);
        }

        try {
            String uri=renderResult.storageUri();
            String prefix="localFsStorageProvider://";
            if(uri==null || !uri.startsWith(prefix))throw new IllegalArgumentException("unsupported Render output location");
            lifecycle.complete(tenantId,jobId,uri.substring(prefix.length()),contentTypeForFormat(renderResult.format()));
            return jobId;
        } catch(RuntimeException failure) {
            // A newly submitted job belongs to its caller transaction; rollback must not
            // invoke an independent failure writer against the same uncommitted row.
            if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                failureService.recordDurableFailure(jobId, "Output acceptance failed: "+failure.getMessage(), com.example.platform.render.api.event.RenderFailureReason.OUTPUT_REJECTED);
            throw failure;
        }
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
            if(dag.pipelineResult()==null || dag.finalStorageUri()==null || dag.finalStorageUri().isBlank())
                throw new IllegalStateException("DAG did not provide an output result");
            String artifactId=dag.pipelineResult().artifactId(); // provider output tag, never canonical Artifact identity
            String storageUri=dag.finalStorageUri();
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
                        Map.of("aiScript", aiScript), tenantId, projectId
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
                .map(TimelineSnapshotView::payloadJson);
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

    private void updateStatus(String jobId, String projectId, RenderJobStatus oldStatus,
                              RenderJobStatus newStatus, String errorCode) {
        lifecycle.transition(jobId,projectId,oldStatus,newStatus,errorCode);
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
        if(format==null)throw new IllegalArgumentException("Render output format required");
        return switch(format.toLowerCase(java.util.Locale.ROOT)) {
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> throw new IllegalArgumentException("unsupported Render output format: "+format);
        };
    }
}

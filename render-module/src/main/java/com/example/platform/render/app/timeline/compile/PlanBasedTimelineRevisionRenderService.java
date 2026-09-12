package com.example.platform.render.app.timeline.compile;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.output.RenderProductProvenance;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.TimelineInputProductResolver;
import com.example.platform.render.app.timeline.TimelineRenderJobMapper;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.compile.ArtifactDependencyGraph;
import com.example.platform.render.domain.compile.LogicalCapabilityGraph;
import com.example.platform.render.domain.compile.NormalizedTimeline;
import com.example.platform.render.domain.compile.executionplan.ExecutionEnvironmentTarget;
import com.example.platform.render.domain.compile.executionplan.ExecutionPolicy;
import com.example.platform.render.domain.compile.executionplan.RenderExecutionPlan;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Plan-based render service that executes TimelineRevision rendering
 * through the compile pipeline and LocalExecutionPlanRunner.
 *
 * <p>Internal only — bridges the existing render API to the plan-based execution path.</p>
 *
 * <p>This service reuses all existing services (StorageRuntime, ProductRuntime,
 * Provider, etc.) but orchestrates them through the execution plan.</p>
 */
@Service
public class PlanBasedTimelineRevisionRenderService {

    private static final Logger log = LoggerFactory.getLogger(PlanBasedTimelineRevisionRenderService.class);

    private final com.example.platform.timeline.app.TimelineRevisionQueryService revisionQueryService;
    private final TimelineSnapshotService snapshotService;
    private final TimelineRenderJobMapper mapper;
    private final TimelineScriptParser parser;
    private final TimelineInputProductResolver inputProductResolver;
    private final TimelineNormalizationService normalizer;
    private final ArtifactGraphCompiler artifactCompiler;
    private final CapabilityGraphCompiler capabilityCompiler;
    private final RenderExecutionPlanCompiler planCompiler;
    private final RenderPlanPolicyGuard policyGuard;
    private final LocalExecutionPlanRunner planRunner;
    private final RenderInputMaterializationService materializationService;
    private final RenderOutputRegistrationService registrationService;
    private final ProductRuntimeService productRuntime;
    private final StorageRuntimeService storageRuntime;
    private final Path storageRoot;
    private final RenderAuditRecorder auditRecorder;
    private final com.example.platform.render.domain.compile.remotion.ProviderExecutionDocumentGenerationService docGenerationService;

    @Autowired
    public PlanBasedTimelineRevisionRenderService(
            com.example.platform.timeline.app.TimelineRevisionQueryService revisionQueryService,
            TimelineSnapshotService snapshotService,
            TimelineRenderJobMapper mapper,
            TimelineScriptParser parser,
            TimelineInputProductResolver inputProductResolver,
            TimelineNormalizationService normalizer,
            ArtifactGraphCompiler artifactCompiler,
            CapabilityGraphCompiler capabilityCompiler,
            RenderExecutionPlanCompiler planCompiler,
            RenderPlanPolicyGuard policyGuard,
            LocalExecutionPlanRunner planRunner,
            RenderInputMaterializationService materializationService,
            RenderOutputRegistrationService registrationService,
            ProductRuntimeService productRuntime,
            StorageRuntimeService storageRuntime,
            Path storageRoot) {
        this(revisionQueryService, snapshotService, mapper, parser, inputProductResolver,
                normalizer, artifactCompiler, capabilityCompiler, planCompiler, policyGuard, planRunner,
                materializationService, registrationService, productRuntime,
                storageRuntime, storageRoot, null);
    }

    public PlanBasedTimelineRevisionRenderService(
            com.example.platform.timeline.app.TimelineRevisionQueryService revisionQueryService,
            TimelineSnapshotService snapshotService,
            TimelineRenderJobMapper mapper,
            TimelineScriptParser parser,
            TimelineInputProductResolver inputProductResolver,
            TimelineNormalizationService normalizer,
            ArtifactGraphCompiler artifactCompiler,
            CapabilityGraphCompiler capabilityCompiler,
            RenderExecutionPlanCompiler planCompiler,
            RenderPlanPolicyGuard policyGuard,
            LocalExecutionPlanRunner planRunner,
            RenderInputMaterializationService materializationService,
            RenderOutputRegistrationService registrationService,
            ProductRuntimeService productRuntime,
            StorageRuntimeService storageRuntime,
            Path storageRoot,
            RenderAuditRecorder auditRecorder) {
        this.revisionQueryService = revisionQueryService;
        this.snapshotService = snapshotService;
        this.mapper = mapper;
        this.parser = parser;
        this.inputProductResolver = inputProductResolver;
        this.normalizer = normalizer;
        this.artifactCompiler = artifactCompiler;
        this.capabilityCompiler = capabilityCompiler;
        this.planCompiler = planCompiler;
        this.policyGuard = policyGuard;
        this.planRunner = planRunner;
        this.materializationService = materializationService;
        this.registrationService = registrationService;
        this.productRuntime = productRuntime;
        this.storageRuntime = storageRuntime;
        this.storageRoot = storageRoot;
        this.auditRecorder = auditRecorder;
        this.docGenerationService = new com.example.platform.render.domain.compile.remotion.ProviderExecutionDocumentGenerationService();
    }

    /**
     * Render with correlation context — enriched with graph/plan IDs as compile progresses.
     */
    public TimelineRevisionRenderService.RevisionRenderResult render(
            String projectId, String revisionId, String outputProfile,
            RenderCorrelationContext correlation) {
        RenderCorrelationContext corr = correlation != null ? correlation
                : RenderCorrelationContext.create(projectId, revisionId, "PLAN_BASED");
        return doRender(projectId, revisionId, outputProfile, corr);
    }

    /**
     * Render a TimelineRevision through the plan-based execution pipeline.
     */
    public TimelineRevisionRenderService.RevisionRenderResult render(
            String projectId, String revisionId, String outputProfile) {
        return doRender(projectId, revisionId, outputProfile, null);
    }

    private TimelineRevisionRenderService.RevisionRenderResult doRender(
            String projectId, String revisionId, String outputProfile,
            RenderCorrelationContext correlation) {

        log.info("Plan-based render requested: project={} revision={} profile={}",
                projectId, revisionId, outputProfile);

        RenderCorrelationContext corr = correlation != null ? correlation
                : RenderCorrelationContext.create(projectId, revisionId, "PLAN_BASED");

        // 1. Load TimelineRevision (ownership-scoped: project + tenant participate in query)
        var revisionOpt = revisionQueryService.findById(projectId, TenantContext.get(), revisionId);
        if (revisionOpt.isEmpty()) {
            throw new IllegalArgumentException("Timeline revision not found: " + revisionId);
        }
        var revision = revisionOpt.get();

        String tenantId = revision.tenantId();
        String snapshotId = revision.snapshotId();

        // 2. Load snapshot and parse to TimelineSpec (ownership-scoped)
        var payloadOpt = snapshotService.findOwnedById(projectId, tenantId, snapshotId)
                .map(TimelineSnapshotService.SnapshotInfo::payloadJson);
        if (payloadOpt.isEmpty()) {
            throw new IllegalStateException("Snapshot not found: " + snapshotId);
        }
        String timelineJson = payloadOpt.get();
        var specOpt = parser.parse(timelineJson);
        if (specOpt.isEmpty()) {
            throw new IllegalStateException("Failed to parse timeline: " + revisionId);
        }
        TimelineSpec spec = specOpt.get();

        // 3. Map to render job request
        var mappingResult = mapper.toRenderJobRequest(
                tenantId, projectId, spec, outputProfile, revisionId, snapshotId);
        String renderJobId = ("rj_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        corr = corr.withRenderJobId(renderJobId);

        // 4. Resolve input Products
        var resolverResult = inputProductResolver.resolve(mappingResult.sourceAssetIds());
        if (!resolverResult.valid()) {
            throw new IllegalStateException("Input resolution failed: " + resolverResult.failureReason());
        }
        List<String> inputProductIds = resolverResult.inputProductIds();
        corr = corr.withInputProductIds(inputProductIds);
        String primaryInputProductId = inputProductIds.get(0);

        // 5. Compile pipeline with correlation enrichment
        NormalizedTimeline timeline = normalizer.normalize(spec, projectId);
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        corr = corr.withGraphIds(artifactGraph.graphId(), null);
        emitAudit(RenderAuditEventType.ARTIFACT_GRAPH_COMPILED, corr,
                "Artifact graph compiled: " + artifactGraph.graphId());

        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);
        corr = corr.withGraphIds(artifactGraph.graphId(), capGraph.graphId());
        emitAudit(RenderAuditEventType.CAPABILITY_GRAPH_COMPILED, corr,
                "Capability graph compiled: " + capGraph.graphId());

        // 6. Compile the provider-neutral execution plan. Provider document
        // generation remains available at the provider-runtime boundary.
        RenderExecutionPlan executionPlan = planCompiler.compile(
                capGraph, ExecutionPolicy.production());
        corr = corr.withPlanIds(capGraph.graphId(), executionPlan.planId().toString());
        emitAudit(RenderAuditEventType.RENDER_EXECUTION_PLAN_COMPILED, corr,
                "Execution plan compiled: " + executionPlan.planId());

        // 8. Build execution context
        Path outputDir = storageRoot.resolve("render-output").resolve(renderJobId);
        LocalExecutionPlanContext context = new LocalExecutionPlanContext(
                renderJobId, tenantId, projectId, revisionId, snapshotId,
                timelineJson, outputProfile,
                inputProductIds, primaryInputProductId, null,
                storageRoot, outputDir, "output.mp4",
                mappingResult.width(), mappingResult.height(), mappingResult.fps(),
                mappingResult.duration(), mappingResult.hasSubtitles(),
                mappingResult.outputFormat(),
                Map.of());

        // 9. Execute through plan runner
        LocalExecutionPlanRunResult runResult = planRunner.run(executionPlan, context);

        if (!runResult.isSuccess()) {
            throw new IllegalStateException(
                    "Plan-based render failed: " + runResult.message());
        }

        // 10. Build response (same as existing service)
        // Output product was registered during REGISTER_OUTPUT step
        // We need to find the output product ID from the registration step result
        String outputProductId = runResult.stepResults().stream()
                .filter(r -> "REGISTER_OUTPUT".equals(r.stepType()))
                .filter(LocalExecutionPlanStepResult::isSuccess)
                .map(r -> extractProductId(r.message()))
                .filter(id -> id != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No output product ID found in registration step result"));

        corr = corr.withOutputProductId(outputProductId);

        var outputProductOpt = productRuntime.find(outputProductId);
        if (outputProductOpt.isEmpty()) {
            throw new IllegalStateException("Output product not found: " + outputProductId);
        }
        Product outputProduct = outputProductOpt.get();

        log.info("Plan-based render completed: revision={} product={} status={}",
                revisionId, outputProduct.productId(), outputProduct.status());

        return new TimelineRevisionRenderService.RevisionRenderResult(
                renderJobId, revisionId, snapshotId,
                outputProduct.productId(), outputProduct.status().name(),
                outputProduct.storageReferenceId(), outputProduct.mimeType(),
                mappingResult.outputFormat(),
                mappingResult.width(), mappingResult.height(), mappingResult.fps(),
                mappingResult.duration(), mappingResult.hasSubtitles(),
                "typed-provider-plugin", "plan-based-timeline-revision-render",
                inputProductIds, inputProductIds.size());
    }

    private String extractProductId(String message) {
        if (message != null && message.contains("product=")) {
            int start = message.indexOf("product=") + 8;
            int end = message.indexOf(",", start);
            if (end < 0) end = message.length();
            return message.substring(start, end).trim();
        }
        return null;
    }

    private void emitAudit(RenderAuditEventType type, RenderCorrelationContext corr, String message) {
        if (auditRecorder == null) return;
        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(type)
                .fromCorrelation(corr)
                .message(message)
                .build());
    }
}

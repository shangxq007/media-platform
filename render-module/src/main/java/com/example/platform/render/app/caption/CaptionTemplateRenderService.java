package com.example.platform.render.app.caption;

import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.*;
import com.example.platform.render.app.timeline.compile.*;
import com.example.platform.render.domain.caption.*;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Internal service for Caption Template Render MVP.
 * Validates, adapts to TimelineSpec, executes through PLAN_BASED pipeline.
 * Does NOT call FFmpeg directly. Internal only.
 */
@Service
public class CaptionTemplateRenderService {

    private static final Logger log = LoggerFactory.getLogger(CaptionTemplateRenderService.class);

    private final CaptionTemplateRenderContractValidator validator;
    private final CaptionTemplateTimelineAdapter adapter;
    private final CaptionTemplateRenderResultMapper resultMapper;
    private final TimelineNormalizationService normalizer;
    private final ArtifactGraphCompiler artifactCompiler;
    private final CapabilityGraphCompiler capabilityCompiler;
    private final ProviderBindingCompiler bindingCompiler;
    private final ProviderExecutionDocumentDraftCompiler draftCompiler;
    private final RenderExecutionPlanCompiler planCompiler;
    private final RenderPlanPolicyGuard policyGuard;
    private final LocalExecutionPlanRunner planRunner;
    private final RenderInputMaterializationService materializationService;
    private final RenderOutputRegistrationService registrationService;
    private final ProductRuntimeService productRuntime;
    private final StorageRuntimeService storageRuntime;
    private final RenderToolCapabilityInventory toolInventory;
    private final TimelineInputProductResolver inputProductResolver;
    private final Path storageRoot;

    public CaptionTemplateRenderService(
            CaptionTemplateRenderContractValidator validator,
            CaptionTemplateTimelineAdapter adapter,
            CaptionTemplateRenderResultMapper resultMapper,
            TimelineNormalizationService normalizer,
            ArtifactGraphCompiler artifactCompiler,
            CapabilityGraphCompiler capabilityCompiler,
            ProviderBindingCompiler bindingCompiler,
            ProviderExecutionDocumentDraftCompiler draftCompiler,
            RenderExecutionPlanCompiler planCompiler,
            RenderPlanPolicyGuard policyGuard,
            LocalExecutionPlanRunner planRunner,
            RenderInputMaterializationService materializationService,
            RenderOutputRegistrationService registrationService,
            ProductRuntimeService productRuntime,
            StorageRuntimeService storageRuntime,
            RenderToolCapabilityInventory toolInventory,
            TimelineInputProductResolver inputProductResolver,
            Path storageRoot) {
        this.validator = validator;
        this.adapter = adapter;
        this.resultMapper = resultMapper;
        this.normalizer = normalizer;
        this.artifactCompiler = artifactCompiler;
        this.capabilityCompiler = capabilityCompiler;
        this.bindingCompiler = bindingCompiler;
        this.draftCompiler = draftCompiler;
        this.planCompiler = planCompiler;
        this.policyGuard = policyGuard;
        this.planRunner = planRunner;
        this.materializationService = materializationService;
        this.registrationService = registrationService;
        this.productRuntime = productRuntime;
        this.storageRuntime = storageRuntime;
        this.toolInventory = toolInventory;
        this.inputProductResolver = inputProductResolver;
        this.storageRoot = storageRoot;
    }

    public CaptionTemplateRenderResult render(CaptionTemplateRenderRequest request) {
        // 1. Validate
        CaptionTemplateValidationResult validation = validator.validate(request);
        if (!validation.valid()) {
            return resultMapper.mapValidationFailure(validation.errors());
        }

        // 2. Adapt to TimelineSpec
        TimelineSpec spec = adapter.adapt(request);
        String renderJobId = Ids.newId("rj");

        // Resolve sourceProductId (asset ID) to actual Product IDs
        var resolverResult = inputProductResolver.resolve(List.of(request.sourceProductId()));
        if (!resolverResult.valid()) {
            return resultMapper.mapFailure(renderJobId,
                    "Input product resolution failed: " + resolverResult.failureReason());
        }
        List<String> inputProductIds = resolverResult.inputProductIds();
        String primaryInputProductId = inputProductIds.get(0);

        // 3. Compile pipeline
        NormalizedTimeline timeline = normalizer.normalize(spec, request.projectId());
        ArtifactDependencyGraph artifactGraph = artifactCompiler.compile(timeline);
        LogicalCapabilityGraph capGraph = capabilityCompiler.compile(artifactGraph);

        var ffmpegCandidate = new ProviderBindingCompiler.ProviderCandidate(
                "ffmpeg",
                com.example.platform.render.infrastructure.ProviderStatus.PRODUCTION,
                com.example.platform.render.infrastructure.ProviderType.RENDER,
                "P0", true, toolInventory.isToolAvailable("ffmpeg"),
                toolInventory.isToolAvailable("ffmpeg") ? "available" : null,
                capGraph.nodes().stream()
                        .flatMap(n -> n.requirement() != null
                                ? n.requirement().requiredCapabilities().stream()
                                : java.util.stream.Stream.empty())
                        .distinct().toList(),
                List.of());

        ProviderBindingPlan bindingPlan = bindingCompiler.compile(
                capGraph, List.of(ffmpegCandidate), "PRODUCTION");
        List<ProviderExecutionDocumentDraft> drafts = draftCompiler.compile(bindingPlan);
        RenderExecutionPlan executionPlan = planCompiler.compile(
                bindingPlan, drafts, ExecutionPolicy.production());

        // 4. Build execution context
        CaptionOutputProfileSpec profile = request.effectiveOutputProfile();
        Path outputDir = storageRoot.resolve("render-output").resolve(renderJobId);
        double totalDuration = request.captionSegments().stream()
                .mapToDouble(CaptionSegmentSpec::endSeconds).max().orElse(5.0);

        LocalExecutionPlanContext context = new LocalExecutionPlanContext(
                renderJobId, "tenant-1", request.projectId(),
                "caption-template", "snapshot-" + renderJobId,
                "{}", "default_1080p",
                inputProductIds, primaryInputProductId, null,
                storageRoot, outputDir, "output.mp4",
                profile.width(), profile.height(), (int) Math.round(profile.fps()),
                totalDuration, true, profile.container(), Map.of());

        // 5. Execute
        LocalExecutionPlanRunResult runResult = planRunner.run(executionPlan, context);

        if (!runResult.isSuccess()) {
            return resultMapper.mapFailure(renderJobId, runResult.message());
        }

        // 6. Extract output product
        String outputProductId = runResult.stepResults().stream()
                .filter(r -> "REGISTER_OUTPUT".equals(r.stepType()))
                .filter(LocalExecutionPlanStepResult::isSuccess)
                .map(r -> extractProductId(r.message()))
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);

        if (outputProductId == null) {
            return resultMapper.mapFailure(renderJobId, "No output product registered");
        }

        var outputProductOpt = productRuntime.find(outputProductId);
        if (outputProductOpt.isEmpty()) {
            return resultMapper.mapFailure(renderJobId, "Output product not found");
        }

        Product outputProduct = outputProductOpt.get();
        return CaptionTemplateRenderResult.success(
                renderJobId, outputProduct.productId(), profile);
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
}

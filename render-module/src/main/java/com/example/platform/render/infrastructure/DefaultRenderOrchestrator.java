package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.font.FontManifestResolver;
import com.example.platform.render.infrastructure.font.FontPreflightResult;
import com.example.platform.render.infrastructure.font.RenderJobFontPreflight;
import com.example.platform.render.infrastructure.remotion.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class DefaultRenderOrchestrator implements RenderOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(DefaultRenderOrchestrator.class);

    private final RenderPlanner planner;
    private final RenderJobFontPreflight fontPreflight;
    private final FontManifestResolver fontResolver;
    private final RenderProviderRegistry registry;
    private final RenderEnvironmentChecker environmentChecker;

    public DefaultRenderOrchestrator(RenderPlanner planner,
                                      RenderJobFontPreflight fontPreflight,
                                      FontManifestResolver fontResolver,
                                      RenderProviderRegistry registry,
                                      RenderEnvironmentChecker environmentChecker) {
        this.planner = planner;
        this.fontPreflight = fontPreflight;
        this.fontResolver = fontResolver;
        this.registry = registry;
        this.environmentChecker = environmentChecker;
    }

    @Override
    public RenderResult execute(RenderJob job) {
        Instant startedAt = Instant.now();
        List<RenderStepResult> stepResults = new ArrayList<>();
        List<RenderArtifact> allArtifacts = new ArrayList<>();
        boolean fallbackOccurred = false;

        try {
            FontPreflightResult preflight = fontPreflight.preflight(job);
            if (!preflight.passed()) {
                return RenderResult.failed(job.id(), "Font preflight failed: " + preflight.errors());
            }

            RenderPlan plan = planner.plan(job);
            Map<String, RenderArtifact> artifactByStep = new LinkedHashMap<>();

            for (RenderStep step : plan.steps()) {
                RenderStepResult stepResult = executeStep(step, job, artifactByStep);
                stepResults.add(stepResult);
                if (stepResult.fallbackUsed()) fallbackOccurred = true;
                if (stepResult.isFailed()) {
                    RenderExecutionTrace trace = new RenderExecutionTrace(
                            job.id(), job.jobType(), job.mode(),
                            stepResults, allArtifacts, false, fallbackOccurred,
                            startedAt, Instant.now());
                    return RenderResult.failed(job.id(), "Step failed: " + stepResult.stepId(), trace);
                }
                if (stepResult.outputArtifacts() != null) {
                    for (RenderArtifact artifact : stepResult.outputArtifacts()) {
                        allArtifacts.add(artifact);
                        artifactByStep.put(artifact.createdByStepId(), artifact);
                    }
                }
            }

            RenderArtifact finalOutput = allArtifacts.stream()
                    .filter(a -> a.artifactType() == RenderArtifactType.FINAL_OUTPUT)
                    .findFirst().orElse(null);

            RenderExecutionTrace trace = new RenderExecutionTrace(
                    job.id(), job.jobType(), job.mode(),
                    stepResults, allArtifacts, true, fallbackOccurred,
                    startedAt, Instant.now());

            String artifactId = finalOutput != null ? finalOutput.artifactId() : "none";
            String storageUri = finalOutput != null ? finalOutput.url() : null;
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

            return new RenderResult(job.id(), artifactId, storageUri, durationMs,
                    finalOutput != null ? finalOutput.mimeType() : "unknown",
                    finalOutput != null ? finalOutput.width() + "x" + finalOutput.height() : "unknown",
                    true, "orchestrator", trace.jobId(), trace.jobId(),
                    "All " + stepResults.size() + " steps completed", trace);
        } catch (Exception e) {
            RenderExecutionTrace trace = new RenderExecutionTrace(
                    job.id(), job.jobType(), job.mode(),
                    stepResults, allArtifacts, false, fallbackOccurred,
                    startedAt, Instant.now());
            return RenderResult.failed(job.id(), "Orchestrator error: " + e.getMessage(), trace);
        }
    }

    private RenderStepResult makeResult(String stepId, String providerName, String providerType,
                                         String status, List<RenderArtifact> artifacts,
                                         List<String> warnings, List<String> errors,
                                         long durationMs, boolean fallbackUsed,
                                         Instant startedAt) {
        List<RenderArtifact> safeArtifacts = artifacts != null ? artifacts : java.util.Collections.<RenderArtifact>emptyList();
        List<String> safeWarnings = warnings != null ? warnings : java.util.Collections.<String>emptyList();
        List<String> safeErrors = errors != null ? errors : java.util.Collections.<String>emptyList();
        return new RenderStepResult(stepId, providerName, providerType, status,
                null, safeArtifacts, java.util.Collections.<String>emptyList(), safeWarnings, safeErrors,
                durationMs, fallbackUsed, startedAt, Instant.now());
    }

    private RenderStepResult executeStep(RenderStep step, RenderJob job,
                                          Map<String, RenderArtifact> artifactByStep) {
        Instant stepStart = Instant.now();
        String providerName = step.providerName();
        String providerType = step.providerType().name();

        try {
            RenderProvider provider = registry.getProvider(providerName).orElse(null);
            if (provider == null) {
                return makeResult(step.id(), providerName, providerType, "FAILED",
                        List.of(), List.of(),
                        List.of("Provider not found: " + providerName),
                        Duration.between(stepStart, Instant.now()).toMillis(),
                        false, stepStart);
            }

            String inputUri = resolveInputUri(step, artifactByStep);
            RenderProvider.RenderResult providerResult = provider.render(
                    job.id() + "-" + step.id(), inputUri, "default");

            RenderArtifactType finalType = (step.providerType() == ProviderType.RENDER)
                    ? RenderArtifactType.INTERMEDIATE
                    : RenderArtifactType.FINAL_OUTPUT;

            RenderArtifact outputArtifact = new RenderArtifact(
                    "art-" + step.id() + "-" + UUID.randomUUID().toString().substring(0, 8),
                    finalType, providerResult.storageUri(), providerResult.storageUri(),
                    "video/mp4", 0, null, providerResult.duration(),
                    null, null, null, step.id(), Instant.now()
            );

            return makeResult(step.id(), providerName, providerType, "COMPLETED",
                    List.of(outputArtifact), List.of(), List.of(),
                    Duration.between(stepStart, Instant.now()).toMillis(),
                    false, stepStart);
        } catch (Exception e) {
            return makeResult(step.id(), providerName, providerType, "FAILED",
                    List.of(), List.of(),
                    List.of("Step error: " + e.getMessage()),
                    Duration.between(stepStart, Instant.now()).toMillis(),
                    false, stepStart);
        }
    }

    private String resolveInputUri(RenderStep step, Map<String, RenderArtifact> artifactByStep) {
        if (step.dependsOn() != null && !step.dependsOn().isEmpty()) {
            RenderArtifact dep = artifactByStep.get(step.dependsOn().getFirst());
            if (dep != null) return dep.url();
        }
        return "input";
    }
}

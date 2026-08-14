package com.example.platform.execution.domain;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.TrimOperation;
import com.example.platform.execution.domain.operation.ThumbnailOperation;
import com.example.platform.execution.domain.operation.ScaleOperation;
import com.example.platform.execution.domain.operation.DecodeOperation;
import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.storage.contract.ContentDigest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Test fixtures and factory methods for Media Execution Plan domain objects.
 */
public final class MediaExecutionPlanFixtures {

    public static final String DEFAULT_TENANT_ID = "tenant-001";
    public static final String DEFAULT_PRODUCT_ID = "product-001";
    public static final String DEFAULT_TIMELINE_REV = "rev-001";
    public static final String DEFAULT_TIMELINE_REV_DIGEST = "a".repeat(64);

    private MediaExecutionPlanFixtures() {
    }

    public static ExecutionPlanId planId(String value) {
        return new ExecutionPlanId(value);
    }

    public static ExecutionStepId stepId(String value) {
        return new ExecutionStepId(value);
    }

    public static ExecutionInputId inputId(String value) {
        return new ExecutionInputId(value);
    }

    public static ExecutionOutputId outputId(String value) {
        return new ExecutionOutputId(value);
    }

    public static ExecutionEdgeId edgeId(String value) {
        return new ExecutionEdgeId(value);
    }

    public static ArtifactId artifactId(String value) {
        return new ArtifactId(value);
    }

    public static ContentDigest sha256Digest(String hex) {
        return ContentDigest.sha256(hex);
    }

    public static ContentDigest defaultDigest() {
        return ContentDigest.sha256("a".repeat(64));
    }

    public static ExecutionCreationContext creationContext() {
        return ExecutionCreationContext.minimal(Instant.parse("2025-01-01T00:00:00Z"));
    }

    public static ExecutionCreationContext creationContext(Instant instant) {
        return ExecutionCreationContext.minimal(instant);
    }

    public static ExecutionResourceRequirement minimalResources() {
        return ExecutionResourceRequirement.minimal();
    }

    public static ExecutionResourceRequirement standardResources() {
        return ExecutionResourceRequirement.standard();
    }

    public static ExecutionResourceRequirement highPerformanceResources() {
        return ExecutionResourceRequirement.highPerformance();
    }

    public static ExecutionCapabilityRequirement capability(String id) {
        return ExecutionCapabilityRequirement.of(id);
    }

    public static ExecutionInputBinding primaryInput(String id, String artifactId) {
        return ExecutionInputBinding.primaryMedia(
                inputId(id),
                artifactId(artifactId),
                defaultDigest(),
                1024L,
                "video/mp4");
    }

    public static ExecutionInputBinding input(String id, String artifactId, ContentDigest digest) {
        return ExecutionInputBinding.primaryMedia(
                inputId(id),
                artifactId(artifactId),
                digest,
                1024L,
                "video/mp4");
    }

    public static ExecutionOutputDeclaration primaryOutput(String outputId, String stepId) {
        return ExecutionOutputDeclaration.primary(
                outputId(outputId),
                ArtifactKind.DERIVED_MEDIA,
                "video/mp4",
                stepId(stepId));
    }

    public static ExecutionOutputDeclaration intermediateOutput(String outputId, String stepId) {
        return ExecutionOutputDeclaration.intermediate(
                outputId(outputId),
                ArtifactKind.DERIVED_MEDIA,
                "video/mp4",
                stepId(stepId));
    }

    public static MediaExecutionStep step(String id, com.example.platform.execution.domain.operation.MediaOperation op) {
        return MediaExecutionStep.of(stepId(id), op, minimalResources(), capability("base"));
    }

    public static MediaExecutionStep stepWithOutputs(String id,
                                                      com.example.platform.execution.domain.operation.MediaOperation op,
                                                      String... outputIds) {
        Set<ExecutionOutputId> outputs = new LinkedHashSet<>();
        for (String oid : outputIds) {
            outputs.add(outputId(oid));
        }
        return MediaExecutionStep.builder(
                stepId(id), op, Set.of(), outputs,
                minimalResources(), capability("base"),
                ExecutionDeterminism.DETERMINISTIC,
                ExecutionStepFailurePolicy.FAIL_PLAN);
    }

    public static MediaExecutionStep stepWithInputs(String id,
                                                     com.example.platform.execution.domain.operation.MediaOperation op,
                                                     String... inputIds) {
        Set<ExecutionInputId> inputs = new LinkedHashSet<>();
        for (String iid : inputIds) {
            inputs.add(inputId(iid));
        }
        return MediaExecutionStep.builder(
                stepId(id), op, inputs, Set.of(),
                minimalResources(), capability("base"),
                ExecutionDeterminism.DETERMINISTIC,
                ExecutionStepFailurePolicy.FAIL_PLAN);
    }

    public static ExecutionDependency dataEdge(String id, String from, String to) {
        return ExecutionDependency.data(edgeId(id), stepId(from), stepId(to));
    }

    public static ExecutionDependency controlEdge(String id, String from, String to) {
        return ExecutionDependency.control(edgeId(id), stepId(from), stepId(to));
    }

    public static ExecutionDependency validationEdge(String id, String from, String to) {
        return ExecutionDependency.validation(edgeId(id), stepId(from), stepId(to));
    }

    public static MediaExecutionPlanBuilder builder() {
        return MediaExecutionPlanBuilder.create()
                .planId(planId("plan-001"))
                .tenantId(DEFAULT_TENANT_ID)
                .productId(DEFAULT_PRODUCT_ID)
                .timelineRevisionId(DEFAULT_TIMELINE_REV)
                .timelineRevisionDigest(DEFAULT_TIMELINE_REV_DIGEST)
                .schemaVersion(ExecutionPlanSchemaVersion.V1)
                .creationContext(creationContext());
    }

    /**
     * Builds a valid simple chain: step1 -> step2 -> step3
     */
    public static MediaExecutionPlan validChain() {
        return builder()
                .addInput(primaryInput("in-1", "art-001"))
                .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"))
                .addStep(stepWithInputs("step-2", TranscodeOperation.to("h264", "mp4"), "in-1"))
                .addStep(stepWithInputs("step-3", TrimOperation.toDuration(Duration.ofSeconds(30)), "in-1"))
                .addEdge(dataEdge("e-1", "step-1", "step-2"))
                .addEdge(dataEdge("e-2", "step-2", "step-3"))
                .addOutput(primaryOutput("out-1", "step-1"))
                .build();
    }

    /**
     * Builds a valid diamond: step1 -> step2, step1 -> step3, step2 -> step4, step3 -> step4
     */
    public static MediaExecutionPlan validDiamond() {
        return builder()
                .addInput(primaryInput("in-1", "art-001"))
                .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"))
                .addStep(stepWithInputs("step-2", TranscodeOperation.to("h264", "mp4"), "in-1"))
                .addStep(stepWithInputs("step-3", ScaleOperation.to(1920, 1080), "in-1"))
                .addStep(stepWithInputs("step-4", TrimOperation.toDuration(Duration.ofSeconds(30)), "in-1"))
                .addEdge(dataEdge("e-1", "step-1", "step-2"))
                .addEdge(dataEdge("e-2", "step-1", "step-3"))
                .addEdge(dataEdge("e-3", "step-2", "step-4"))
                .addEdge(dataEdge("e-4", "step-3", "step-4"))
                .addOutput(primaryOutput("out-1", "step-1"))
                .build();
    }

    /**
     * Builds a valid branch: step1 -> step2, step1 -> step3
     */
    public static MediaExecutionPlan validBranch() {
        return builder()
                .addInput(primaryInput("in-1", "art-001"))
                .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"))
                .addStep(stepWithInputs("step-2", TranscodeOperation.to("h264", "mp4"), "in-1"))
                .addStep(stepWithInputs("step-3", ThumbnailOperation.at(640, 360), "in-1"))
                .addEdge(dataEdge("e-1", "step-1", "step-2"))
                .addEdge(dataEdge("e-2", "step-1", "step-3"))
                .addOutput(primaryOutput("out-1", "step-1"))
                .build();
    }
}

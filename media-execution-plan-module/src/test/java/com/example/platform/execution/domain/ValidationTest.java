package com.example.platform.execution.domain;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.execution.domain.operation.*;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DAG validation.
 */
@DisplayName("DAG Validation")
class ValidationTest {

    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");

    private ExecutionInputBinding createInput(String id) {
        return ExecutionInputBinding.primaryMedia(
                new ExecutionInputId(id), new ArtifactId("art-" + id),
                DIGEST, 1000L, "video/mp4");
    }

    private MediaExecutionStep createStep(String id, MediaOperation op) {
        return MediaExecutionStep.of(
                new ExecutionStepId(id), op,
                ExecutionResourceRequirement.standard(),
                ExecutionCapabilityRequirement.of("test"));
    }

    private ExecutionOutputDeclaration createOutput(String id, String stepId) {
        return ExecutionOutputDeclaration.primary(
                new ExecutionOutputId(id), ArtifactKind.DELIVERY_RENDITION,
                "video/mp4", new ExecutionStepId(stepId));
    }

    private MediaExecutionPlan buildPlan(
            List<ExecutionInputBinding> inputs,
            List<MediaExecutionStep> steps,
            List<ExecutionDependency> edges,
            List<ExecutionOutputDeclaration> outputs) {
        return MediaExecutionPlanBuilder.create()
                .planId(new ExecutionPlanId("plan-001"))
                .tenantId("tenant-1")
                .productId("product-1")
                .timelineRevisionId("rev-001")
                .timelineRevisionDigest("digest-001")
                .schemaVersion(ExecutionPlanSchemaVersion.V1)
                .creationContext(ExecutionCreationContext.minimal(NOW))
                .addInputs(inputs)
                .addSteps(steps)
                .addEdges(edges)
                .addOutputs(outputs)
                .build();
    }

    @Test
    @DisplayName("Valid chain: A -> B -> C is accepted")
    void validChainAccepted() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", TranscodeOperation.to("h264", "mp4")));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")));
        List<ExecutionOutputDeclaration> outputs = List.of(
                createOutput("out1", "s3"));

        MediaExecutionPlan plan = buildPlan(inputs, steps, edges, outputs);
        assertThat(plan).isNotNull();
        assertThat(plan.stepCount()).isEqualTo(3);
        assertThat(plan.edgeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Valid branch: A -> B, A -> C is accepted")
    void validBranchAccepted() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", ScaleOperation.to(1280, 720)));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s3")));
        List<ExecutionOutputDeclaration> outputs = List.of(
                createOutput("out1", "s2"),
                createOutput("out2", "s3"));

        MediaExecutionPlan plan = buildPlan(inputs, steps, edges, outputs);
        assertThat(plan).isNotNull();
    }

    @Test
    @DisplayName("Valid diamond: A -> B, A -> C, B -> D, C -> D is accepted")
    void validDiamondAccepted() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", CropOperation.of(0, 0, 1920, 1080)),
                createStep("s4", TranscodeOperation.to("h264", "mp4")));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s3")),
                ExecutionDependency.data(new ExecutionEdgeId("e3"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s4")),
                ExecutionDependency.data(new ExecutionEdgeId("e4"),
                        new ExecutionStepId("s3"), new ExecutionStepId("s4")));
        List<ExecutionOutputDeclaration> outputs = List.of(
                createOutput("out1", "s4"));

        MediaExecutionPlan plan = buildPlan(inputs, steps, edges, outputs);
        assertThat(plan).isNotNull();
    }

    @Test
    @DisplayName("Direct cycle: A -> B -> A is rejected")
    void directCycleRejected() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s1")));
        List<ExecutionOutputDeclaration> outputs = List.of();

        assertThatThrownBy(() -> buildPlan(inputs, steps, edges, outputs))
                .isInstanceOf(ExecutionPlanDomainException.class)
                .satisfies(ex -> assertThat(((ExecutionPlanDomainException) ex).code())
                        .isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_CYCLE));
    }

    @Test
    @DisplayName("Multi-hop cycle: A -> B -> C -> A is rejected")
    void multiHopCycleRejected() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", TranscodeOperation.to("h264", "mp4")));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")),
                ExecutionDependency.data(new ExecutionEdgeId("e3"),
                        new ExecutionStepId("s3"), new ExecutionStepId("s1")));
        List<ExecutionOutputDeclaration> outputs = List.of();

        assertThatThrownBy(() -> buildPlan(inputs, steps, edges, outputs))
                .isInstanceOf(ExecutionPlanDomainException.class)
                .satisfies(ex -> assertThat(((ExecutionPlanDomainException) ex).code())
                        .isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_CYCLE));
    }

    @Test
    @DisplayName("Self-dependency is rejected")
    void selfDependencyRejected() {
        assertThatThrownBy(() -> ExecutionDependency.data(
                new ExecutionEdgeId("e1"),
                new ExecutionStepId("s1"), new ExecutionStepId("s1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-dependency");
    }

    @Test
    @DisplayName("Duplicate step is rejected")
    void duplicateStepRejected() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s1", ScaleOperation.to(1920, 1080)));
        List<ExecutionDependency> edges = List.of();
        List<ExecutionOutputDeclaration> outputs = List.of();

        assertThatThrownBy(() -> buildPlan(inputs, steps, edges, outputs))
                .isInstanceOf(ExecutionPlanDomainException.class)
                .satisfies(ex -> assertThat(((ExecutionPlanDomainException) ex).code())
                        .isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_DUPLICATE_STEP));
    }

    @Test
    @DisplayName("Duplicate dependency edge is rejected")
    void duplicateEdgeRejected() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")));
        List<ExecutionOutputDeclaration> outputs = List.of();

        assertThatThrownBy(() -> buildPlan(inputs, steps, edges, outputs))
                .isInstanceOf(ExecutionPlanDomainException.class)
                .satisfies(ex -> assertThat(((ExecutionPlanDomainException) ex).code())
                        .isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_DUPLICATE_DEPENDENCY));
    }

    @Test
    @DisplayName("Orphan output (output with no producer in steps) is rejected")
    void orphanOutputRejected() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")));
        List<ExecutionDependency> edges = List.of();
        List<ExecutionOutputDeclaration> outputs = List.of(
                createOutput("out1", "s999"));  // step s999 doesn't exist

        assertThatThrownBy(() -> buildPlan(inputs, steps, edges, outputs))
                .isInstanceOf(ExecutionPlanDomainException.class);
    }

    @Test
    @DisplayName("Output ID conflict is rejected")
    void outputConflictRejected() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")));
        List<ExecutionOutputDeclaration> outputs = List.of(
                createOutput("out1", "s1"),
                createOutput("out1", "s2"));  // same output ID

        assertThatThrownBy(() -> buildPlan(inputs, steps, edges, outputs))
                .isInstanceOf(ExecutionPlanDomainException.class)
                .satisfies(ex -> assertThat(((ExecutionPlanDomainException) ex).code())
                        .isEqualTo(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_OUTPUT_CONFLICT));
    }

    @Test
    @DisplayName("Multiple roots are accepted")
    void multipleRootsAccepted() {
        List<ExecutionInputBinding> inputs = List.of(
                createInput("in1"), createInput("in2"));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", DecodeOperation.of("aac")),
                createStep("s3", ComposeOperation.layers(List.of("l1"), "1920x1080", "30")));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s3")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")));
        List<ExecutionOutputDeclaration> outputs = List.of(
                createOutput("out1", "s3"));

        MediaExecutionPlan plan = buildPlan(inputs, steps, edges, outputs);
        assertThat(plan.rootStepIds()).hasSize(2);
    }
}

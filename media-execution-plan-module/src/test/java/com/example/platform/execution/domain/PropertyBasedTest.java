package com.example.platform.execution.domain;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.execution.domain.operation.*;
import com.example.platform.execution.domain.projection.MediaExecutionGraphProjection;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for deterministic invariants.
 */
@DisplayName("Property-Based Tests")
class PropertyBasedTest {

    private static final ContentDigest DIGEST_1 = ContentDigest.sha256("a".repeat(64));
    private static final ContentDigest DIGEST_2 = ContentDigest.sha256("b".repeat(64));
    private static final Instant NOW = Instant.parse("2025-01-15T10:30:00Z");

    private ExecutionInputBinding createInput(String id, ContentDigest digest) {
        return ExecutionInputBinding.primaryMedia(
                new ExecutionInputId(id), new ArtifactId("art-" + id),
                digest, 1000L, "video/mp4");
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

    private MediaExecutionPlan buildPlanWithInputs(
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
    @DisplayName("Property: Valid DAG always has deterministic topological order")
    void validDagDeterministicTopoOrder() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", TranscodeOperation.to("h264", "mp4")));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")));
        List<ExecutionOutputDeclaration> outputs = List.of(createOutput("out1", "s3"));

        // Build plan multiple times
        MediaExecutionPlan plan1 = buildPlanWithInputs(inputs, steps, edges, outputs);
        MediaExecutionPlan plan2 = buildPlanWithInputs(inputs, steps, edges, outputs);

        List<ExecutionStepId> order1 = MediaExecutionPlanValidator.topologicalOrder(plan1);
        List<ExecutionStepId> order2 = MediaExecutionPlanValidator.topologicalOrder(plan2);

        assertThat(order1).isEqualTo(order2);
    }

    @Test
    @DisplayName("Property: Cycle insertion is always rejected")
    void cycleAlwaysRejected() {
        // A -> B -> C -> A is always a cycle
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));
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

        for (int i = 0; i < 10; i++) {
            final int iteration = i;
            assertThatThrownBy(() -> buildPlanWithInputs(inputs, steps, edges, outputs))
                    .as("Cycle rejected on iteration " + iteration)
                    .isInstanceOf(ExecutionPlanDomainException.class);
        }
    }

    @Test
    @DisplayName("Property: Step insertion order does not change plan digest")
    void stepOrderIndependent() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));

        // Step order 1
        List<MediaExecutionStep> steps1 = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", TranscodeOperation.to("h264", "mp4")));

        // Step order 2 (reversed)
        List<MediaExecutionStep> steps2 = List.of(
                createStep("s3", TranscodeOperation.to("h264", "mp4")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s1", DecodeOperation.of("h264")));

        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")));
        List<ExecutionOutputDeclaration> outputs = List.of(createOutput("out1", "s3"));

        MediaExecutionPlan plan1 = buildPlanWithInputs(inputs, steps1, edges, outputs);
        MediaExecutionPlan plan2 = buildPlanWithInputs(inputs, steps2, edges, outputs);

        assertThat(plan1.digest()).isEqualTo(plan2.digest());
    }

    @Test
    @DisplayName("Property: Edge insertion order does not change plan digest")
    void edgeOrderIndependent() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", TranscodeOperation.to("h264", "mp4")));

        // Edge order 1
        List<ExecutionDependency> edges1 = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")));

        // Edge order 2 (reversed)
        List<ExecutionDependency> edges2 = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")),
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")));

        List<ExecutionOutputDeclaration> outputs = List.of(createOutput("out1", "s3"));

        MediaExecutionPlan plan1 = buildPlanWithInputs(inputs, steps, edges1, outputs);
        MediaExecutionPlan plan2 = buildPlanWithInputs(inputs, steps, edges2, outputs);

        assertThat(plan1.digest()).isEqualTo(plan2.digest());
    }

    @Test
    @DisplayName("Property: Same semantic plan has same digest")
    void sameSemanticSameDigest() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")));
        List<ExecutionOutputDeclaration> outputs = List.of(createOutput("out1", "s2"));

        // Build same plan multiple times
        Set<String> digests = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            MediaExecutionPlan plan = buildPlanWithInputs(inputs, steps, edges, outputs);
            digests.add(plan.digest().value());
        }

        assertThat(digests).hasSize(1);
    }

    @Test
    @DisplayName("Property: Material operation change changes digest")
    void operationChangeChangesDigest() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));
        List<ExecutionDependency> edges = List.of();
        List<ExecutionOutputDeclaration> outputs = List.of();

        // Plan with ScaleOperation
        List<MediaExecutionStep> steps1 = List.of(
                createStep("s1", ScaleOperation.to(1920, 1080)));
        MediaExecutionPlan plan1 = buildPlanWithInputs(inputs, steps1, edges, outputs);

        // Plan with different operation
        List<MediaExecutionStep> steps2 = List.of(
                createStep("s1", CropOperation.of(0, 0, 1920, 1080)));
        MediaExecutionPlan plan2 = buildPlanWithInputs(inputs, steps2, edges, outputs);

        assertThat(plan1.digest()).isNotEqualTo(plan2.digest());
    }

    @Test
    @DisplayName("Property: Input artifact digest change changes digest")
    void inputDigestChangeChangesDigest() {
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")));
        List<ExecutionDependency> edges = List.of();
        List<ExecutionOutputDeclaration> outputs = List.of();

        // Plan with digest 1
        List<ExecutionInputBinding> inputs1 = List.of(createInput("in1", DIGEST_1));
        MediaExecutionPlan plan1 = buildPlanWithInputs(inputs1, steps, edges, outputs);

        // Plan with digest 2
        List<ExecutionInputBinding> inputs2 = List.of(createInput("in1", DIGEST_2));
        MediaExecutionPlan plan2 = buildPlanWithInputs(inputs2, steps, edges, outputs);

        assertThat(plan1.digest()).isNotEqualTo(plan2.digest());
    }

    @Test
    @DisplayName("Property: Graph projection digest is deterministic")
    void graphDigestDeterministic() {
        List<ExecutionInputBinding> inputs = List.of(createInput("in1", DIGEST_1));
        List<MediaExecutionStep> steps = List.of(
                createStep("s1", DecodeOperation.of("h264")),
                createStep("s2", ScaleOperation.to(1920, 1080)),
                createStep("s3", TranscodeOperation.to("h264", "mp4")));
        List<ExecutionDependency> edges = List.of(
                ExecutionDependency.data(new ExecutionEdgeId("e1"),
                        new ExecutionStepId("s1"), new ExecutionStepId("s2")),
                ExecutionDependency.data(new ExecutionEdgeId("e2"),
                        new ExecutionStepId("s2"), new ExecutionStepId("s3")));
        List<ExecutionOutputDeclaration> outputs = List.of(createOutput("out1", "s3"));

        MediaExecutionPlan plan1 = buildPlanWithInputs(inputs, steps, edges, outputs);
        MediaExecutionPlan plan2 = buildPlanWithInputs(inputs, steps, edges, outputs);

        MediaExecutionGraphProjection proj1 = MediaExecutionGraphProjection.fromPlan(plan1);
        MediaExecutionGraphProjection proj2 = MediaExecutionGraphProjection.fromPlan(plan2);

        assertThat(proj1.graphDigest()).isEqualTo(proj2.graphDigest());
    }

    @Test
    @DisplayName("Property: Canonical serialization is idempotent")
    void canonicalSerializationIdempotent() {
        MediaExecutionStep step = createStep("s1", DecodeOperation.of("h264"));
        String canonical1 = step.canonicalForm();
        String canonical2 = step.canonicalForm();
        assertThat(canonical1).isEqualTo(canonical2);
    }

    @Test
    @DisplayName("Property: Caller input immutability — builder does not mutate input lists")
    void callerInputImmutability() {
        List<ExecutionInputBinding> inputs = new ArrayList<>(List.of(createInput("in1", DIGEST_1)));
        List<MediaExecutionStep> steps = new ArrayList<>(List.of(createStep("s1", DecodeOperation.of("h264"))));
        List<ExecutionDependency> edges = new ArrayList<>();
        List<ExecutionOutputDeclaration> outputs = new ArrayList<>(List.of(createOutput("out1", "s1")));

        int inputSize = inputs.size();
        int stepSize = steps.size();
        int edgeSize = edges.size();
        int outputSize = outputs.size();

        buildPlanWithInputs(inputs, steps, edges, outputs);

        assertThat(inputs).hasSize(inputSize);
        assertThat(steps).hasSize(stepSize);
        assertThat(edges).hasSize(edgeSize);
        assertThat(outputs).hasSize(outputSize);
    }
}
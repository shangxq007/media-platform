package com.example.platform.execution.domain;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.TrimOperation;
import com.example.platform.execution.domain.operation.ThumbnailOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Output Producer Validation")
class OutputProducerValidationTest {

    @Nested
    @DisplayName("ExecutionOutputDeclaration")
    class ExecutionOutputDeclarationTest {

        @Test
        @DisplayName("creates primary output")
        void createsPrimaryOutput() {
            ExecutionOutputDeclaration output = ExecutionOutputDeclaration.primary(
                    outputId("out-1"),
                    ArtifactKind.DERIVED_MEDIA,
                    "video/mp4",
                    stepId("step-1"));

            assertThat(output.isPrimaryOutput()).isTrue();
            assertThat(output.isIntermediate()).isFalse();
            assertThat(output.producingStepId().value()).isEqualTo("step-1");
        }

        @Test
        @DisplayName("creates intermediate output")
        void createsIntermediateOutput() {
            ExecutionOutputDeclaration output = ExecutionOutputDeclaration.intermediate(
                    outputId("out-1"),
                    ArtifactKind.DERIVED_MEDIA,
                    "video/mp4",
                    stepId("step-1"));

            assertThat(output.isPrimaryOutput()).isFalse();
            assertThat(output.isIntermediate()).isTrue();
        }

        @Test
        @DisplayName("rejects null outputId")
        void rejectsNullOutputId() {
            assertThatThrownBy(() -> new ExecutionOutputDeclaration(
                    null,
                    ArtifactKind.DERIVED_MEDIA,
                    "video/mp4",
                    ExecutionOutputRole.PRIMARY_OUTPUT,
                    stepId("step-1"),
                    java.util.Map.of(),
                    "standard"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null artifactKind")
        void rejectsNullArtifactKind() {
            assertThatThrownBy(() -> new ExecutionOutputDeclaration(
                    outputId("out-1"),
                    null,
                    "video/mp4",
                    ExecutionOutputRole.PRIMARY_OUTPUT,
                    stepId("step-1"),
                    java.util.Map.of(),
                    "standard"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects blank media type")
        void rejectsBlankMediaType() {
            assertThatThrownBy(() -> new ExecutionOutputDeclaration(
                    outputId("out-1"),
                    ArtifactKind.DERIVED_MEDIA,
                    "",
                    ExecutionOutputRole.PRIMARY_OUTPUT,
                    stepId("step-1"),
                    java.util.Map.of(),
                    "standard"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("rejects null producing step")
        void rejectsNullProducingStep() {
            assertThatThrownBy(() -> new ExecutionOutputDeclaration(
                    outputId("out-1"),
                    ArtifactKind.DERIVED_MEDIA,
                    "video/mp4",
                    ExecutionOutputRole.PRIMARY_OUTPUT,
                    null,
                    java.util.Map.of(),
                    "standard"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Output validation in plan")
    class OutputValidationInPlanTest {

        @Test
        @DisplayName("rejects output with unknown producing step")
        void rejectsUnknownProducingStep() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"))
                    .addOutput(primaryOutput("out-1", "nonexistent-step"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class);
        }

        @Test
        @DisplayName("rejects duplicate output declaration")
        void rejectsDuplicateOutputDeclaration() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .addOutput(intermediateOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class);
        }

        @Test
        @DisplayName("rejects step producing undeclared output")
        void rejectsUndeclaredOutput() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class);
        }

        @Test
        @DisplayName("valid plan passes output validation")
        void validPlanPassesOutputValidation() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(stepWithOutputs("step-1", MediaInspectionOperation.minimal(), "out-1"))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.outputs()).hasSize(1);
            assertThat(plan.outputs().get(0).producingStepId().value()).isEqualTo("step-1");
        }
    }
}

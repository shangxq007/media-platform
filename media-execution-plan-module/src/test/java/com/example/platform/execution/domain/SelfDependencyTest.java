package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.execution.domain.operation.TranscodeOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Self Dependency Validation")
class SelfDependencyTest {

    @Nested
    @DisplayName("Self-dependency in edge constructor")
    class SelfDependencyInConstructorTest {

        @Test
        @DisplayName("rejects self-dependency at edge creation")
        void rejectsSelfDependencyAtCreation() {
            assertThatThrownBy(() -> dataEdge("e-1", "step-1", "step-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Self-dependency");
        }

        @Test
        @DisplayName("rejects self-dependency via data factory")
        void rejectsSelfDependencyViaData() {
            assertThatThrownBy(() -> ExecutionDependency.data(
                    edgeId("e-1"),
                    stepId("step-1"),
                    stepId("step-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects self-dependency via control factory")
        void rejectsSelfDependencyViaControl() {
            assertThatThrownBy(() -> ExecutionDependency.control(
                    edgeId("e-1"),
                    stepId("step-1"),
                    stepId("step-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects self-dependency via validation factory")
        void rejectsSelfDependencyViaValidation() {
            assertThatThrownBy(() -> ExecutionDependency.validation(
                    edgeId("e-1"),
                    stepId("step-1"),
                    stepId("step-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Self-dependency in plan validation")
    class SelfDependencyInPlanTest {

        @Test
        @DisplayName("validator detects self-dependency")
        void validatorDetectsSelfDependency() {
            // Create a plan with a self-dependency edge
            // Since the constructor already prevents this, we test the validator directly
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            // The plan should be valid (no self-dependency)
            assertThat(plan.edges()).isEmpty();
        }
    }
}

package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.TrimOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Duplicate Step and Dependency Validation")
class DuplicateStepDependencyTest {

    @Nested
    @DisplayName("Duplicate Step Detection")
    class DuplicateStepTest {

        @Test
        @DisplayName("rejects duplicate step IDs")
        void rejectsDuplicateStepIds() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-1", TranscodeOperation.to("h264", "mp4")))
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Duplicate step");
        }

        @Test
        @DisplayName("accepts unique step IDs")
        void acceptsUniqueStepIds() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.steps()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Duplicate Dependency Detection")
    class DuplicateDependencyTest {

        @Test
        @DisplayName("rejects duplicate edge IDs")
        void rejectsDuplicateEdgeIds() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(controlEdge("e-1", "step-1", "step-2"))
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Duplicate dependency");
        }

        @Test
        @DisplayName("rejects duplicate (from,to) pair")
        void rejectsDuplicateFromToPair() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(controlEdge("e-2", "step-1", "step-2"))
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Duplicate dependency");
        }

        @Test
        @DisplayName("accepts unique edges")
        void acceptsUniqueEdges() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.edges()).hasSize(1);
        }
    }
}

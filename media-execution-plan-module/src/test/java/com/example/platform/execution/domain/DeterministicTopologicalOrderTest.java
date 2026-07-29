package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.TrimOperation;
import com.example.platform.execution.domain.operation.ScaleOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Deterministic Topological Order")
class DeterministicTopologicalOrderTest {

    @Nested
    @DisplayName("Basic ordering")
    class BasicOrderingTest {

        @Test
        @DisplayName("returns steps in topological order")
        void returnsTopologicalOrder() {
            MediaExecutionPlan plan = validChain();
            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);

            assertThat(order).hasSize(3);
            // step-1 must come before step-2, step-2 before step-3
            assertThat(order.indexOf(stepId("step-1")))
                    .isLessThan(order.indexOf(stepId("step-2")));
            assertThat(order.indexOf(stepId("step-2")))
                    .isLessThan(order.indexOf(stepId("step-3")));
        }

        @Test
        @DisplayName("returns all steps in the plan")
        void returnsAllSteps() {
            MediaExecutionPlan plan = validDiamond();
            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);

            assertThat(order).hasSize(4);
            assertThat(order).containsExactlyInAnyOrder(
                    stepId("step-1"), stepId("step-2"), stepId("step-3"), stepId("step-4"));
        }
    }

    @Nested
    @DisplayName("Determinism")
    class DeterminismTest {

        @Test
        @DisplayName("same plan yields same order on repeated calls")
        void sameOrderOnRepeatedCalls() {
            MediaExecutionPlan plan = validDiamond();

            List<ExecutionStepId> order1 = MediaExecutionPlanValidator.topologicalOrder(plan);
            List<ExecutionStepId> order2 = MediaExecutionPlanValidator.topologicalOrder(plan);

            assertThat(order1).isEqualTo(order2);
        }

        @Test
        @DisplayName("diamond topology has deterministic order")
        void diamondTopologyDeterministic() {
            MediaExecutionPlan plan = validDiamond();
            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);

            // step-1 must be first (only root)
            assertThat(order.get(0)).isEqualTo(stepId("step-1"));
            // step-4 must be last (only sink)
            assertThat(order.get(3)).isEqualTo(stepId("step-4"));
            // step-2 and step-3 can be in either order but must be deterministic
            // With our sorted approach, step-2 should come before step-3
            assertThat(order.indexOf(stepId("step-2")))
                    .isLessThan(order.indexOf(stepId("step-3")));
        }

        @Test
        @DisplayName("branch topology has deterministic order")
        void branchTopologyDeterministic() {
            MediaExecutionPlan plan = validBranch();
            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);

            // step-1 must be first
            assertThat(order.get(0)).isEqualTo(stepId("step-1"));
            // step-2 and step-3 must be after step-1
            assertThat(order.indexOf(stepId("step-1")))
                    .isLessThan(order.indexOf(stepId("step-2")));
            assertThat(order.indexOf(stepId("step-1")))
                    .isLessThan(order.indexOf(stepId("step-3")));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("empty plan returns empty order")
        void emptyPlanReturnsEmptyOrder() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .build();

            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);
            assertThat(order).isEmpty();
        }

        @Test
        @DisplayName("single step returns single element")
        void singleStepReturnsSingleElement() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);
            assertThat(order).containsExactly(stepId("step-1"));
        }

        @Test
        @DisplayName("disconnected steps are all included")
        void disconnectedStepsAllIncluded() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", ScaleOperation.to(1920, 1080)))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            List<ExecutionStepId> order = MediaExecutionPlanValidator.topologicalOrder(plan);
            assertThat(order).hasSize(3);
            assertThat(order).containsExactlyInAnyOrder(
                    stepId("step-1"), stepId("step-2"), stepId("step-3"));
        }
    }
}

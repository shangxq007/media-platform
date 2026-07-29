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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Cycle Detection")
class CycleDetectionTest {

    @Nested
    @DisplayName("Direct Cycle")
    class DirectCycleTest {

        @Test
        @DisplayName("rejects direct 2-node cycle")
        void rejectsDirect2NodeCycle() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-2", "step-1"))
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Cycle");
        }

        @Test
        @DisplayName("rejects direct 1-node cycle (self-dependency)")
        void rejectsDirect1NodeCycle() {
            // Self-dependency is caught at edge construction
            assertThatThrownBy(() -> dataEdge("e-1", "step-1", "step-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Self-dependency");
        }
    }

    @Nested
    @DisplayName("Multi-hop Cycle")
    class MultiHopCycleTest {

        @Test
        @DisplayName("rejects 3-node cycle")
        void rejects3NodeCycle() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", TrimOperation.toDuration(Duration.ofSeconds(30))))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-2", "step-3"))
                    .addEdge(dataEdge("e-3", "step-3", "step-1"))
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Cycle");
        }

        @Test
        @DisplayName("rejects 4-node cycle")
        void rejects4NodeCycle() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", TrimOperation.toDuration(Duration.ofSeconds(30))))
                    .addStep(step("step-4", ScaleOperation.to(1920, 1080)))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-2", "step-3"))
                    .addEdge(dataEdge("e-3", "step-3", "step-4"))
                    .addEdge(dataEdge("e-4", "step-4", "step-1"))
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Cycle");
        }

        @Test
        @DisplayName("rejects cycle in complex DAG")
        void rejectsCycleInComplexDAG() {
            MediaExecutionPlanBuilder builder = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", TrimOperation.toDuration(Duration.ofSeconds(30))))
                    .addStep(step("step-4", ScaleOperation.to(1920, 1080)))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-2", "step-3"))
                    .addEdge(dataEdge("e-3", "step-3", "step-4"))
                    .addEdge(dataEdge("e-4", "step-4", "step-2"))  // cycle: 2->3->4->2
                    .addOutput(primaryOutput("out-1", "step-1"));

            assertThatThrownBy(builder::build)
                    .isInstanceOf(ExecutionPlanDomainException.class)
                    .hasMessageContaining("Cycle");
        }
    }

    @Nested
    @DisplayName("Valid DAGs")
    class ValidDAGsTest {

        @Test
        @DisplayName("accepts valid chain")
        void acceptsValidChain() {
            MediaExecutionPlan plan = validChain();
            assertThat(plan.steps()).hasSize(3);
            assertThat(plan.edges()).hasSize(2);
        }

        @Test
        @DisplayName("accepts valid diamond")
        void acceptsValidDiamond() {
            MediaExecutionPlan plan = validDiamond();
            assertThat(plan.steps()).hasSize(4);
            assertThat(plan.edges()).hasSize(4);
        }

        @Test
        @DisplayName("accepts valid branch")
        void acceptsValidBranch() {
            MediaExecutionPlan plan = validBranch();
            assertThat(plan.steps()).hasSize(3);
            assertThat(plan.edges()).hasSize(2);
        }

        @Test
        @DisplayName("accepts disconnected steps")
        void acceptsDisconnectedSteps() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.edges()).isEmpty();
        }
    }
}

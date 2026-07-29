package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaInspectionOperation;
import com.example.platform.execution.domain.operation.TranscodeOperation;
import com.example.platform.execution.domain.operation.TrimOperation;
import com.example.platform.execution.domain.operation.ScaleOperation;
import com.example.platform.execution.domain.operation.ThumbnailOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.platform.execution.domain.MediaExecutionPlanFixtures.*;

@DisplayName("Valid Chain, Branch, Diamond Topologies")
class ValidTopologyTest {

    @Nested
    @DisplayName("Valid Chain")
    class ValidChainTest {

        @Test
        @DisplayName("builds a 3-step chain")
        void builds3StepChain() {
            MediaExecutionPlan plan = validChain();

            assertThat(plan.steps()).hasSize(3);
            assertThat(plan.edges()).hasSize(2);
            assertThat(plan.rootStepIds()).containsExactly(stepId("step-1"));
            assertThat(plan.sinkStepIds()).containsExactly(stepId("step-3"));
        }

        @Test
        @DisplayName("builds a 5-step chain")
        void builds5StepChain() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", TrimOperation.toDuration(Duration.ofSeconds(30))))
                    .addStep(step("step-4", ScaleOperation.to(1920, 1080)))
                    .addStep(step("step-5", ThumbnailOperation.at(640, 360)))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-2", "step-3"))
                    .addEdge(dataEdge("e-3", "step-3", "step-4"))
                    .addEdge(dataEdge("e-4", "step-4", "step-5"))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.steps()).hasSize(5);
            assertThat(plan.edges()).hasSize(4);
            assertThat(plan.rootStepIds()).containsExactly(stepId("step-1"));
            assertThat(plan.sinkStepIds()).containsExactly(stepId("step-5"));
        }
    }

    @Nested
    @DisplayName("Valid Branch")
    class ValidBranchTest {

        @Test
        @DisplayName("builds a branch topology")
        void buildsBranchTopology() {
            MediaExecutionPlan plan = validBranch();

            assertThat(plan.steps()).hasSize(3);
            assertThat(plan.edges()).hasSize(2);
            assertThat(plan.rootStepIds()).containsExactly(stepId("step-1"));
            assertThat(plan.sinkStepIds()).containsExactlyInAnyOrder(stepId("step-2"), stepId("step-3"));
        }

        @Test
        @DisplayName("builds a fan-out topology")
        void buildsFanOutTopology() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", ScaleOperation.to(1920, 1080)))
                    .addStep(step("step-4", ThumbnailOperation.at(640, 360)))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-1", "step-3"))
                    .addEdge(dataEdge("e-3", "step-1", "step-4"))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.steps()).hasSize(4);
            assertThat(plan.edges()).hasSize(3);
            assertThat(plan.rootStepIds()).containsExactly(stepId("step-1"));
            assertThat(plan.sinkStepIds()).containsExactlyInAnyOrder(
                    stepId("step-2"), stepId("step-3"), stepId("step-4"));
        }
    }

    @Nested
    @DisplayName("Valid Diamond")
    class ValidDiamondTest {

        @Test
        @DisplayName("builds a diamond topology")
        void buildsDiamondTopology() {
            MediaExecutionPlan plan = validDiamond();

            assertThat(plan.steps()).hasSize(4);
            assertThat(plan.edges()).hasSize(4);
            assertThat(plan.rootStepIds()).containsExactly(stepId("step-1"));
            assertThat(plan.sinkStepIds()).containsExactly(stepId("step-4"));
        }

        @Test
        @DisplayName("builds a complex diamond with multiple paths")
        void buildsComplexDiamond() {
            MediaExecutionPlan plan = MediaExecutionPlanFixtures.builder()
                    .addInput(primaryInput("in-1", "art-001"))
                    .addStep(step("step-1", MediaInspectionOperation.minimal()))
                    .addStep(step("step-2", TranscodeOperation.to("h264", "mp4")))
                    .addStep(step("step-3", ScaleOperation.to(1920, 1080)))
                    .addStep(step("step-4", TrimOperation.toDuration(Duration.ofSeconds(30))))
                    .addStep(step("step-5", ThumbnailOperation.at(640, 360)))
                    .addEdge(dataEdge("e-1", "step-1", "step-2"))
                    .addEdge(dataEdge("e-2", "step-1", "step-3"))
                    .addEdge(dataEdge("e-3", "step-2", "step-4"))
                    .addEdge(dataEdge("e-4", "step-3", "step-4"))
                    .addEdge(dataEdge("e-5", "step-4", "step-5"))
                    .addOutput(primaryOutput("out-1", "step-1"))
                    .build();

            assertThat(plan.steps()).hasSize(5);
            assertThat(plan.edges()).hasSize(5);
            assertThat(plan.rootStepIds()).containsExactly(stepId("step-1"));
            assertThat(plan.sinkStepIds()).containsExactly(stepId("step-5"));
        }
    }
}

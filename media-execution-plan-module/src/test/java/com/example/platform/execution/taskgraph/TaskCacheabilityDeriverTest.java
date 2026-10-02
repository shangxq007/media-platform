package com.example.platform.execution.taskgraph;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-2a-2c: cacheability is derived from the task's determinism declarations, most restrictive
 * member winning.
 */
class TaskCacheabilityDeriverTest {

    @Test
    void allDeterministicMembersAreCacheable() {
        assertThat(TaskCacheabilityDeriver.cacheabilityOfUnits(
                        List.of(unit(true, RenderDeterminismClass.DETERMINISTIC))))
                .isEqualTo(Cacheability.CACHEABLE);
    }

    @Test
    void conditionalDeterminismRequiresFullPinning() {
        assertThat(TaskCacheabilityDeriver.cacheabilityOfUnits(
                        List.of(unit(true, RenderDeterminismClass.CONDITIONALLY_DETERMINISTIC))))
                .isEqualTo(Cacheability.CACHEABLE_WHEN_FULLY_PINNED);
    }

    @Test
    void nonDeterministicMemberIsNeverCacheable() {
        assertThat(TaskCacheabilityDeriver.cacheabilityOfUnits(
                        List.of(unit(true, RenderDeterminismClass.NON_DETERMINISTIC))))
                .isEqualTo(Cacheability.NOT_CACHEABLE);
    }

    @Test
    void flagFalseRequiresFullPinningEvenWhenIntentsAreDeterministic() {
        assertThat(TaskCacheabilityDeriver.cacheabilityOfUnits(
                        List.of(unit(false, RenderDeterminismClass.DETERMINISTIC))))
                .isEqualTo(Cacheability.CACHEABLE_WHEN_FULLY_PINNED);
    }

    @Test
    void mostRestrictiveMemberWins() {
        assertThat(TaskCacheabilityDeriver.cacheabilityOfUnits(List.of(
                        unit(true, RenderDeterminismClass.DETERMINISTIC),
                        unit(true, RenderDeterminismClass.NON_DETERMINISTIC))))
                .isEqualTo(Cacheability.NOT_CACHEABLE);
    }

    @Test
    void emptyTaskListYieldsEmptyMap() {
        assertThat(TaskCacheabilityDeriver.derive(List.of()))
                .isEmpty();
    }

    // ---------- fixture ----------

    private static PhysicalPlanUnit unit(boolean cacheable, RenderDeterminismClass determinism) {
        return new PhysicalPlanUnit(
                new ExecutionStepId("unit-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(new ExecutionIntentRef(new RenderExecutionRequirement(
                        RenderExecutionRequirement.GpuRequirement.NONE, determinism, false))),
                null,
                cacheable);
    }
}

package com.example.platform.execution.taskgraph;

import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Derives the per-task reuse policy from the task's own determinism declarations.
 *
 * <p>Pure function over immutable task semantics — no state, no clock, no policy registry.
 *
 * <p>Rule (most restrictive member wins):
 * <ul>
 *   <li>any member determinism intent {@code NON_DETERMINISTIC} → {@link Cacheability#NOT_CACHEABLE};</li>
 *   <li>otherwise, if any member is not {@code deterministicallyCacheable} or intends
 *       {@code CONDITIONALLY_DETERMINISTIC} → {@link Cacheability#CACHEABLE_WHEN_FULLY_PINNED};</li>
 *   <li>otherwise → {@link Cacheability#CACHEABLE}.</li>
 * </ul>
 */
public final class TaskCacheabilityDeriver {

    private TaskCacheabilityDeriver() {
    }

    /** Derives the cacheability of every task in the graph, keyed by executable task identity. */
    public static Map<ExecutableTaskId, Cacheability> derive(ProviderBoundExecutableTaskGraph graph) {
        Objects.requireNonNull(graph, "graph");
        return derive(graph.tasks());
    }

    /** Derives the cacheability of each task; an empty task list yields an empty map. */
    public static Map<ExecutableTaskId, Cacheability> derive(Iterable<ExecutableTask> tasks) {
        Objects.requireNonNull(tasks, "tasks");
        Map<ExecutableTaskId, Cacheability> derived = new LinkedHashMap<>();
        for (ExecutableTask task : tasks) {
            derived.put(task.id(), cacheabilityOf(task));
        }
        return Map.copyOf(derived);
    }

    /** Most-restrictive-wins cacheability for one task. */
    public static Cacheability cacheabilityOf(ExecutableTask task) {
        Objects.requireNonNull(task, "task");
        return cacheabilityOfUnits(task.memberships().stream()
                .map(com.example.platform.execution.composition.ExecutableTaskMembership::physicalPlanUnit)
                .toList());
    }

    /** Most-restrictive-wins cacheability over the task's member plan units. */
    public static Cacheability cacheabilityOfUnits(Iterable<PhysicalPlanUnit> units) {
        Objects.requireNonNull(units, "units");
        boolean anyNonDeterministic = false;
        boolean anyConditional = false;
        boolean allDeterministicallyCacheable = true;
        for (PhysicalPlanUnit unit : units) {
            Objects.requireNonNull(unit, "plan unit");
            if (!unit.deterministicallyCacheable()) {
                allDeterministicallyCacheable = false;
            }
            for (ExecutionIntentRef intent : unit.executionIntentRefs()) {
                switch (intent.declaration().determinism()) {
                    case NON_DETERMINISTIC -> anyNonDeterministic = true;
                    case CONDITIONALLY_DETERMINISTIC -> anyConditional = true;
                    default -> {
                        // DETERMINISTIC contributes nothing beyond the cacheable flag.
                    }
                }
            }
        }
        if (anyNonDeterministic) {
            return Cacheability.NOT_CACHEABLE;
        }
        if (!allDeterministicallyCacheable || anyConditional) {
            return Cacheability.CACHEABLE_WHEN_FULLY_PINNED;
        }
        return Cacheability.CACHEABLE;
    }
}

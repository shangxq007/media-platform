package com.example.platform.execution.taskgraph;

import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import java.util.*;

/** Planning-owned comparison of real bound computations, including provider and predecessor semantics.
 * Regions are a derived diagnostic projection; they never replace Timeline or authorize reuse. */
public record IncrementalExecutionImpact(Map<ExecutableTaskId, Decision> decisions,
        List<RenderExecutionCoverage> affectedRegions) {
    public record Decision(ExecutionReuseKey key, boolean unchanged, String reason) {}
    public IncrementalExecutionImpact {
        decisions = Map.copyOf(decisions); affectedRegions = List.copyOf(affectedRegions);
    }
    public static IncrementalExecutionImpact analyze(ProviderBoundExecutableTaskGraph target,
            ProviderBoundExecutableTaskGraph reference) {
        var current = ExecutionReuseKeyDeriver.derive(target);
        var previous = ExecutionReuseKeyDeriver.derive(reference);
        var oldKeys = new HashSet<>(previous.values());
        var newKeys = new HashSet<>(current.values());
        var finalTask = GraphFinalOutput.requireRenderMaster(target).id();
        Map<ExecutableTaskId, Decision> decisions = new LinkedHashMap<>();
        Set<RenderExecutionCoverage> regions = new LinkedHashSet<>();
        for (var task : target.tasks()) {
            boolean same = oldKeys.contains(current.get(task.id()));
            boolean intermediate = !task.id().equals(finalTask) && task.authoritativeOutputIds().size() == 1;
            decisions.put(task.id(), new Decision(current.get(task.id()), same && intermediate,
                    !intermediate ? "CURRENT_JOB_FINAL_OUTPUT" : same ? "UNCHANGED_BOUND_COMPUTATION" : "COMPUTATION_OR_DEPENDENCY_CHANGED"));
            if (!same) addSourceRegions(task, regions);
        }
        for (var task : reference.tasks())
            if (!newKeys.contains(previous.get(task.id()))) addSourceRegions(task, regions);
        // Effects/global semantics may change with no decode change: conservatively affect the whole output.
        if (regions.isEmpty() && !newKeys.equals(oldKeys)) {
            var extent = target.sourcePhysicalPlan().units().stream().map(u -> u.propagatedExtent())
                    .filter(Objects::nonNull).findFirst();
            extent.ifPresent(e -> regions.add(new RenderExecutionCoverage(e.start(), e.end(), e.frameRate())));
        }
        return new IncrementalExecutionImpact(decisions, List.copyOf(regions));
    }
    private static void addSourceRegions(ExecutableTask task, Set<RenderExecutionCoverage> regions) {
        task.memberships().stream().map(m -> m.physicalPlanUnit())
                .filter(u -> "decode".equals(u.operationKey()))
                .map(u -> u.executionCoverage()).filter(Objects::nonNull).forEach(regions::add);
    }
}

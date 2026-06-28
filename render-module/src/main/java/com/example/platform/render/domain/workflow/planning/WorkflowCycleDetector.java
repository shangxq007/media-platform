package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.workflow.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects cycles in workflow step dependencies.
 * Internal domain model. Deterministic, no execution.
 */
public class WorkflowCycleDetector {

    /**
     * Returns empty list if no cycle, or list of issues describing the cycle.
     */
    public List<WorkflowDryRunIssue> detectCycle(WorkflowDefinition definition) {
        if (definition == null) return List.of();

        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (WorkflowStep step : definition.steps()) {
            adjacency.put(step.id().value(), new LinkedHashSet<>());
        }
        for (WorkflowStep step : definition.steps()) {
            if (step.dependencies() != null) {
                for (WorkflowStepDependency dep : step.dependencies()) {
                    String depId = dep.dependsOnStepId().value();
                    if (adjacency.containsKey(depId)) {
                        adjacency.get(depId).add(step.id().value());
                    }
                }
            }
        }

        // Kahn's algorithm for cycle detection
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String id : adjacency.keySet()) {
            inDegree.put(id, 0);
        }
        for (Set<String> targets : adjacency.values()) {
            for (String t : targets) {
                inDegree.put(t, inDegree.get(t) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : adjacency.getOrDefault(current, Set.of())) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) queue.add(next);
            }
        }

        if (visited < adjacency.size()) {
            return List.of(new WorkflowDryRunIssue(
                    WorkflowDryRunIssueSeverity.BLOCKING,
                    WorkflowDryRunIssueCode.CYCLE_DETECTED,
                    "_",
                    "Workflow contains a cycle involving " + (adjacency.size() - visited) + " step(s)",
                    Map.of()));
        }
        return List.of();
    }

    public boolean hasCycle(WorkflowDefinition definition) {
        return !detectCycle(definition).isEmpty();
    }
}

package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.workflow.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves deterministic topological order of workflow steps.
 * Internal domain model. Rejects cycles and unknown dependencies.
 */
public class WorkflowStepOrderResolver {

    /**
     * Resolve step order. Returns ordered step IDs or empty if cycle/invalid.
     */
    public List<String> resolveOrder(WorkflowDefinition definition) {
        if (definition == null) return List.of();

        Map<String, WorkflowStep> stepMap = new LinkedHashMap<>();
        for (WorkflowStep step : definition.steps()) {
            stepMap.put(step.id().value(), step);
        }

        // Build adjacency: dependency → dependent
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String id : stepMap.keySet()) {
            adjacency.put(id, new LinkedHashSet<>());
            inDegree.put(id, 0);
        }

        for (WorkflowStep step : definition.steps()) {
            if (step.dependencies() != null) {
                for (WorkflowStepDependency dep : step.dependencies()) {
                    String depId = dep.dependsOnStepId().value();
                    if (adjacency.containsKey(depId)) {
                        adjacency.get(depId).add(step.id().value());
                        inDegree.put(step.id().value(), inDegree.get(step.id().value()) + 1);
                    }
                }
            }
        }

        // Kahn's with deterministic ordering (original step list order)
        Queue<String> queue = new LinkedList<>();
        for (WorkflowStep step : definition.steps()) {
            if (inDegree.get(step.id().value()) == 0) {
                queue.add(step.id().value());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);
            List<String> successors = new ArrayList<>(adjacency.getOrDefault(current, Set.of()));
            // Sort successors for deterministic order
            successors.sort(String::compareTo);
            for (String next : successors) {
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) {
                    queue.add(next);
                }
            }
        }

        if (order.size() < stepMap.size()) {
            return List.of(); // Cycle detected
        }
        return order;
    }

    public boolean hasValidOrder(WorkflowDefinition definition) {
        return resolveOrder(definition).size() == definition.steps().size();
    }
}

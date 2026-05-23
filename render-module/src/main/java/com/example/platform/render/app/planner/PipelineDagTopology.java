package com.example.platform.render.app.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Topological ordering for {@link PipelineExecutionPlan} task DAG. */
public final class PipelineDagTopology {

    private PipelineDagTopology() {
    }

    /**
     * Returns execution waves: each inner list can run in parallel; waves run sequentially.
     */
    public static List<List<PipelineTask>> executionWaves(PipelineExecutionPlan plan) {
        Map<String, PipelineTask> byId = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (PipelineTask task : plan.tasks()) {
            byId.put(task.taskId(), task);
            indegree.putIfAbsent(task.taskId(), 0);
            for (String dep : task.dependsOn()) {
                indegree.merge(task.taskId(), 1, Integer::sum);
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(task.taskId());
                indegree.putIfAbsent(dep, indegree.getOrDefault(dep, 0));
            }
        }

        List<List<PipelineTask>> waves = new ArrayList<>();
        Set<String> resolved = new HashSet<>();
        int guard = plan.tasks().size() + 1;

        while (resolved.size() < plan.tasks().size() && guard-- > 0) {
            List<PipelineTask> wave = new ArrayList<>();
            for (PipelineTask task : plan.tasks()) {
                if (resolved.contains(task.taskId())) {
                    continue;
                }
                boolean ready = task.dependsOn() == null || task.dependsOn().isEmpty()
                        || task.dependsOn().stream().allMatch(resolved::contains);
                if (ready) {
                    wave.add(task);
                }
            }
            if (wave.isEmpty()) {
                break;
            }
            for (PipelineTask t : wave) {
                resolved.add(t.taskId());
            }
            waves.add(wave);
        }

        if (resolved.size() < plan.tasks().size()) {
            List<PipelineTask> remaining = plan.tasks().stream()
                    .filter(t -> !resolved.contains(t.taskId()))
                    .toList();
            waves.add(new ArrayList<>(remaining));
        }
        return waves;
    }
}

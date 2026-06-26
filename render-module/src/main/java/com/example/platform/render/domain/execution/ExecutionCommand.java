package com.example.platform.render.domain.execution;

import java.util.List;
import java.util.Map;

/**
 * One executable unit within an ExecutionTask.
 * Examples: local process (executable + args), BMF graph, OpenCue layer.
 * No execution logic. Purely a data model.
 */
public record ExecutionCommand(
        String commandType,
        String executable,
        List<String> arguments,
        Map<String, String> environment,
        String workingDirectory) {

    public static ExecutionCommand process(String executable, List<String> args) {
        return new ExecutionCommand("PROCESS", executable, args, Map.of(), null);
    }

    public static ExecutionCommand bmfGraph(String graphDef) {
        return new ExecutionCommand("BMF_GRAPH", graphDef, List.of(), Map.of(), null);
    }
}

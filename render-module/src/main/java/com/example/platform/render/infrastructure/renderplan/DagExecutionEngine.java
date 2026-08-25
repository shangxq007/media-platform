package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG Execution Engine - executes RenderPlanIr with topological sort.
 * 
 * <p>Features:
 * <ul>
 *   <li>Topological sort execution</li>
 *   <li>Parallel execution for independent nodes</li>
 *   <li>Store artifacts</li>
 * </ul>
 */
@Service
public class DagExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(DagExecutionEngine.class);

    private final ToolRouter toolRouter;

    public DagExecutionEngine(ToolRouter toolRouter) {
        this.toolRouter = toolRouter;
    }

    /**
     * Execute a render plan.
     */
    public ExecutionResult execute(RenderPlanIr plan) {
        Instant startTime = Instant.now();
        log.info("Executing render plan {} with {} nodes", plan.planId(), plan.size());

        // Get topological order
        List<RenderPlanIr.RenderNode> topoOrder = plan.getTopologicalOrder();
        Map<String, String> nodeOutputs = new ConcurrentHashMap<>();
        List<String> executedNodes = new ArrayList<>();
        List<String> cachedNodes = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Execute nodes in topological order
        for (RenderPlanIr.RenderNode node : topoOrder) {
            try {
                // Get input from parent nodes
                Map<String, String> parentOutputs = new HashMap<>();
                for (RenderPlanIr.RenderNode parent : plan.getParents(node.id())) {
                    String output = nodeOutputs.get(parent.id());
                    if (output != null) {
                        parentOutputs.put(parent.id(), output);
                    }
                }

                // Execute node
                String output = executeNode(node, parentOutputs);
                nodeOutputs.put(node.id(), output);
                executedNodes.add(node.id());

            } catch (Exception e) {
                log.error("Failed to execute node {}: {}", node.id(), e.getMessage());
                errors.add("Node " + node.id() + ": " + e.getMessage());
            }
        }

        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

        // Get root output
        String rootOutput = plan.rootId() != null ? nodeOutputs.get(plan.rootId()) : null;

        return new ExecutionResult(
                plan.planId(),
                plan.jobId(),
                errors.isEmpty(),
                rootOutput,
                nodeOutputs,
                executedNodes,
                cachedNodes,
                errors,
                durationMs
        );
    }

    /**
     * Execute a single node.
     */
    private String executeNode(RenderPlanIr.RenderNode node, Map<String, String> parentOutputs) {
        // Get the appropriate tool
        ToolRouter.RenderTool tool = toolRouter.getTool(node.tool());

        // Execute
        ToolRouter.ToolResult result = tool.execute(
                node.id(),
                node.type().name(),
                node.params(),
                parentOutputs
        );

        if (!result.success()) {
            throw new RuntimeException("Node execution failed: " + result.error());
        }

        return result.outputUri();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ExecutionResult(
            String planId,
            String jobId,
            boolean success,
            String rootOutput,
            Map<String, String> nodeOutputs,
            List<String> executedNodes,
            List<String> cachedNodes,
            List<String> errors,
            long durationMs
    ) {
        public String getSummary() {
            return String.format("Plan %s: %s (executed=%d, cached=%d, errors=%d, %dms)",
                    planId, success ? "SUCCESS" : "FAILED",
                    executedNodes.size(), cachedNodes.size(), errors.size(), durationMs);
        }
    }
}

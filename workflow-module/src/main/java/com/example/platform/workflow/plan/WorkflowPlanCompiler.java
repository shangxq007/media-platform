package com.example.platform.workflow.plan;

import com.example.platform.workflow.definition.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Application lowering from versioned typed declarations to a complete immutable process plan. */
public final class WorkflowPlanCompiler {
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();
    public WorkflowPlan compile(UserWorkflowDefinition definition) {
        if (definition.schemaVersion() != 2) throw new IllegalArgumentException("Executable definition schema 2 required");
        if (definition.nodes().isEmpty() || definition.nodes().size() > 100 || definition.edges().size() > 500)
            throw new IllegalArgumentException("Definition size");
        List<WorkflowPlan.Node> nodes = new ArrayList<>();
        for (var declaration : definition.nodes()) {
            if (!"workflow.node.v2".equals(declaration.configSchemaRef()) || declaration.configValues().schemaVersion() != 2
                    || declaration.configValues().canonicalJson().getBytes(StandardCharsets.UTF_8).length > 65536)
                throw new IllegalArgumentException("Invalid executable node schema/size");
            var node = codec.decodeNode(declaration.configValues().canonicalJson());
            if (!node.id().equals(declaration.nodeId()) || !node.kind().name().equals(declaration.nodeType().name()))
                throw new IllegalArgumentException("Declaration/config identity mismatch");
            if (declaration.errorPolicy() == UserWorkflowDefinitionNode.ErrorPolicy.SKIP)
                throw new IllegalArgumentException("Implicit effect skipping is unsupported");
            if ((declaration.errorPolicy() == UserWorkflowDefinitionNode.ErrorPolicy.RETRY) != (node.retry().maximumAttempts() > 1))
                throw new IllegalArgumentException("Retry declaration mismatch");
            nodes.add(node);
        }
        Set<String> nonRoots = new HashSet<>();
        List<WorkflowPlan.ControlEdge> edges = new ArrayList<>();
        for (var edge : definition.edges()) {
            if (edge.conditionRef() != null && !edge.conditionRef().isEmpty())
                throw new IllegalArgumentException("Choice predicates belong to typed CHOICE nodes");
            nonRoots.add(edge.targetNodeId());
            edges.add(new WorkflowPlan.ControlEdge(edge.sourceNodeId(),edge.targetNodeId(),edge.sortOrder()));
        }
        var roots = nodes.stream().map(WorkflowPlan.Node::id).filter(id -> !nonRoots.contains(id)).toList();
        if (roots.size() != 1) throw new IllegalArgumentException("Exactly one structured root required");
        var plan = new WorkflowPlan(1, definition.definitionId().value(), definition.version().versionNumber(),
                definition.tenantId(), definition.projectId(), roots.getFirst(), nodes, edges);
        // Round trip detaches nested owner values from mutable caller collections and verifies child pins.
        return codec.decode(codec.encode(plan));
    }
}

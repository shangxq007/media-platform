package com.example.platform.workflow.plan;

import java.util.*;
import static com.example.platform.workflow.plan.WorkflowPlan.*;

/** Validates the entire pinned tree before any effect can be scheduled. */
public final class WorkflowPlanValidator {
    public void validate(WorkflowPlan plan) { validate(plan, 1, new HashSet<>()); }
    private void validate(WorkflowPlan plan, int depth, Set<String> ancestors) {
        if (depth > 8) fail("Subworkflow nesting exceeds 8");
        String identity = plan.tenantId() + "/" + plan.definitionId() + "/" + plan.definitionVersion();
        if (!ancestors.add(identity)) fail("Recursive subworkflow");
        if (plan.nodes().isEmpty() || plan.nodes().size() > 100 || plan.edges().size() > 500) fail("Plan size");
        Map<String, Node> nodes = new HashMap<>();
        for (Node n : plan.nodes()) if (nodes.put(n.id(), n) != null) fail("Duplicate node");
        if (!nodes.containsKey(plan.rootNodeId())) fail("Missing root");
        Map<String, List<ControlEdge>> children = new HashMap<>();
        Set<String> owned = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (ControlEdge e : plan.edges()) {
            if (!nodes.containsKey(e.parentId()) || !nodes.containsKey(e.childId())) fail("Missing edge endpoint");
            if (e.childId().equals(plan.rootNodeId()) || !owned.add(e.childId())) fail("Multiple ownership or cycle");
            if (!orders.add(e.parentId() + "/" + e.order())) fail("Ambiguous child order");
            children.computeIfAbsent(e.parentId(), k -> new ArrayList<>()).add(e);
        }
        Set<String> visited = new HashSet<>();
        visit(plan.rootNodeId(), children, visited);
        if (visited.size() != nodes.size()) fail("Disconnected or cyclic control graph");
        for (Node n : plan.nodes()) {
            int count = children.getOrDefault(n.id(), List.of()).size();
            switch (n.kind()) {
                case SEQUENCE -> { if (count == 0) fail("Empty sequence"); }
                case PARALLEL -> { if (count < 1 || count > 32) fail("Parallel ALL branch bound"); }
                case CHOICE -> { if (count != 2 || n.predicate() == null) fail("Choice requires predicate and two branches"); }
                case LOOP -> { if (count != 1 || n.predicate() == null || n.bound() < 1 || n.bound() > 1000) fail("Loop bound/body"); }
                case FOREACH -> {
                    if (count != 1 || n.collection() == null || n.bound() < 1 || n.bound() > 1000
                            || n.concurrency() < 1 || n.concurrency() > 32) fail("Foreach bound/body");
                }
                case WAIT -> { if (count != 0 || n.waitSpec() == null) fail("Wait leaf required"); }
                case OPERATION_INVOCATION -> { if (count != 0 || n.operation() == null) fail("Typed Operation leaf required"); }
                case SUBWORKFLOW -> {
                    if (count != 0 || n.childPlan() == null) fail("Pinned child plan required");
                    WorkflowPlan child = n.childPlan().plan();
                    if (!child.tenantId().equals(plan.tenantId()) || !child.projectId().equals(plan.projectId())) fail("Child scope mismatch");
                    validate(child, depth + 1, new HashSet<>(ancestors));
                }
            }
            if (n.kind() != Kind.OPERATION_INVOCATION && (n.operation() != null || !n.capabilities().isEmpty() || !n.bindings().isEmpty()
                    || n.retry().maximumAttempts() != 1)) fail("Effect fields on control node");
            if (n.kind() != Kind.SUBWORKFLOW && n.childPlan() != null) fail("Child pin on non-child node");
            if (n.kind() != Kind.WAIT && n.waitSpec() != null) fail("Wait fields on non-wait node");
            if (n.kind() != Kind.CHOICE && n.kind() != Kind.LOOP && n.predicate() != null) fail("Predicate on unsupported node");
            if (n.kind() != Kind.FOREACH && (n.collection() != null || n.concurrency() != 0)) fail("Collection on non-foreach node");
            if (n.kind() != Kind.FOREACH && n.kind() != Kind.LOOP && n.bound() != 0) fail("Bound on non-loop node");
        }
    }
    private void visit(String id, Map<String,List<ControlEdge>> edges, Set<String> visited) {
        if (!visited.add(id)) fail("Cycle");
        for (ControlEdge edge : edges.getOrDefault(id, List.of())) visit(edge.childId(), edges, visited);
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}

package com.example.platform.workflow.plan;

import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.extension.domain.CapabilityRequirement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Frozen bounded process semantics. Runtime bindings and credentials are not plan semantics. */
public record WorkflowPlan(int formatVersion, String definitionId, int definitionVersion,
                           String tenantId, String projectId, String rootNodeId,
                           List<Node> nodes, List<ControlEdge> edges) {
    public WorkflowPlan {
        if (formatVersion != 1 || definitionVersion < 1) throw new IllegalArgumentException("Unsupported version");
        text(definitionId); text(tenantId); text(projectId); text(rootNodeId);
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
    }
    public enum Kind { SEQUENCE, PARALLEL, CHOICE, WAIT, LOOP, FOREACH, SUBWORKFLOW, OPERATION_INVOCATION }
    public enum Comparison { EQUAL, NOT_EQUAL, IS_SET, IS_EMPTY }
    public enum WaitKind { TIMER, SIGNAL, APPROVAL }
    public enum Source { INPUT, RESULT, ITEM }
    /** Data references never serve as control edges; paths are exact named fields, not expressions. */
    public record ValueRef(Source source, String key) {
        public ValueRef { Objects.requireNonNull(source); text(key); }
    }
    public record Predicate(Comparison comparison, ValueRef left, String expectedJson) {
        public Predicate { Objects.requireNonNull(comparison); Objects.requireNonNull(left); }
    }
    /** Child ownership is explicit. Ordered child edges describe structured control, never data flow. */
    public record ControlEdge(String parentId, String childId, int order) {
        public ControlEdge { text(parentId); text(childId); if (order < 0) throw new IllegalArgumentException("Negative order"); }
    }
    public record Wait(WaitKind kind, long timeoutMillis) {
        public Wait { Objects.requireNonNull(kind); if (timeoutMillis < 1) throw new IllegalArgumentException("Wait deadline required"); }
    }
    public record Retry(int maximumAttempts, long initialDelayMillis) {
        public Retry { if (maximumAttempts < 1 || initialDelayMillis < 1) throw new IllegalArgumentException("Bounded retry required"); }
    }
    public record ChildPin(String digest, WorkflowPlan plan) {
        public ChildPin { text(digest); Objects.requireNonNull(plan); }
    }
    public record Node(String id, Kind kind, Predicate predicate, Wait waitSpec,
                       int bound, int concurrency, ValueRef collection,
                       OperationRequest operation, List<CapabilityRequirement> capabilities,
                       Map<String, ValueRef> bindings, Retry retry, ChildPin childPlan) {
        public Node {
            text(id); Objects.requireNonNull(kind);
            capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            bindings = Map.copyOf(bindings == null ? Map.of() : bindings);
            retry = retry == null ? new Retry(1, 1000) : retry;
        }
        public static Node control(String id, Kind kind) {
            return new Node(id, kind, null, null, 0, 0, null, null, List.of(), Map.of(), null, null);
        }
    }
    private static void text(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Required plan identity");
    }
}

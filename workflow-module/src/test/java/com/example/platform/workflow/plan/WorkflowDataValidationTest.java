package com.example.platform.workflow.plan;

import static com.example.platform.workflow.plan.WorkflowPlan.*;
import static org.assertj.core.api.Assertions.*;

import com.example.platform.operation.operation.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorkflowDataValidationTest {
    private Node effect(String id) {
        return new Node(id, Kind.OPERATION_INVOCATION, null, null, 0, 0, null,
                new OperationRequest(new OperationDefinitionId("test.echo"), OperationDefinitionVersion.V1_0,
                        new OperationTargetRequest.TimelineTargetRequest("project"), new OperationParameters.NoParameters(),
                        "base", "hash", null), null, null, null, null);
    }
    private Node loop(String id, Comparison comparison, String key) {
        return new Node(id, Kind.LOOP, new Predicate(comparison, new ValueRef(Source.RESULT, key), null),
                null, 2, 0, null, null, null, null, null, null);
    }
    private WorkflowPlan plan(List<Node> nodes, List<ControlEdge> edges) {
        return new WorkflowPlanCodec().decode(new WorkflowPlanCodec().encode(new WorkflowPlan(1,
                "definition", 1, "tenant", "project", nodes.getFirst().id(), nodes, edges)));
    }
    private void rejects(WorkflowPlan plan) {
        assertThatThrownBy(() -> new WorkflowDataValidation().validate(plan, Map.of("items", "[1]")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Result not definitely available");
    }
    @ParameterizedTest
    @EnumSource(value=Comparison.class, names={"IS_SET", "IS_EMPTY"})
    void nonProducingNodesAndUnknownResultsAreNeverOptionalLoopResults(Comparison comparison) {
        for (WaitKind kind : WaitKind.values()) {
            var wait = new Node("wait", Kind.WAIT, null, new Wait(kind, 1), 0, 0, null, null, null, null, null, null);
            for (String key : List.of("wait", "body", "unknown"))
                rejects(plan(List.of(loop("root", comparison, key), Node.control("body", Kind.SEQUENCE), effect("effect"), wait),
                        List.of(new ControlEdge("root", "body", 0), new ControlEdge("body", "effect", 0), new ControlEdge("body", "wait", 1))));
        }
    }
    @ParameterizedTest
    @EnumSource(value=Comparison.class, names={"IS_SET", "IS_EMPTY"})
    void iterationAndChildResultsRemainLexicallyHidden(Comparison comparison) {
        var each = new Node("each", Kind.FOREACH, null, null, 2, 1, new ValueRef(Source.INPUT, "items"), null, null, null, null, null);
        for (String key : List.of("hidden", "each"))
            rejects(plan(List.of(loop("root", comparison, key), each, effect("hidden")),
                    List.of(new ControlEdge("root", "each", 0), new ControlEdge("each", "hidden", 0))));
        var child = new WorkflowPlan(1, "child", 1, "tenant", "project", "hidden", List.of(effect("hidden")), List.of());
        var sub = new Node("sub", Kind.SUBWORKFLOW, null, null, 0, 0, null, null, null, null, null,
                new ChildPin(new WorkflowPlanCodec().digest(child), child));
        for (String key : List.of("hidden", "sub"))
            rejects(plan(List.of(loop("root", comparison, key), sub), List.of(new ControlEdge("root", "sub", 0))));
    }
    @ParameterizedTest
    @EnumSource(value=Comparison.class, names={"IS_SET", "IS_EMPTY"})
    void initiallyAbsentOperationResultsAndEnclosingIterationScopeAreValid(Comparison comparison) {
        var inner = loop("loop", comparison, "effect");
        for (Kind kind : List.of(Kind.SEQUENCE, Kind.PARALLEL)) {
            var plan = plan(List.of(inner, Node.control("body", kind), effect("effect")),
                    List.of(new ControlEdge("loop", "body", 0), new ControlEdge("body", "effect", 0)));
            assertThatCode(() -> new WorkflowDataValidation().validate(plan, Map.of())).doesNotThrowAnyException();
        }
        var each = new Node("each", Kind.FOREACH, null, null, 2, 1, new ValueRef(Source.INPUT, "items"), null, null, null, null, null);
        var scoped = plan(List.of(each, inner, effect("effect")), List.of(new ControlEdge("each", "loop", 0), new ControlEdge("loop", "effect", 0)));
        assertThatCode(() -> new WorkflowDataValidation().validate(scoped, Map.of("items", "[1]"))).doesNotThrowAnyException();
        var future = plan(List.of(Node.control("root", Kind.SEQUENCE), loop("loop", comparison, "future"),
                        effect("body"), effect("future")), List.of(new ControlEdge("root", "loop", 0),
                        new ControlEdge("root", "future", 1), new ControlEdge("loop", "body", 0)));
        rejects(future);
    }
}

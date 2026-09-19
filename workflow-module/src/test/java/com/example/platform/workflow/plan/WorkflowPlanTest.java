package com.example.platform.workflow.plan;

import static org.assertj.core.api.Assertions.*;
import static com.example.platform.workflow.plan.WorkflowPlan.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkflowPlanTest {
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();
    static WorkflowPlan plan(List<Node> nodes, List<ControlEdge> edges) {
        return new WorkflowPlan(1, "definition", 1, "tenant", "project", nodes.getFirst().id(), nodes, edges);
    }
    static Node timer(String id) {
        return new Node(id, Kind.WAIT, null, new Wait(WaitKind.TIMER, 1), 0, 0, null, null, null, null, null, null);
    }
    @Test void codecRoundTripAndPermutationHaveSameDigest() {
        var root = Node.control("root", Kind.SEQUENCE);
        var a = timer("a"); var b = timer("b");
        var edges = List.of(new ControlEdge("root","a",0),new ControlEdge("root","b",1));
        var one = plan(List.of(root,a,b),edges);
        var two = plan(List.of(root,b,a),List.of(edges.get(1),edges.get(0)));
        assertThat(codec.digest(one)).isEqualTo(codec.digest(two));
        assertThat(codec.digest(codec.decode(codec.encode(one)))).isEqualTo(codec.digest(one));
        assertThat(codec.digest(plan(List.of(root,a,b),List.of(new ControlEdge("root","a",1),new ControlEdge("root","b",0)))))
                .isNotEqualTo(codec.digest(one));
    }
    @Test void duplicateJsonFieldsAndUnknownFieldsFail() {
        var serialized = codec.encode(plan(List.of(timer("root")), List.of()));
        assertThatThrownBy(() -> codec.decode(serialized.replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(serialized.replace("\"formatVersion\":1", "\"formatVersion\":1,\"providerId\":\"x\""))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cyclesAndMultipleControlOwnersFail() {
        var root=Node.control("root",Kind.SEQUENCE); var a=Node.control("a",Kind.SEQUENCE);
        assertThatThrownBy(() -> codec.encode(plan(List.of(root,a),List.of(new ControlEdge("root","a",0),new ControlEdge("a","root",0)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(plan(List.of(root,timer("a")),List.of(new ControlEdge("root","a",0),new ControlEdge("root","a",1)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void orphanAndMissingEndpointFail() {
        assertThatThrownBy(() -> codec.encode(plan(List.of(timer("root"), timer("orphan")),List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(plan(List.of(timer("root")),List.of(new ControlEdge("root","missing",0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void loopMustHaveExplicitBoundAndBody() {
        Node loop=new Node("root",Kind.LOOP,new Predicate(Comparison.IS_SET,new ValueRef(Source.INPUT,"x"),null),null,1001,0,null,null,null,null,null,null);
        assertThatThrownBy(() -> codec.encode(plan(List.of(loop,timer("body")),List.of(new ControlEdge("root","body",0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void childScopeAndDigestCannotBeForged() {
        var child=plan(List.of(timer("root")),List.of());
        Node parent=new Node("root",Kind.SUBWORKFLOW,null,null,0,0,null,null,null,null,null,new ChildPin("forged",child));
        var plan=new WorkflowPlan(1,"parent",1,"tenant","project","root",List.of(parent),List.of());
        assertThatThrownBy(() -> codec.encode(plan)).isInstanceOf(IllegalArgumentException.class);
        parent=new Node("root",Kind.SUBWORKFLOW,null,null,0,0,null,null,null,null,null,new ChildPin(codec.digest(child),child));
        var differentScope=new WorkflowPlan(1,"parent",1,"other","project","root",List.of(parent),List.of());
        assertThatThrownBy(() -> codec.encode(differentScope)).isInstanceOf(IllegalArgumentException.class);
    }
}

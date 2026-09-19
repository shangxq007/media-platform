package com.example.platform.workflow.plan;

import static org.assertj.core.api.Assertions.*;
import static com.example.platform.workflow.plan.WorkflowPlan.*;
import com.example.platform.workflow.temporal.*;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.WorkflowOptions;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Real Temporal SDK test server. Controlled activities test control flow, not owner effects or PostgreSQL. */
class PlanWalkSdkTest {
    @Test void sequenceChoiceParallelAndTimerExecuteInSdk() {
        var nodes=List.of(Node.control("root",Kind.SEQUENCE),Node.control("parallel",Kind.PARALLEL),
                WorkflowPlanTest.timer("a"),WorkflowPlanTest.timer("b"),
                new Node("choice",Kind.CHOICE,new Predicate(Comparison.EQUAL,new ValueRef(Source.INPUT,"flag"),"true"),null,0,0,null,null,null,null,null,null),
                WorkflowPlanTest.timer("yes"),WorkflowPlanTest.timer("no"));
        var edges=List.of(new ControlEdge("root","parallel",0),new ControlEdge("root","choice",1),
                new ControlEdge("parallel","a",0),new ControlEdge("parallel","b",1),new ControlEdge("choice","yes",0),new ControlEdge("choice","no",1));
        try(var env=TestWorkflowEnvironment.newInstance()) {
            var worker=env.newWorker("ep07-test");
            worker.registerWorkflowImplementationTypes(PlanWalkWorkflowImpl.class);
            var effects=new Effects();worker.registerActivitiesImplementations(effects);env.start();
            var workflow=env.getWorkflowClient().newWorkflowStub(PlanWalkWorkflow.class,WorkflowOptions.newBuilder().setTaskQueue("ep07-test").build());
            workflow.execute("run",new WorkflowPlanCodec().encode(WorkflowPlanTest.plan(nodes,edges)),Map.of("flag","true"));
            assertThat(effects.status).isEqualTo("SUCCEEDED");
            assertThat(effects.completed).contains("root/root/parallel/a","root/root/parallel/b","root/root/choice/yes","root/root");
            assertThat(effects.completed).doesNotContain("root/root/choice/no");
        }
    }
    @Test void boundedForeachTraversesEveryItem() {
        var root=new Node("root",Kind.FOREACH,null,null,3,2,new ValueRef(Source.INPUT,"items"),null,null,null,null,null);
        var plan=WorkflowPlanTest.plan(List.of(root,WorkflowPlanTest.timer("body")),List.of(new ControlEdge("root","body",0)));
        var effects=execute(plan,Map.of("items","[1,2,3]"),false);
        assertThat(effects.status).isEqualTo("SUCCEEDED");
        assertThat(effects.completed).contains("root/root/item-0/body","root/root/item-1/body","root/root/item-2/body");
    }
    @Test void collectionOverflowFailsBeforeBody() {
        var root=new Node("root",Kind.FOREACH,null,null,1,1,new ValueRef(Source.INPUT,"items"),null,null,null,null,null);
        var plan=WorkflowPlanTest.plan(List.of(root,WorkflowPlanTest.timer("body")),List.of(new ControlEdge("root","body",0)));
        var effects=execute(plan,Map.of("items","[1,2]"),true);
        assertThat(effects.status).isEqualTo("FAILED");
        assertThat(effects.completed).isEmpty();
    }
    @Test void loopExhaustionIsFailureNotAnActivityRetry() {
        var root=new Node("root",Kind.LOOP,new Predicate(Comparison.IS_SET,new ValueRef(Source.INPUT,"condition"),null),null,2,0,null,null,null,null,null,null);
        var plan=WorkflowPlanTest.plan(List.of(root,WorkflowPlanTest.timer("body")),List.of(new ControlEdge("root","body",0)));
        var effects=execute(plan,Map.of("condition","true"),true);
        assertThat(effects.completed).containsExactly("root/root/iteration-0/body","root/root/iteration-1/body");
        assertThat(effects.status).isEqualTo("FAILED");
    }
    @Test void unreleasedApprovalFailsAtDeadline() {
        var root=new Node("root",Kind.WAIT,null,new Wait(WaitKind.APPROVAL,1),0,0,null,null,null,null,null,null);
        var effects=execute(WorkflowPlanTest.plan(List.of(root),List.of()),Map.of(),true);
        assertThat(effects.status).isEqualTo("FAILED");
        assertThat(effects.completed).isEmpty();
    }
    @Test void pinnedChildExecutes() {
        var child=WorkflowPlanTest.plan(List.of(WorkflowPlanTest.timer("child")),List.of());
        var node=new Node("root",Kind.SUBWORKFLOW,null,null,0,0,null,null,null,null,null,
                new ChildPin(new WorkflowPlanCodec().digest(child),child));
        var parent=new WorkflowPlan(1,"parent",1,"tenant","project","root",List.of(node),List.of());
        assertThat(execute(parent,Map.of(),false).completed).contains("root/root/child/child","root/root");
    }
    private Effects execute(WorkflowPlan plan,Map<String,String> inputs,boolean failure) {
        try(var env=TestWorkflowEnvironment.newInstance()) {
            var worker=env.newWorker("ep07-test");
            worker.registerWorkflowImplementationTypes(PlanWalkWorkflowImpl.class);
            var effects=new Effects();worker.registerActivitiesImplementations(effects);env.start();
            var workflow=env.getWorkflowClient().newWorkflowStub(PlanWalkWorkflow.class,WorkflowOptions.newBuilder().setTaskQueue("ep07-test").build());
            if(failure) assertThatThrownBy(() -> workflow.execute("run",new WorkflowPlanCodec().encode(plan),inputs))
                    .isInstanceOf(io.temporal.client.WorkflowFailedException.class);
            else workflow.execute("run",new WorkflowPlanCodec().encode(plan),inputs);
            return effects;
        }
    }
    public static class Effects implements PlanWalkActivities {
        final List<String> completed=Collections.synchronizedList(new ArrayList<>());
        volatile String status;
        public String invoke(String run,String step,String plan,String node,Map<String,String> bindings) { throw new AssertionError("No Operation expected"); }
        public void waiting(String run,String step,String kind,long deadline) {}
        public void stepCompleted(String run,String step,String result) { completed.add(step); }
        public void terminal(String run,String status) { this.status=status; }
    }
}

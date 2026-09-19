package com.example.platform.workflow.temporal;

import com.example.platform.workflow.plan.*;
import static com.example.platform.workflow.plan.WorkflowPlan.*;
import com.fasterxml.jackson.databind.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.*;
import java.time.Duration;
import java.util.*;

/** Deterministic adapter for Workflow-owned typed process semantics. No live lookups in replay. */
public final class PlanWalkWorkflowImpl implements PlanWalkWorkflow {
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();
    private final ObjectMapper json = new ObjectMapper();
    private final PlanWalkActivities projections = Workflow.newActivityStub(PlanWalkActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(1)).build());
    private final Map<String, Boolean> releases = new HashMap<>();
    private final Map<String, WaitKind> waiting = new HashMap<>();
    private boolean cancelled;
    private String runId;
    private Map<String,String> inputs;

    @Override public Map<String,String> execute(String runId, String planJson, Map<String,String> inputs) {
        this.runId = runId;
        this.inputs = Map.copyOf(inputs);
        Map<String,String> results = new TreeMap<>();
        String terminal = "SUCCEEDED";
        try {
            WorkflowPlan plan = codec.decode(planJson);
            walk(plan, plan.rootNodeId(), "root", results, null);
            checkCancellation();
        } catch (CanceledFailure e) { terminal = "CANCELLED"; }
        catch (RuntimeException e) { terminal = cancelled ? "CANCELLED" : "FAILED"; }
        final String outcome = terminal;
        Workflow.newDetachedCancellationScope(() -> projections.terminal(runId, outcome)).run();
        if (!"SUCCEEDED".equals(outcome))
            throw ApplicationFailure.newNonRetryableFailure(outcome, "WORKFLOW_" + outcome);
        return Map.copyOf(results);
    }

    private void walk(WorkflowPlan plan, String id, String path, Map<String,String> results, String item) {
        checkCancellation();
        Node node = plan.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
        String step = path + "/" + id;
        List<ControlEdge> children = plan.edges().stream().filter(e -> e.parentId().equals(id))
                .sorted(Comparator.comparingInt(ControlEdge::order)).toList();
        switch (node.kind()) {
            case SEQUENCE -> {
                for (ControlEdge edge : children) walk(plan, edge.childId(), step, results, item);
            }
            case PARALLEL -> {
                List<Map<String,String>> branchResults = new ArrayList<>();
                List<Promise<Void>> promises = new ArrayList<>();
                for (ControlEdge edge : children) {
                    Map<String,String> branch = new TreeMap<>(results);
                    branchResults.add(branch);
                    promises.add(Async.procedure(() -> walk(plan, edge.childId(), step, branch, item)));
                }
                Promise.allOf(promises).get();
                for (Map<String,String> branch : branchResults) merge(results, branch);
            }
            case CHOICE -> walk(plan, children.get(test(node.predicate(), results, item) ? 0 : 1).childId(), step, results, item);
            case LOOP -> {
                int iteration = 0;
                while (test(node.predicate(), results, item)) {
                    if (iteration == node.bound()) throw ApplicationFailure.newNonRetryableFailure("Loop bound exhausted", "WORKFLOW_BOUND");
                    walk(plan, children.getFirst().childId(), step + "/iteration-" + iteration++, results, item);
                }
            }
            case FOREACH -> {
                JsonNode collection = read(value(node.collection(), results, item));
                if (!collection.isArray() || collection.size() > node.bound())
                    throw ApplicationFailure.newNonRetryableFailure("Invalid bounded collection", "WORKFLOW_INPUT");
                for (int start = 0; start < collection.size(); start += node.concurrency()) {
                    List<Promise<Void>> promises = new ArrayList<>();
                    List<Map<String,String>> batch = new ArrayList<>();
                    for (int i = start; i < Math.min(start + node.concurrency(), collection.size()); i++) {
                        final int index = i;
                        Map<String,String> branch = new TreeMap<>(results); batch.add(branch);
                        promises.add(Async.procedure(() -> walk(plan, children.getFirst().childId(),
                                step + "/item-" + index, branch, collection.get(index).toString())));
                    }
                    Promise.allOf(promises).get();
                    for (Map<String,String> branch : batch) merge(results, branch);
                }
            }
            case WAIT -> {
                waiting.put(step, node.waitSpec().kind());
                try {
                    projections.waiting(runId, step, node.waitSpec().kind().name(),
                            Workflow.currentTimeMillis() + node.waitSpec().timeoutMillis());
                    boolean signalled = Workflow.await(Duration.ofMillis(node.waitSpec().timeoutMillis()),
                            () -> cancelled || releases.containsKey(step));
                    checkCancellation();
                    if (node.waitSpec().kind() != WaitKind.TIMER) {
                        if (!signalled) throw ApplicationFailure.newNonRetryableFailure("Wait expired", "WORKFLOW_TIMEOUT");
                        if (node.waitSpec().kind() == WaitKind.APPROVAL && !releases.get(step))
                            throw ApplicationFailure.newNonRetryableFailure("Approval rejected", "WORKFLOW_REJECTED");
                    }
                } finally { waiting.remove(step); releases.remove(step); }
            }
            case SUBWORKFLOW -> {
                WorkflowPlan child = node.childPlan().plan();
                walk(child, child.rootNodeId(), step + "/child", results, item);
            }
            case OPERATION_INVOCATION -> {
                Map<String,String> bindings = new TreeMap<>();
                node.bindings().forEach((key, ref) -> bindings.put(key, value(ref, results, item)));
                var activities = Workflow.newActivityStub(PlanWalkActivities.class, ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofMinutes(10))
                        .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(node.retry().maximumAttempts())
                                .setInitialInterval(Duration.ofMillis(node.retry().initialDelayMillis())).build()).build());
                String result = activities.invoke(runId, step, codec.encode(plan), node.id(), bindings);
                results.put(step, result);
                // Named output can be consumed sequentially; parallel branches must not share a name.
                results.put(node.id(), result);
            }
        }
        checkCancellation();
        projections.stepCompleted(runId, step, results.get(step));
    }
    private void merge(Map<String,String> target, Map<String,String> branch) {
        branch.forEach((key, value) -> {
            // Branch-local aliases must not escape an ALL join; only qualified instance outputs do.
            if (!key.contains("/")) return;
            if (target.containsKey(key) && !Objects.equals(target.get(key), value))
                throw ApplicationFailure.newNonRetryableFailure("Conflicting parallel result name", "WORKFLOW_BINDING");
            target.put(key, value);
        });
    }
    private boolean test(Predicate predicate, Map<String,String> results, String item) {
        String raw = value(predicate.left(), results, item);
        JsonNode actual = read(raw);
        return switch (predicate.comparison()) {
            case EQUAL -> actual.equals(read(predicate.expectedJson()));
            case NOT_EQUAL -> !actual.equals(read(predicate.expectedJson()));
            case IS_SET -> !actual.isNull();
            case IS_EMPTY -> actual.isNull() || actual.isTextual() && actual.asText().isEmpty()
                    || actual.isContainerNode() && actual.isEmpty();
        };
    }
    private String value(ValueRef ref, Map<String,String> results, String item) {
        return switch (ref.source()) {
            case INPUT -> inputs.get(ref.key());
            case RESULT -> results.get(ref.key());
            case ITEM -> item;
        };
    }
    private JsonNode read(String raw) {
        try { return raw == null ? json.nullNode() : json.readTree(raw); }
        catch (Exception e) { throw ApplicationFailure.newNonRetryableFailure("Invalid typed JSON value", "WORKFLOW_INPUT"); }
    }
    private void checkCancellation() {
        if (cancelled) throw new CanceledFailure("Workflow cancelled");
        CancellationScope.throwCanceled();
    }
    @Override public void release(String stepId, String releaseId, boolean approved) {
        if (!cancelled && waiting.containsKey(stepId) && waiting.get(stepId) != WaitKind.TIMER) releases.putIfAbsent(stepId, approved);
    }
    @Override public void cancelRun() { cancelled = true; }
}

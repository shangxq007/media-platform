package com.example.platform.workflow.temporal;

import static com.example.platform.workflow.plan.WorkflowPlan.*;

import com.example.platform.workflow.plan.*;
import com.example.platform.workflow.plan.WorkflowCursor.Frame;
import com.fasterxml.jackson.databind.*;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.*;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.*;

import java.time.Duration;
import java.util.*;

/** Durable mechanics over an explicit Workflow-owned cursor. All effects stay in activities. */
@WorkflowImpl(taskQueues = "workflow-process")
public final class PlanWalkWorkflowImpl implements PlanWalkWorkflow {
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();
    private final ObjectMapper json = new ObjectMapper();
    private final PlanWalkActivities projections =
            Workflow.newActivityStub(
                    PlanWalkActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(1))
                            .build());
    private final Map<String, WorkflowPlan> plans = new TreeMap<>();
    private WorkflowCursor state;
    private final Map<String, Boolean> earlyReleases = new TreeMap<>();
    private boolean earlyCancellation;
    private String runId;
    private Map<String, String> inputs;
    private final List<Promise<Void>> actions = new ArrayList<>();
    private long nextDeadline;

    @Override
    public Map<String, String> execute(
            String runId, String planJson, Map<String, String> inputs, String cursorJson) {
        this.runId = runId;
        this.inputs = Map.copyOf(inputs);
        var plan = codec.decode(planJson);
        register(plan);
        state =
                cursorJson == null
                        ? new WorkflowCursor()
                        : decode(cursorJson, WorkflowCursor.class);
        if (state.root == null)
            state.root =
                    new Frame(
                            codec.digest(plan),
                            plan.rootNodeId(),
                            "root/" + plan.rootNodeId(),
                            Map.of(),
                            null);
        state.releases.putAll(earlyReleases);
        earlyReleases.clear();
        state.cancelled |= earlyCancellation;
        long startedTransitions = state.transitions;
        String terminal = "SUCCEEDED";
        String failureCode = null;
        try {
            while (!state.root.done) {
                cancelled();
                actions.clear();
                nextDeadline = Long.MAX_VALUE;
                long before = state.transitions;
                advance(state.root);
                if (!actions.isEmpty()) Promise.allOf(actions).get();
                else if (!state.root.done && before == state.transitions) {
                    // Signals/timers are durable Temporal events; deadlines survive cursor
                    // reconstruction.
                    long delay =
                            nextDeadline == Long.MAX_VALUE
                                    ? 60000
                                    : Math.max(1, nextDeadline - Workflow.currentTimeMillis());
                    Workflow.await(
                            Duration.ofMillis(delay),
                            () -> state.cancelled || hasRelease(state.root));
                }
                cancelled();
                if (!state.root.done
                        && (state.transitions - startedTransitions >= 64
                                || Workflow.getInfo().isContinueAsNewSuggested()))
                    // Continue-As-New does not inherit memo in the adopted SDK. Rebuild it
                    // from the same immutable accepted inputs used by every execution.
                    // Dispatch keeps requiring both pins, including on successor runs.
                    Workflow.continueAsNew(
                            ContinueAsNewOptions.newBuilder()
                                    .setMemo(Map.of("workflowRunId", runId, "planDigest", codec.digest(plan)))
                                    .build(),
                            runId, planJson, inputs, encode(state));
            }
        } catch (CanceledFailure e) {
            terminal = "CANCELLED";
            failureCode = "CANCELLED";
        } catch (RuntimeException e) {
            terminal = state.cancelled ? "CANCELLED" : failureKind(e);
            failureCode = code(e);
        }
        final String requested = terminal;
        final String failure = failureCode;
        String[] projected = new String[1];
        Workflow.newDetachedCancellationScope(
                        () ->
                                projected[0] =
                                        projections.terminal(
                                                runId, requested, failure, state.failureStep))
                .run();
        final String outcome = projected[0];
        if ("CANCELLED".equals(outcome)) throw new CanceledFailure("Workflow cancelled");
        if (!"SUCCEEDED".equals(outcome))
            throw ApplicationFailure.newNonRetryableFailure(outcome, "WORKFLOW_" + outcome);
        return Map.copyOf(state.root.values);
    }

    private String code(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof ApplicationFailure a) return a.getType();
        return "WORKFLOW_ACTIVITY_FAILURE";
    }

    private String failureKind(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof ApplicationFailure a && "WORKFLOW_TIMEOUT".equals(a.getType()))
                return "TIMED_OUT";
        return "FAILED";
    }

    private void register(WorkflowPlan plan) {
        plans.put(codec.digest(plan), plan);
        for (var n : plan.nodes()) if (n.childPlan() != null) register(n.childPlan().plan());
    }

    private void advance(Frame frame) {
        if (frame.done || actions.size() >= 32) return;
        cancelled();
        var plan = plans.get(frame.planDigest);
        if (plan == null) throw invalid("Unknown cursor plan");
        var node =
                plan.nodes().stream()
                        .filter(n -> n.id().equals(frame.nodeId))
                        .findFirst()
                        .orElseThrow();
        var children =
                plan.edges().stream()
                        .filter(e -> e.parentId().equals(node.id()))
                        .sorted(Comparator.comparingInt(ControlEdge::order))
                        .toList();
        switch (node.kind()) {
            case SEQUENCE -> {
                if (frame.index == children.size()) {
                    complete(frame);
                    return;
                }
                if (frame.children.isEmpty())
                    frame.children.add(
                            child(
                                    frame,
                                    children.get(frame.index).childId(),
                                    frame.stepId,
                                    frame.item));
                var active = frame.children.getFirst();
                advance(active);
                if (active.done) {
                    frame.values = new TreeMap<>(active.values);
                    frame.children.clear();
                    frame.index++;
                    state.transitions++;
                }
            }
            case PARALLEL -> {
                if (!frame.initialized) {
                    for (var edge : children)
                        frame.children.add(child(frame, edge.childId(), frame.stepId, frame.item));
                    frame.initialized = true;
                }
                for (var branch : frame.children) advance(branch);
                if (frame.children.stream().allMatch(f -> f.done)) {
                    Map<String, String> merged = new TreeMap<>(frame.values);
                    for (var branch : frame.children)
                        branch.values.forEach(
                                (key, value) -> {
                                    if (!Objects.equals(frame.values.get(key), value)
                                            && merged.containsKey(key)
                                            && !Objects.equals(merged.get(key), value)
                                            && !Objects.equals(
                                                    merged.get(key), frame.values.get(key)))
                                        throw invalid("Conflicting branch output");
                                    if (!Objects.equals(frame.values.get(key), value))
                                        merged.put(key, value);
                                });
                    frame.values = merged;
                    complete(frame);
                }
            }
            case CHOICE -> {
                if (!frame.initialized) {
                    frame.children.add(
                            child(
                                    frame,
                                    children.get(test(node.predicate(), frame) ? 0 : 1).childId(),
                                    frame.stepId,
                                    frame.item));
                    frame.initialized = true;
                }
                var selected = frame.children.getFirst();
                advance(selected);
                if (selected.done) {
                    frame.values = new TreeMap<>(selected.values);
                    complete(frame);
                }
            }
            case LOOP -> {
                if (frame.children.isEmpty()) {
                    if (!test(node.predicate(), frame)) {
                        complete(frame);
                        return;
                    }
                    if (frame.index >= node.bound())
                        throw ApplicationFailure.newNonRetryableFailure(
                                "Loop bound exhausted", "WORKFLOW_BOUND");
                    frame.children.add(
                            child(
                                    frame,
                                    children.getFirst().childId(),
                                    frame.stepId + "/iteration-" + frame.index,
                                    frame.item));
                }
                var active = frame.children.getFirst();
                advance(active);
                if (active.done) {
                    frame.values = new TreeMap<>(active.values);
                    frame.children.clear();
                    frame.index++;
                    state.transitions++;
                }
            }
            case FOREACH -> {
                var collection = read(value(node.collection(), frame));
                if (!collection.isArray() || collection.size() > node.bound())
                    throw invalid("Invalid bounded collection");
                if (frame.children.isEmpty()) {
                    if (frame.index == collection.size()) {
                        complete(frame);
                        return;
                    }
                    for (int i = frame.index;
                            i < Math.min(frame.index + node.concurrency(), collection.size());
                            i++)
                        frame.children.add(
                                child(
                                        frame,
                                        children.getFirst().childId(),
                                        frame.stepId + "/item-" + i,
                                        collection.get(i).toString()));
                }
                for (var branch : frame.children) advance(branch);
                if (frame.children.stream().allMatch(f -> f.done)) {
                    frame.index += frame.children.size();
                    frame.children.clear();
                    state.transitions++;
                }
            }
            case SUBWORKFLOW -> {
                if (frame.children.isEmpty()) {
                    var sub = node.childPlan().plan();
                    frame.children.add(
                            new Frame(
                                    node.childPlan().digest(),
                                    sub.rootNodeId(),
                                    frame.stepId + "/child/" + sub.rootNodeId(),
                                    Map.of(),
                                    frame.item));
                }
                var active = frame.children.getFirst();
                advance(active);
                if (active.done) {
                    complete(frame);
                }
            }
            case WAIT -> {
                if (node.waitSpec().kind() == WaitKind.TIMER) state.releases.remove(frame.stepId);
                if (!frame.waitRegistered) {
                    if (frame.deadline == 0)
                        frame.deadline =
                                Workflow.currentTimeMillis() + node.waitSpec().timeoutMillis();
                    frame.waitRegistered = true;
                    actions.add(
                            Async.procedure(
                                    () -> {
                                        projections.waiting(
                                                runId,
                                                frame.stepId,
                                                node.waitSpec().kind().name(),
                                                frame.deadline);
                                        frame.waitRegistered = true;
                                    }));
                    return;
                }
                if (node.waitSpec().kind() != WaitKind.TIMER
                        && state.releases.containsKey(frame.stepId)) {
                    boolean approved = state.releases.remove(frame.stepId);
                    if (node.waitSpec().kind() == WaitKind.APPROVAL && !approved) {
                        state.failureStep = frame.stepId;
                        throw ApplicationFailure.newNonRetryableFailure(
                                "Approval rejected", "WORKFLOW_APPROVAL_REJECTED");
                    }
                    complete(frame);
                } else if (Workflow.currentTimeMillis() >= frame.deadline) {
                    if (node.waitSpec().kind() != WaitKind.TIMER) {
                        state.failureStep = frame.stepId;
                        throw ApplicationFailure.newNonRetryableFailure(
                                "Wait expired", "WORKFLOW_TIMEOUT");
                    }
                    complete(frame);
                } else nextDeadline = Math.min(nextDeadline, frame.deadline);
            }
            case OPERATION_INVOCATION -> {
                Map<String, String> bindings = new TreeMap<>();
                node.bindings().forEach((key, ref) -> bindings.put(key, value(ref, frame)));
                var activity =
                        Workflow.newActivityStub(
                                PlanWalkActivities.class,
                                ActivityOptions.newBuilder()
                                        .setStartToCloseTimeout(Duration.ofMinutes(10))
                                        .setRetryOptions(
                                                RetryOptions.newBuilder()
                                                        .setMaximumAttempts(
                                                                node.retry().maximumAttempts())
                                                        .setInitialInterval(
                                                                Duration.ofMillis(
                                                                        node.retry()
                                                                                .initialDelayMillis()))
                                                        .build())
                                        .build());
                actions.add(
                        Async.procedure(
                                () -> {
                                    String result;
                                    try {
                                        result =
                                                activity.invoke(
                                                        runId,
                                                        frame.stepId,
                                                        codec.encode(plan),
                                                        node.id(),
                                                        bindings);
                                    } catch (RuntimeException failure) {
                                        state.failureStep = frame.stepId;
                                        throw failure;
                                    }
                                    frame.values.put(node.id(), result);
                                    projections.stepCompleted(runId, frame.stepId, result);
                                    frame.done = true;
                                    state.transitions++;
                                }));
            }
        }
    }

    private Frame child(Frame parent, String id, String path, String item) {
        return new Frame(parent.planDigest, id, path + "/" + id, parent.values, item);
    }

    private void complete(Frame frame) {
        if (actions.size() >= 32) return;
        actions.add(
                Async.procedure(
                        () -> {
                            projections.stepCompleted(runId, frame.stepId, null);
                            frame.done = true;
                            state.transitions++;
                        }));
    }

    private boolean test(Predicate predicate, Frame frame) {
        var actual = read(value(predicate.left(), frame));
        return switch (predicate.comparison()) {
            case EQUAL -> actual.equals(read(predicate.expectedJson()));
            case NOT_EQUAL -> !actual.equals(read(predicate.expectedJson()));
            case IS_SET -> !actual.isNull();
            case IS_EMPTY ->
                    actual.isNull()
                            || actual.isTextual() && actual.asText().isEmpty()
                            || actual.isContainerNode() && actual.isEmpty();
        };
    }

    private String value(ValueRef ref, Frame frame) {
        return switch (ref.source()) {
            case INPUT -> inputs.get(ref.key());
            case ITEM ->
                    "item".equals(ref.key())
                            ? frame.item
                            : read(frame.item).path(ref.key()).toString();
            case RESULT -> {
                String[] path = ref.key().split("\\.", 2);
                String raw = frame.values.get(path[0]);
                yield path.length == 1 ? raw : read(raw).path(path[1]).toString();
            }
        };
    }

    private boolean hasRelease(Frame frame) {
        return frame.waitRegistered && !frame.done && state.releases.containsKey(frame.stepId)
                || frame.children.stream().anyMatch(this::hasRelease);
    }

    private Frame waiting(Frame frame, String id) {
        if (frame.stepId.equals(id) && frame.waitRegistered && !frame.done) return frame;
        for (var child : frame.children) {
            var match = waiting(child, id);
            if (match != null) return match;
        }
        return null;
    }

    @Override
    public void release(String stepId, String releaseId, boolean approved) {
        // A signal may be replayed before the main workflow method initializes its cursor.
        // The application already fenced this durable command against the exact persisted wait.
        if (state == null) {
            earlyReleases.putIfAbsent(stepId, approved);
            return;
        }
        if (!state.cancelled) state.releases.putIfAbsent(stepId, approved);
    }

    @Override
    public void cancelRun() {
        if (state == null) earlyCancellation = true;
        else state.cancelled = true;
    }

    private void cancelled() {
        if (state.cancelled) throw new CanceledFailure("Workflow cancelled");
        CancellationScope.throwCanceled();
    }

    private JsonNode read(String value) {
        try {
            return value == null ? json.nullNode() : json.readTree(value);
        } catch (Exception e) {
            throw invalid("Invalid typed value");
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw invalid("Invalid cursor");
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw invalid("Invalid cursor");
        }
    }

    private static ApplicationFailure invalid(String message) {
        return ApplicationFailure.newNonRetryableFailure(message, "WORKFLOW_INVALID");
    }
}

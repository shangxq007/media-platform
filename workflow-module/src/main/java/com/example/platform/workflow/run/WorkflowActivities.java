package com.example.platform.workflow.run;

import com.example.platform.operation.invocation.*;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.workflow.plan.*;
import com.example.platform.workflow.temporal.PlanWalkActivities;

import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@ActivityImpl(taskQueues = "workflow-process")
public class WorkflowActivities implements PlanWalkActivities {
    private final WorkflowRunStore store;
    private final OperationInvocationPort operations;
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();

    public WorkflowActivities(WorkflowRunStore store, OperationInvocationPort operations) {
        this.store = store;
        this.operations = operations;
    }

    @Override
    @Transactional
    public String invoke(
            String runId,
            String stepId,
            String planJson,
            String nodeId,
            Map<String, String> bindings) {
        var run = store.lock(runId);
        var plan = codec.decode(planJson);
        String digest = codec.digest(plan);
        if (!contains(codec.decode(run.planJson()), digest))
            throw invalid("Plan not pinned by run");
        var node =
                plan.nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElseThrow();
        var base = node.operation();
        if (base == null) throw invalid("Not an Operation");
        for (String key : bindings.keySet())
            if (!Set.of("baseRevisionId", "baseContentHash").contains(key))
                throw invalid("Unsupported binding");
        String revision =
                bindings.containsKey("baseRevisionId")
                        ? text(bindings.get("baseRevisionId"))
                        : base.baseRevisionId();
        String hash =
                bindings.containsKey("baseContentHash")
                        ? text(bindings.get("baseContentHash"))
                        : base.baseContentHash();
        var request =
                new OperationRequest(
                        base.definitionId(),
                        base.version(),
                        base.target(),
                        base.parameters(),
                        revision,
                        hash,
                        base.requestMetadata());
        String requestDigest =
                RunJson.digest(digest + ":" + nodeId + ":" + RunJson.write(bindings));
        var existing = store.receipt(runId, stepId);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().get("request_digest").equals(requestDigest))
                throw invalid("Conflicting invocation identity");
            return (String) existing.getFirst().get("result_json");
        }
        if (WorkflowRunStore.terminal(run) || run.cancelRequested())
            throw ApplicationFailure.newNonRetryableFailure(
                    "Run cancelled or terminal", "WORKFLOW_CANCELLED");
        var actor = RunJson.read(run.actorJson(), CanonicalActor.class);
        var context =
                new OperationInvocationContext(
                        actor,
                        "workflow:" + runId + ":" + RunJson.digest(stepId),
                        runId,
                        "workflow");
        try {
            operations.validate(request, context, run.projectId());
            String implementation =
                    RunJson.write(List.of(request.definitionId(), request.version()));
            var pins =
                    RunJson.read(run.bindingsJson(), com.fasterxml.jackson.databind.JsonNode.class);
            if (!implementation.equals(pins.path(digest + ":" + nodeId).asText()))
                throw invalid("Pinned Operation implementation unavailable");
            var result = operations.invoke(request, context);
            Map<String, String> output;
            if (result instanceof OperationInvocationResult.Applied a)
                output =
                        Map.of(
                                "revisionId",
                                a.newRevisionId(),
                                "contentHash",
                                a.resultContentHash(),
                                "invocationId",
                                a.invocationId());
            else if (result instanceof OperationInvocationResult.NoOp n)
                output =
                        Map.of(
                                "revisionId",
                                n.baseRevisionId(),
                                "contentHash",
                                n.unchangedContentHash(),
                                "invocationId",
                                n.invocationId());
            else throw invalid("Unknown Operation outcome");
            String json = RunJson.write(output);
            store.receipt(runId, stepId, requestDigest, json);
            store.running(runId);
            return json;
        } catch (OperationInvocationException e) {
            // Owner failures are semantic outcomes, not permission to blindly retry an external
            // effect.
            throw ApplicationFailure.newNonRetryableFailure(
                    e.code().name(), "OPERATION_" + e.code().name());
        }
    }

    private boolean contains(WorkflowPlan plan, String digest) {
        return codec.digest(plan).equals(digest)
                || plan.nodes().stream()
                        .anyMatch(
                                n ->
                                        n.childPlan() != null
                                                && contains(n.childPlan().plan(), digest));
    }

    private String text(String json) {
        var value = RunJson.read(json, com.fasterxml.jackson.databind.JsonNode.class);
        if (!value.isTextual() || value.asText().isBlank()) throw invalid("Text binding required");
        return value.asText();
    }

    @Override
    @Transactional
    public void waiting(String runId, String stepId, String kind, long deadline) {
        var run = store.lock(runId);
        if (WorkflowRunStore.terminal(run) || run.cancelRequested())
            throw invalid("Run not active");
        store.waiting(runId, stepId, kind, deadline);
        store.running(runId);
    }

    @Override
    @Transactional
    public void stepCompleted(String runId, String stepId, String result) {
        var run = store.lock(runId);
        if (WorkflowRunStore.terminal(run)) return;
        store.completed(runId, stepId, result);
    }

    @Override
    @Transactional
    public String terminal(String runId, String status, String failureCode, String failedStep) {
        if (!Set.of("SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT").contains(status))
            throw invalid("Invalid terminal state");
        var run = store.lock(runId);
        if (WorkflowRunStore.terminal(run)) return run.status();
        String effective = run.cancelRequested() ? "CANCELLED" : status;
        if (run.cancelRequested()) failureCode = "CANCELLED";
        if ("SUCCEEDED".equals(effective)
                && !store.completed(
                        runId,
                        WorkflowStepIdentity.root(codec.decode(run.planJson()).rootNodeId())))
            throw invalid("Cannot complete an unfinished plan");
        if (!"SUCCEEDED".equals(effective))
            store.failedStep(runId, failedStep, effective, failureCode);
        store.terminal(runId, effective, "SUCCEEDED".equals(effective) ? null : failureCode);
        return effective;
    }

    private static ApplicationFailure invalid(String message) {
        return ApplicationFailure.newNonRetryableFailure(message, "WORKFLOW_INVALID_STATE");
    }
}

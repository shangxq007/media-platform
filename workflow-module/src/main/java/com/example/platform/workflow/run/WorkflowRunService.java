package com.example.platform.workflow.run;

import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.api.project.*;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.authorization.*;
import com.example.platform.workflow.authorization.AuthorizationActions;
import com.example.platform.workflow.plan.WorkflowPlanCodec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class WorkflowRunService {
    private final WorkflowRunStore store;
    private final WorkflowAdmission admission;
    private final CanonicalActorResolver actors;
    private final ProjectScopeQueries scopes;
    private final AuthorizationDecisionPort authorization;
    private final OutboxEventService outbox;
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();

    public WorkflowRunService(
            WorkflowRunStore store,
            WorkflowAdmission admission,
            CanonicalActorResolver actors,
            ProjectScopeQueries scopes,
            AuthorizationDecisionPort authorization,
            OutboxEventService outbox) {
        this.store = store;
        this.admission = admission;
        this.actors = actors;
        this.scopes = scopes;
        this.authorization = authorization;
        this.outbox = outbox;
    }

    public record Start(
            String definitionId,
            int definitionVersion,
            String projectId,
            String idempotencyKey,
            String inputsJson) {
        public Start {
            if (definitionId == null
                    || definitionId.isBlank()
                    || definitionVersion < 1
                    || projectId == null
                    || projectId.isBlank()
                    || inputsJson == null)
                throw new IllegalArgumentException("Exact definition, Project and inputs required");
        }
    }

    public record View(
            String id,
            String tenantId,
            String workspaceId,
            String projectId,
            String status,
            String workflowPlanDigest,
            String failureCode,
            List<Map<String, Object>> steps) {}

    @Transactional
    public View start(String tenant, Start command) {
        if (command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128)
            throw new IllegalArgumentException("Bounded idempotency key required");
        var actor = actor(tenant);
        var scope =
                authorize(
                        actor,
                        tenant,
                        command.projectId(),
                        null,
                        AuthorizationActions.WORKFLOW_EXECUTION_START);
        var inputs = RunJson.inputs(command.inputsJson());
        String requestDigest =
                RunJson.digest(
                        RunJson.write(
                                List.of(
                                        command.definitionId(),
                                        command.definitionVersion(),
                                        scope,
                                        actor.actorId(),
                                        actor.actorType(),
                                        Objects.toString(actor.accountId(), ""),
                                        inputs)));
        var existing = store.request(tenant, command.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().requestDigest().equals(requestDigest))
                throw new IllegalArgumentException("Conflicting idempotency key");
            return view(existing.get());
        }
        var definition =
                admission.published(tenant, command.definitionId(), command.definitionVersion());
        if (!scope.projectId().equals(definition.projectId()))
            throw new IllegalArgumentException("Definition Project mismatch");
        var plan = admission.resolve(definition);
        var bindings = admission.preflight(plan, actor, inputs);
        String id = UUID.randomUUID().toString();
        var run =
                new WorkflowRunStore.Run(
                        id,
                        tenant,
                        scope.workspaceId(),
                        scope.projectId(),
                        command.idempotencyKey(),
                        requestDigest,
                        RunJson.write(actor),
                        definition.definitionId().value(),
                        definition.version().versionNumber(),
                        codec.digest(plan),
                        codec.encode(plan),
                        RunJson.write(inputs),
                        RunJson.write(bindings),
                        "ACCEPTED",
                        false,
                        null);
        store.insert(run);
        outbox.append(
                RunEvents.START.append(
                        tenant,
                        new RunEvents.Start(tenant, id, run.workflowPlanDigest()),
                        "workflow-start:" + id));
        return view(run);
    }

    public View get(String tenant, String id) {
        var run = require(tenant, id);
        authorize(
                actor(tenant),
                tenant,
                run.projectId(),
                id,
                AuthorizationActions.WORKFLOW_EXECUTION_READ);
        return view(run);
    }

    @Transactional
    public View cancel(String tenant, String id) {
        var run = require(tenant, id);
        var actor = actor(tenant);
        authorize(
                actor, tenant, run.projectId(), id, AuthorizationActions.WORKFLOW_EXECUTION_CANCEL);
        run = store.lock(id);
        if (!WorkflowRunStore.terminal(run) && !run.cancelRequested()) {
            store.requestCancel(id, RunJson.write(actor));
            outbox.append(
                    RunEvents.CONTROL.append(
                            tenant,
                            new RunEvents.Control(tenant, id, null, "cancel", false, true),
                            "workflow-cancel:" + id));
        }
        return view(store.require(id));
    }

    @Transactional
    public View release(
            String tenant, String id, String stepId, String releaseId, boolean approved) {
        if (releaseId == null || releaseId.isBlank() || releaseId.length() > 128)
            throw new IllegalArgumentException("Release identity required");
        var run = require(tenant, id);
        var actor = actor(tenant);
        authorize(
                actor,
                tenant,
                run.projectId(),
                id,
                AuthorizationActions.WORKFLOW_EXECUTION_APPROVE);
        run = store.lock(id);
        if (WorkflowRunStore.terminal(run) || run.cancelRequested())
            throw new IllegalArgumentException("Run terminal/cancelling");
        var rows = store.lockWait(id, stepId);
        if (rows.size() != 1) throw new IllegalArgumentException("Wait not found");
        var row = rows.getFirst();
        if (!"WAITING".equals(row.get("status")) || "TIMER".equals(row.get("wait_kind")))
            throw new IllegalArgumentException("Not a releasable wait");
        if (row.get("release_id") != null) {
            var acceptedActor =
                    RunJson.read((String) row.get("released_by_json"), CanonicalActor.class);
            if (!actor.actorId().equals(acceptedActor.actorId())
                    || !Objects.equals(actor.accountId(), acceptedActor.accountId()))
                throw new IllegalArgumentException("Release identity belongs to another actor");
            if (!releaseId.equals(row.get("release_id"))
                    || !Objects.equals(approved, row.get("approved")))
                throw new IllegalArgumentException("Conflicting wait release");
            return view(run);
        }
        // Deadline uses database time at the command boundary, not a mutable browser clock.
        int changed = store.release(id, stepId, releaseId, approved, RunJson.write(actor));
        if (changed != 1) throw new IllegalArgumentException("Wait deadline expired");
        outbox.append(
                RunEvents.CONTROL.append(
                        tenant,
                        new RunEvents.Control(tenant, id, stepId, releaseId, approved, false),
                        "workflow-release:" + id + ":" + RunJson.digest(stepId)));
        return view(run);
    }

    private WorkflowRunStore.Run require(String tenant, String id) {
        return store.find(tenant, id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
    }

    private CanonicalActor actor(String tenant) {
        var actor =
                actors.resolveCurrentActor()
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.UNAUTHORIZED));
        if (!tenant.equals(actor.tenantId())
                || actor.actorType() == ActorType.USER
                        && (actor.accountId() == null || actor.accountId().isBlank()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN);
        return actor;
    }

    private ProjectScope authorize(
            CanonicalActor actor,
            String tenant,
            String project,
            String run,
            AuthorizationActions action) {
        var scope = scopes.resolveForAcceptance(tenant, project);
        authorization.requireAuthorized(
                new AuthorizationRequest(
                        actor,
                        action.action(),
                        new AuthorizableResourceRef(
                                AuthorizationResourceType.WORKFLOW_EXECUTION,
                                run,
                                tenant,
                                project,
                                null),
                        new AuthorizationContext("workflow", scope.workspaceId(), Map.of())));
        return scope;
    }

    private View view(WorkflowRunStore.Run run) {
        return new View(
                run.id(),
                run.tenantId(),
                run.workspaceId(),
                run.projectId(),
                run.status(),
                run.workflowPlanDigest(),
                run.failureCode(),
                store.steps(run.id()));
    }
}

package com.example.platform.workflow.run;

import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.operation.invocation.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.workflow.definition.domain.*;
import com.example.platform.workflow.definition.port.UserWorkflowDefinitionRepository;
import com.example.platform.workflow.plan.*;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkflowAdmission {
    private final UserWorkflowDefinitionRepository definitions;
    private final OperationInvocationPort operations;
    private final CapabilityRegistryPort capabilities;
    private final WorkflowPlanCodec codec = new WorkflowPlanCodec();

    public WorkflowAdmission(
            UserWorkflowDefinitionRepository definitions,
            OperationInvocationPort operations,
            CapabilityRegistryPort capabilities) {
        this.definitions = definitions;
        this.operations = operations;
        this.capabilities = capabilities;
    }

    public UserWorkflowDefinition published(String tenant, String id, int version) {
        var def =
                definitions
                        .findExactVersion(
                                tenant,
                                new UserWorkflowDefinitionId(id),
                                new UserWorkflowDefinitionVersion(version))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Published definition not found"));
        if (def.status() != UserWorkflowDefinitionStatus.PUBLISHED)
            throw new IllegalArgumentException("Definition not published");
        return def;
    }

    public WorkflowPlan resolve(UserWorkflowDefinition def) {
        return resolve(def, 1, new HashSet<>());
    }

    private WorkflowPlan resolve(UserWorkflowDefinition def, int depth, Set<String> ancestors) {
        String key = def.definitionId().value() + ":" + def.version().versionNumber();
        if (depth > 8 || !ancestors.add(key))
            throw new IllegalArgumentException("Recursive/deep child workflow");
        var plan = new WorkflowPlanCompiler().compile(def);
        List<WorkflowPlan.Node> nodes = new ArrayList<>();
        for (var n : plan.nodes()) {
            var pin = n.childPlan();
            if (pin != null) {
                var child =
                        resolve(
                                published(
                                        def.tenantId(),
                                        pin.plan().definitionId(),
                                        pin.plan().definitionVersion()),
                                depth + 1,
                                new HashSet<>(ancestors));
                if (!child.projectId().equals(def.projectId())
                        || !codec.digest(child).equals(pin.digest()))
                    throw new IllegalArgumentException("Published child pin/scope mismatch");
                pin = new WorkflowPlan.ChildPin(codec.digest(child), child);
            }
            nodes.add(
                    new WorkflowPlan.Node(
                            n.id(),
                            n.kind(),
                            n.predicate(),
                            n.waitSpec(),
                            n.bound(),
                            n.concurrency(),
                            n.collection(),
                            n.operation(),
                            n.capabilities(),
                            n.bindings(),
                            n.retry(),
                            pin));
        }
        return codec.decode(
                codec.encode(
                        new WorkflowPlan(
                                plan.formatVersion(),
                                plan.definitionId(),
                                plan.definitionVersion(),
                                plan.tenantId(),
                                plan.projectId(),
                                plan.rootNodeId(),
                                nodes,
                                plan.edges())));
    }

    public Map<String, String> preflight(
            WorkflowPlan plan, CanonicalActor actor, Map<String, String> inputs) {
        new WorkflowDataValidation().validate(plan, inputs);
        Map<String, String> pins = new TreeMap<>();
        collectBindings(plan, actor, pins, inputs, plan.rootNodeId(), null);
        return Map.copyOf(pins);
    }

    private void validateOperation(
            com.example.platform.operation.operation.OperationRequest request,
            OperationInvocationContext context,
            String projectId) {
        try {
            operations.validate(request, context, projectId);
        } catch (OperationInvocationException rejected) {
            var status =
                    switch (rejected.code()) {
                        case AUTHORIZATION_DENIED, AUTHORIZATION_CONTEXT_MISMATCH ->
                                org.springframework.http.HttpStatus.FORBIDDEN;
                        case IDEMPOTENCY_CONFLICT, STALE_BASE_REVISION, PLAN_CHANGED ->
                                org.springframework.http.HttpStatus.CONFLICT;
                        default -> org.springframework.http.HttpStatus.BAD_REQUEST;
                    };
            throw new org.springframework.web.server.ResponseStatusException(
                    status, rejected.code().name());
        }
    }

    private void collectBindings(
            WorkflowPlan plan,
            CanonicalActor actor,
            Map<String, String> pins,
            Map<String, String> inputs,
            String nodeId,
            com.fasterxml.jackson.databind.JsonNode item) {
        var node =
                plan.nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElseThrow();
        if (node.operation() != null) {
            var base = node.operation();
            String revision = base.baseRevisionId(), hash = base.baseContentHash();
            for (var binding : node.bindings().entrySet()) {
                if (binding.getValue().source() == WorkflowPlan.Source.RESULT) continue;
                var value = staticValue(binding.getValue(), inputs, item);
                if (value == null || value.isMissingNode())
                    continue; // An empty foreach has no item, but its owner contract is still
                // preflighted.
                if (!value.isTextual() || value.asText().isBlank())
                    throw new IllegalArgumentException("Operation binding requires text");
                if (binding.getKey().equals("baseRevisionId")) revision = value.asText();
                else hash = value.asText();
            }
            var request =
                    new com.example.platform.operation.operation.OperationRequest(
                            base.definitionId(),
                            base.version(),
                            base.target(),
                            base.parameters(),
                            revision,
                            hash,
                            base.requestMetadata());
            validateOperation(
                    request,
                    new OperationInvocationContext(actor, "workflow-admission", null, "workflow"),
                    plan.projectId());
            String key = codec.digest(plan) + ":" + node.id();
            if (!pins.containsKey(key)) {
                pins.put(key, RunJson.write(List.of(base.definitionId(), base.version())));
                for (var requirement : node.capabilities()) {
                    var ids = new ArrayList<com.example.platform.extension.domain.CapabilityId>();
                    ids.add(requirement.capabilityId());
                    ids.addAll(requirement.alternatives());
                    var available =
                            ids.stream()
                                    .flatMap(
                                            id ->
                                                    capabilities
                                                            .findCapabilityImplementations(id)
                                                            .stream())
                                    .filter(i -> requirement.accepts(i.contractVersion()))
                                    .sorted(
                                            Comparator.comparing(
                                                    i -> i.implementationId().toString()))
                                    .toList();
                    if (available.isEmpty())
                        throw new IllegalArgumentException("Missing compatible capability");
                    pins.put(
                            key + ":" + requirement.capabilityId(),
                            RunJson.write(available.getFirst()));
                }
            }
        }
        if (node.childPlan() != null) {
            var child = node.childPlan().plan();
            collectBindings(child, actor, pins, inputs, child.rootNodeId(), null);
        }
        var children = plan.edges().stream().filter(e -> e.parentId().equals(node.id())).toList();
        if (node.kind() == WorkflowPlan.Kind.FOREACH) {
            var collection = staticValue(node.collection(), inputs, item);
            if (collection == null || collection.isEmpty() || collection.isMissingNode()) {
                for (var edge : children)
                    collectBindings(
                            plan,
                            actor,
                            pins,
                            inputs,
                            edge.childId(),
                            com.fasterxml.jackson.databind.node.MissingNode.getInstance());
            } else
                for (var element : collection)
                    for (var edge : children)
                        collectBindings(plan, actor, pins, inputs, edge.childId(), element);
        } else
            for (var edge : children)
                collectBindings(plan, actor, pins, inputs, edge.childId(), item);
    }

    private com.fasterxml.jackson.databind.JsonNode staticValue(
            WorkflowPlan.ValueRef ref,
            Map<String, String> inputs,
            com.fasterxml.jackson.databind.JsonNode item) {
        if (ref.source() == WorkflowPlan.Source.INPUT)
            return RunJson.read(
                    inputs.get(ref.key()), com.fasterxml.jackson.databind.JsonNode.class);
        if (ref.source() == WorkflowPlan.Source.ITEM)
            return item == null || item.isMissingNode()
                    ? com.fasterxml.jackson.databind.node.MissingNode.getInstance()
                    : "item".equals(ref.key()) ? item : item.get(ref.key());
        return null;
    }
}

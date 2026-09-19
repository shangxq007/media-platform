package com.example.platform.workflow.plan;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTargetRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit sealed value vocabulary only; no default typing or class names supplied by callers. */
public final class WorkflowPlanCodec {
    private final ObjectMapper mapper;
    public WorkflowPlanCodec() {
        mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        register(OperationParameters.class, new HashSet<>());
        register(OperationTargetRequest.class, new HashSet<>());
    }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="variant")
    private interface Variant {}
    private void register(Class<?> type, Set<Class<?>> seen) {
        if (!seen.add(type)) return;
        if (type.isSealed()) {
            mapper.addMixIn(type, Variant.class);
            for (Class<?> sub : type.getPermittedSubclasses()) {
                mapper.registerSubtypes(new NamedType(sub, sub.getSimpleName()));
                register(sub, seen);
            }
        }
        if (type.isRecord()) for (var component : type.getRecordComponents()) register(component.getType(), seen);
    }
    public WorkflowPlan.Node decodeNode(String json) {
        try { return mapper.readValue(json, WorkflowPlan.Node.class); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid typed Workflow node", e); }
    }
    public WorkflowPlan decode(String json) {
        try {
            WorkflowPlan plan = mapper.readValue(json, WorkflowPlan.class);
            new WorkflowPlanValidator().validate(plan);
            verifyPins(plan);
            return plan;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid typed Workflow plan", e); }
    }
    public String encode(WorkflowPlan plan) {
        new WorkflowPlanValidator().validate(plan);
        verifyPins(plan);
        try {
            var ordered = new WorkflowPlan(plan.formatVersion(), plan.definitionId(), plan.definitionVersion(),
                    plan.tenantId(), plan.projectId(), plan.rootNodeId(),
                    plan.nodes().stream().map(this::normalizeNode).sorted(Comparator.comparing(WorkflowPlan.Node::id)).toList(),
                    plan.edges().stream().sorted(Comparator.comparing(WorkflowPlan.ControlEdge::parentId)
                            .thenComparingInt(WorkflowPlan.ControlEdge::order)).toList());
            return mapper.writeValueAsString(ordered);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Unserializable Workflow plan", e); }
    }
    private WorkflowPlan.Node normalizeNode(WorkflowPlan.Node node) {
        var child = node.childPlan();
        if (child != null) child = new WorkflowPlan.ChildPin(child.digest(), decode(encode(child.plan())));
        return new WorkflowPlan.Node(node.id(), node.kind(), node.predicate(), node.waitSpec(), node.bound(),
                node.concurrency(), node.collection(), node.operation(), node.capabilities(), node.bindings(), node.retry(), child);
    }
    public String digest(WorkflowPlan plan) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(encode(plan).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void verifyPins(WorkflowPlan plan) {
        for (var node : plan.nodes()) if (node.childPlan() != null
                && !digest(node.childPlan().plan()).equals(node.childPlan().digest()))
            throw new IllegalArgumentException("Child plan digest mismatch");
    }
}

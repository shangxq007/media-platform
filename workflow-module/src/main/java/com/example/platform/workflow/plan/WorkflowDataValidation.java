package com.example.platform.workflow.plan;

import static com.example.platform.workflow.plan.WorkflowPlan.*;

import com.fasterxml.jackson.databind.*;

import java.util.*;

/**
 * Definite availability follows control ownership. Statically pinned collection items are
 * type-checked.
 */
public final class WorkflowDataValidation {
    private final ObjectMapper json = new ObjectMapper();

    public void validate(WorkflowPlan plan, Map<String, String> inputs) {
        Map<String, JsonNode> values = new HashMap<>();
        inputs.forEach((k, v) -> values.put(k, parse(v)));
        walk(plan, plan.rootNodeId(), new HashSet<>(), values, null);
    }

    private Set<String> walk(
            WorkflowPlan plan,
            String id,
            Set<String> available,
            Map<String, JsonNode> inputs,
            JsonNode item) {
        Node node = plan.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
        var children =
                plan.edges().stream()
                        .filter(e -> e.parentId().equals(id))
                        .sorted(Comparator.comparingInt(ControlEdge::order))
                        .toList();
        Set<String> result = new HashSet<>(available);
        if (node.predicate() != null) {
            boolean optionalLoopResult =
                    node.kind() == Kind.LOOP
                            && node.predicate().left().source() == Source.RESULT
                            && Set.of(Comparison.IS_SET, Comparison.IS_EMPTY)
                                    .contains(node.predicate().comparison())
                            && descendants(plan, node.id()).contains(node.predicate().left().key());
            if (!optionalLoopResult) check(node.predicate().left(), available, inputs, item, false);
            if (node.predicate().comparison() == Comparison.EQUAL
                    || node.predicate().comparison() == Comparison.NOT_EQUAL)
                parse(node.predicate().expectedJson());
        }
        node.bindings()
                .forEach(
                        (field, ref) -> {
                            if (!Set.of("baseRevisionId", "baseContentHash").contains(field))
                                fail("Unsupported Operation binding target");
                            check(ref, available, inputs, item, true);
                        });
        switch (node.kind()) {
            case SEQUENCE -> {
                for (var edge : children) result = walk(plan, edge.childId(), result, inputs, item);
            }
            case PARALLEL -> {
                for (var edge : children)
                    result.addAll(
                            walk(plan, edge.childId(), new HashSet<>(available), inputs, item));
            }
            case CHOICE -> {
                var left =
                        walk(
                                plan,
                                children.get(0).childId(),
                                new HashSet<>(available),
                                inputs,
                                item);
                left.retainAll(
                        walk(
                                plan,
                                children.get(1).childId(),
                                new HashSet<>(available),
                                inputs,
                                item));
                result.addAll(left);
            }
            case LOOP ->
                    walk(
                            plan,
                            children.getFirst().childId(),
                            new HashSet<>(available),
                            inputs,
                            item);
            case FOREACH -> {
                check(node.collection(), available, inputs, item, false);
                if (node.collection().source() == Source.RESULT)
                    fail("Operation outcomes are not collection values");
                JsonNode collection = staticValue(node.collection(), inputs, item);
                if (!collection.isMissingNode()
                        && (!collection.isArray() || collection.size() > node.bound()))
                    fail("Invalid bounded collection");
                if (collection.isEmpty() || collection.isMissingNode())
                    walk(
                            plan,
                            children.getFirst().childId(),
                            new HashSet<>(available),
                            inputs,
                            com.fasterxml.jackson.databind.node.MissingNode.getInstance());
                else
                    for (JsonNode element : collection)
                        walk(
                                plan,
                                children.getFirst().childId(),
                                new HashSet<>(available),
                                inputs,
                                element);
            }
            case SUBWORKFLOW -> validate(node.childPlan().plan(), stringInputs(inputs));
            case OPERATION_INVOCATION -> result.add(node.id());
            case WAIT -> {}
        }
        return result;
    }

    private Set<String> descendants(WorkflowPlan plan, String id) {
        Set<String> result = new HashSet<>();
        for (var edge : plan.edges())
            if (edge.parentId().equals(id)) {
                result.add(edge.childId());
                result.addAll(descendants(plan, edge.childId()));
            }
        return result;
    }

    private Map<String, String> stringInputs(Map<String, JsonNode> inputs) {
        Map<String, String> values = new HashMap<>();
        inputs.forEach((k, v) -> values.put(k, v.toString()));
        return values;
    }

    private void check(
            ValueRef ref,
            Set<String> available,
            Map<String, JsonNode> inputs,
            JsonNode item,
            boolean text) {
        if (ref.source() != Source.RESULT) {
            var value = staticValue(ref, inputs, item);
            if (text && !value.isMissingNode() && (!value.isTextual() || value.asText().isBlank()))
                fail("Operation binding requires nonempty text");
            return;
        }
        String[] parts = ref.key().split("\\.", -1);
        if (!available.contains(parts[0])) fail("Result not definitely available: " + ref.key());
        if (parts.length == 2
                && !Set.of("revisionId", "contentHash", "invocationId").contains(parts[1]))
            fail("Unknown Operation result field");
        if (text && (parts.length != 2 || !Set.of("revisionId", "contentHash").contains(parts[1])))
            fail("Operation result field is not text");
        if (parts.length > 2) fail("Arbitrary binding paths prohibited");
    }

    private JsonNode staticValue(ValueRef ref, Map<String, JsonNode> inputs, JsonNode item) {
        JsonNode value;
        if (ref.source() == Source.INPUT) value = inputs.get(ref.key());
        else if (ref.source() == Source.ITEM)
            value =
                    item == null
                            ? null
                            : item.isMissingNode()
                                    ? item
                                    : "item".equals(ref.key()) ? item : item.get(ref.key());
        else throw new IllegalArgumentException("Result is not a statically pinned value");
        if (value == null) fail("Missing data binding: " + ref.key());
        return value;
    }

    private JsonNode parse(String value) {
        try {
            if (value == null) fail("Typed JSON value required");
            var n = json.readTree(value);
            if (n == null) fail("Typed JSON value required");
            return n;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid data value", e);
        }
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}

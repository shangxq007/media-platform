package com.example.platform.workflow.definition.validation;

import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import com.example.platform.graph.api.GraphViews;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.WorkflowNodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic W2 V1 graph + configuration validation (graph-validation-
 * contract.tsv, node-type-contract.tsv, configuration-parameter-contract.txt).
 * Reuses the platform-algorithms/graph kernel for G-008/G-009/G-010 — no
 * second graph-algorithm implementation. Never executes nodes.
 */
public final class UserWorkflowDefinitionValidator {

    private UserWorkflowDefinitionValidator() {
    }

    public static final int MAX_NODES = 100;
    public static final int MAX_EDGES = 500;
    public static final int MAX_CONFIG_BYTES = 64 * 1024;
    public static final int MAX_PARAMETER_BYTES = 16 * 1024;
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final ObjectMapper CANONICAL =
            new ObjectMapper().configure(
                    com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /** Secret-shaped value patterns (credential-residue conventions). */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(ghp_|gho_|ghu_|ghs_|github_pat_|glpat-|gldt-|xox[baprs]-)"
                    + "|(sk-(ant|proj|svcacct)-)|(sk-[A-Za-z0-9]{20,})"
                    + "|(AKIA[0-9A-Z]{16})|(AIza[0-9A-Za-z_-]{20,})|(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})");

    /** Per-type config schema: required fields, optional fields, value constraints. */
    private record ConfigSchema(Set<String> required, Set<String> optional,
                                Map<String, Pattern> valuePatterns) {
    }

    private static final Map<WorkflowNodeType, ConfigSchema> SCHEMAS = schemaMap();

    private static Map<WorkflowNodeType, ConfigSchema> schemaMap() {
        Map<WorkflowNodeType, ConfigSchema> m = new TreeMap<>();
        m.put(WorkflowNodeType.ACTION, new ConfigSchema(
                Set.of("capabilityKey", "capabilityVersion"),
                Set.of("inputRefs", "outputRefs"), Map.of()));
        m.put(WorkflowNodeType.EXTENSION_POINT, new ConfigSchema(
                Set.of("extensionPointKey"), Set.of("providerKey"), Map.of()));
        m.put(WorkflowNodeType.CONDITION, new ConfigSchema(
                Set.of("conditionType", "fieldRef"), Set.of("expectedValue"),
                Map.of("conditionType",
                        Pattern.compile("^(EQUAL|NOT_EQUAL|IS_SET|IS_EMPTY)$"))));
        m.put(WorkflowNodeType.APPROVAL, new ConfigSchema(
                Set.of("approverRole"), Set.of("timeoutMinutes"), Map.of()));
        m.put(WorkflowNodeType.DELAY, new ConfigSchema(
                Set.of("durationMinutes"), Set.of(),
                Map.of("durationMinutes", Pattern.compile("^([1-9][0-9]{0,3})$")))); // 1..1440
        m.put(WorkflowNodeType.NOTIFICATION, new ConfigSchema(
                Set.of("notificationType"), Set.of("channel"), Map.of()));
        m.put(WorkflowNodeType.WEBHOOK, new ConfigSchema(
                Set.of("webhookKey"), Set.of("payloadSchemaRef"), Map.of()));
        m.put(WorkflowNodeType.HOOK, new ConfigSchema(
                Set.of("hookPointKey"), Set.of("hookPhase"),
                Map.of("hookPhase", Pattern.compile("^(BEFORE|AFTER)$"))));
        return m;
    }

    public static UserWorkflowValidationResult validate(UserWorkflowDefinition definition) {
        List<UserWorkflowValidationIssue> issues = new ArrayList<>();

        // Configuration schema version (definition-level)
        if (definition.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_SCHEMA_VERSION,
                    "definition schemaVersion " + definition.schemaVersion()
                            + " is not supported (supported: " + SUPPORTED_SCHEMA_VERSION + ")"));
        }

        // G-001 / G-002 bounds
        if (definition.nodes().size() > MAX_NODES) {
            issues.add(issue(UserWorkflowValidationCode.G_001_NODE_COUNT_LIMIT,
                    "node count " + definition.nodes().size() + " exceeds " + MAX_NODES));
        }
        if (definition.edges().size() > MAX_EDGES) {
            issues.add(issue(UserWorkflowValidationCode.G_002_EDGE_COUNT_LIMIT,
                    "edge count " + definition.edges().size() + " exceeds " + MAX_EDGES));
        }

        // G-003 unique node ids
        Set<String> nodeIds = new TreeSet<>();
        for (UserWorkflowDefinitionNode node : definition.nodes()) {
            if (!nodeIds.add(node.nodeId())) {
                issues.add(issue(UserWorkflowValidationCode.G_003_NODE_IDS_UNIQUE,
                        "duplicate nodeId: " + node.nodeId()));
            }
        }

        // Edges: endpoints (G-004/G-005), self (G-006), duplicates (G-011)
        Set<String> edgeKeys = new HashSet<>();
        for (UserWorkflowDefinitionEdge edge : definition.edges()) {
            if (!nodeIds.contains(edge.sourceNodeId())) {
                issues.add(issue(UserWorkflowValidationCode.G_004_EDGE_SOURCE_EXISTS,
                        "edge " + edge.edgeId() + " source missing: " + edge.sourceNodeId()));
            }
            if (!nodeIds.contains(edge.targetNodeId())) {
                issues.add(issue(UserWorkflowValidationCode.G_005_EDGE_TARGET_EXISTS,
                        "edge " + edge.edgeId() + " target missing: " + edge.targetNodeId()));
            }
            if (edge.sourceNodeId().equals(edge.targetNodeId())) {
                issues.add(issue(UserWorkflowValidationCode.G_006_SELF_EDGE_PROHIBITED,
                        "edge " + edge.edgeId() + " is a self edge"));
            }
            String key = edge.sourceNodeId() + "|" + edge.targetNodeId() + "|"
                    + (edge.conditionRef() == null ? "" : edge.conditionRef());
            if (!edgeKeys.add(key)) {
                issues.add(issue(UserWorkflowValidationCode.G_011_DUPLICATE_EDGE_PROHIBITED,
                        "duplicate edge " + edge.edgeId() + " (" + key + ")"));
            }
        }

        // G-007 exactly one entry node (zero incoming)
        List<String> entries = nodeIds.stream()
                .filter(n -> definition.edges().stream().noneMatch(e -> n.equals(e.targetNodeId())))
                .sorted()
                .toList();
        if (entries.size() != 1) {
            issues.add(issue(UserWorkflowValidationCode.G_007_EXACTLY_ONE_ENTRY,
                    "expected exactly one entry node, found " + entries.size() + ": " + entries));
        }

        // G-008/G-009/G-010 via the deterministic graph kernel
        if (!nodeIds.isEmpty()) {
            DirectedGraphView<String> graph = GraphViews.directedFromEdges(nodeIds,
                    definition.edges().stream()
                            .map(e -> Map.entry(e.sourceNodeId(), e.targetNodeId()))
                            .collect(Collectors.toList()));

            // G-009: directed cycle detection (entry-independent)
            if (GraphAlgorithms.detectCycles(graph).hasCycle()) {
                issues.add(issue(UserWorkflowValidationCode.G_009_GRAPH_ACYCLIC,
                        "directed cycle detected"));
            }

            // G-008: weak connectivity (single component). Build the undirected
            // view and check reachability from a deterministic start node.
            List<Map.Entry<String, String>> undirected = new ArrayList<>();
            for (UserWorkflowDefinitionEdge e : definition.edges()) {
                undirected.add(Map.entry(e.sourceNodeId(), e.targetNodeId()));
                undirected.add(Map.entry(e.targetNodeId(), e.sourceNodeId()));
            }
            DirectedGraphView<String> undirectedView =
                    GraphViews.directedFromEdges(nodeIds, undirected);
            String start = nodeIds.iterator().next();
            Set<String> component = GraphAlgorithms.reachableFrom(undirectedView, Set.of(start));
            if (!component.containsAll(nodeIds)) {
                issues.add(issue(UserWorkflowValidationCode.G_008_GRAPH_CONNECTED,
                        "graph is not a single connected component"));
            }

            // G-010: directed reachability from the single entry node
            if (entries.size() == 1) {
                Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of(entries.get(0)));
                Set<String> unreachable = new TreeSet<>(nodeIds);
                unreachable.removeAll(reachable);
                for (String n : unreachable) {
                    issues.add(issue(UserWorkflowValidationCode.G_010_ALL_NODES_REACHABLE,
                            "node not reachable from entry: " + n));
                }
            }
        }

        // G-012 at least one terminal node (zero outgoing)
        List<String> terminals = nodeIds.stream()
                .filter(n -> definition.edges().stream().noneMatch(e -> n.equals(e.sourceNodeId())))
                .sorted()
                .toList();
        if (terminals.isEmpty()) {
            issues.add(issue(UserWorkflowValidationCode.G_012_TERMINAL_NODE_REQUIRED,
                    "at least one terminal node is required"));
        }

        // Per-node configuration validation
        for (UserWorkflowDefinitionNode node : definition.nodes().stream()
                .sorted(java.util.Comparator.comparing(UserWorkflowDefinitionNode::nodeId)).toList()) {
            validateNodeConfig(node, issues);
        }

        return new UserWorkflowValidationResult(
                issues.stream().noneMatch(i -> i.severity() == UserWorkflowValidationIssue.Severity.ERROR),
                issues);
    }

    private static void validateNodeConfig(
            UserWorkflowDefinitionNode node, List<UserWorkflowValidationIssue> issues) {
        String json = node.configValues().canonicalJson();

        // CONFIG_TOO_LARGE
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_TOO_LARGE,
                    "node " + node.nodeId() + " config exceeds " + MAX_CONFIG_BYTES + " bytes"));
        }
        // parameter declaration documents
        int parameterBytes = declarationBytes(node);
        if (parameterBytes > MAX_PARAMETER_BYTES) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_TOO_LARGE,
                    "node " + node.nodeId() + " parameter declarations exceed " + MAX_PARAMETER_BYTES + " bytes"));
        }
        // CONFIG_SECRET_LIKE_VALUE
        if (SECRET_PATTERN.matcher(json).find()) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_SECRET_LIKE_VALUE,
                    "node " + node.nodeId() + " config contains a secret-like value"));
        }

        ConfigSchema schema = SCHEMAS.get(node.nodeType());
        if (schema == null) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE,
                    "node " + node.nodeId() + " has unsupported node type: " + node.nodeType()));
            return;
        }
        if (node.configValues().schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_SCHEMA_VERSION,
                    "node " + node.nodeId() + " config schema version unsupported"));
        }

        JsonNode tree;
        try {
            tree = CANONICAL.readTree(json);
        } catch (Exception e) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE,
                    "node " + node.nodeId() + " config is not valid JSON"));
            return;
        }
        if (tree == null || !tree.isObject()) {
            issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE,
                    "node " + node.nodeId() + " config must be a JSON object"));
            return;
        }

        Set<String> allowed = new HashSet<>(schema.required());
        allowed.addAll(schema.optional());

        List<String> fieldNames = new ArrayList<>();
        tree.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.sort(String::compareTo);
        for (String field : fieldNames) {
            JsonNode value = tree.get(field);
            if (value == null || value.isNull()) {
                issues.add(issue(UserWorkflowValidationCode.CONFIG_NULL_FIELD,
                        "node " + node.nodeId() + " field " + field + " must not be null"));
            } else if (!allowed.contains(field)) {
                issues.add(issue(UserWorkflowValidationCode.CONFIG_UNKNOWN_FIELD,
                        "node " + node.nodeId() + " unknown config field: " + field));
            }
        }
        for (String required : new TreeSet<>(schema.required())) {
            if (tree.get(required) == null || tree.get(required).isNull()) {
                issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE,
                        "node " + node.nodeId() + " missing required config field: " + required));
            }
        }
        for (Map.Entry<String, Pattern> constraint : schema.valuePatterns().entrySet()) {
            JsonNode value = tree.get(constraint.getKey());
            if (value != null && !value.isNull()) {
                if (!value.isTextual() || !constraint.getValue().matcher(value.asText()).matches()) {
                    issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE,
                            "node " + node.nodeId() + " field " + constraint.getKey()
                                    + " violates its value constraint"));
                }
            }
        }
        if (WorkflowNodeType.DELAY == node.nodeType() && tree.has("durationMinutes")) {
            JsonNode d = tree.get("durationMinutes");
            if (d != null && d.isNumber() && d.asInt() > 1440) {
                issues.add(issue(UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE,
                        "node " + node.nodeId() + " durationMinutes exceeds 1440"));
            }
        }
    }

    private static int declarationBytes(UserWorkflowDefinitionNode node) {
        try {
            List<Object> all = new ArrayList<>();
            all.addAll(node.inputDeclarations());
            all.addAll(node.outputDeclarations());
            return CANONICAL.writeValueAsBytes(all).length;
        } catch (Exception e) {
            return MAX_PARAMETER_BYTES + 1;
        }
    }

    private static UserWorkflowValidationIssue issue(
            UserWorkflowValidationCode code, String message) {
        return new UserWorkflowValidationIssue(code, message,
                UserWorkflowValidationIssue.Severity.ERROR);
    }
}

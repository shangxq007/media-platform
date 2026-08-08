package com.example.platform.workflow.definition.validation;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.domain.WorkflowNodeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UWD-RED-005..011 (VALIDATION). Authentic RED: fails at compile time until
 * the W2 validator exists.
 */
class UserWorkflowDefinitionValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");

    private static UserWorkflowDefinitionNode node(String id, WorkflowNodeType type, String config) {
        return new UserWorkflowDefinitionNode(id, type, "node-" + id,
                "w2/" + type.name().toLowerCase() + "/config/v1",
                new UserWorkflowDefinitionNode.VersionedJsonDocument(1, config),
                List.of(), List.of(), UserWorkflowDefinitionNode.ErrorPolicy.FAIL);
    }

    private static UserWorkflowDefinitionNode actionNode(String id) {
        return node(id, WorkflowNodeType.ACTION,
                "{\"capabilityKey\":\"render.render-job.create\",\"capabilityVersion\":\"1\"}");
    }

    private static UserWorkflowDefinition build(List<UserWorkflowDefinitionNode> nodes,
                                                List<UserWorkflowDefinitionEdge> edges) {
        return new UserWorkflowDefinition(
                UserWorkflowDefinitionId.generate(), UserWorkflowDefinitionVersion.of(1),
                "tenant-a", null, "wf", null, UserWorkflowDefinitionStatus.DRAFT,
                nodes, edges, List.of(), UserWorkflowTriggerBinding.manual(),
                1, 1L, NOW, "u-1", NOW, "u-1", null, null, null, null);
    }

    private static List<UserWorkflowDefinitionNode> chain(int n) {
        List<UserWorkflowDefinitionNode> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            nodes.add(actionNode("n" + i));
        }
        return nodes;
    }

    private static List<UserWorkflowDefinitionEdge> chainEdges(int n) {
        List<UserWorkflowDefinitionEdge> edges = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            edges.add(UserWorkflowDefinitionEdge.unconditional("e" + i, "n" + i, "n" + (i + 1), i));
        }
        return edges;
    }

    @Test
    void nodeCountAboveLimitRejected() {
        UserWorkflowDefinition d = build(chain(101), chainEdges(101));
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_001_NODE_COUNT_LIMIT));
    }

    @Test
    void edgeCountAboveLimitRejected() {
        List<UserWorkflowDefinitionNode> nodes = chain(100);
        List<UserWorkflowDefinitionEdge> edges = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            edges.add(UserWorkflowDefinitionEdge.unconditional("e" + i, "n0", "n" + (i % 100), i));
        }
        UserWorkflowDefinition d = build(nodes, edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_002_EDGE_COUNT_LIMIT));
    }

    @Test
    void cycleRejected() {
        List<UserWorkflowDefinitionEdge> edges = List.of(
                UserWorkflowDefinitionEdge.unconditional("e1", "n0", "n1", 0),
                UserWorkflowDefinitionEdge.unconditional("e2", "n1", "n0", 1));
        UserWorkflowDefinition d = build(chain(2), edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_009_GRAPH_ACYCLIC));
    }

    @Test
    void multipleEntryNodesRejected() {
        List<UserWorkflowDefinitionEdge> edges = List.of(
                UserWorkflowDefinitionEdge.unconditional("e1", "n0", "n2", 0),
                UserWorkflowDefinitionEdge.unconditional("e2", "n1", "n2", 1));
        UserWorkflowDefinition d = build(chain(3), edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_007_EXACTLY_ONE_ENTRY));
    }

    @Test
    void disconnectedGraphRejected() {
        List<UserWorkflowDefinitionEdge> edges = List.of(
                UserWorkflowDefinitionEdge.unconditional("e1", "n0", "n1", 0),
                UserWorkflowDefinitionEdge.unconditional("e2", "n2", "n3", 1));
        UserWorkflowDefinition d = build(chain(4), edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_008_GRAPH_CONNECTED));
    }

    @Test
    void missingEdgeEndpointRejected() {
        List<UserWorkflowDefinitionEdge> edges = List.of(
                UserWorkflowDefinitionEdge.unconditional("e1", "n0", "missing", 0));
        UserWorkflowDefinition d = build(chain(2), edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_004_EDGE_SOURCE_EXISTS)
                || hasIssue(d, UserWorkflowValidationCode.G_005_EDGE_TARGET_EXISTS));
    }

    @Test
    void validChainPasses() {
        UserWorkflowDefinition d = build(chain(3), chainEdges(3));
        assertTrue(UserWorkflowDefinitionValidator.validate(d).valid());
    }

    @Test
    void selfEdgeRejected() {
        List<UserWorkflowDefinitionEdge> edges = List.of(
                UserWorkflowDefinitionEdge.unconditional("e1", "n0", "n0", 0));
        UserWorkflowDefinition d = build(chain(2), edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_006_SELF_EDGE_PROHIBITED));
    }

    @Test
    void duplicateEdgeRejected() {
        List<UserWorkflowDefinitionEdge> edges = List.of(
                UserWorkflowDefinitionEdge.unconditional("e1", "n0", "n1", 0),
                UserWorkflowDefinitionEdge.unconditional("e2", "n0", "n1", 1));
        UserWorkflowDefinition d = build(chain(2), edges);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_011_DUPLICATE_EDGE_PROHIBITED));
    }

    @Test
    void invalidNodeConfigurationRejected() {
        // unknown config field
        UserWorkflowDefinition d1 = build(List.of(
                node("n0", WorkflowNodeType.ACTION, "{\"capabilityKey\":\"k\",\"capabilityVersion\":\"1\",\"bogus\":1}")), List.of());
        assertFalse(UserWorkflowDefinitionValidator.validate(d1).valid());
        assertTrue(hasIssue(d1, UserWorkflowValidationCode.CONFIG_UNKNOWN_FIELD));
        // missing required field
        UserWorkflowDefinition d2 = build(List.of(
                node("n0", WorkflowNodeType.ACTION, "{\"capabilityKey\":\"k\"}")), List.of());
        assertFalse(UserWorkflowDefinitionValidator.validate(d2).valid());
        assertTrue(hasIssue(d2, UserWorkflowValidationCode.CONFIG_INVALID_NODE_TYPE));
        // secret-like value
        UserWorkflowDefinition d3 = build(List.of(
                node("n0", WorkflowNodeType.ACTION, "{\"capabilityKey\":\"ghp_abc12345\",\"capabilityVersion\":\"1\"}")), List.of());
        assertFalse(UserWorkflowDefinitionValidator.validate(d3).valid());
        assertTrue(hasIssue(d3, UserWorkflowValidationCode.CONFIG_SECRET_LIKE_VALUE));
        // configuration too large
        String big = "{\"capabilityKey\":\"" + "x".repeat(70 * 1024) + "\",\"capabilityVersion\":\"1\"}";
        UserWorkflowDefinition d4 = build(List.of(node("n0", WorkflowNodeType.ACTION, big)), List.of());
        assertFalse(UserWorkflowDefinitionValidator.validate(d4).valid());
        assertTrue(hasIssue(d4, UserWorkflowValidationCode.CONFIG_TOO_LARGE));
    }

    @Test
    void invalidSchemaVersionRejected() {
        UserWorkflowDefinition d = new UserWorkflowDefinition(
                UserWorkflowDefinitionId.generate(), UserWorkflowDefinitionVersion.of(1),
                "tenant-a", null, "wf", null, UserWorkflowDefinitionStatus.DRAFT,
                chain(1), List.of(), List.of(), UserWorkflowTriggerBinding.manual(),
                2, 1L, NOW, "u-1", NOW, "u-1", null, null, null, null);
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.CONFIG_INVALID_SCHEMA_VERSION));
    }

    @Test
    void duplicateNodeIdRejected() {
        UserWorkflowDefinition d = build(
                List.of(actionNode("n0"), actionNode("n0")), List.of());
        assertFalse(UserWorkflowDefinitionValidator.validate(d).valid());
        assertTrue(hasIssue(d, UserWorkflowValidationCode.G_003_NODE_IDS_UNIQUE));
    }

    private static boolean hasIssue(UserWorkflowDefinition d, UserWorkflowValidationCode code) {
        return UserWorkflowDefinitionValidator.validate(d).issues().stream()
                .anyMatch(i -> i.issueCode() == code);
    }
}

package com.example.platform.workflow.definition.infrastructure;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowErrorCode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowParameterDeclaration;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.port.UserWorkflowDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * JDBC adapter for the user workflow definition aggregate
 * (persistence-contract.json). JdbcTemplate + canonical JSON in TEXT columns;
 * optimistic_version BIGINT compare-and-set. No database FKs (application-
 * enforced integrity, platform convention).
 */
@Repository
public class UserWorkflowDefinitionJdbcRepository implements UserWorkflowDefinitionRepository {

    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final JdbcTemplate jdbc;

    public UserWorkflowDefinitionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── writes ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void insertDraft(UserWorkflowDefinition draft) {
        try {
            jdbc.update("""
                    INSERT INTO user_workflow_definition
                    (definition_id, tenant_id, project_id, created_at, created_by)
                    VALUES (?, ?, ?, ?, ?)
                    """, draft.definitionId().value(), draft.tenantId(), draft.projectId(),
                    Timestamp.from(draft.createdAt()), draft.createdBy());
            insertVersionRows(draft);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT,
                    "definition version already exists: " + draft.definitionId() + "/" + draft.version());
        }
    }

    @Override
    @Transactional
    public UserWorkflowDefinition updateDraft(UserWorkflowDefinition updated) {
        int rows = jdbc.update("""
                UPDATE user_workflow_definition_version SET
                name = ?, description = ?, status = ?, schema_version = ?,
                trigger_json = ?, parameter_json = ?,
                updated_at = ?, updated_by = ?, optimistic_version = optimistic_version + 1
                WHERE definition_id = ? AND version_number = ?
                AND tenant_id = ? AND optimistic_version = ? AND status = 'DRAFT'
                """, updated.name(), updated.description(), updated.status().name(),
                updated.schemaVersion(), triggerJson(updated.triggerBinding()),
                parametersJson(updated.parameters()), Timestamp.from(updated.updatedAt()),
                updated.updatedBy(), updated.definitionId().value(), updated.version().versionNumber(),
                updated.tenantId(), updated.optimisticVersion());
        if (rows == 0) {
            throw optimisticOrImmutable(updated);
        }
        replaceGraphRows(updated);
        return withBumpedVersion(updated);
    }

    @Override
    @Transactional
    public UserWorkflowDefinition publish(UserWorkflowDefinition published) {
        int rows = jdbc.update("""
                UPDATE user_workflow_definition_version SET
                status = 'PUBLISHED', published_at = ?, published_by = ?,
                updated_at = ?, updated_by = ?, optimistic_version = optimistic_version + 1
                WHERE definition_id = ? AND version_number = ?
                AND tenant_id = ? AND optimistic_version = ? AND status = 'VALIDATED'
                """, Timestamp.from(published.publishedAt()), published.publishedBy(),
                Timestamp.from(published.updatedAt()), published.updatedBy(),
                published.definitionId().value(), published.version().versionNumber(),
                published.tenantId(), published.optimisticVersion());
        if (rows == 0) {
            throw optimisticOrImmutable(published);
        }
        return withBumpedVersion(published);
    }

    @Override
    @Transactional
    public UserWorkflowDefinition archive(UserWorkflowDefinition archived) {
        int rows = jdbc.update("""
                UPDATE user_workflow_definition_version SET
                status = 'ARCHIVED', archived_at = ?, archived_by = ?,
                updated_at = ?, updated_by = ?, optimistic_version = optimistic_version + 1
                WHERE definition_id = ? AND version_number = ?
                AND tenant_id = ? AND optimistic_version = ?
                """, Timestamp.from(archived.archivedAt()), archived.archivedBy(),
                Timestamp.from(archived.updatedAt()), archived.updatedBy(),
                archived.definitionId().value(), archived.version().versionNumber(),
                archived.tenantId(), archived.optimisticVersion());
        if (rows == 0) {
            throw optimisticOrImmutable(archived);
        }
        return withBumpedVersion(archived);
    }

    @Override
    @Transactional
    public void insertVersion(UserWorkflowDefinition newDraft) {
        try {
            insertVersionRows(newDraft);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT,
                    "definition version already exists: " + newDraft.definitionId() + "/" + newDraft.version());
        }
    }

    // ── reads ──────────────────────────────────────────────────────────────

    @Override
    public Optional<UserWorkflowDefinition> findExactVersion(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version) {
        List<UserWorkflowDefinition> rows = jdbc.query("""
                SELECT * FROM user_workflow_definition_version
                WHERE definition_id = ? AND version_number = ? AND tenant_id = ?
                """, VERSION_ROW, definitionId.value(), version.versionNumber(), tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assemble(rows.get(0), tenantId, definitionId, version));
    }

    @Override
    public Optional<UserWorkflowDefinition> findLatest(String tenantId, UserWorkflowDefinitionId definitionId) {
        List<UserWorkflowDefinition> rows = jdbc.query("""
                SELECT * FROM user_workflow_definition_version
                WHERE definition_id = ? AND tenant_id = ?
                ORDER BY version_number DESC LIMIT 1
                """, VERSION_ROW, definitionId.value(), tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserWorkflowDefinition head = rows.get(0);
        return Optional.of(assemble(head, tenantId, definitionId, head.version()));
    }

    @Override
    public List<UserWorkflowDefinition> listByTenant(String tenantId, String projectId) {
        List<UserWorkflowDefinition> versions;
        if (projectId == null) {
            versions = jdbc.query("""
                    SELECT * FROM user_workflow_definition_version
                    WHERE tenant_id = ?
                    ORDER BY definition_id ASC, version_number DESC
                    """, VERSION_ROW, tenantId);
        } else {
            versions = jdbc.query("""
                    SELECT * FROM user_workflow_definition_version
                    WHERE tenant_id = ? AND project_id = ?
                    ORDER BY definition_id ASC, version_number DESC
                    """, VERSION_ROW, tenantId, projectId);
        }
        // latest version per lineage (first row per definition_id after DESC order)
        List<UserWorkflowDefinition> result = new ArrayList<>();
        String lastDefinition = null;
        for (UserWorkflowDefinition v : versions) {
            if (!v.definitionId().value().equals(lastDefinition)) {
                result.add(assemble(v, tenantId, v.definitionId(), v.version()));
                lastDefinition = v.definitionId().value();
            }
        }
        result.sort(Comparator.comparing(d -> d.definitionId().value()));
        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private final RowMapper<UserWorkflowDefinition> VERSION_ROW = (rs, rowNum) -> fromVersionRow(rs);

    private UserWorkflowDefinition assemble(
            UserWorkflowDefinition versionRow,
            String tenantId,
            UserWorkflowDefinitionId definitionId,
            UserWorkflowDefinitionVersion version) {
        List<UserWorkflowDefinitionNode> nodes = jdbc.query("""
                SELECT * FROM user_workflow_definition_node
                WHERE definition_id = ? AND version_number = ?
                ORDER BY sort_order ASC, node_id ASC
                """, NODE_ROW, definitionId.value(), version.versionNumber());
        List<UserWorkflowDefinitionEdge> edges = jdbc.query("""
                SELECT * FROM user_workflow_definition_edge
                WHERE definition_id = ? AND version_number = ?
                ORDER BY sort_order ASC, edge_id ASC
                """, EDGE_ROW, definitionId.value(), version.versionNumber());
        return withGraph(versionRow, nodes, edges);
    }

    private UserWorkflowDefinition fromVersionRow(ResultSet rs) throws SQLException {
        return new UserWorkflowDefinition(
                UserWorkflowDefinitionId.of(rs.getString("definition_id")),
                UserWorkflowDefinitionVersion.of(rs.getInt("version_number")),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                rs.getString("name"),
                rs.getString("description"),
                UserWorkflowDefinitionStatus.valueOf(rs.getString("status")),
                List.of(), List.of(),
                parseParameters(rs.getString("parameter_json")),
                parseTrigger(rs.getString("trigger_json")),
                rs.getInt("schema_version"),
                rs.getLong("optimistic_version"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getString("updated_by"),
                toInstant(rs.getTimestamp("published_at")),
                rs.getString("published_by"),
                toInstant(rs.getTimestamp("archived_at")),
                rs.getString("archived_by"));
    }

    private final RowMapper<UserWorkflowDefinitionNode> NODE_ROW = (rs, rowNum) -> new UserWorkflowDefinitionNode(
            rs.getString("node_id"),
            com.example.platform.workflow.definition.domain.WorkflowNodeType.valueOf(rs.getString("node_type")),
            rs.getString("name"),
            nodeSchemaRef(rs.getString("node_type")),
            new UserWorkflowDefinitionNode.VersionedJsonDocument(
                    SUPPORTED_SCHEMA_VERSION, rs.getString("config_json")),
            parseParameters(rs.getString("input_json")),
            parseParameters(rs.getString("output_json")),
            UserWorkflowDefinitionNode.ErrorPolicy.valueOf(rs.getString("error_policy")));

    private final RowMapper<UserWorkflowDefinitionEdge> EDGE_ROW = (rs, rowNum) -> new UserWorkflowDefinitionEdge(
            rs.getString("edge_id"),
            rs.getString("source_node_id"),
            rs.getString("target_node_id"),
            rs.getString("condition_ref"),
            rs.getInt("sort_order"));

    private void insertVersionRows(UserWorkflowDefinition d) {
        jdbc.update("""
                INSERT INTO user_workflow_definition_version
                (definition_id, version_number, tenant_id, project_id, name, description, status,
                 schema_version, optimistic_version, trigger_json, parameter_json,
                 created_at, created_by, updated_at, updated_by, published_at, published_by,
                 archived_at, archived_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, d.definitionId().value(), d.version().versionNumber(), d.tenantId(),
                d.projectId(), d.name(), d.description(), d.status().name(),
                d.schemaVersion(), d.optimisticVersion(), triggerJson(d.triggerBinding()),
                parametersJson(d.parameters()), Timestamp.from(d.createdAt()), d.createdBy(),
                Timestamp.from(d.updatedAt()), d.updatedBy(),
                d.publishedAt() == null ? null : Timestamp.from(d.publishedAt()), d.publishedBy(),
                d.archivedAt() == null ? null : Timestamp.from(d.archivedAt()), d.archivedBy());
        insertGraphRows(d);
    }

    private void replaceGraphRows(UserWorkflowDefinition d) {
        jdbc.update("DELETE FROM user_workflow_definition_node WHERE definition_id = ? AND version_number = ?",
                d.definitionId().value(), d.version().versionNumber());
        jdbc.update("DELETE FROM user_workflow_definition_edge WHERE definition_id = ? AND version_number = ?",
                d.definitionId().value(), d.version().versionNumber());
        insertGraphRows(d);
    }

    private void insertGraphRows(UserWorkflowDefinition d) {
        for (UserWorkflowDefinitionNode node : d.nodes()) {
            jdbc.update("""
                    INSERT INTO user_workflow_definition_node
                    (definition_id, version_number, node_id, tenant_id, node_type, name,
                     config_json, input_json, output_json, error_policy, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, d.definitionId().value(), d.version().versionNumber(), node.nodeId(),
                    d.tenantId(), node.nodeType().name(), node.name(),
                    node.configValues().canonicalJson(), parametersJson(node.inputDeclarations()),
                    parametersJson(node.outputDeclarations()), node.errorPolicy().name(),
                    d.nodes().indexOf(node));
        }
        for (UserWorkflowDefinitionEdge edge : d.edges()) {
            jdbc.update("""
                    INSERT INTO user_workflow_definition_edge
                    (definition_id, version_number, edge_id, tenant_id, source_node_id, target_node_id,
                     condition_ref, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, d.definitionId().value(), d.version().versionNumber(), edge.edgeId(),
                    d.tenantId(), edge.sourceNodeId(), edge.targetNodeId(),
                    edge.conditionRef() == null ? "" : edge.conditionRef(), edge.sortOrder());
        }
    }

    private UserWorkflowException optimisticOrImmutable(UserWorkflowDefinition expected) {
        Optional<UserWorkflowDefinition> current =
                findExactVersion(expected.tenantId(), expected.definitionId(), expected.version());
        if (current.isEmpty()) {
            return new UserWorkflowException(UserWorkflowErrorCode.Code.VERSION_NOT_FOUND,
                    "version not found: " + expected.definitionId() + "/" + expected.version());
        }
        UserWorkflowDefinition row = current.get();
        if (row.status() == UserWorkflowDefinitionStatus.PUBLISHED
                || row.status() == UserWorkflowDefinitionStatus.ARCHIVED) {
            return new UserWorkflowException(UserWorkflowErrorCode.Code.PUBLISHED_IMMUTABLE,
                    "published or archived versions are immutable");
        }
        return new UserWorkflowException(UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT,
                "expected optimisticVersion " + expected.optimisticVersion()
                        + " but row holds " + row.optimisticVersion());
    }

    private static UserWorkflowDefinition withBumpedVersion(UserWorkflowDefinition d) {
        return new UserWorkflowDefinition(d.definitionId(), d.version(), d.tenantId(), d.projectId(),
                d.name(), d.description(), d.status(), d.nodes(), d.edges(), d.parameters(),
                d.triggerBinding(), d.schemaVersion(), d.optimisticVersion() + 1,
                d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(),
                d.publishedAt(), d.publishedBy(), d.archivedAt(), d.archivedBy());
    }

    private static UserWorkflowDefinition withGraph(
            UserWorkflowDefinition header, List<UserWorkflowDefinitionNode> nodes,
            List<UserWorkflowDefinitionEdge> edges) {
        return new UserWorkflowDefinition(header.definitionId(), header.version(), header.tenantId(),
                header.projectId(), header.name(), header.description(), header.status(),
                nodes, edges, header.parameters(), header.triggerBinding(), header.schemaVersion(),
                header.optimisticVersion(), header.createdAt(), header.createdBy(),
                header.updatedAt(), header.updatedBy(), header.publishedAt(), header.publishedBy(),
                header.archivedAt(), header.archivedBy());
    }

    // ── canonical JSON helpers ─────────────────────────────────────────────

    private static String triggerJson(UserWorkflowTriggerBinding trigger) {
        ObjectNode o = CANONICAL.createObjectNode();
        o.put("triggerType", trigger.triggerType().name());
        o.put("referenceId", trigger.referenceId());
        o.put("referenceVersion", trigger.referenceVersion());
        return write(o);
    }

    private static UserWorkflowTriggerBinding parseTrigger(String json) {
        try {
            JsonNode tree = CANONICAL.readTree(json);
            UserWorkflowTriggerBinding.TriggerType type =
                    UserWorkflowTriggerBinding.TriggerType.valueOf(tree.path("triggerType").asText());
            return new UserWorkflowTriggerBinding(type,
                    tree.hasNonNull("referenceId") ? tree.get("referenceId").asText() : null,
                    tree.hasNonNull("referenceVersion") ? tree.get("referenceVersion").asText() : null);
        } catch (Exception e) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION,
                    "stored trigger json is not canonical: " + e.getMessage());
        }
    }

    private static String parametersJson(List<UserWorkflowParameterDeclaration> parameters) {
        ArrayNode a = CANONICAL.createArrayNode();
        for (UserWorkflowParameterDeclaration p : parameters) {
            ObjectNode o = CANONICAL.createObjectNode();
            o.put("parameterId", p.parameterId());
            o.put("name", p.name());
            o.put("type", p.type());
            o.put("schemaRef", p.schemaRef());
            o.put("required", p.required());
            o.put("defaultValueRef", p.defaultValueRef());
            a.add(o);
        }
        return write(a);
    }

    private static List<UserWorkflowParameterDeclaration> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<UserWorkflowParameterDeclaration> result = new ArrayList<>();
            for (JsonNode n : CANONICAL.readTree(json)) {
                result.add(new UserWorkflowParameterDeclaration(
                        n.path("parameterId").asText(), n.path("name").asText(),
                        n.path("type").asText(), n.path("schemaRef").asText(),
                        n.path("required").asBoolean(),
                        n.hasNonNull("defaultValueRef") ? n.get("defaultValueRef").asText() : null));
            }
            result.sort(Comparator.comparing(UserWorkflowParameterDeclaration::parameterId));
            return result;
        } catch (Exception e) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION,
                    "stored parameter json is not canonical: " + e.getMessage());
        }
    }

    private static String nodeSchemaRef(String nodeType) {
        return "w2/" + nodeType.toLowerCase() + "/config/v1";
    }

    private static String write(JsonNode node) {
        try {
            return CANONICAL.writeValueAsString(node);
        } catch (Exception e) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION,
                    "canonical serialization failed: " + e.getMessage());
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

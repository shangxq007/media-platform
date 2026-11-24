package com.example.platform.workflow.definition.infrastructure;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.domain.WorkflowNodeType;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UWD-RED-014 (JDBC_INTEGRATION). Authentic RED: fails at compile time until
 * the W2 JDBC adapter exists. Uses the canonical Flyway migrations against
 * PostgresTestContainerSupport; the real migration file is proven by
 * UWD-RED-015 in platform-app.
 */
class UserWorkflowDefinitionJdbcRepositoryTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");

    private static DataSource dataSource;
    private static UserWorkflowDefinitionJdbcRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        com.example.platform.shared.test.TestDatabaseSchema.migrateApplication();
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);
        repository = new UserWorkflowDefinitionJdbcRepository(jdbc);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void cleanTables() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("delete from workflow.user_workflow_definition_edge");
        jdbc.execute("delete from workflow.user_workflow_definition_node");
        jdbc.execute("delete from workflow.user_workflow_definition_version");
        jdbc.execute("delete from workflow.user_workflow_definition");
    }

    private static UserWorkflowDefinition draft(String tenantId, String projectId, String name) {
        return new UserWorkflowDefinition(
                UserWorkflowDefinitionId.generate(), UserWorkflowDefinitionVersion.of(1),
                tenantId, projectId, name, "desc",
                UserWorkflowDefinitionStatus.DRAFT,
                List.of(node("n0"), node("n1")),
                List.of(UserWorkflowDefinitionEdge.unconditional("e0", "n0", "n1", 0)),
                List.of(), UserWorkflowTriggerBinding.manual(),
                1, 1L, NOW, "u-1", NOW, "u-1", null, null, null, null);
    }

    private static UserWorkflowDefinitionNode node(String id) {
        return new UserWorkflowDefinitionNode(id, WorkflowNodeType.ACTION, "node-" + id,
                "w2/action/config/v1",
                new UserWorkflowDefinitionNode.VersionedJsonDocument(1,
                        "{\"capabilityKey\":\"render.render-job.create\",\"capabilityVersion\":\"1\"}"),
                List.of(), List.of(), UserWorkflowDefinitionNode.ErrorPolicy.FAIL);
    }

    @Test
    void definitionRoundtripPreservesAggregate() {
        UserWorkflowDefinition d = draft("tenant-a", null, "wf");
        repository.insertDraft(d);

        Optional<UserWorkflowDefinition> exact =
                repository.findExactVersion("tenant-a", d.definitionId(), d.version());
        assertTrue(exact.isPresent());
        UserWorkflowDefinition found = exact.get();
        assertEquals(d.definitionId(), found.definitionId());
        assertEquals(d.version(), found.version());
        assertEquals(d.tenantId(), found.tenantId());
        assertEquals(d.name(), found.name());
        assertEquals(d.status(), found.status());
        assertEquals(d.nodes().size(), found.nodes().size());
        assertEquals(d.edges().size(), found.edges().size());
        assertEquals(d.nodes().get(0).nodeId(), found.nodes().get(0).nodeId());
        assertEquals(d.nodes().get(0).configValues().canonicalJson(), found.nodes().get(0).configValues().canonicalJson());

        Optional<UserWorkflowDefinition> latest =
                repository.findLatest("tenant-a", d.definitionId());
        assertTrue(latest.isPresent());
        assertEquals(d.version(), latest.get().version());
    }

    @Test
    void tenantIsolation() {
        UserWorkflowDefinition d = draft("tenant-a", null, "wf");
        repository.insertDraft(d);
        assertTrue(repository.findExactVersion("tenant-b", d.definitionId(), d.version()).isEmpty());
        assertTrue(repository.findLatest("tenant-b", d.definitionId()).isEmpty());
        assertTrue(repository.listByTenant("tenant-b", null).isEmpty());
    }

    @Test
    void versionUniqueness() {
        UserWorkflowDefinition d = draft("tenant-a", null, "wf");
        repository.insertDraft(d);
        assertThrows(UserWorkflowException.class, () -> repository.insertDraft(d));
    }

    @Test
    void optimisticCasConflict() {
        UserWorkflowDefinition d = draft("tenant-a", null, "wf");
        repository.insertDraft(d);

        UserWorkflowDefinition stale = new UserWorkflowDefinition(d.definitionId(), d.version(),
                d.tenantId(), d.projectId(), "stale-name", d.description(), d.status(),
                d.nodes(), d.edges(), d.parameters(), d.triggerBinding(), d.schemaVersion(),
                99L, d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(),
                d.publishedAt(), d.publishedBy(), d.archivedAt(), d.archivedBy());
        assertThrows(UserWorkflowException.class, () -> repository.updateDraft(stale));
    }

    @Test
    void publishAndArchivePersistStatus() {
        UserWorkflowDefinition d = draft("tenant-a", null, "wf");
        repository.insertDraft(d);
        UserWorkflowDefinition validatedRow = repository.updateDraft(d.markValidated("u-1", NOW));
        UserWorkflowDefinition published = validatedRow.publish("u-1", NOW);
        repository.publish(published);

        Optional<UserWorkflowDefinition> found =
                repository.findExactVersion("tenant-a", d.definitionId(), d.version());
        assertTrue(found.isPresent());
        assertEquals(UserWorkflowDefinitionStatus.PUBLISHED, found.get().status());
        assertNotNull(found.get().publishedAt());

        UserWorkflowDefinition archived = found.get().archive("u-1", NOW);
        repository.archive(archived);
        Optional<UserWorkflowDefinition> archivedFound =
                repository.findExactVersion("tenant-a", d.definitionId(), d.version());
        assertEquals(UserWorkflowDefinitionStatus.ARCHIVED, archivedFound.get().status());
        assertNotNull(archivedFound.get().archivedAt());
    }

    @Test
    void insertVersionAndListByTenant() {
        UserWorkflowDefinition d = draft("tenant-a", null, "wf");
        repository.insertDraft(d);
        UserWorkflowDefinition validatedRow = repository.updateDraft(d.markValidated("u-1", NOW));
        UserWorkflowDefinition publishedRow = repository.publish(validatedRow.publish("u-1", NOW));
        UserWorkflowDefinition v2 = publishedRow.createNextVersion("u-1", NOW);
        repository.insertVersion(v2);

        // listByTenant returns the latest version per definition lineage
        assertEquals(1, repository.listByTenant("tenant-a", null).size());

        UserWorkflowDefinition p2 = draft("tenant-a", "project-x", "other");
        repository.insertDraft(p2);
        assertEquals(1, repository.listByTenant("tenant-a", "project-x").size());
        assertEquals(2, repository.listByTenant("tenant-a", null).size());
    }
}

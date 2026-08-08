package com.example.platform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Canonical V1 consolidated schema verification for User Workflow Definition V1
 * (USER_WORKFLOW_DEFINITION_V1_CONTRACT_V2).
 *
 * <p>Proves that the REAL canonical V1 migration file at
 * {@code src/main/resources/db/migration} (applied via Flyway, NOT manual script
 * execution) produces the complete platform schema including the four frozen W2
 * tables. This replaces the incremental V2+ migration tests (FORBIDDEN under
 * Contract V2) and guards the single-V1 greenfield policy (AR-W2-MIG-01..04).
 *
 * <p>Authentic: a clean PostgreSQL instance is migrated solely from canonical V1;
 * assertions query the live catalog via the JDBC {@link DatabaseMetaData} and
 * {@code information_schema}.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserWorkflowDefinitionV1SchemaMigrationTest {

    @Container
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("uwd_v1_schema")
            .withUsername("test")
            .withPassword("test");

    private static boolean deployed = false;

    @Test
    @Order(1)
    @DisplayName("V1: canonical V1 migrates a clean database using only the consolidated V1")
    void v1_deploysFromCleanDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load();
        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted >= 1, "At least one migration should execute");
        deployed = true;
    }

    @Test
    @Order(2)
    @DisplayName("V1: flyway_schema_history contains exactly one row, version 1, script V1__initial_schema.sql")
    void flywayHistoryExactlyOneV1() throws Exception {
        assertTrue(deployed);
        try (Connection conn = db.createConnection("")) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "select version, script from flyway_schema_history order by installed_rank")) {
                assertTrue(rs.next(), "flyway_schema_history must contain at least one row");
                assertEquals("1", rs.getString("version"), "active Flyway version must be 1");
                assertEquals("V1__initial_schema.sql", rs.getString("script"),
                        "script must be the canonical consolidated V1");
                assertFalse(rs.next(), "flyway_schema_history must contain exactly one row");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("V1: all four W2 tables exist")
    void allFourW2TablesExist() throws Exception {
        assertTrue(deployed);
        Set<String> tables = getTables(db);
        assertTrue(tables.contains("user_workflow_definition"), "user_workflow_definition required");
        assertTrue(tables.contains("user_workflow_definition_version"),
                "user_workflow_definition_version required");
        assertTrue(tables.contains("user_workflow_definition_node"), "user_workflow_definition_node required");
        assertTrue(tables.contains("user_workflow_definition_edge"), "user_workflow_definition_edge required");
    }

    @Test
    @Order(4)
    @DisplayName("V1: W2 columns, types and nullability match the frozen DDL")
    void w2ColumnsTypesNullability() throws Exception {
        assertTrue(deployed);

        // user_workflow_definition
        assertColumn("user_workflow_definition", "definition_id", "character varying", false);
        assertColumn("user_workflow_definition", "tenant_id", "character varying", false);
        assertColumn("user_workflow_definition", "project_id", "character varying", true);
        assertColumn("user_workflow_definition", "created_at", "timestamp without time zone", false);
        assertColumn("user_workflow_definition", "created_by", "character varying", false);

        // user_workflow_definition_version
        assertColumn("user_workflow_definition_version", "definition_id", "character varying", false);
        assertColumn("user_workflow_definition_version", "version_number", "integer", false);
        assertColumn("user_workflow_definition_version", "tenant_id", "character varying", false);
        assertColumn("user_workflow_definition_version", "project_id", "character varying", true);
        assertColumn("user_workflow_definition_version", "name", "character varying", false);
        assertColumn("user_workflow_definition_version", "description", "text", true);
        assertColumn("user_workflow_definition_version", "status", "character varying", false);
        assertColumn("user_workflow_definition_version", "schema_version", "integer", false);
        assertColumn("user_workflow_definition_version", "optimistic_version", "bigint", false);
        assertColumn("user_workflow_definition_version", "trigger_json", "text", false);
        assertColumn("user_workflow_definition_version", "parameter_json", "text", false);
        assertColumn("user_workflow_definition_version", "created_at", "timestamp without time zone",
                false);
        assertColumn("user_workflow_definition_version", "created_by", "character varying", false);
        assertColumn("user_workflow_definition_version", "updated_at", "timestamp without time zone",
                false);
        assertColumn("user_workflow_definition_version", "updated_by", "character varying", false);
        assertColumn("user_workflow_definition_version", "published_at", "timestamp without time zone",
                true);
        assertColumn("user_workflow_definition_version", "published_by", "character varying", true);
        assertColumn("user_workflow_definition_version", "archived_at", "timestamp without time zone", true);
        assertColumn("user_workflow_definition_version", "archived_by", "character varying", true);

        // user_workflow_definition_node
        assertColumn("user_workflow_definition_node", "definition_id", "character varying", false);
        assertColumn("user_workflow_definition_node", "version_number", "integer", false);
        assertColumn("user_workflow_definition_node", "node_id", "character varying", false);
        assertColumn("user_workflow_definition_node", "tenant_id", "character varying", false);
        assertColumn("user_workflow_definition_node", "node_type", "character varying", false);
        assertColumn("user_workflow_definition_node", "name", "character varying", false);
        assertColumn("user_workflow_definition_node", "config_json", "text", false);
        assertColumn("user_workflow_definition_node", "input_json", "text", true);
        assertColumn("user_workflow_definition_node", "output_json", "text", true);
        assertColumn("user_workflow_definition_node", "error_policy", "character varying", false);
        assertColumn("user_workflow_definition_node", "sort_order", "integer", false);

        // user_workflow_definition_edge
        assertColumn("user_workflow_definition_edge", "definition_id", "character varying", false);
        assertColumn("user_workflow_definition_edge", "version_number", "integer", false);
        assertColumn("user_workflow_definition_edge", "edge_id", "character varying", false);
        assertColumn("user_workflow_definition_edge", "tenant_id", "character varying", false);
        assertColumn("user_workflow_definition_edge", "source_node_id", "character varying", false);
        assertColumn("user_workflow_definition_edge", "target_node_id", "character varying", false);
        assertColumn("user_workflow_definition_edge", "condition_ref", "character varying", false);
        assertColumn("user_workflow_definition_edge", "sort_order", "integer", false);
    }

    @Test
    @Order(5)
    @DisplayName("V1: W2 constraints — PK per table; edge UNIQUE covers condition_ref")
    void w2Constraints() throws Exception {
        assertTrue(deployed);

        List<String> pkTables = new ArrayList<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getPrimaryKeys(null, "public", null);
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME").toLowerCase();
                if (table.startsWith("user_workflow_definition")) {
                    pkTables.add(table);
                }
            }
        }
        // All four W2 tables must expose a primary key (duplicates possible across
        // catalog calls; compare as a set).
        Set<String> pkSet = new TreeSet<>(pkTables);
        assertEquals(Set.of("user_workflow_definition", "user_workflow_definition_edge",
                "user_workflow_definition_node", "user_workflow_definition_version"), pkSet,
                "all four W2 tables must have primary keys");

        // Edge unique constraint must include condition_ref.
        try (Connection conn = db.createConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select indexdef from pg_indexes where tablename = 'user_workflow_definition_edge' "
                             + "and indexdef ilike '%unique%'")) {
            List<String> uniqueDefs = new ArrayList<>();
            while (rs.next()) uniqueDefs.add(rs.getString("indexdef"));
            assertTrue(uniqueDefs.stream().anyMatch(i -> i.contains("condition_ref")),
                    "edge unique constraint must cover condition_ref: " + uniqueDefs);
        }
    }

    @Test
    @Order(6)
    @DisplayName("V1: pre-W2 tables preserved (render_job, notification_preference, outbox_events)")
    void preW2TablesPreserved() throws Exception {
        assertTrue(deployed);
        Set<String> tables = getTables(db);
        assertTrue(tables.contains("render_job"), "pre-W2 render_job must remain");
        assertTrue(tables.contains("notification_preference"),
                "pre-W2 notification_preference must remain");
        assertTrue(tables.contains("outbox_events"), "pre-W2 outbox_events must remain");
    }

    // === Helper methods (same convention as SchemaEquivalenceVerificationTest) ===

    private Set<String> getTables(PostgreSQLContainer<?> db) throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getTables(null, "public", null, new String[]{"TABLE"});
            while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase());
        }
        return tables;
    }

    private void assertColumn(String table, String name, String type, boolean nullable) throws SQLException {
        Map<String, ColumnInfo> columns = new TreeMap<>();
        try (Connection conn = db.createConnection("")) {
            ResultSet rs = conn.getMetaData().getColumns(null, "public", table, null);
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME").toLowerCase();
                // DatabaseMetaData reports the base type name (e.g. "varchar"); map it to
                // the information_schema style ("character varying") the frozen DDL expects.
                String dataType = normalizeTypeName(rs.getString("TYPE_NAME").toLowerCase());
                String isNullable = rs.getString("IS_NULLABLE");
                columns.put(col, new ColumnInfo(col, dataType, isNullable));
            }
        }
        ColumnInfo info = columns.get(name);
        assertNotNull(info, "column " + name + " missing in " + table + ": " + columns.keySet());
        assertEquals(type, info.dataType, "column " + name + " type in " + table);
        assertEquals(nullable ? "YES" : "NO", info.nullable,
                "column " + name + " nullability in " + table);
    }

    /**
     * Maps JDBC {@link DatabaseMetaData} base type names to the
     * {@code information_schema.columns.data_type} vocabulary used by the frozen DDL
     * contract. Only the aliases actually produced by the PostgreSQL driver are mapped;
     * anything unknown is returned unchanged so a genuine mismatch still fails.
     */
    private static String normalizeTypeName(String jdbcType) {
        return switch (jdbcType) {
            case "varchar" -> "character varying";
            case "int4" -> "integer";
            case "int8" -> "bigint";
            case "timestamp" -> "timestamp without time zone";
            default -> jdbcType;
        };
    }

    private record ColumnInfo(String name, String dataType, String nullable) {}
}

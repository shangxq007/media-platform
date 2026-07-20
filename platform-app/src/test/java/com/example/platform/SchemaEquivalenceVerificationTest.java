package com.example.platform;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SchemaEquivalenceVerificationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("schema_equiv_test")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() {
        try (var conn = postgres.createConnection("");
             var stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA public CASCADE");
            stmt.execute("CREATE SCHEMA public");
        } catch (Exception e) { /* ignore */ }
    }

    private Flyway createFlyway() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load();
    }

    @Test
    @DisplayName("Greenfield baseline: exactly one V1 migration")
    void exactlyOneV1Migration() {
        Flyway flyway = createFlyway();
        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted >= 1, "At least one migration should execute");

        // Verify exactly one migration in history
        var info = flyway.info();
        assertEquals(1, info.all().length, "Exactly one migration expected");
        assertEquals("1", info.all()[0].getVersion().getVersion());
    }

    @Test
    @DisplayName("Single V1 produces all expected tables")
    void allExpectedTablesExist() {
        createFlyway().migrate();

        Set<String> tables = new HashSet<>();
        try (Connection conn = postgres.createConnection("")) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, "public", null, new String[]{"TABLE"});
            while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase());
        } catch (SQLException e) { fail("Failed: " + e.getMessage()); }

        List<String> expected = List.of("render_job", "outbox_events", "platform_job",
                "platform_task", "audit_records", "schedules", "config_item",
                "storage_object", "render_job_lifecycle_events",
                "ingest_preflight_safe_report_records");
        for (String t : expected) assertTrue(tables.contains(t), "Missing: " + t);
    }

    @Test
    @DisplayName("render_job has all columns including V4 additions")
    void renderJob_hasAllColumns() {
        createFlyway().migrate();

        Set<String> columns = new HashSet<>();
        try (Connection conn = postgres.createConnection("")) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, "public", "render_job", null);
            while (rs.next()) columns.add(rs.getString("COLUMN_NAME").toLowerCase());
        } catch (SQLException e) { fail("Failed: " + e.getMessage()); }

        List<String> expected = List.of("id", "project_id", "tenant_id",
                "timeline_snapshot_id", "profile", "status", "created_at",
                "selected_provider", "updated_at", "base_job_id", "trace_id");
        for (String c : expected) assertTrue(columns.contains(c), "Missing: " + c);
    }
}

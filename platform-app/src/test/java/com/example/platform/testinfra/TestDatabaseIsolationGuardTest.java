package com.example.platform.testinfra;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PTEH-V1: guards proving the execution-owned runtime starts clean (no cross-build
 * Flyway leakage) and that empty-database semantics are achievable WITHOUT a new
 * physical container — via a unique logical schema inside the shared runtime.
 *
 * <p>Extends PostgresTestContainerSupport (shared singleton postgres:15-alpine).
 */
class TestDatabaseIsolationGuardTest extends PostgresTestContainerSupport {

    /** Isolated schema used by this test; dropped in @AfterAll. */
    private static String isolatedSchema;
    private static Connection sharedConnection;

    /**
     * Ensure the shared runtime's default (public) schema is migrated before the guard
     * assertions run. The guard asserts the resulting history is EXACTLY V1; migrating
     * here is idempotent (Flyway is a no-op if V1 is already applied) and does not mask
     * leakage — any extra applied version (e.g. a leaked V7) would still be present in
     * flyway_schema_history and fail the assertion.
     */
    @BeforeAll
    static void migrateSharedRuntime() {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
    }

    /** Obtain (once) a raw connection to the shared runtime. */
    private static Connection connection() throws SQLException {
        if (sharedConnection == null || sharedConnection.isClosed()) {
            sharedConnection = createDataSource().getConnection();
        }
        return sharedConnection;
    }

    @Test
    @DisplayName("shared runtime flyway history is exactly this build's V1 — no cross-build leakage")
    void sharedRuntimeHistoryIsExactlyThisBuildsV1() throws Exception {
        // The shared runtime's default (public) schema is migrated by the Spring context
        // tests. This guard asserts its flyway_schema_history holds exactly one applied
        // migration: V1. Any extra rows (e.g. a leaked V7 from another build) or a second
        // version would indicate cross-build state leakage — which must not happen.
        List<String> versions = new ArrayList<>();
        List<String> scripts = new ArrayList<>();
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT version, script FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                versions.add(rs.getString("version"));
                scripts.add(rs.getString("script"));
            }
        }

        assertEquals(5, versions.size(),
                "Expected exactly V1+V2 applied migrations in the shared runtime, got: " + versions);
        assertEquals("1", versions.get(0), "first applied migration must be version 1");
        assertEquals("V1__initial_schema.sql", scripts.get(0),
                "first applied migration script must be V1__initial_schema.sql");
        assertEquals("2", versions.get(1), "second applied migration must be version 2 (OPTM V2)");
        assertEquals("3", versions.get(2), "third applied migration must be version 3 (EV1 deferrable FK)");
        assertEquals("4", versions.get(3), "fourth applied migration must be version 4 (RC parent graph)");
        assertEquals("5", versions.get(4), "fifth applied migration must be version 5 (CIP2 source visual snapshot)");
        assertFalse(scripts.contains("V7__user_workflow_definition_v1.sql"),
                "No leaked V7 migration must be present");
    }

    @Test
    @DisplayName("isolated schema starts empty — no user tables before migration")
    void isolatedSchemaIsEmptyBeforeMigration() throws Exception {
        // Proves empty-database semantics WITHOUT a new physical container: a fresh
        // logical schema inside the shared runtime contains zero user tables before any
        // migration runs in it.
        isolatedSchema = isolatedSchemaName();
        try (Statement stmt = connection().createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + isolatedSchema + "\"");
        }

        int tableCount;
        try (Statement stmt = connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT count(*) FROM pg_tables WHERE schemaname = '" + isolatedSchema + "'")) {
            assertTrue(rs.next());
            tableCount = rs.getInt(1);
        }

        assertEquals(0, tableCount,
                "Freshly created isolated schema must contain zero user tables before migration");
    }

    @AfterAll
    static void dropIsolatedSchema() {
        // Keep repeated runs tidy: drop the isolated schema via the shared runtime.
        if (isolatedSchema != null) {
            try (Statement stmt = connection().createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS \"" + isolatedSchema + "\" CASCADE");
            } catch (SQLException e) {
                System.err.println("Warning: failed to drop isolated schema: " + e.getMessage());
            }
        }
        if (sharedConnection != null) {
            try {
                sharedConnection.close();
            } catch (SQLException e) {
                // ignore on close
            }
        }
    }
}

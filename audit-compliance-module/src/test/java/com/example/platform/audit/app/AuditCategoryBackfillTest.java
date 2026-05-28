package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;

/**
 * Tests for the audit_records category backfill rules (V2 migration).
 *
 * <p>Uses an in-memory H2 database to verify that the SQL UPDATE rules
 * correctly backfill NULL categories based on action prefix and resource_type.
 */
class AuditCategoryBackfillTest {

    private DataSource dataSource;
    private DSLContext dsl;

    @BeforeEach
    void setUp() throws Exception {
        // Create in-memory H2 database
        var hikariConfig = new com.zaxxer.hikari.HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:h2:mem:audit_backfill_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");
        dataSource = new com.zaxxer.hikari.HikariDataSource(hikariConfig);

        // Create audit_records table
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE audit_records (
                    id VARCHAR(64) PRIMARY KEY,
                    actor_type VARCHAR(50) NOT NULL,
                    actor_id VARCHAR(100),
                    action VARCHAR(120) NOT NULL,
                    resource_type VARCHAR(120) NOT NULL,
                    resource_id VARCHAR(120),
                    payload TEXT,
                    category VARCHAR(50),
                    created_at TIMESTAMP NOT NULL
                )
                """);
        }

        dsl = org.jooq.impl.DSL.using(dataSource, org.jooq.SQLDialect.H2);
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            hikari.close();
        }
    }

    @Test
    void adminActionsBackfilledToAdminAudit() {
        insertRecord("aud-1", "SYSTEM", "ADMIN_LIST_TENANTS", "tenant", "t1", null);
        runBackfill();

        assertEquals("ADMIN_AUDIT", getCategory("aud-1"));
    }

    @Test
    void renderJobCreatedBackfilledToRender() {
        insertRecord("aud-2", "SYSTEM", "RENDER_JOB_CREATED", "RENDER_JOB", "j1", null);
        runBackfill();

        assertEquals("RENDER", getCategory("aud-2"));
    }

    @Test
    void problematicDataBackfilledToDataGovernance() {
        insertRecord("aud-3", "SYSTEM", "PROBLEMATIC_DATA_DETECTED", "problematic_data", "r1", null);
        runBackfill();

        assertEquals("DATA_GOVERNANCE", getCategory("aud-3"));
    }

    @Test
    void viewDashboardBackfilledToIdentity() {
        insertRecord("aud-4", "USER", "VIEW_DASHBOARD", "DASHBOARD", "d1", null);
        runBackfill();

        assertEquals("IDENTITY", getCategory("aud-4"));
    }

    @Test
    void submitFeedbackBackfilledToIdentity() {
        insertRecord("aud-5", "USER", "SUBMIT_FEEDBACK", "FEEDBACK", "f1", null);
        runBackfill();

        assertEquals("IDENTITY", getCategory("aud-5"));
    }

    @Test
    void existingCategoryNotOverwritten() {
        insertRecord("aud-7", "SYSTEM", "ADMIN_LIST_TENANTS", "tenant", "t1", "CONFIG");
        runBackfill();

        assertEquals("CONFIG", getCategory("aud-7"),
                "Existing non-null category should NOT be overwritten");
    }

    @Test
    void nonNullCategoryPreserved() {
        insertRecord("aud-8", "USER", "NLQ_PREVIEW", "NLQ_QUERY", null, "NLQ");
        runBackfill();

        assertEquals("NLQ", getCategory("aud-8"));
    }

    @Test
    void unclassifiableRecordsBackfilledToUnknown() {
        insertRecord("aud-9", "UNKNOWN", "UNKNOWN_ACTION", "UNKNOWN_TYPE", "x1", null);
        runBackfill();

        assertEquals("UNKNOWN", getCategory("aud-9"),
                "Records that cannot be reliably classified should be backfilled to UNKNOWN");
    }

    @Test
    void migrationIsIdempotent() {
        insertRecord("aud-10", "SYSTEM", "ADMIN_LIST_TENANTS", "tenant", "t1", null);
        runBackfill();
        assertEquals("ADMIN_AUDIT", getCategory("aud-10"));

        // Run again — should not change anything
        runBackfill();
        assertEquals("ADMIN_AUDIT", getCategory("aud-10"));
    }

    @Test
    void multipleRecordsBackfilledCorrectly() {
        insertRecord("aud-11", "SYSTEM", "ADMIN_LIST_TENANTS", "tenant", "t1", null);
        insertRecord("aud-12", "SYSTEM", "RENDER_JOB_CREATED", "RENDER_JOB", "j1", null);
        insertRecord("aud-13", "USER", "VIEW_DASHBOARD", "DASHBOARD", "d1", null);
        insertRecord("aud-14", "SYSTEM", "PROBLEMATIC_DATA_DETECTED", "problematic_data", "r1", null);
        runBackfill();

        assertEquals("ADMIN_AUDIT", getCategory("aud-11"));
        assertEquals("RENDER", getCategory("aud-12"));
        assertEquals("IDENTITY", getCategory("aud-13"));
        assertEquals("DATA_GOVERNANCE", getCategory("aud-14"));
    }

    @Test
    void permissionActionsBackfilledCorrectly() {
        insertRecord("aud-15", "SYSTEM", "ROLE_ASSIGN", "PERMISSION", "r1", null);
        insertRecord("aud-16", "SYSTEM", "ROLE_REVOKE", "PERMISSION", "r1", null);
        insertRecord("aud-17", "SYSTEM", "MEMBER_ADD", "PERMISSION", "m1", null);
        runBackfill();

        assertEquals("PERMISSION", getCategory("aud-15"));
        assertEquals("PERMISSION", getCategory("aud-16"));
        assertEquals("PERMISSION", getCategory("aud-17"));
    }

    @Test
    void configActionsBackfilledCorrectly() {
        insertRecord("aud-18", "SYSTEM", "WORKSPACE_CREATE", "CONFIG", "w1", null);
        insertRecord("aud-19", "SYSTEM", "GROUP_CREATE", "CONFIG", "g1", null);
        runBackfill();

        assertEquals("CONFIG", getCategory("aud-18"));
        assertEquals("CONFIG", getCategory("aud-19"));
    }

    @Test
    void extensionActionsBackfilledCorrectly() {
        insertRecord("aud-20", "system", "ROUTING_RULE_CREATED", "EXTENSION_ROUTING", "r1", null);
        insertRecord("aud-21", "system", "RESOURCE_LIMIT_EXCEEDED", "EXTENSION_RESOURCE", "r1", null);
        runBackfill();

        assertEquals("EXTENSION_ROUTING", getCategory("aud-20"));
        assertEquals("EXTENSION_RESOURCE", getCategory("aud-21"));
    }

    @Test
    void renderArtifactCreatedBackfilledToRender() {
        insertRecord("aud-22", "SYSTEM", "ARTIFACT_CREATED", "ARTIFACT", "a1", null);
        runBackfill();

        assertEquals("RENDER", getCategory("aud-22"));
    }

    @Test
    void graphqlExecuteWithCorrectResourceTypeBackfilled() {
        // GraphQLAuditInterceptor records: action='EXECUTE', resource_type='graphql_operation'
        insertRecord("aud-gql-1", "GRAPHQL", "EXECUTE", "graphql_operation", "op-1", null);
        runBackfill();

        assertEquals("GRAPHQL_OPERATION", getCategory("aud-gql-1"),
                "action=EXECUTE + resource_type=graphql_operation should be classified as GRAPHQL_OPERATION");
    }

    @Test
    void graphqlExecuteWithUppercaseResourceTypeBackfilled() {
        insertRecord("aud-gql-2", "GRAPHQL", "EXECUTE", "GRAPHQL_OPERATION", "op-2", null);
        runBackfill();

        assertEquals("GRAPHQL_OPERATION", getCategory("aud-gql-2"));
    }

    @Test
    void graphqlActionContainingGraphqlBackfilled() {
        insertRecord("aud-gql-3", "GRAPHQL", "GRAPHQL_QUERY", "graphql_operation", "op-3", null);
        runBackfill();

        assertEquals("GRAPHQL_OPERATION", getCategory("aud-gql-3"),
                "action containing 'GRAPHQL' should be classified");
    }

    @Test
    void graphqlResourceTypeContainingGraphqlBackfilled() {
        insertRecord("aud-gql-4", "USER", "QUERY", "graphql_operation", "q-1", null);
        runBackfill();

        assertEquals("GRAPHQL_OPERATION", getCategory("aud-gql-4"),
                "resource_type containing 'graphql' should be classified");
    }

    @Test
    void nonGraphqlExecuteBackfilledToUnknown() {
        // action=EXECUTE but resource_type is NOT graphql-related
        insertRecord("aud-gql-5", "SYSTEM", "EXECUTE", "WORKFLOW", "wf-1", null);
        runBackfill();

        assertEquals("UNKNOWN", getCategory("aud-gql-5"),
                "action=EXECUTE with non-graphql resource_type should be backfilled to UNKNOWN");
    }

    @Test
    void graphqlExistingCategoryNotOverwritten() {
        insertRecord("aud-gql-6", "GRAPHQL", "EXECUTE", "graphql_operation", "op-6", "SECURITY");
        runBackfill();

        assertEquals("SECURITY", getCategory("aud-gql-6"),
                "Existing non-null category should NOT be overwritten to GRAPHQL_OPERATION");
    }

    @Test
    void graphqlNonGraphqlExecuteBackfilledToUnknown() {
        insertRecord("aud-gql-7", "GRAPHQL", "EXECUTE", "graphql_operation", "op-7", null);
        insertRecord("aud-gql-8", "SYSTEM", "EXECUTE", "WORKFLOW", "wf-2", null);
        runBackfill();

        assertEquals("GRAPHQL_OPERATION", getCategory("aud-gql-7"));
        assertEquals("UNKNOWN", getCategory("aud-gql-8"),
                "Non-graphql EXECUTE should be backfilled to UNKNOWN");
    }

    @Test
    void noNullCategoriesAfterBackfill() {
        insertRecord("aud-23", "SYSTEM", "ADMIN_LIST_TENANTS", "tenant", "t1", null);
        insertRecord("aud-24", "SYSTEM", "RENDER_JOB_CREATED", "RENDER_JOB", "j1", null);
        insertRecord("aud-25", "UNKNOWN", "UNKNOWN_ACTION", "UNKNOWN_TYPE", "x1", null);
        runBackfill();

        long nullCount = dsl.fetchCount(
                DSL.table("audit_records"),
                DSL.field("category").isNull());

        // V3 migration converts all remaining NULL to UNKNOWN
        assertEquals(0, nullCount,
                "No records should have NULL category after backfill");
    }

    // ==================== Helpers ====================

    private void insertRecord(String id, String actorType, String action,
                               String resourceType, String resourceId, String category) {
        dsl.insertInto(DSL.table("audit_records"))
                .columns(
                        DSL.field("id"),
                        DSL.field("actor_type"),
                        DSL.field("actor_id"),
                        DSL.field("action"),
                        DSL.field("resource_type"),
                        DSL.field("resource_id"),
                        DSL.field("payload"),
                        DSL.field("category"),
                        DSL.field("created_at")
                )
                .values(
                        id, actorType, "user-1", action, resourceType, resourceId,
                        "{}", category, OffsetDateTime.now()
                )
                .execute();
    }

    private String getCategory(String id) {
        return dsl.select(DSL.field("category"))
                .from(DSL.table("audit_records"))
                .where(DSL.field("id").eq(id))
                .fetchOne(0, String.class);
    }

    /**
     * Execute the backfill SQL rules (same as V2 migration).
     * Idempotent: safe to run multiple times.
     */
    private void runBackfill() {
        String[] rules = {
                "UPDATE audit_records SET category = 'ADMIN_AUDIT' WHERE category IS NULL AND action LIKE 'ADMIN_%'",
                "UPDATE audit_records SET category = 'RENDER' WHERE category IS NULL AND (action LIKE 'RENDER_%' OR action = 'ARTIFACT_CREATED' OR action = 'ARTIFACT_TOMBSTONED' OR resource_type = 'RENDER_JOB')",
                "UPDATE audit_records SET category = 'DATA_GOVERNANCE' WHERE category IS NULL AND action LIKE 'PROBLEMATIC_DATA_%'",
                "UPDATE audit_records SET category = 'FEATURE_FLAG' WHERE category IS NULL AND (action LIKE 'FEATURE_FLAG_%' OR resource_type = 'FEATURE_FLAG')",
                "UPDATE audit_records SET category = 'ENTITLEMENT' WHERE category IS NULL AND (action LIKE 'ENTITLEMENT_%' OR resource_type = 'ENTITLEMENT')",
                "UPDATE audit_records SET category = 'GRAPHQL_OPERATION' WHERE category IS NULL AND (action LIKE '%GRAPHQL%' OR resource_type LIKE '%graphql%' OR resource_type LIKE '%GRAPHQL%' OR (action = 'EXECUTE' AND resource_type IN ('graphql_operation', 'GRAPHQL_OPERATION', 'GRAPHQL')))",
                "UPDATE audit_records SET category = 'NLQ' WHERE category IS NULL AND (action LIKE 'NLQ_%' OR resource_type IN ('NLQ_QUERY', 'NLQ_REPORT'))",
                "UPDATE audit_records SET category = 'PROVIDER_HEALTH' WHERE category IS NULL AND (action LIKE 'PROVIDER_%' OR resource_type = 'PROVIDER_HEALTH')",
                "UPDATE audit_records SET category = 'IDENTITY' WHERE category IS NULL AND (action IN ('VIEW_DASHBOARD', 'SUBMIT_FEEDBACK') OR resource_type IN ('DASHBOARD', 'FEEDBACK'))",
                "UPDATE audit_records SET category = 'API_REQUEST' WHERE category IS NULL AND (action LIKE 'API_%' OR action = 'REQUEST_RECEIVED' OR resource_type = 'API_REQUEST')",
                "UPDATE audit_records SET category = 'EXTENSION' WHERE category IS NULL AND action LIKE 'EXTENSION_%'",
                "UPDATE audit_records SET category = 'EXTENSION_ROUTING' WHERE category IS NULL AND action LIKE 'ROUTING_RULE_%'",
                "UPDATE audit_records SET category = 'EXTENSION_RESOURCE' WHERE category IS NULL AND (action LIKE 'RESOURCE_LIMIT_%' OR action LIKE 'ROLLBACK_POINT_%')",
                "UPDATE audit_records SET category = 'PERMISSION' WHERE category IS NULL AND action IN ('ROLE_ASSIGN', 'ROLE_REVOKE', 'MEMBER_ADD', 'MEMBER_REMOVE')",
                "UPDATE audit_records SET category = 'CONFIG' WHERE category IS NULL AND action IN ('WORKSPACE_CREATE', 'WORKSPACE_UPDATE', 'GROUP_CREATE')",
                "UPDATE audit_records SET category = 'PROMPT' WHERE category IS NULL AND resource_type = 'PROMPT'",
                "UPDATE audit_records SET category = 'POLICY' WHERE category IS NULL AND resource_type = 'POLICY'",
                "UPDATE audit_records SET category = 'SANDBOX' WHERE category IS NULL AND action LIKE 'SANDBOX_%'",
        };
        for (String sql : rules) {
            dsl.execute(sql);
        }

        // V3: Backfill remaining NULL to UNKNOWN (enforced by NOT NULL constraint)
        dsl.execute("UPDATE audit_records SET category = 'UNKNOWN' WHERE category IS NULL");

        // V3: Add CHECK constraint (PostgreSQL/H2 compatible)
        try {
            dsl.execute("""
                ALTER TABLE audit_records
                ADD CONSTRAINT chk_audit_records_category
                CHECK (category IN (
                    'CONFIG', 'PROMPT', 'POLICY', 'PLUGIN', 'MANUAL_RETRY',
                    'PERMISSION', 'EXTENSION', 'EXTENSION_ROUTING', 'EXTENSION_RESOURCE',
                    'SANDBOX', 'ENTITLEMENT', 'GRAPHQL_OPERATION', 'PROVIDER_HEALTH',
                    'API_REQUEST', 'FEATURE_FLAG', 'NLQ', 'GENERAL', 'RENDER',
                    'DATA_GOVERNANCE', 'IDENTITY', 'ADMIN_AUDIT', 'UNKNOWN'
                ))
                """);
        } catch (Exception e) {
            // Constraint may already exist from previous run — idempotent
        }

        // V3: Add NOT NULL constraint
        try {
            dsl.execute("ALTER TABLE audit_records ALTER COLUMN category SET NOT NULL");
        } catch (Exception e) {
            // Column may already be NOT NULL — idempotent
        }
    }
}

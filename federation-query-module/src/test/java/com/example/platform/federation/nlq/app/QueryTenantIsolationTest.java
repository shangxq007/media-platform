package com.example.platform.federation.nlq.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryTenantIsolationTest {

    private SqlScopeInjector scopeInjector;

    @BeforeEach
    void setUp() {
        scopeInjector = new SqlScopeInjector();
    }

    @Test
    void injectsTenantScopeWhenMissing() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", null, "user-1", false, false);

        assertTrue(scoped.contains("tenant_id = :tenant_id"));
    }

    @Test
    void injectsWorkspaceScopeWhenMissing() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", "ws-1", "user-1", false, false);

        assertTrue(scoped.contains("workspace_id = :workspace_id"));
    }

    @Test
    void injectsUserScopeForNonAdmin() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", "ws-1", "user-1", false, false);

        assertTrue(scoped.contains("created_by = :user_id"));
    }

    @Test
    void doesNotInjectUserScopeForAdmin() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", "ws-1", "user-1", true, false);

        assertFalse(scoped.contains("created_by = :user_id"));
    }

    @Test
    void doesNotInjectScopeForAdminWithGlobalPermission() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", "ws-1", "user-1", true, true);

        assertEquals(sql, scoped);
    }

    @Test
    void doesNotDuplicateExistingTenantScope() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE tenant_id = 'x' LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", null, "user-1", false, false);

        assertTrue(scoped.contains("WHERE tenant_id = 'x'"));
        assertFalse(scoped.contains("AND tenant_id = :tenant_id"));
    }

    @Test
    void appendsAndWhenWhereExists() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE status = 'active' LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", null, "user-1", false, false);

        assertTrue(scoped.contains("AND tenant_id = :tenant_id"));
    }

    @Test
    void appendsWhereWhenNoWhere() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", null, "user-1", false, false);

        assertTrue(scoped.contains("WHERE"));
        assertTrue(scoped.contains("tenant_id = :tenant_id"));
    }

    @Test
    void buildsScopeParametersForNonAdmin() {
        var params = scopeInjector.buildScopeParameters("tenant-1", "ws-1", "user-1", false, false);

        assertEquals("tenant-1", params.get("tenant_id"));
        assertEquals("ws-1", params.get("workspace_id"));
        assertEquals("user-1", params.get("user_id"));
    }

    @Test
    void buildsScopeParametersWithoutUserForAdmin() {
        var params = scopeInjector.buildScopeParameters("tenant-1", "ws-1", "user-1", true, false);

        assertEquals("tenant-1", params.get("tenant_id"));
        assertEquals("ws-1", params.get("workspace_id"));
        assertFalse(params.containsKey("user_id"));
    }

    @Test
    void buildsEmptyScopeParametersForAdminWithGlobal() {
        var params = scopeInjector.buildScopeParameters("tenant-1", "ws-1", "user-1", true, true);

        assertTrue(params.isEmpty());
    }

    @Test
    void handlesNullTenantId() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, null, "ws-1", "user-1", false, false);

        assertFalse(scoped.contains("tenant_id"));
        assertTrue(scoped.contains("workspace_id"));
    }

    @Test
    void handlesNullWorkspaceId() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String scoped = scopeInjector.injectScope(sql, "tenant-1", null, "user-1", false, false);

        assertTrue(scoped.contains("tenant_id"));
        assertFalse(scoped.contains("workspace_id"));
    }
}

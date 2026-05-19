package com.example.platform.federation.nlq.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlScopeInjectorTest {

    private SqlScopeInjector injector;

    @BeforeEach
    void setUp() {
        injector = new SqlScopeInjector();
    }

    @Test
    void adminWithGlobalQuerySkipsInjection() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "t1", "w1", "u1", true, true);
        assertEquals(sql, result);
    }

    @Test
    void injectsTenantIdForNonAdmin() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", "ws_1", "user_1", false, false);
        assertTrue(result.contains("tenant_id = :tenant_id"));
    }

    @Test
    void injectsWorkspaceIdForNonAdmin() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", "ws_1", "user_1", false, false);
        assertTrue(result.contains("workspace_id = :workspace_id"));
    }

    @Test
    void injectsUserIdForNonAdmin() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", "ws_1", "user_1", false, false);
        assertTrue(result.contains("created_by = :user_id"));
    }

    @Test
    void usesAndWhenWhereExists() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE status = 'completed' LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", null, null, false, false);
        assertTrue(result.contains("AND tenant_id = :tenant_id"));
    }

    @Test
    void usesWhereWhenNoWhere() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", null, null, false, false);
        assertTrue(result.contains("WHERE tenant_id = :tenant_id"));
    }

    @Test
    void adminWithoutGlobalQueryStillInjectsScope() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", "ws_1", "user_1", true, false);
        assertTrue(result.contains("tenant_id = :tenant_id"));
        assertTrue(result.contains("workspace_id = :workspace_id"));
    }

    @Test
    void doesNotInjectUserIdForAdmin() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", "ws_1", "user_1", true, false);
        assertFalse(result.contains("created_by = :user_id"));
    }

    @Test
    void nullTenantIdSkipsTenantInjection() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, null, "ws_1", "user_1", false, false);
        assertFalse(result.contains("tenant_id"));
        assertTrue(result.contains("workspace_id = :workspace_id"));
    }

    @Test
    void nullWorkspaceIdSkipsWorkspaceInjection() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", null, "user_1", false, false);
        assertTrue(result.contains("tenant_id = :tenant_id"));
        assertFalse(result.contains("workspace_id"));
    }

    @Test
    void nullUserIdSkipsUserInjection() {
        String sql = "SELECT job_id FROM render_jobs_report_view LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", "ws_1", null, false, false);
        assertFalse(result.contains("created_by"));
    }

    @Test
    void buildScopeParametersForNonAdmin() {
        Map<String, Object> params = injector.buildScopeParameters("t1", "w1", "u1", false, false);
        assertEquals("t1", params.get("tenant_id"));
        assertEquals("w1", params.get("workspace_id"));
        assertEquals("u1", params.get("user_id"));
    }

    @Test
    void buildScopeParametersForAdminWithGlobalQuery() {
        Map<String, Object> params = injector.buildScopeParameters("t1", "w1", "u1", true, true);
        assertTrue(params.isEmpty());
    }

    @Test
    void buildScopeParametersForAdminWithoutGlobalQuery() {
        Map<String, Object> params = injector.buildScopeParameters("t1", "w1", "u1", true, false);
        assertEquals("t1", params.get("tenant_id"));
        assertEquals("w1", params.get("workspace_id"));
        assertFalse(params.containsKey("user_id"));
    }

    @Test
    void buildScopeParametersWithNullValues() {
        Map<String, Object> params = injector.buildScopeParameters(null, null, null, false, false);
        assertTrue(params.isEmpty());
    }

    @Test
    void doesNotDuplicateExistingTenantCondition() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE tenant_id = 'existing' LIMIT 100";
        String result = injector.injectScope(sql, "tenant_123", null, null, false, false);
        assertFalse(result.contains("tenant_id = :tenant_id"));
    }

    @Test
    void doesNotDuplicateExistingWorkspaceCondition() {
        String sql = "SELECT job_id FROM render_jobs_report_view WHERE workspace_id = 'existing' LIMIT 100";
        String result = injector.injectScope(sql, null, "ws_1", null, false, false);
        assertFalse(result.contains("workspace_id = :workspace_id"));
    }
}

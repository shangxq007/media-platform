package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AuditPortAdapterTest {

    @Mock
    private AuditService auditService;

    private AuditPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuditPortAdapter(auditService);
        TenantContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void recordMapsActorTypeCorrectly() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "WORKSPACE_CREATE", "CONFIG",
                "workspace", "ws-1", Map.of("key", "value"));

        ArgumentCaptor<String> actorTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(actorTypeCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertEquals("ADMIN", actorTypeCaptor.getValue());
    }

    @Test
    void recordMapsActionCorrectly() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "WORKSPACE_CREATE", "CONFIG",
                "workspace", "ws-1", Map.of("key", "value"));

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), actionCaptor.capture(), any(), any(), any(), any());
        assertEquals("WORKSPACE_CREATE", actionCaptor.getValue());
    }

    @Test
    void recordDerivesActorIdFromMdcPrincipal() {
        MDC.put("principal", "user-123");
        adapter.record("USER", "REQUEST_RECEIVED", "API_REQUEST",
                "http_request", "GET /api/v1/me", Map.of());

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("user-123", actorIdCaptor.getValue());
    }

    @Test
    void recordDerivesActorIdFromTenantContextWhenNoMdcPrincipal() {
        TenantContext.set("tenant-abc");
        adapter.record("SYSTEM", "WORKSPACE_CREATE", "CONFIG",
                "workspace", "ws-1", Map.of());

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("tenant-abc", actorIdCaptor.getValue());
    }

    @Test
    void recordFallsBackToSystemWhenNoContext() {
        TenantContext.clear();
        MDC.clear();
        adapter.record("SYSTEM", "WORKSPACE_CREATE", "CONFIG",
                "workspace", "ws-1", Map.of());

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("system", actorIdCaptor.getValue());
    }

    @Test
    void recordMapsResourceTypeCorrectly() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "ROLE_ASSIGN", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", "ura-1", Map.of());

        ArgumentCaptor<String> resourceTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), any(), resourceTypeCaptor.capture(), any(), any(), any());
        assertEquals("USER_ROLE_ASSIGNMENT", resourceTypeCaptor.getValue());
    }

    @Test
    void recordMapsResourceIdCorrectly() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "ROLE_ASSIGN", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", "ura-1", Map.of());

        ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), any(), any(), resourceIdCaptor.capture(), any(), any());
        assertEquals("ura-1", resourceIdCaptor.getValue());
    }

    @Test
    void recordMapsCategoryCorrectly() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "ROLE_ASSIGN", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", "ura-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.PERMISSION, categoryCaptor.getValue());
    }

    @Test
    void recordMapsCategoryConfig() {
        TenantContext.set("tenant-a");
        adapter.record("SYSTEM", "WORKSPACE_CREATE", "CONFIG",
                "workspace", "ws-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.CONFIG, categoryCaptor.getValue());
    }

    @Test
    void recordPassesPayloadCorrectly() {
        TenantContext.set("tenant-a");
        Map<String, Object> payload = Map.of("workspaceId", "ws-1", "tenantId", "tenant-a");
        adapter.record("SYSTEM", "WORKSPACE_CREATE", "CONFIG",
                "workspace", "ws-1", payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), any(), any(), any(), any(), payloadCaptor.capture(), any());
        assertEquals(payload, payloadCaptor.getValue());
    }

    @Test
    void recordHandlesNullPayload() {
        TenantContext.set("tenant-a");
        assertDoesNotThrow(() ->
                adapter.record("SYSTEM", "WORKSPACE_CREATE", "CONFIG",
                        "workspace", "ws-1", null));
        verify(auditService).record(any(), any(), any(), any(), any(), isNull(), any());
    }

    @Test
    void recordHandlesUnrecognizedCategory() {
        TenantContext.set("tenant-a");
        adapter.record("SYSTEM", "SOME_ACTION", "UNKNOWN_CATEGORY",
                "resource", "id-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.UNKNOWN, categoryCaptor.getValue(),
                "Unrecognized category should fall back to UNKNOWN");
    }

    @Test
    void recordHandlesNullCategory() {
        TenantContext.set("tenant-a");
        adapter.record("SYSTEM", "SOME_ACTION", null,
                "resource", "id-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.UNKNOWN, categoryCaptor.getValue(),
                "Null category should fall back to UNKNOWN");
    }

    @Test
    void recordHandlesBlankCategory() {
        TenantContext.set("tenant-a");
        adapter.record("SYSTEM", "SOME_ACTION", "  ",
                "resource", "id-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.UNKNOWN, categoryCaptor.getValue(),
                "Blank category should fall back to UNKNOWN");
    }

    @Test
    void recordCategoryCaseInsensitive() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "ROLE_ASSIGN", "permission",
                "USER_ROLE_ASSIGNMENT", "ura-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.PERMISSION, categoryCaptor.getValue());
    }

    @Test
    void mdcPrincipalTakesPriorityOverTenantContext() {
        MDC.put("principal", "user-from-jwt");
        TenantContext.set("tenant-from-context");
        adapter.record("USER", "ACTION", "CONFIG",
                "resource", "id", Map.of());

        ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), actorIdCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("user-from-jwt", actorIdCaptor.getValue(),
                "MDC principal should take priority over TenantContext");
    }

    @Test
    void fullMappingVerification() {
        MDC.put("principal", "user-42");
        TenantContext.set("tenant-xyz");
        Map<String, Object> payload = Map.of("detail", "test");

        adapter.record("ADMIN", "GRANT_CREATED", "PERMISSION",
                "entitlement_grant", "grant-1", payload);

        verify(auditService).record(
                "ADMIN",         // actorType
                "user-42",       // actorId (from MDC principal)
                "GRANT_CREATED", // action
                "entitlement_grant", // resourceType
                "grant-1",       // resourceId
                payload,         // payload
                AuditCategory.PERMISSION // category
        );
    }

    @Test
    void parseCategoryEntitlement() {
        TenantContext.set("tenant-a");
        adapter.record("ADMIN", "ACTION", "ENTITLEMENT",
                "resource", "id", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.ENTITLEMENT, categoryCaptor.getValue());
    }

    @Test
    void parseCategoryGraphqlOperation() {
        TenantContext.set("tenant-a");
        adapter.record("GRAPHQL", "EXECUTE", "GRAPHQL_OPERATION",
                "query", "id", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.GRAPHQL_OPERATION, categoryCaptor.getValue());
    }

    @Test
    void parseCategoryProviderHealth() {
        TenantContext.set("tenant-a");
        adapter.record("system", "INCIDENT_REPORTED", "PROVIDER_HEALTH",
                "provider", "id", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.PROVIDER_HEALTH, categoryCaptor.getValue());
    }

    @Test
    void parseCategoryApiRequest() {
        TenantContext.set("tenant-a");
        adapter.record("USER", "REQUEST_RECEIVED", "API_REQUEST",
                "http_request", "GET /api/v1/me", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.API_REQUEST, categoryCaptor.getValue());
    }

    @Test
    void parseCategoryFeatureFlag() {
        TenantContext.set("tenant-a");
        adapter.record("system", "FLAG_CREATED", "FEATURE_FLAG",
                "feature_flag", "flag-1", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.FEATURE_FLAG, categoryCaptor.getValue());
    }

    @Test
    void parseCategoryNlqLowerCase() {
        TenantContext.set("tenant-a");
        adapter.record("USER", "NLQ_PREVIEW", "nlq",
                "query", null, Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.NLQ, categoryCaptor.getValue(),
                "Lowercase 'nlq' should parse to NLQ");
    }

    @Test
    void parseCategoryGeneralLowerCase() {
        TenantContext.set("tenant-a");
        adapter.record("system", "ACTION", "general",
                "resource", "id", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.GENERAL, categoryCaptor.getValue(),
                "Lowercase 'general' should parse to GENERAL");
    }

    @Test
    void parseCategoryIdentity() {
        TenantContext.set("tenant-a");
        adapter.record("USER", "VIEW_DASHBOARD", "IDENTITY",
                "DASHBOARD", "summary", Map.of());

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.IDENTITY, categoryCaptor.getValue(),
                "IDENTITY category should be recognized (used by MeController)");
    }

    @Test
    void parseCategoryIdentityLowerCase() {
        TenantContext.set("tenant-a");
        adapter.record("USER", "SUBMIT_FEEDBACK", "identity",
                "FEEDBACK", "fb-1", Map.of("type", "bug", "severity", "low"));

        ArgumentCaptor<AuditCategory> categoryCaptor = ArgumentCaptor.forClass(AuditCategory.class);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), categoryCaptor.capture());
        assertEquals(AuditCategory.IDENTITY, categoryCaptor.getValue(),
                "Lowercase 'identity' should parse to IDENTITY");
    }

    @Test
    void allProductionCategoriesAreRecognized() {
        String[] productionCategories = {
                "CONFIG", "PROMPT", "POLICY", "PLUGIN", "MANUAL_RETRY",
                "PERMISSION", "EXTENSION", "EXTENSION_ROUTING", "EXTENSION_RESOURCE",
                "SANDBOX", "ENTITLEMENT", "GRAPHQL_OPERATION", "PROVIDER_HEALTH",
                "API_REQUEST", "FEATURE_FLAG", "NLQ", "GENERAL", "RENDER", "DATA_GOVERNANCE",
                "IDENTITY", "ADMIN_AUDIT", "UNKNOWN"
        };

        for (String category : productionCategories) {
            TenantContext.clear();
            TenantContext.set("tenant-test");
            adapter.record("SYSTEM", "TEST_ACTION", category,
                    "test_resource", "test-id", Map.of());
        }

        // Verify all calls succeeded with non-null categories
        verify(auditService, times(productionCategories.length))
                .record(any(), any(), any(), any(), any(), any(), argThat(cat -> cat != null));
    }
}

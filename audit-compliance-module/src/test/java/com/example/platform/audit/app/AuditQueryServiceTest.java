package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.audit.api.dto.AuditRecordPage;
import com.example.platform.audit.api.dto.AuditRecordQuery;
import com.example.platform.audit.api.dto.AuditRecordSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock
    private AuditService auditService;

    private AuditQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AuditQueryService(auditService);
    }

    @Test
    void queryByCategory_returnsFilteredRecords() {
        List<Map<String, Object>> mockRecords = List.of(
                createMockRecord("aud-1", "ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "SUCCESS"),
                createMockRecord("aud-2", "ADMIN_AUDIT", "ADMIN_DELETE_WORKSPACE", "DENIED"));

        when(auditService.findByCategory(AuditCategory.ADMIN_AUDIT, 1000)).thenReturn(mockRecords);

        AuditRecordQuery query = new AuditRecordQuery(0, 50, "ADMIN_AUDIT", null, null, null, null, null, null, null, null, null);
        AuditRecordPage result = queryService.query(query);

        assertNotNull(result);
        assertEquals(2, result.items().size());
        assertEquals("ADMIN_AUDIT", result.items().get(0).category());
        assertEquals("aud-1", result.items().get(0).id());
    }

    @Test
    void queryByAction_filtersCorrectly() {
        List<Map<String, Object>> mockRecords = List.of(
                createMockRecord("aud-1", "ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "SUCCESS"),
                createMockRecord("aud-2", "DATA_GOVERNANCE", "PROBLEMATIC_DATA_DETECTED", "SUCCESS"));

        when(auditService.recent(1000)).thenReturn(mockRecords);

        AuditRecordQuery query = new AuditRecordQuery(0, 50, null, "ADMIN_LIST_TENANTS", null, null, null, null, null, null, null, null);
        AuditRecordPage result = queryService.query(query);

        assertEquals(1, result.items().size());
        assertEquals("ADMIN_LIST_TENANTS", result.items().get(0).action());
    }

    @Test
    void query_paginatesCorrectly() {
        List<Map<String, Object>> mockRecords = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            mockRecords.add(createMockRecord("aud-" + i, "ADMIN_AUDIT", "ACTION_" + i, "SUCCESS"));
        }

        when(auditService.recent(1000)).thenReturn(mockRecords);

        AuditRecordQuery query = new AuditRecordQuery(2, 20, null, null, null, null, null, null, null, null, null, null);
        AuditRecordPage result = queryService.query(query);

        assertEquals(20, result.items().size());
        assertEquals(2, result.page());
        assertEquals(20, result.size());
        assertEquals(100, result.total());
    }

    @Test
    void findById_returnsDetail() {
        Map<String, Object> mockRecord = createMockRecord("aud-1", "ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "SUCCESS");
        mockRecord.put("payload", "{\"targetTenantId\":\"tenant-a\",\"result\":\"SUCCESS\",\"apiKey\":\"sk-secret-key\"}");

        when(auditService.recent(1000)).thenReturn(List.of(mockRecord));

        var result = queryService.findById("aud-1");

        assertTrue(result.isPresent());
        assertEquals("aud-1", result.get().id());
        assertNotNull(result.get().payload());
        assertEquals("[REDACTED]", result.get().payload().get("apiKey"));
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(auditService.recent(1000)).thenReturn(List.of());

        var result = queryService.findById("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void sanitizePayload_redactsSensitiveKeys() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetTenantId", "tenant-a");
        payload.put("result", "SUCCESS");
        payload.put("apiKey", "sk-secret-key");
        payload.put("signedUrl", "https://s3.amazonaws.com/bucket?X-Amz-Signature=abc");
        payload.put("nested", Map.of("password", "secret123", "safe", "value"));

        Map<String, Object> sanitized = AuditQueryService.sanitizePayload(payload);

        assertEquals("tenant-a", sanitized.get("targetTenantId"));
        assertEquals("SUCCESS", sanitized.get("result"));
        assertEquals("[REDACTED]", sanitized.get("apiKey"));
        assertEquals("[REDACTED]", sanitized.get("signedUrl"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) sanitized.get("nested");
        assertEquals("[REDACTED]", nested.get("password"));
        assertEquals("value", nested.get("safe"));
    }

    private static Map<String, Object> createMockRecord(String id, String category, String action, String result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("category", category);
        record.put("action", action);
        record.put("actor_type", "ADMIN");
        record.put("actor_id", "admin-1");
        record.put("resource_type", "tenant");
        record.put("resource_id", "tenant-a");
        record.put("created_at", "2026-05-26T10:00:00Z");
        record.put("payload", "{\"result\":\"" + result + "\"}");
        return record;
    }
}

package com.example.platform.audit.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.audit.api.dto.*;
import com.example.platform.audit.app.AuditQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuditQueryControllerTest {

    @Mock
    private AuditQueryService queryService;

    private AuditQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditQueryController(queryService);
    }

    @Test
    void listRecords_returnsOkWithPage() {
        AuditRecordPage expectedPage = new AuditRecordPage(
                java.util.List.of(
                        new AuditRecordSummary("aud-1", "2026-05-26T10:00:00Z",
                                "ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "admin-1",
                                "tenant", "tenant-a", "tenant-a", "SUCCESS", "req-1", "trace-1")),
                0, 50, 1);

        when(queryService.query(any(AuditRecordQuery.class))).thenReturn(expectedPage);

        ResponseEntity<AuditRecordPage> response = controller.listRecords(
                0, 50, "ADMIN_AUDIT", null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().items().size());
        assertEquals("ADMIN_AUDIT", response.getBody().items().get(0).category());
    }

    @Test
    void listRecords_withActionFilter() {
        when(queryService.query(any(AuditRecordQuery.class))).thenReturn(
                new AuditRecordPage(java.util.List.of(), 0, 50, 0));

        ResponseEntity<AuditRecordPage> response = controller.listRecords(
                0, 50, null, "ADMIN_DELETE_WORKSPACE", null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatusCode().value());
        verify(queryService).query(argThat(q -> "ADMIN_DELETE_WORKSPACE".equals(q.action())));
    }

    @Test
    void listRecords_clampsSize() {
        when(queryService.query(any(AuditRecordQuery.class))).thenReturn(
                new AuditRecordPage(java.util.List.of(), 0, 200, 0));

        controller.listRecords(0, 500, null, null, null, null, null, null, null, null, null, null);

        verify(queryService).query(argThat(q -> q.size() == 200));
    }

    @Test
    void getRecord_returnsOkWithDetail() {
        AuditRecordDetail expected = new AuditRecordDetail(
                "aud-1", "2026-05-26T10:00:00Z", "ADMIN_AUDIT", "ADMIN_LIST_TENANTS",
                "ADMIN", "admin-1", "tenant", "tenant-a",
                java.util.Map.of("targetTenantId", "tenant-a", "result", "SUCCESS"));

        when(queryService.findById("aud-1")).thenReturn(java.util.Optional.of(expected));

        ResponseEntity<AuditRecordDetail> response = controller.getRecord("aud-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("aud-1", response.getBody().id());
    }

    @Test
    void getRecord_notFound_returns404() {
        when(queryService.findById("nonexistent")).thenReturn(java.util.Optional.empty());

        ResponseEntity<AuditRecordDetail> response = controller.getRecord("nonexistent");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void listCategories_returnsAllCategories() {
        ResponseEntity<java.util.List<String>> response = controller.listCategories();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
        assertTrue(response.getBody().contains("ADMIN_AUDIT"));
        assertTrue(response.getBody().contains("UNKNOWN"));
    }
}

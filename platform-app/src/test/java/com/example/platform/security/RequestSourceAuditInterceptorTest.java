package com.example.platform.security;

import com.example.platform.auditcontract.api.AuditPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestSourceAuditInterceptorTest {

    private AuditPort auditPort;
    private RequestSourceAuditInterceptor interceptor;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        interceptor = new RequestSourceAuditInterceptor(auditPort);
    }

    @Test
    void shouldResolveWebSource() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/render/jobs");
        when(request.getMethod()).thenReturn("POST");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(request).setAttribute("request.source", "WEB");
        verify(auditPort).record(eq("USER"), eq("REQUEST_RECEIVED"), eq("API_REQUEST"),
                eq("http_request"), eq("POST /api/render/jobs"), any());
    }

    @Test
    void shouldResolveMcpSource() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/mcp/render/jobs");
        when(request.getMethod()).thenReturn("GET");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(request).setAttribute("request.source", "MCP");
    }

    @Test
    void shouldUseExistingSourceAttribute() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/render/jobs");
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute("request.source")).thenReturn("MCP");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(request).setAttribute("request.source", "MCP");
    }

    @Test
    void shouldSanitizeUuidsInPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/render/jobs/550e8400-e29b-41d4-a716-446655440000");
        when(request.getMethod()).thenReturn("GET");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(auditPort).record(any(), eq("REQUEST_RECEIVED"), eq("API_REQUEST"),
                eq("http_request"), eq("GET /api/render/jobs/{id}"), any());
    }

    @Test
    void shouldReturnTrue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/render/jobs");
        when(request.getMethod()).thenReturn("GET");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    void longWorkspaceGrantRouteRetainsCompletePathAndDistinctBoundedAuditKeys() {
        String prefix="/api/workspaces/ws_"+"a".repeat(32)+"/entitlements/grants/ws_grant_";
        var keys=org.mockito.ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked") var payloads=org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        for(String id:java.util.List.of("b".repeat(32),"c".repeat(32))) {
            var request=new org.springframework.mock.web.MockHttpServletRequest("POST",prefix+id+"/revoke");
            assertTrue(interceptor.preHandle(request,new org.springframework.mock.web.MockHttpServletResponse(),null));
        }
        verify(auditPort,times(2)).record(eq("USER"),eq("REQUEST_RECEIVED"),eq("API_REQUEST"),eq("http_request"),keys.capture(),payloads.capture());
        assertNotEquals(keys.getAllValues().get(0),keys.getAllValues().get(1));
        assertTrue(keys.getAllValues().stream().allMatch(k->k.length()<=120&&k.startsWith("http:sha256:")));
        assertEquals(prefix+"b".repeat(32)+"/revoke",payloads.getAllValues().get(0).get("path"));
        assertEquals(prefix+"c".repeat(32)+"/revoke",payloads.getAllValues().get(1).get("path"));
    }
}

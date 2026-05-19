package com.example.platform.security;

import com.example.platform.shared.audit.AuditPort;
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
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getMethod()).thenReturn("POST");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(request).setAttribute("request.source", "WEB");
        verify(auditPort).record(eq("USER"), eq("REQUEST_RECEIVED"), eq("API_REQUEST"),
                eq("http_request"), eq("POST /api/v1/render/jobs"), any());
    }

    @Test
    void shouldResolveMcpSource() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/mcp/render/jobs");
        when(request.getMethod()).thenReturn("GET");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(request).setAttribute("request.source", "MCP");
    }

    @Test
    void shouldUseExistingSourceAttribute() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute("request.source")).thenReturn("MCP");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(request).setAttribute("request.source", "MCP");
    }

    @Test
    void shouldSanitizeUuidsInPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs/550e8400-e29b-41d4-a716-446655440000");
        when(request.getMethod()).thenReturn("GET");

        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
        verify(auditPort).record(any(), eq("REQUEST_RECEIVED"), eq("API_REQUEST"),
                eq("http_request"), eq("GET /api/v1/render/jobs/{id}"), any());
    }

    @Test
    void shouldReturnTrue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(request.getMethod()).thenReturn("GET");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, null));
    }
}

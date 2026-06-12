package com.example.platform.app;

import com.example.platform.shared.monitoring.SentryMonitoringService;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerSentryTest {

    private final SentryMonitoringService sentryService = new SentryMonitoringService();
    private final ErrorCodeRegistry errorCodeRegistry = mock(ErrorCodeRegistry.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(errorCodeRegistry, Optional.of(sentryService));
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void shouldHandleIllegalArgumentException() {
        when(request.getHeader("Accept-Language")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/test");
        ProblemDetail pd = handler.handleIllegalArgument(
                new IllegalArgumentException("Invalid input"), request);
        assertNotNull(pd);
        assertEquals(HttpStatus.BAD_REQUEST.value(), pd.getStatus());
    }

    @Test
    void shouldHandleIllegalStateException() {
        when(request.getHeader("Accept-Language")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/test");
        ProblemDetail pd = handler.handleIllegalState(
                new IllegalStateException("Bad state"), request);
        assertNotNull(pd);
        assertEquals(HttpStatus.CONFLICT.value(), pd.getStatus());
        assertEquals("Conflict", pd.getTitle());
    }

    @Test
    void shouldHandleGeneralException() {
        when(request.getHeader("Accept-Language")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/test");
        ProblemDetail pd = handler.handleUnknown(
                new RuntimeException("Something went wrong"), request);
        assertNotNull(pd);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.getStatus());
    }

    @Test
    void shouldIncludeTimestamp() {
        when(request.getHeader("Accept-Language")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/test");
        ProblemDetail pd = handler.handleUnknown(new RuntimeException("test"), request);
        assertNotNull(pd.getProperties());
        assertNotNull(pd.getProperties().get("timestamp"));
    }

    @Test
    void shouldWorkWithoutSentryService() {
        GlobalExceptionHandler noSentry = new GlobalExceptionHandler(errorCodeRegistry, Optional.empty());
        when(request.getHeader("Accept-Language")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/test");
        assertDoesNotThrow(() -> noSentry.handleUnknown(new RuntimeException("test"), request));
        ProblemDetail pd = noSentry.handleUnknown(new RuntimeException("test"), request);
        assertNotNull(pd);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.getStatus());
    }

    @Test
    void shouldIncludeTraceIdInResponse() {
        when(request.getHeader("Accept-Language")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/test");
        org.slf4j.MDC.put("traceId", "test-trace-123");
        try {
            ProblemDetail pd = handler.handleUnknown(new RuntimeException("test"), request);
            assertEquals("test-trace-123", pd.getProperties().get("traceId"));
        } finally {
            org.slf4j.MDC.clear();
        }
    }
}

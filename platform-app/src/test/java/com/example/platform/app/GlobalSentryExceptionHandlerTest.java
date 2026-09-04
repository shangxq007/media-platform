package com.example.platform.app;

import com.example.platform.observability.monitoring.SentryMonitoringService;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerSentryTest {

    private final SentryMonitoringService sentryService = mock(SentryMonitoringService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(Optional.of(sentryService));
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
        IllegalStateException exception = new IllegalStateException("Bad state");
        ProblemDetail pd = handler.handleIllegalState(exception, request);
        assertNotNull(pd);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.getStatus());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.title(), pd.getTitle());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.code(), pd.getProperties().get("errorCode"));
        verify(sentryService).captureException(same(exception), eq(Map.of(
                "type", "IllegalStateException",
                "module", "api")));
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
        GlobalExceptionHandler noSentry = new GlobalExceptionHandler(Optional.empty());
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

    @Test
    void mapsTheExceptionCarriedLocalizedCodeWithoutARegistry() {
        when(request.getHeader("Accept-Language")).thenReturn("zh-CN");
        when(request.getRequestURI()).thenReturn("/test");
        ConfigurableErrorCode code = new ConfigurableErrorCode("NOTIFICATION-400-010", 4002010,
                Map.of("en", "Critical notification event cannot be disabled", "zh", "关键通知事件不可关闭"),
                "notification", HttpStatus.BAD_REQUEST.value());

        ProblemDetail pd = handler.handlePlatform(
                new PlatformException(code, "detail", Map.of("eventKey", "security.alert"), "en"), request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), pd.getStatus());
        assertEquals("关键通知事件不可关闭", pd.getDetail());
        assertEquals("NOTIFICATION-400-010", pd.getProperties().get("errorCode"));
        assertEquals(Map.of("eventKey", "security.alert"), pd.getProperties().get("details"));
    }
}

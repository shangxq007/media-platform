package com.example.platform.app;

import com.example.platform.shared.monitoring.GlobalSentryExceptionHandler;
import com.example.platform.shared.monitoring.SentryMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GlobalSentryExceptionHandlerTest {

    private final SentryMonitoringService sentryService = new SentryMonitoringService();
    private final GlobalSentryExceptionHandler handler = new GlobalSentryExceptionHandler(Optional.of(sentryService));

    @Test
    void shouldHandleIllegalArgumentException() {
        ProblemDetail pd = handler.handleIllegalArgument(
                new IllegalArgumentException("Invalid input"));
        assertNotNull(pd);
        assertEquals(HttpStatus.BAD_REQUEST.value(), pd.getStatus());
        assertEquals("Bad Request", pd.getTitle());
    }

    @Test
    void shouldHandleIllegalStateException() {
        ProblemDetail pd = handler.handleIllegalState(
                new IllegalStateException("Bad state"));
        assertNotNull(pd);
        assertEquals(HttpStatus.CONFLICT.value(), pd.getStatus());
        assertEquals("Conflict", pd.getTitle());
    }

    @Test
    void shouldHandleGeneralException() {
        ProblemDetail pd = handler.handleGeneralException(
                new RuntimeException("Something went wrong"));
        assertNotNull(pd);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.getStatus());
        assertEquals("Internal Server Error", pd.getTitle());
    }

    @Test
    void shouldIncludeTimestamp() {
        ProblemDetail pd = handler.handleGeneralException(new RuntimeException("test"));
        assertNotNull(pd.getProperties());
        assertNotNull(pd.getProperties().get("timestamp"));
    }

    @Test
    void shouldWorkWithoutSentryService() {
        GlobalSentryExceptionHandler noSentry = new GlobalSentryExceptionHandler(Optional.empty());
        assertDoesNotThrow(() -> noSentry.handleGeneralException(new RuntimeException("test")));
        ProblemDetail pd = noSentry.handleGeneralException(new RuntimeException("test"));
        assertNotNull(pd);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.getStatus());
    }
}

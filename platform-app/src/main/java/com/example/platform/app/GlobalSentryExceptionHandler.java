package com.example.platform.shared.monitoring;

import com.example.platform.shared.monitoring.SentryMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Global exception handler that captures exceptions via Sentry
 * and returns structured error responses.
 */
@RestControllerAdvice
@Order(10)
public class GlobalSentryExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalSentryExceptionHandler.class);

    private final Optional<SentryMonitoringService> sentryMonitoringService;

    public GlobalSentryExceptionHandler(Optional<SentryMonitoringService> sentryMonitoringService) {
        this.sentryMonitoringService = sentryMonitoringService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        sentryMonitoringService.ifPresent(s -> s.captureException(ex, Map.of(
                "type", "IllegalArgumentException",
                "module", "api"
        )));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        sentryMonitoringService.ifPresent(s -> s.captureException(ex, Map.of(
                "type", "IllegalStateException",
                "module", "api"
        )));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflict");
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        sentryMonitoringService.ifPresent(s -> s.captureException(ex, Map.of(
                "type", ex.getClass().getSimpleName(),
                "module", "api"
        )));

        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        return pd;
    }
}

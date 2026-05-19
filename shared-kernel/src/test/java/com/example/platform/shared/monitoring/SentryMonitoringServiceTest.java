package com.example.platform.shared.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SentryMonitoringServiceTest {

    private SentryMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new SentryMonitoringService();
    }

    @Test
    void shouldHandleDisabledSentry() {
        // When disabled, captureException should not throw
        assertDoesNotThrow(() ->
                service.captureException(new RuntimeException("test"), Map.of("key", "value")));
    }

    @Test
    void shouldHandleNullContext() {
        assertDoesNotThrow(() ->
                service.captureException(new RuntimeException("test"), null));
    }

    @Test
    void shouldHandleRenderPipelineException() {
        assertDoesNotThrow(() ->
                service.captureRenderPipelineException(
                        new RuntimeException("render failed"),
                        "job-123", "javacv", "tenant-1", "user-1"));
    }

    @Test
    void shouldHandleProviderException() {
        assertDoesNotThrow(() ->
                service.captureProviderException(
                        new RuntimeException("provider error"),
                        "ofx", "job-456", "tenant-1"));
    }

    @Test
    void shouldHandleRemoteWorkerException() {
        assertDoesNotThrow(() ->
                service.captureRemoteWorkerException(
                        new RuntimeException("worker error"),
                        "worker-1", "job-789", "tenant-1"));
    }

    @Test
    void shouldHandlePromptExecutionException() {
        assertDoesNotThrow(() ->
                service.capturePromptExecutionException(
                        new RuntimeException("prompt error"),
                        "exec-123", "template-456", "tenant-1", "user-1"));
    }

    @Test
    void shouldHandleMessageCapture() {
        assertDoesNotThrow(() ->
                service.captureMessage("test message", "info", Map.of()));
    }

    @Test
    void shouldHandleUserContext() {
        assertDoesNotThrow(() ->
                service.setUserContext("user-1", "tenant-1", "user@test.com"));
    }

    @Test
    void shouldHandleTagSetting() {
        assertDoesNotThrow(() ->
                service.setTag("renderJobId", "job-123"));
    }

    @Test
    void shouldBeDisabledByDefault() {
        assertFalse(service.isEnabled());
    }

    @Test
    void shouldReturnEnvironment() {
        assertNotNull(service.getEnvironment());
    }
}

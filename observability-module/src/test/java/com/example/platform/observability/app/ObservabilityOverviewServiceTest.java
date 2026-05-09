package com.example.platform.observability.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityOverviewServiceTest {

    private final ObservabilityOverviewService service = new ObservabilityOverviewService();

    @Test
    void overviewReturnsExpectedKeys() {
        Map<String, Object> result = service.overview();

        assertNotNull(result);
        assertTrue(result.containsKey("module"));
        assertTrue(result.containsKey("status"));
        assertTrue(result.containsKey("description"));
        assertTrue(result.containsKey("timestamp"));
        assertTrue(result.containsKey("traceKeys"));
        assertTrue(result.containsKey("headers"));
    }

    @Test
    void overviewReturnsCorrectModuleIdentifier() {
        Map<String, Object> result = service.overview();
        assertEquals("observability-module", result.get("module"));
        assertEquals("active", result.get("status"));
    }

    @Test
    void overviewTraceKeysContainExpectedValues() {
        Map<String, Object> result = service.overview();
        @SuppressWarnings("unchecked")
        java.util.List<String> traceKeys = (java.util.List<String>) result.get("traceKeys");
        assertNotNull(traceKeys);
        assertTrue(traceKeys.contains("traceId"));
        assertTrue(traceKeys.contains("requestId"));
        assertTrue(traceKeys.contains("tenantId"));
        assertTrue(traceKeys.contains("projectId"));
        assertTrue(traceKeys.contains("jobId"));
        assertTrue(traceKeys.contains("workflowId"));
    }

    @Test
    void overviewHeadersContainTraceAndRequestHeaders() {
        Map<String, Object> result = service.overview();
        @SuppressWarnings("unchecked")
        java.util.List<String> headers = (java.util.List<String>) result.get("headers");
        assertNotNull(headers);
        assertTrue(headers.contains("X-Trace-Id"));
        assertTrue(headers.contains("X-Request-Id"));
    }

    @Test
    void overviewTimestampIsNotNull() {
        Map<String, Object> result = service.overview();
        assertNotNull(result.get("timestamp"));
    }
}

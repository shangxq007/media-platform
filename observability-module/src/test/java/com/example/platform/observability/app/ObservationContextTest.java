package com.example.platform.observability.app;

import com.example.platform.observability.context.TraceKeys;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ObservationContextTest {
    final MdcObservationContext context = new MdcObservationContext();
    @AfterEach void clear() { MDC.clear(); }
    @Test void snapshotIsImmutableAndDoesNotRetainAfterCleanup() {
        MDC.put(TraceKeys.PRINCIPAL, "diagnostic-principal");
        var before = context.snapshot();
        MDC.clear();
        assertEquals("diagnostic-principal", before.principal());
        assertNull(context.snapshot().principal());
    }
    @Test void filterFailureClearsOwnedFieldsAndPreservesUnrelatedDiagnostics() {
        MDC.put("unrelated", "keep");
        var request = new MockHttpServletRequest("GET", "/api/example");
        request.addHeader("X-Trace-Id", "trace-a"); request.addHeader("X-Request-Id", "request-a");
        assertThrows(jakarta.servlet.ServletException.class,
                () -> new PlatformTraceCorrelationFilter().doFilter(request, new MockHttpServletResponse(), (a,b) -> {
                    assertEquals("trace-a", context.snapshot().traceId());
                    assertEquals("request-a", context.snapshot().requestId());
                    throw new jakarta.servlet.ServletException("isolated failure");
                }));
        assertNull(context.snapshot().traceId()); assertNull(context.snapshot().requestId());
        assertEquals("keep", MDC.get("unrelated"));
    }
    @Test void concurrentRequestsAndReusedThreadRemainIsolated() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> a = () -> traceRequest("a");
            Callable<String> b = () -> traceRequest("b");
            var results = executor.invokeAll(List.of(a,b));
            assertEquals("a", results.get(0).get()); assertEquals("b", results.get(1).get());
            assertTrue(executor.submit(() -> context.snapshot().traceId() == null).get());
        }
    }
    private String traceRequest(String trace) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/example"); request.addHeader("X-Trace-Id", trace);
        String[] seen = new String[1];
        new PlatformTraceCorrelationFilter().doFilter(request, new MockHttpServletResponse(), (a,b) -> seen[0] = context.snapshot().traceId());
        assertNull(context.snapshot().traceId());
        return seen[0];
    }
}

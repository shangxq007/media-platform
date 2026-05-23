package com.example.platform.observability.app;

import com.example.platform.shared.logging.TraceKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformTraceCorrelationFilterTest {

    private final PlatformTraceCorrelationFilter filter = new PlatformTraceCorrelationFilter();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void generatesTraceAndRequestIdsWhenHeadersAbsent() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(PlatformTraceCorrelationFilter.TRACE_HEADER)).thenReturn(null);
        when(request.getHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void usesProvidedTraceIdFromHeader() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        final String[] captured = new String[2];
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            captured[0] = MDC.get(TraceKeys.TRACE_ID);
            captured[1] = MDC.get(TraceKeys.REQUEST_ID);
            return null;
        }).when(chain).doFilter(request, response);

        when(request.getHeader(PlatformTraceCorrelationFilter.TRACE_HEADER)).thenReturn("test-trace-123");
        when(request.getHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER)).thenReturn("test-req-456");

        filter.doFilterInternal(request, response, chain);

        assertEquals("test-trace-123", captured[0]);
        assertEquals("test-req-456", captured[1]);
    }

    @Test
    void generatesUuidWhenHeaderIsBlank() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        final String[] captured = new String[2];
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            captured[0] = MDC.get(TraceKeys.TRACE_ID);
            captured[1] = MDC.get(TraceKeys.REQUEST_ID);
            return null;
        }).when(chain).doFilter(request, response);

        when(request.getHeader(PlatformTraceCorrelationFilter.TRACE_HEADER)).thenReturn("   ");
        when(request.getHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER)).thenReturn("");

        filter.doFilterInternal(request, response, chain);

        assertNotNull(captured[0]);
        assertNotNull(captured[1]);
        assertFalse(captured[0].isBlank());
        assertFalse(captured[1].isBlank());
    }

    @Test
    void clearsMdcAfterFilter() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(PlatformTraceCorrelationFilter.TRACE_HEADER)).thenReturn("trace-1");
        when(request.getHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER)).thenReturn("req-1");

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get(TraceKeys.TRACE_ID));
        assertNull(MDC.get(TraceKeys.REQUEST_ID));
    }

    @Test
    void setsResponseHeaders() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(PlatformTraceCorrelationFilter.TRACE_HEADER)).thenReturn("my-trace");
        when(request.getHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER)).thenReturn("my-req");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(PlatformTraceCorrelationFilter.TRACE_HEADER, "my-trace");
        verify(response).setHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER, "my-req");
    }

    @Test
    void generatesUniqueIdsForEachRequest() throws ServletException, IOException {
        List<String> traceIds = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            doAnswer(invocation -> {
                traceIds.add(MDC.get(TraceKeys.TRACE_ID));
                return null;
            }).when(chain).doFilter(request, response);

            when(request.getHeader(PlatformTraceCorrelationFilter.TRACE_HEADER)).thenReturn(null);
            when(request.getHeader(PlatformTraceCorrelationFilter.REQUEST_HEADER)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);
        }

        assertEquals(2, traceIds.size());
        assertNotNull(traceIds.get(0));
        assertNotNull(traceIds.get(1));
    }
}

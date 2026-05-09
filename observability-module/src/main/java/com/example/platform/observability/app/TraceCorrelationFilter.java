package com.example.platform.observability.app;

import com.example.platform.shared.logging.TraceKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceCorrelationFilter extends OncePerRequestFilter {
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = firstNonBlank(request.getHeader(TRACE_HEADER), UUID.randomUUID().toString());
        String requestId = firstNonBlank(request.getHeader(REQUEST_HEADER), UUID.randomUUID().toString());
        try {
            MDC.put(TraceKeys.TRACE_ID, traceId);
            MDC.put(TraceKeys.REQUEST_ID, requestId);
            response.setHeader(TRACE_HEADER, traceId);
            response.setHeader(REQUEST_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceKeys.TRACE_ID);
            MDC.remove(TraceKeys.REQUEST_ID);
        }
    }

    private String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}

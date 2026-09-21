package com.example.platform.observability.app;

import com.example.platform.observability.context.TraceKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class PlatformTraceCorrelationFilter extends OncePerRequestFilter {

    @Bean
    FilterRegistrationBean<PlatformTraceCorrelationFilter> platformTraceCorrelationFilterRegistration() {
        FilterRegistrationBean<PlatformTraceCorrelationFilter> registration = new FilterRegistrationBean<>(this);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setEnabled(true);
        return registration;
    }

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        clearRequestContext();
        String traceId = firstNonBlank(request.getHeader(TRACE_HEADER), UUID.randomUUID().toString());
        String requestId = firstNonBlank(request.getHeader(REQUEST_HEADER), UUID.randomUUID().toString());
        try {
            MDC.put(TraceKeys.TRACE_ID, traceId);
            MDC.put(TraceKeys.REQUEST_ID, requestId);
            String projectId = request.getHeader("X-Project-Id");
            if (projectId != null && !projectId.isBlank()) MDC.put(TraceKeys.PROJECT_ID, projectId);
            response.setHeader(TRACE_HEADER, traceId);
            response.setHeader(REQUEST_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            clearRequestContext();
        }
    }

    private static void clearRequestContext() {
        // Diagnostic fields never establish actor, tenant or Workspace authority.
        for (String key : new String[]{TraceKeys.TRACE_ID, TraceKeys.REQUEST_ID, TraceKeys.PROJECT_ID,
                TraceKeys.TENANT_ID, TraceKeys.PRINCIPAL, "workspaceId"}) MDC.remove(key);
        com.example.platform.shared.web.TenantContext.clear();
    }

    private String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}

package com.example.platform.logging;

import com.example.platform.shared.logging.TraceKeys;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Sets request-scoped MDC fields: requestId, traceId, projectId.
 * TenantId and principal are set by auth filters (JwtAuthFilter, OAuth2RequestContextFilter)
 * which run later in the chain.
 */
@Configuration
public class PlatformRequestContextFilter extends OncePerRequestFilter {

    @Bean
    @Order(1)
    FilterRegistrationBean<PlatformRequestContextFilter> platformRequestContextFilterRegistration() {
        FilterRegistrationBean<PlatformRequestContextFilter> registration = new FilterRegistrationBean<>(this);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        registration.setEnabled(true);
        return registration;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(TraceKeys.REQUEST_ID, Optional.ofNullable(request.getHeader("X-Request-Id"))
                    .orElse(UUID.randomUUID().toString()));
            MDC.put(TraceKeys.TRACE_ID, Optional.ofNullable(request.getHeader("X-Trace-Id"))
                    .orElse(UUID.randomUUID().toString().replace("-", "")));
            Optional.ofNullable(request.getHeader("X-Project-Id")).ifPresent(v -> MDC.put(TraceKeys.PROJECT_ID, v));
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceKeys.REQUEST_ID);
            MDC.remove(TraceKeys.TRACE_ID);
            MDC.remove(TraceKeys.PROJECT_ID);
        }
    }
}

package com.example.platform.logging;

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
            MDC.put("requestId", Optional.ofNullable(request.getHeader("X-Request-Id"))
                    .orElse(UUID.randomUUID().toString()));
            MDC.put("traceId", Optional.ofNullable(request.getHeader("X-Trace-Id"))
                    .orElse(UUID.randomUUID().toString().replace("-", "")));
            Optional.ofNullable(request.getHeader("X-Tenant-Id")).ifPresent(v -> MDC.put("tenantId", v));
            Optional.ofNullable(request.getHeader("X-Project-Id")).ifPresent(v -> MDC.put("projectId", v));
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

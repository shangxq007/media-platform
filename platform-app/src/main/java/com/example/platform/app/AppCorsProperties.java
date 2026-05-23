package com.example.platform.app;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.cors")
public record AppCorsProperties(
        List<String> allowedOriginPatterns,
        boolean allowCredentials,
        List<String> allowedHeaders) {

    public AppCorsProperties {
        if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()) {
            allowedOriginPatterns = List.of("http://localhost:*", "http://127.0.0.1:*");
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            allowedHeaders = List.of(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "X-Tenant-Id",
                    "X-Request-Id",
                    "X-Api-Key");
        }
    }

    public boolean hasWildcardOriginWithCredentials() {
        if (!allowCredentials) {
            return false;
        }
        return allowedOriginPatterns.stream()
                .anyMatch(p -> "*".equals(p) || "**".equals(p) || (p != null && p.contains("://*")));
    }
}

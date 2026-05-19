package com.example.platform.app;

import com.example.platform.identity.app.ApiKeyAuthFilter;
import com.example.platform.identity.app.IdentityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration for the media-platform API.
 *
 * Features:
 * - CORS configuration with allowed origins
 * - API Key authentication filter
 * - Rate limiting (via RateLimitFilter)
 * - IP whitelist support
 * - Security headers
 */
@Configuration
public class SecurityConfiguration {

    private final IdentityProperties identityProperties;

    public SecurityConfiguration(IdentityProperties identityProperties) {
        this.identityProperties = identityProperties;
    }

    /**
     * CORS configuration.
     * In production, restrict allowedOrigins to specific domains.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = identityProperties.getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            config.setAllowedOrigins(allowedOrigins);
        } else {
            // Development default - restrict in production
            config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Register API Key auth filter with proper ordering.
     */
    @Bean
    @Order(1)
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(ApiKeyAuthFilter filter) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        registration.setEnabled(identityProperties.isApiKeyAuthEnabled());
        return registration;
    }
}

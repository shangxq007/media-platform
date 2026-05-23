package com.example.platform.app;

import com.example.platform.identity.app.ApiKeyAuthFilter;
import com.example.platform.identity.app.IdentityAccessService;
import com.example.platform.identity.app.IdentityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AppCorsProperties.class)
public class SecurityConfiguration {

    private final IdentityProperties identityProperties;
    private final AppCorsProperties corsProperties;

    public SecurityConfiguration(IdentityProperties identityProperties, AppCorsProperties corsProperties) {
        this.identityProperties = identityProperties;
        this.corsProperties = corsProperties;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsProperties.allowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        config.setAllowCredentials(corsProperties.allowCredentials());
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @Order(1)
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(
            IdentityAccessService identityAccessService) {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(identityAccessService, identityProperties);
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        registration.setEnabled(identityProperties.isApiKeyAuthEnabled());
        return registration;
    }
}

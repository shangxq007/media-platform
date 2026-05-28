package com.example.platform.security;

import com.example.platform.identity.app.ApiKeyAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "false", matchIfMissing = true)
public class SecurityFilterChainConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityFilterChainConfig(JwtAuthFilter jwtAuthFilter, ApiKeyAuthFilter apiKeyAuthFilter,
            CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @Order(1)
    FilterRegistrationBean<ApiKeyAuthFilter> mcpApiKeyAuthFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(apiKeyAuthFilter);
        registration.addUrlPatterns("/api/v1/mcp/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(buildCspDirectives()))
                .contentTypeOptions(contentTypeOptions -> {})
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(frame -> frame.deny())
                .permissionsPolicyHeader(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()")))
            .authorizeHttpRequests(SecurityHttpRules::applyApiAuthorization)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    public static String buildCspDirectives() {
        return "default-src 'self';"
                + " base-uri 'self';"
                + " object-src 'none';"
                + " frame-ancestors 'none';"
                + " form-action 'self';"
                + " img-src 'self' data: blob: https:;"
                + " font-src 'self' data:;"
                + " style-src 'self' 'unsafe-inline';"
                + " script-src 'self';"
                + " connect-src 'self' https: wss:;"
                + " media-src 'self' blob: data: https:;"
                + " worker-src 'self' blob:;"
                + " child-src 'self' blob:;"
                + " manifest-src 'self';";
    }
}

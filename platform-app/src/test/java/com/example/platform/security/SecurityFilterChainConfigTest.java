package com.example.platform.security;

import com.example.platform.identity.app.ApiKeyAuthFilter;
import com.example.platform.identity.app.IdentityAccessService;
import com.example.platform.identity.app.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityFilterChainConfigTest {

    @Test
    void shouldCreateMcpApiKeyFilterRegistration() {
        JwtProperties props = new JwtProperties("test-secret-key-that-is-at-least-256-bits-long-for-hmac!", 3600000);
        JwtAuthFilter jwtFilter = new JwtAuthFilter(props);
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        IdentityProperties identityProps = new IdentityProperties();
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(identityService, identityProps);

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(java.util.List.of("http://localhost:*"));
        corsConfig.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource cors = new UrlBasedCorsConfigurationSource();
        cors.registerCorsConfiguration("/**", corsConfig);
        ObjectProvider<ApiKeyAuthFilter> apiKeyProvider = mock(ObjectProvider.class);
        when(apiKeyProvider.getIfAvailable()).thenReturn(apiKeyFilter);
        SecurityFilterChainConfig config = new SecurityFilterChainConfig(jwtFilter, apiKeyProvider, cors);
        var registration = config.mcpApiKeyAuthFilterRegistration();

        assertNotNull(registration);
        assertArrayEquals(new String[]{"/api/v1/mcp/*"}, registration.getUrlPatterns().toArray());
        assertEquals(1, registration.getOrder());
    }
}

package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OAuth2RequestContextFilterTest {

    private final OAuth2SecurityProperties properties =
            new OAuth2SecurityProperties(
                    true, "https://auth.example/application/o/app/", null, "tenantId", "roles", "platform_user_id",
                    false, true, true, "tenant-1");
    private final OAuth2RequestContextFilter filter = new OAuth2RequestContextFilter(properties);

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void populatesRequestAttributesAndTenantContext() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-42")
                .claim("tenantId", "tenant-9")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("user-42", request.getAttribute("jwt.subject"));
        assertEquals("tenant-9", request.getAttribute("jwt.tenantId"));
        verify(chain).doFilter(request, response);
    }
}

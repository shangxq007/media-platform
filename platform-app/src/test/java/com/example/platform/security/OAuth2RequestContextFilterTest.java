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
    private final com.example.platform.identity.api.account.AccountIdentityQueries memberships=mock(com.example.platform.identity.api.account.AccountIdentityQueries.class);
    private final OAuth2RequestContextFilter filter = new OAuth2RequestContextFilter(properties,memberships);

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
                .issuer("https://auth.example/application/o/app/")
                .subject("external-subject")
                .claim("platform_user_id","forged-member")
                .claim("tenantId", "tenant-9")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        org.mockito.Mockito.when(memberships.resolve("https://auth.example/application/o/app/","external-subject","tenant-9"))
                .thenReturn(new com.example.platform.identity.api.account.AccountMembership("account-42","user-42","tenant-9","VIEWER"));
        filter.doFilter(request, response, chain);

        assertEquals("user-42", request.getAttribute("jwt.subject"));
        assertEquals("tenant-9", request.getAttribute("jwt.tenantId"));
        assertEquals("external-subject",request.getAttribute("auth.subject"));assertEquals("account-42",request.getAttribute("identity.accountId"));
        assertEquals(List.of("VIEWER"),request.getAttribute("jwt.roles"));
        verify(chain).doFilter(request, response);
    }
}

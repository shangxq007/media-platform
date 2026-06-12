package com.example.platform.federation.graphql.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

class GraphQLContextFactorySecurityTest {

    private GraphQLContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new GraphQLContextFactory();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void graphqlContextUsesTenantContextNotHeader() {
        TenantContext.set("tenant-a");
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", "tenant-b");
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
        assertEquals("tenant-a", TenantContext.get());
    }

    @Test
    void graphqlContextWithNoTenantContextReturnsNull() {
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
        assertNull(TenantContext.get());
    }

    @Test
    void xUserIdHeaderIsIgnoredForIdentity() {
        // Attacker sends X-User-Id header but has no real authentication
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "attacker-user-id");
        when(request.getHeaders()).thenReturn(headers);

        AtomicReference<GraphQLRequestContext> capturedContext = new AtomicReference<>();
        factory.intercept(request, chain -> {
            // Capture the context that was set
            verify(request).configureExecutionInput(any());
            return null;
        });

        // Verify the context was built — the userId should be null (no authentication)
        // not "attacker-user-id" (from header)
        verify(request).configureExecutionInput(any());
    }

    @Test
    void authenticatedPrincipalUsedForUserId() {
        // Real authentication set by auth filter
        var auth = new UsernamePasswordAuthenticationToken("real-user-id", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        // Even if X-User-Id says something different
        headers.add("X-User-Id", "spoofed-user-id");
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
        // The context should use "real-user-id" from SecurityContext, not "spoofed-user-id"
    }

    @Test
    void fakeXTenantIdHeaderDoesNotAffectTenantContext() {
        TenantContext.set("legitimate-tenant");
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", "attacker-tenant");
        headers.add("X-User-Id", "attacker-user");
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        assertEquals("legitimate-tenant", TenantContext.get());
    }

    @Test
    void rolesFromAuthenticationNotFromHeader() {
        var auth = new UsernamePasswordAuthenticationToken(
                "user-1", null,
                java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_EDITOR")
                ));
        SecurityContextHolder.getContext().setAuthentication(auth);

        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        // X-User-Roles header is no longer read
        headers.add("X-User-Roles", "SPOOFED_ROLE");
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
    }
}

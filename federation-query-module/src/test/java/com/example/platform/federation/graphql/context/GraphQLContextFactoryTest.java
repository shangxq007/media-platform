package com.example.platform.federation.graphql.context;

import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphQLContextFactoryTest {

    private GraphQLContextFactory factory;

    @BeforeEach
    void setUp() {
        var owner=mock(com.example.platform.identity.api.workspace.WorkspaceQueries.class);
        when(owner.getWorkspace("ws-789")).thenReturn(new com.example.platform.identity.api.workspace.WorkspaceResponse("ws-789","tenant","Workspace",null,"FREE","ACTIVE",null,null));
        factory = new GraphQLContextFactory(owner);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsContextWithAuthenticatedUser() {
        TenantContext.set("tenant");
        // Set up authenticated principal
        var auth = new UsernamePasswordAuthenticationToken(
                "user-123", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Workspace-Id", "ws-789");
        headers.add("X-Trace-Id", "trace-abc");
        headers.add("X-Request-Id", "req-def");
        headers.add("User-Agent", "test-agent");
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
    }

    @Test
    void fallsBackToTenantContext() {
        TenantContext.set("fallback-tenant");
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
    }

    @Test
    void anonymousWhenNoAuthentication() {
        // No SecurityContext set
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
    }
}

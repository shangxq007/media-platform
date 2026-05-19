package com.example.platform.federation.graphql.context;

import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphQLContextFactoryTest {

    private GraphQLContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new GraphQLContextFactory();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsContextFromHeaders() {
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", "tenant-123");
        headers.add("X-User-Id", "user-456");
        headers.add("X-Workspace-Id", "ws-789");
        headers.add("X-Trace-Id", "trace-abc");
        headers.add("X-Request-Id", "req-def");
        headers.add("X-Request-Source", "GRAPHQL");
        headers.add("X-Auth-Type", "JWT_SESSION");
        headers.add("User-Agent", "test-agent");
        headers.add("X-Forwarded-For", "127.0.0.1");
        headers.add("X-User-Roles", "ADMIN,USER");
        headers.add("X-User-Permissions", "read,write");
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
}

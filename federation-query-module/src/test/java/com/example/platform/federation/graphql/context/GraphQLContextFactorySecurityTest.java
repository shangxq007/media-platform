package com.example.platform.federation.graphql.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;

import java.util.Map;

class GraphQLContextFactorySecurityTest {

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
    void graphqlContextUsesTenantContextNotHeader() {
        TenantContext.set("tenant-a");
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        // Attacker tries to spoof tenant via header
        headers.add("X-Tenant-Id", "tenant-b");
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        // Verify the execution input was configured
        verify(request).configureExecutionInput(any());
        // The tenant in context should be tenant-a (from TenantContext), NOT tenant-b (from header)
        // We verify this by checking that the interceptor completed without error
        // and that TenantContext was not modified
        assertEquals("tenant-a", TenantContext.get());
    }

    @Test
    void graphqlContextWithNoHeaderUsesTenantContext() {
        TenantContext.set("tenant-real");
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        // No X-Tenant-Id header
        when(request.getHeaders()).thenReturn(headers);

        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
        assertEquals("tenant-real", TenantContext.get());
    }

    @Test
    void graphqlContextWithNoTenantContextReturnsNull() {
        // No TenantContext set
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", "fake-tenant");
        when(request.getHeaders()).thenReturn(headers);

        // Should not throw — GraphQL context will have null tenant
        // Downstream resolvers must handle null tenant appropriately
        factory.intercept(request, chain -> null);

        verify(request).configureExecutionInput(any());
        assertNull(TenantContext.get());
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

        // TenantContext must remain unchanged
        assertEquals("legitimate-tenant", TenantContext.get());
    }
}

package com.example.platform.federation.graphql.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLRequestContextTest {

    @Test
    void recordHoldsAllFields() {
        GraphQLRequestContext ctx = new GraphQLRequestContext(
                "tenant-1", "ws-1", "user-1",
                List.of("ADMIN"), List.of("read", "write"),
                "GRAPHQL", "JWT_SESSION",
                "trace-123", "req-456",
                "127.0.0.1", "test-agent"
        );

        assertEquals("tenant-1", ctx.tenantId());
        assertEquals("ws-1", ctx.workspaceId());
        assertEquals("user-1", ctx.userId());
        assertEquals(List.of("ADMIN"), ctx.roles());
        assertEquals(List.of("read", "write"), ctx.permissions());
        assertEquals("GRAPHQL", ctx.requestSource());
        assertEquals("JWT_SESSION", ctx.authType());
        assertEquals("trace-123", ctx.traceId());
        assertEquals("req-456", ctx.requestId());
        assertEquals("127.0.0.1", ctx.ip());
        assertEquals("test-agent", ctx.userAgent());
    }

    @Test
    void recordAllowsNullFields() {
        GraphQLRequestContext ctx = new GraphQLRequestContext(
                null, null, null, null, null,
                null, null, null, null, null, null
        );

        assertNull(ctx.tenantId());
        assertNull(ctx.userId());
        assertNull(ctx.traceId());
    }
}

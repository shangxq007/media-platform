package com.example.platform.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallerContextTest {

    @Test
    void shouldCreateWebContext() {
        CallerContext ctx = new CallerContext("WEB", "user-1", "tenant-1", "JWT_SESSION", "trace-1");
        assertEquals("WEB", ctx.source());
        assertEquals("user-1", ctx.userId());
        assertEquals("tenant-1", ctx.tenantId());
        assertEquals("JWT_SESSION", ctx.authType());
        assertEquals("trace-1", ctx.traceId());
        assertTrue(ctx.isWeb());
        assertFalse(ctx.isMcp());
    }

    @Test
    void shouldCreateMcpContext() {
        CallerContext ctx = new CallerContext("MCP", "service-account", "tenant-2", "API_KEY", "trace-2");
        assertEquals("MCP", ctx.source());
        assertTrue(ctx.isMcp());
        assertFalse(ctx.isWeb());
    }

    @Test
    void shouldHandleNullFields() {
        CallerContext ctx = new CallerContext("WEB", null, null, "JWT_SESSION", null);
        assertNull(ctx.userId());
        assertNull(ctx.tenantId());
        assertNull(ctx.traceId());
        assertTrue(ctx.isWeb());
    }
}

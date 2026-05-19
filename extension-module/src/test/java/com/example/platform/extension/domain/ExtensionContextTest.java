package com.example.platform.extension.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionContextTest {

    @Test
    void shouldBuildWithDefaults() {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey("test-ext")
                .build();

        assertEquals("test-ext", ctx.extensionKey());
        assertNull(ctx.tenantId());
        assertNull(ctx.userId());
        assertEquals(ExtensionTrustLevel.SEMI_TRUSTED, ctx.trustLevel());
        assertTrue(ctx.config().isEmpty());
        assertTrue(ctx.attributes().isEmpty());
    }

    @Test
    void shouldBuildFully() {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey("test-ext")
                .extensionVersion("2.0.0")
                .tenantId("tenant-1")
                .userId("user-1")
                .traceId("trace-abc")
                .trustLevel(ExtensionTrustLevel.FULLY_TRUSTED)
                .config("key1", "val1")
                .attribute("attr1", 42)
                .build();

        assertEquals("test-ext", ctx.extensionKey());
        assertEquals("2.0.0", ctx.extensionVersion());
        assertEquals("tenant-1", ctx.tenantId());
        assertEquals("user-1", ctx.userId());
        assertEquals("trace-abc", ctx.traceId());
        assertEquals(ExtensionTrustLevel.FULLY_TRUSTED, ctx.trustLevel());
        assertEquals("val1", ctx.config().get("key1"));
        assertEquals(42, ctx.attributes().get("attr1"));
    }

    @Test
    void shouldHaveImmutableCollections() {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey("test-ext")
                .config("k", "v")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> ctx.config().put("x", "y"));
    }
}

package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagContextFactoryTest {

    @Test
    void createContextWithAllFields() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of("ADMIN", "USER"), List.of("group-1"),
                "enterprise", "api", "prod", "us-east", "low",
                Map.of("custom", "value")
        );
        assertEquals("tenant-1", context.tenantId());
        assertEquals("ws-1", context.workspaceId());
        assertEquals("user-1", context.userId());
        assertEquals(List.of("ADMIN", "USER"), context.roles());
        assertEquals(List.of("group-1"), context.groups());
        assertEquals("enterprise", context.tier());
        assertEquals("api", context.requestSource());
        assertEquals("prod", context.environment());
        assertEquals("us-east", context.region());
        assertEquals("low", context.riskLevel());
        assertEquals(Map.of("custom", "value"), context.attributes());
    }

    @Test
    void createContextWithMinimalFields() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null,
                null, null, null, null, null, null, null, null
        );
        assertEquals("tenant-1", context.tenantId());
        assertNull(context.workspaceId());
        assertNull(context.userId());
        assertNull(context.roles());
        assertNull(context.groups());
        assertNull(context.tier());
        assertNull(context.requestSource());
        assertNull(context.environment());
        assertNull(context.region());
        assertNull(context.riskLevel());
        assertNull(context.attributes());
    }

    @Test
    void createContextWithEmptyCollections() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of()
        );
        assertNotNull(context.roles());
        assertTrue(context.roles().isEmpty());
        assertNotNull(context.groups());
        assertTrue(context.groups().isEmpty());
        assertNotNull(context.attributes());
        assertTrue(context.attributes().isEmpty());
    }

    @Test
    void contextImmutabilityViaRecord() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of("ADMIN"), List.of("g1"),
                "pro", "web", "dev", "eu", "medium",
                Map.of("key", "val")
        );
        assertEquals("tenant-1", context.tenantId());
        assertThrows(UnsupportedOperationException.class, () -> {
            context.roles().add("HACKER");
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            context.groups().add("HACKER");
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            context.attributes().put("hack", "value");
        });
    }

    @Test
    void contextEquality() {
        FeatureFlagContext c1 = new FeatureFlagContext(
                "t1", "w1", "u1",
                List.of("R"), List.of("G"),
                "tier", "src", "env", "reg", "risk",
                Map.of("k", "v")
        );
        FeatureFlagContext c2 = new FeatureFlagContext(
                "t1", "w1", "u1",
                List.of("R"), List.of("G"),
                "tier", "src", "env", "reg", "risk",
                Map.of("k", "v")
        );
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void contextInequality() {
        FeatureFlagContext c1 = new FeatureFlagContext(
                "t1", null, null, null, null, null, null, null, null, null, null
        );
        FeatureFlagContext c2 = new FeatureFlagContext(
                "t2", null, null, null, null, null, null, null, null, null, null
        );
        assertNotEquals(c1, c2);
    }

    @Test
    void contextWithNullOptionalFields() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of()
        );
        assertNull(context.tenantId());
        assertNull(context.userId());
        assertNull(context.tier());
    }

    @Test
    void contextWithMultipleRoles() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of("ADMIN", "EDITOR", "VIEWER"), List.of(),
                null, null, null, null, null, Map.of()
        );
        assertEquals(3, context.roles().size());
        assertTrue(context.roles().contains("ADMIN"));
        assertTrue(context.roles().contains("EDITOR"));
        assertTrue(context.roles().contains("VIEWER"));
    }

    @Test
    void contextWithMultipleGroups() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of("group-a", "group-b", "group-c"),
                null, null, null, null, null, Map.of()
        );
        assertEquals(3, context.groups().size());
        assertTrue(context.groups().contains("group-a"));
        assertTrue(context.groups().contains("group-b"));
        assertTrue(context.groups().contains("group-c"));
    }

    @Test
    void contextWithComplexAttributes() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null,
                Map.of(
                        "stringAttr", "value",
                        "intAttr", 42,
                        "boolAttr", true,
                        "listAttr", List.of("a", "b")
                )
        );
        assertEquals("value", context.attributes().get("stringAttr"));
        assertEquals(42, context.attributes().get("intAttr"));
        assertEquals(true, context.attributes().get("boolAttr"));
        assertEquals(List.of("a", "b"), context.attributes().get("listAttr"));
    }

    @Test
    void contextToStringContainsKeyFields() {
        FeatureFlagContext context = new FeatureFlagContext(
                "t1", "w1", "u1",
                List.of(), List.of(), null, null, null, null, null, Map.of()
        );
        String str = context.toString();
        assertTrue(str.contains("t1"));
        assertTrue(str.contains("w1"));
        assertTrue(str.contains("u1"));
    }
}

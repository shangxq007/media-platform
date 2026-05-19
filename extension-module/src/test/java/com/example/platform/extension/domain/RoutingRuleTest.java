package com.example.platform.extension.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutingRuleTest {

    @Test
    void shouldCreateValidRule() {
        RoutingRule rule = new RoutingRule("r1", "test-rule", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 50, true);

        assertEquals("r1", rule.id());
        assertEquals("ext-1", rule.extensionCode());
        assertEquals("2.0.0", rule.targetVersion());
        assertEquals(50, rule.trafficPercent());
    }

    @Test
    void shouldThrowOnBlankExtensionCode() {
        assertThrows(IllegalArgumentException.class, () ->
                new RoutingRule("r1", "test", "", null, "2.0.0", null, null, null, 0, 50, true));
    }

    @Test
    void shouldThrowOnBlankTargetVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                new RoutingRule("r1", "test", "ext-1", null, "", null, null, null, 0, 50, true));
    }

    @Test
    void shouldThrowOnInvalidTrafficPercent() {
        assertThrows(IllegalArgumentException.class, () ->
                new RoutingRule("r1", "test", "ext-1", null, "2.0.0", null, null, null, 0, 101, true));
        assertThrows(IllegalArgumentException.class, () ->
                new RoutingRule("r1", "test", "ext-1", null, "2.0.0", null, null, null, 0, -1, true));
    }

    @Test
    void shouldMatchByTenant() {
        RoutingRule rule = new RoutingRule("r1", "test", "ext-1", null, "2.0.0",
                "tenant-1", null, null, 0, 50, true);

        assertTrue(rule.matches("tenant-1", null, null));
        assertFalse(rule.matches("tenant-2", null, null));
    }

    @Test
    void shouldMatchByUserId() {
        RoutingRule rule = new RoutingRule("r1", "test", "ext-1", null, "2.0.0",
                null, "user-1", null, 0, 50, true);

        assertTrue(rule.matches(null, "user-1", null));
        assertFalse(rule.matches(null, "user-2", null));
    }

    @Test
    void shouldMatchByScene() {
        RoutingRule rule = new RoutingRule("r1", "test", "ext-1", null, "2.0.0",
                null, null, "render", 0, 50, true);

        assertTrue(rule.matches(null, null, "render"));
        assertFalse(rule.matches(null, null, "export"));
    }

    @Test
    void shouldNotMatchWhenDisabled() {
        RoutingRule rule = new RoutingRule("r1", "test", "ext-1", null, "2.0.0",
                "tenant-1", null, null, 0, 50, false);

        assertFalse(rule.matches("tenant-1", null, null));
    }

    @Test
    void shouldMatchWithNullTenantRule() {
        RoutingRule rule = new RoutingRule("r1", "test", "ext-1", null, "2.0.0",
                null, null, null, 0, 50, true);

        assertTrue(rule.matches("any-tenant", "any-user", "any-scene"));
    }

    @Test
    void shouldMatchByVersion() {
        RoutingRule rule = new RoutingRule("r1", "test", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 0, 50, true);

        assertTrue(rule.matches(null, null, null));
    }
}

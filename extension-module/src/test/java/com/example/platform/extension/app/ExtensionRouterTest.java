package com.example.platform.extension.app;

import com.example.platform.extension.domain.RoutingRule;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionRouterTest {

    private AuditPort auditPort;
    private ExtensionRouter router;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        router = new ExtensionRouter(auditPort);
    }

    @Test
    void shouldAddAndGetRule() {
        RoutingRule rule = router.createRule("test-rule", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 50, "admin");

        assertNotNull(rule);
        List<RoutingRule> rules = router.getRules("ext-1");
        assertEquals(1, rules.size());
        verify(auditPort).record(eq("system"), eq("ROUTING_RULE_CREATED"), eq("EXTENSION_ROUTING"),
                eq("routing_rule"), eq(rule.id()), any(Map.class));
    }

    @Test
    void shouldResolveVersionByTrafficPercent() {
        router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 100, "admin");

        Optional<String> resolved = router.resolveVersion("ext-1", "1.0.0", null, null, null);
        assertTrue(resolved.isPresent());
        assertEquals("2.0.0", resolved.get());
    }

    @Test
    void shouldReturnEmptyWhenNoRulesMatch() {
        Optional<String> resolved = router.resolveVersion("ext-1", "1.0.0", null, null, null);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void shouldNotMatchDisabledRule() {
        RoutingRule rule = router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 100, "admin");
        router.removeRule(rule.id());

        Optional<String> resolved = router.resolveVersion("ext-1", "1.0.0", null, null, null);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void shouldMatchByTenant() {
        router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                "tenant-1", null, null, 10, 100, "admin");

        assertTrue(router.resolveVersion("ext-1", "1.0.0", "tenant-1", null, null).isPresent());
        assertTrue(router.resolveVersion("ext-1", "1.0.0", "tenant-2", null, null).isEmpty());
    }

    @Test
    void shouldMatchByScene() {
        router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, "render", 10, 100, "admin");

        assertTrue(router.resolveVersion("ext-1", "1.0.0", null, null, "render").isPresent());
        assertTrue(router.resolveVersion("ext-1", "1.0.0", null, null, "export").isEmpty());
    }

    @Test
    void shouldUpdateRule() {
        RoutingRule rule = router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 50, "admin");

        router.updateRule(rule.id(), 75);

        List<RoutingRule> rules = router.getRules("ext-1");
        assertEquals(75, rules.get(0).trafficPercent());
        verify(auditPort).record(eq("system"), eq("ROUTING_RULE_UPDATED"), eq("EXTENSION_ROUTING"),
                eq("routing_rule"), eq(rule.id()), any(Map.class));
    }

    @Test
    void shouldRemoveRule() {
        RoutingRule rule = router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 50, "admin");
        router.removeRule(rule.id());

        assertTrue(router.getRules("ext-1").isEmpty());
        verify(auditPort).record(eq("system"), eq("ROUTING_RULE_DELETED"), eq("EXTENSION_ROUTING"),
                eq("routing_rule"), eq(rule.id()), any(Map.class));
    }

    @Test
    void shouldRollbackRules() {
        router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 50, "admin");
        router.createRule("rule-2", "ext-1", "2.0.0", "3.0.0",
                null, null, null, 5, 25, "admin");

        router.rollbackRules("ext-1", "admin");

        assertTrue(router.getRules("ext-1").isEmpty());
        verify(auditPort).record(eq("admin"), eq("ROUTING_RULE_ROLLED_BACK"), eq("EXTENSION_ROUTING"),
                eq("routing_rule"), eq("ext-1"), any(Map.class));
    }

    @Test
    void shouldGetAllRules() {
        router.createRule("rule-1", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 10, 50, "admin");
        router.createRule("rule-2", "ext-2", "1.0.0", "2.0.0",
                null, null, null, 5, 25, "admin");

        List<RoutingRule> all = router.getAllRules();
        assertEquals(2, all.size());
    }

    @Test
    void shouldSortByPriority() {
        router.createRule("rule-low", "ext-1", "1.0.0", "2.0.0",
                null, null, null, 1, 50, "admin");
        router.createRule("rule-high", "ext-1", "1.0.0", "3.0.0",
                null, null, null, 100, 50, "admin");

        List<RoutingRule> rules = router.getRules("ext-1");
        assertEquals(2, rules.size());
        assertTrue(rules.get(0).priority() > rules.get(1).priority());
    }
}

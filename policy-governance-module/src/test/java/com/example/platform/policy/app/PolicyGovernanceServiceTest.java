package com.example.platform.policy.app;

import com.example.platform.policy.domain.ExplainResult;
import com.example.platform.policy.domain.PolicyDefinition;
import com.example.platform.policy.domain.PolicyVersion;
import com.example.platform.policy.featureflag.AppFeaturesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PolicyGovernanceServiceTest {

    private PolicyGovernanceService service;

    @BeforeEach
    void setUp() {
        AppFeaturesProperties props = new AppFeaturesProperties();
        service = new PolicyGovernanceService(props);
    }

    @Test
    void createPolicyReturnsPolicyWithGeneratedId() {
        PolicyDefinition policy = service.createPolicy("rate-limit", "Rate Limiting", "content");
        assertNotNull(policy.id());
        assertTrue(policy.id().startsWith("pol-"));
        assertEquals("rate-limit", policy.code());
        assertEquals("Rate Limiting", policy.name());
        assertEquals("content", policy.content());
        assertEquals("ACTIVE", policy.status());
    }

    @Test
    void createPolicyThrowsForDuplicateCode() {
        service.createPolicy("dup", "Name", "content");
        assertThrows(IllegalArgumentException.class,
                () -> service.createPolicy("dup", "Other", "other"));
    }

    @Test
    void findPolicyByCodeReturnsPolicy() {
        service.createPolicy("findable", "Name", "content");
        Optional<PolicyDefinition> found = service.findPolicyByCode("findable");
        assertTrue(found.isPresent());
        assertEquals("Name", found.get().name());
    }

    @Test
    void findPolicyByCodeReturnsEmptyForUnknown() {
        Optional<PolicyDefinition> found = service.findPolicyByCode("missing");
        assertTrue(found.isEmpty());
    }

    @Test
    void findPolicyByIdReturnsPolicy() {
        PolicyDefinition created = service.createPolicy("byid", "Name", "content");
        Optional<PolicyDefinition> found = service.findPolicyById(created.id());
        assertTrue(found.isPresent());
    }

    @Test
    void listPoliciesReturnsAllCreated() {
        service.createPolicy("p1", "N1", "c1");
        service.createPolicy("p2", "N2", "c2");
        List<PolicyDefinition> policies = service.listPolicies();
        assertEquals(2, policies.size());
    }

    @Test
    void listPoliciesReturnsEmptyWhenNone() {
        assertTrue(service.listPolicies().isEmpty());
    }

    @Test
    void explainReturnsDenyForUnknownPolicy() {
        ExplainResult result = service.explain("pol-999", Map.of());
        assertEquals("DENY", result.decision());
        assertTrue(result.explanation().contains("Policy not found"));
        assertFalse(result.conflicts().isEmpty());
    }

    @Test
    void explainReturnsAllowForExistingPolicy() {
        PolicyDefinition policy = service.createPolicy("test-pol", "Test", "content");
        ExplainResult result = service.explain(policy.id(), Map.of("user", "alice"));
        assertEquals("ALLOW", result.decision());
        assertTrue(result.explanation().contains("Test"));
        assertTrue(result.conflicts().isEmpty());
    }

    @Test
    void explainWithNullContext() {
        PolicyDefinition policy = service.createPolicy("null-ctx", "Test", "content");
        ExplainResult result = service.explain(policy.id(), null);
        assertEquals("ALLOW", result.decision());
        assertTrue(result.explanation().contains("none"));
    }

    @Test
    void createVersionReturnsIncrementingVersions() {
        PolicyDefinition policy = service.createPolicy("ver-pol", "Versioned", "v1");
        PolicyVersion v1 = service.createVersion(policy.id(), "content v1");
        PolicyVersion v2 = service.createVersion(policy.id(), "content v2");

        assertEquals(1, v1.version());
        assertEquals(2, v2.version());
        assertEquals(policy.id(), v1.policyId());
        assertEquals("content v1", v1.content());
        assertEquals("content v2", v2.content());
    }

    @Test
    void createVersionThrowsForUnknownPolicyId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createVersion("pol-999", "content"));
    }

    @Test
    void listVersionsReturnsAllVersions() {
        PolicyDefinition policy = service.createPolicy("vlist", "V", "c");
        service.createVersion(policy.id(), "c1");
        service.createVersion(policy.id(), "c2");
        List<PolicyVersion> versions = service.listVersions(policy.id());
        assertEquals(2, versions.size());
    }

    @Test
    void listVersionsReturnsEmptyForNoVersions() {
        PolicyDefinition policy = service.createPolicy("novers", "N", "c");
        assertTrue(service.listVersions(policy.id()).isEmpty());
    }

    @Test
    void overviewReturnsModuleInfo() {
        Map<String, Object> overview = service.overview();
        assertEquals("policy-governance-module", overview.get("module"));
        assertEquals("active", overview.get("status"));
        assertEquals(0, overview.get("policyCount"));
    }

    @Test
    void overviewIncludesPolicyCount() {
        service.createPolicy("p1", "N1", "c1");
        service.createPolicy("p2", "N2", "c2");
        Map<String, Object> overview = service.overview();
        assertEquals(2, overview.get("policyCount"));
    }

    @Test
    void listPoliciesReturnsImmutableList() {
        service.createPolicy("imm", "N", "c");
        List<PolicyDefinition> policies = service.listPolicies();
        assertThrows(UnsupportedOperationException.class, () -> policies.add(
                new PolicyDefinition("x", "x", "x", "x", "x")));
    }
}

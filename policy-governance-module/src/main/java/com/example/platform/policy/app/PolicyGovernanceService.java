package com.example.platform.policy.app;

import com.example.platform.policy.domain.ExplainResult;
import com.example.platform.policy.domain.PolicyDefinition;
import com.example.platform.policy.domain.PolicyVersion;
import com.example.platform.policy.features.AppFeaturesProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class PolicyGovernanceService {

    private final AppFeaturesProperties appFeaturesProperties;
    private final Map<String, PolicyDefinition> policiesById = new ConcurrentHashMap<>();
    private final Map<String, PolicyDefinition> policiesByCode = new ConcurrentHashMap<>();
    private final Map<String, List<PolicyVersion>> versionsByPolicyId = new ConcurrentHashMap<>();
    private final AtomicLong policySeq = new AtomicLong(0);
    private final AtomicLong versionSeq = new AtomicLong(0);

    public PolicyGovernanceService(AppFeaturesProperties appFeaturesProperties) {
        this.appFeaturesProperties = appFeaturesProperties;
    }

    public Map<String, Object> overview() {
        return Map.of(
                "module", "policy-governance-module",
                "status", "active",
                "description", "策略治理模块：OpenFeature + Unleash（可选）、LiteFlow/路由/灰度策略。",
                "unleashEnabled", appFeaturesProperties.getUnleash().isEnabled(),
                "policyCount", policiesById.size()
        );
    }

    /**
     * Creates a new policy definition.
     */
    public PolicyDefinition createPolicy(String code, String name, String content) {
        if (policiesByCode.containsKey(code)) {
            throw new IllegalArgumentException("Policy code already exists: " + code);
        }
        String id = "pol-" + policySeq.incrementAndGet();
        PolicyDefinition policy = new PolicyDefinition(id, code, name, content, "ACTIVE");
        policiesById.put(id, policy);
        policiesByCode.put(code, policy);
        versionsByPolicyId.put(id, new ArrayList<>());
        return policy;
    }

    /**
     * Explains the decision for a policy given a context.
     * This is a stub implementation that returns a simple explanation.
     */
    public ExplainResult explain(String policyId, Map<String, String> context) {
        PolicyDefinition policy = policiesById.get(policyId);
        if (policy == null) {
            return new ExplainResult(
                    "DENY",
                    "Policy not found: " + policyId,
                    List.of("Policy does not exist"));
        }
        List<PolicyVersion> versions = versionsByPolicyId.getOrDefault(policyId, List.of());
        String explanation = "Policy '" + policy.name() + "' (code=" + policy.code()
                + ") evaluated with " + versions.size() + " version(s). "
                + "Context keys: " + (context != null ? context.keySet() : "none");
        return new ExplainResult("ALLOW", explanation, List.of());
    }

    /**
     * Lists all registered policies.
     */
    public List<PolicyDefinition> listPolicies() {
        return List.copyOf(policiesById.values());
    }

    public Optional<PolicyDefinition> findPolicyByCode(String code) {
        return Optional.ofNullable(policiesByCode.get(code));
    }

    public Optional<PolicyDefinition> findPolicyById(String id) {
        return Optional.ofNullable(policiesById.get(id));
    }

    /**
     * Creates a new version for an existing policy.
     */
    public PolicyVersion createVersion(String policyId, String content) {
        PolicyDefinition policy = policiesById.get(policyId);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown policy id: " + policyId);
        }
        List<PolicyVersion> versions = versionsByPolicyId.computeIfAbsent(
                policyId, k -> new ArrayList<>());
        int nextVersion = versions.size() + 1;
        String id = "pver-" + versionSeq.incrementAndGet();
        PolicyVersion version = new PolicyVersion(id, policyId, nextVersion, content);
        versions.add(version);
        return version;
    }

    public List<PolicyVersion> listVersions(String policyId) {
        return List.copyOf(versionsByPolicyId.getOrDefault(policyId, List.of()));
    }
}

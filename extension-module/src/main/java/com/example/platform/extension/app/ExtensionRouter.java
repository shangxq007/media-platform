package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ExtensionRouter {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRouter.class);

    private final AuditPort auditPort;
    private final ConcurrentHashMap<String, List<RoutingRule>> routingRules = new ConcurrentHashMap<>();

    public ExtensionRouter(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void addRule(RoutingRule rule) {
        routingRules.computeIfAbsent(rule.extensionCode(), k -> new ArrayList<>()).add(rule);
        routingRules.get(rule.extensionCode()).sort(Comparator.comparingInt(RoutingRule::priority).reversed());
        auditPort.record("system", "ROUTING_RULE_CREATED", "EXTENSION_ROUTING",
                "routing_rule", rule.id(), Map.of(
                        "extensionCode", rule.extensionCode(),
                        "targetVersion", rule.targetVersion(),
                        "trafficPercent", rule.trafficPercent()));
    }

    public void updateRule(String ruleId, int trafficPercent) {
        for (List<RoutingRule> rules : routingRules.values()) {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).id().equals(ruleId)) {
                    RoutingRule old = rules.get(i);
                    RoutingRule updated = new RoutingRule(
                            old.id(), old.ruleName(), old.extensionCode(),
                            old.sourceVersion(), old.targetVersion(),
                            old.tenantId(), old.userId(), old.scene(),
                            old.priority(), trafficPercent, old.enabled());
                    rules.set(i, updated);
                    auditPort.record("system", "ROUTING_RULE_UPDATED", "EXTENSION_ROUTING",
                            "routing_rule", ruleId, Map.of("trafficPercent", trafficPercent));
                    return;
                }
            }
        }
    }

    public void removeRule(String ruleId) {
        for (List<RoutingRule> rules : routingRules.values()) {
            rules.removeIf(r -> {
                if (r.id().equals(ruleId)) {
                    auditPort.record("system", "ROUTING_RULE_DELETED", "EXTENSION_ROUTING",
                            "routing_rule", ruleId, Map.of("extensionCode", r.extensionCode()));
                    return true;
                }
                return false;
            });
        }
    }

    public Optional<String> resolveVersion(String extensionCode, String currentVersion,
                                            String tenantId, String userId, String scene) {
        List<RoutingRule> rules = routingRules.getOrDefault(extensionCode, List.of());
        List<RoutingRule> matchingRules = rules.stream()
                .filter(r -> r.matches(tenantId, userId, scene))
                .filter(r -> r.sourceVersion() == null || r.sourceVersion().equals(currentVersion))
                .sorted(Comparator.comparingInt(RoutingRule::priority).reversed())
                .toList();

        if (matchingRules.isEmpty()) {
            return Optional.empty();
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        int cumulative = 0;
        for (RoutingRule rule : matchingRules) {
            cumulative += rule.trafficPercent();
            if (roll < cumulative) {
                log.debug("Routing {} to version {} (rule={}, percent={})",
                        extensionCode, rule.targetVersion(), rule.id(), rule.trafficPercent());
                return Optional.of(rule.targetVersion());
            }
        }
        return Optional.empty();
    }

    public List<RoutingRule> getRules(String extensionCode) {
        return List.copyOf(routingRules.getOrDefault(extensionCode, List.of()));
    }

    public List<RoutingRule> getAllRules() {
        return routingRules.values().stream().flatMap(Collection::stream).toList();
    }

    public void rollbackRules(String extensionCode, String rolledBackBy) {
        List<RoutingRule> rules = routingRules.getOrDefault(extensionCode, List.of());
        List<String> ruleIds = rules.stream().map(RoutingRule::id).toList();
        routingRules.remove(extensionCode);
        auditPort.record(rolledBackBy, "ROUTING_RULE_ROLLED_BACK", "EXTENSION_ROUTING",
                "routing_rule", extensionCode, Map.of(
                        "removedRules", ruleIds.size(),
                        "ruleIds", ruleIds));
        log.info("Rolled back {} routing rules for extension {}", ruleIds.size(), extensionCode);
    }

    public RoutingRule createRule(String ruleName, String extensionCode, String sourceVersion,
                                   String targetVersion, String tenantId, String userId,
                                   String scene, int priority, int trafficPercent,
                                   String createdBy) {
        String id = Ids.newId("route");
        RoutingRule rule = new RoutingRule(id, ruleName, extensionCode, sourceVersion,
                targetVersion, tenantId, userId, scene, priority, trafficPercent, true);
        addRule(rule);
        return rule;
    }
}

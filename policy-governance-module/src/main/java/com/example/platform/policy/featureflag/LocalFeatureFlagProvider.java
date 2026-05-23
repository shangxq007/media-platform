package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class LocalFeatureFlagProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFeatureFlagProvider.class);

    private final FeatureFlagPersistence store;

    public LocalFeatureFlagProvider(@Autowired(required = false) FeatureFlagJdbcStore jdbcStore) {
        this.store = jdbcStore != null ? jdbcStore : new InMemoryFeatureFlagPersistence();
    }

    /** For unit tests without Spring context. */
    public LocalFeatureFlagProvider() {
        this.store = new InMemoryFeatureFlagPersistence();
    }

    public Optional<FeatureFlagDefinition> getFlag(String flagKey) {
        return store.findByKey(flagKey);
    }

    public FeatureFlagDefinition saveFlag(FeatureFlagDefinition definition) {
        return store.save(definition);
    }

    public List<FeatureFlagDefinition> listFlags() {
        return store.findAll();
    }

    public List<FeatureFlagDefinition> listFlagsByTag(String tag) {
        return listFlags().stream()
                .filter(f -> f.tags() != null && f.tags().contains(tag))
                .collect(Collectors.toList());
    }

    public boolean deleteFlag(String flagKey) {
        return store.delete(flagKey);
    }

    public void saveRule(String flagKey, FeatureFlagTargetingRule rule) {
        store.saveRule(flagKey, rule);
    }

    public List<FeatureFlagTargetingRule> getRules(String flagKey) {
        return store.findRules(flagKey);
    }

    public void clearRules(String flagKey) {
        store.clearRules(flagKey);
    }

    public FeatureFlagDecision evaluate(FeatureFlagEvaluationRequest request) {
        String flagKey = request.flagKey();
        FeatureFlagContext context = request.context();
        Object defaultValue = request.defaultValue();

        FeatureFlagDefinition definition = store.findByKey(flagKey).orElse(null);
        if (definition == null || !definition.enabled() || definition.archived()) {
            boolean fallback = defaultValue instanceof Boolean ? (Boolean) defaultValue : false;
            String reason = definition == null ? "FLAG_NOT_DEFINED"
                    : definition.archived() ? "FLAG_ARCHIVED" : "FLAG_DISABLED";
            return new FeatureFlagDecision(
                    flagKey, fallback, null, reason,
                    FeatureFlagProviderType.LOCAL, null,
                    context != null ? context.tenantId() : null,
                    context != null ? context.workspaceId() : null,
                    context != null ? context.userId() : null,
                    Instant.now(), Map.of("reason", reason)
            );
        }

        List<FeatureFlagTargetingRule> rules = store.findRules(flagKey).stream()
                .filter(FeatureFlagTargetingRule::enabled)
                .sorted(Comparator.comparingInt(r -> r.priority() != null ? r.priority() : Integer.MAX_VALUE))
                .collect(Collectors.toList());

        for (FeatureFlagTargetingRule rule : rules) {
            if (isRuleExpired(rule)) {
                continue;
            }
            if (matchesRule(rule, context)) {
                boolean enabled = resolveEnabledFromRule(rule, context, definition, defaultValue);
                String variant = resolveVariant(rule, definition, enabled);
                return new FeatureFlagDecision(
                        flagKey, enabled, variant, "RULE_MATCHED",
                        FeatureFlagProviderType.LOCAL, rule.ruleId(),
                        context != null ? context.tenantId() : null,
                        context != null ? context.workspaceId() : null,
                        context != null ? context.userId() : null,
                        Instant.now(), Map.of("matchedRule", rule.ruleId())
                );
            }
        }

        boolean defaultEnabled = defaultValue instanceof Boolean ? (Boolean) defaultValue : false;
        return new FeatureFlagDecision(
                flagKey, defaultEnabled, null, "NO_MATCHING_RULE",
                FeatureFlagProviderType.LOCAL, null,
                context != null ? context.tenantId() : null,
                context != null ? context.workspaceId() : null,
                context != null ? context.userId() : null,
                Instant.now(), Map.of()
        );
    }

    public List<FeatureFlagDecision> evaluateBatch(List<FeatureFlagEvaluationRequest> requests) {
        return requests.stream().map(this::evaluate).collect(Collectors.toList());
    }

    private boolean isRuleExpired(FeatureFlagTargetingRule rule) {
        Instant now = Instant.now();
        if (rule.startAt() != null && now.isBefore(rule.startAt())) return true;
        if (rule.endAt() != null && now.isAfter(rule.endAt())) return true;
        return false;
    }

    private boolean matchesRule(FeatureFlagTargetingRule rule, FeatureFlagContext context) {
        if (context == null) {
            return rule.tenantId() == null && rule.workspaceId() == null
                    && rule.userId() == null && rule.role() == null
                    && rule.group() == null && rule.tier() == null
                    && rule.region() == null && rule.requestSource() == null
                    && rule.environment() == null && rule.percentage() == null;
        }

        if (rule.tenantId() != null && !rule.tenantId().equals(context.tenantId())) return false;
        if (rule.workspaceId() != null && !rule.workspaceId().equals(context.workspaceId())) return false;
        if (rule.userId() != null && !rule.userId().equals(context.userId())) return false;
        if (rule.role() != null && (context.roles() == null || !context.roles().contains(rule.role()))) return false;
        if (rule.group() != null && (context.groups() == null || !context.groups().contains(rule.group()))) return false;
        if (rule.tier() != null && !rule.tier().equals(context.tier())) return false;
        if (rule.region() != null && !rule.region().equals(context.region())) return false;
        if (rule.requestSource() != null && !rule.requestSource().equals(context.requestSource())) return false;
        if (rule.environment() != null && !rule.environment().equals(context.environment())) return false;

        if (rule.percentage() != null) {
            return isWithinPercentage(rule.percentage(), context);
        }

        return true;
    }

    private boolean isWithinPercentage(Double percentage, FeatureFlagContext context) {
        String hashKey = context.userId() != null ? context.userId()
                : context.tenantId() != null ? context.tenantId() : UUID.randomUUID().toString();
        int hash = Math.abs(hashKey.hashCode() % 100);
        return hash < percentage;
    }

    private boolean resolveEnabledFromRule(FeatureFlagTargetingRule rule, FeatureFlagContext context,
                                            FeatureFlagDefinition definition, Object defaultValue) {
        if (rule.percentage() != null && context != null) {
            return isWithinPercentage(rule.percentage(), context);
        }
        if (definition.flagType() == FeatureFlagType.BOOLEAN) {
            return definition.defaultValue() instanceof Boolean ? (Boolean) definition.defaultValue() : true;
        }
        return true;
    }

    private String resolveVariant(FeatureFlagTargetingRule rule, FeatureFlagDefinition definition, boolean enabled) {
        if (definition.variants() != null && !definition.variants().isEmpty()) {
            for (FeatureFlagVariant variant : definition.variants()) {
                if (enabled && "enabled".equals(variant.key())) return variant.key();
                if (!enabled && "disabled".equals(variant.key())) return variant.key();
            }
        }
        return enabled ? "enabled" : "disabled";
    }
}

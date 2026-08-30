package com.example.platform.billing.usage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Explicit rule/version resolver with no implicit or default mapping. */
@Component
public class MeteringRuleRegistry {

    private final Map<RuleKey, MeteringRule> rules = new ConcurrentHashMap<>();

    public MeteringRule register(MeteringRule rule) {
        MeteringRule existing = rules.putIfAbsent(new RuleKey(rule.ruleId(), rule.version()), rule);
        if (existing != null && !existing.equals(rule)) {
            throw new IllegalStateException("Rule/version already registered with different payload");
        }
        return existing != null ? existing : rule;
    }

    public Optional<MeteringRule> find(String ruleId, String version) {
        return Optional.ofNullable(rules.get(new RuleKey(ruleId, version)));
    }

    public void clear() {
        rules.clear();
    }

    private record RuleKey(String ruleId, String version) {}
}

package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDefinition;
import com.example.platform.policy.featureflag.domain.FeatureFlagTargetingRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory persistence for unit tests without a database. */
public class InMemoryFeatureFlagPersistence implements FeatureFlagPersistence {

    private final ConcurrentHashMap<String, FeatureFlagDefinition> flags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<FeatureFlagTargetingRule>> rules = new ConcurrentHashMap<>();

    @Override
    public Optional<FeatureFlagDefinition> findByKey(String flagKey) {
        FeatureFlagDefinition def = flags.get(flagKey);
        if (def == null) {
            return Optional.empty();
        }
        return Optional.of(copy(def, rules.getOrDefault(flagKey, List.of())));
    }

    @Override
    public List<FeatureFlagDefinition> findAll() {
        return flags.values().stream()
                .map(def -> copy(def, rules.getOrDefault(def.flagKey(), List.of())))
                .toList();
    }

    @Override
    public FeatureFlagDefinition save(FeatureFlagDefinition definition) {
        flags.put(definition.flagKey(), definition);
        if (definition.targetingRules() != null) {
            rules.put(definition.flagKey(), new ArrayList<>(definition.targetingRules()));
        }
        return definition;
    }

    @Override
    public boolean delete(String flagKey) {
        rules.remove(flagKey);
        return flags.remove(flagKey) != null;
    }

    @Override
    public void saveRule(String flagKey, FeatureFlagTargetingRule rule) {
        rules.computeIfAbsent(flagKey, k -> new ArrayList<>()).add(rule);
    }

    @Override
    public List<FeatureFlagTargetingRule> findRules(String flagKey) {
        return List.copyOf(rules.getOrDefault(flagKey, List.of()));
    }

    @Override
    public void clearRules(String flagKey) {
        rules.remove(flagKey);
    }

    private static FeatureFlagDefinition copy(FeatureFlagDefinition def, List<FeatureFlagTargetingRule> ruleList) {
        return new FeatureFlagDefinition(
                def.flagKey(), def.name(), def.description(), def.flagType(), def.defaultValue(),
                def.variants(), ruleList, def.enabled(), def.owner(), def.tags(),
                def.createdAt(), def.updatedAt(), def.archived());
    }
}

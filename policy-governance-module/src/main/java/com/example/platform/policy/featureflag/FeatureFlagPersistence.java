package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.FeatureFlagDefinition;
import com.example.platform.policy.featureflag.domain.FeatureFlagTargetingRule;

import java.util.List;
import java.util.Optional;

public interface FeatureFlagPersistence {

    Optional<FeatureFlagDefinition> findByKey(String flagKey);

    List<FeatureFlagDefinition> findAll();

    FeatureFlagDefinition save(FeatureFlagDefinition definition);

    boolean delete(String flagKey);

    void saveRule(String flagKey, FeatureFlagTargetingRule rule);

    List<FeatureFlagTargetingRule> findRules(String flagKey);

    void clearRules(String flagKey);
}

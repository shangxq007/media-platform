package com.example.platform.policy.featureflag;

import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.policy.featureflag.LocalFeatureFlagProvider;
import com.example.platform.policy.featureflag.OpenFeatureFlagEvaluator;
import com.example.platform.policy.featureflag.domain.*;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@DependsOn("openFeatureLifecycle")
public class FeatureFlagService implements FeatureFlagEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final Client client = OpenFeatureAPI.getInstance().getClient();
    private final LocalFeatureFlagProvider localProvider;
    private final OpenFeatureFlagEvaluator openFeatureEvaluator;
    private final AppFeaturesProperties appFeaturesProperties;
    private final ConcurrentHashMap<String, FeatureFlagDefinition> flagCache = new ConcurrentHashMap<>();

    public FeatureFlagService(
            LocalFeatureFlagProvider localProvider,
            OpenFeatureFlagEvaluator openFeatureEvaluator,
            AppFeaturesProperties appFeaturesProperties) {
        this.localProvider = localProvider;
        this.openFeatureEvaluator = openFeatureEvaluator;
        this.appFeaturesProperties = appFeaturesProperties;
    }

    @Override
    public boolean isEnabled(
            String flagKey, String targetingKey, Map<String, String> attributes, boolean defaultValue) {
        Map<String, Value> attrs = new HashMap<>();
        if (attributes != null) {
            attributes.forEach((k, v) -> attrs.put(k, new Value(v != null ? v : "")));
        }
        var ctx = new ImmutableContext(targetingKey != null ? targetingKey : "", attrs);
        return client.getBooleanValue(flagKey, defaultValue, ctx);
    }

    public FeatureFlagEvaluationResult evaluate(FeatureFlagEvaluationRequest request) {
        FeatureFlagDecision decision;
        if (appFeaturesProperties.getUnleash().isEnabled()) {
            decision = openFeatureEvaluator.evaluate(request);
        } else {
            ensureCached(request.flagKey());
            decision = localProvider.evaluate(request);
        }
        return new FeatureFlagEvaluationResult(decision);
    }

    public List<FeatureFlagEvaluationResult> evaluateBatch(List<FeatureFlagEvaluationRequest> requests) {
        boolean useOpenFeature = appFeaturesProperties.getUnleash().isEnabled();
        List<FeatureFlagDecision> decisions;
        if (useOpenFeature) {
            decisions = requests.stream()
                    .map(openFeatureEvaluator::evaluate)
                    .collect(Collectors.toList());
        } else {
            requests.forEach(r -> ensureCached(r.flagKey()));
            decisions = localProvider.evaluateBatch(requests);
        }
        return decisions.stream()
                .map(FeatureFlagEvaluationResult::new)
                .collect(Collectors.toList());
    }

    public FeatureFlagDefinition createFlag(FeatureFlagDefinition definition) {
        FeatureFlagDefinition toSave = definition;
        if (definition.createdAt() == null) {
            toSave = new FeatureFlagDefinition(
                    definition.flagKey(), definition.name(), definition.description(),
                    definition.flagType(), definition.defaultValue(), definition.variants(),
                    definition.targetingRules(), definition.enabled(), definition.owner(),
                    definition.tags(), Instant.now(), Instant.now(), false
            );
        }
        localProvider.saveFlag(toSave);
        String savedFlagKey = toSave.flagKey();
        if (toSave.targetingRules() != null) {
            localProvider.clearRules(savedFlagKey);
            toSave.targetingRules().forEach(r -> localProvider.saveRule(savedFlagKey, r));
        }
        reloadFlagFromStore(savedFlagKey);
        log.info("FeatureFlagService: created flag '{}'", toSave.flagKey());
        return getFlag(savedFlagKey).orElse(toSave);
    }

    public Optional<FeatureFlagDefinition> getFlag(String flagKey) {
        FeatureFlagDefinition cached = flagCache.get(flagKey);
        if (cached != null) return Optional.of(cached);
        return localProvider.getFlag(flagKey);
    }

    public List<FeatureFlagDefinition> listFlags() {
        List<FeatureFlagDefinition> localFlags = localProvider.listFlags();
        localFlags.forEach(f -> flagCache.putIfAbsent(f.flagKey(), f));
        return localFlags;
    }

    public FeatureFlagDefinition updateFlag(String flagKey, FeatureFlagDefinition definition) {
        FeatureFlagDefinition existing = flagCache.get(flagKey);
        if (existing == null) {
            existing = localProvider.getFlag(flagKey).orElse(null);
        }
        if (existing == null) {
            throw new IllegalArgumentException("Flag not found: " + flagKey);
        }
        FeatureFlagDefinition updated = new FeatureFlagDefinition(
                flagKey, definition.name(), definition.description(),
                definition.flagType(), definition.defaultValue(), definition.variants(),
                definition.targetingRules(), definition.enabled(), definition.owner(),
                definition.tags(), existing.createdAt(), Instant.now(), existing.archived()
        );
        localProvider.saveFlag(updated);
        if (definition.targetingRules() != null) {
            localProvider.clearRules(flagKey);
            definition.targetingRules().forEach(r -> localProvider.saveRule(flagKey, r));
        }
        reloadFlagFromStore(flagKey);
        return getFlag(flagKey).orElse(updated);
    }

    public FeatureFlagDefinition enableFlag(String flagKey) {
        return updateFlagEnabled(flagKey, true);
    }

    public FeatureFlagDefinition disableFlag(String flagKey) {
        return updateFlagEnabled(flagKey, false);
    }

    public FeatureFlagDefinition archiveFlag(String flagKey) {
        FeatureFlagDefinition existing = flagCache.get(flagKey);
        if (existing == null) {
            existing = localProvider.getFlag(flagKey).orElse(null);
        }
        if (existing == null) {
            throw new IllegalArgumentException("Flag not found: " + flagKey);
        }
        FeatureFlagDefinition archived = new FeatureFlagDefinition(
                existing.flagKey(), existing.name(), existing.description(),
                existing.flagType(), existing.defaultValue(), existing.variants(),
                existing.targetingRules(), false, existing.owner(),
                existing.tags(), existing.createdAt(), Instant.now(), true
        );
        localProvider.saveFlag(archived);
        reloadFlagFromStore(flagKey);
        log.info("FeatureFlagService: archived flag '{}'", flagKey);
        return getFlag(flagKey).orElse(archived);
    }

    public void addTargetingRule(String flagKey, FeatureFlagTargetingRule rule) {
        localProvider.saveRule(flagKey, rule);
        log.info("FeatureFlagService: added rule '{}' to flag '{}'", rule.ruleId(), flagKey);
    }

    public List<FeatureFlagTargetingRule> getTargetingRules(String flagKey) {
        return localProvider.getRules(flagKey);
    }

    public List<FeatureFlagDefinition> getFlagsForContext(FeatureFlagContext context) {
        return listFlags().stream()
                .filter(f -> !f.archived())
                .collect(Collectors.toList());
    }

    private FeatureFlagDefinition updateFlagEnabled(String flagKey, boolean enabled) {
        FeatureFlagDefinition existing = flagCache.get(flagKey);
        if (existing == null) {
            existing = localProvider.getFlag(flagKey).orElse(null);
        }
        if (existing == null) {
            throw new IllegalArgumentException("Flag not found: " + flagKey);
        }
        FeatureFlagDefinition updated = new FeatureFlagDefinition(
                existing.flagKey(), existing.name(), existing.description(),
                existing.flagType(), existing.defaultValue(), existing.variants(),
                existing.targetingRules(), enabled, existing.owner(),
                existing.tags(), existing.createdAt(), Instant.now(), existing.archived()
        );
        localProvider.saveFlag(updated);
        reloadFlagFromStore(flagKey);
        log.info("FeatureFlagService: {} flag '{}'", enabled ? "enabled" : "disabled", flagKey);
        return getFlag(flagKey).orElse(updated);
    }

    /** Refreshes one flag from the authoritative persistence store into the read cache. */
    public void reloadFlagFromStore(String flagKey) {
        localProvider.getFlag(flagKey).ifPresent(def -> flagCache.put(flagKey, def));
    }

    public void reloadAllFlagsFromStore() {
        flagCache.clear();
        localProvider.listFlags().forEach(def -> flagCache.put(def.flagKey(), def));
    }

    private void ensureCached(String flagKey) {
        if (!flagCache.containsKey(flagKey)) {
            reloadFlagFromStore(flagKey);
        }
    }
}

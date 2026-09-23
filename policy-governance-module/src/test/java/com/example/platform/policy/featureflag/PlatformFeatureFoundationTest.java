package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlatformFeatureFoundationTest {
    private static FeatureFlagDefinition flag(String key, boolean enabled) {
        return new FeatureFlagDefinition(key, key, null, FeatureFlagType.BOOLEAN, enabled,
                List.of(), List.of(), true, "platform", List.of(), Instant.now(), Instant.now(), false);
    }

    @Test void enabledAndDisabledAndUnknownAreExplicit() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        provider.saveFlag(flag("enabled", true));
        provider.saveFlag(new FeatureFlagDefinition("disabled", "disabled", null, FeatureFlagType.BOOLEAN, false, List.of(), List.of(), false, "platform", List.of(), Instant.now(), Instant.now(), false));
        assertEquals("NO_MATCHING_RULE", provider.evaluate(new FeatureFlagEvaluationRequest("enabled", null, false)).reasonCode());
        assertEquals("FLAG_DISABLED", provider.evaluate(new FeatureFlagEvaluationRequest("disabled", null, true)).reasonCode());
        assertEquals("FLAG_NOT_DEFINED", provider.evaluate(new FeatureFlagEvaluationRequest("unknown", null, true)).reasonCode());
    }

    @Test void percentageEvaluationRequiresStableAuthoritativeSubject() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        provider.saveFlag(flag("rollout", true));
        provider.saveRule("rollout", new FeatureFlagTargetingRule("r", "rollout", 1, true,
                null, null, null, null, null, null, 50d, null, null, null, null, null));
        FeatureFlagDecision missing = provider.evaluate(new FeatureFlagEvaluationRequest("rollout",
                new FeatureFlagContext(null, null, null, List.of(), List.of(), null, null, null, null, null, Map.of()), false));
        assertEquals("INVALID_CONTEXT", missing.reasonCode());
        FeatureFlagContext context = new FeatureFlagContext("tenant-a", "workspace-a", "user-a",
                List.of(), List.of(), null, "server", null, null, null, Map.of());
        boolean first = provider.evaluate(new FeatureFlagEvaluationRequest("rollout", context, false)).enabled();
        for (int i = 0; i < 20; i++) assertEquals(first,
                provider.evaluate(new FeatureFlagEvaluationRequest("rollout", context, false)).enabled());
    }

    @Test void snapshotFreezesEvaluationForReplay() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        provider.saveFlag(flag("render.v2", true));
        FeatureFlagSnapshotResolver resolver = new FeatureFlagSnapshotResolver(provider);
        FeatureFlagContext context = new FeatureFlagContext("tenant-a", "workspace-a", "user-a",
                List.of(), List.of(), null, "server", null, null, null, Map.of());
        FeatureFlagSnapshot snapshot = resolver.capture(context, Map.of("render.v2", false));
        assertEquals(snapshot.decision("render.v2"), resolver.replay(snapshot, "render.v2"));
        InMemoryFeatureFlagSnapshotStore store = new InMemoryFeatureFlagSnapshotStore();
        FeatureFlagSnapshot persisted = resolver.captureAndPersist(context, Map.of("render.v2", false), store);
        assertEquals(snapshot.decision("render.v2").enabled(), store.load(persisted.snapshotId()).decision("render.v2").enabled());
        assertThrows(IllegalArgumentException.class, () -> snapshot.decision("later-added-flag"));
    }

    @Test void snapshotRejectsMissingTenantAndProviderFailureDoesNotSwitchAuthority() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagSnapshotResolver resolver = new FeatureFlagSnapshotResolver(provider);
        FeatureFlagContext incomplete = new FeatureFlagContext(null, "workspace-a", "user-a",
                List.of(), List.of(), null, "server", null, null, null, Map.of());
        assertThrows(IllegalArgumentException.class, () -> resolver.capture(incomplete, Map.of("x", false)));
        PlatformFeatureProvider failing = request -> new FeatureFlagDecision(request.flagKey(), false, null,
                "PROVIDER_ERROR", FeatureFlagProviderType.LOCAL, null, "tenant-a", null, null,
                Instant.now(), Map.of("error", "control-plane unavailable"));
        FeatureFlagDecision decision = failing.evaluate(new FeatureFlagEvaluationRequest("x", incomplete, false));
        assertEquals("PROVIDER_ERROR", decision.reasonCode());
    }
}

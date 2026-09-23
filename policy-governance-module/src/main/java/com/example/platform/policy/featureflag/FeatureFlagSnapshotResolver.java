package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Resolves and freezes feature decisions before a durable workflow is admitted. */
public final class FeatureFlagSnapshotResolver {
    private static final String REVISION = "platform-feature-provider-v1";
    private final PlatformFeatureProvider provider;

    public FeatureFlagSnapshotResolver(PlatformFeatureProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public FeatureFlagSnapshot capture(FeatureFlagContext context, Map<String, Object> defaults) {
        if (context == null || context.tenantId() == null || context.tenantId().isBlank()) {
            throw new IllegalArgumentException("authoritative tenant scope is required");
        }
        Map<String, Object> requested = defaults == null ? Map.of() : new TreeMap<>(defaults);
        Map<String, FeatureFlagDecision> decisions = new TreeMap<>();
        requested.forEach((key, value) -> {
            FeatureFlagDecision decision = provider.evaluate(
                    new FeatureFlagEvaluationRequest(key, context, value));
            decisions.put(key, decision);
        });
        String snapshotId = digest(context, decisions);
        return new FeatureFlagSnapshot(snapshotId, REVISION, context.tenantId(), context.workspaceId(), Instant.now(), decisions);
    }

    public FeatureFlagSnapshot captureAndPersist(FeatureFlagContext context, Map<String, Object> defaults,
                                                   FeatureFlagSnapshotStore store) {
        FeatureFlagSnapshot snapshot = capture(context, defaults);
        store.persist(snapshot);
        return snapshot;
    }

    public FeatureFlagDecision replay(FeatureFlagSnapshot snapshot, String flagKey) {
        return snapshot.decision(flagKey);
    }

    public FeatureFlagDecision replay(FeatureFlagSnapshotStore store, String snapshotId,
                                     FeatureFlagContext authoritativeContext, String flagKey) {
        if (authoritativeContext == null || authoritativeContext.tenantId() == null) {
            throw new IllegalArgumentException("authoritative tenant scope is required");
        }
        return store.loadForScope(snapshotId, authoritativeContext.tenantId(), authoritativeContext.workspaceId())
                .decision(flagKey);
    }

    private static String digest(FeatureFlagContext context, Map<String, FeatureFlagDecision> decisions) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((context.tenantId() + "\\0" + Objects.toString(context.workspaceId(), "") + "\\0" + decisions).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : md.digest()) out.append(String.format("%02x", b));
            return "ffs_" + out;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required for feature snapshots", impossible);
        }
    }
}

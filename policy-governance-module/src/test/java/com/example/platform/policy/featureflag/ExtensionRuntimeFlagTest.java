package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.AppFeaturesProperties;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionRuntimeFlagTest {

    private FeatureFlagService service;
    private LocalFeatureFlagProvider localProvider;

    @BeforeEach
    void setUp() {
        localProvider = new LocalFeatureFlagProvider();
        AppFeaturesProperties props = new AppFeaturesProperties();
        OpenFeatureFlagEvaluator openFeatureEvaluator = new OpenFeatureFlagEvaluator();
        service = new FeatureFlagService(localProvider, openFeatureEvaluator, props);
    }

    @Test
    void extensionRuntimeFlagCreated() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "extension.runtime.enabled", "Extension Runtime", "Controls extension runtime",
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension", "runtime"),
                null, null, false
        );
        FeatureFlagDefinition created = service.createFlag(def);
        assertEquals("extension.runtime.enabled", created.flagKey());
        assertTrue(created.enabled());
    }

    @Test
    void extensionRuntimeFlagEvaluatesCorrectly() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.eval", "Ext Runtime Eval", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension"),
                null, null, false
        ));
        localProvider.saveRule("ext.runtime.eval", new FeatureFlagTargetingRule(
                "r-ext", "ext.runtime.eval", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1", List.of(), List.of(),
                null, "api", null, null, null, Map.of());
        FeatureFlagEvaluationResult result = service.evaluate(
                new FeatureFlagEvaluationRequest("ext.runtime.eval", ctx, false));

        assertNotNull(result);
        assertNotNull(result.decision());
        assertEquals("ext.runtime.eval", result.decision().flagKey());
    }

    @Test
    void extensionRuntimeFlagDisabled() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.disabled", "Ext Runtime Disabled", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension"),
                null, null, false
        ));
        service.disableFlag("ext.runtime.disabled");

        Optional<FeatureFlagDefinition> flag = service.getFlag("ext.runtime.disabled");
        assertTrue(flag.isPresent());
        assertFalse(flag.get().enabled());
    }

    @Test
    void extensionRuntimeFlagArchived() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.archive", "Ext Runtime Archive", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension"),
                null, null, false
        ));
        service.archiveFlag("ext.runtime.archive");

        Optional<FeatureFlagDefinition> flag = service.getFlag("ext.runtime.archive");
        assertTrue(flag.isPresent());
        assertTrue(flag.get().archived());
    }

    @Test
    void extensionRuntimeFlagWithVariants() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "ext.runtime.variant", "Ext Runtime Variant", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(
                        new FeatureFlagVariant("enabled", "on", "Enabled"),
                        new FeatureFlagVariant("disabled", "off", "Disabled")
                ),
                List.of(), true, "platform", List.of("extension"),
                null, null, false
        );
        FeatureFlagDefinition created = service.createFlag(def);
        assertEquals(2, created.variants().size());
    }

    @Test
    void extensionRuntimeFlagListedForContext() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.list-1", "Ext Runtime 1", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension"),
                null, null, false
        ));
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.list-2", "Ext Runtime 2", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension"),
                null, null, false
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        List<FeatureFlagDefinition> flags = service.getFlagsForContext(ctx);

        assertTrue(flags.stream().anyMatch(f -> "ext.runtime.list-1".equals(f.flagKey())));
        assertTrue(flags.stream().anyMatch(f -> "ext.runtime.list-2".equals(f.flagKey())));
    }

    @Test
    void extensionRuntimeFlagNotListedWhenArchived() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.archived", "Ext Archived", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of("extension"),
                null, null, false
        ));
        service.archiveFlag("ext.runtime.archived");

        FeatureFlagContext ctx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        List<FeatureFlagDefinition> flags = service.getFlagsForContext(ctx);

        assertTrue(flags.stream().noneMatch(f -> "ext.runtime.archived".equals(f.flagKey())));
    }

    @Test
    void extensionRuntimeFlagWithTags() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "ext.runtime.tagged", "Ext Tagged", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform",
                List.of("extension", "runtime", "beta"),
                null, null, false
        );
        FeatureFlagDefinition created = service.createFlag(def);
        assertTrue(created.tags().contains("extension"));
        assertTrue(created.tags().contains("runtime"));
        assertTrue(created.tags().contains("beta"));
    }

    @Test
    void extensionRuntimeFlagBatchEvaluation() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.batch.1", "Batch 1", null,
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of(),
                null, null, false
        ));
        service.createFlag(new FeatureFlagDefinition(
                "ext.batch.2", "Batch 2", null,
                FeatureFlagType.BOOLEAN, false,
                List.of(), List.of(), true, "platform", List.of(),
                null, null, false
        ));

        List<FeatureFlagEvaluationRequest> requests = List.of(
                new FeatureFlagEvaluationRequest("ext.batch.1", null, true),
                new FeatureFlagEvaluationRequest("ext.batch.2", null, false)
        );
        List<FeatureFlagEvaluationResult> results = service.evaluateBatch(requests);

        assertEquals(2, results.size());
    }

    @Test
    void extensionRuntimeFlagUpdated() {
        service.createFlag(new FeatureFlagDefinition(
                "ext.runtime.update", "Ext Update", "old description",
                FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "platform", List.of(),
                null, null, false
        ));

        FeatureFlagDefinition updateDef = new FeatureFlagDefinition(
                "ext.runtime.update", "Ext Updated", "new description",
                FeatureFlagType.BOOLEAN, false,
                List.of(), List.of(), false, "platform", List.of("updated"),
                null, null, false
        );
        FeatureFlagDefinition updated = service.updateFlag("ext.runtime.update", updateDef);

        assertEquals("Ext Updated", updated.name());
        assertEquals("new description", updated.description());
    }
}

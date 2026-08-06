package com.example.platform.extension.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.api.port.PluginSelectionPolicy;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.OperationRequest;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GREEN: deterministic matching (frozen contract A092). Reconciled from the
 * authentic RED proof (PluginSelectionRedTest): the baseline had no
 * deterministic matcher; these assertions prove capability match, handled
 * object match, API compatibility, health eligibility and selection now exist.
 */
class PluginMatcherTest {

    private PluginRegistryImpl registry;
    private PluginHealthRegistry healthRegistry;
    private PluginMatcher matcher;

    @BeforeEach
    void setUp() {
        registry = new PluginRegistryImpl();
        healthRegistry = new PluginHealthRegistry();
        matcher = new PluginMatcher(registry, healthRegistry, new PluginDefaultSelectionPolicy());
    }

    private static PluginDescriptor plugin(String id, String capabilityId) {
        return new PluginDescriptor(
                id, "1.0.0", "1", "media-platform",
                List.of(new CapabilityDescriptor(
                        capabilityId, "1", "render", "RenderExecutionPlan",
                        "ArtifactReference", CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new HandledObjectDescriptor(
                        "RenderExecutionPlan", "1",
                        "com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlan",
                        List.of("profile", "timelineSnapshotId"), List.of(),
                        HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                InvocationContract.syncOnlyDefault(),
                List.of(new PermissionDescriptor("ffmpeg.execute")),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(),
                PluginGuarantee.ffmpegDefaults());
    }

    @Test
    void capabilityMatch() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        var results = matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan"));
        assertEquals(1, results.size());
        assertEquals("media.render.ffmpeg", results.get(0).pluginId());
    }

    @Test
    void capabilityVersionMatch() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        assertTrue(matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan"))
                .stream().noneMatch(r -> !r.capabilityContractVersion().equals("1")));
        // Version mismatch -> no match.
        assertTrue(matcher.match(OperationRequest.of("media.render", "2", "RenderExecutionPlan")).isEmpty());
    }

    @Test
    void handledObjectMatch() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        assertTrue(matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).size() == 1);
        assertTrue(matcher.match(OperationRequest.of("media.render", "1", "SomeOtherObject")).isEmpty());
    }

    @Test
    void platformApiCompatibility() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        assertTrue(matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).size() == 1);
    }

    @Test
    void healthEligibilityUnknownHealthyDegradedEligible() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        healthRegistry.record("media.render.ffmpeg", PluginHealth.State.UNKNOWN);
        assertEquals(1, matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).size());
        healthRegistry.record("media.render.ffmpeg", PluginHealth.State.HEALTHY);
        assertEquals(1, matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).size());
        healthRegistry.record("media.render.ffmpeg", PluginHealth.State.DEGRADED);
        assertEquals(1, matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).size());
    }

    @Test
    void healthEligibilityUnhealthyDisabledIneligible() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        healthRegistry.record("media.render.ffmpeg", PluginHealth.State.UNHEALTHY);
        assertTrue(matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).isEmpty());
        healthRegistry.record("media.render.ffmpeg", PluginHealth.State.DISABLED);
        assertTrue(matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan")).isEmpty());
    }

    @Test
    void explicitSelection() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        registry.register(plugin("media.render.second", "media.render"));
        OperationRequest request = new OperationRequest(
                "media.render", "1", "RenderExecutionPlan", null,
                new OperationRequest.SelectionPolicyContext("media.render.ffmpeg", "1.0.0"));
        var selected = matcher.select(request);
        assertEquals("media.render.ffmpeg", selected.pluginId());
    }

    @Test
    void policyPrioritySelection() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        registry.register(plugin("media.render.second", "media.render"));
        // Policy priority: second plugin higher priority.
        PluginSelectionPolicy policy = d -> d.pluginId().equals("media.render.second") ? 10 : 0;
        PluginMatcher prioritized = new PluginMatcher(registry, healthRegistry, policy);
        OperationRequest request = new OperationRequest(
                "media.render", "1", "RenderExecutionPlan", null, null);
        var selected = prioritized.select(request);
        assertEquals("media.render.second", selected.pluginId());
    }

    @Test
    void noMatchOutcome() {
        registry.register(plugin("media.render.ffmpeg", "media.render"));
        var results = matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan"));
        assertEquals(1, results.size());
        var empty = matcher.match(OperationRequest.of("missing.capability", "1", "RenderExecutionPlan"));
        assertTrue(empty.isEmpty());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> matcher.select(OperationRequest.of("missing.capability", "1", "RenderExecutionPlan")));
        assertTrue(ex.getMessage().startsWith(PluginMatcher.MTC_NO_MATCH));
    }

    @Test
    void deterministicMatchingStableOrdering() {
        registry.register(plugin("media.render.zzz", "media.render"));
        registry.register(plugin("media.render.aaa", "media.render"));
        var first = matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan"));
        var second = matcher.match(OperationRequest.of("media.render", "1", "RenderExecutionPlan"));
        assertEquals(first, second);
        // Stable identity ordering in candidate presentation.
        assertEquals("media.render.aaa", first.get(0).pluginId());
    }
}

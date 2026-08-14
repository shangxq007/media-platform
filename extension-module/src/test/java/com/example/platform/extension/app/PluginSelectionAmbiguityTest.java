package com.example.platform.extension.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.OperationRequest;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GREEN: selection and ambiguity (frozen contract A067/A068). Reconciled from
 * the authentic RED proof (PluginSelectionRedTest): the baseline could not
 * reject ambiguous provider selection; these assertions prove the frozen
 * AMBIGUOUS_SELECTION_FAILURE terminal and the prohibition of first-bean,
 * registration-order, classpath-order, filesystem-order and random selection.
 */
class PluginSelectionAmbiguityTest {

    private PluginRegistryImpl registry;
    private PluginHealthRegistry healthRegistry;
    private PluginMatcher matcher;

    @BeforeEach
    void setUp() {
        registry = new PluginRegistryImpl();
        healthRegistry = new PluginHealthRegistry();
        matcher = new PluginMatcher(registry, healthRegistry, new PluginDefaultSelectionPolicy());
    }

    private static PluginDescriptor plugin(String id, String capabilityId, String capabilityVersion) {
        return new PluginDescriptor(
                id, "1.0.0", "1", "media-platform",
                List.of(new CapabilityDescriptor(
                        capabilityId, capabilityVersion, "render", "RenderExecutionPlan",
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
    void materialAmbiguityFailsInsteadOfSilentSelection() {
        registry.register(plugin("media.render.aaa", "media.render", "1.0"));
        registry.register(plugin("media.render.bbb", "media.render", "1.0"));
        // Both candidates eligible, equal priority, distinct stable IDs:
        // material ambiguity MUST terminate with AMBIGUOUS_SELECTION_FAILURE,
        // not first-registration-wins.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> matcher.select(OperationRequest.of("media.render", "1.0", "RenderExecutionPlan")));
        assertTrue(ex.getMessage().startsWith(PluginMatcher.MTC_AMBIGUOUS));
    }

    @Test
    void ambiguityIndependentOfRegistrationOrder() {
        // Registration order reversed: same outcome (ambiguity), same candidate
        // presentation order (stable identity ordering).
        registry.register(plugin("media.render.bbb", "media.render", "1.0"));
        registry.register(plugin("media.render.aaa", "media.render", "1.0"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> matcher.select(OperationRequest.of("media.render", "1.0", "RenderExecutionPlan")));
        assertTrue(ex.getMessage().startsWith(PluginMatcher.MTC_AMBIGUOUS));
        var candidates = matcher.match(OperationRequest.of("media.render", "1.0", "RenderExecutionPlan"));
        assertEquals("media.render.aaa", candidates.get(0).pluginId());
        assertEquals("media.render.bbb", candidates.get(1).pluginId());
    }

    @Test
    void explicitRequestResolvesAmbiguity() {
        registry.register(plugin("media.render.aaa", "media.render", "1.0"));
        registry.register(plugin("media.render.bbb", "media.render", "1.0"));
        OperationRequest request = new OperationRequest(
                "media.render", "1.0", "RenderExecutionPlan", null,
                new OperationRequest.SelectionPolicyContext("media.render.bbb", "1.0.0"));
        var selected = matcher.select(request);
        assertEquals("media.render.bbb", selected.pluginId());
    }

    @Test
    void noFirstBeanOrClasspathOrFilesystemSelection() {
        registry.register(plugin("media.render.aaa", "media.render", "1.0"));
        registry.register(plugin("media.render.bbb", "media.render", "1.0"));
        // select() must not pick by any order other than the frozen pipeline;
        // with material ambiguity it must fail (proven above). The candidate
        // list is stable-ID ordered, never classpath/filesystem/bean ordered.
        var candidates = matcher.match(OperationRequest.of("media.render", "1.0", "RenderExecutionPlan"));
        assertEquals(candidates.stream().map(r -> r.pluginId()).sorted().toList(),
                candidates.stream().map(r -> r.pluginId()).toList());
    }

    @Test
    void singleCandidateSelectsDeterministically() {
        registry.register(plugin("media.render.only", "media.render", "1.0"));
        var selected = matcher.select(OperationRequest.of("media.render", "1.0", "RenderExecutionPlan"));
        assertEquals("media.render.only", selected.pluginId());
    }

    @Test
    void stableDiagnostics() {
        registry.register(plugin("media.render.aaa", "media.render", "1.0"));
        registry.register(plugin("media.render.bbb", "media.render", "1.0"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> matcher.select(OperationRequest.of("media.render", "1.0", "RenderExecutionPlan")));
        // Stable machine-readable outcome code is the message prefix.
        assertTrue(ex.getMessage().contains(PluginMatcher.MTC_AMBIGUOUS));
        assertTrue(ex.getMessage().contains("media.render.aaa"));
        assertTrue(ex.getMessage().contains("media.render.bbb"));
    }
}

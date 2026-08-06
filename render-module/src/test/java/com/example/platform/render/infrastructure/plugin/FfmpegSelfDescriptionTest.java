package com.example.platform.render.infrastructure.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.app.PluginHealthRegistry;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ToolCapability;
import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GREEN: FFmpeg self-description (frozen contract A093). Proves the first
 * plugin self-description registers by stable capability IDs (never by
 * provider-key strings), declares both frozen capabilities, the RenderExecutionPlan
 * schema-1 handled object, TRUSTED_IN_PROCESS runtime, SYNC_ONLY invocation,
 * declared permissions/resources and health derived from the existing tool
 * check. No second render provider and no execution-path change.
 */
class FfmpegSelfDescriptionTest {

    private PluginRegistryImpl registry;
    private PluginHealthRegistry healthRegistry;
    private ToolRegistry toolRegistry;
    private FfmpegRenderToolSelfDescription contributor;

    @BeforeEach
    void setUp() {
        healthRegistry = new PluginHealthRegistry();
        registry = new PluginRegistryImpl(
                new com.example.platform.extension.app.PluginDescriptorValidator(),
                healthRegistry);
        toolRegistry = new ToolRegistry();
        contributor = new FfmpegRenderToolSelfDescription(registry, healthRegistry, toolRegistry);
    }

    private void registerFfmpegTool(boolean available) {
        String path = available ? "/usr/bin/ffmpeg" : "/nonexistent/ffmpeg";
        toolRegistry.registerExecutable("ffmpeg", path);
        toolRegistry.registerTool(new ToolDefinition(
                "ffmpeg", "FFmpeg", "media tool", path,
                List.of(new ToolCapability("h264", "h264"), new ToolCapability("subtitle-burn", "subtitle burn-in")),
                ToolSandboxPolicy.defaults()));
    }

    @Test
    void pluginRegisteredByCapabilityIdNotKeyString() {
        contributor.registerSelfDescription();
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        // Registers by capability ID, not by concrete provider-key branching.
        assertTrue(descriptor.capabilities().stream()
                .anyMatch(c -> c.capabilityId().equals("media.render")));
        assertTrue(descriptor.capabilities().stream()
                .anyMatch(c -> c.capabilityId().equals("subtitle.burn-in")));
        assertFalse(descriptor.capabilities().stream()
                .anyMatch(c -> c.capabilityId().equals("ffmpeg")));
    }

    @Test
    void mediaRenderAndSubtitleBurnInDiscoverable() {
        contributor.registerSelfDescription();
        assertEquals(1, registry.findCapabilityCandidates("media.render", "1").size());
        assertEquals(1, registry.findCapabilityCandidates("subtitle.burn-in", "1").size());
        assertEquals("media.render.ffmpeg",
                registry.findCapabilityCandidates("media.render", "1").get(0).pluginId());
    }

    @Test
    void renderExecutionPlanSchema1Handled() {
        contributor.registerSelfDescription();
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        assertEquals(1, descriptor.handledObjects().size());
        assertEquals("RenderExecutionPlan", descriptor.handledObjects().get(0).objectTypeId());
        assertEquals("1", descriptor.handledObjects().get(0).schemaVersion());
        assertEquals("com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlan",
                descriptor.handledObjects().get(0).javaBoundaryType());
    }

    @Test
    void trustedInProcessAndSyncOnlyDeclared() {
        contributor.registerSelfDescription();
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        assertEquals(PluginRuntimeRequirement.RuntimeMode.TRUSTED_IN_PROCESS,
                descriptor.runtimeRequirements().runtime());
        assertEquals(com.example.platform.extension.domain.ExtensionTrustLevel.FULLY_TRUSTED,
                descriptor.runtimeRequirements().trustLevel());
        assertTrue(descriptor.invocationContract().synchronous());
        assertEquals(com.example.platform.extension.domain.InvocationContract.TimeoutClassification.BOUNDED_DEFAULT_60S,
                descriptor.invocationContract().timeoutClassification());
    }

    @Test
    void permissionsDeclared() {
        contributor.registerSelfDescription();
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        var permissionIds = descriptor.permissions().stream()
                .map(p -> p.permissionId()).toList();
        assertTrue(permissionIds.contains("ffmpeg.execute"));
        assertTrue(permissionIds.contains("temporary-file.write"));
        assertTrue(permissionIds.contains("asset.read"));
        assertTrue(permissionIds.contains("storage.read"));
        assertTrue(permissionIds.contains("cpu.use"));
        assertTrue(permissionIds.contains("memory.use"));
        assertTrue(permissionIds.contains("font.read"));
    }

    @Test
    void resourcesDeclared() {
        contributor.registerSelfDescription();
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        var resources = descriptor.resourceRequirements();
        assertFalse(resources.gpu());
        assertFalse(resources.networkAllowed());
        assertEquals(60_000L, resources.timeoutMs());
        assertTrue(resources.boundsValid());
    }

    @Test
    void guaranteesFalseByDefault() {
        contributor.registerSelfDescription();
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        assertFalse(descriptor.guarantees().producesCanonicalTimeline());
        assertFalse(descriptor.guarantees().producesReadyProduct());
        assertTrue(descriptor.guarantees().legal());
    }

    @Test
    void healthDerivedFromExistingToolCheck() {
        registerFfmpegTool(true);
        contributor.registerSelfDescription();
        PluginHealth health = registry.healthOf("media.render.ffmpeg");
        assertEquals(PluginHealth.State.HEALTHY, health.state());
    }

    @Test
    void healthUnknownWhenToolNotRegistered() {
        contributor.registerSelfDescription();
        PluginHealth health = registry.healthOf("media.render.ffmpeg");
        assertEquals(PluginHealth.State.UNKNOWN, health.state());
        assertTrue(health.eligible());
    }

    @Test
    void descriptorValidAndRegisteredOnce() {
        registerFfmpegTool(true);
        contributor.registerSelfDescription();
        // Registration performs no render execution and no second provider;
        // descriptor registered exactly once.
        assertEquals(1, registry.enumerate().size());
        var descriptor = registry.findByPluginId("media.render.ffmpeg").orElseThrow();
        assertEquals("1.0.0", descriptor.pluginVersion());
        assertEquals("1", descriptor.platformApiVersion());
    }
}

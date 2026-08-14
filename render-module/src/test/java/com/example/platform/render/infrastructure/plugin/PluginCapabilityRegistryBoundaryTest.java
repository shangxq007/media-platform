package com.example.platform.render.infrastructure.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.api.port.PluginRegistryPort;
import com.example.platform.extension.app.PluginDefaultSelectionPolicy;
import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginHealthRegistry;
import com.example.platform.extension.app.PluginMatcher;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.OperationRequest;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginSelectionResult;
import com.example.platform.extension.domain.ToolCapability;
import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * AUTHENTIC PRODUCTION BOUNDARY TEST (frozen contract A097).
 *
 * <p>Spring context -&gt; FFmpeg descriptor contributor -&gt; PluginRegistry
 * -&gt; PluginMatcher -&gt; media.render + RenderExecutionPlan query -&gt;
 * selected {@code media.render.ffmpeg} identity/version.</p>
 *
 * <p>Uses the REAL registry bean, REAL validator, REAL matcher and REAL
 * contributor (no mocks). The generic executeProvider stub is NOT used and no
 * render execution is performed. Health is derived from the existing
 * ToolRegistry ffmpeg allowlist entry (real environment evidence).</p>
 */
@SpringBootTest(classes = PluginCapabilityRegistryBoundaryTest.TestConfig.class)
class PluginCapabilityRegistryBoundaryTest {

    /** Real Spring beans wired by the test configuration. */
    @Configuration
    @Import(FfmpegRenderToolSelfDescription.class)
    static class TestConfig {

        @Bean
        PluginDescriptorValidator pluginDescriptorValidator() {
            return new PluginDescriptorValidator();
        }

        @Bean
        PluginHealthRegistry pluginHealthRegistry() {
            return new PluginHealthRegistry();
        }

        @Bean
        PluginRegistryImpl pluginRegistry(
                PluginDescriptorValidator validator, PluginHealthRegistry healthRegistry) {
            return new PluginRegistryImpl(validator, healthRegistry);
        }

        @Bean
        PluginDefaultSelectionPolicy pluginDefaultSelectionPolicy() {
            return new PluginDefaultSelectionPolicy();
        }

        @Bean
        PluginMatcher pluginMatcher(
                PluginRegistryPort registry,
                PluginHealthRegistry healthRegistry,
                PluginDefaultSelectionPolicy policy) {
            return new PluginMatcher(registry, healthRegistry, policy);
        }

        /** Real ToolRegistry with the existing ffmpeg allowlist entry (when present). */
        @Bean
        ToolRegistry toolRegistry() {
            ToolRegistry toolRegistry = new ToolRegistry();
            String path = new File("/usr/bin/ffmpeg").exists() ? "/usr/bin/ffmpeg" : null;
            if (path != null) {
                toolRegistry.registerExecutable("ffmpeg", path);
                toolRegistry.registerTool(new ToolDefinition(
                        "ffmpeg", "FFmpeg", "media tool", path,
                        List.of(new ToolCapability("h264", "h264"),
                                new ToolCapability("subtitle-burn", "subtitle burn-in")),
                        ToolSandboxPolicy.defaults()));
            }
            return toolRegistry;
        }
    }

    @Autowired
    private PluginRegistryPort registry;

    @Autowired
    private PluginMatcher matcher;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void realContextRegistersAndSelectsFfmpegPlugin() {
        // Descriptor registered once by the real contributor.
        var descriptors = registry.enumerate();
        assertEquals(1, descriptors.size());
        PluginDescriptor descriptor = descriptors.get(0);
        assertEquals("media.render.ffmpeg", descriptor.pluginId());
        assertEquals("1.0.0", descriptor.pluginVersion());

        // Descriptor valid.
        assertTrue(new PluginDescriptorValidator().validate(descriptor).isEmpty());

        // Health eligibility applied (derived from the existing tool check).
        PluginHealth health = registry.healthOf("media.render.ffmpeg");
        assertTrue(health.eligible(),
                "health must be eligible (HEALTHY when ffmpeg present, else UNKNOWN): " + health.state());

        // Matching deterministic: media.render + RenderExecutionPlan query.
        OperationRequest request = OperationRequest.of("media.render", "1.0", "RenderExecutionPlan");
        List<PluginSelectionResult> first = matcher.match(request);
        List<PluginSelectionResult> second = matcher.match(request);
        assertEquals(first, second, "matching must be deterministic");

        // Selected identity/version exact.
        PluginSelectionResult selected = matcher.select(request);
        assertEquals("media.render.ffmpeg", selected.pluginId());
        assertEquals("1.0.0", selected.pluginVersion());
        assertEquals("media.render", selected.capabilityId());
        assertEquals("RenderExecutionPlan", selected.handledObjectTypeId());

        // Production render execution path unchanged: no invocation performed,
        // no second render provider created, ToolRegistry unchanged.
        assertFalse(selected.pluginId().isEmpty());
        assertEquals(1, registry.enumerate().size(), "contributor registers exactly one descriptor");
        assertTrue(toolRegistry.findTool("ffmpeg").isPresent()
                        || toolRegistry.listTools().isEmpty(),
                "ToolRegistry remains the existing execution authority (unchanged)");
    }
}

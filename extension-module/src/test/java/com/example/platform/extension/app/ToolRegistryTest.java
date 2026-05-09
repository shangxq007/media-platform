package com.example.platform.extension.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolCapability;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.extension.domain.ToolEnvironmentReport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void shouldRegisterAndResolveAllowedExecutable() {
        registry.registerExecutable("ffmpeg", "/usr/bin/ffmpeg");
        assertTrue(registry.isAllowedExecutable("/usr/bin/ffmpeg"));
    }

    @Test
    void shouldRejectNonAbsoluteExecutablePath() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("ffmpeg", "ffmpeg"));
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("evil", "/usr/bin/../../../bin/sh"));
    }

    @Test
    void shouldRegisterToolWithAllowedExecutable() {
        registry.registerExecutable("ffmpeg", "/usr/bin/ffmpeg");
        ToolDefinition tool = new ToolDefinition(
                "ffmpeg", "FFmpeg", "Video processing tool",
                "/usr/bin/ffmpeg",
                List.of(new ToolCapability("h264", "H.264 encoding")),
                ToolSandboxPolicy.defaults());
        registry.registerTool(tool);

        assertTrue(registry.findTool("ffmpeg").isPresent());
        assertEquals("/usr/bin/ffmpeg", registry.resolveExecutable("ffmpeg"));
    }

    @Test
    void shouldRejectToolWithUnregisteredExecutable() {
        ToolDefinition tool = new ToolDefinition(
                "ffmpeg", "FFmpeg", "Video processing tool",
                "/opt/ffmpeg/bin/ffmpeg",
                List.of(new ToolCapability("h264", "H.264 encoding")),
                ToolSandboxPolicy.defaults());
        assertThrows(IllegalArgumentException.class, () -> registry.registerTool(tool));
    }

    @Test
    void shouldReturnEmptyForUnknownTool() {
        assertTrue(registry.findTool("nonexistent").isEmpty());
    }

    @Test
    void shouldReportEnvironmentAvailability() {
        registry.registerExecutable("ffmpeg", "/usr/bin/ffmpeg");
        ToolDefinition tool = new ToolDefinition(
                "ffmpeg", "FFmpeg", "Video processing tool",
                "/usr/bin/ffmpeg",
                List.of(new ToolCapability("h264", "H.264 encoding")),
                ToolSandboxPolicy.defaults());
        registry.registerTool(tool);

        ToolEnvironmentReport report = registry.validateEnvironment();
        assertNotNull(report);
        assertNotNull(report.tools());
        assertEquals(1, report.tools().size());
        // /usr/bin/ffmpeg may or may not exist in test environment
        ToolEnvironmentReport.ToolAvailability availability = report.tools().get(0);
        assertEquals("ffmpeg", availability.toolKey());
    }

    @Test
    void shouldListAllRegisteredTools() {
        registry.registerExecutable("ffmpeg", "/usr/bin/ffmpeg");
        registry.registerExecutable("melt", "/usr/bin/melt");
        registry.registerTool(new ToolDefinition(
                "ffmpeg", "FFmpeg", "Video tool", "/usr/bin/ffmpeg",
                List.of(), ToolSandboxPolicy.defaults()));
        registry.registerTool(new ToolDefinition(
                "melt", "MLT melt", "Timeline renderer", "/usr/bin/melt",
                List.of(), ToolSandboxPolicy.defaults()));

        List<ToolDefinition> tools = registry.listTools();
        assertEquals(2, tools.size());
    }

    @Test
    void shouldRejectNullPath() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("bad", null));
    }

    @Test
    void shouldRejectBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("bad", "  "));
    }
}

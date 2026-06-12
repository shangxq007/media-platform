package com.example.platform.extension.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolCapability;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.extension.domain.ToolEnvironmentReport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/usr/bin/../../../bin/sh",
            "/opt/..%2F..%2Fbin/sh",
            "/opt/%2e%2e/bin/sh",
            "/usr/bin/evil\\..\\sh",
    })
    void shouldRejectEncodedTraversal(String path) {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("evil", path));
    }

    @Test
    void shouldRejectNullByteInPath() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("evil", "/usr/bin/ffmpeg\0"));
    }

    @Test
    void shouldRejectBackslashInPath() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerExecutable("evil", "/usr/bin/..\\..\\sh"));
    }

    @Test
    void shouldAcceptValidAbsolutePaths() {
        registry.registerExecutable("ffmpeg", "/usr/bin/ffmpeg");
        registry.registerExecutable("melt", "/usr/local/bin/melt");
        assertTrue(registry.isAllowedExecutable("/usr/bin/ffmpeg"));
        assertTrue(registry.isAllowedExecutable("/usr/local/bin/melt"));
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

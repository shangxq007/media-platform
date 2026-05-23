package com.example.platform.extension.config;

import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads {@link CliToolsProperties} into {@link ToolRegistry} at startup.
 *
 * <p>Also registers pass-through tools for each configured executable key so render
 * providers can invoke {@code ffmpeg}, {@code melt}, {@code gst-launch-1.0}, and
 * {@code MP4Box} directly.</p>
 */
@Configuration
public class CliToolRegistryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CliToolRegistryBootstrap.class);

    private static final List<String> RENDER_TOOL_KEYS =
            List.of("ffmpeg", "ffprobe", "melt", "gst-launch-1.0", "MP4Box");

    @Bean
    CommandLineRunner registerCliTools(ToolRegistry registry, CliToolsProperties properties) {
        return args -> {
            for (Map.Entry<String, String> entry : properties.getExecutables().entrySet()) {
                registry.registerExecutable(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, CliToolsProperties.Recipe> entry : properties.getTools().entrySet()) {
                String toolKey = entry.getKey();
                CliToolsProperties.Recipe recipe = entry.getValue();
                String executablePath = properties.getExecutables().get(recipe.getExecutableKey());
                if (executablePath == null) {
                    log.warn("Skipping CLI tool '{}': unknown executable-key '{}'",
                            toolKey, recipe.getExecutableKey());
                    continue;
                }
                registry.registerTool(new ToolDefinition(
                        toolKey,
                        toolKey,
                        "Configured CLI recipe",
                        executablePath,
                        List.of(),
                        ToolSandboxPolicy.defaults()));
            }

            for (String toolKey : RENDER_TOOL_KEYS) {
                if (registry.findTool(toolKey).isPresent()) {
                    continue;
                }
                String executablePath = properties.getExecutables().get(toolKey);
                if (executablePath == null) {
                    continue;
                }
                registry.registerTool(new ToolDefinition(
                        toolKey,
                        toolKey,
                        "Render media CLI",
                        executablePath,
                        List.of(),
                        ToolSandboxPolicy.defaults()));
                log.info("Auto-registered pass-through render tool: {}", toolKey);
            }
        };
    }
}

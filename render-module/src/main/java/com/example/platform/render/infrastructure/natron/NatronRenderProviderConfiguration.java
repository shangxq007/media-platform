package com.example.platform.render.infrastructure.natron;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "render.providers.natron", name = "enabled", havingValue = "true")
public class NatronRenderProviderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NatronRenderProviderConfiguration.class);

    @Bean
    NatronRenderProvider natronRenderProvider(ProcessToolRunner processToolRunner,
                                              NatronPocJobExtractor jobExtractor,
                                              NatronPocCommandBuilder commandBuilder,
                                              NatronBatchScriptGenerator batchScriptGenerator,
                                              NatronRenderDurationResolver durationResolver,
                                              NatronRenderProviderProperties properties) {
        return new NatronRenderProvider(processToolRunner, jobExtractor, commandBuilder,
                batchScriptGenerator, durationResolver, properties);
    }

    @Bean
    CommandLineRunner natronPocToolRegistration(
            ToolRegistry toolRegistry,
            NatronPocScriptResolver scriptResolver,
            NatronRendererBinaryResolver rendererResolver,
            NatronRenderProviderProperties properties,
            @Value("${app.storage.local-root:/tmp/platform}") String storageRoot) {
        return args -> {
            String scriptPath = scriptResolver.resolve(properties, storageRoot);
            toolRegistry.registerExecutable(NatronPocCommandBuilder.TOOL_KEY, scriptPath);
            if (toolRegistry.findTool(NatronPocCommandBuilder.TOOL_KEY).isEmpty()) {
                toolRegistry.registerTool(new ToolDefinition(
                        NatronPocCommandBuilder.TOOL_KEY,
                        "Natron POC render",
                        "Natron batch script + FFmpeg fallback",
                        scriptPath,
                        List.of(),
                        ToolSandboxPolicy.defaults()));
            }
            log.info("Registered Natron POC tool at {}", scriptPath);

            rendererResolver.resolveAbsolutePath(properties).ifPresent(rendererPath -> {
                toolRegistry.registerExecutable(NatronRendererBinaryResolver.TOOL_KEY, rendererPath);
                if (toolRegistry.findTool(NatronRendererBinaryResolver.TOOL_KEY).isEmpty()) {
                    toolRegistry.registerTool(new ToolDefinition(
                            NatronRendererBinaryResolver.TOOL_KEY,
                            "NatronRenderer",
                            "Natron headless renderer",
                            rendererPath,
                            List.of(),
                            ToolSandboxPolicy.defaults()));
                }
                log.info("Registered NatronRenderer at {}", rendererPath);
            });
        };
    }

}

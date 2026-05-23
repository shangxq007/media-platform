package com.example.platform.render.infrastructure.blender;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "render.providers.blender", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BlenderRenderProviderProperties.class)
public class BlenderRenderProviderConfiguration {

    @Bean
    BlenderRenderProvider blenderRenderProvider(ProcessToolRunner processToolRunner,
                                                BlenderRenderProviderProperties properties,
                                                TimelineScriptParser timelineScriptParser) {
        return new BlenderRenderProvider(processToolRunner, properties, timelineScriptParser);
    }
}

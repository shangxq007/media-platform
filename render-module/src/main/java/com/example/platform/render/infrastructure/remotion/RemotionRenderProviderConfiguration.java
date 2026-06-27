package com.example.platform.render.infrastructure.remotion;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.font.RenderJobFontPreflight;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "render.providers.remotion", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RemotionRenderProviderProperties.class, RemotionWorkerProperties.class})
public class RemotionRenderProviderConfiguration {

    @Bean
    RemotionRenderProvider remotionRenderProvider(ProcessToolRunner processToolRunner,
                                                    RemotionRenderProviderProperties properties,
                                                    TimelineScriptParser timelineScriptParser,
                                                    RenderJobFontPreflight fontPreflight) {
        return new RemotionRenderProvider(processToolRunner, properties, timelineScriptParser, fontPreflight);
    }
}

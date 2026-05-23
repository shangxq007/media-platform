package com.example.platform.render.infrastructure.vapoursynth;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VapourSynthRenderProviderProperties.class)
@ConditionalOnProperty(prefix = "render.providers.vapoursynth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VapourSynthRenderProviderConfiguration {

    @Bean
    VapourSynthRenderProvider vapourSynthRenderProvider(ProcessToolRunner processToolRunner,
                                                        VapourSynthRenderProviderProperties properties,
                                                        TimelineScriptParser timelineScriptParser) {
        return new VapourSynthRenderProvider(processToolRunner, properties, timelineScriptParser);
    }
}

package com.example.platform.render.infrastructure.shotstack;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "render.providers.shotstack", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShotstackRenderProviderProperties.class)
public class ShotstackRenderProviderConfiguration {

    @Bean
    ShotstackApiClient shotstackApiClient(ShotstackRenderProviderProperties properties) {
        return new ShotstackApiClient(properties);
    }

    @Bean
    ShotstackRenderProvider shotstackRenderProvider(ShotstackTimelineMapper timelineMapper,
                                                    ShotstackApiClient apiClient,
                                                    ShotstackRenderProviderProperties properties,
                                                    com.example.platform.render.domain.timeline.TimelineScriptParser timelineScriptParser,
                                                    com.example.platform.render.infrastructure.MediaProbeService mediaProbeService) {
        return new ShotstackRenderProvider(timelineMapper, apiClient, properties,
                timelineScriptParser, mediaProbeService);
    }
}

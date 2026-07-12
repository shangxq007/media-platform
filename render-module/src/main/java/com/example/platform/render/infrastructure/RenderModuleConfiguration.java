package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.effects.EffectFilterGraphBuilder;
import com.example.platform.render.app.RenderWorkerQueueProperties;
import com.example.platform.render.app.storage.RenderOutputStorageProperties;
import com.example.platform.render.domain.environment.OpenCueProperties;
import com.example.platform.render.infrastructure.bento4.Bento4PackagingProviderProperties;
import com.example.platform.render.infrastructure.natron.NatronRenderProviderProperties;
import com.example.platform.render.infrastructure.shotstack.ShotstackRenderProviderProperties;
import com.example.platform.render.infrastructure.ffmpeg.FFmpegCommandFactory;
import com.example.platform.render.infrastructure.mlt.MLTCommandFactory;
import com.example.platform.render.infrastructure.mlt.MltProjectXmlBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        com.example.platform.render.app.timeline.compile.TimelineRenderExecutionProperties.class,
        RenderProviderProperties.class,
        NatronRenderProviderProperties.class,
        Bento4PackagingProviderProperties.class,
        ShotstackRenderProviderProperties.class,
        RenderWorkerQueueProperties.class,
        RenderCacheProperties.class,
        TimelineAssetGcProperties.class,
        OpenCueProperties.class,
        RenderOutputStorageProperties.class
})
public class RenderModuleConfiguration {

    @Bean
    FFmpegCommandFactory ffmpegCommandFactory(EffectFilterGraphBuilder effectFilterGraphBuilder) {
        FFmpegCommandFactory factory = new FFmpegCommandFactory();
        factory.setEffectFilterGraphBuilder(effectFilterGraphBuilder);
        return factory;
    }

    @Bean
    MltProjectXmlBuilder mltProjectXmlBuilder() {
        return new MltProjectXmlBuilder();
    }

    @Bean
    MLTCommandFactory mltCommandFactory() {
        return new MLTCommandFactory();
    }
}

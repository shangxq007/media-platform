package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.ffmpeg.FFmpegRenderProvider;
import com.example.platform.render.infrastructure.gpac.GPACRenderProvider;
import com.example.platform.render.infrastructure.gpac.GPACPackagingProvider;
import com.example.platform.render.infrastructure.gstreamer.GStreamerRenderProvider;
import com.example.platform.render.infrastructure.mlt.MltRenderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(RenderProviderProperties.class)
public class RenderProviderAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderAutoConfiguration.class);

    @Bean
    CommandLineRunner registerProviders(RenderProviderRegistry registry,
                                         JavaCVRenderProvider javacvProvider,
                                         OFXRenderProvider ofxProvider,
                                         MockRenderProvider mockProvider,
                                         RenderProviderProperties properties,
                                         Optional<MltRenderProvider> mltProvider,
                                         Optional<FFmpegRenderProvider> ffmpegProvider,
                                         Optional<GStreamerRenderProvider> gstreamerProvider,
                                         Optional<GPACRenderProvider> gpacRenderProvider,
                                         Optional<GPACPackagingProvider> gpacPackagingProvider) {
        return args -> {
            registry.register("javacv", javacvProvider, javacvProvider.getCapability());
            registry.register("ofx", ofxProvider, ofxProvider.getCapability());
            registry.register("mock", mockProvider, new RenderProviderCapability(
                    "mock",
                    Set.of("mp4"),
                    Set.of("h264", "aac"),
                    Set.of("video.fade_in", "video.fade_out", "text.subtitle_burn_in", "audio.volume"),
                    Set.of("dissolve"),
                    Set.of("burn_in"),
                    "1920x1080",
                    false,
                    false,
                    true,
                    Set.of("test_mock")
            ));

            mltProvider.ifPresent(mlt -> {
                registry.register("mlt", mlt, new RenderProviderCapability(
                        "mlt",
                        Set.of("mp4", "webm"),
                        Set.of("h264", "aac"),
                        Set.of("timeline", "multi-track", "transitions", "compositing"),
                        Set.of("dissolve", "wipe", "slide"),
                        Set.of("burn_in"),
                        "1920x1080",
                        true,
                        false,
                        false,
                        Set.of("social_1080p", "social_720p", "default_1080p", "default_720p")
                ));
                log.info("MLT render provider registered");
            });

            ffmpegProvider.ifPresent(ffmpeg -> {
                registry.register("ffmpeg", ffmpeg, new RenderProviderCapability(
                        "ffmpeg",
                        Set.of("mp4", "webm", "mkv", "mov"),
                        Set.of("h264", "h265", "vp9", "av1", "aac", "mp3"),
                        Set.of("video.fade_in", "video.fade_out", "video.watermark",
                                "text.subtitle_burn_in", "audio.volume", "video.thumbnail",
                                "video.probe", "video.dash", "video.hls"),
                        Set.of("dissolve", "fade_in", "fade_out"),
                        Set.of("burn_in"),
                        "3840x2160",
                        true,
                        false,
                        false,
                        Set.of("social_1080p", "social_720p", "default_1080p", "default_720p",
                                "broadcast_4k", "proxy_480p")
                ));
                log.info("FFmpeg render provider registered");
            });

            gstreamerProvider.ifPresent(gstreamer -> {
                registry.register("gstreamer", gstreamer, new RenderProviderCapability(
                        "gstreamer",
                        Set.of("mp4", "webm"),
                        Set.of("h264", "aac"),
                        Set.of("pipeline", "real-time", "streaming", "multi-track",
                                "compositing", "subtitle-overlay", "filter-graph"),
                        Set.of("dissolve"),
                        Set.of("overlay"),
                        "1920x1080",
                        true,
                        false,
                        false,
                        Set.of("default_1080p", "default_720p", "social_1080p", "social_720p",
                                "gstreamer_1080p", "gstreamer_720p")
                ));
                log.info("GStreamer render provider registered");
            });

            gpacRenderProvider.ifPresent(gpac -> {
                registry.register("gpac", gpac, new RenderProviderCapability(
                        "gpac",
                        Set.of("mp4", "mpd", "m3u8"),
                        Set.of("h264", "aac"),
                        Set.of("mp4", "dash", "hls", "cmaf", "faststart",
                                "multi-track", "subtitle-track"),
                        Set.of(),
                        Set.of("burn_in"),
                        "1920x1080",
                        true,
                        false,
                        false,
                        Set.of("default_1080p", "default_720p", "social_1080p", "social_720p",
                                "gpac_dash", "gpac_hls", "gpac_cmaf")
                ));
                log.info("GPAC render provider registered");
            });

            gpacPackagingProvider.ifPresent(gpac -> {
                log.info("GPAC packaging provider registered (packaging mode)");
            });

            for (RenderProviderCapability cap : registry.getAllCapabilities()) {
                RenderProvider provider = registry.getProvider(cap.providerKey()).orElse(null);
                if (provider != null) {
                    long start = System.currentTimeMillis();
                    RenderProvider.EnvironmentValidationResult envResult = provider.validateEnvironment();
                    long latency = System.currentTimeMillis() - start;
                    RenderProviderHealthCheck health = envResult.valid()
                            ? RenderProviderHealthCheck.ok(cap.providerKey(), latency)
                            : RenderProviderHealthCheck.failed(cap.providerKey(), envResult.message());
                    registry.updateHealthCheck(cap.providerKey(), health);
                    log.info("Provider '{}' health: {} ({}ms)", cap.providerKey(), envResult.valid(), latency);
                }
            }

            log.info("Render provider registry initialized with {} providers", registry.getAllProviders().size());
            log.info("Available effects: {}", registry.getAvailableEffects());
        };
    }
}

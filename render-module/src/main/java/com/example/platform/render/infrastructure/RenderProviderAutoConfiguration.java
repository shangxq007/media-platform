package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.ffmpeg.FFmpegRenderProvider;
import com.example.platform.render.infrastructure.gpac.GPACRenderProvider;
import com.example.platform.render.infrastructure.gpac.GPACPackagingProvider;
import com.example.platform.render.infrastructure.gstreamer.GStreamerRenderProvider;
import com.example.platform.render.infrastructure.mlt.MltRenderProvider;
import com.example.platform.render.infrastructure.bento4.Bento4PackagingProvider;
import com.example.platform.render.infrastructure.natron.NatronRenderProvider;
import com.example.platform.render.infrastructure.natron.NatronRenderProviderProperties;
import com.example.platform.render.infrastructure.shotstack.ShotstackRenderProvider;
import com.example.platform.render.infrastructure.libass.LibassOverlayRenderProvider;
import com.example.platform.render.infrastructure.remotion.RemotionRenderProvider;
import com.example.platform.render.infrastructure.blender.BlenderRenderProvider;
import com.example.platform.render.infrastructure.vapoursynth.VapourSynthRenderProvider;
import com.example.platform.render.infrastructure.shaka.ShakaPackagingProvider;
import com.example.platform.render.infrastructure.skia.SkiaStickerOverlayProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(RenderProviderProperties.class)
public class RenderProviderAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderAutoConfiguration.class);

    @Bean
    CommandLineRunner registerProviders(RenderProviderRegistry registry,
                                         Optional<JavaCVRenderProvider> javacvProvider,
                                         Optional<OFXRenderProvider> ofxProvider,
                                         Optional<MockRenderProvider> mockProvider,
                                         RenderProviderProperties properties,
                                         Optional<MltRenderProvider> mltProvider,
                                         Optional<FFmpegRenderProvider> ffmpegProvider,
                                         Optional<GStreamerRenderProvider> gstreamerProvider,
                                         Optional<GPACRenderProvider> gpacRenderProvider,
                                         Optional<GPACPackagingProvider> gpacPackagingProvider,
                                         Optional<NatronRenderProvider> natronProvider,
                                         Optional<NatronRenderProviderProperties> natronProperties,
                                         Optional<Bento4PackagingProvider> bento4PackagingProvider,
                                         Optional<ShotstackRenderProvider> shotstackProvider,
                                         Optional<LibassOverlayRenderProvider> libassProvider,
                                         Optional<RemotionRenderProvider> remotionProvider,
                                         Optional<BlenderRenderProvider> blenderProvider,
                                         Optional<ShakaPackagingProvider> shakaPackagingProvider,
                                         Optional<SkiaStickerOverlayProvider> skiaProvider,
                                         Optional<VapourSynthRenderProvider> vapourSynthProvider) {
        return args -> {
            javacvProvider.ifPresent(p -> registry.register("javacv", p, p.getCapability()));
            ofxProvider.ifPresent(p -> registry.register("ofx", p, p.getCapability()));
            mockProvider.ifPresent(p -> {
                registry.register("mock", p, new RenderProviderCapability(
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
                        Set.of("test_mock")));
            });

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

            gpacPackagingProvider.ifPresent(gpac ->
                    log.info("GPAC packaging provider registered (packaging mode)"));
            bento4PackagingProvider.ifPresent(b4 ->
                    log.info("Bento4 packaging provider registered (dash/hls/dash_drm)"));

            natronProvider.ifPresent(natron -> {
                var effectKeys = natronProperties
                        .map(NatronRenderProviderProperties::getSupportedEffectKeys)
                        .orElse(List.of("video.natron_vignette"));
                registry.register("natron", natron, new RenderProviderCapability(
                        "natron",
                        Set.of("mp4"),
                        Set.of("h264", "aac"),
                        new java.util.LinkedHashSet<>(effectKeys),
                        Set.of(),
                        Set.of(),
                        "1920x1080",
                        true,
                        false,
                        true,
                        Set.of("natron_poc_1080p", "natron_poc_720p")));
                log.info("Natron render provider registered (effects: {})", effectKeys);
            });

            shotstackProvider.ifPresent(shotstack -> registry.register("shotstack", shotstack,
                    new RenderProviderCapability(
                            "shotstack",
                            Set.of("mp4"),
                            Set.of("h264", "aac"),
                            Set.of("video.shotstack_template"),
                            Set.of(),
                            Set.of(),
                            "1920x1080",
                            true,
                            false,
                            true,
                            Set.of("shotstack_social_1080p", "shotstack_social_720p"))));
            shotstackProvider.ifPresent(s ->
                    log.info("Shotstack cloud render provider registered"));

            libassProvider.ifPresent(libass -> registry.register("libass", libass, libass.getCapability()));
            libassProvider.ifPresent(l -> log.info("Libass L6 subtitle provider registered"));

            remotionProvider.ifPresent(remotion -> registry.register("remotion", remotion,
                    new RenderProviderCapability(
                            "remotion",
                            Set.of("mp4"),
                            Set.of("h264", "aac"),
                            Set.of("video.remotion_template"),
                            Set.of(),
                            Set.of(),
                            "1920x1080",
                            true,
                            false,
                            true,
                            Set.of("remotion_1080p", "remotion_social"))));
            remotionProvider.ifPresent(r -> log.info("Remotion L3 worker registered"));

            blenderProvider.ifPresent(blender -> registry.register("blender", blender,
                    new RenderProviderCapability(
                            "blender",
                            Set.of("mp4"),
                            Set.of("h264"),
                            Set.of("video.blender_scene"),
                            Set.of(),
                            Set.of(),
                            "3840x2160",
                            true,
                            false,
                            true,
                            Set.of("blender_1080p", "blender_4k"))));
            blenderProvider.ifPresent(b -> log.info("Blender L4 worker registered"));

            vapourSynthProvider.ifPresent(vs -> registry.register("vapoursynth", vs,
                    new RenderProviderCapability(
                            "vapoursynth",
                            Set.of("mp4"),
                            Set.of("h264"),
                            Set.of("video.vapoursynth_preprocess", "video.denoise", "video.deinterlace"),
                            Set.of(),
                            Set.of(),
                            "1920x1080",
                            true,
                            false,
                            true,
                            Set.of("default_1080p", "social_1080p"))));
            vapourSynthProvider.ifPresent(v -> log.info("VapourSynth external render worker registered"));

            shakaPackagingProvider.ifPresent(shaka ->
                    log.info("Shaka Packager registered (L7 dash)"));

            skiaProvider.ifPresent(skia -> registry.register("skia", skia, skia.getCapability()));
            skiaProvider.ifPresent(s -> log.info("Skia L6 sticker overlay provider registered"));

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

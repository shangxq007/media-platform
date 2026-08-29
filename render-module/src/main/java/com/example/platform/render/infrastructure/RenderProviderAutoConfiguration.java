package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.gpac.GPACPackagingProvider;
import com.example.platform.render.infrastructure.gstreamer.GStreamerRenderProvider;
import com.example.platform.render.infrastructure.mlt.MltRenderProvider;
import com.example.platform.render.infrastructure.bento4.Bento4PackagingProvider;
import com.example.platform.render.infrastructure.natron.NatronRenderProvider;
import com.example.platform.render.infrastructure.natron.NatronRenderProviderProperties;
import com.example.platform.render.infrastructure.shotstack.ShotstackRenderProvider;
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
                                        
                                        
                                        RenderProviderProperties properties,
                                        Optional<MltRenderProvider> mltProvider,
                                         Optional<GStreamerRenderProvider> gstreamerProvider,
                                         
                                         Optional<GPACPackagingProvider> gpacPackagingProvider,
                                         Optional<NatronRenderProvider> natronProvider,
                                         Optional<NatronRenderProviderProperties> natronProperties,
                                         Optional<Bento4PackagingProvider> bento4PackagingProvider,
                                         Optional<ShotstackRenderProvider> shotstackProvider,
                                         Optional<RemotionRenderProvider> remotionProvider,
                                         Optional<BlenderRenderProvider> blenderProvider,
                                         Optional<ShakaPackagingProvider> shakaPackagingProvider,
                                         Optional<SkiaStickerOverlayProvider> skiaProvider,
                                         Optional<VapourSynthRenderProvider> vapourSynthProvider) {
        return args -> {
            mltProvider.ifPresent(mlt -> {
                registry.register("mlt", mlt, mlt.getCapability());
                log.info("MLT render provider registered (POC/P1/Timeline-NLE)");
            });

            gstreamerProvider.ifPresent(gstreamer -> {
                registry.register("gstreamer", gstreamer, gstreamer.getCapability());
                log.info("GStreamer render provider registered (HOLD/P2/Realtime Pipeline)");
            });

            gpacPackagingProvider.ifPresent(gpac ->
                    log.info("GPAC packaging provider registered (packaging mode)"));
            bento4PackagingProvider.ifPresent(b4 ->
                    log.info("Bento4 packaging provider registered (dash/hls/dash_drm)"));

            natronProvider.ifPresent(natron -> {
                registry.register("natron", natron, natron.getCapability());
                log.info("Natron render provider registered (HOLD/P3/Node VFX)");
            });

            shotstackProvider.ifPresent(shotstack -> {
                registry.register("shotstack", shotstack, shotstack.getCapability());
                log.info("Shotstack cloud render provider registered (OPTIONAL/P2/External Cloud)");
            });

            remotionProvider.ifPresent(remotion -> {
                registry.register("remotion", remotion, remotion.getCapability());
                log.info("Remotion render provider registered (POC/P1/Subtitle & Template)");
            });

            blenderProvider.ifPresent(blender -> {
                registry.register("blender", blender, blender.getCapability());
                log.info("Blender render provider registered (POC/P1/3D Render)");
            });

            vapourSynthProvider.ifPresent(vs -> {
                registry.register("vapoursynth", vs, vs.getCapability());
                log.info("VapourSynth preprocess provider registered (HOLD/P2/Video Preprocess)");
            });

            shakaPackagingProvider.ifPresent(shaka ->
                    log.info("Shaka Packager registered (L7 dash)"));

            skiaProvider.ifPresent(skia -> {
                registry.register("skia", skia, skia.getCapability());
                log.info("Skia sticker overlay provider registered");
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
                    log.info("Provider '{}' health: {} ({}ms) status={} priority={} type={}",
                            cap.providerKey(), envResult.valid(), latency,
                            cap.status(), cap.priority(), cap.providerType());
                }
            }

            log.info("Render provider registry initialized with {} providers", registry.getAllProviders().size());
            log.info("Available effects: {}", registry.getAvailableEffects());
        };
    }
}

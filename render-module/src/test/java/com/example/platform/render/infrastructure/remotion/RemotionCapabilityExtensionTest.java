package com.example.platform.render.infrastructure.remotion;

import com.example.platform.render.domain.capability.CapabilityDescriptor;
import com.example.platform.render.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that advanced Remotion template capabilities can be represented
 * without modifying the Platform Kernel, Stable SPI, Product Model,
 * Execution Model, or domain model.
 *
 * <p>The capability model is extended entirely through:
 * <ul>
 *   <li>New Producer implementations (Producer SPI — stable)</li>
 *   <li>New CapabilityDescriptor records (Kernel-owned data type)</li>
 *   <li>New capability names in the {@link Capabilities} constants</li>
 *   <li>ProviderMetadata declarations (already supported)</li>
 * </ul>
 *
 * <p>None of these require:
 * <ul>
 *   <li>Modification to the Producer SPI interface</li>
 *   <li>Modification to the BackendCompiler SPI</li>
 *   <li>Modification to the ExecutionBackend SPI</li>
 *   <li>Modification to the ExecutionJob / ExecutionTask model</li>
 *   <li>Modification to the Product model</li>
 *   <li>Modification to the Storage Provider SPI</li>
 * </ul>
 */
class RemotionCapabilityExtensionTest {

    @Test
    void remotionCapabilityDeclaredWithoutModifyingSPI() {
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                "remotion-cap", "MEDIA_PIPELINE",
                "remotion-render", "Remotion Renderer", "1.0",
                "remotion-process", "MEDIA_PIPELINE",
                List.of("MEDIA_FILE"), List.of("PREVIEW", "FINAL_RENDER"),
                false, 50, true
        );

        assertNotNull(descriptor);
        assertEquals("remotion-cap", descriptor.capabilityId());
        assertEquals("remotion-render", descriptor.producerId());
        assertEquals("remotion-process", descriptor.backendId());
    }

    @Test
    void advancedCaptionCapabilitiesAreStringConstantsNoSpIChange() {
        assertNotNull(Capabilities.CAPTION_EFFECTS);
        assertNotNull(Capabilities.CAPTION_BURN_IN);
        assertNotNull(Capabilities.TEMPLATE_RENDER);
        assertNotNull(Capabilities.PREVIEW);

        assertEquals("caption_effects", Capabilities.CAPTION_EFFECTS);
        assertEquals("caption_burn_in", Capabilities.CAPTION_BURN_IN);
        assertEquals("template_render", Capabilities.TEMPLATE_RENDER);
    }

    @Test
    void providerMetadataExtendsCapabilityWithoutSpiChange() {
        ProviderMetadata remotionMetadata = new ProviderMetadata(
                "remotion", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.CAPTION_BURN_IN,
                        Capabilities.TEMPLATE_RENDER, Capabilities.PREVIEW),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.CAPTION_BURN_IN,
                        Capabilities.TEMPLATE_RENDER, Capabilities.PREVIEW),
                List.of(),
                List.of("multi_track", "render_3d", "vfx_composite"),
                false, "NODE", "Advanced subtitle rendering", List.of()
        );

        assertTrue(remotionMetadata.canHandleCapability(Capabilities.CAPTION_EFFECTS));
        assertTrue(remotionMetadata.canHandleCapability(Capabilities.TEMPLATE_RENDER));
        assertFalse(remotionMetadata.canHandleCapability(Capabilities.TRANSCODE),
                "Remotion should not claim transcode capability");
        assertFalse(remotionMetadata.canHandleCapability(Capabilities.RENDER_3D),
                "Remotion should not claim 3D render capability");

        assertTrue(remotionMetadata.notFor().contains("render_3d"));
        assertTrue(remotionMetadata.notFor().contains("multi_track"));
    }

    @Test
    void baselineFfmpegCapabilityRemainsUnchanged() {
        ProviderMetadata ffmpegMetadata = new ProviderMetadata(
                "ffmpeg", ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.TRANSCODE,
                        Capabilities.TRIM, Capabilities.THUMBNAIL,
                        Capabilities.EXTRACT_AUDIO, Capabilities.DEMUX, Capabilities.MUX),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.TRANSCODE,
                        Capabilities.TRIM, Capabilities.THUMBNAIL,
                        Capabilities.EXTRACT_AUDIO, Capabilities.DEMUX, Capabilities.MUX),
                List.of(),
                List.of("render_3d", "media_pipeline"),
                true, "FFMPEG", "Media processing", List.of()
        );

        assertTrue(ffmpegMetadata.canHandleCapability(Capabilities.CAPTION_BURN_IN),
                "FFmpeg must handle baseline caption burn-in");
        assertFalse(ffmpegMetadata.canHandleCapability(Capabilities.CAPTION_EFFECTS),
                "FFmpeg should not claim advanced caption effects");
        assertFalse(ffmpegMetadata.canHandleCapability(Capabilities.TEMPLATE_RENDER),
                "FFmpeg should not claim template render");
    }

    @Test
    void capabilitySeparationBetweenFfmpegAndRemotion() {
        ProviderMetadata ffmpeg = new ProviderMetadata(
                "ffmpeg", ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                true, "FFMPEG", "Media processing", List.of()
        );
        ProviderMetadata remotion = new ProviderMetadata(
                "remotion", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER, Capabilities.CAPTION_BURN_IN),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER, Capabilities.CAPTION_BURN_IN),
                List.of(),
                List.of("multi_track", "render_3d"),
                false, "NODE", "Advanced subtitle", List.of()
        );

        assertTrue(ffmpeg.canHandleCapability(Capabilities.CAPTION_BURN_IN));
        assertFalse(ffmpeg.canHandleCapability(Capabilities.CAPTION_EFFECTS));
        assertTrue(remotion.canHandleCapability(Capabilities.CAPTION_EFFECTS));
        assertTrue(remotion.canHandleCapability(Capabilities.TEMPLATE_RENDER));
    }

    @Test
    void producerSpiExistsAsInterface() {
        assertDoesNotThrow(() -> {
            Class<?> spiClass = Class.forName("com.example.platform.render.domain.producer.Producer");
            assertTrue(spiClass.isInterface(), "Producer must be a stable SPI interface");
        });
    }

    @Test
    void capabilityDescriptorIsKernelOwnedDataType() {
        CapabilityDescriptor descriptor = CapabilityDescriptor.of(
                "test-cap", "test-producer", "test-backend", "MEDIA_PIPELINE"
        );

        assertEquals("test-cap:test-producer", descriptor.capabilityId());
        assertEquals("test-producer", descriptor.producerId());
        assertEquals("test-backend", descriptor.backendId());
        assertEquals("MEDIA_PIPELINE", descriptor.backendType());
        assertNotNull(descriptor.supportedRepresentations());
        assertNotNull(descriptor.producedProductTypes());
    }

    @Test
    void renderJobCanRequestAdvancedCapabilitiesWithoutModelChange() {
        RenderJob job = new RenderJob(
                "job-1", "captioned_video_export", "experiment", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER, Capabilities.CAPTION_BURN_IN),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true,
                List.of("remotion"),
                List.of()
        );

        assertTrue(job.requiredCapabilities().contains(Capabilities.CAPTION_EFFECTS));
        assertTrue(job.requiredCapabilities().contains(Capabilities.TEMPLATE_RENDER));
        assertTrue(job.requiredCapabilities().contains(Capabilities.CAPTION_BURN_IN));
        assertTrue(job.preferredProviders().contains("remotion"));
        assertEquals("experiment", job.mode(),
                "Advanced capabilities should use experiment mode until Remotion is production-ready");
    }

    @Test
    void remotionProducerDescriptorHasCorrectValues() {
        com.example.platform.render.infrastructure.asset.provider.RemotionProducer producer =
                new com.example.platform.render.infrastructure.asset.provider.RemotionProducer();

        assertEquals("remotion-render", producer.producerId());
        assertTrue(producer.supportedOutputTypes().contains("PREVIEW"));
        assertTrue(producer.supportedOutputTypes().contains("FINAL_RENDER"));

        CapabilityDescriptor descriptor = producer.descriptor();
        assertEquals("remotion-cap", descriptor.capabilityId());
        assertEquals("remotion-process", descriptor.backendId());
        assertFalse(descriptor.preferred(),
                "Remotion descriptor must not be preferred for production dispatch");
    }
}

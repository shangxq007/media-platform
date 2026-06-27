package com.example.platform.render.infrastructure.remotion;

import com.example.platform.render.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that Remotion is NOT dispatch-eligible for baseline subtitle burn-in.
 *
 * <p>Remotion is a STUB provider in the lease/dispatch layer. It can never be
 * dispatched under any condition. FFmpeg/libass is the production baseline
 * for subtitle burn-in.
 *
 * <p>These tests verify the dispatch exclusion rules codified in:
 * <ul>
 *   <li>{@link ProviderStatus} — STUB has canBeConfiguredForDispatch=false</li>
 *   <li>{@link ProviderEligibility} — blocks non-configurable statuses</li>
 *   <li>{@link com.example.platform.render.infrastructure.farm.RenderJobLeaseService} — INELIGIBLE set includes STUB</li>
 * </ul>
 */
class RemotionDispatchExclusionTest {

    @Test
    void remotionProviderStatusIsPocNotProduction() {
        assertFalse(ProviderStatus.POC.isProductionDispatchEligible(),
                "POC must not be production dispatch eligible by default");
        assertTrue(ProviderStatus.POC.canBeConfiguredForDispatch(),
                "POC providers can be configured for dispatch (explicit allow)");
    }

    @Test
    void stubStatusCanNeverBeConfiguredForDispatch() {
        assertFalse(ProviderStatus.STUB.isProductionDispatchEligible());
        assertFalse(ProviderStatus.STUB.canBeConfiguredForDispatch(),
                "STUB providers can never be configured for dispatch");
    }

    @Test
    void stubStatusIsIneligibleInProviderEligibility() {
        ProviderMetadata stubMetadata = new ProviderMetadata(
                "stub-provider", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS),
                List.of(), List.of(), false,
                "NODE", "Stub purpose", List.of()
        );

        RenderJob baselineSubtitleJob = createBaselineBurnInJob();

        assertFalse(ProviderEligibility.isEligible(stubMetadata, baselineSubtitleJob),
                "STUB provider must not be eligible even with capability match");
    }

    @Test
    void remotionCannotDisplaceFfmpegForBaselineBurnIn() {
        ProviderMetadata remotionStubMetadata = new ProviderMetadata(
                "remotion", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                List.of(), List.of(), false,
                "NODE", "Advanced subtitle rendering", List.of()
        );
        ProviderMetadata ffmpegMetadata = new ProviderMetadata(
                "ffmpeg", ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.TRANSCODE, Capabilities.TRIM),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.TRANSCODE, Capabilities.TRIM),
                List.of(), List.of(), true,
                "FFMPEG", "Media processing", List.of()
        );

        RenderJob baselineSubtitleJob = createBaselineBurnInJob();

        assertFalse(ProviderEligibility.isEligible(remotionStubMetadata, baselineSubtitleJob),
                "Remotion (STUB) must NOT be eligible for baseline subtitle burn-in");
        assertTrue(ProviderEligibility.isEligible(ffmpegMetadata, baselineSubtitleJob),
                "FFmpeg (PRODUCTION) must be eligible for baseline subtitle burn-in");
    }

    @Test
    void remotionNotEligibleEvenWhenPreferred() {
        ProviderMetadata remotionStubMetadata = new ProviderMetadata(
                "remotion", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS),
                List.of(), List.of(), false,
                "NODE", "Advanced subtitles", List.of()
        );

        RenderJob jobPreferringRemotion = new RenderJob(
                "job-1", "captioned_video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_BURN_IN),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true,
                List.of("remotion"),
                List.of()
        );

        assertFalse(ProviderEligibility.isEligible(remotionStubMetadata, jobPreferringRemotion),
                "Remotion (STUB) must NOT be eligible even when listed as preferred provider");
    }

    @Test
    void remotionBlockedForCaptionBurnInWhenBaselineFfmpegAvailable() {
        ProviderMetadata remotionPocMetadata = new ProviderMetadata(
                "remotion", ProviderStatus.POC, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS),
                List.of(), List.of(), false,
                "NODE", "Advanced subtitles", List.of()
        );
        ProviderMetadata ffmpegMetadata = new ProviderMetadata(
                "ffmpeg", ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(), List.of(), true,
                "FFMPEG", "Media processing", List.of()
        );

        RenderJob job = new RenderJob(
                "job-1", "captioned_video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_BURN_IN),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of("remotion")
        );

        assertTrue(ProviderEligibility.isEligible(ffmpegMetadata, job),
                "FFmpeg must still be eligible when Remotion is blocked");
        assertFalse(ProviderEligibility.isEligible(remotionPocMetadata, job),
                "Remotion must be blocked when listed in blockedProviders");
    }

    @Test
    void advancedTemplateCapabilityDoesNotTriggerRemotionForBurnIn() {
        ProviderMetadata remotionStubMetadata = new ProviderMetadata(
                "remotion", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                List.of(), List.of(), false,
                "NODE", "Advanced subtitle", List.of()
        );
        ProviderMetadata ffmpegMetadata = new ProviderMetadata(
                "ffmpeg", ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(), List.of(), true,
                "FFMPEG", "Media processing", List.of()
        );

        RenderJob baselineBurnInJob = new RenderJob(
                "job-1", "captioned_video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_BURN_IN),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true,
                List.of("remotion"),
                List.of()
        );

        assertFalse(ProviderEligibility.isEligible(remotionStubMetadata, baselineBurnInJob),
                "Remotion (STUB) must NOT be eligible for baseline subtitle burn-in");
        assertTrue(ProviderEligibility.isEligible(ffmpegMetadata, baselineBurnInJob),
                "FFmpeg must be eligible for baseline subtitle burn-in even when Remotion is preferred");
    }

    @Test
    void leaseServiceHasRemotionStubStatus() {
        for (ProviderStatus status : ProviderStatus.values()) {
            if (status == ProviderStatus.STUB || status == ProviderStatus.SKELETON
                    || status == ProviderStatus.DEPRECATED || status == ProviderStatus.MOCK) {
                assertFalse(status.canBeConfiguredForDispatch(),
                        status + " must not be configurable for dispatch");
            }
        }
    }

    @Test
    void productionStatusIsOnlyStatusDispatchEligibleWithoutConfig() {
        assertTrue(ProviderStatus.PRODUCTION.isProductionDispatchEligible());
        assertTrue(ProviderStatus.PRODUCTION.canBeConfiguredForDispatch());

        for (ProviderStatus status : ProviderStatus.values()) {
            if (status != ProviderStatus.PRODUCTION) {
                assertFalse(status.isProductionDispatchEligible(),
                        status + " must not be production dispatch eligible by default");
            }
        }
    }

    private static RenderJob createBaselineBurnInJob() {
        return new RenderJob(
                "job-1", "captioned_video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_BURN_IN),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of()
        );
    }
}

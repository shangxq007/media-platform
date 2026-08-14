package com.example.platform.render.app.mediaprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.media.app.MediaProbeService;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * MCMV2-C: render-side probe facade delegates to the media-domain
 * normalization boundary; re-probe never changes MediaAssetId.
 */
class MediaAssetProbeServiceTest {

    @Test
    void probeAndPersistDelegatesToMediaDomainWithStableIdentity() {
        MediaProbeService mediaProbeService = mock(MediaProbeService.class);
        NormalizedMediaProbe expected = new NormalizedMediaProbe(
                MediaAssetId.of("asset-1"), null, null, false, true, true, List.of());
        when(mediaProbeService.probeAndPersist(
                MediaAssetId.of("asset-1"), "tenant-1", "proj-1", "/tmp/test.mp4"))
                .thenReturn(expected);

        MediaAssetProbeService facade = new MediaAssetProbeService(mediaProbeService);
        NormalizedMediaProbe result = facade.probeAndPersist("tenant-1", "proj-1", "asset-1", "/tmp/test.mp4");

        assertThat(result.mediaAssetId()).isEqualTo(MediaAssetId.of("asset-1"));
        verify(mediaProbeService).probeAndPersist(
                MediaAssetId.of("asset-1"), "tenant-1", "proj-1", "/tmp/test.mp4");
    }

    @Test
    void getLatestProbeReturnsNormalizedModel() {
        MediaProbeService mediaProbeService = mock(MediaProbeService.class);
        NormalizedMediaProbe expected = new NormalizedMediaProbe(
                MediaAssetId.of("asset-1"), null, null, false, true, false, List.of());
        when(mediaProbeService.latestNormalized(MediaAssetId.of("asset-1")))
                .thenReturn(Optional.of(expected));

        MediaAssetProbeService facade = new MediaAssetProbeService(mediaProbeService);
        NormalizedMediaProbe result = facade.getLatestProbe("tenant-1", "asset-1");

        assertThat(result).isSameAs(expected);
        verify(mediaProbeService).latestNormalized(MediaAssetId.of("asset-1"));
    }
}

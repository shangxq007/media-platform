package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.planning.FinalComposerHint;
import com.example.platform.render.domain.interchange.TimelineExtensions;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineTrack;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinalComposerSelectorTest {

    private final FinalComposerSelector selector = new FinalComposerSelector();

    @Test
    void explicitHintOverridesAuto() {
        TimelineSpec spec = TimelineSpec.create("tl", "T", TimelineOutputSpec.mp4_1080p30());
        TimelineExtensions ext = new TimelineExtensions(
                "2.0", FinalComposerHint.TYPED_PROVIDER_PLUGIN, List.of(), List.of(), List.of(), java.util.Map.of(), false);
        assertEquals(FinalComposerHint.TYPED_PROVIDER_PLUGIN, selector.resolve(spec, ext));
        assertNull(selector.backendKey(FinalComposerHint.TYPED_PROVIDER_PLUGIN));
    }

    @Test
    void twoVideoTracksSelectMlt() {
        TimelineSpec spec = new TimelineSpec("tl", "T", null,
                List.of(
                        TimelineTrack.of("v1", "V1", TimelineTrack.TrackType.VIDEO),
                        TimelineTrack.of("v2", "V2", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 0, java.util.Map.of());
        assertEquals(FinalComposerHint.MLT, selector.resolve(spec, TimelineExtensions.defaults()));
    }

    @Test
    void autoAndUnboundStrategyDoNotInventBackendIdentity() {
        assertNull(selector.backendKey(FinalComposerHint.AUTO));
        assertNull(selector.backendKey(FinalComposerHint.TYPED_PROVIDER_PLUGIN));
    }
}

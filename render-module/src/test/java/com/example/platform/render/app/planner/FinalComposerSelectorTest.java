package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinalComposerSelectorTest {

    private final FinalComposerSelector selector = new FinalComposerSelector();

    @Test
    void explicitHintOverridesAuto() {
        TimelineSpec spec = TimelineSpec.create("tl", "T", TimelineOutputSpec.mp4_1080p30());
        TimelineExtensions ext = new TimelineExtensions(
                "2.0", FinalComposerHint.FFMPEG, List.of(), List.of(), List.of(), java.util.Map.of(), false);
        assertEquals(FinalComposerHint.FFMPEG, selector.resolve(spec, ext));
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
}

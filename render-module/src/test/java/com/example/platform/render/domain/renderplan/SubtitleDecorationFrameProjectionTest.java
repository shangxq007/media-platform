package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.SubtitleCueMotion;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubtitleDecorationFrameProjectionTest {
    private static TimedTextMaterializationRequirement cue(String localStart, boolean enabled) {
        var fixture = TestPlans.textElement();
        var decoration = new SubtitleCueMotion.Decoration(
                "sunflower-v1", "right-of-cue-v1", enabled, localStart, "1/1");
        var frame = new TextFrame(FontRational.whole(200), null, fixture.frame().horizontalAlignment(),
                fixture.frame().verticalAlignment(), fixture.frame().wrapBehavior(), fixture.frame().overflowBehavior());
        return new TimedTextMaterializationRequirement(fixture.id(), FontRational.of(12_000_000_001L, 1_000_000_000L),
                FontRational.of(3_999_999_999L, 1_000_000_000L), fixture.styledText(), frame, fixture.fallbackPolicy(),
                fixture.resolvedFontRuns(), new SubtitleCueMotion(null, List.of(), List.of(), null, decoration));
    }

    private static RenderExtent extent(int startMillis, int endMillis) {
        return new RenderExtent(MediaTime.ofMillis(startMillis), MediaTime.ofMillis(endMillis), FrameRate.of(24, 1));
    }

    @Test
    void projectsAbsoluteAndClippedHalfOpenIntervalsWithoutAnIntermediateLong() {
        for (String localStart : List.of("1/1000000000", "1/9007199254740881")) {
            var materialization = cue(localStart, true);
            var uncut = materialization.cueRaster(extent(0, 16_000)).orElseThrow();
            assertEquals(289, uncut.decoration().firstFrame());
            assertEquals(313, uncut.decoration().endFrame());
            assertEquals(MediaTime.ZERO, uncut.elapsed());
            var shifted = materialization.cueRaster(extent(12_000, 15_000)).orElseThrow();
            assertEquals(1, shifted.decoration().firstFrame());
            assertEquals(25, shifted.decoration().endFrame());
            var clippedStart = materialization.cueRaster(extent(12_500, 15_000)).orElseThrow();
            assertEquals(0, clippedStart.decoration().firstFrame());
            assertEquals(13, clippedStart.decoration().endFrame());
            assertEquals(MediaTime.parse("499999999/1000000000"), clippedStart.elapsed());
        }
        assertNull(cue("1/1000000000", false).cueRaster(extent(0, 16_000)).orElseThrow().decoration());
    }
}

package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SegmentPipelinePayloadBuilderTest {

    @Test
    void buildsSegmentRenderAndStitchPayloads() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl", "T", TimelineOutputSpec.mp4_1080p30());
        String segmentJson = SegmentPipelinePayloadBuilder.segmentRenderPayload(
                spec, "seg_0", 0, 120, 30, null);
        assertTrue(segmentJson.contains("segmentRender"));
        assertTrue(segmentJson.contains("seg_0"));

        Map<String, String> artifacts = new LinkedHashMap<>();
        artifacts.put("seg_0", "localFs://a/seg0.mp4");
        artifacts.put("seg_1", "localFs://a/seg1.mp4");
        String stitchJson = SegmentPipelinePayloadBuilder.segmentStitchPayload(
                spec, List.of("seg_0", "seg_1"), artifacts, 30);
        assertTrue(stitchJson.contains("segmentStitch"));
        assertTrue(stitchJson.contains("seg_0"));
    }
}

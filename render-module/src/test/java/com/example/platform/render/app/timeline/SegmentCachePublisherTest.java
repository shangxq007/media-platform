package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineSegment;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SegmentCachePublisherTest {

    @Test
    void publishesCacheKeyIndexWithRemoteUri() {
        RenderCacheProperties props = new RenderCacheProperties();
        props.setRemoteEnabled(true);
        props.setRemoteUriPrefix("s3://cache");
        SegmentCachePublisher publisher = new SegmentCachePublisher(new RenderCacheUriResolver(props), props);

        Map<String, Map<String, String>> index = publisher.publish(
                "tenant-a",
                List.of(new TimelineSegment("seg_0", 0, 120, "segment:tl:seg_0:r1:SEGMENT")),
                Map.of("seg_0", "localFs://artifacts/job/seg_0/output.mp4"));

        assertTrue(index.containsKey("segment:tl:seg_0:r1:SEGMENT"));
        assertEquals("seg_0", index.get("segment:tl:seg_0:r1:SEGMENT").get("segmentId"));
        assertTrue(index.get("segment:tl:seg_0:r1:SEGMENT").get("remoteUri").startsWith("s3://cache"));
    }
}

package com.example.platform.render.domain.timeline;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenTimelineioAdapterMetadataTest {

    @Test
    void shouldExportPlatformProjectMetadataInTimelineRoot() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        Map<String, String> rootMeta = new LinkedHashMap<>();
        rootMeta.put(TimelinePlatformMetadata.PLATFORM_PROJECT_ID, "proj_123");
        rootMeta.put(TimelinePlatformMetadata.PLATFORM_ASSET_REGISTRY_URI, "asset-registry://project/proj_123");

        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(TimelineTrack.of("v1", "Video 1", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 0, rootMeta);

        String otioJson = OpenTimelineioAdapter.toOtioJson(timeline);

        assertNotNull(otioJson);
        assertTrue(otioJson.contains("platform.project_id"));
        assertTrue(otioJson.contains("proj_123"));
        assertTrue(otioJson.contains("platform.asset_registry_uri"));
    }

    @Test
    void shouldExportPlatformClipMetadata() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        Map<String, String> refMeta = new LinkedHashMap<>();
        refMeta.put(TimelinePlatformMetadata.PLATFORM_ASSET_ID, "asset_123");
        refMeta.put(TimelinePlatformMetadata.PLATFORM_ASSET_VERSION, "v7");
        refMeta.put(TimelinePlatformMetadata.PLATFORM_XMP_URI, "xmp://asset/asset_123/version/v7");
        refMeta.put(TimelinePlatformMetadata.PLATFORM_ENTITY_REF, "asset://asset_123?v=v7");

        TimelineAssetRef assetRef = new TimelineAssetRef("asset_123", "file:///tmp/video.mp4",
                "mp4", 10, 1920, 1080, refMeta, null);

        TimelineClip clip = new TimelineClip("c1", assetRef, 0, 0, 10, 10, List.of());

        TimelineTrack track = new TimelineTrack("v1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), output, 0, Map.<String,String>of());

        String otioJson = OpenTimelineioAdapter.toOtioJson(timeline);

        assertNotNull(otioJson);
        assertTrue(otioJson.contains("\"platform\""));
        assertTrue(otioJson.contains("asset_id"));
        assertTrue(otioJson.contains("asset_123"));
        assertTrue(otioJson.contains("v7"));
    }

    @Test
    void shouldNotBreakRoundTripWithPlatformMetadata() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        Map<String, String> refMeta = new LinkedHashMap<>();
        refMeta.put(TimelinePlatformMetadata.PLATFORM_ASSET_ID, "asset_456");
        refMeta.put(TimelinePlatformMetadata.PLATFORM_ASSET_VERSION, "v1");

        TimelineAssetRef assetRef = new TimelineAssetRef("asset_456", "file:///tmp/video.mp4",
                "mp4", 5, 1920, 1080, refMeta, null);

        TimelineClip clip = new TimelineClip("c1", assetRef, 0, 0, 5, 5, List.of());
        TimelineTrack track = new TimelineTrack("v1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        TimelineSpec timeline = new TimelineSpec("tl-roundtrip", "RT", null,
                List.of(track), List.of(), output, 0, Map.<String,String>of());

        String otioJson = OpenTimelineioAdapter.toOtioJson(timeline);
        assertTrue(otioJson.contains("\"platform\""));
        assertTrue(otioJson.contains("asset_id"));

        TimelineSpec imported = OpenTimelineioAdapter.fromOtioJson(otioJson);

        assertEquals("tl-roundtrip", imported.id());
        assertEquals(1, imported.tracks().size());
        assertEquals(1, imported.tracks().get(0).clips().size());
        TimelineClip importedClip = imported.tracks().get(0).clips().get(0);
        assertNotNull(importedClip);
        assertTrue(importedClip.clipDuration() > 0);
    }

    @Test
    void shouldExportEffectMetadataWithPlatformKeys() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineClipEffect effect = new TimelineClipEffect("eff_1", "caption.render.rich",
                "pack_001", "v2", List.of("remotion"),
                Map.of("fontSize", (Object) 24));

        TimelineAssetRef assetRef = TimelineAssetRef.of("asset_789", "file:///tmp/video.mp4");
        TimelineClip clip = new TimelineClip("c1", assetRef, 0, 0, 5, 5, List.of(effect));
        TimelineTrack track = new TimelineTrack("v1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        TimelineSpec timeline = new TimelineSpec("tl-fx", "FX Test", null,
                List.of(track), List.of(), output, 0, Map.<String,String>of());

        String otioJson = OpenTimelineioAdapter.toOtioJson(timeline);

        assertNotNull(otioJson);
        assertTrue(otioJson.contains("Effect.1"));
        assertTrue(otioJson.contains("caption.render.rich"));
    }
}

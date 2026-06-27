package com.example.platform.render.testsupport;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.*;

import java.util.List;
import java.util.Map;

/**
 * Canonical timeline fixture for Timeline Core Testable R1 smoke testing.
 *
 * <p>Provides a minimal, deterministic timeline suitable for proving the
 * Timeline → RenderJob → Storage → Product closure path. The fixture
 * uses {@code asset://} scheme URIs (not filesystem paths) and a controlled
 * output specification.</p>
 *
 * <p>This fixture does NOT invoke real FFmpeg/libass rendering. It is used
 * with controlled local output files to prove the integration chain.</p>
 */
public final class TimelineCoreSmokeFixture {

    public static final String TIMELINE_ID = "tl_smoke_001";
    public static final String PROJECT_ID = "prj_smoke_001";
    public static final String TENANT_ID = "ten_smoke_001";
    public static final String ASSET_ID = "ast_smoke_001";
    public static final String ASSET_URI = "asset://ast_smoke_001";

    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    public static final int FPS = 30;
    public static final double DURATION_SEC = 10.0;
    public static final String OUTPUT_FORMAT = "mp4";

    private TimelineCoreSmokeFixture() {}

    /**
     * Creates a minimal video-only timeline: 1 VIDEO track, 1 clip, 10s, 1080p30.
     */
    public static TimelineSpec createMinimalVideoTimeline() {
        TimelineAssetRef assetRef = TimelineAssetRef.of(ASSET_ID, ASSET_URI);
        TimelineClip clip = TimelineClip.of("clip_001", assetRef, 0.0, 0.0, DURATION_SEC);
        TimelineTrack videoTrack = new TimelineTrack(
                "trk_v1", "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);

        TimelineOutputSpec outputSpec = TimelineOutputSpec.mp4_1080p30();

        return new TimelineSpec(
                TIMELINE_ID, "Smoke Test Timeline", "R1 smoke fixture",
                List.of(videoTrack), List.of(), outputSpec, DURATION_SEC,
                Map.of("tenantId", TENANT_ID, "projectId", PROJECT_ID));
    }

    /**
     * Creates a timeline with 1 VIDEO track and 1 subtitle text overlay.
     */
    public static TimelineSpec createVideoWithSubtitleTimeline() {
        TimelineSpec base = createMinimalVideoTimeline();
        TimelineTextOverlay subtitle = TimelineTextOverlay.of(
                "sub_001", "Hello World", 1.0, 5.0);
        return new TimelineSpec(
                base.id(), base.name(), base.description(),
                base.tracks(), List.of(subtitle), base.outputSpec(),
                base.totalDuration(), base.metadata());
    }

    /**
     * Serializes a TimelineSpec to JSON using the same parser/writer conventions
     * as the production pipeline. Produces canonical TimelineSpec JSON that
     * {@link com.example.platform.render.domain.timeline.TimelineScriptParser} can parse.
     */
    public static String toJson(TimelineSpec spec) {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            // Serialize as canonical TimelineSpec JSON (matches TimelineScriptParser expectations)
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("id", spec.id());
            root.put("name", spec.name());
            if (spec.description() != null) {
                root.put("description", spec.description());
            }

            // Tracks at top level (TimelineScriptParser expects this)
            java.util.List<Map<String, Object>> tracksJson = new java.util.ArrayList<>();
            if (spec.tracks() != null) {
                for (TimelineTrack track : spec.tracks()) {
                    Map<String, Object> trk = new java.util.LinkedHashMap<>();
                    trk.put("id", track.id());
                    trk.put("name", track.name());
                    trk.put("type", track.type().name());
                    trk.put("layer", track.layer());
                    trk.put("muted", track.muted());
                    trk.put("locked", track.locked());
                    java.util.List<Map<String, Object>> clipsJson = new java.util.ArrayList<>();
                    if (track.clips() != null) {
                        for (TimelineClip clip : track.clips()) {
                            Map<String, Object> c = new java.util.LinkedHashMap<>();
                            c.put("id", clip.id());
                            if (clip.assetRef() != null) {
                                Map<String, Object> assetRef = new java.util.LinkedHashMap<>();
                                assetRef.put("assetId", clip.assetRef().assetId());
                                assetRef.put("storageUri", clip.assetRef().storageUri());
                                assetRef.put("format", clip.assetRef().format());
                                assetRef.put("duration", clip.assetRef().duration());
                                assetRef.put("width", clip.assetRef().width());
                                assetRef.put("height", clip.assetRef().height());
                                c.put("assetRef", assetRef);
                            }
                            c.put("timelineStart", clip.timelineStart());
                            c.put("assetInPoint", clip.assetInPoint());
                            c.put("assetOutPoint", clip.assetOutPoint());
                            c.put("clipDuration", clip.clipDuration());
                            clipsJson.add(c);
                        }
                    }
                    trk.put("clips", clipsJson);
                    tracksJson.add(trk);
                }
            }
            root.put("tracks", tracksJson);

            // Text overlays at top level
            if (spec.textOverlays() != null && !spec.textOverlays().isEmpty()) {
                java.util.List<Map<String, Object>> overlays = new java.util.ArrayList<>();
                for (TimelineTextOverlay overlay : spec.textOverlays()) {
                    Map<String, Object> o = new java.util.LinkedHashMap<>();
                    o.put("id", overlay.id());
                    o.put("text", overlay.text());
                    o.put("fontFamily", overlay.fontFamily());
                    o.put("fontSize", overlay.fontSize());
                    o.put("color", overlay.color());
                    o.put("positionX", overlay.positionX());
                    o.put("positionY", overlay.positionY());
                    o.put("startTime", overlay.startTime());
                    o.put("duration", overlay.duration());
                    if (overlay.backgroundColor() != null) {
                        o.put("backgroundColor", overlay.backgroundColor());
                    }
                    overlays.add(o);
                }
                root.put("textOverlays", overlays);
            }

            // OutputSpec at top level (TimelineScriptParser expects this)
            Map<String, Object> outputSpecJson = new java.util.LinkedHashMap<>();
            outputSpecJson.put("format", spec.outputSpec().format());
            outputSpecJson.put("resolution", spec.outputSpec().resolution());
            outputSpecJson.put("frameRate", spec.outputSpec().frameRate());
            outputSpecJson.put("videoCodec", spec.outputSpec().videoCodec());
            outputSpecJson.put("videoBitrate", spec.outputSpec().videoBitrate());
            if (spec.outputSpec().pixelFormat() != null) {
                outputSpecJson.put("pixelFormat", spec.outputSpec().pixelFormat());
            }
            root.put("outputSpec", outputSpecJson);

            root.put("totalDuration", spec.totalDuration());

            // Metadata
            if (spec.metadata() != null && !spec.metadata().isEmpty()) {
                root.put("metadata", spec.metadata());
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize TimelineSpec to JSON", e);
        }
    }

    /**
     * Creates a SnapshotInfo from the fixture timeline.
     */
    public static TimelineSnapshotService.SnapshotInfo toSnapshotInfo(TimelineSpec spec) {
        return new TimelineSnapshotService.SnapshotInfo(
                "snap_" + spec.id(), PROJECT_ID, TENANT_ID,
                toJson(spec), "1.0.0");
    }
}

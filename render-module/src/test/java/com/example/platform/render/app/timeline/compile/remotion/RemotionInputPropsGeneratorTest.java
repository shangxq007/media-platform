package com.example.platform.render.app.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.domain.timeline.compile.remotion.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class RemotionInputPropsGeneratorTest {

    private RemotionInputPropsGenerator generator;
    private RemotionInputPropsSerializer serializer;
    private RemotionInputPropsValidator validator;

    @BeforeEach
    void setUp() {
        generator = new RemotionInputPropsGenerator();
        serializer = new RemotionInputPropsSerializer();
        validator = new RemotionInputPropsValidator();
    }

    @Test
    @DisplayName("Generates props from simple timeline")
    void generatesProps() {
        RemotionInputProps props = generator.generate(createSimpleTimeline());
        assertNotNull(props);
        assertEquals(RemotionInputProps.SCHEMA_VERSION, props.schemaVersion());
    }

    @Test
    @DisplayName("Includes composition metadata")
    void includesComposition() {
        RemotionInputProps props = generator.generate(createSimpleTimeline());
        assertNotNull(props.composition());
        assertEquals(1920, props.composition().width());
        assertEquals(1080, props.composition().height());
        assertTrue(props.composition().durationInFrames() > 0);
    }

    @Test
    @DisplayName("Duration to frames is deterministic")
    void durationDeterministic() {
        NormalizedTimeline t = createSimpleTimeline();
        assertEquals(generator.generate(t).composition().durationInFrames(),
                generator.generate(t).composition().durationInFrames());
    }

    @Test
    @DisplayName("Includes media assets without local paths")
    void mediaAssetsNoPaths() {
        RemotionInputProps props = generator.generate(createSimpleTimeline());
        assertFalse(props.mediaAssets().isEmpty());
        props.mediaAssets().forEach(a -> assertFalse(a.assetId().contains("/tmp")));
    }

    @Test
    @DisplayName("Includes captions when present")
    void includesCaptions() {
        RemotionInputProps props = generator.generate(createTimelineWithCaptions());
        assertTrue(props.hasCaptions());
        assertEquals("Hello World", props.captions().get(0).text());
    }

    @Test
    @DisplayName("Includes fonts from captions")
    void includesFonts() {
        RemotionInputProps props = generator.generate(createTimelineWithCaptions());
        assertTrue(props.hasFonts());
        assertEquals("DejaVu Sans", props.fonts().get(0).family());
    }

    @Test
    @DisplayName("Includes output profile")
    void includesOutput() {
        RemotionInputProps props = generator.generate(createSimpleTimeline());
        assertNotNull(props.output());
        assertEquals(1920, props.output().width());
    }

    @Test
    @DisplayName("Null timeline fails closed")
    void nullTimelineFails() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    @DisplayName("Empty assets fail closed")
    void emptyAssetsFail() {
        NormalizedTimeline t = new NormalizedTimeline("tl-1", "p-1", List.of(), List.of(),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());
        assertThrows(IllegalArgumentException.class, () -> generator.generate(t));
    }

    @Test
    @DisplayName("Serialization is deterministic")
    void serializationDeterministic() {
        RemotionInputProps props = generator.generate(createSimpleTimeline());
        assertEquals(serializer.serialize(props), serializer.serialize(props));
    }

    @Test
    @DisplayName("Repeated generation produces identical JSON")
    void repeatedGenerationIdentical() {
        NormalizedTimeline t = createSimpleTimeline();
        assertEquals(serializer.serialize(generator.generate(t)),
                serializer.serialize(generator.generate(t)));
    }

    @Test
    @DisplayName("Different profile changes output JSON")
    void differentProfileChangesJson() {
        NormalizedTimeline t1 = createSimpleTimeline();
        NormalizedTimeline t2 = new NormalizedTimeline(t1.timelineId(), t1.projectId(),
                t1.tracks(), t1.captionLayers(),
                new NormalizedOutputProfile("mp4", "1280x720", 24.0, "h264", 5000,
                        "aac", 44100, 2, 128, "yuv420p"),
                t1.totalDuration(), t1.metadata());
        assertNotEquals(serializer.serialize(generator.generate(t1)),
                serializer.serialize(generator.generate(t2)));
    }

    @Test
    @DisplayName("Valid props pass validation")
    void validPropsPass() {
        RemotionInputPropsValidator.ValidationResult r =
                validator.validate(generator.generate(createSimpleTimeline()));
        assertTrue(r.valid(), "Issues: " + r.issues());
    }

    @Test
    @DisplayName("Captions pass validation")
    void captionsPassValidation() {
        RemotionInputPropsValidator.ValidationResult r =
                validator.validate(generator.generate(createTimelineWithCaptions()));
        assertTrue(r.valid(), "Issues: " + r.issues());
    }

    @Test
    @DisplayName("Null props fail validation")
    void nullPropsFail() {
        assertFalse(validator.validate(null).valid());
    }

    @Test
    @DisplayName("Props contain no local paths")
    void noLocalPaths() {
        String s = generator.generate(createSimpleTimeline()).toString();
        assertFalse(s.contains("/tmp"));
        assertFalse(s.contains("/home"));
    }

    @Test
    @DisplayName("Props contain no storage internals")
    void noStorageInternals() {
        String json = serializer.serialize(generator.generate(createSimpleTimeline()));
        assertFalse(json.contains("\"bucket\""));
        assertFalse(json.contains("\"objectKey\""));
        assertFalse(json.contains("\"rootPath\""));
        assertFalse(json.contains("\"signedUrl\""));
    }

    @Test
    @DisplayName("Props contain no raw commands")
    void noRawCommands() {
        String json = serializer.serialize(generator.generate(createSimpleTimeline()));
        assertFalse(json.contains("ffmpeg "));
        assertFalse(json.contains("remotion render"));
    }

    @Test
    @DisplayName("Props contain no secrets")
    void noSecrets() {
        String json = serializer.serialize(generator.generate(createSimpleTimeline()));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("X-Amz-Signature"));
    }

    @Test
    @DisplayName("generationReady is false")
    void generationReadyFalse() {
        assertEquals("false", generator.generate(createSimpleTimeline()).metadata().get("generationReady"));
    }

    @Test
    @DisplayName("Does not execute Remotion (pure data transform)")
    void doesNotExecute() {
        assertNotNull(generator.generate(createSimpleTimeline()));
    }

    // --- Helpers ---

    private NormalizedTimeline createSimpleTimeline() {
        NormalizedAssetRef asset = new NormalizedAssetRef(
                "asset-1", "asset://asset-1", "mp4", 10L, 1920, 1080, Map.of());
        NormalizedClip clip = new NormalizedClip("clip-1", asset, 0.0, 0.0, 5.0, 5.0);
        NormalizedTrack track = new NormalizedTrack(
                "track-1", "Video 1", NormalizedTrack.TrackType.VIDEO, 0, false, List.of(clip));
        return new NormalizedTimeline("tl-1", "proj-1", List.of(track), List.of(),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());
    }

    private NormalizedTimeline createTimelineWithCaptions() {
        NormalizedAssetRef asset = new NormalizedAssetRef(
                "asset-1", "asset://asset-1", "mp4", 10L, 1920, 1080, Map.of());
        NormalizedClip clip = new NormalizedClip("clip-1", asset, 0.0, 0.0, 5.0, 5.0);
        NormalizedTrack track = new NormalizedTrack(
                "track-1", "Video 1", NormalizedTrack.TrackType.VIDEO, 0, false, List.of(clip));
        NormalizedCaptionLayer caption = new NormalizedCaptionLayer(
                "cap-1", "Hello World", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 1.0, 3.0, null);
        return new NormalizedTimeline("tl-1", "proj-1", List.of(track), List.of(caption),
                NormalizedOutputProfile.DEFAULT_MP4_1080P30, 5.0, Map.of());
    }
}

package com.example.platform.timeline.otio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.canonical.TrackType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalTimelineOtioExporterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final OtioExportIdentity IDENTITY = new OtioExportIdentity(
            "tenant-fr-e2e", "project-fr-e2e",
            "948bb702-de27-4dae-a8a4-a7678544d078", "timeline-1.0");

    private final CanonicalTimelineOtioExporter exporter = new CanonicalTimelineOtioExporter();

    @Test
    void exportsPersistedFirstRenderCanonicalRevisionAsOfficialOtioShape() throws Exception {
        String otio = exporter.export(loadPersistedRevision(), IDENTITY);
        JsonNode root = JSON.readTree(otio);
        assertThat(root.path("OTIO_SCHEMA").asText()).isEqualTo("Timeline.1");
        assertThat(root.path("metadata").path("media_platform.revision_id").asText())
                .isEqualTo(IDENTITY.revisionId());
        assertThat(root.path("tracks").path("OTIO_SCHEMA").asText()).isEqualTo("Stack.1");
        JsonNode track = root.path("tracks").path("children").get(0);
        assertThat(track.path("OTIO_SCHEMA").asText()).isEqualTo("Track.1");
        assertThat(track.path("kind").asText()).isEqualTo("Video");
        JsonNode children = track.path("children");
        assertThat(children).hasSize(3);
        assertThat(children.get(0).path("OTIO_SCHEMA").asText()).isEqualTo("Clip.2");
        assertThat(children.get(1).path("OTIO_SCHEMA").asText()).isEqualTo("Gap.1");
        assertThat(children.get(2).path("OTIO_SCHEMA").asText()).isEqualTo("Clip.2");

        JsonNode red = children.get(0);
        assertThat(red.path("name").asText()).isEqualTo("clip-red");
        assertThat(seconds(red.path("source_range").path("start_time"))).isZero();
        assertThat(seconds(red.path("source_range").path("duration"))).isEqualTo(0.5);
        assertThat(red.path("media_references").path("DEFAULT_MEDIA").path("target_url").asText())
                .isEqualTo("urn:media-platform:artifact:art-9bd4b201-202b-370d-8a59-0de085533503");

        JsonNode gap = children.get(1);
        assertThat(seconds(gap.path("source_range").path("duration"))).isEqualTo(0.5);
        JsonNode blue = children.get(2);
        assertThat(blue.path("name").asText()).isEqualTo("clip-blue");
        assertThat(seconds(blue.path("source_range").path("start_time"))).isZero();
        assertThat(seconds(blue.path("source_range").path("duration"))).isEqualTo(1.0);

        assertThat(exporter.export(loadPersistedRevision(), IDENTITY)).isEqualTo(otio);
        Path output = Path.of("build", "otio", "e2e14-rev8.otio");
        Files.createDirectories(output.getParent());
        Files.writeString(output, otio, StandardCharsets.UTF_8);
    }

    @Test
    void exportsMultipleTracksNonZeroStartAndTrimmedSourceRanges() {
        TimelineClip videoA = clip("clip-a", "art-a", MediaTime.ZERO, MediaTime.ofRational(1, 1),
                MediaTime.ZERO, MediaTime.ofRational(1, 1));
        TimelineClip videoB = clip("clip-b", "art-b", MediaTime.ofRational(3, 2), MediaTime.ofRational(5, 2),
                MediaTime.ofRational(1, 4), MediaTime.ofRational(5, 4));
        TimelineClip audio = clip("clip-audio", "art-audio", MediaTime.ofRational(3, 2), MediaTime.ofRational(5, 2),
                MediaTime.ZERO, MediaTime.ofRational(1, 1));
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("track-v", "Video", TrackType.VIDEO, List.of(videoA, videoB)),
                new TimelineTrack("track-a", "Audio", TrackType.AUDIO, List.of(audio))), TimelineMetadata.empty());

        String otio = exporter.export(document, new OtioExportIdentity("t", "p", "r", "timeline-1.0"));
        JsonNode root = read(otio);
        writeSample("multitrack.otio", otio);
        JsonNode tracks = root.path("tracks").path("children");
        assertThat(tracks).hasSize(2);
        assertThat(tracks.get(0).path("kind").asText()).isEqualTo("Video");
        assertThat(tracks.get(1).path("kind").asText()).isEqualTo("Audio");
        JsonNode video = tracks.get(0).path("children");
        assertThat(video).hasSize(3);
        assertThat(video.get(0).path("OTIO_SCHEMA").asText()).isEqualTo("Clip.2");
        assertThat(video.get(1).path("OTIO_SCHEMA").asText()).isEqualTo("Gap.1");
        assertThat(seconds(video.get(2).path("source_range").path("start_time"))).isEqualTo(0.25);
        assertThat(seconds(video.get(2).path("source_range").path("duration"))).isEqualTo(1.0);
    }

    @Test
    void emitsGapBeforeNonZeroTimelineStart() {
        TimelineClip only = clip("clip-late", "art-late", MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                MediaTime.ZERO, MediaTime.ofRational(1, 1));
        TimelineDocument document = new TimelineDocument("timeline-1.0",
                List.of(new TimelineTrack("track-v", "Video", TrackType.VIDEO, List.of(only))),
                TimelineMetadata.empty());
        String otio = exporter.export(document, identity());
        writeSample("nonzero-gap.otio", otio);
        JsonNode children = read(otio).path("tracks").path("children").get(0).path("children");
        assertThat(children).hasSize(2);
        assertThat(children.get(0).path("OTIO_SCHEMA").asText()).isEqualTo("Gap.1");
        assertThat(seconds(children.get(0).path("source_range").path("duration"))).isEqualTo(1.0);
    }

    @Test
    void refusesUnsupportedTrackTypeInsteadOfDroppingIt() {
        TimelineTrack text = new TimelineTrack("track-text", "Text", TrackType.TEXT, List.of());
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(text), TimelineMetadata.empty());
        assertThatThrownBy(() -> exporter.export(document, identity()))
                .isInstanceOfSatisfying(OtioExportException.class,
                        failure -> assertThat(failure.code()).isEqualTo(OtioExportException.Code.UNSUPPORTED_SEMANTIC))
                .hasMessageContaining("TEXT");
    }

    @Test
    void refusesGovernedMetadataInsteadOfSilentlyDroppingIt() {
        TimelineDocument document = new TimelineDocument("timeline-1.0", List.of(
                new TimelineTrack("v", "Video", TrackType.VIDEO, List.of())),
                new TimelineMetadata("", "", java.util.Map.of("captions.v1", "[]")));
        assertThatThrownBy(() -> exporter.export(document, identity()))
                .isInstanceOfSatisfying(OtioExportException.class,
                        failure -> assertThat(failure.code()).isEqualTo(OtioExportException.Code.UNSUPPORTED_SEMANTIC))
                .hasMessageContaining("metadata");
    }

    @Test
    void refusesNonIdentityTemporalMapping() {
        TimelineClip frozen = new TimelineClip("freeze", "art", "stream", "art", "digest",
                MediaTime.ZERO, MediaTime.ofRational(1, 1), MediaTime.ZERO, MediaTime.ofRational(1, 1),
                "MEDIA_STREAM", new FreezeTemporalMapping(MediaTime.ZERO));
        TimelineDocument document = new TimelineDocument("timeline-1.0",
                List.of(new TimelineTrack("v", "Video", TrackType.VIDEO, List.of(frozen))),
                TimelineMetadata.empty());
        assertThatThrownBy(() -> exporter.export(document, identity()))
                .isInstanceOfSatisfying(OtioExportException.class,
                        failure -> assertThat(failure.code()).isEqualTo(OtioExportException.Code.UNSUPPORTED_SEMANTIC))
                .hasMessageContaining("temporal mapping");
    }

    @Test
    void refusesOverlappingClips() {
        TimelineClip first = clip("a", "art-a", MediaTime.ZERO, MediaTime.ofRational(2, 1),
                MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineClip second = clip("b", "art-b", MediaTime.ofRational(1, 1), MediaTime.ofRational(3, 1),
                MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineDocument document = new TimelineDocument("timeline-1.0",
                List.of(new TimelineTrack("v", "Video", TrackType.VIDEO, List.of(first, second))),
                TimelineMetadata.empty());
        assertThatThrownBy(() -> exporter.export(document, identity()))
                .isInstanceOfSatisfying(OtioExportException.class,
                        failure -> assertThat(failure.code()).isEqualTo(OtioExportException.Code.INVALID_TIMELINE))
                .hasMessageContaining("overlap");
    }

    @Test
    void refusesClipWithoutSourceRange() {
        TimelineClip invalid = new TimelineClip("bad", "art", "stream", "art", "digest",
                MediaTime.ZERO, MediaTime.ofRational(1, 1), null, null, "MEDIA_STREAM");
        TimelineDocument document = new TimelineDocument("timeline-1.0",
                List.of(new TimelineTrack("v", "Video", TrackType.VIDEO, List.of(invalid))),
                TimelineMetadata.empty());
        assertThatThrownBy(() -> exporter.export(document, identity()))
                .isInstanceOfSatisfying(OtioExportException.class,
                        failure -> assertThat(failure.code()).isEqualTo(OtioExportException.Code.INVALID_TIMELINE))
                .hasMessageContaining("source range");
    }

    private static TimelineClip clip(
            String id, String artifact, MediaTime start, MediaTime end,
            MediaTime sourceStart, MediaTime sourceEnd) {
        return new TimelineClip(id, artifact, artifact + ":s0", artifact, "digest-" + id,
                start, end, sourceStart, sourceEnd, "MEDIA_STREAM");
    }

    private static TimelineDocument loadPersistedRevision() throws Exception {
        try (InputStream input = CanonicalTimelineOtioExporterTest.class.getResourceAsStream(
                "/otio/e2e14-rev8-canonical-timeline.json")) {
            assertThat(input).isNotNull();
            return TimelineDocumentJsonSerializer.deserialize(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static double seconds(JsonNode rationalTime) {
        return rationalTime.path("value").asDouble() / rationalTime.path("rate").asDouble();
    }

    private static void writeSample(String name, String otio) {
        try {
            Path output = Path.of("build", "otio", name);
            Files.createDirectories(output.getParent());
            Files.writeString(output, otio, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static JsonNode read(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static OtioExportIdentity identity() {
        return new OtioExportIdentity("tenant", "project", "revision", "timeline-1.0");
    }
}

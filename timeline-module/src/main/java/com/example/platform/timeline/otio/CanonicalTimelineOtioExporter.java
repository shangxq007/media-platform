package com.example.platform.timeline.otio;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;

/**
 * Read-only Canonical Timeline -> OTIO JSON projection.
 *
 * <p>This is an exchange projection, not a second Timeline authority. It consumes the
 * persisted canonical {@link TimelineDocument} and emits the official OTIO JSON shape.
 * The adapter intentionally supports only the lossless MVP subset and fails closed for
 * transitions, effects, text, automations, non-identity temporal mappings, and
 * non-media source kinds.</p>
 */
public final class CanonicalTimelineOtioExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Export one canonical document with stable source metadata. */
    public String export(TimelineDocument document, OtioExportIdentity identity) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(identity, "identity");
        validateSupportedDocument(document);

        ObjectNode timeline = MAPPER.createObjectNode();
        timeline.put("OTIO_SCHEMA", "Timeline.1");
        ObjectNode metadata = timeline.putObject("metadata");
        metadata.put("media_platform.tenant_id", identity.tenantId());
        metadata.put("media_platform.project_id", identity.projectId());
        metadata.put("media_platform.revision_id", identity.revisionId());
        metadata.put("media_platform.timeline_schema_version", identity.schemaVersion());
        metadata.put("media_platform.canonical_document_schema_version", document.getSchemaVersion());
        timeline.put("name", timelineName(document, identity));
        timeline.set("global_start_time", rationalTime(MediaTime.ZERO));
        timeline.set("tracks", tracks(document));
        return write(timeline);
    }

    private static void validateSupportedDocument(TimelineDocument document) {
        if (!document.getTransitions().isEmpty()) {
            throw unsupported("transitions are not supported by OTIO-R1");
        }
        if (!document.getAutomations().isEmpty()) {
            throw unsupported("automations are not supported by OTIO-R1");
        }
        if (!document.getTextElements().isEmpty()) {
            throw unsupported("text elements are not supported by OTIO-R1");
        }
        if (!document.getSemanticRelationships().isEmpty()) {
            throw unsupported("semantic relationships are not supported by OTIO-R1");
        }
        if (!document.getAudioMix().equals(AudioMix.EMPTY)) {
            throw unsupported("audio mix semantics are not supported by OTIO-R1");
        }
        if (document.getMetadata().description() != null
                && !document.getMetadata().description().isBlank()) {
            throw unsupported("timeline description metadata is not supported by OTIO-R1");
        }
        if (!document.getMetadata().properties().isEmpty()) {
            throw unsupported("timeline metadata properties are not supported by OTIO-R1");
        }
    }

    private static ObjectNode tracks(TimelineDocument document) {
        ObjectNode stack = MAPPER.createObjectNode();
        stack.put("OTIO_SCHEMA", "Stack.1");
        stack.putObject("metadata");
        stack.put("name", "tracks");
        stack.putNull("source_range");
        stack.putArray("effects");
        stack.putArray("markers");
        stack.put("enabled", true);
        stack.putNull("color");
        ArrayNode children = stack.putArray("children");
        for (TimelineTrack track : document.getTracks()) {
            children.add(track(track));
        }
        return stack;
    }

    private static ObjectNode track(TimelineTrack track) {
        String kind = switch (track.type()) {
            case VIDEO -> "Video";
            case AUDIO -> "Audio";
            case TEXT, EFFECT -> throw unsupported("track type " + track.type() + " is not supported by OTIO-R1");
        };
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "Track.1");
        node.putObject("metadata").put("media_platform.track_id", track.trackId());
        node.put("name", track.name() == null || track.name().isBlank() ? track.trackId() : track.name());
        node.putNull("source_range");
        node.putArray("effects");
        node.putArray("markers");
        node.put("enabled", true);
        node.putNull("color");
        node.put("kind", kind);
        ArrayNode children = node.putArray("children");

        MediaTime cursor = MediaTime.ZERO;
        MediaTime end = MediaTime.ZERO;
        for (TimelineClip clip : track.clips()) {
            validateClip(clip);
            MediaTime start = clip.getStartTime();
            MediaTime clipEnd = clip.getEndTime();
            if (start.isLessThan(cursor)) {
                throw invalid("track " + track.trackId() + " clips overlap at " + start);
            }
            if (start.isGreaterThan(cursor)) {
                children.add(gap(start.subtract(cursor)));
            }
            children.add(clip(clip));
            cursor = clipEnd;
            end = clipEnd;
        }
        return node;
    }

    private static void validateClip(TimelineClip clip) {
        if (clip.getEffects() != null && !clip.getEffects().isEmpty()) {
            throw unsupported("clip effects are not supported by OTIO-R1: " + clip.getClipId().value());
        }
        if (!"MEDIA_STREAM".equals(clip.getSourceKind())) {
            throw unsupported("clip source kind is not MEDIA_STREAM: " + clip.getSourceKind());
        }
        if (clip.getArtifactId() == null || clip.getArtifactId().isBlank()) {
            throw invalid("clip has no artifact identity: " + clip.getClipId().value());
        }
        if (clip.getTrimStart() == null || clip.getTrimEnd() == null) {
            throw invalid("clip has no exact source range: " + clip.getClipId().value());
        }
        if (!clip.getStartTime().isLessThan(clip.getEndTime())) {
            throw invalid("clip has a non-positive timeline range: " + clip.getClipId().value());
        }
        if (!clip.getTrimStart().isLessThan(clip.getTrimEnd())) {
            throw invalid("clip has a non-positive source range: " + clip.getClipId().value());
        }
        if (!clip.getEndTime().subtract(clip.getStartTime())
                .equals(clip.getTrimEnd().subtract(clip.getTrimStart()))) {
            throw unsupported("clip source duration differs from timeline duration: " + clip.getClipId().value());
        }
        TemporalMapping mapping = clip.getTemporalMapping();
        if (!(mapping instanceof ConstantRateTemporalMapping constant)
                || constant.direction() != PlaybackDirection.FORWARD
                || constant.rate().numerator() != 1
                || constant.rate().denominator() != 1) {
            throw unsupported("non-identity temporal mapping is not supported by OTIO-R1: "
                    + clip.getClipId().value());
        }
    }

    private static ObjectNode clip(TimelineClip clip) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "Clip.2");
        ObjectNode metadata = node.putObject("metadata");
        metadata.put("media_platform.clip_id", clip.getClipId().value());
        metadata.put("media_platform.artifact_id", clip.getArtifactId());
        metadata.put("media_platform.content_digest", clip.getContentDigest());
        metadata.put("media_platform.media_asset_id", clip.getMediaAssetId());
        metadata.put("media_platform.media_stream_id", clip.getMediaStreamId());
        metadata.put("media_platform.source_kind", clip.getSourceKind());
        node.put("name", clip.getClipId().value());
        node.set("source_range", timeRange(clip.getTrimStart(), clip.getTrimEnd()));
        node.putArray("effects");
        node.putArray("markers");
        node.put("enabled", true);
        node.putNull("color");
        ObjectNode mediaReferences = node.putObject("media_references");
        mediaReferences.set("DEFAULT_MEDIA", externalReference(clip.getArtifactId()));
        node.put("active_media_reference_key", "DEFAULT_MEDIA");
        return node;
    }

    private static ObjectNode gap(MediaTime duration) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "Gap.1");
        node.putObject("metadata").put("media_platform.kind", "GAP");
        node.put("name", "");
        node.set("source_range", timeRange(MediaTime.ZERO, duration));
        node.putArray("effects");
        node.putArray("markers");
        node.put("enabled", true);
        node.putNull("color");
        return node;
    }

    private static ObjectNode externalReference(String artifactId) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "ExternalReference.1");
        node.putObject("metadata");
        node.put("name", "");
        node.putNull("available_range");
        node.putNull("available_image_bounds");
        node.put("target_url", "urn:media-platform:artifact:" + artifactId);
        return node;
    }

    private static ObjectNode timeRange(MediaTime start, MediaTime end) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "TimeRange.1");
        node.set("duration", rationalTime(end.subtract(start)));
        node.set("start_time", rationalTime(start));
        return node;
    }

    private static ObjectNode rationalTime(MediaTime time) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "RationalTime.1");
        node.put("rate", time.timeScale());
        node.put("value", time.ticks());
        return node;
    }

    private static String timelineName(TimelineDocument document, OtioExportIdentity identity) {
        String title = document.getMetadata().title();
        return title == null || title.isBlank() ? "Timeline " + identity.revisionId() : title;
    }

    private static String write(ObjectNode root) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception failure) {
            throw new IllegalStateException("failed to serialize OTIO timeline", failure);
        }
    }

    private static OtioExportException unsupported(String message) {
        return new OtioExportException(OtioExportException.Code.UNSUPPORTED_SEMANTIC, message);
    }

    private static OtioExportException invalid(String message) {
        return new OtioExportException(OtioExportException.Code.INVALID_TIMELINE, message);
    }
}

package com.example.platform.render.domain.timeline.canonical;

import com.example.platform.audio.domain.mix.AudioMix;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Canonical Timeline Document - immutable, strongly typed, schema-versioned.
 *
 * <p>AUDIO_V2 (A3/A13): the document carries the canonical typed
 * {@link AudioMix} semantic state (owned by audio-module). Audio semantic
 * edits therefore alter the Timeline revision semantic content (and content
 * hash) through this single reference; no Audio state copy lives in Timeline.
 * An absent mix is normalized to {@link AudioMix#EMPTY} for deterministic
 * serialization.
 */
public class TimelineDocument {

    @JsonProperty("schemaVersion")
    private final String schemaVersion;

    @JsonProperty("tracks")
    private final List<TimelineTrack> tracks;

    @JsonProperty("metadata")
    private final TimelineMetadata metadata;

    @JsonProperty("audioMix")
    private final AudioMix audioMix;

    /** Convenience constructor: document without an audio mix (normalized to empty). */
    public TimelineDocument(
            String schemaVersion,
            List<TimelineTrack> tracks,
            TimelineMetadata metadata) {
        this(schemaVersion, tracks, metadata, AudioMix.EMPTY);
    }

    @JsonCreator
    public TimelineDocument(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("tracks") List<TimelineTrack> tracks,
            @JsonProperty("metadata") TimelineMetadata metadata,
            @JsonProperty("audioMix") AudioMix audioMix) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.tracks = tracks != null ? List.copyOf(tracks) : List.of();
        this.metadata = metadata != null ? metadata : TimelineMetadata.empty();
        this.audioMix = audioMix != null ? audioMix : AudioMix.EMPTY;
    }

    public static final String CURRENT_SCHEMA_VERSION = "timeline-1.0";

    public String getSchemaVersion() { return schemaVersion; }
    public List<TimelineTrack> getTracks() { return tracks; }
    public TimelineMetadata getMetadata() { return metadata; }
    public AudioMix getAudioMix() { return audioMix; }
}

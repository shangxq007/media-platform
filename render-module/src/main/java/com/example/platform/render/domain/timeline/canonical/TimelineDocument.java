package com.example.platform.render.domain.timeline.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Canonical Timeline Document - immutable, strongly typed, schema-versioned.
 */
public class TimelineDocument {
    
    @JsonProperty("schemaVersion")
    private final String schemaVersion;
    
    @JsonProperty("tracks")  
    private final List<TimelineTrack> tracks;
    
    @JsonProperty("metadata")
    private final TimelineMetadata metadata;

    @JsonCreator
    public TimelineDocument(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("tracks") List<TimelineTrack> tracks,
            @JsonProperty("metadata") TimelineMetadata metadata) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.tracks = tracks != null ? List.copyOf(tracks) : List.of();
        this.metadata = metadata != null ? metadata : TimelineMetadata.empty();
    }

    public static final String CURRENT_SCHEMA_VERSION = "timeline-1.0";

    public String getSchemaVersion() { return schemaVersion; }
    public List<TimelineTrack> getTracks() { return tracks; }
    public TimelineMetadata getMetadata() { return metadata; }
}

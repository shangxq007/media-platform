package com.example.platform.timeline.canonical;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    @JsonProperty("semanticRelationships")
    private final List<SemanticRelationship> semanticRelationships;

    /**
     * ROADMAP_19 (C34/C54): authored TextElements. NON_EMPTY inclusion keeps
     * pre-#19 documents byte- and hash-stable (no empty textElements key).
     */
    @JsonProperty("textElements")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<TextElement> textElements;

    /** Convenience constructor: document without audio mix / relationships. */
    public TimelineDocument(
            String schemaVersion,
            List<TimelineTrack> tracks,
            TimelineMetadata metadata) {
        this(schemaVersion, tracks, metadata, AudioMix.EMPTY, List.of(), List.of());
    }

    /** Convenience constructor: document with audio mix, no relationships. */
    public TimelineDocument(
            String schemaVersion,
            List<TimelineTrack> tracks,
            TimelineMetadata metadata,
            AudioMix audioMix) {
        this(schemaVersion, tracks, metadata, audioMix, List.of(), List.of());
    }

    /** Convenience constructor: document with audio mix + relationships. */
    public TimelineDocument(
            String schemaVersion,
            List<TimelineTrack> tracks,
            TimelineMetadata metadata,
            AudioMix audioMix,
            List<SemanticRelationship> semanticRelationships) {
        this(schemaVersion, tracks, metadata, audioMix, semanticRelationships, List.of());
    }

    @JsonCreator
    public TimelineDocument(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("tracks") List<TimelineTrack> tracks,
            @JsonProperty("metadata") TimelineMetadata metadata,
            @JsonProperty("audioMix") AudioMix audioMix,
            @JsonProperty("semanticRelationships") List<SemanticRelationship> semanticRelationships,
            @JsonProperty("textElements") List<TextElement> textElements) {
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
        this.semanticRelationships = semanticRelationships != null
                ? semanticRelationships.stream().sorted(RELATIONSHIP_ORDER).toList()
                : List.of();
        this.textElements = textElements != null
                ? textElements.stream().sorted(TEXT_ELEMENT_ORDER).toList()
                : List.of();
        // ROADMAP #19 (A2): TextElement instance identity is aggregate-unique —
        // duplicate IDs fail closed at construction (canonical authored state).
        java.util.Set<String> elementIds = new java.util.HashSet<>();
        for (TextElement element : this.textElements) {
            if (!elementIds.add(element.id().value())) {
                throw new IllegalArgumentException(
                        "Duplicate TextElement id in document: " + element.id().value());
            }
        }
    }

    /** Deterministic relationship ordering: kind, then semantic identity key. */
    private static final java.util.Comparator<SemanticRelationship> RELATIONSHIP_ORDER =
            java.util.Comparator
                    .comparing((SemanticRelationship r) -> r.kind().name())
                    .thenComparing(r -> r instanceof com.example.platform.timeline.semantics.relationship.SyncRelationship s
                            ? s.identityKey()
                            : ((com.example.platform.timeline.semantics.relationship.GroupRelationship) r).groupId().value());

    /** ROADMAP_19 (C51): deterministic TextElement compositing order by id. */
    private static final java.util.Comparator<TextElement> TEXT_ELEMENT_ORDER =
            java.util.Comparator.comparing((TextElement e) -> e.id().value());

    public static final String CURRENT_SCHEMA_VERSION = "timeline-1.0";

    public String getSchemaVersion() { return schemaVersion; }
    public List<TimelineTrack> getTracks() { return tracks; }
    public TimelineMetadata getMetadata() { return metadata; }
    public AudioMix getAudioMix() { return audioMix; }
    public List<SemanticRelationship> getSemanticRelationships() { return semanticRelationships; }
    public List<TextElement> getTextElements() { return textElements; }
}

package com.example.platform.artifact.contract;

import com.example.platform.shared.time.MediaTime;
import java.util.List;
import java.util.Objects;

/** Immutable normalized facts bound to an accepted source Artifact. */
@org.springframework.modulith.NamedInterface("app")
public record ArtifactSourceFacts(MediaTime duration, List<Stream> streams, StreamCounts counts) {
    public ArtifactSourceFacts(MediaTime duration, List<Stream> streams) {
        this(duration, streams, null);
    }

    /** Counts all streams from the probe, including untimed streams unavailable to editing. */
    public record StreamCounts(int video, int audio) {
        public StreamCounts {
            if (video < 0 || audio < 0) throw new IllegalArgumentException("negative stream count");
        }
        public int of(Kind kind) { return kind == Kind.VIDEO ? video : audio; }
    }

    public ArtifactSourceFacts {
        Objects.requireNonNull(duration, "duration");
        if (!duration.isGreaterThan(MediaTime.ZERO)) throw new IllegalArgumentException("positive duration required");
        streams = List.copyOf(streams);
        if (counts != null) {
            for (Kind kind : Kind.values()) {
                if (streams.stream().filter(stream -> stream.kind() == kind).count() > counts.of(kind)) {
                    throw new IllegalArgumentException("source stream count contradicts timed streams");
                }
            }
        }
        if (streams.isEmpty() || streams.stream().map(Stream::streamId).distinct().count() != streams.size()) {
            throw new IllegalArgumentException("unique source streams required");
        }
        if (streams.stream().anyMatch(stream -> stream.duration().isGreaterThan(duration))) {
            throw new IllegalArgumentException("stream duration exceeds source duration");
        }
    }
    public record Stream(String streamId, Kind kind, MediaTime duration, String displayAspectRatio) {
        public Stream(String streamId, Kind kind, MediaTime duration) {
            this(streamId, kind, duration, null);
        }

        public Stream {
            if (streamId == null || streamId.isBlank()) throw new IllegalArgumentException("stream identity required");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(duration, "duration");
            if (!duration.isGreaterThan(MediaTime.ZERO)) throw new IllegalArgumentException("positive stream duration required");
            if (displayAspectRatio != null && (kind != Kind.VIDEO
                    || !displayAspectRatio.matches("[1-9][0-9]{0,9}:[1-9][0-9]{0,9}"))) {
                throw new IllegalArgumentException("Invalid source display aspect fact");
            }
        }
    }
    public enum Kind { VIDEO, AUDIO }
}

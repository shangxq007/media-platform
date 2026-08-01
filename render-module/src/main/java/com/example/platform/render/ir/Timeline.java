package com.example.platform.render.ir;

import java.util.List;
import java.util.Objects;

/**
 * A timeline containing an ordered list of video tracks.
 */
public record Timeline(String id, List<VideoTrack> tracks) {
    public Timeline {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tracks, "tracks must not be null");
        tracks = List.copyOf(tracks);
    }
}

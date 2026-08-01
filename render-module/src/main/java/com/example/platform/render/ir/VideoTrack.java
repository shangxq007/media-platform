package com.example.platform.render.ir;

import java.util.List;
import java.util.Objects;

/**
 * A video track containing an ordered list of clips.
 */
public record VideoTrack(String id, List<Clip> clips) {
    public VideoTrack {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clips, "clips must not be null");
        clips = List.copyOf(clips);
    }
}

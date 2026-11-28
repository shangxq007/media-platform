package com.example.platform.timeline.api.review;

import com.example.platform.shared.time.MediaTime;

/** Exact timeline position and the comparison slot that presented it. The branch is a display label, not a lookup key. */
public record ReviewFrameAnchor(Side side, String branch, String position) {
    public enum Side { A, B }

    public ReviewFrameAnchor {
        if (side == null || branch == null || !branch.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("A comparison side and branch are required");
        }
        if (position == null || !position.matches("[0-9]{1,19}/[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("The position must be a nonnegative exact rational number of seconds");
        }
        MediaTime time = MediaTime.parse(position);
        position = time.ticks() + "/" + time.timeScale();
    }

    public MediaTime time() {
        return MediaTime.parse(position);
    }
}

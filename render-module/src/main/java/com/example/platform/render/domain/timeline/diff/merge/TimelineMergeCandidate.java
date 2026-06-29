package com.example.platform.render.domain.timeline.diff.merge;

import com.example.platform.render.domain.timeline.diff.TimelineDiff;
import java.util.Map;

/**
 * A merge candidate representing one side of a three-way merge.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergeCandidate(
        TimelineMergeSide side,
        TimelineDiff diff,
        Map<String, String> safeMetadata) {

    public TimelineMergeCandidate {
        if (side == null) throw new IllegalArgumentException("Side must not be null");
        if (diff == null) throw new IllegalArgumentException("Diff must not be null");
    }
}

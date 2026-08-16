package com.example.platform.timeline.version.application;

import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.version.TimelineBranch;
import com.example.platform.timeline.version.TimelineBranchName;
import com.example.platform.timeline.version.TimelineCommit;
import com.example.platform.timeline.version.TimelineCommitId;
import com.example.platform.timeline.version.TimelineRevisionRef;
import java.util.Optional;

/**
 * Pure interface for looking up timeline version entities.
 * No database implementation. No StorageRuntime. No ProductRuntime.
 * Tests may use in-memory fake lookup.
 */
public interface TimelineVersionLookup {

    Optional<TimelineBranch> findBranch(TimelineBranchName branchName);

    Optional<CanonicalTimelineSnapshot> findSnapshot(TimelineRevisionRef revisionRef);

    Optional<TimelineCommit> findCommit(TimelineCommitId commitId);
}

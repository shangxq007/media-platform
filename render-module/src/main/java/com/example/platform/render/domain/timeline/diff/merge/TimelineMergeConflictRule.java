package com.example.platform.render.domain.timeline.diff.merge;

import com.example.platform.render.domain.timeline.diff.TimelineChangeType;
import com.example.platform.render.domain.timeline.diff.TimelineConflictType;
import java.util.Map;

/**
 * Rule mapping a change type to a conflict type and issue code.
 * Vocabulary only — no rule engine, no scripts, no external code execution.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergeConflictRule(
        TimelineMergeConflictRuleId id,
        TimelineChangeType changeType,
        TimelineConflictType conflictType,
        TimelineMergeConflictIssueCode issueCode,
        boolean blocking,
        Map<String, String> safeMetadata) {

    public TimelineMergeConflictRule {
        if (id == null) throw new IllegalArgumentException("Rule ID must not be null");
        if (changeType == null) throw new IllegalArgumentException("Change type must not be null");
        if (conflictType == null) throw new IllegalArgumentException("Conflict type must not be null");
        if (issueCode == null) throw new IllegalArgumentException("Issue code must not be null");
    }
}

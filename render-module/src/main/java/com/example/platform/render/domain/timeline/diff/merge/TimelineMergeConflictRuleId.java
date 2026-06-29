package com.example.platform.render.domain.timeline.diff.merge;

/**
 * Identifier for a merge conflict rule. Internal domain model.
 */
public record TimelineMergeConflictRuleId(String value) {
    public TimelineMergeConflictRuleId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineMergeConflictRuleId must not be blank");
    }
}

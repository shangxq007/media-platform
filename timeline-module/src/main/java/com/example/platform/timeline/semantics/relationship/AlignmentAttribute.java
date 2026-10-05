package com.example.platform.timeline.semantics.relationship;

import java.util.Objects;

/**
 * L5 lineage: typed alignment attribute (tag + explicit typed value).
 *
 * <p>Never a {@code Map<String,String>}: the value kind is expressed by
 * {@link AlignmentAttributeValue}.
 */
public record AlignmentAttribute(String tag, AlignmentAttributeValue value) {

    public AlignmentAttribute {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(value, "value");
        if (tag.isBlank()) {
            throw new IllegalArgumentException("AlignmentAttribute.tag must not be blank");
        }
    }
}

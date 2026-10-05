package com.example.platform.timeline.semantics.relationship;

import java.util.Objects;

/**
 * L5 lineage: typed value of an {@link AlignmentAttribute}.
 *
 * <p>Value kinds are explicit typed variants (no {@code Map<String,String>}, no {@code Object},
 * no {@code JsonNode}). Jackson type metadata below is decode plumbing only.
 */
@com.fasterxml.jackson.annotation.JsonTypeInfo(
        use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
        include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
        property = "kind")
@com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = AlignmentAttributeValue.TextValue.class, name = "TEXT"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = AlignmentAttributeValue.NumberValue.class, name = "NUMBER"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = AlignmentAttributeValue.BooleanValue.class, name = "BOOLEAN")
})
public sealed interface AlignmentAttributeValue {

    record TextValue(String value) implements AlignmentAttributeValue {
        public TextValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record NumberValue(long value) implements AlignmentAttributeValue {
    }

    record BooleanValue(boolean value) implements AlignmentAttributeValue {
    }
}

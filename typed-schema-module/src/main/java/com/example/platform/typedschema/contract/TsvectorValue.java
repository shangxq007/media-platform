package com.example.platform.typedschema.contract;

import java.io.Serializable;
import java.util.Objects;

/**
 * Type-safe wrapper for PostgreSQL {@code tsvector} values.
 *
 * <p>PostgreSQL tsvector is a sorted list of distinct lexemes, which
 * are words that have been normalized to make them suitable for
 * full-text search. The raw representation is stored as a string,
 * e.g. {@code 'cat':1 'dog':4 'rat':2}.</p>
 *
 * <p>This wrapper prevents accidental use of raw {@code Object} for
 * tsvector columns and provides a clear domain type.</p>
 *
 * @param value the raw tsvector text representation; never null
 */
public record TsvectorValue(String value) implements Serializable {

    private static final long serialVersionUID = 1L;

    public TsvectorValue {
        Objects.requireNonNull(value, "tsvector value must not be null");
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.example.platform.workerfabric.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Shared canonicalization for dependency features and build/runtime flags. */
final class RuntimeDependencyNames {

    private static final String NORMALIZED_NAME = "[a-z0-9]+(?:[._-][a-z0-9]+)*";

    private RuntimeDependencyNames() {}

    static List<String> canonicalize(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<String> canonical = new ArrayList<>(values.size());
        HashSet<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || !value.matches(NORMALIZED_NAME)) {
                throw new IllegalArgumentException(field + " must contain only normalized names");
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException(field + " must not contain duplicates");
            }
            canonical.add(value);
        }
        canonical.sort(String::compareTo);
        return List.copyOf(canonical);
    }
}

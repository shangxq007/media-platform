package com.example.platform.capability.effective;

import java.util.List;

final class EffectiveCapabilityValidation {

    private EffectiveCapabilityValidation() {
    }

    static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }

    static List<String> immutableNonBlank(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        List<String> copy = List.copyOf(values);
        copy.forEach(value -> requireNonBlank(value, field));
        return copy;
    }
}

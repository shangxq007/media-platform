package com.example.platform.studio.identity;

import java.text.Normalizer;

public interface StudioId {
    String value();

    static String requireValid(String value, String type) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(type + " must not be blank");
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (!normalized.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}._:-]{0,127}")) {
            throw new IllegalArgumentException(type + " has invalid format");
        }
        return normalized;
    }
}

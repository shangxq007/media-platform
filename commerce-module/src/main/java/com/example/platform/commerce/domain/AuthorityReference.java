package com.example.platform.commerce.domain;

public record AuthorityReference(String key, long version) {
    public AuthorityReference {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("authority reference key is required");
        if (version < 1) throw new IllegalArgumentException("authority reference version must be positive");
    }
}

package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C24): authored font family selection name — intent, NOT exact identity. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class FontFamilyName {

    private final String name;

    public FontFamilyName(String name) {
        Objects.requireNonNull(name, "name");
        String n = name.trim();
        if (n.isEmpty()) {
            throw new IllegalArgumentException("font family name must not be blank");
        }
        if (n.contains("/") || n.contains("\\") || n.contains("://")) {
            throw new IllegalArgumentException("font family name must not contain path/URL syntax");
        }
        this.name = n;
    }

    public String value() { return name; }

    @Override
    public boolean equals(Object o) { return o instanceof FontFamilyName f && name.equals(f.name); }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return name; }
}

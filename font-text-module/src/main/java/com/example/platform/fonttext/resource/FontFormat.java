package com.example.platform.fonttext.resource;

import java.util.Objects;

/** ROADMAP_19 (C9/C41): bounded typed font format. Never raw extension/MIME string. */
public enum FontFormat {
    TRUETYPE, OPENTYPE_CFF, TRUETYPE_COLLECTION, OPENTYPE_COLLECTION, WOFF, WOFF2, UNKNOWN;

    public static FontFormat fromObservation(String providerValue) {
        if (providerValue == null || providerValue.isBlank()) {
            return UNKNOWN;
        }
        String v = providerValue.trim().toLowerCase();
        return switch (v) {
            case "truetype", "ttf" -> TRUETYPE;
            case "opentype", "cff", "otf" -> OPENTYPE_CFF;
            case "collection", "ttc" -> TRUETYPE_COLLECTION;
            case "otc" -> OPENTYPE_COLLECTION;
            case "woff" -> WOFF;
            case "woff2" -> WOFF2;
            default -> UNKNOWN;
        };
    }
}

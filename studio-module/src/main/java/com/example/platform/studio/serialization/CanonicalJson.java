package com.example.platform.studio.serialization;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class CanonicalJson {
    private CanonicalJson() {}

    public static byte[] utf8(String json) { return json.getBytes(StandardCharsets.UTF_8); }

    public static String object(Map<String, String> members) {
        var sorted = new TreeMap<>(members);
        var result = new StringBuilder("{");
        boolean first = true;
        for (var entry : sorted.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!first) result.append(',');
            first = false;
            result.append(quote(entry.getKey())).append(':').append(entry.getValue());
        }
        return result.append('}').toString();
    }

    public static String array(List<String> values) { return "[" + String.join(",", values) + "]"; }

    public static String quote(String value) {
        if (value == null) throw new IllegalArgumentException("canonical string is required");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        var result = new StringBuilder("\"");
        normalized.codePoints().forEach(cp -> {
            switch (cp) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (cp < 0x20) result.append(String.format(Locale.ROOT, "\\u%04x", cp));
                    else result.appendCodePoint(cp);
                }
            }
        });
        return result.append('"').toString();
    }

    public static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }
}

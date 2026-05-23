package com.example.platform.render.app.timeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * Stable content hash for Internal Timeline JSON (after canonicalization).
 */
@Component
public class TimelineContentHasher {

    private final TimelineCanonicalizer canonicalizer;

    public TimelineContentHasher(TimelineCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public String hashInternalTimeline(String timelineJson) {
        try {
            TimelineCanonicalizer.CanonicalizeResult canonical = canonicalizer.canonicalize(timelineJson);
            return sha256Hex(canonical.timelineJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot hash timeline JSON: " + e.getMessage(), e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

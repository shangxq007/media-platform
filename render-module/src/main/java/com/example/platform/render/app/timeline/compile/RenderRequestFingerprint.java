package com.example.platform.render.app.timeline.compile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Deterministic fingerprint for a render request.
 *
 * <p>Internal only — identifies a unique render request based on stable
 * logical inputs. Used for deduplication.</p>
 *
 * <p>Components:
 * <ul>
 *   <li>projectId</li>
 *   <li>timelineRevisionId</li>
 *   <li>normalized output profile</li>
 *   <li>render execution mode (if it changes execution semantics)</li>
 * </ul>
 *
 * <p>Excludes: random UUID, timestamp, temp paths, job IDs, worker host,
 * process environment, bucket/key/rootPath.</p>
 *
 * @param value the deterministic fingerprint hash
 */
public record RenderRequestFingerprint(String value) {

    /**
     * Generate a deterministic fingerprint from render request components.
     *
     * @param projectId          the project identifier
     * @param timelineRevisionId the timeline revision identifier
     * @param outputProfile      the normalized output profile
     * @param executionMode      the execution mode (LEGACY or PLAN_BASED)
     * @return the fingerprint
     */
    public static RenderRequestFingerprint generate(
            String projectId, String timelineRevisionId,
            String outputProfile, String executionMode) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(safeBytes(projectId));
            md.update("|".getBytes(StandardCharsets.UTF_8));
            md.update(safeBytes(timelineRevisionId));
            md.update("|".getBytes(StandardCharsets.UTF_8));
            md.update(safeBytes(normalizeProfile(outputProfile)));
            md.update("|".getBytes(StandardCharsets.UTF_8));
            md.update(safeBytes(executionMode));
            return new RenderRequestFingerprint(
                    "rfp-" + HexFormat.of().formatHex(md.digest()).substring(0, 24));
        } catch (Exception e) {
            return new RenderRequestFingerprint(
                    "rfp-" + projectId + "-" + timelineRevisionId + "-" + normalizeProfile(outputProfile));
        }
    }

    /**
     * Normalize output profile to a canonical form.
     */
    static String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) return "default_1080p";
        return profile.trim().toLowerCase().replace(" ", "_");
    }

    private static byte[] safeBytes(String value) {
        return (value != null ? value : "").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return value;
    }
}

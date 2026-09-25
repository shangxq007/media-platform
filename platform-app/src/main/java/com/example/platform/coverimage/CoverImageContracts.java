package com.example.platform.coverimage;

/**
 * Canonical media.cover-image capability contract.
 *
 * <p>Platform owns this contract. One capability, one provider, one execution path. The subject is a
 * canonical Artifact (no second identity) and the produced cover is committed as an image Artifact
 * related to the subject through {@code ProvenanceRelationType.COVER_OF}.
 */
public final class CoverImageContracts {

    public static final String CAPABILITY = "media.cover-image";
    public static final String PROVIDER = "platform-ffmpeg-cover-image";
    public static final String PROVIDER_VERSION = "1.0.0";
    public static final String OPERATION_ID = "cover-image:" + CAPABILITY + "@1";

    private CoverImageContracts() {}

    public enum Status { ADMITTED, RUNNING, COMMITTING, COMPLETED, FAILED, CANCELLED }

    /** Immutable cover request; the subject is the canonical Artifact identity. */
    public record Request(
            String tenantId,
            String projectId,
            String subjectArtifactId,
            double timestampSeconds,
            String imageFormat,
            Integer width,
            Integer quality,
            String idempotencyKey) {

        public Request {
            require(tenantId, "tenantId");
            require(projectId, "projectId");
            require(subjectArtifactId, "subjectArtifactId");
            require(idempotencyKey, "idempotencyKey");
            require(imageFormat, "imageFormat");
            if (!"png".equals(imageFormat) && !"jpeg".equals(imageFormat)) {
                throw new IllegalArgumentException("imageFormat must be png or jpeg");
            }
            if (!Double.isFinite(timestampSeconds) || timestampSeconds < 0) {
                throw new IllegalArgumentException("timestampSeconds must be finite and non-negative");
            }
            if (width != null && (width < 16 || width > 8192)) {
                throw new IllegalArgumentException("width must be within 16..8192");
            }
            if (quality != null && (quality < 1 || quality > 100)) {
                throw new IllegalArgumentException("quality must be within 1..100");
            }
        }
    }

    /** Durable task outcome; {@code artifactId} is present only when the cover was committed. */
    public record Result(String taskId, Status status, String artifactId, String failureCode) {}

    static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

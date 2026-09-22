package com.example.platform.thumbnail;

import java.util.Locale;

/** Canonical media.thumbnail request and provider contract; FFmpeg is not exposed here. */
public final class ThumbnailContracts {
    public static final String CAPABILITY = "media.thumbnail";
    public static final String PROVIDER = "ffmpeg.cpu.frame-extract.v1";
    private ThumbnailContracts() {}

    public record Request(String tenantId, String projectId, String sourceAssetId,
            double timestampSeconds, String imageFormat, Integer width, Integer quality,
            String idempotencyKey) {
        public Request {
            require(tenantId, "tenantId"); require(projectId, "projectId");
            require(sourceAssetId, "sourceAssetId"); require(idempotencyKey, "idempotencyKey");
            if (!Double.isFinite(timestampSeconds) || timestampSeconds < 0) throw new IllegalArgumentException("timestamp must be finite and non-negative");
            imageFormat = imageFormat == null ? "jpeg" : imageFormat.toLowerCase(Locale.ROOT);
            if (!imageFormat.equals("jpeg") && !imageFormat.equals("png")) throw new IllegalArgumentException("unsupported image format");
            if (width != null && (width < 16 || width > 4096)) throw new IllegalArgumentException("unsupported thumbnail width");
            if (quality != null && (quality < 1 || quality > 100)) throw new IllegalArgumentException("unsupported thumbnail quality");
        }
        private static void require(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); }
    }

    public enum Status { ADMITTED, RUNNING, COMMITTING, COMPLETED, FAILED, CANCELLED }
    public record Result(String taskId, Status status, String artifactId, String failureCode) {}
}

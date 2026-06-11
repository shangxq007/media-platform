package com.example.platform.render.infrastructure.api;

import java.util.List;

public record PublicApiError(
        String code,
        String message,
        List<String> details
) {
    public static PublicApiError invalidRequest(String message) {
        return new PublicApiError("INVALID_REQUEST", message, List.of());
    }

    public static PublicApiError templateNotAllowed(String templateId) {
        return new PublicApiError("TEMPLATE_NOT_ALLOWED",
                "Template not in allowlist: " + templateId, List.of());
    }

    public static PublicApiError fontNotFound(String fontAssetId) {
        return new PublicApiError("FONT_NOT_FOUND",
                "Font asset not found: " + fontAssetId, List.of());
    }

    public static PublicApiError fontNotReady(String fontAssetId) {
        return new PublicApiError("FONT_NOT_READY",
                "Font asset not in READY state: " + fontAssetId, List.of());
    }

    public static PublicApiError fontNotOwned(String fontAssetId) {
        return new PublicApiError("FONT_NOT_OWNED",
                "Font asset does not belong to tenant: " + fontAssetId, List.of());
    }

    public static PublicApiError unsupportedFormat(String format) {
        return new PublicApiError("UNSUPPORTED_FORMAT",
                "Output format not supported: " + format, List.of());
    }

    public static PublicApiError captionLimitExceeded(int count) {
        return new PublicApiError("CAPTION_LIMIT_EXCEEDED",
                "Too many captions: " + count, List.of());
    }

    public static PublicApiError durationLimitExceeded() {
        return new PublicApiError("DURATION_LIMIT_EXCEEDED",
                "Caption or video duration exceeds limit", List.of());
    }

    public static PublicApiError jobNotFound(String jobId) {
        return new PublicApiError("JOB_NOT_FOUND",
                "Job not found: " + jobId, List.of());
    }

    public static PublicApiError artifactNotFound(String artifactId) {
        return new PublicApiError("ARTIFACT_NOT_FOUND",
                "Artifact not found: " + artifactId, List.of());
    }

    public static PublicApiError rateLimited() {
        return new PublicApiError("RATE_LIMITED",
                "Too many requests", List.of());
    }

    public static PublicApiError quotaExceeded() {
        return new PublicApiError("QUOTA_EXCEEDED",
                "Quota exhausted", List.of());
    }

    public static PublicApiError rawJsRejected() {
        return new PublicApiError("INVALID_REQUEST",
                "Raw Remotion JS is not allowed", List.of());
    }

    public static PublicApiError rawCommandRejected() {
        return new PublicApiError("INVALID_REQUEST",
                "Raw FFmpeg command is not allowed", List.of());
    }

    public static PublicApiError providerSelectionRejected() {
        return new PublicApiError("INVALID_REQUEST",
                "Provider selection is not allowed", List.of());
    }
}

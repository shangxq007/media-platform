package com.example.platform.render.domain.artifact;

/**
 * Types of artifact nodes in the DAG.
 */
public enum ArtifactNodeType {

    /**
     * Video output (e.g., MP4, WebM).
     */
    VIDEO("video"),

    /**
     * Audio output (e.g., MP3, WAV, AAC).
     */
    AUDIO("audio"),

    /**
     * Image output (e.g., PNG, JPEG, thumbnail).
     */
    IMAGE("image"),

    /**
     * Timeline JSON (intermediate representation).
     */
    TIMELINE_JSON("timeline"),

    /**
     * Thumbnail image for video.
     */
    THUMBNAIL("thumbnail"),

    /**
     * Subtitle file (e.g., SRT, ASS, VTT).
     */
    SUBTITLE("subtitle"),

    /**
     * Render plan or pipeline execution JSON.
     */
    RENDER_PLAN("plan"),

    /**
     * Generic/unknown type.
     */
    UNKNOWN("unknown");

    private final String mimeTypePrefix;

    ArtifactNodeType(String mimeTypePrefix) {
        this.mimeTypePrefix = mimeTypePrefix;
    }

    /**
     * Get the MIME type prefix for this artifact type.
     */
    public String getMimeTypePrefix() {
        return mimeTypePrefix;
    }

    /**
     * Infer artifact type from file extension.
     */
    public static ArtifactNodeType fromExtension(String extension) {
        if (extension == null) return UNKNOWN;
        return switch (extension.toLowerCase()) {
            case "mp4", "webm", "mov", "avi", "mkv" -> VIDEO;
            case "mp3", "wav", "aac", "ogg", "flac" -> AUDIO;
            case "png", "jpg", "jpeg", "gif", "webp" -> IMAGE;
            case "json" -> TIMELINE_JSON;
            case "srt", "ass", "vtt" -> SUBTITLE;
            default -> UNKNOWN;
        };
    }

    /**
     * Infer artifact type from MIME type.
     */
    public static ArtifactNodeType fromMimeType(String mimeType) {
        if (mimeType == null) return UNKNOWN;
        return switch (mimeType.split("/")[0]) {
            case "video" -> VIDEO;
            case "audio" -> AUDIO;
            case "image" -> IMAGE;
            case "application" -> mimeType.contains("json") ? TIMELINE_JSON : UNKNOWN;
            case "text" -> mimeType.contains("subtitle") ? SUBTITLE : UNKNOWN;
            default -> UNKNOWN;
        };
    }
}

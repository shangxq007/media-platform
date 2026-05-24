package com.example.platform.render.domain.clientexport;

import java.time.Instant;
import java.util.Set;

public record ClientExportSession(
        String id,
        String tenantId,
        String workspaceId,
        String projectId,
        String userId,
        String timelineSnapshotId,
        String exportType,
        String preset,
        String status,
        int progress,
        String resolution,
        int fps,
        String format,
        boolean watermarkEnabled,
        Integer videoBitrate,
        Integer audioBitrate,
        Integer maxDurationSec,
        String outputUri,
        String artifactId,
        String downloadPath,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public static final String STATUS_CREATED     = "CREATED";
    public static final String STATUS_PREPARING   = "PREPARING";
    public static final String STATUS_EXPORTING   = "EXPORTING";
    public static final String STATUS_COMPLETED   = "COMPLETED";
    public static final String STATUS_FAILED      = "FAILED";
    public static final String STATUS_CANCELLED   = "CANCELLED";
    public static final String STATUS_EXPIRED     = "EXPIRED";

    private static final Set<String> TERMINAL_STATES = Set.of(
            STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED, STATUS_EXPIRED);

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(status);
    }

    public boolean canTransitionTo(String target) {
        return switch (status) {
            case STATUS_CREATED   -> Set.of(STATUS_PREPARING, STATUS_EXPORTING, STATUS_CANCELLED).contains(target);
            case STATUS_PREPARING -> Set.of(STATUS_EXPORTING, STATUS_FAILED, STATUS_CANCELLED).contains(target);
            case STATUS_EXPORTING -> Set.of(STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED).contains(target);
            default -> false;
        };
    }
}

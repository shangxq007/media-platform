package com.example.platform.render.app.dto;

public record RenderJobResponse(String id, String projectId, String timelineSnapshotId, String profile, String status) {}
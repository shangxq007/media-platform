package com.example.platform.render.app.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRenderJobRequest(@NotBlank String projectId, @NotBlank String timelineSnapshotId, @NotBlank String profile) {}